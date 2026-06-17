package com.example.tradingbot.api.controller;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.core.fill.external_snapshot.FillExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.model.trade.market_price_data.external_snapshot.MarketPriceDataExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.integration.service.okx.OkxRestClient;
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
 * Прокси/диагностический контроллер для ручного прогона методов
 * {@link IntegrationService} против биржи (demo/prod по конфигу OKX).
 * Отдаёт нормализованные снапшоты/ACK напрямую — инструмент
 * тестирования интеграции шага 4, не часть продуктового API. Write-
 * эндпоинты строят минимальные domain-объекты из параметров. Растёт
 * вместе с методами IntegrationService.
 */
@RestController
@RequestMapping("/api/proxy/okx")
@RequiredArgsConstructor
@Tag(name = "OKX proxy", description = "Ручной прогон методов IntegrationService (тестирование интеграции)")
public class OkxProxyController {

    private final IntegrationService integrationService;
    private final OkxRestClient okxRestClient;

    /**
     * Диагностическое чтение account config (acctLv/posMode) — прямой
     * сырой signed GET через приватный клиент (surface-gap: метода/DTO в
     * клиентском слое нет; под разовую диагностику F3b). Read-only.
     */
    @GetMapping("/account-config")
    @Operation(summary = "[диагностика] Сырой account config OKX (acctLv/posMode)")
    public String getAccountConfig() {
        return okxRestClient.getAccountConfig();
    }

    @GetMapping("/instrument")
    @Operation(summary = "Спецификация инструмента с биржи")
    public InstrumentExternalSnapshot getInstrument(@RequestParam String instId, @RequestParam String instType) {
        return integrationService.getInstrument(instId, instType);
    }

    @GetMapping("/market-price")
    @Operation(summary = "Рыночная цена (тикер) инструмента — live last/ask/bid")
    public MarketPriceDataExternalSnapshot getMarketPriceData(@RequestParam String instId) {
        return integrationService.getMarketPriceData(instId);
    }

    @GetMapping("/order")
    @Operation(summary = "Ordinary order по ordId или clOrdId")
    public OrderExternalSnapshot getOrder(@RequestParam String instId,
                                          @RequestParam(required = false) String ordId,
                                          @RequestParam(required = false) String clOrdId) {
        return integrationService.getOrder(instId, ordId, clOrdId);
    }

    @GetMapping("/position")
    @Operation(summary = "Позиция по инструменту")
    public PositionExternalSnapshot getPosition(@RequestParam String instId) {
        return integrationService.getPosition(instId);
    }

    @GetMapping("/algo-order")
    @Operation(summary = "Standalone algo-order по algoId или algoClOrdId")
    public AlgoOrderExternalSnapshot getAlgoOrder(@RequestParam String instId,
                                                  @RequestParam(required = false) String algoId,
                                                  @RequestParam(required = false) String algoClOrdId) {
        return integrationService.getAlgoOrder(instId, algoId, algoClOrdId);
    }

    @GetMapping("/balance")
    @Operation(summary = "Баланс аккаунта по валюте")
    public BalanceContainerExternalSnapshot getBalance(@RequestParam String ccy) {
        return integrationService.getBalance(ccy);
    }

    @GetMapping("/fills")
    @Operation(summary = "Исполнения (fills) по инструменту")
    public List<FillExternalSnapshot> getFills(@RequestParam String instId,
                                               @RequestParam(required = false) String after,
                                               @RequestParam(required = false) Integer limit) {
        return integrationService.getFills(instId, after, limit);
    }

    @PostMapping("/order")
    @Operation(summary = "Постановка ordinary order (минимальный Order из параметров)")
    public ExchangeAck placeOrder(@RequestParam String instId,
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
        return integrationService.placeOrder(order, instId);
    }

    @DeleteMapping("/order")
    @Operation(summary = "Отмена ordinary order по ordId или clOrdId")
    public ExchangeAck cancelOrder(@RequestParam String instId,
                                   @RequestParam(required = false) String ordId,
                                   @RequestParam(required = false) String clOrdId) {
        Order order = new Order();
        order.setExternalId(ordId);
        order.setInternalId(clOrdId);
        return integrationService.cancelOrder(order, instId);
    }

    @PostMapping("/algo-order")
    @Operation(summary = "Постановка standalone algo-order (минимальный AlgoOrder из параметров)")
    public ExchangeAck placeAlgoOrder(@RequestParam String instId,
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
        return integrationService.placeAlgoOrder(algoOrder, instId);
    }

    @DeleteMapping("/algo-order")
    @Operation(summary = "Отмена standalone algo-order (семья из conditionType)")
    public ExchangeAck cancelAlgoOrder(@RequestParam String instId,
                                       @RequestParam String conditionType,
                                       @RequestParam(required = false) String algoId,
                                       @RequestParam(required = false) String algoClOrdId) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setConditionType(AlgoOrder.ConditionType.valueOf(conditionType));
        algoOrder.setExternalId(algoId);
        algoOrder.setInternalId(algoClOrdId);
        return integrationService.cancelAlgoOrder(algoOrder, instId);
    }

    @PostMapping("/position/close")
    @Operation(summary = "Закрытие позиции по инструменту")
    public ExchangeAck closePosition(@RequestParam String instId, @RequestParam String ccy) {
        return integrationService.closePosition(instId, ccy);
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
