package com.example.tradingbot.api.controller;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.CancelOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.ClosePositionOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.InstrumentOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OkxAlgoOrderResponse;
import com.example.tradingbot.integration.model.okx.response.OkxApiResponse;
import com.example.tradingbot.integration.model.okx.response.OkxBalanceResponse;
import com.example.tradingbot.integration.model.okx.response.OkxFillResponse;
import com.example.tradingbot.integration.model.okx.response.OkxPositionResponse;
import com.example.tradingbot.integration.model.okx.response.OkxTickerResponse;
import com.example.tradingbot.integration.model.okx.response.OrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OrderOkxResponse;
import com.example.tradingbot.integration.service.okx.OkxRestClient;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A2 raw-passthrough — поверхность контура тестов API источника
 * (`.claude/decisions/source-api-target-rebase.md`). Зовёт
 * {@link OkxRestClient} напрямую и отдаёт **сырой** {@link OkxApiResponse}
 * (типизированный DTO источника) — без снапшот/маппер-слоя
 * {@code IntegrationService}. Подпись и креды — на стороне app (signing
 * interceptor); demo/prod — профилем OKX. Write-эндпоинты строят
 * минимальный domain-объект из параметров и прогоняют **реальную**
 * сериализацию request-DTO мапперами (адаптер-константы tdMode/posSide).
 * Инструмент тестирования интеграции, не часть продуктового API.
 */
@RestController
@RequestMapping("/api/proxy/okx")
@RequiredArgsConstructor
@Tag(name = "OKX raw passthrough", description = "Сырой прогон OkxRestClient (тестирование интеграции)")
public class OkxProxyController {

    private final OkxRestClient okxRestClient;
    private final OrderMapper orderMapper;
    private final AlgoOrderMapper algoOrderMapper;

    /**
     * Диагностическое чтение account config (acctLv/posMode) — прямой
     * сырой signed GET через приватный клиент. Read-only.
     */
    @GetMapping("/account-config")
    @Operation(summary = "[диагностика] Сырой account config OKX (acctLv/posMode)")
    public String getAccountConfig() {
        return okxRestClient.getAccountConfig();
    }

    @GetMapping("/instrument")
    @Operation(summary = "Сырая спецификация инструмента")
    public OkxApiResponse<InstrumentOkxResponse> getInstrument(@RequestParam String instId,
                                                               @RequestParam String instType) {
        return okxRestClient.getInstruments(instType, instId);
    }

    @GetMapping("/ticker")
    @Operation(summary = "Сырой тикер инструмента — live last/ask/bid + ts")
    public OkxApiResponse<OkxTickerResponse> getTicker(@RequestParam String instId) {
        return okxRestClient.getTicker(instId);
    }

    @GetMapping("/order")
    @Operation(summary = "Сырой ordinary order по ordId или clOrdId")
    public OkxApiResponse<OrderOkxResponse> getOrder(@RequestParam String instId,
                                                     @RequestParam(required = false) String ordId,
                                                     @RequestParam(required = false) String clOrdId) {
        return okxRestClient.getOrder(instId, ordId, clOrdId);
    }

    @GetMapping("/position")
    @Operation(summary = "Сырые позиции по инструменту")
    public OkxApiResponse<OkxPositionResponse> getPosition(@RequestParam String instId) {
        return okxRestClient.getPositions(instId);
    }

    @GetMapping("/algo-order")
    @Operation(summary = "Сырой standalone algo-order по algoId или algoClOrdId")
    public OkxApiResponse<OkxAlgoOrderResponse> getAlgoOrder(@RequestParam String instId,
                                                             @RequestParam(required = false) String algoId,
                                                             @RequestParam(required = false) String algoClOrdId) {
        return okxRestClient.getAlgoOrder(instId, algoId, algoClOrdId);
    }

    @GetMapping("/balance")
    @Operation(summary = "Сырой баланс аккаунта по валюте")
    public OkxApiResponse<OkxBalanceResponse> getBalance(@RequestParam String ccy) {
        return okxRestClient.getBalance(ccy);
    }

    @GetMapping("/fills")
    @Operation(summary = "Сырые исполнения (fills, 3 дня) по инструменту")
    public OkxApiResponse<OkxFillResponse> getFills(@RequestParam String instId,
                                                    @RequestParam(required = false) String after,
                                                    @RequestParam(required = false) Integer limit) {
        return okxRestClient.getFills(instId, after, limit);
    }

    @GetMapping("/fills-history")
    @Operation(summary = "Сырые исполнения (fills, 3 месяца) по инструменту")
    public OkxApiResponse<OkxFillResponse> getFillsHistory(@RequestParam String instId,
                                                           @RequestParam(required = false) String after,
                                                           @RequestParam(required = false) Integer limit) {
        return okxRestClient.getFillsHistory(instId, after, limit);
    }

    @GetMapping("/candles")
    @Operation(summary = "Сырые последние свечи (докачка хвоста) по инструменту и bar")
    public OkxApiResponse<List<String>> getLatestCandles(@RequestParam String instId,
                                                         @RequestParam String bar,
                                                         @RequestParam(required = false) Integer limit) {
        return okxRestClient.getLatestCandles(instId, bar, limit);
    }

    @GetMapping("/history-candles")
    @Operation(summary = "Сырые исторические свечи (пагинация назад по after) по инструменту и bar")
    public OkxApiResponse<List<String>> getHistoryCandles(@RequestParam String instId,
                                                          @RequestParam String bar,
                                                          @RequestParam(required = false) Long after,
                                                          @RequestParam(required = false) Integer limit) {
        return okxRestClient.getHistoryCandles(instId, bar, after, limit);
    }

    @GetMapping("/orders-pending")
    @Operation(summary = "Сырые live/pending ordinary orders по инструменту")
    public OkxApiResponse<OrderOkxResponse> getPendingOrders(@RequestParam String instId) {
        return okxRestClient.getPendingOrders(instId);
    }

    @GetMapping("/orders-history")
    @Operation(summary = "Сырая история ordinary orders (7 дней) по инструменту")
    public OkxApiResponse<OrderOkxResponse> getOrderHistory(@RequestParam String instId) {
        return okxRestClient.getOrderHistory(instId);
    }

    @GetMapping("/orders-algo-pending")
    @Operation(summary = "Сырые live/pending algo orders по инструменту и ordType")
    public OkxApiResponse<OkxAlgoOrderResponse> getPendingAlgoOrders(@RequestParam String instId,
                                                                     @RequestParam String ordType) {
        return okxRestClient.getPendingAlgoOrders(instId, ordType);
    }

    @GetMapping("/orders-algo-history")
    @Operation(summary = "Сырая история algo orders (3 месяца) по инструменту и ordType")
    public OkxApiResponse<OkxAlgoOrderResponse> getAlgoOrderHistory(@RequestParam String instId,
                                                                    @RequestParam String ordType) {
        return okxRestClient.getAlgoOrderHistory(instId, ordType);
    }

    @PostMapping("/order")
    @Operation(summary = "Сырая постановка ordinary order (минимальный Order из параметров)")
    public OkxApiResponse<OrderAckOkxResponse> placeOrder(@RequestParam String instId,
                                                          @RequestParam String side,
                                                          @RequestParam String sz,
                                                          @RequestParam(required = false) String px,
                                                          @RequestParam String clOrdId,
                                                          @RequestParam(required = false) Boolean reduceOnly) {
        Order order = new Order();
        order.setInternalId(clOrdId);
        order.setSide(side);
        order.setSize(new BigDecimal(sz));
        if (nonNull(px)) {
            order.setPrice(new BigDecimal(px));
        }
        order.setPositionReducingOnly(reduceOnly);
        PlaceOrderOkxRequest request = orderMapper.domainToPlaceRequest(order, instId);
        return okxRestClient.placeOrder(request);
    }

    @DeleteMapping("/order")
    @Operation(summary = "Сырая отмена ordinary order по ordId или clOrdId")
    public OkxApiResponse<OrderAckOkxResponse> cancelOrder(@RequestParam String instId,
                                                           @RequestParam(required = false) String ordId,
                                                           @RequestParam(required = false) String clOrdId) {
        Order order = new Order();
        order.setExternalId(ordId);
        order.setInternalId(clOrdId);
        CancelOrderOkxRequest request = orderMapper.domainToCancelRequest(order, instId);
        return okxRestClient.cancelOrder(request);
    }

    @PostMapping("/algo-order")
    @Operation(summary = "Сырая постановка standalone algo-order (минимальный AlgoOrder из параметров)")
    public OkxApiResponse<AlgoOrderAckOkxResponse> placeAlgoOrder(@RequestParam String instId,
                                                                  @RequestParam String algoClOrdId,
                                                                  @RequestParam String direction,
                                                                  @RequestParam String conditionType,
                                                                  @RequestParam String sz,
                                                                  @RequestParam(required = false) Boolean reduceOnly,
                                                                  @RequestParam(required = false) String slTriggerPx,
                                                                  @RequestParam(required = false) String slTriggerPxType,
                                                                  @RequestParam(required = false) String tpTriggerPx,
                                                                  @RequestParam(required = false) String tpTriggerPxType,
                                                                  @RequestParam(required = false) String trailingPercents,
                                                                  @RequestParam(required = false) String activePx) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setInternalId(algoClOrdId);
        algoOrder.setDirection(AlgoOrder.Direction.valueOf(direction));
        algoOrder.setConditionType(AlgoOrder.ConditionType.valueOf(conditionType));
        algoOrder.setSize(new BigDecimal(sz));
        algoOrder.setPositionReducingOnly(reduceOnly);
        algoOrder.setCondition(buildCondition(conditionType, slTriggerPx, slTriggerPxType,
                tpTriggerPx, tpTriggerPxType, trailingPercents, activePx));
        PlaceAlgoOrderOkxRequest request = algoOrderMapper.domainToPlaceRequest(algoOrder, instId);
        return okxRestClient.placeAlgoOrder(request);
    }

    @DeleteMapping("/algo-order")
    @Operation(summary = "Сырая отмена standalone algo-order (семья из conditionType)")
    public OkxApiResponse<AlgoOrderAckOkxResponse> cancelAlgoOrder(@RequestParam String instId,
                                                                   @RequestParam String conditionType,
                                                                   @RequestParam(required = false) String algoId,
                                                                   @RequestParam(required = false) String algoClOrdId) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setConditionType(AlgoOrder.ConditionType.valueOf(conditionType));
        algoOrder.setExternalId(algoId);
        algoOrder.setInternalId(algoClOrdId);
        CancelAlgoOrderOkxRequest request = algoOrderMapper.domainToCancelRequest(algoOrder, instId);
        List<CancelAlgoOrderOkxRequest> body = List.of(request);
        return isAdvanceFamily(algoOrder.getConditionType())
                ? okxRestClient.cancelAdvanceAlgos(body)
                : okxRestClient.cancelAlgos(body);
    }

    @PostMapping("/position/close")
    @Operation(summary = "Сырое закрытие позиции по инструменту")
    public OkxApiResponse<OrderAckOkxResponse> closePosition(@RequestParam String instId, @RequestParam String ccy) {
        ClosePositionOkxRequest request = new ClosePositionOkxRequest();
        request.setInstId(instId);
        request.setMgnMode(Constants.Okx.TD_MODE_ISOLATED);
        request.setPosSide(Constants.Okx.POS_SIDE_NET);
        request.setCcy(ccy);
        request.setAutoCxl(Boolean.TRUE);
        return okxRestClient.closePosition(request);
    }

    /** Advance-семья (trailing/move_order_stop) → cancel-advance-algos; иначе ordinary cancel-algos. */
    private boolean isAdvanceFamily(AlgoOrder.ConditionType type) {
        return switch (type) {
            case TRAILING_PERCENTS, TRAILING_VALUE -> true;
            default -> false;
        };
    }

    private Condition buildCondition(String conditionType, String slTriggerPx, String slTriggerPxType,
                                     String tpTriggerPx, String tpTriggerPxType,
                                     String trailingPercents, String activePx) {
        Condition condition = new Condition();
        condition.setType(AlgoOrder.ConditionType.valueOf(conditionType));
        if (nonNull(slTriggerPx) || nonNull(tpTriggerPx)) {
            Trigger trigger = new Trigger();
            if (nonNull(slTriggerPx)) {
                trigger.setStopLoss(new TriggerPrice(triggerType(slTriggerPxType), new BigDecimal(slTriggerPx),
                        null, null));
            }
            if (nonNull(tpTriggerPx)) {
                trigger.setTakeProfit(new TriggerPrice(triggerType(tpTriggerPxType), new BigDecimal(tpTriggerPx),
                        null, null));
            }
            condition.setTrigger(trigger);
        }
        if (nonNull(trailingPercents) || nonNull(activePx)) {
            Trailing trailing = new Trailing();
            if (nonNull(trailingPercents)) {
                trailing.setTrailingPercents(new BigDecimal(trailingPercents));
            }
            if (nonNull(activePx)) {
                trailing.setActivationPrice(new TriggerPrice(null, new BigDecimal(activePx), null, null));
            }
            condition.setTrailing(trailing);
        }
        return condition;
    }

    private AlgoOrder.TriggerPriceType triggerType(String type) {
        return isNull(type) ? null : AlgoOrder.TriggerPriceType.valueOf(type);
    }
}
