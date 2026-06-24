package com.example.tradingbot.integration.service;

import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.core.fill.external_snapshot.FillExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalRulesExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.domain.model.trade.market_price.external_snapshot.MarketPriceDataExternalSnapshot;
import java.util.List;

/**
 * Граница интеграции с биржей / adapter-layer: сервисы ходят на биржу
 * только через него, наружу выходят только нормализованные
 * {@code *ExternalSnapshot} (docs/components/ClientService.md,
 * docs/rules/raw-exchange-dto-boundary.md).
 *
 * <p>Nullable contract read/refresh: snapshot найден → snapshot;
 * успешно, но не найден → {@code null}; ошибка API/parse/invariant →
 * exception. Маршрутизация по биржам (multi-exchange) — за пределами
 * шага 1 (одна биржа OKX).
 */
public interface IntegrationService {

    /**
     * Спецификация инструмента → нормализованный снапшот.
     *
     * @return снапшот; {@code null} — инструмент на бирже не найден.
     */
    InstrumentExternalSnapshot getInstrument(String externalInstrumentId, String externalInstrumentType);

    /**
     * Внешние правила инструмента (спецификация: tick/lot/min size,
     * per-order max sizes, ctVal, max leverage, торгуемость) →
     * нормализованный снапшот. Тот же endpoint спецификации, что и
     * {@link #getInstrument}, но граничный снапшот несёт ограничители для
     * риск-преконтроля.
     *
     * @return снапшот; {@code null} — инструмент на бирже не найден.
     */
    InstrumentExternalRulesExternalSnapshot getInstrumentRules(String externalInstrumentId,
                                                               String externalInstrumentType);

    /**
     * Текущие цены инструмента (last/bid/ask + время тикера) →
     * нормализованный снапшот по REST ticker. Не persisted; нужен прямо
     * перед расчётом параметров действия.
     *
     * @return снапшот; {@code null} — тикера на бирже нет.
     */
    MarketPriceDataExternalSnapshot getMarketPriceData(String externalInstrumentId);

    /**
     * История свечей (пагинация назад).
     *
     * @param afterMillis свечи строго старше этого ts (ms); {@code null} — с самых свежих.
     * @return снапшоты свечей (пустой список — данных нет / достигнуто начало истории).
     */
    List<CandleExternalSnapshot> getHistoryCandles(String externalInstrumentId, String externalBar,
                                                   Long afterMillis, Integer limit);

    /**
     * Последние свечи (докачка хвоста).
     *
     * @return снапшоты свечей (пустой список — данных нет).
     */
    List<CandleExternalSnapshot> getLatestCandles(String externalInstrumentId, String externalBar, Integer limit);

    /**
     * Ordinary order с биржи по {@code externalId} (предпочтительно) или
     * {@code internalId} (stable client id).
     *
     * @return снапшот; {@code null} — ордер на бирже не найден.
     */
    OrderExternalSnapshot getOrder(String externalInstrumentId, String externalId, String internalId);

    /**
     * Live/pending ordinary orders по инструменту (звено order
     * evidence-cycle).
     */
    List<OrderExternalSnapshot> getPendingOrders(String externalInstrumentId);

    /**
     * История ordinary orders по инструменту (звено order evidence-cycle).
     */
    List<OrderExternalSnapshot> getOrderHistory(String externalInstrumentId);

    /**
     * Standalone algo-order с биржи по {@code externalId} (algoId,
     * предпочтительно) или {@code internalId} (algoClOrdId).
     *
     * @return снапшот; {@code null} — algo-order на бирже не найден.
     */
    AlgoOrderExternalSnapshot getAlgoOrder(String externalInstrumentId, String externalId, String internalId);

    /**
     * Live/pending algo orders по инструменту и типу условия (звено algo
     * evidence-cycle; ordType резолвится из conditionType в adapter).
     */
    List<AlgoOrderExternalSnapshot> getPendingAlgoOrders(String externalInstrumentId,
                                                         AlgoOrder.ConditionType conditionType);

    /**
     * История algo orders по инструменту и типу условия (звено algo
     * evidence-cycle).
     */
    List<AlgoOrderExternalSnapshot> getAlgoOrderHistory(String externalInstrumentId,
                                                        AlgoOrder.ConditionType conditionType);

    /**
     * Позиция по инструменту с биржи.
     *
     * @return снапшот; {@code null} — позиции на бирже нет (успешный
     *         запрос; closed-on-exchange).
     */
    PositionExternalSnapshot getPosition(String externalInstrumentId);

    /**
     * Постановка ordinary order на биржу. ACK не runtime-truth:
     * подтверждение факта — через REFRESH_ORDER.
     *
     * @return нормализованный ACK биржи (принят/отклонён + ordId).
     */
    ExchangeAck placeOrder(Order order, String externalInstrumentId);

    /**
     * Отмена ordinary order на бирже (по externalId/internalId). ACK не
     * runtime-truth.
     *
     * @return нормализованный ACK биржи.
     */
    ExchangeAck cancelOrder(Order order, String externalInstrumentId);

    /**
     * Постановка standalone algo-order на биржу. ACK не runtime-truth.
     *
     * @return нормализованный ACK биржи (принят/отклонён + algoId).
     */
    ExchangeAck placeAlgoOrder(AlgoOrder algoOrder, String externalInstrumentId);

    /**
     * Отмена standalone algo-order на бирже. Cancel-endpoint ветвится по
     * семье algo (ordinary vs advance), выводимой из conditionType.
     *
     * @return нормализованный ACK биржи.
     */
    ExchangeAck cancelAlgoOrder(AlgoOrder algoOrder, String externalInstrumentId);

    /**
     * Закрытие позиции по инструменту (market reduce-only). ACK не
     * runtime-truth: полное закрытие подтверждает REFRESH_POSITION.
     *
     * @return нормализованный ACK биржи.
     */
    ExchangeAck closePosition(String externalInstrumentId, String settleCurrency);

    /**
     * Выставление рабочего плеча инструмента на бирже перед постановкой
     * entry-ордера (idempotent: совпадает с уже выставленным → биржа
     * подтверждает без изменения). См.
     * docs/components/PrecheckHandler.md (set-leverage перед постановкой).
     *
     * @return нормализованный ACK биржи (успех при code OK).
     */
    ExchangeAck setLeverage(String externalInstrumentId, Integer leverage);

    /**
     * Баланс аккаунта по settle currency. Normal null-контракт не
     * применяется: пустой/невалидный ответ — controlled error.
     *
     * @return нормализованный account-level снапшот баланса.
     */
    BalanceContainerExternalSnapshot getBalance(String settleCurrency);

    /**
     * Исполнения (fills) по инструменту, пагинация назад по billId.
     * RefreshFillsExecutor матчит их с Order/AlgoOrder и агрегирует.
     *
     * @param afterBillId якорь пагинации (billId); {@code null} — с самых свежих.
     * @return снапшоты fills (пустой список — нет исполнений в окне).
     */
    List<FillExternalSnapshot> getFills(String externalInstrumentId, String afterBillId, Integer limit);

    /**
     * История исполнений (fills, до 3 месяцев) — эскалация после
     * getFills (3 дня) в fills evidence-cycle.
     *
     * @return снапшоты fills (пустой список — нет исполнений в окне).
     */
    List<FillExternalSnapshot> getFillsHistory(String externalInstrumentId, String afterBillId, Integer limit);
}
