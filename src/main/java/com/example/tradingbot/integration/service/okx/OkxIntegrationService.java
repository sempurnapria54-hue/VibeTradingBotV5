package com.example.tradingbot.integration.service.okx;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.core.fill.external_snapshot.FillExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.CancelOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.ClosePositionOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.CandleOkxResponse;
import com.example.tradingbot.integration.model.okx.response.InstrumentOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OkxAlgoOrderResponse;
import com.example.tradingbot.integration.model.okx.response.OkxApiResponse;
import com.example.tradingbot.integration.model.okx.response.OkxBalanceResponse;
import com.example.tradingbot.integration.model.okx.response.OkxFillResponse;
import com.example.tradingbot.integration.model.okx.response.OkxPositionResponse;
import com.example.tradingbot.integration.model.okx.response.OrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OrderOkxResponse;
import com.example.tradingbot.integration.service.ExchangeIntegrationException;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.mapping.FillMapper;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.util.Constants;
import java.util.List;
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
public class OkxIntegrationService implements IntegrationService {

    private final OkxRestClient okxRestClient;
    private final InstrumentMapper instrumentMapper;
    private final CandleMapper candleMapper;
    private final OrderMapper orderMapper;
    private final PositionMapper positionMapper;
    private final BalanceContainerMapper balanceContainerMapper;
    private final AlgoOrderMapper algoOrderMapper;
    private final FillMapper fillMapper;

    @Override
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

    @Override
    public List<CandleExternalSnapshot> getHistoryCandles(String externalInstrumentId, String externalBar,
                                                          Long afterMillis, Integer limit) {
        OkxApiResponse<List<String>> response = execute(
                () -> okxRestClient.getHistoryCandles(externalInstrumentId, externalBar, afterMillis, limit),
                "history-candles", "instId=" + externalInstrumentId + " bar=" + externalBar);
        verifyCode(response, "history-candles", "instId=" + externalInstrumentId);
        return toCandleSnapshots(response.getData());
    }

    @Override
    public List<CandleExternalSnapshot> getLatestCandles(String externalInstrumentId, String externalBar,
                                                         Integer limit) {
        OkxApiResponse<List<String>> response = execute(
                () -> okxRestClient.getLatestCandles(externalInstrumentId, externalBar, limit),
                "candles", "instId=" + externalInstrumentId + " bar=" + externalBar);
        verifyCode(response, "candles", "instId=" + externalInstrumentId);
        return toCandleSnapshots(response.getData());
    }

    @Override
    public OrderExternalSnapshot getOrder(String externalInstrumentId, String externalId, String internalId) {
        OkxApiResponse<OrderOkxResponse> response = execute(
                () -> okxRestClient.getOrder(externalInstrumentId, externalId, internalId),
                "trade-order", "instId=" + externalInstrumentId + " ordId=" + externalId + " clOrdId=" + internalId);
        verifyCode(response, "trade-order", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return orderMapper.integrationToSnapshot(response.getData().getFirst());
    }

    @Override
    public AlgoOrderExternalSnapshot getAlgoOrder(String externalInstrumentId, String externalId, String internalId) {
        OkxApiResponse<OkxAlgoOrderResponse> response = execute(
                () -> okxRestClient.getAlgoOrder(externalInstrumentId, externalId, internalId),
                "order-algo", "instId=" + externalInstrumentId + " algoId=" + externalId);
        verifyCode(response, "order-algo", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return algoOrderMapper.integrationToSnapshot(response.getData().getFirst());
    }

    @Override
    public PositionExternalSnapshot getPosition(String externalInstrumentId) {
        OkxApiResponse<OkxPositionResponse> response = execute(
                () -> okxRestClient.getPositions(externalInstrumentId),
                "account-positions", "instId=" + externalInstrumentId);
        verifyCode(response, "account-positions", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return null;
        }
        return positionMapper.integrationToSnapshot(response.getData().getFirst());
    }

    @Override
    public ExchangeAck placeOrder(Order order, String externalInstrumentId) {
        PlaceOrderOkxRequest request = orderMapper.domainToPlaceRequest(order, externalInstrumentId);
        OkxApiResponse<OrderAckOkxResponse> response = execute(() -> okxRestClient.placeOrder(request),
                "place-order", "instId=" + externalInstrumentId + " clOrdId=" + order.getInternalId());
        verifyCode(response, "place-order", "instId=" + externalInstrumentId);
        return toOrderAck(response, "place-order", externalInstrumentId);
    }

    @Override
    public ExchangeAck cancelOrder(Order order, String externalInstrumentId) {
        CancelOrderOkxRequest request = orderMapper.domainToCancelRequest(order, externalInstrumentId);
        OkxApiResponse<OrderAckOkxResponse> response = execute(() -> okxRestClient.cancelOrder(request),
                "cancel-order", "instId=" + externalInstrumentId + " ordId=" + order.getExternalId());
        verifyCode(response, "cancel-order", "instId=" + externalInstrumentId);
        return toOrderAck(response, "cancel-order", externalInstrumentId);
    }

    @Override
    public ExchangeAck placeAlgoOrder(AlgoOrder algoOrder, String externalInstrumentId) {
        PlaceAlgoOrderOkxRequest request = algoOrderMapper.domainToPlaceRequest(algoOrder, externalInstrumentId);
        OkxApiResponse<AlgoOrderAckOkxResponse> response = execute(() -> okxRestClient.placeAlgoOrder(request),
                "place-algo", "instId=" + externalInstrumentId + " algoClOrdId=" + algoOrder.getInternalId());
        verifyCode(response, "place-algo", "instId=" + externalInstrumentId);
        return toAlgoAck(response, "place-algo", externalInstrumentId);
    }

    @Override
    public ExchangeAck cancelAlgoOrder(AlgoOrder algoOrder, String externalInstrumentId) {
        CancelAlgoOrderOkxRequest request = algoOrderMapper.domainToCancelRequest(algoOrder, externalInstrumentId);
        List<CancelAlgoOrderOkxRequest> body = List.of(request);
        OkxApiResponse<AlgoOrderAckOkxResponse> response = execute(
                () -> isAdvanceFamily(algoOrder.getConditionType())
                        ? okxRestClient.cancelAdvanceAlgos(body)
                        : okxRestClient.cancelAlgos(body),
                "cancel-algo", "instId=" + externalInstrumentId + " algoId=" + algoOrder.getExternalId());
        verifyCode(response, "cancel-algo", "instId=" + externalInstrumentId);
        return toAlgoAck(response, "cancel-algo", externalInstrumentId);
    }

    @Override
    public ExchangeAck closePosition(String externalInstrumentId, String settleCurrency) {
        ClosePositionOkxRequest request = new ClosePositionOkxRequest();
        request.setInstId(externalInstrumentId);
        request.setMgnMode(Constants.Okx.TD_MODE_ISOLATED);
        request.setPosSide(Constants.Okx.POS_SIDE_NET);
        request.setCcy(settleCurrency);
        request.setAutoCxl(Boolean.TRUE);
        OkxApiResponse<OrderAckOkxResponse> response = execute(() -> okxRestClient.closePosition(request),
                "close-position", "instId=" + externalInstrumentId);
        verifyCode(response, "close-position", "instId=" + externalInstrumentId);
        return ExchangeAck.builder().success(Boolean.TRUE).build();
    }

    @Override
    public BalanceContainerExternalSnapshot getBalance(String settleCurrency) {
        OkxApiResponse<OkxBalanceResponse> response = execute(() -> okxRestClient.getBalance(settleCurrency),
                "account-balance", "ccy=" + settleCurrency);
        verifyCode(response, "account-balance", "ccy=" + settleCurrency);
        if (isEmpty(response.getData())) {
            log.error("OKX empty balance [account-balance] ccy={}", settleCurrency);
            throw new ExchangeIntegrationException("OKX empty balance [account-balance] ccy=" + settleCurrency);
        }
        return balanceContainerMapper.integrationToSnapshot(response.getData().getFirst());
    }

    private ExchangeAck toOrderAck(OkxApiResponse<OrderAckOkxResponse> response, String endpoint, String instId) {
        if (isEmpty(response.getData())) {
            log.error("OKX empty ack [{}] instId={}", endpoint, instId);
            throw new ExchangeIntegrationException("OKX empty ack [" + endpoint + "] instId=" + instId);
        }
        return orderMapper.integrationToAck(response.getData().getFirst());
    }

    private ExchangeAck toAlgoAck(OkxApiResponse<AlgoOrderAckOkxResponse> response, String endpoint, String instId) {
        if (isEmpty(response.getData())) {
            log.error("OKX empty ack [{}] instId={}", endpoint, instId);
            throw new ExchangeIntegrationException("OKX empty ack [" + endpoint + "] instId=" + instId);
        }
        return algoOrderMapper.integrationToAck(response.getData().getFirst());
    }

    /** Advance-семья (trailing/move_order_stop) → cancel-advance-algos; иначе ordinary cancel-algos. */
    private boolean isAdvanceFamily(AlgoOrder.ConditionType type) {
        return switch (type) {
            case TRAILING_PERCENTS, TRAILING_VALUE -> true;
            default -> false;
        };
    }

    @Override
    public List<FillExternalSnapshot> getFills(String externalInstrumentId, String afterBillId, Integer limit) {
        OkxApiResponse<OkxFillResponse> response = execute(
                () -> okxRestClient.getFills(externalInstrumentId, afterBillId, limit),
                "trade-fills", "instId=" + externalInstrumentId);
        verifyCode(response, "trade-fills", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream()
                .map(fillMapper::integrationToSnapshot)
                .collect(toList());
    }

    @Override
    public List<OrderExternalSnapshot> getPendingOrders(String externalInstrumentId) {
        OkxApiResponse<OrderOkxResponse> response = execute(() -> okxRestClient.getPendingOrders(externalInstrumentId),
                "orders-pending", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-pending", "instId=" + externalInstrumentId);
        return toOrderSnapshots(response);
    }

    @Override
    public List<OrderExternalSnapshot> getOrderHistory(String externalInstrumentId) {
        OkxApiResponse<OrderOkxResponse> response = execute(() -> okxRestClient.getOrderHistory(externalInstrumentId),
                "orders-history", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-history", "instId=" + externalInstrumentId);
        return toOrderSnapshots(response);
    }

    @Override
    public List<AlgoOrderExternalSnapshot> getPendingAlgoOrders(String externalInstrumentId,
                                                                AlgoOrder.ConditionType conditionType) {
        String ordType = algoOrderMapper.resolveAlgoOrdType(conditionType);
        OkxApiResponse<OkxAlgoOrderResponse> response = execute(
                () -> okxRestClient.getPendingAlgoOrders(externalInstrumentId, ordType),
                "orders-algo-pending", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-algo-pending", "instId=" + externalInstrumentId);
        return toAlgoOrderSnapshots(response);
    }

    @Override
    public List<AlgoOrderExternalSnapshot> getAlgoOrderHistory(String externalInstrumentId,
                                                               AlgoOrder.ConditionType conditionType) {
        String ordType = algoOrderMapper.resolveAlgoOrdType(conditionType);
        OkxApiResponse<OkxAlgoOrderResponse> response = execute(
                () -> okxRestClient.getAlgoOrderHistory(externalInstrumentId, ordType),
                "orders-algo-history", "instId=" + externalInstrumentId);
        verifyCode(response, "orders-algo-history", "instId=" + externalInstrumentId);
        return toAlgoOrderSnapshots(response);
    }

    @Override
    public List<FillExternalSnapshot> getFillsHistory(String externalInstrumentId, String afterBillId, Integer limit) {
        OkxApiResponse<OkxFillResponse> response = execute(
                () -> okxRestClient.getFillsHistory(externalInstrumentId, afterBillId, limit),
                "fills-history", "instId=" + externalInstrumentId);
        verifyCode(response, "fills-history", "instId=" + externalInstrumentId);
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream().map(fillMapper::integrationToSnapshot).collect(toList());
    }

    private List<OrderExternalSnapshot> toOrderSnapshots(OkxApiResponse<OrderOkxResponse> response) {
        if (isEmpty(response.getData())) {
            return List.of();
        }
        return response.getData().stream().map(orderMapper::integrationToSnapshot).collect(toList());
    }

    private List<AlgoOrderExternalSnapshot> toAlgoOrderSnapshots(OkxApiResponse<OkxAlgoOrderResponse> response) {
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
        boolean success = Objects.equals(Constants.Okx.SUCCESS_CODE, response.getCode());
        if (isFalse(success)) {
            log.error("OKX error [{}] {} code={} msg={}", endpoint, context, response.getCode(), response.getMsg());
            throw new ExchangeIntegrationException("OKX error [" + endpoint + "] code=" + response.getCode()
                    + " msg=" + response.getMsg());
        }
    }
}
