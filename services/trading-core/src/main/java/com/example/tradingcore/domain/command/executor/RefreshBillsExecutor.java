package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealCashFlowDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.util.Constants;
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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Добыча движений средств окна сделки: конвейер «свежий эндпоинт →
 * архив», приземление строк разбивки с дедупом, резолв категории по
 * отображению контура площадки, линковка предикатом окна и курс чужой
 * валюты лестницей огрубления — всё той же транзакцией, что и строка
 * (docs/components/RefreshBillsExecutor.md, docs/models/mapping/DealCashFlow.md,
 * docs/spec/cash-flow-linkage.json).
 *
 * <p><b>Предикат остановки звена</b> — «курс применён у всех строк
 * блокирующей области» (docs/spec/deal-result.json, {@code rateBlocking}):
 * живая строка области без курса означает, что факт не добыт, — звено не
 * завершается и повторяется по бюджету попыток своей строки исполнения.
 * Догон курса идёт ЭТИМ ЖЕ проходом по строкам прежних проходов, то есть
 * внутри бюджета, а не помимо него.
 *
 * <p><b>Исход «не добыто» — третий, а не отказ.</b> Площадка ответила и
 * повода для радиусной реакции не дала; классификации у него нет
 * намеренно (docs/rules/command-lifecycle.md §Правило, клауза 2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshBillsExecutor implements CommandExecutor {

    /** Статусы курса, при которых строка ещё ждёт лестницу: операнд области блокировки и догона. */
    private static final Set<DealCashFlow.RateStatus> PENDING_RATE_STATUSES =
            EnumSet.of(DealCashFlow.RateStatus.RATE_UNAVAILABLE,
                    DealCashFlow.RateStatus.SETTLE_CURRENCY_UNAVAILABLE);

    private final DealDataService dealDataService;
    private final DealCashFlowDataService dealCashFlowDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final InstrumentDataService instrumentDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final ExchangeContourProperties exchangeContourProperties;
    private final AnomalyReportService anomalyReportService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_BILLS_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        ExchangeAccount account = dealContext.getExchangeAccount();
        OffsetDateTime lowerBound = deal.billsWindowLowerBound();
        if (isNull(lowerBound)) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                    "Bills window is not addressable: neither the boundary nor its surrogate is set for deal "
                            + deal.getId());
        }
        ExchangeContourProperties.Contour contour =
                exchangeContourProperties.forExchange(account.getExchangeCode());
        OffsetDateTime sourceTime = exchangeOperationsClient.getServerTime();
        List<DealCashFlow> fetched = fetchWindow(account.getInternalId(), lowerBound, sourceTime, contour);

        // Перерезолв корзины по ТЕКУЩЕМУ отображению идёт первым: держатель
        // пополняет отображение по наблюдённому перечню, и строка, севшая в
        // корзину прежним проходом, обязана из неё выйти — иначе она навсегда
        // остаётся вне области сверки. Он же и есть тропа СНЯТИЯ состояния.
        reclassifyUnclassified(deal.getId(), contour);
        Boolean basketStood = dealCashFlowDataService.unclassifiedBasketStands(account.getId());

        List<Long> persistedNow = new ArrayList<>();
        List<DealCashFlow> unclassifiedNow = new ArrayList<>();
        for (DealCashFlow flow : fetched) {
            persistRow(flow, dealContext, contour, lowerBound, sourceTime, persistedNow, unclassifiedNow);
        }
        // Отчёт заводится ровно на ВОЗНИКНОВЕНИЕ состояния: корзина стояла
        // непустой — состояние держится, второй строки по тому же ключу нет;
        // была пуста и наполнилась — состояние возникло заново.
        if (isFalse(basketStood) && isNotEmpty(unclassifiedNow)) {
            reportUnclassified(dealContext, unclassifiedNow.getFirst());
        }
        dealDataService.advanceBillsFetchedThrough(deal.getId(), sourceTime);

        Long blocking = retryAndCountBlocking(deal.getId(), dealContext, contour, persistedNow);
        if (blocking > 0) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Rate not applied for " + blocking + " blocking cash flow rows of deal " + deal.getId());
        }
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Свежий эндпоинт всегда; архив — когда нижняя граница окна старше его
     * глубины. Слияние по идентификатору записи: страницы двух эндпоинтов
     * могут перекрываться.
     *
     * <p><b>Глубина — величина контура, а не константа кода:</b> её же
     * читает признак полноты разбивки, и второй носитель одного числа
     * разошёлся бы с первым (docs/models/domain/core/Exchange.md
     * §«Настройки контура биржи»).
     *
     * <p>Глубже архива система не ходит — это задел, а не отсутствующая у
     * источника возможность (docs/components/RefreshBillsExecutor.md
     * §«Глубина конвейера»).
     */
    private List<DealCashFlow> fetchWindow(String accountInternalId, OffsetDateTime lowerBound,
                                           OffsetDateTime sourceTime,
                                           ExchangeContourProperties.Contour contour) {
        Map<String, DealCashFlow> unique = new LinkedHashMap<>();
        addUnique(unique, exchangeOperationsClient.getBills(accountInternalId, lowerBound, sourceTime));
        if (lowerBound.isBefore(sourceTime.minus(Duration.ofDays(contour.getBillsFreshDepthDays())))) {
            addUnique(unique, exchangeOperationsClient.getBillsArchive(accountInternalId, lowerBound, sourceTime));
        }
        return new ArrayList<>(unique.values());
    }

    private void addUnique(Map<String, DealCashFlow> target, List<DealCashFlow> flows) {
        flows.forEach(flow -> {
            if (isNotBlank(flow.getExternalBillId())) {
                target.putIfAbsent(flow.getExternalBillId(), flow);
            }
        });
    }

    /**
     * Приземление одной строки: дедуп по ключу идемпотентности, категория,
     * линковка и курс — одной транзакцией. Строка, уже стоящая по ключу,
     * пропускается: повторный проход перечитывает окно и новых строк не
     * порождает.
     */
    private void persistRow(DealCashFlow flow, DealContext dealContext,
                            ExchangeContourProperties.Contour contour, OffsetDateTime lowerBound,
                            OffsetDateTime sourceTime, List<Long> persistedNow,
                            List<DealCashFlow> unclassifiedNow) {
        Long accountId = dealContext.getExchangeAccount().getId();
        if (isTrue(dealCashFlowDataService.exists(accountId, flow.getExternalBillId()))) {
            return;
        }
        flow.setExchangeAccountId(accountId);
        resolveCategory(flow, contour);
        if (DealCashFlow.CashFlowCategory.OTHER.equals(flow.getCategory())) {
            unclassifiedNow.add(flow);
        }
        if (isTrue(linksToDeal(flow, dealContext, lowerBound, sourceTime))) {
            flow.setDealId(dealContext.getDeal().getId());
        }
        applyRateLadder(flow, dealContext);
        persistedNow.add(dealCashFlowDataService.save(flow).getId());
    }

    /**
     * Резолв категории — по ТИПУ операции, от частного к общему; тип вне
     * отображения садится в принимающую корзину
     * (docs/models/mapping/DealCashFlow.md §«Резолв категории»). Отчёт
     * отсюда не заводится: он про СОСТОЯНИЕ корзины, а не про строку.
     */
    private void resolveCategory(DealCashFlow flow, ExchangeContourProperties.Contour contour) {
        flow.setCategory(contour.resolveCategory(flow.getExternalType(), flow.getExternalSubType())
                .orElse(DealCashFlow.CashFlowCategory.OTHER));
    }

    /**
     * Перерезолв строк из принимающей корзины по ТЕКУЩЕМУ отображению.
     * Отображение пополняет держатель по наблюдённому перечню, и без
     * перерезолва строка, севшая в корзину до пополнения, оставалась бы вне
     * области сверки навсегда — то есть искажала бы её молча.
     *
     * <p>Это же и тропа СНЯТИЯ состояния: опустевшая корзина — операнд,
     * которого дедупу журнального отчёта прежде не давал никто.
     */
    private void reclassifyUnclassified(Long dealId, ExchangeContourProperties.Contour contour) {
        for (DealCashFlow flow : dealCashFlowDataService.findUnclassifiedByDeal(dealId)) {
            contour.resolveCategory(flow.getExternalType(), flow.getExternalSubType())
                    .ifPresent(category -> {
                        flow.setCategory(category);
                        dealCashFlowDataService.save(flow);
                    });
        }
    }

    /**
     * Журнальный отчёт о непустой корзине. Дедуп — по СТОЯЩЕМУ состоянию
     * объекта радиуса, а не по статусу отчёта: наличие прежней строки по
     * тому же ключу возникновение состояния заново не гасит
     * (docs/rules/error-handling-policy.md).
     *
     * <p><b>Журнал реакции не гейтит:</b> сбой записи приземлившиеся строки
     * не валит — иначе отказ носителя наблюдаемости отнимал бы добытый
     * факт. Дом клаузы — docs/rules/error-handling-policy.md §«Отказ
     * журнального носителя реакцию не гейтит».
     */
    private void reportUnclassified(DealContext dealContext, DealCashFlow flow) {
        log.warn("UNCLASSIFIED_CASH_FLOW type={} subType={} billId={} — basket is not empty",
                flow.getExternalType(), flow.getExternalSubType(), flow.getExternalBillId());
        try {
            anomalyReportService.journalState(dealContext,
                    HoldSignal.exchangeAccountJournal(Constants.Hold.UNCLASSIFIED_CASH_FLOW), null);
        } catch (RuntimeException e) {
            log.error("Journal UNCLASSIFIED_CASH_FLOW failed dealId={}", dealContext.getDeal().getId(), e);
        }
    }

    /**
     * Предикат линковки (docs/spec/cash-flow-linkage.json,
     * {@code linksToDeal}): нетерминальная сделка, тот же инструмент
     * (пустая привязка в множество не входит), время события в окне
     * [нижняя граница; время источника]. Ось счёта выполнена построением —
     * окно читается по счёту контекста.
     */
    private Boolean linksToDeal(DealCashFlow flow, DealContext dealContext, OffsetDateTime lowerBound,
                                OffsetDateTime sourceTime) {
        if (isTrue(dealContext.getDeal().isTerminal())) {
            return false;
        }
        if (isBlank(flow.getExternalInstrumentId())
                || isFalse(Objects.equals(flow.getExternalInstrumentId(),
                        dealContext.getInstrument().getExternalId()))) {
            return false;
        }
        OffsetDateTime eventAt = flow.getExternalCreatedAt();
        return nonNull(eventAt) && isFalse(eventAt.isBefore(lowerBound)) && isFalse(eventAt.isAfter(sourceTime));
    }

    /**
     * Лестница огрубления разрешения, ступени 0-4
     * (docs/components/RefreshBillsExecutor.md §«Лестница огрубления
     * разрешения»): расчётная валюта не резолвится → курс не ищется вовсе;
     * валюта движения равна расчётной → курс не нужен; секундная свеча
     * момента; минутная свеча момента; курса нет.
     *
     * <p><b>Порядок ступеней 0 и 1 не переставим:</b> сравнение валют
     * требует резолвленной расчётной, поэтому её проверка идёт до
     * сравнения. Иначе пустота читалась бы как «курс не нужен» — ошибка в
     * разрешающую сторону: она сняла бы блокировку итога и опубликовала
     * его по неполным данным.
     *
     * <p><b>Применённое разрешение записывается</b> координатой
     * таймфрейма: иначе постфактум не отличить точный пересчёт от
     * огрублённого, а направление смещения минутной свечи относительно
     * секундной неизвестно.
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
     * Расчётная валюта — ИНСТРУМЕНТА строки: у строки инструмента сделки
     * берётся из контекста прохода, у чужого — проекцией каталога; строка
     * без инструментной привязки и инструмент вне проекции валюты не имеют.
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
                .findSettlementCurrency(dealContext.getExchangeAccount().getExchangeCode(),
                        flow.getExternalInstrumentId())
                .orElse(null);
    }

    /**
     * Свеча индекса пары котировки, НАКРЫВАЮЩАЯ момент операции; иначе
     * ступень не применяется. Проверка накрытия обязательна: источник
     * отдаёт ближайшую доступную, и без неё курс мог бы приехать с бара,
     * закончившегося до операции.
     */
    private Boolean applyIndexRate(DealCashFlow flow, String indexInstrumentId, TimeFrame timeFrame) {
        Candle candle = exchangeOperationsClient.getIndexCandleAt(indexInstrumentId, timeFrame,
                flow.getExternalCreatedAt());
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

    /**
     * Догон курса и счёт оставшихся блокирующих строк. Строки прежних
     * проходов пробуют лестницу заново; строки ЭТОГО прохода повторно не
     * гоняются — их лестница только что отработала.
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
     * Область блокировки итога (docs/spec/deal-result.json,
     * {@code rateBlocking}): непогашенный статус курса у строки, не
     * выведенной из области сверки списком исключений площадки.
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
