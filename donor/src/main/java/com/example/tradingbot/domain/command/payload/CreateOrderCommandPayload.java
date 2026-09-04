package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Параметры CREATE_ORDER. Хранит минимум для создания локального Order;
 * client id / external id берутся из создаваемой сущности.
 * positionSide/marginMode — generic intent (OKX adapter всё равно ставит
 * isolated/net). См. docs/components/CreateOrderExecutor.md.
 */
@Value
@Builder
public class CreateOrderCommandPayload implements ServiceCommandPayload {

    /** Бизнес-тип ордера. */
    Order.Type orderType;

    /** Нормализованное торговое направление стратегии. */
    StrategyTradeDirection strategyDirection;

    /** Сторона (buy/sell). */
    String side;

    /** Сторона позиции (generic intent). */
    String positionSide;

    /** Инструмент на бирже (instId). */
    String instrumentExternalId;

    /** Режим маржи (generic intent). */
    String marginMode;

    /** Тип исполнения (limit/market intent). */
    String executionType;

    /** Размер в контрактах (результат SizeCalculator). */
    BigDecimal sizeContracts;

    /** Цена размещения. */
    BigDecimal price;

    /** Слать ли цену на биржу (market-like — нет). */
    Boolean sendPriceToExchange;

    /** Доменное намерение reduce-only. */
    Boolean positionReducingOnly;

    /** Attached protection при создании со стартовым SL (nullable). */
    AttachedProtectionPayload attachedProtection;

    /** Транш, чью экспозицию нога создаёт или гасит. */
    Long dealTrancheId;

    /**
     * Цена входа, по которой считался риск ноги. У рыночного входа —
     * расчётная референс-цена, на биржу не отправляемая.
     */
    BigDecimal plannedEntryPrice;

    /** Уровень стопа, под который считался риск ноги. */
    BigDecimal plannedStopPrice;

    /** Плановый риск ноги — убыток на её стопе при постановке. */
    BigDecimal plannedRiskAmount;

    /** Валюта планового риска ноги — расчётная валюта инструмента. */
    String plannedRiskCurrency;

    /** Размер контракта на момент постановки ноги. */
    BigDecimal plannedContractValue;
}
