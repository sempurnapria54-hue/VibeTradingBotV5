package com.example.connector.okx.gateway;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.credentials.ExchangeCredentialsResolver;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.BalanceContainerMapper;
import com.example.connector.okx.mapping.CandleMapper;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.mapping.InstrumentExternalRulesMapper;
import com.example.connector.okx.mapping.InstrumentMapper;
import com.example.connector.okx.mapping.MarketPriceDataMapper;
import com.example.connector.okx.mapping.MarketSnapshotMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.mapping.TimeFrameMapper;
import com.example.connector.okx.mapping.TradeFeeRateMapper;
import com.example.connector.okx.snapshot.CandleExternalSnapshot;
import com.example.connector.okx.snapshot.MarketTickerExternalSnapshot;
import com.example.connector.okx.snapshot.InstrumentExternalSnapshot;
import com.example.connector.okx.snapshot.PositionCloseResultExternalSnapshot;
import com.example.connector.okx.source.OkxSourceReader;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Реализация входного контракта коннектора на площадке OKX.
 *
 * <p><b>Два слоя, и разделение между ними несущее.</b>
 * {@link OkxSourceReader} говорит с площадкой и отдаёт **граничные
 * снапшоты** — форму, в которой ответ впервые разобран и проверен. Этот
 * класс — граница **сервиса**: он резолвит ключи по счёту и переводит
 * снапшот в общую модель. Снапшот наружу не выходит: он несёт форму
 * источника, а у второй площадки она своя
 * ({@code docs/rules/raw-exchange-dto-boundary.md} — там же о том, что
 * границ две).
 *
 * <p><b>Доменных решений здесь не принимается.</b> Резолв внешнего
 * статуса в доменный — интерпретация факта, и её дом у вызывающего
 * ({@code docs/rules/external-status-resolution.md}); коннектор отдаёт
 * модель с тем, что видно из ответа, и не назначает ей исхода.
 */
@Service
@RequiredArgsConstructor
public class OkxExchangeGateway implements ExchangeGateway {

    private final OkxSourceReader reader;
    private final ExchangeCredentialsResolver credentialsResolver;
    private final OrderMapper orderMapper;
    private final AlgoOrderMapper algoOrderMapper;
    private final PositionMapper positionMapper;
    private final InstrumentMapper instrumentMapper;
    private final InstrumentExternalRulesMapper instrumentExternalRulesMapper;
    private final BalanceContainerMapper balanceContainerMapper;
    private final CandleMapper candleMapper;
    private final TimeFrameMapper timeFrameMapper;
    private final DealCashFlowMapper dealCashFlowMapper;
    private final TradeFeeRateMapper tradeFeeRateMapper;
    private final MarketPriceDataMapper marketPriceDataMapper;
    private final MarketSnapshotMapper marketSnapshotMapper;

    // --- команды площадке ------------------------------------------------

    @Override
    public ExchangeAck placeOrder(String accountInternalId, Order order, String externalInstrumentId) {
        return reader.placeOrder(keys(accountInternalId), order, externalInstrumentId);
    }

    @Override
    public ExchangeAck cancelOrder(String accountInternalId, Order order, String externalInstrumentId) {
        return reader.cancelOrder(keys(accountInternalId), order, externalInstrumentId);
    }

    @Override
    public ExchangeAck placeAlgoOrder(String accountInternalId, AlgoOrder algoOrder, String externalInstrumentId) {
        return reader.placeAlgoOrder(keys(accountInternalId), algoOrder, externalInstrumentId);
    }

    @Override
    public ExchangeAck cancelAlgoOrder(String accountInternalId, AlgoOrder algoOrder, String externalInstrumentId) {
        return reader.cancelAlgoOrder(keys(accountInternalId), algoOrder, externalInstrumentId);
    }

    @Override
    public ExchangeAck cancelAttachedProtection(String accountInternalId, AttachedAlgoOrder attached,
                                                String externalInstrumentId) {
        return reader.cancelAttachedProtection(keys(accountInternalId), attached, externalInstrumentId);
    }

    @Override
    public ExchangeAck closePosition(String accountInternalId, String externalInstrumentId, String settleCurrency) {
        return reader.closePosition(keys(accountInternalId), externalInstrumentId, settleCurrency);
    }

    @Override
    public ExchangeAck setLeverage(String accountInternalId, String externalInstrumentId, Integer leverage) {
        return reader.setLeverage(keys(accountInternalId), externalInstrumentId, leverage);
    }

    // --- добыча факта ------------------------------------------------------

    @Override
    public Order getOrder(String accountInternalId, String externalInstrumentId, String externalId,
                          String internalId) {
        return one(reader.getOrder(keys(accountInternalId), externalInstrumentId, externalId, internalId),
                orderMapper::snapshotToDomain);
    }

    @Override
    public List<Order> getPendingOrders(String accountInternalId, String externalInstrumentId) {
        return many(reader.getPendingOrders(keys(accountInternalId), externalInstrumentId),
                orderMapper::snapshotToDomain);
    }

    @Override
    public List<Order> getAllPendingOrders(String accountInternalId) {
        return many(reader.getAllPendingOrders(keys(accountInternalId)), orderMapper::snapshotToDomain);
    }

    @Override
    public List<Order> getOrderHistory(String accountInternalId, String externalInstrumentId) {
        return many(reader.getOrderHistory(keys(accountInternalId), externalInstrumentId),
                orderMapper::snapshotToDomain);
    }

    @Override
    public AlgoOrder getAlgoOrder(String accountInternalId, String externalInstrumentId, String externalId,
                                  String internalId) {
        return one(reader.getAlgoOrder(keys(accountInternalId), externalInstrumentId, externalId, internalId),
                algoOrderMapper::snapshotToDomain);
    }

    @Override
    public List<AlgoOrder> getAllPendingAlgoOrders(String accountInternalId) {
        return many(reader.getAllPendingAlgoOrders(keys(accountInternalId)), algoOrderMapper::snapshotToDomain);
    }

    @Override
    public List<AlgoOrder> getPendingAlgoOrders(String accountInternalId, String externalInstrumentId,
                                                AlgoOrder.ConditionType conditionType) {
        return many(reader.getPendingAlgoOrders(keys(accountInternalId), externalInstrumentId, conditionType),
                algoOrderMapper::snapshotToDomain);
    }

    @Override
    public List<AlgoOrder> getAlgoOrderHistory(String accountInternalId, String externalInstrumentId,
                                               AlgoOrder.ConditionType conditionType, String externalId) {
        return many(reader.getAlgoOrderHistory(keys(accountInternalId), externalInstrumentId, conditionType,
                externalId), algoOrderMapper::snapshotToDomain);
    }

    @Override
    public List<AttachedAlgoOrder> getPendingMaterializedProtections(String accountInternalId,
                                                                     String externalInstrumentId) {
        return many(reader.getPendingMaterializedProtections(keys(accountInternalId), externalInstrumentId),
                orderMapper::snapshotToDomain);
    }

    @Override
    public List<AttachedAlgoOrder> getMaterializedProtectionHistory(String accountInternalId,
                                                                    String externalInstrumentId,
                                                                    ProtectionHistoryLeg leg) {
        return many(reader.getMaterializedProtectionHistory(keys(accountInternalId), externalInstrumentId, leg),
                orderMapper::snapshotToDomain);
    }

    @Override
    public List<Position> getPositionCloseRecords(String accountInternalId, String externalInstrumentId,
                                                  OffsetDateTime windowBegin) {
        return many(reader.getPositionCloseRecords(keys(accountInternalId), externalInstrumentId, windowBegin),
                this::toClosedPosition);
    }

    @Override
    public Position getPosition(String accountInternalId, String externalInstrumentId) {
        return one(reader.getPosition(keys(accountInternalId), externalInstrumentId),
                positionMapper::snapshotToDomain);
    }

    @Override
    public List<Position> getPositions(String accountInternalId) {
        return many(reader.getPositions(keys(accountInternalId)), positionMapper::snapshotToDomain);
    }

    @Override
    public BalanceContainer getBalance(String accountInternalId, String settleCurrency) {
        return one(reader.getBalance(keys(accountInternalId), settleCurrency),
                balanceContainerMapper::snapshotToDomain);
    }

    @Override
    public List<DealCashFlow> getBills(String accountInternalId, OffsetDateTime begin, OffsetDateTime end) {
        return many(reader.getBills(keys(accountInternalId), begin, end), dealCashFlowMapper::snapshotToDomain);
    }

    @Override
    public List<DealCashFlow> getBillsArchive(String accountInternalId, OffsetDateTime begin, OffsetDateTime end) {
        return many(reader.getBillsArchive(keys(accountInternalId), begin, end),
                dealCashFlowMapper::snapshotToDomain);
    }

    @Override
    public List<TradeFeeRate> getTradeFeeRates(String accountInternalId, String externalInstrumentType) {
        return many(reader.getTradeFeeRates(keys(accountInternalId), externalInstrumentType),
                tradeFeeRateMapper::snapshotToDomain);
    }

    // --- публичные чтения --------------------------------------------------

    @Override
    public Instrument getInstrument(String externalInstrumentId, String externalInstrumentType) {
        return toInstrument(reader.getInstrument(externalInstrumentId, externalInstrumentType));
    }

    @Override
    public List<Instrument> getInstruments(String externalInstrumentType) {
        return many(reader.getInstruments(externalInstrumentType), this::toInstrument);
    }

    @Override
    public List<Candle> getLatestCandles(String externalInstrumentId, TimeFrame timeframe, Integer limit) {
        return closedOnly(reader.getLatestCandles(externalInstrumentId,
                timeFrameMapper.domainToOkx(timeframe), limit));
    }

    @Override
    public List<Candle> getHistoryCandles(String externalInstrumentId, TimeFrame timeframe, Long afterMillis,
                                          Integer limit) {
        return closedOnly(reader.getHistoryCandles(externalInstrumentId,
                timeFrameMapper.domainToOkx(timeframe), afterMillis, limit));
    }

    @Override
    public InstrumentExternalRules getInstrumentRules(String externalInstrumentId,
                                                      String externalInstrumentType) {
        return one(reader.getInstrumentRules(externalInstrumentId, externalInstrumentType),
                instrumentExternalRulesMapper::snapshotToDomain);
    }

    @Override
    public Candle getIndexCandleAt(String indexInstrumentId, TimeFrame timeframe, OffsetDateTime at) {
        return one(reader.getIndexCandleAt(indexInstrumentId, timeFrameMapper.domainToOkx(timeframe), at),
                candleMapper::snapshotToDomain);
    }

    @Override
    public MarketPriceData getMarketPriceData(String externalInstrumentId) {
        return one(reader.getMarketPriceData(externalInstrumentId), marketPriceDataMapper::snapshotToDomain);
    }

    @Override
    public Map<String, MarketTicker> getTickers(String externalInstrumentType) {
        List<MarketTickerExternalSnapshot> snapshots = reader.getTickers(externalInstrumentType);
        if (isNull(snapshots)) {
            return Map.of();
        }
        return snapshots.stream().collect(Collectors.toMap(
                MarketTickerExternalSnapshot::getExternalInstrumentId,
                marketSnapshotMapper::snapshotToDomain));
    }

    @Override
    public MarketOrderBook getOrderBook(String externalInstrumentId, Integer depth) {
        return one(reader.getOrderBook(externalInstrumentId, depth),
                marketSnapshotMapper::snapshotToDomain);
    }

    @Override
    public Map<String, BigDecimal> getMarkPrices(String externalInstrumentType) {
        return reader.getMarkPrices(externalInstrumentType);
    }

    @Override
    public Map<String, BigDecimal> getIndexPrices(String quoteCurrency) {
        return reader.getIndexPrices(quoteCurrency);
    }

    @Override
    public OffsetDateTime getServerTime() {
        return reader.getServerTime();
    }

    // --- общее -------------------------------------------------------------

    /**
     * Ключи счёта.
     *
     * <p>Резолвятся на КАЖДОМ приватном вызове, а не кэшируются полем:
     * коннектор стейтлесс и обслуживает любой счёт любого тенанта, а поле
     * означало бы, что второй вызов подписан ключами первого.
     */
    private ExchangeCredentials keys(String accountInternalId) {
        return credentialsResolver.resolve(accountInternalId);
    }

    /**
     * Инструмент из справочника площадки.
     *
     * <p><b>Правил торговли здесь нет намеренно</b> — они отдельная
     * операция ({@code getInstrumentRules}): площадка меняет их
     * независимо от листинга, и читатель синхронизирует их своим тиком.
     */
    private Instrument toInstrument(InstrumentExternalSnapshot snapshot) {
        if (isNull(snapshot)) {
            return null;
        }
        Instrument instrument = new Instrument();
        instrumentMapper.snapshotToDomain(instrument, snapshot);
        return instrument;
    }

    /**
     * Закрытый эпизод позиции.
     *
     * <p>Живого размера у него нет: это позиция в прошлом, и модель несёт
     * ровно то, что сообщает запись закрытия.
     */
    private Position toClosedPosition(PositionCloseResultExternalSnapshot snapshot) {
        if (isNull(snapshot)) {
            return null;
        }
        Position position = new Position();
        positionMapper.updateFromCloseSnapshot(snapshot, position);
        return position;
    }

    /** Пусто на входе — пусто на выходе: «не найдено» ошибкой не является. */
    private <S, D> D one(S snapshot, Function<S, D> toDomain) {
        return isNull(snapshot) ? null : toDomain.apply(snapshot);
    }

    /** Пустой список остаётся пустым: «ничего не найдено» — не отказ. */
    private <S, D> List<D> many(List<S> snapshots, Function<S, D> toDomain) {
        return isNull(snapshots)
                ? List.of()
                : snapshots.stream().map(toDomain).collect(Collectors.toList());
    }

    /**
     * Наружу уходят только ЗАКРЫТЫЕ бары: признак закрытия виден на
     * границе и дальше не едет — доменная свеча его не несёт
     * ({@code docs/models/domain/other/Candle.md}).
     *
     * <p><b>Фильтр стои́т здесь, потому что больше ему стоять негде.</b>
     * Читатель, получив незакрытый бар неотличимым от закрытого, записал
     * бы его в историю — и всякий расчёт по этой истории получил бы
     * look-ahead. Ошибка была бы в разрешающую сторону, а такие
     * запрещены ({@code docs/concept.md}, П1).
     *
     * <p>Незакрытый бар — законный факт площадки, и когда он кому-то
     * понадобится, он приедет СВОЕЙ операцией («текущий бар»), а не
     * ослаблением этой: две разные истины одной операцией не отдаются.
     */
    private List<Candle> closedOnly(List<CandleExternalSnapshot> snapshots) {
        return isNull(snapshots)
                ? List.of()
                : snapshots.stream()
                        .filter(snapshot -> isTrue(snapshot.getConfirm()))
                        .map(candleMapper::snapshotToDomain)
                        .collect(Collectors.toList());
    }
}
