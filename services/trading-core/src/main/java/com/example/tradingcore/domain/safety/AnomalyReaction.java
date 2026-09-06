package com.example.tradingcore.domain.safety;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.config.AnomalyReportProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.AnomalyReportDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Отвечает на находку детектора: держит гистерезис и зовёт исполнителя
 * блокировки. Собственной ступени не выбирает — она приходит с находкой
 * (docs/components/AnomalyJob.md §«Такт и гистерезис»).
 *
 * <p><b>Носитель подтверждения — стоящий отчёт, а не счётчик.</b>
 * Детектор с гистерезисом в два тика на первом тике заводит
 * наблюдательную строку и ступени не запрашивает; на следующем признак
 * считается подтверждённым, если строка по этому ключу уже стои́т и
 * заведена в окне. Отдельного счётчика не заводится: durable-факт
 * «признак наблюдался прошлым тиком» и есть стоящая строка, и она
 * переживает рестарт — в отличие от памяти инстанса, которая обнуляется
 * ровно в аварии, когда рестарты и происходят.
 *
 * <p><b>Повторное снятие риска не запускается.</b> Признак, держащийся
 * именно потому, что снятие риска не подтвердилось, каждым тиком зовёт
 * исполнителя заново — и анкер этот вызов поглощает. Права на доведение
 * недоделанного у автоматического сигнала нет
 * (docs/components/SafetyHoldCoordinator.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyReaction {

    /** Единица гистерезиса: реакция с первого наблюдения. */
    private static final Integer WITHOUT_HYSTERESIS = 1;

    private final AnomalyReportDataService reportDataService;
    private final AnomalyReportService reportService;
    private final HoldService holdService;
    private final AnomalyJobProperties jobProperties;
    private final AnomalyReportProperties reportProperties;

    /**
     * Применить реакцию по находке: поднять ступень либо записать
     * наблюдение и ждать подтверждения следующим тиком.
     */
    public void apply(AnomalyFinding finding, ExchangeAccount account) {
        DealContext context = DealContext.builder()
                .exchangeAccount(account)
                .instrument(finding.getInstrument())
                .build();
        if (isTrue(reactsOnFirstSight(finding))) {
            holdService.raise(signalOf(finding), context);
            return;
        }
        if (isFalse(confirmed(finding, account))) {
            journal(finding, context);
            return;
        }
        if (isTrue(finding.getJournalOnly())) {
            return;
        }
        holdService.raise(signalOf(finding), context);
    }

    /**
     * Ступень поднимается с первого наблюдения: гистерезиса у находки нет,
     * и реакция у неё есть. Журнальная находка сюда не попадает —
     * подтверждать ей нечего, а стоящая строка служит ей дедупом, а не
     * операндом ступени.
     */
    private Boolean reactsOnFirstSight(AnomalyFinding finding) {
        return WITHOUT_HYSTERESIS.equals(finding.getHysteresisTicks())
                && isFalse(finding.getJournalOnly());
    }

    /**
     * Признак подтверждён: наблюдательная строка по этому ключу уже стои́т
     * — заведена в окне наблюдения и не позже, чем разрешает минимальный
     * возраст подтверждения.
     *
     * <p><b>Границ у окна две, и обе обязательны.</b> Нижняя — иначе отчёт
     * недельной давности читался бы подтверждением. Верхняя — иначе
     * подтверждением служит строка, заведённая тем же или смежным
     * проходом секундами раньше, а гонка чтения, против которой
     * гистерезис заведён, живёт такт.
     *
     * <p><b>Гейт стои́т и перед журнальной находкой.</b> Без него
     * журнальная тропа заводила бы строку каждым тиком бессрочно — ровно
     * то размножение отчётов, против которого дедуп по стоящему состоянию
     * и записан.
     */
    private Boolean confirmed(AnomalyFinding finding, ExchangeAccount account) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return reportDataService.existsStanding(account.getId(), instrumentId(finding),
                finding.getSubjectExternalId(), finding.getCode(),
                AnomalyReport.Severity.NON_CRITICAL,
                now.minus(reportProperties.getObservationWindow()),
                now.minus(jobProperties.getConfirmationMinAge()));
    }

    /**
     * Наблюдательная строка. Она же — операнд подтверждения следующим
     * тиком, поэтому заводится и тогда, когда ступень ещё не поднята:
     * «ничего не нашли» и «нашли, ждём подтверждения» обязаны быть
     * различимы в данных.
     */
    private void journal(AnomalyFinding finding, DealContext context) {
        try {
            reportService.journalState(context, observationSignal(finding), finding.getSubjectExternalId());
        } catch (RuntimeException e) {
            log.error("Anomaly observation row is not written code={}", finding.getCode(), e);
        }
    }

    /**
     * Сигнал наблюдения — всегда журнальный: на первом тике реакции нет, и
     * критичность отчёта производна от состава реакции, а не от того,
     * какой она станет при подтверждении.
     */
    private HoldSignal observationSignal(AnomalyFinding finding) {
        return HoldScope.EXCHANGE_ACCOUNT.equals(finding.getScope())
                ? HoldSignal.exchangeAccountJournal(finding.getCode())
                : HoldSignal.instrumentJournal(finding.getCode());
    }

    private HoldSignal signalOf(AnomalyFinding finding) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(finding.getScope())) {
            return isTrue(finding.tearsDownRisk())
                    ? HoldSignal.exchangeAccount(finding.getCode())
                    : HoldSignal.exchangeAccountSoft(finding.getCode());
        }
        return isTrue(finding.tearsDownRisk())
                ? HoldSignal.instrument(finding.getCode())
                : HoldSignal.instrumentSoft(finding.getCode());
    }

    private Long instrumentId(AnomalyFinding finding) {
        return nonNull(finding.getInstrument()) ? finding.getInstrument().getId() : null;
    }
}
