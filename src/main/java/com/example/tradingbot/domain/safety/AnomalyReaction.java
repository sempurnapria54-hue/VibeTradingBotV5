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
     * Применить реакцию по находке. Возвращает {@code true}, если ступень
     * запрошена, — {@code false} означает «признак записан, подтверждения
     * ждём следующим тиком».
     */
    public Boolean apply(AnomalyFinding finding, Exchange exchange) {
        DealContext context = DealContext.builder()
                .exchange(exchange)
                .instrument(finding.getInstrument())
                .build();
        if (isTrue(finding.getJournalOnly())) {
            journal(finding, context);
            return false;
        }
        if (isFalse(confirmed(finding, exchange))) {
            journal(finding, context);
            return false;
        }
        holdService.raise(signalOf(finding), context);
        return true;
    }

    /**
     * Признак подтверждён: гистерезиса нет вовсе либо наблюдательная
     * строка по этому ключу уже стои́т и заведена не раньше предыдущего
     * тика. Порог берётся из такта джобы с запасом на дрейф расписания.
     */
    private Boolean confirmed(AnomalyFinding finding, Exchange exchange) {
        if (WITHOUT_HYSTERESIS.equals(finding.getHysteresisTicks())) {
            return true;
        }
        OffsetDateTime since = OffsetDateTime.now().minus(properties.getObservationWindow());
        return reportDataService.existsStanding(exchange.getId(), instrumentId(finding),
                finding.getSubjectExternalId(), finding.getCode(),
                AnomalyReport.Severity.NON_CRITICAL, since);
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
