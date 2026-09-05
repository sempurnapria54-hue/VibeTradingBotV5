package com.example.connector.okx.source;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.connector.okx.snapshot.AlgoOrderExternalSnapshot;
import com.example.connector.okx.snapshot.BalanceContainerExternalSnapshot;
import com.example.connector.okx.snapshot.InstrumentExternalRulesExternalSnapshot;
import com.example.connector.okx.snapshot.InstrumentExternalSnapshot;
import com.example.connector.okx.snapshot.MarketOrderBookExternalSnapshot;
import com.example.connector.okx.snapshot.MarketTickerExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.connector.okx.snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.connector.okx.snapshot.OrderExternalSnapshot;
import com.example.connector.okx.snapshot.PositionCloseResultExternalSnapshot;
import com.example.connector.okx.snapshot.PositionExternalSnapshot;
import com.example.connector.okx.snapshot.DealCashFlowExternalSnapshot;
import com.example.connector.okx.snapshot.TradeFeeRateExternalSnapshot;
import com.example.connector.okx.snapshot.CandleExternalSnapshot;
import com.example.connector.okx.snapshot.MarketPriceDataExternalSnapshot;
import com.example.connector.okx.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.CancelOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.ClosePositionOkxRequest;
import com.example.connector.okx.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.PlaceOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.SetLeverageOkxRequest;
import com.example.connector.okx.integration.model.okx.response.AccountBillOkxResponse;
import com.example.connector.okx.integration.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.connector.okx.integration.model.okx.response.CandleOkxResponse;
import com.example.connector.okx.integration.model.okx.response.IndexTickerOkxResponse;
import com.example.connector.okx.integration.model.okx.response.InstrumentOkxResponse;
import com.example.connector.okx.integration.model.okx.response.MarkPriceOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OrderBookOkxResponse;
import com.example.connector.okx.integration.model.okx.response.AlgoOrderOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OkxApiResponse;
import com.example.connector.okx.integration.model.okx.response.BalanceOkxResponse;
import com.example.connector.okx.integration.model.okx.response.PositionOkxResponse;
import com.example.connector.okx.integration.model.okx.response.PositionsHistoryOkxResponse;
import com.example.connector.okx.integration.model.okx.response.ServerTimeOkxResponse;
import com.example.connector.okx.integration.model.okx.response.TickerOkxResponse;
import com.example.connector.okx.integration.model.okx.response.TradeFeeOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OrderAckOkxResponse;
import com.example.connector.okx.integration.model.okx.response.OrderOkxResponse;
import com.example.connector.okx.integration.model.okx.response.SetLeverageOkxResponse;
import com.example.connector.okx.integration.CredentialsRejectedException;
import com.example.connector.okx.integration.ExchangeIntegrationException;
import com.example.connector.okx.resolve.OkxCredentialsRejectionResolver;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.BalanceContainerMapper;
import com.example.connector.okx.mapping.CandleMapper;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.mapping.InstrumentExternalRulesMapper;
import com.example.connector.okx.mapping.InstrumentMapper;
import com.example.connector.okx.mapping.MarketPriceDataMapper;
import com.example.connector.okx.mapping.MarketSnapshotMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.integration.ExternalInvariantViolationException;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.mapping.TradeFeeRateMapper;
import com.example.connector.okx.util.OkxParse;
import com.example.connector.okx.util.OkxConstants;
import com.example.connector.okx.integration.client.OkxRestClient;
import com.example.connector.okx.credentials.ExchangeCredentials;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * OKX-реализация {@link IntegrationService}: публичные endpoint'ы
 * (instruments / candles) и приватные торговые (через подписанный
 * клиент) — ходит через {@link OkxRestClient}, валидирует структуру/код
 * ответа и отдаёт нормализованные снапшоты
 * (docs/rules/raw-exchange-dto-boundary.md). Сырой OKX DTO наружу не
 * выходит.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OkxSourceReader {

    /** Размер страницы обхода bill-записей (потолок источника — 100). */
    private static final Integer BILLS_PAGE_LIMIT = 100;

    /**
     * Семьи algo, которые контур умеет ставить: обычные условные (SL/TP),
     * OCO и трейлинг. Счёт-широкий срез algo складывается из вызова на
     * семью — {@code ordType} у эндпоинта обязателен.
     */
    private static final List<String> ALGO_PENDING_FAMILIES = List.of(
            OkxConstants.ALGO_ORD_TYPE_CONDITIONAL,
            OkxConstants.ALGO_ORD_TYPE_OCO,
            OkxConstants.ALGO_ORD_TYPE_MOVE_STOP);

    private final OkxRestClient okxRestClient;
    private final OkxCredentialsRejectionResolver credentialsRejectionResolver;
    private final InstrumentMapper instrumentMapper;
    private final InstrumentExternalRulesMapper instrumentExternalRulesMapper;
    private final MarketPriceDataMapper marketPriceDataMapper;
    private final MarketSnapshotMapper marketSnapshotMapper;
    private final CandleMapper candleMapper;
    private final OrderMapper orderMapper;
    private final PositionMapper positionMapper;
    private final BalanceContainerMapper balanceContainerMapper;
    private final AlgoOrderMapper algoOrderMapper;
    private final TradeFeeRateMapper tradeFeeRateMapper;
    private final DealCashFlowMapper dealCashFlowMapper;
    public InstrumentExternalSnapshot getInstrument(String externalInstrumentId, String externalInstrumentType) {
        OkxApiResponse<InstrumentOkxResponse> response = execute(
                () -> okxRestClient.getInstruments(externalInstrumentType, externalInstrumentId),
                "instruments", "instId=" + externalInstrumentId + " instType=" + externalInstrumentType);
        verifyCode(response, "instruments", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        InstrumentOkxResponse first = response.getData().getFirst();
        return instrumentMapper.integrationToSnapshot(first);
    }
    /**
     * Листинг инструментов площадки.
     *
     * <p>Публичное чтение: {@code instId} не задаётся, и площадка отдаёт
     * все инструменты типа. Нужен сбору рыночных данных, который ведёт
     * листинг целиком (docs/architecture/market-data-collection.md).
     */
    /**
     * Тикеры всего листинга одного типа инструмента.
     *
     * <p>Одно чтение на срез: поинструментный обход стоил бы сотни
     * запросов из общего бюджета лимитов
     * ({@code docs/processes/snapshot-collection.md}).
     */
    public List<MarketTickerExternalSnapshot> getTickers(String externalInstrumentType) {
        OkxApiResponse<TickerOkxResponse> response = execute(
                () -> okxRestClient.getTickers(externalInstrumentType),
                "tickers", "instType=" + externalInstrumentType);
        verifyCode(response, "tickers", "instType=" + externalInstrumentType);
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream()
                .map(marketSnapshotMapper::integrationToSnapshot)
                .collect(toList());
    }

    /** Книга заявок инструмента на заданную глубину каждой стороны. */
    public MarketOrderBookExternalSnapshot getOrderBook(String externalInstrumentId, Integer depth) {
        OkxApiResponse<OrderBookOkxResponse> response = execute(
                () -> okxRestClient.getOrderBook(externalInstrumentId, depth),
                "books", "instId=" + externalInstrumentId);
        verifyCode(response, "books", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return marketSnapshotMapper.integrationToSnapshot(response.getData().get(0), externalInstrumentId);
    }

    /**
     * Марк-цены листинга: инструмент площадки → цена.
     *
     * <p>Отдаётся картой, а не моделью: марк-цена — одна величина на
     * инструмент, и модель с единственным полем была бы носителем ради
     * формы ({@code .claude/rules/design-simplicity.md}).
     */
    public Map<String, BigDecimal> getMarkPrices(String externalInstrumentType) {
        OkxApiResponse<MarkPriceOkxResponse> response = execute(
                () -> okxRestClient.getMarkPrices(externalInstrumentType),
                "mark-price", "instType=" + externalInstrumentType);
        verifyCode(response, "mark-price", "instType=" + externalInstrumentType);
        if (isEmpty(response.getData())) {
            return Map.of();
        }
        return response.getData().stream()
                .filter(item -> nonNull(marketSnapshotMapper.markPrice(item)))
                .collect(Collectors.toMap(MarkPriceOkxResponse::getInstId,
                        marketSnapshotMapper::markPrice, (first, second) -> first));
    }

    /** Цены индексов одной расчётной валюты: индекс → цена. */
    public Map<String, BigDecimal> getIndexPrices(String quoteCurrency) {
        OkxApiResponse<IndexTickerOkxResponse> response = execute(
                () -> okxRestClient.getIndexTickers(quoteCurrency),
                "index-tickers", "quoteCcy=" + quoteCurrency);
        verifyCode(response, "index-tickers", "quoteCcy=" + quoteCurrency);
        if (isEmpty(response.getData())) {
            return Map.of();
        }
        return response.getData().stream()
                .filter(item -> nonNull(marketSnapshotMapper.indexPrice(item)))
                .collect(Collectors.toMap(IndexTickerOkxResponse::getInstId,
                        marketSnapshotMapper::indexPrice, (first, second) -> first));
    }

    public List<InstrumentExternalSnapshot> getInstruments(String externalInstrumentType) {
        OkxApiResponse<InstrumentOkxResponse> response = execute(
                () -> okxRestClient.getInstruments(externalInstrumentType, null),
                "instruments", "instType=" + externalInstrumentType);
        verifyCode(response, "instruments", "instType=" + externalInstrumentType);
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream()
                .map(instrumentMapper::integrationToSnapshot)
                .collect(toList());
    }

    public InstrumentExternalRulesExternalSnapshot getInstrumentRules(String externalInstrumentId,
                                                                      String externalInstrumentType) {
        OkxApiResponse<InstrumentOkxResponse> response = execute(
                () -> okxRestClient.getInstruments(externalInstrumentType, externalInstrumentId),
                "instruments-rules", "instId=" + externalInstrumentId + " instType=" + externalInstrumentType);
        verifyCode(response, "instruments-rules", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return instrumentExternalRulesMapper.integrationToSnapshot(response.getData().getFirst());
    }
    public List<TradeFeeRateExternalSnapshot> getTradeFeeRates(ExchangeCredentials credentials,
                                                               String externalInstrumentType) {
        OkxApiResponse<TradeFeeOkxResponse> response = execute(
                () -> okxRestClient.getTradeFee(credentials, externalInstrumentType),
                "trade-fee", "instType=" + externalInstrumentType);
        verifyCode(response, "trade-fee", "instType=" + externalInstrumentType);
        if (isEmpty(response.getData())) {
            return List.of();
        }
        TradeFeeOkxResponse rates = response.getData().getFirst();
        // Ставка живёт только в группах: плоские поля верхнего уровня офдок
        // для SWAP/FUTURES помечает deprecated, и прогон контура застал их
        // пустыми (наблюдение AG12.1).
        return emptyIfNull(rates.getFeeGroup()).stream()
                .map(group -> tradeFeeRateMapper.integrationToSnapshot(rates, group))
                .collect(toList());
    }
    public MarketPriceDataExternalSnapshot getMarketPriceData(String externalInstrumentId) {
        OkxApiResponse<TickerOkxResponse> response = execute(
                () -> okxRestClient.getTicker(externalInstrumentId),
                "market-ticker", "instId=" + externalInstrumentId);
        verifyCode(response, "market-ticker", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return marketPriceDataMapper.integrationToSnapshot(response.getData().getFirst());
    }
    public List<CandleExternalSnapshot> getHistoryCandles(String externalInstrumentId, String externalBar,
                                                          Long afterMillis, Integer limit) {
        OkxApiResponse<List<String>> response = execute(
                () -> okxRestClient.getHistoryCandles(externalInstrumentId, externalBar, afterMillis, limit),
                "history-candles", "instId=" + externalInstrumentId + " bar=" + externalBar);
        verifyCode(response, "history-candles", "instId=" + externalInstrumentId);
        return toCandleSnapshots(response.getData());
    }
    public List<CandleExternalSnapshot> getLatestCandles(String externalInstrumentId, String externalBar,
                                                         Integer limit) {
        OkxApiResponse<List<String>> response = execute(
                () -> okxRestClient.getLatestCandles(externalInstrumentId, externalBar, limit),
                "candles", "instId=" + externalInstrumentId + " bar=" + externalBar);
        verifyCode(response, "candles", "instId=" + externalInstrumentId);
        return toCandleSnapshots(response.getData());
    }
    public CandleExternalSnapshot getIndexCandleAt(String indexInstrumentId, String externalBar, OffsetDateTime at) {
        Long after = at.toInstant().toEpochMilli() + 1L;
        OkxApiResponse<List<String>> response = execute(
                () -> okxRestClient.getHistoryIndexCandles(indexInstrumentId, externalBar, after, 1),
                "history-index-candles", "instId=" + indexInstrumentId + " bar=" + externalBar + " at=" + at);
        verifyCode(response, "history-index-candles", "instId=" + indexInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        // Строка индекса — [ts,o,h,l,c,confirm]: колонки объёма нет, и общий
        // маппер девятиколоночной свечи прочёл бы confirm объёмом.
        List<String> row = response.getData().getFirst();
        return CandleExternalSnapshot.builder()
                .openTimestamp(Long.parseLong(row.get(0)))
                .open(OkxParse.decimal(row.get(1)))
                .high(OkxParse.decimal(row.get(2)))
                .low(OkxParse.decimal(row.get(3)))
                .close(OkxParse.decimal(row.get(4)))
                .confirm(Objects.equals(OkxConstants.CONFIRM_CLOSED, row.get(5)))
                .build();
    }
    public OrderExternalSnapshot getOrder(ExchangeCredentials credentials, String externalInstrumentId,
                                          String externalId, String internalId) {
        OkxApiResponse<OrderOkxResponse> response = execute(
                () -> okxRestClient.getOrder(credentials, externalInstrumentId, externalId, internalId),
                "trade-order", "instId=" + externalInstrumentId + " ordId=" + externalId + " clOrdId=" + internalId);
        verifyCode(response, "trade-order", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return orderMapper.integrationToSnapshot(response.getData().getFirst());
    }
    public AlgoOrderExternalSnapshot getAlgoOrder(ExchangeCredentials credentials, String externalInstrumentId,
                                                  String externalId, String internalId) {
        OkxApiResponse<AlgoOrderOkxResponse> response = execute(
                () -> okxRestClient.getAlgoOrder(credentials, externalInstrumentId, externalId, internalId),
                "order-algo", "instId=" + externalInstrumentId + " algoId=" + externalId);
        verifyCode(response, "order-algo", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return algoOrderMapper.integrationToSnapshot(response.getData().getFirst());
    }
    public OffsetDateTime getServerTime() {
        OkxApiResponse<ServerTimeOkxResponse> response = execute(okxRestClient::getServerTime, "public-time", "");
        verifyCode(response, "public-time", "");
        if (isEmpty(response.getData())) {
            throw new ExternalInvariantViolationException("public/time: ответ без серверного времени");
        }
        return OkxParse.offsetTime(response.getData().getFirst().getTs());
    }
    public PositionExternalSnapshot getPosition(ExchangeCredentials credentials, String externalInstrumentId) {
        OkxApiResponse<PositionOkxResponse> response = execute(
                () -> okxRestClient.getPositions(credentials, externalInstrumentId),
                "account-positions", "instId=" + externalInstrumentId);
        verifyCode(response, "account-positions", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return positionMapper.integrationToSnapshot(response.getData().getFirst());
    }
    public List<PositionExternalSnapshot> getPositions(ExchangeCredentials credentials) {
        OkxApiResponse<PositionOkxResponse> response = execute(
                () -> okxRestClient.getAllPositions(credentials), "account-positions", "instType=SWAP");
        verifyCode(response, "account-positions", "instType=SWAP");
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream()
                .map(positionMapper::integrationToSnapshot)
                .collect(toList());
    }
    public List<PositionCloseResultExternalSnapshot> getPositionCloseRecords(ExchangeCredentials credentials, String externalInstrumentId,
                                                                            OffsetDateTime windowBegin) {
        String before = String.valueOf(windowBegin.toInstant().toEpochMilli());
        OkxApiResponse<PositionsHistoryOkxResponse> response = execute(
                () -> okxRestClient.getPositionsHistory(credentials, externalInstrumentId, before),
                "positions-history", "instId=" + externalInstrumentId + " before=" + before);
        verifyCode(response, "positions-history", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream()
                .map(record -> verifyBelongsToInstrument(record, externalInstrumentId))
                .map(positionMapper::integrationToCloseSnapshot)
                .collect(toList());
    }

    /**
     * Структурная проверка принадлежности записи запрошенному
     * инструменту. Без неё корректность чтения держалась бы только
     * фильтром запроса — знанием вызывающего, а не фактом ответа
     * (docs/models/mapping/PositionCloseResult.md).
     */
    private PositionsHistoryOkxResponse verifyBelongsToInstrument(PositionsHistoryOkxResponse record,
                                                                  String externalInstrumentId) {
        if (isFalse(Objects.equals(externalInstrumentId, record.getInstId()))) {
            throw new ExternalInvariantViolationException(
                    "positions-history: запись чужого инструмента: ожидался " + externalInstrumentId
                            + ", пришёл " + record.getInstId());
        }
        return record;
    }
    public ExchangeAck placeOrder(ExchangeCredentials credentials, Order order, String externalInstrumentId) {
        PlaceOrderOkxRequest request = orderMapper.domainToPlaceRequest(order, externalInstrumentId);
        OkxApiResponse<OrderAckOkxResponse> response = execute(() -> okxRestClient.placeOrder(credentials, request),
                "place-order", "instId=" + externalInstrumentId + " clOrdId=" + order.getInternalId());
        return toOrderAck(response, "place-order", externalInstrumentId);
    }
    public ExchangeAck cancelOrder(ExchangeCredentials credentials, Order order, String externalInstrumentId) {
        CancelOrderOkxRequest request = orderMapper.domainToCancelRequest(order, externalInstrumentId);
        OkxApiResponse<OrderAckOkxResponse> response = execute(() -> okxRestClient.cancelOrder(credentials, request),
                "cancel-order", "instId=" + externalInstrumentId + " ordId=" + order.getExternalId());
        return toOrderAck(response, "cancel-order", externalInstrumentId);
    }
    public ExchangeAck placeAlgoOrder(ExchangeCredentials credentials, AlgoOrder algoOrder, String externalInstrumentId) {
        PlaceAlgoOrderOkxRequest request = algoOrderMapper.domainToPlaceRequest(algoOrder, externalInstrumentId);
        OkxApiResponse<AlgoOrderAckOkxResponse> response = execute(() -> okxRestClient.placeAlgoOrder(credentials, request),
                "place-algo", "instId=" + externalInstrumentId + " algoClOrdId=" + algoOrder.getInternalId());
        return toAlgoAck(response, "place-algo", externalInstrumentId);
    }
    public ExchangeAck cancelAlgoOrder(ExchangeCredentials credentials, AlgoOrder algoOrder, String externalInstrumentId) {
        CancelAlgoOrderOkxRequest request = algoOrderMapper.domainToCancelRequest(algoOrder, externalInstrumentId);
        List<CancelAlgoOrderOkxRequest> body = List.of(request);
        OkxApiResponse<AlgoOrderAckOkxResponse> response = execute(
                () -> isAdvanceFamily(algoOrder.getConditionType())
                        ? okxRestClient.cancelAdvanceAlgos(credentials, body)
                        : okxRestClient.cancelAlgos(credentials, body),
                "cancel-algo", "instId=" + externalInstrumentId + " algoId=" + algoOrder.getExternalId());
        return toAlgoAck(response, "cancel-algo", externalInstrumentId);
    }
    public ExchangeAck cancelAttachedProtection(ExchangeCredentials credentials, AttachedAlgoOrder attached,
                                                String externalInstrumentId) {
        CancelAlgoOrderOkxRequest request = orderMapper.domainToCancelRequest(attached, externalInstrumentId);
        List<CancelAlgoOrderOkxRequest> body = List.of(request);
        OkxApiResponse<AlgoOrderAckOkxResponse> response = execute(() -> okxRestClient.cancelAlgos(credentials, body),
                "cancel-attached", "instId=" + externalInstrumentId + " algoId=" + attached.getExternalId());
        return toAlgoAck(response, "cancel-attached", externalInstrumentId);
    }
    public ExchangeAck closePosition(ExchangeCredentials credentials, String externalInstrumentId, String settleCurrency) {
        ClosePositionOkxRequest request = new ClosePositionOkxRequest();
        request.setInstId(externalInstrumentId);
        request.setMgnMode(OkxConstants.TD_MODE_ISOLATED);
        request.setPosSide(OkxConstants.POS_SIDE_NET);
        request.setCcy(settleCurrency);
        request.setAutoCxl(Boolean.TRUE);
        OkxApiResponse<OrderAckOkxResponse> response = execute(() -> okxRestClient.closePosition(credentials, request),
                "close-position", "instId=" + externalInstrumentId);
        return toOrderAck(response, "close-position", externalInstrumentId);
    }
    public ExchangeAck setLeverage(ExchangeCredentials credentials, String externalInstrumentId, Integer leverage) {
        SetLeverageOkxRequest request = new SetLeverageOkxRequest();
        request.setInstId(externalInstrumentId);
        request.setLever(String.valueOf(leverage));
        request.setMgnMode(OkxConstants.TD_MODE_ISOLATED);
        request.setPosSide(OkxConstants.POS_SIDE_NET);
        OkxApiResponse<SetLeverageOkxResponse> response = execute(() -> okxRestClient.setLeverage(credentials, request),
                "set-leverage", "instId=" + externalInstrumentId + " lever=" + leverage);
        verifyCode(response, "set-leverage", "instId=" + externalInstrumentId);
        return ExchangeAck.builder()
                .success(Boolean.TRUE)
                .internalId(externalInstrumentId)
                .code(response.getCode())
                .message(response.getMsg())
                .build();
    }
    public BalanceContainerExternalSnapshot getBalance(ExchangeCredentials credentials, String settleCurrency) {
        OkxApiResponse<BalanceOkxResponse> response = execute(() -> okxRestClient.getBalance(credentials, settleCurrency),
                "account-balance", "ccy=" + settleCurrency);
        verifyCode(response, "account-balance", "ccy=" + settleCurrency);
        if (isEmpty(response.getData())) {
            log.error("OKX empty balance [account-balance] ccy={}", settleCurrency);
            throw new ExchangeIntegrationException("OKX empty balance [account-balance] ccy=" + settleCurrency);
        }
        return balanceContainerMapper.integrationToSnapshot(response.getData().getFirst());
    }

    /**
     * Write-ack из ответа OKX. Пустой {@code data} (или null response) =
     * transport/system-ошибка (auth/rate-limit/5xx) → бросаем retryable
     * {@link ExchangeIntegrationException} с реальными code/msg. Непустой
     * {@code data} = бизнес-исход: {@code success} выводится из
     * per-order {@code sCode} (бизнес-реджект → success=false ack, не
     * throw — не runtime truth, см. ack-not-runtime-truth). Top-level
     * {@code code}/{@code msg} передаём маппером как fallback для
     * ack-кода/сообщения, если per-order {@code sCode}/{@code sMsg} пусты
     * (находка F1: на реджекте они приходили null).
     */
    private ExchangeAck toOrderAck(OkxApiResponse<OrderAckOkxResponse> response, String endpoint, String instId) {
        if (isNull(response) || isEmpty(response.getData())) {
            throw writeFailure(response, endpoint, instId);
        }
        return orderMapper.integrationToAck(response.getData().getFirst(), response.getCode(), response.getMsg());
    }

    private ExchangeAck toAlgoAck(OkxApiResponse<AlgoOrderAckOkxResponse> response, String endpoint, String instId) {
        if (isNull(response) || isEmpty(response.getData())) {
            throw writeFailure(response, endpoint, instId);
        }
        return algoOrderMapper.integrationToAck(response.getData().getFirst(), response.getCode(), response.getMsg());
    }

    private ExchangeIntegrationException writeFailure(OkxApiResponse<?> response, String endpoint, String instId) {
        String code = isNull(response) ? "null" : response.getCode();
        String msg = isNull(response) ? "null response" : response.getMsg();
        log.error("OKX write failed [{}] instId={} code={} msg={}", endpoint, instId, code, msg);
        return new ExchangeIntegrationException("OKX write failed [" + endpoint + "] instId=" + instId
                + " code=" + code + " msg=" + msg);
    }

    /** Advance-семья (trailing/move_order_stop) → cancel-advance-algos; иначе ordinary cancel-algos. */
    private boolean isAdvanceFamily(AlgoOrder.ConditionType type) {
        return switch (type) {
            case TRAILING_PERCENTS, TRAILING_VALUE -> true;
            default -> false;
        };
    }
    public List<OrderExternalSnapshot> getPendingOrders(ExchangeCredentials credentials, String externalInstrumentId) {
        OkxApiResponse<OrderOkxResponse> response = execute(() -> okxRestClient.getPendingOrders(credentials, externalInstrumentId),
                "orders-pending", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-pending", "instId=" + externalInstrumentId);
        return toOrderSnapshots(response);
    }
    public List<OrderExternalSnapshot> getAllPendingOrders(ExchangeCredentials credentials) {
        OkxApiResponse<OrderOkxResponse> response = execute(() -> okxRestClient.getAllPendingOrders(credentials),
                "orders-pending", "instType=SWAP");
        verifyCode(response, "orders-pending", "instType=SWAP");
        verifyPageNotTruncated(response.getData(), "orders-pending", "instType=SWAP");
        return toOrderSnapshots(response);
    }

    /**
     * Семьи перечислены явно: {@code ordType} у эндпоинта обязателен, и
     * счёт-широкий срез складывается из вызова на семью. Перечень — те
     * семьи, которые контур умеет ставить; семья, которую он не ставит,
     * в срезе была бы чужой заявкой, и её ловит свой детектор по маркеру,
     * а не по отсутствию в этом перечне.
     *
     * <p><b>Усечение проверяется у КАЖДОЙ выданной страницы, а не у
     * склейки.</b> Потолок задан на вызов, поэтому суммарный размер трёх
     * семей его превышает штатно: мера по склейке объявляла бы проход
     * неполным при полностью добытом срезе — и трижды подряд поднимала бы
     * мягкую биржевую ступень со снятием вручную.
     */
    public List<AlgoOrderExternalSnapshot> getAllPendingAlgoOrders(ExchangeCredentials credentials) {
        List<AlgoOrderExternalSnapshot> all = new ArrayList<>();
        for (String ordType : ALGO_PENDING_FAMILIES) {
            OkxApiResponse<AlgoOrderOkxResponse> response = execute(
                    () -> okxRestClient.getAllPendingAlgoOrders(credentials, ordType),
                    "orders-algo-pending", "instType=SWAP ordType=" + ordType);
            verifyCode(response, "orders-algo-pending", "instType=SWAP ordType=" + ordType);
            verifyPageNotTruncated(response.getData(), "orders-algo-pending", "ordType=" + ordType);
            all.addAll(toAlgoOrderSnapshots(response));
        }
        return all;
    }

    /**
     * Полная страница означает «возможно, есть ещё», и принять её за
     * полный срез нельзя: детекторы молчали бы о том, что не поместилось,
     * то есть пропуск выглядел бы чистым проходом
     * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
     *
     * <p>Проверка живёт ЗДЕСЬ, а не у читателя среза: потолок — величина
     * источника, он задаётся этому вызову и меряется на его же выдаче.
     * У читателя нет операнда «сколько страниц было выдано», и там же
     * доменный слой читал бы константу источника напрямую.
     */
    private void verifyPageNotTruncated(List<?> page, String endpoint, String params) {
        if (isEmpty(page) || page.size() < OkxConstants.PENDING_PAGE_LIMIT) {
            return;
        }
        throw new ExchangeIntegrationException(String.format(
                "Счёт-широкий срез усечён потолком страницы: endpoint=%s %s limit=%d",
                endpoint, params, OkxConstants.PENDING_PAGE_LIMIT));
    }
    public List<OrderExternalSnapshot> getOrderHistory(ExchangeCredentials credentials, String externalInstrumentId) {
        OkxApiResponse<OrderOkxResponse> response = execute(() -> okxRestClient.getOrderHistory(credentials, externalInstrumentId),
                "orders-history", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-history", "instId=" + externalInstrumentId);
        return toOrderSnapshots(response);
    }
    public List<AlgoOrderExternalSnapshot> getPendingAlgoOrders(ExchangeCredentials credentials, String externalInstrumentId,
                                                                AlgoOrder.ConditionType conditionType) {
        String ordType = algoOrderMapper.resolveAlgoOrdType(conditionType);
        OkxApiResponse<AlgoOrderOkxResponse> response = execute(
                () -> okxRestClient.getPendingAlgoOrders(credentials, externalInstrumentId, ordType),
                "orders-algo-pending", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-algo-pending", "instId=" + externalInstrumentId);
        return toAlgoOrderSnapshots(response);
    }

    /**
     * Обязательный операнд истории закрывается идентификатором записи,
     * если он известен, иначе — терминальными состояниями двумя вызовами
     * (`docs/models/mapping/AlgoOrder.md` §«OKX evidence-cycle / not
     * found»). Без обоих эндпоинт отвечает отказом, а не пустой историей.
     */
    public List<AlgoOrderExternalSnapshot> getAlgoOrderHistory(ExchangeCredentials credentials, String externalInstrumentId,
                                                               AlgoOrder.ConditionType conditionType,
                                                               String externalId) {
        String ordType = algoOrderMapper.resolveAlgoOrdType(conditionType);
        if (isNotBlank(externalId)) {
            return algoHistoryPage(credentials, externalInstrumentId, ordType, externalId, null);
        }
        List<AlgoOrderExternalSnapshot> found = new ArrayList<>(
                algoHistoryPage(credentials, externalInstrumentId, ordType, null, OkxConstants.ALGO_STATE_EFFECTIVE));
        found.addAll(algoHistoryPage(credentials, externalInstrumentId, ordType, null, OkxConstants.ALGO_STATE_CANCELED));
        return found;
    }
    public List<AttachedAlgoOrderExternalSnapshot> getPendingMaterializedProtections(ExchangeCredentials credentials,
                                                                                     String externalInstrumentId) {
        OkxApiResponse<AlgoOrderOkxResponse> response = execute(
                () -> okxRestClient.getPendingAlgoOrders(credentials, externalInstrumentId,
                        OkxConstants.ALGO_ORD_TYPE_CONDITIONAL),
                "orders-algo-pending", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-algo-pending", "instId=" + externalInstrumentId);
        return toProtectionSnapshots(response);
    }
    public List<AttachedAlgoOrderExternalSnapshot> getMaterializedProtectionHistory(ExchangeCredentials credentials, String externalInstrumentId,
                                                                                    ProtectionHistoryLeg leg) {
        String state = protectionHistoryState(leg);
        String context = "instId=" + externalInstrumentId + " state=" + state;
        OkxApiResponse<AlgoOrderOkxResponse> response = execute(
                () -> okxRestClient.getAlgoOrderHistory(credentials, externalInstrumentId,
                        OkxConstants.ALGO_ORD_TYPE_CONDITIONAL, null, state),
                "orders-algo-history", context);
        verifyCode(response, "orders-algo-history", context);
        return toProtectionSnapshots(response);
    }

    /** Нога разбора истории → терминальное состояние записи в query источника. */
    private String protectionHistoryState(ProtectionHistoryLeg leg) {
        return switch (leg) {
            case EFFECTIVE -> OkxConstants.ALGO_STATE_EFFECTIVE;
            case CANCELED -> OkxConstants.ALGO_STATE_CANCELED;
            case ORDER_FAILED -> OkxConstants.ALGO_STATE_ORDER_FAILED;
        };
    }

    private List<AlgoOrderExternalSnapshot> algoHistoryPage(ExchangeCredentials credentials, String externalInstrumentId, String ordType,
                                                            String algoId, String state) {
        String context = "instId=" + externalInstrumentId + " state=" + state;
        OkxApiResponse<AlgoOrderOkxResponse> response = execute(
                () -> okxRestClient.getAlgoOrderHistory(credentials, externalInstrumentId, ordType, algoId, state),
                "orders-algo-history", context);
        verifyCode(response, "orders-algo-history", context);
        return toAlgoOrderSnapshots(response);
    }

    private List<AttachedAlgoOrderExternalSnapshot> toProtectionSnapshots(
            OkxApiResponse<AlgoOrderOkxResponse> response) {
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream().map(orderMapper::integrationToSnapshot).collect(toList());
    }
    public List<DealCashFlowExternalSnapshot> getBills(ExchangeCredentials credentials, OffsetDateTime begin,
                                                       OffsetDateTime end) {
        return fetchBills("account-bills",
                (beginMs, endMs, after) -> okxRestClient.getBills(credentials, beginMs, endMs, after, BILLS_PAGE_LIMIT),
                begin, end);
    }
    public List<DealCashFlowExternalSnapshot> getBillsArchive(ExchangeCredentials credentials, OffsetDateTime begin,
                                                              OffsetDateTime end) {
        return fetchBills("account-bills-archive",
                (beginMs, endMs, after) -> okxRestClient.getBillsArchive(credentials, beginMs, endMs, after, BILLS_PAGE_LIMIT),
                begin, end);
    }

    /**
     * Обход окна bill-записей с пагинацией назад по billId: следующая
     * страница — {@code after = min(billId)} предыдущей, стоп — пустая
     * страница (docs/integrations/okx/contracts/account-bills.md).
     * Курсор пагинации границу не пересекает — наружу уходит список
     * снапшотов целиком.
     */
    private List<DealCashFlowExternalSnapshot> fetchBills(String operation, BillsPage page,
                                                          OffsetDateTime begin, OffsetDateTime end) {
        String beginMs = String.valueOf(begin.toInstant().toEpochMilli());
        String endMs = String.valueOf(end.toInstant().toEpochMilli());
        List<DealCashFlowExternalSnapshot> snapshots = new ArrayList<>();
        String after = null;
        while (true) {
            String cursor = after;
            OkxApiResponse<AccountBillOkxResponse> response = execute(() -> page.fetch(beginMs, endMs, cursor),
                    operation, "begin=" + beginMs + " end=" + endMs + " after=" + cursor);
            verifyCode(response, operation, "begin=" + beginMs + " end=" + endMs);
            if (isEmpty(response.getData())) {
                return snapshots;
            }
            response.getData().stream().map(dealCashFlowMapper::integrationToSnapshot).forEach(snapshots::add);
            after = response.getData().get(response.getData().size() - 1).getBillId();
        }
    }

    /** Страница bill-записей одного эндпоинта (свежего либо архивного). */
    @FunctionalInterface
    private interface BillsPage {

        OkxApiResponse<AccountBillOkxResponse> fetch(String beginMs, String endMs, String after);
    }

    private List<OrderExternalSnapshot> toOrderSnapshots(OkxApiResponse<OrderOkxResponse> response) {
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream().map(orderMapper::integrationToSnapshot).collect(toList());
    }

    private List<AlgoOrderExternalSnapshot> toAlgoOrderSnapshots(OkxApiResponse<AlgoOrderOkxResponse> response) {
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream().map(algoOrderMapper::integrationToSnapshot).collect(toList());
    }

    private List<CandleExternalSnapshot> toCandleSnapshots(List<List<String>> data) {
        if (isEmpty(data)) {
            return List.of();
        }
        return data.stream()
                .map(CandleOkxResponse::of)
                .map(candleMapper::integrationToSnapshot)
                .collect(toList());
    }

    private <T> T execute(Supplier<T> call, String endpoint, String context) {
        try {
            return call.get();
        } catch (RestClientException e) {
            log.error("OKX transport error [{}] {}", endpoint, context, e);
            throw new ExchangeIntegrationException("OKX transport error [" + endpoint + "] " + context, e);
        }
    }

    private void verifyCode(OkxApiResponse<?> response, String endpoint, String context) {
        if (isNull(response)) {
            log.error("OKX null response [{}] {}", endpoint, context);
            throw new ExchangeIntegrationException("OKX null response [" + endpoint + "] " + context);
        }
        boolean success = Objects.equals(OkxConstants.SUCCESS_CODE, response.getCode());
        if (isFalse(success)) {
            log.error("OKX error [{}] {} code={} msg={}", endpoint, context, response.getCode(), response.getMsg());
            // Отказ в кредах опознаётся ЗДЕСЬ: граница — единственный слой,
            // который видит ответ источника, и момент — любой приватный вызов
            // (docs/components/IntegrationService.md). Дальше он едет своим
            // исключением: повтором не лечится и поднимает биржевую ступень 2.
            if (isTrue(credentialsRejectionResolver.isCredentialsRejected(response.getCode()))) {
                throw new CredentialsRejectedException("OKX rejected our credentials [" + endpoint
                        + "] code=" + response.getCode() + " msg=" + response.getMsg());
            }
            throw new ExchangeIntegrationException("OKX error [" + endpoint + "] code=" + response.getCode()
                    + " msg=" + response.getMsg());
        }
    }
}
