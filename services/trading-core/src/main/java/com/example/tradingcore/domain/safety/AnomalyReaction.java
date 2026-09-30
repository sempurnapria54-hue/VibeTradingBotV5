package com.example.tradingcore.domain.safety;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.config.AnomalyReportProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.AnomalyReportDataService;
import com.example.tradingcore.util.Constants;
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
 * <p><b>Носитель подтверждения — серия на стоящем отчёте, а не счётчик
 * в памяти.</b> Детектор с гистерезисом в два тика на первом тике заводит
 * наблюдательную строку, отмечает на ней момент наблюдения и ступени не
 * запрашивает; на следующем признак считается подтверждённым, если серия
 * этой строки жива — каждое наблюдение её продлевает, а полный проход, в
 * котором признака не было, прерывает. Durable-факт «признак наблюдался
 * прошлым проходом» переживает рестарт — в отличие от памяти инстанса,
 * которая обнуляется ровно в аварии, когда рестарты и происходят.
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
     *
     * <p>Наблюдённые строки находки едут контекстом: по нему отчёт
     * собирает внешний снимок на любой из троп — журнальной, мягкой и
     * полной, — не читая площадку второй раз.
     */
    public void apply(AnomalyFinding finding, ExchangeAccount account) {
        DealContext context = DealContext.builder()
                .exchangeAccount(account)
                .instrument(finding.getInstrument())
                .externalObservation(finding.getExternalObservation())
                .build();
        if (isTrue(reactsOnFirstSight(finding))) {
            holdService.raise(signalOf(finding), context);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Boolean confirmed = confirmed(finding, account, now);
        if (isFalse(confirmed)) {
            journal(finding, context);
        }
        observe(finding, account, now);
        if (isFalse(confirmed) || isTrue(finding.getJournalOnly())) {
            return;
        }
        holdService.raise(signalOf(finding), context);
    }

    /**
     * Прервать серии счёта, которых полный проход не продлил: признак, не
     * наблюдённый проходом, начатым в {@code passStartedAt}, подряд уже не
     * держится. Зовёт проход только ПОЛНЫЙ — на неполном детекторы молчат,
     * и молчание чистым не является.
     */
    public void breakUnobservedSeries(ExchangeAccount account, OffsetDateTime passStartedAt) {
        reportDataService.breakSeries(account.getId(), passStartedAt);
    }

    /**
     * Реакция на контролируемое исключение границы, пришедшее на чтении среза
     * счёта: безусловная биржевая ступень 2 тем же кодом, что у всякой тропы
     * (docs/rules/controlled-exchange-exceptions.md §«Реакция — безусловная
     * биржевая ступень 2»).
     *
     * <p><b>Гистерезиса здесь нет, и это не пропуск.</b> Признак — не
     * расхождение БД с биржей, которое производит гонка чтения, а ответ
     * площадки, нарушивший контракт, — наш незавершённый ход его не
     * производит. Сущности и сделки у отказа среза нет: из состава реакции
     * остаётся её счётная часть, и контекст несёт только счёт.
     */
    public void controlledFailure(ExchangeAccount account) {
        holdService.raise(HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_CONTROLLED_FAILURE),
                DealContext.builder().exchangeAccount(account).build());
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
     * Признак подтверждён: у наблюдательной строки по этому ключу, стоящей
     * в окне наблюдения, серия жива, и последнее её наблюдение не моложе
     * минимального возраста подтверждения.
     *
     * <p><b>Серия, а не стоящая строка.</b> Строка стои́т окно целиком и
     * чистого прохода между наблюдениями не видит: признак, замеченный,
     * пропавший и вернувшийся в пределах окна, подтверждался бы сразу. Серию
     * полный проход без признака прерывает (§«Такт и гистерезис»).
     *
     * <p><b>Верхняя граница обязательна.</b> Без неё подтверждением служит
     * наблюдение того же или смежного прохода секундами раньше, а гонка
     * чтения, против которой гистерезис заведён, живёт такт.
     *
     * <p><b>Гейт стои́т и перед журнальной находкой.</b> Второй строки он
     * ей не даёт вместе с дедупом писателя отчёта, который строку, стоящую
     * в окне, не задваивает.
     */
    private Boolean confirmed(AnomalyFinding finding, ExchangeAccount account, OffsetDateTime now) {
        return reportDataService.existsSeries(account.getId(), instrumentId(finding),
                finding.getSubjectExternalId(), finding.getCode(),
                AnomalyReport.Severity.NON_CRITICAL,
                now.minus(reportProperties.getObservationWindow()),
                now.minus(jobProperties.getConfirmationMinAge()));
    }

    /**
     * Продлить серию стоящей строки моментом этого наблюдения — той, что
     * заведена сейчас, либо той, что уже стои́т в окне. Отказ записи
     * реакцию не гейтит: непродлённая серия подтверждения не даст, то есть
     * ошибается в сторону пропуска, а не ложного снятия риска.
     */
    private void observe(AnomalyFinding finding, ExchangeAccount account, OffsetDateTime now) {
        try {
            reportDataService.markObserved(account.getId(), instrumentId(finding),
                    finding.getSubjectExternalId(), finding.getCode(),
                    AnomalyReport.Severity.NON_CRITICAL,
                    now.minus(reportProperties.getObservationWindow()), now);
        } catch (RuntimeException e) {
            log.error("Anomaly observation series is not extended code={}", finding.getCode(), e);
        }
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
