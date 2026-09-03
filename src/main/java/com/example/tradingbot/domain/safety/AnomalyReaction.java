package com.example.tradingbot.domain.safety;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.AnomalyJobProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Отвечает на находку детектора: держит гистерезис и зовёт исполнителя
 * блокировки. Собственной ступени не выбирает — она приходит с находкой.
 *
 * <p><b>Носитель подтверждения — стоящий отчёт, а не счётчик.</b>
 * Детектор с гистерезисом в два тика на первом тике заводит наблюдательную
 * строку и ступени не запрашивает; на следующем признак считается
 * подтверждённым, если строка по этому ключу уже стои́т и заведена не
 * раньше предыдущего тика. Отдельного счётчика не заводится: durable-факт
 * «признак наблюдался прошлым тиком» и есть стоящая строка, и она
 * переживает рестарт — в отличие от памяти инстанса, которая обнуляется
 * ровно в аварии (docs/components/AnomalyJob.md §«Такт и гистерезис»).
 *
 * <p><b>Повторное снятие риска не запускается.</b> Признак, держащийся
 * именно потому, что снятие риска не подтвердилось, каждым тиком зовёт
 * исполнителя заново — и анкер этот вызов поглощает. Права на доведение
 * недоделанного у автоматического сигнала нет, оно есть только у явного
 * вызова держателя (docs/components/SafetyHoldCoordinator.md).
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
    private final AnomalyJobProperties properties;

    /**
     * Применить реакцию по находке: поднять ступень либо записать
     * наблюдение и ждать подтверждения следующим тиком.
     */
    public void apply(AnomalyFinding finding, Exchange exchange) {
        DealContext context = DealContext.builder()
                .exchange(exchange)
                .instrument(finding.getInstrument())
                .build();
        if (isTrue(reactsOnFirstSight(finding))) {
            holdService.raise(signalOf(finding), context);
            return;
        }
        if (isFalse(confirmed(finding, exchange))) {
            journal(finding, context);
            return;
        }
        if (isTrue(finding.getJournalOnly())) {
            return;
        }
        holdService.raise(signalOf(finding), context);
    }

    /**
     * Ступень поднимается с первого наблюдения: гистерезиса у находки
     * нет, и реакция у неё есть. Журнальная находка сюда не попадает —
     * подтверждать ей нечего, а стоящая строка у неё служит дедупом, а не
     * операндом ступени.
     */
    private Boolean reactsOnFirstSight(AnomalyFinding finding) {
        return WITHOUT_HYSTERESIS.equals(finding.getHysteresisTicks())
                && isFalse(finding.getJournalOnly());
    }

    /**
     * Признак подтверждён: гистерезиса нет вовсе либо наблюдательная
     * строка по этому ключу уже стои́т — заведена не раньше предыдущего
     * тика и не этим же проходом.
     *
     * <p><b>Границ у окна две, и верхняя обязательна.</b> Без неё два
     * прохода, идущие подряд в секундах (ручной триггер сразу после
     * планового тика, рестарт с немедленным тиком, дрейф расписания),
     * подтверждают друг друга: гистерезис заведён против гонки чтения
     * длиной в такт, а такая пара проходов её не переживает и «подтвердит»
     * транзиторное расхождение жёсткой ступенью.
     *
     * <p><b>Гейт стои́т и перед журнальной находкой.</b> Строка, пока
     * состояние держится, одна: журнальная тропа без него заводила бы её
     * каждым тиком бессрочно — ровно то размножение отчётов, против
     * которого дедуп по стоящему состоянию и записан
     * (docs/rules/error-handling-policy.md §«Состояние «держится» читается
     * по объекту, а не по статусу отчёта»).
     */
    private Boolean confirmed(AnomalyFinding finding, Exchange exchange) {
        OffsetDateTime now = OffsetDateTime.now();
        return reportDataService.existsStanding(exchange.getId(), instrumentId(finding),
                finding.getSubjectExternalId(), finding.getCode(),
                AnomalyReport.Severity.NON_CRITICAL,
                now.minus(properties.getObservationWindow()),
                now.minus(properties.getConfirmationMinAge()));
    }

    /**
     * Наблюдательная строка. Она же — операнд подтверждения следующим
     * тиком, поэтому заводится и тогда, когда ступень ещё не поднята:
     * «ничего не нашли» и «нашли, ждём подтверждения» обязаны быть
     * различимы в данных (П3).
     */
    private void journal(AnomalyFinding finding, DealContext context) {
        try {
            reportService.journal(context, observationSignal(finding), finding.getSubjectExternalId());
        } catch (RuntimeException e) {
            log.error("[anomaly] журнальная строка не заведена code={}", finding.getCode(), e);
        }
    }

    /**
     * Сигнал наблюдения — всегда мягкий: на первом тике реакции нет, и
     * критичность отчёта производна от состава реакции, а не от того,
     * какой она станет при подтверждении.
     */
    private HoldSignal observationSignal(AnomalyFinding finding) {
        return HoldScope.EXCHANGE.equals(finding.getScope())
                ? HoldSignal.exchangeJournal(finding.getCode())
                : HoldSignal.instrumentJournal(finding.getCode());
    }

    private HoldSignal signalOf(AnomalyFinding finding) {
        if (HoldScope.EXCHANGE.equals(finding.getScope())) {
            return isTrue(finding.tearsDownRisk())
                    ? HoldSignal.exchange(finding.getCode())
                    : HoldSignal.exchangeSoft(finding.getCode());
        }
        return isTrue(finding.tearsDownRisk())
                ? HoldSignal.instrument(finding.getCode())
                : HoldSignal.instrumentSoft(finding.getCode());
    }

    private Long instrumentId(AnomalyFinding finding) {
        return nonNull(finding.getInstrument()) ? finding.getInstrument().getId() : null;
    }
}
