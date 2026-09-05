package com.example.connector.okx.api.controller;

import com.example.connector.okx.api.model.AlgoOrderHistoryApiQuery;
import com.example.connector.okx.api.model.ExchangeOrderLookupApiQuery;
import com.example.connector.okx.gateway.ExchangeGateway;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import io.swagger.v3.oas.annotations.Operation;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Приватные операции площадки: команды и добыча факта по конкретному
 * счёту. Транспортная форма входного контракта
 * ({@link ExchangeGateway}); дом перечня —
 * {@code docs/components/IntegrationService.md} §«Входной контракт».
 *
 * <p><b>Счёт стои́т в пути, а не в теле, и это не оформление.</b>
 * Коннектор стейтлесс: счёт — операнд КАЖДОГО приватного вызова, по нему
 * берутся ключи. В пути он виден сетевой политике и логу доступа, а тело
 * запроса ими не читается.
 *
 * <p><b>На проводе — доменные модели, а не отдельный слой api.</b> Так
 * объявлено домом контрактов: формы запросов суть классы артефакта
 * {@code domain-model} ({@code docs/architecture/contracts.md} §«Два
 * канала»). Граница здесь внутрикластерная, обе стороны собираются из
 * одного монорепозитория, и промежуточный слой был бы третьей копией той
 * же формы — той самой, что стареет первой. Правило «наружу не отдаём
 * ключ базы» ({@code .claude/rules/codestyle.md} §«Идентичность наружу»)
 * этим не задето: его предмет — поверхность ПРОДУКТА, чей дом
 * {@code bff}, а не вызов ядра к своему коннектору.
 *
 * <p><b>Ответ команды — подтверждение приёма, не исход.</b> Что
 * произошло на самом деле, узнаётся наблюдением
 * ({@code docs/rules/ack-not-runtime-truth.md}).
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountInternalId}")
@RequiredArgsConstructor
public class ExchangeAccountOperationsController {

    private final ExchangeGateway gateway;

    // --- команды площадке ------------------------------------------------

    @Operation(summary = "Выставить заявку")
    @PostMapping("/orders")
    public ExchangeAck placeOrder(@PathVariable String accountInternalId,
                                  @RequestParam String externalInstrumentId,
                                  @RequestBody Order order) {
        return gateway.placeOrder(accountInternalId, order, externalInstrumentId);
    }

    @Operation(summary = "Снять выставленную заявку")
    @PostMapping("/orders/cancellations")
    public ExchangeAck cancelOrder(@PathVariable String accountInternalId,
                                   @RequestParam String externalInstrumentId,
                                   @RequestBody Order order) {
        return gateway.cancelOrder(accountInternalId, order, externalInstrumentId);
    }

    @Operation(summary = "Выставить условную заявку")
    @PostMapping("/algo-orders")
    public ExchangeAck placeAlgoOrder(@PathVariable String accountInternalId,
                                      @RequestParam String externalInstrumentId,
                                      @RequestBody AlgoOrder algoOrder) {
        return gateway.placeAlgoOrder(accountInternalId, algoOrder, externalInstrumentId);
    }

    @Operation(summary = "Снять условную заявку")
    @PostMapping("/algo-orders/cancellations")
    public ExchangeAck cancelAlgoOrder(@PathVariable String accountInternalId,
                                       @RequestParam String externalInstrumentId,
                                       @RequestBody AlgoOrder algoOrder) {
        return gateway.cancelAlgoOrder(accountInternalId, algoOrder, externalInstrumentId);
    }

    @Operation(summary = "Снять защиту, привязанную к заявке")
    @PostMapping("/attached-protections/cancellations")
    public ExchangeAck cancelAttachedProtection(@PathVariable String accountInternalId,
                                                @RequestParam String externalInstrumentId,
                                                @RequestBody AttachedAlgoOrder attached) {
        return gateway.cancelAttachedProtection(accountInternalId, attached, externalInstrumentId);
    }

    @Operation(summary = "Закрыть позицию по рынку")
    @PostMapping("/positions/closures")
    public ExchangeAck closePosition(@PathVariable String accountInternalId,
                                     @RequestParam String externalInstrumentId,
                                     @RequestParam String settleCurrency) {
        return gateway.closePosition(accountInternalId, externalInstrumentId, settleCurrency);
    }

    @Operation(summary = "Настроить плечо счёта на инструменте")
    @PostMapping("/leverage")
    public ExchangeAck setLeverage(@PathVariable String accountInternalId,
                                   @RequestParam String externalInstrumentId,
                                   @RequestParam Integer leverage) {
        return gateway.setLeverage(accountInternalId, externalInstrumentId, leverage);
    }

    // --- добыча факта ------------------------------------------------------

    @Operation(summary = "Заявка по идентификатору")
    @GetMapping("/orders/lookup")
    public Order getOrder(@PathVariable String accountInternalId,
                          @ParameterObject ExchangeOrderLookupApiQuery lookup) {
        return gateway.getOrder(accountInternalId, lookup.getExternalInstrumentId(),
                lookup.getExternalId(), lookup.getInternalId());
    }

    @Operation(summary = "Все живые заявки счёта")
    @GetMapping("/orders/pending")
    public List<Order> getAllPendingOrders(@PathVariable String accountInternalId) {
        return gateway.getAllPendingOrders(accountInternalId);
    }

    @Operation(summary = "Живые заявки инструмента")
    @GetMapping("/orders/pending/instrument")
    public List<Order> getPendingOrders(@PathVariable String accountInternalId,
                                        @RequestParam String externalInstrumentId) {
        return gateway.getPendingOrders(accountInternalId, externalInstrumentId);
    }

    @Operation(summary = "История заявок инструмента")
    @GetMapping("/orders/history")
    public List<Order> getOrderHistory(@PathVariable String accountInternalId,
                                       @RequestParam String externalInstrumentId) {
        return gateway.getOrderHistory(accountInternalId, externalInstrumentId);
    }

    @Operation(summary = "Условная заявка по идентификатору")
    @GetMapping("/algo-orders/lookup")
    public AlgoOrder getAlgoOrder(@PathVariable String accountInternalId,
                                  @ParameterObject ExchangeOrderLookupApiQuery lookup) {
        return gateway.getAlgoOrder(accountInternalId, lookup.getExternalInstrumentId(),
                lookup.getExternalId(), lookup.getInternalId());
    }

    @Operation(summary = "Все живые условные заявки счёта")
    @GetMapping("/algo-orders/pending")
    public List<AlgoOrder> getAllPendingAlgoOrders(@PathVariable String accountInternalId) {
        return gateway.getAllPendingAlgoOrders(accountInternalId);
    }

    @Operation(summary = "Живые условные заявки инструмента одного рода условия")
    @GetMapping("/algo-orders/pending/instrument")
    public List<AlgoOrder> getPendingAlgoOrders(@PathVariable String accountInternalId,
                                                @RequestParam String externalInstrumentId,
                                                @RequestParam AlgoOrder.ConditionType conditionType) {
        return gateway.getPendingAlgoOrders(accountInternalId, externalInstrumentId, conditionType);
    }

    @Operation(summary = "История условных заявок инструмента")
    @GetMapping("/algo-orders/history")
    public List<AlgoOrder> getAlgoOrderHistory(@PathVariable String accountInternalId,
                                               @ParameterObject AlgoOrderHistoryApiQuery query) {
        return gateway.getAlgoOrderHistory(accountInternalId, query.getExternalInstrumentId(),
                query.getConditionType(), query.getExternalId());
    }

    @Operation(summary = "Живые материализованные защиты инструмента")
    @GetMapping("/attached-protections/pending")
    public List<AttachedAlgoOrder> getPendingMaterializedProtections(
            @PathVariable String accountInternalId,
            @RequestParam String externalInstrumentId) {
        return gateway.getPendingMaterializedProtections(accountInternalId, externalInstrumentId);
    }

    @Operation(summary = "История материализованных защит по одной ноге")
    @GetMapping("/attached-protections/history")
    public List<AttachedAlgoOrder> getMaterializedProtectionHistory(
            @PathVariable String accountInternalId,
            @RequestParam String externalInstrumentId,
            @RequestParam ProtectionHistoryLeg leg) {
        return gateway.getMaterializedProtectionHistory(accountInternalId, externalInstrumentId, leg);
    }

    @Operation(summary = "Закрытые эпизоды позиции в окне")
    @GetMapping("/positions/closed")
    public List<Position> getPositionCloseRecords(
            @PathVariable String accountInternalId,
            @RequestParam String externalInstrumentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime windowBegin) {
        return gateway.getPositionCloseRecords(accountInternalId, externalInstrumentId, windowBegin);
    }

    @Operation(summary = "Все позиции счёта")
    @GetMapping("/positions")
    public List<Position> getPositions(@PathVariable String accountInternalId) {
        return gateway.getPositions(accountInternalId);
    }

    @Operation(summary = "Позиция инструмента")
    @GetMapping("/positions/instrument")
    public Position getPosition(@PathVariable String accountInternalId,
                                @RequestParam String externalInstrumentId) {
        return gateway.getPosition(accountInternalId, externalInstrumentId);
    }

    @Operation(summary = "Баланс расчётной валюты")
    @GetMapping("/balance")
    public BalanceContainer getBalance(@PathVariable String accountInternalId,
                                       @RequestParam String settleCurrency) {
        return gateway.getBalance(accountInternalId, settleCurrency);
    }

    @Operation(summary = "Движения средств за окно")
    @GetMapping("/bills")
    public List<DealCashFlow> getBills(
            @PathVariable String accountInternalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime begin,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return gateway.getBills(accountInternalId, begin, end);
    }

    @Operation(summary = "Архив движений средств за окно")
    @GetMapping("/bills/archive")
    public List<DealCashFlow> getBillsArchive(
            @PathVariable String accountInternalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime begin,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return gateway.getBillsArchive(accountInternalId, begin, end);
    }

    @Operation(summary = "Ставки комиссии счёта")
    @GetMapping("/trade-fee-rates")
    public List<TradeFeeRate> getTradeFeeRates(@PathVariable String accountInternalId,
                                               @RequestParam String externalInstrumentType) {
        return gateway.getTradeFeeRates(accountInternalId, externalInstrumentType);
    }
}
