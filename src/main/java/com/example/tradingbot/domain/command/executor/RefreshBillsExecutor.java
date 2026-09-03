package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.config.ExchangeContourProperties;
import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.other.external_snapshot.DealCashFlowExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.domain.safety.AnomalyReport;
import com.example.tradingbot.domain.safety.AnomalyReportService;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.DealCashFlowMapper;
import com.example.tradingbot.mapping.TimeFrameMapper;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealCashFlowDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.util.Constants;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет REFRESH_BILLS: грузит движения средств окна сделки конвейером
 * «свежий эндпоинт → архив» (пагинация внутри границы), персистит строки
 * разбивки с дедупом по ключу идемпотентности, резолвит категорию по
 * отображению контура биржи, линкует к сделке предикатом окна и
 * проставляет курс чужой валюты лестницей огрубления — той же
 * транзакцией, что и строку. См. docs/components/RefreshBillsExecutor.md,
 * docs/models/mapping/DealCashFlow.md, docs/spec/cash-flow-linkage.json.
 *
 * <p><b>Предикат остановки звена</b> — «курс применён у всех строк
 * блокирующей области» (docs/spec/deal-result.json, {@code rateBlocking}):
 * живая строка области без курса означает, что факт не добыт, — звено не
 * завершается и повторяется по бюджету действия
 * (docs/rules/command-lifecycle.md). Догон курса идёт этим же проходом по
 * строкам прежних проходов.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshBillsExecutor implements CommandExecutor {

    /** Глубина свежего эндпоинта bills у источника, дней (контракт account-bills). */
    private static final long FRESH_ENDPOINT_DEPTH_DAYS = 7L;

    /** Статусы курса, при которых строка ещё ждёт лестницу (операнд rateBlocking и догона). */
    private static final Set<DealCashFlow.RateStatus> PENDING_RATE_STATUSES =
            EnumSet.of(DealCashFlow.RateStatus.RATE_UNAVAILABLE,
                    DealCashFlow.RateStatus.SETTLE_CURRENCY_UNAVAILABLE);

    private final DealDataService dealDataService;
    private final DealCashFlowDataService dealCashFlowDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final InstrumentDataService instrumentDataService;
    private final IntegrationService integrationService;
    private final DealCashFlowMapper dealCashFlowMapper;
    private final TimeFrameMapper timeFrameMapper;
    private final ExchangeContourProperties exchangeContourProperties;
    private final AnomalyReportService anomalyReportService;
    private final AnomalyReportDataService anomalyReportDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_BILLS;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        OffsetDateTime lowerBound = nonNull(deal.getBillsWindowBegin())
                ? deal.getBillsWindowBegin()
                : deal.getExternalCreatedAt();
        if (isNull(lowerBound)) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                    "окно линковки не адресуемо: ни границы, ни суррогата — писатель externalCreatedAt не отработал"
                            + " (docs/spec/cash-flow-linkage.json §boundResolved)");
        }
        OffsetDateTime sourceTime = integrationService.getServerTime();
        List<DealCashFlowExternalSnapshot> snapshots = fetchWindow(lowerBound, sourceTime);
        Optional<String> invalid = firstInvalid(snapshots);
        if (invalid.isPresent()) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.EXCHANGE_ERROR, invalid.get());
        }

        ExchangeContourProperties.Contour contour =
                exchangeContourProperties.forExchange(dealContext.getExchange().getName());
        List<Long> persistedNow = new ArrayList<>();
        for (DealCashFlowExternalSnapshot snapshot : snapshots) {
            persistRow(snapshot, dealContext, contour, lowerBound, sourceTime, persistedNow);
        }
        dealDataService.advanceBillsFetchedThrough(deal.getId(), sourceTime);

        Long blocking = retryAndCountBlocking(deal.getId(), dealContext, contour, persistedNow);
        if (blocking > 0) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.EXCHANGE_ERROR,
                    "курс не применён у " + blocking + " строк блокирующей области — факт не добыт,"
                            + " звено не завершено (docs/spec/deal-result.json §rateBlocking)");
        }
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    // ------------------------------------------------------------------
    // Конвейер добычи
    // ------------------------------------------------------------------

    /**
     * Свежий эндпоинт всегда; архив — когда нижняя граница окна старше
     * его глубины. Слияние по идентификатору записи: страницы двух
     * эндпоинтов могут перекрываться.
     */
    private List<DealCashFlowExternalSnapshot> fetchWindow(OffsetDateTime lowerBound, OffsetDateTime sourceTime) {
        Map<String, DealCashFlowExternalSnapshot> unique = new LinkedHashMap<>();
        addUnique(unique, integrationService.getBills(lowerBound, sourceTime));
        if (lowerBound.isBefore(sourceTime.minus(Duration.ofDays(FRESH_ENDPOINT_DEPTH_DAYS)))) {
            addUnique(unique, integrationService.getBillsArchive(lowerBound, sourceTime));
        }
        return new ArrayList<>(unique.values());
    }

    private void addUnique(Map<String, DealCashFlowExternalSnapshot> target,
                           List<DealCashFlowExternalSnapshot> snapshots) {
        snapshots.forEach(snapshot -> {
            if (isNotBlank(snapshot.getExternalBillId())) {
                target.putIfAbsent(snapshot.getExternalBillId(), snapshot);
            }
        });
    }

    /** Граница разбора: обязательные поля записи непусты, иначе звено падает громко. */
    private Optional<String> firstInvalid(List<DealCashFlowExternalSnapshot> snapshots) {
        for (DealCashFlowExternalSnapshot snapshot : snapshots) {
            if (isBlank(snapshot.getExternalBillId()) || isNull(snapshot.getAmount())
                    || isBlank(snapshot.getCcy()) || isBlank(snapshot.getExternalType())
                    || isNull(snapshot.getExternalCreatedAt())) {
                return Optional.of("запись движения не проходит границу разбора (обязательное поле пусто):"
                        + " billId=" + snapshot.getExternalBillId());
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Строка разбивки: категория, линковка, курс
    // ------------------------------------------------------------------

    private void persistRow(DealCashFlowExternalSnapshot snapshot, DealContext dealContext,
                            ExchangeContourProperties.Contour contour, OffsetDateTime lowerBound,
                            OffsetDateTime sourceTime, List<Long> persistedNow) {
        Long exchangeId = dealContext.getExchange().getId();
        if (isTrue(dealCashFlowDataService.exists(exchangeId, snapshot.getExternalBillId()))) {
            return;
        }
        DealCashFlow flow = dealCashFlowMapper.snapshotToDomain(snapshot);
        flow.setExchangeId(exchangeId);
        resolveCategory(flow, contour, dealContext);
        if (isTrue(linksToDeal(flow, dealContext, lowerBound, sourceTime))) {
            flow.setDealId(dealContext.getDeal().getId());
        }
        applyRateLadder(flow, dealContext);
        persistedNow.add(dealCashFlowDataService.save(flow).getId());
    }

    /**
     * Резолв категории — по типу операции, от частного к общему; тип вне
     * отображения садится в принимающую корзину OTHER и поднимает
     * журнальный STATE-отчёт нераспознанного движения с дедупом по
     * стоящему состоянию (docs/models/mapping/DealCashFlow.md
     * §«Резолв категории»).
     */
    private void resolveCategory(DealCashFlow flow, ExchangeContourProperties.Contour contour,
                                 DealContext dealContext) {
        Optional<DealCashFlow.CashFlowCategory> resolved =
                contour.resolveCategory(flow.getExternalType(), flow.getExternalSubType());
        if (resolved.isPresent()) {
            flow.setCategory(resolved.get());
            return;
        }
        flow.setCategory(DealCashFlow.CashFlowCategory.OTHER);
        reportUnclassified(dealContext, flow);
    }

    private void reportUnclassified(DealContext dealContext, DealCashFlow flow) {
        Long exchangeId = dealContext.getExchange().getId();
        if (isTrue(anomalyReportDataService.existsByKey(exchangeId, Constants.Hold.UNCLASSIFIED_CASH_FLOW,
                AnomalyReport.Severity.NON_CRITICAL))) {
            return;
        }
        log.warn("[bills] нераспознанное движение: type={} subType={} billId={} — корзина OTHER непуста",
                flow.getExternalType(), flow.getExternalSubType(), flow.getExternalBillId());
        anomalyReportService.journal(dealContext,
                HoldSignal.exchangeJournal(Constants.Hold.UNCLASSIFIED_CASH_FLOW));
    }

    /**
     * Предикат линковки (docs/spec/cash-flow-linkage.json §linksToDeal):
     * нетерминальная сделка, тот же инструмент (пустая привязка в
     * множество не входит), время события в окне [нижняя граница; время
     * источника]. Ось биржи выполнена построением — окно читается по
     * счёту биржи контекста.
     */
    private Boolean linksToDeal(DealCashFlow flow, DealContext dealContext,
                                OffsetDateTime lowerBound, OffsetDateTime sourceTime) {
        if (isTrue(dealContext.getDeal().isTerminal())) {
            return false;
        }
        if (isBlank(flow.getExternalInstrumentId())
                || isFalse(Objects.equals(flow.getExternalInstrumentId(),
                        dealContext.getInstrument().getExternalId()))) {
            return false;
        }
        OffsetDateTime eventAt = flow.getExternalCreatedAt();
        return isFalse(eventAt.isBefore(lowerBound)) && isFalse(eventAt.isAfter(sourceTime));
    }

    // ------------------------------------------------------------------
    // Лестница огрубления разрешения курса
    // ------------------------------------------------------------------

    /**
     * Ступени 0-4 (docs/components/RefreshBillsExecutor.md §«Лестница
     * огрубления разрешения»): расчётная валюта не резолвится → курс не
     * ищется вовсе; валюта движения равна расчётной → курс не нужен;
     * секундная свеча момента; минутная свеча момента; курса нет.
     * Применённое разрешение записывается координатой таймфрейма.
     */
    private void applyRateLadder(DealCashFlow flow, DealContext dealContext) {
        String settleCurrency = resolveSettleCurrency(flow, dealContext);
        if (isBlank(settleCurrency)) {
            flow.setRateStatus(DealCashFlow.RateStatus.SETTLE_CURRENCY_UNAVAILABLE);
            return;
        }
        if (Objects.equals(flow.getCcy(), settleCurrency)) {
            flow.setRateStatus(DealCashFlow.RateStatus.NOT_REQUIRED);
            return;
        }
        String indexInstrumentId = flow.getCcy() + "-" + settleCurrency;
        if (isTrue(applyIndexRate(flow, indexInstrumentId, TimeFrame.ONE_SECOND))) {
            return;
        }
        if (isTrue(applyIndexRate(flow, indexInstrumentId, TimeFrame.ONE_MINUTE))) {
            return;
        }
        flow.setRateStatus(DealCashFlow.RateStatus.RATE_UNAVAILABLE);
    }

    /**
     * Расчётная валюта — инструмента строки: у строки инструмента сделки
     * берётся из контекста, у чужого — проекцией из каталога; строка без
     * инструментной привязки и инструмент вне каталога валюты не имеют.
     */
    private String resolveSettleCurrency(DealCashFlow flow, DealContext dealContext) {
        Instrument instrument = dealContext.getInstrument();
        if (Objects.equals(flow.getExternalInstrumentId(), instrument.getExternalId())) {
            return instrument.getExternalSettlementCurrency();
        }
        if (isBlank(flow.getExternalInstrumentId())) {
            return null;
        }
        return instrumentDataService
                .findSettlementCurrency(flow.getExchangeId(), flow.getExternalInstrumentId())
                .orElse(null);
    }

    /** Свеча индекса пары котировки, накрывающая момент операции; иначе ступень не применяется. */
    private Boolean applyIndexRate(DealCashFlow flow, String indexInstrumentId, TimeFrame timeFrame) {
        CandleExternalSnapshot candle = integrationService.getIndexCandleAt(indexInstrumentId,
                timeFrameMapper.domainToOkx(timeFrame), flow.getExternalCreatedAt());
        if (isNull(candle) || isNull(candle.getOpenTimestamp()) || isNull(candle.getClose())) {
            return false;
        }
        long momentMillis = flow.getExternalCreatedAt().toInstant().toEpochMilli();
        if (candle.getOpenTimestamp() + timeFrame.getDurationMillis() <= momentMillis) {
            return false;
        }
        flow.setAppliedRate(candle.getClose());
        flow.setRateStatus(DealCashFlow.RateStatus.APPLIED);
        flow.setAppliedRateCandleInstrument(indexInstrumentId);
        flow.setAppliedRateCandleTimeframe(timeFrame);
        flow.setAppliedRateCandleOpenTime(
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTimestamp()), ZoneOffset.UTC));
        return true;
    }

    // ------------------------------------------------------------------
    // Догон курса и предикат остановки звена
    // ------------------------------------------------------------------

    /**
     * Строки прежних проходов, ждущие курс, пробуют лестницу заново;
     * затем считаются оставшиеся блокирующие (rateBlocking:
     * непогашенный статус курса у неисключённой строки). Строки этого же
     * прохода повторно не гоняются — их лестница только что отработала.
     */
    private Long retryAndCountBlocking(Long dealId, DealContext dealContext,
                                       ExchangeContourProperties.Contour contour, List<Long> persistedNow) {
        long blocking = 0L;
        for (DealCashFlow flow : dealCashFlowDataService.findByDeal(dealId)) {
            if (isFalse(PENDING_RATE_STATUSES.contains(flow.getRateStatus()))) {
                continue;
            }
            if (isFalse(persistedNow.contains(flow.getId()))) {
                applyRateLadder(flow, dealContext);
                dealCashFlowDataService.save(flow);
            }
            if (isTrue(rateBlocks(flow, contour))) {
                blocking++;
            }
        }
        return blocking;
    }

    /**
     * rateBlocking (docs/spec/deal-result.json): статусы ожидания курса
     * кодируют попадание в блокирующую область (чужая валюта без курса
     * либо нерезолвленная расчётная), исключённые биржей типы из области
     * выведены.
     */
    private Boolean rateBlocks(DealCashFlow flow, ExchangeContourProperties.Contour contour) {
        if (isFalse(PENDING_RATE_STATUSES.contains(flow.getRateStatus()))) {
            return false;
        }
        return isFalse(contour.excludesFromReconciliation(flow.getExternalType(), flow.getExternalSubType()));
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
