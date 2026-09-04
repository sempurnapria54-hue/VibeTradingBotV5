package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Параметры CREATE_ALGO_ORDER: тип условия, направление, инструмент,
 * рассчитанный размер и готовое дерево condition (trigger/trailing с
 * рассчитанными ценами). closeFraction не передаётся — command-layer
 * получает готовый sizeContracts. См.
 * docs/components/CreateAlgoOrderExecutor.md.
 */
@Value
@Builder
public class CreateAlgoOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Транш, которому принадлежит условная заявка. Обязателен наравне со
     * сделкой: покрытие считается потраншево, и защита без транша не
     * атрибутируема — то есть в инварианте покрытия не участвует, оставаясь
     * при этом живым риском (docs/models/domain/core/AlgoOrder.md).
     */
    Long dealTrancheId;

    /** Тип условия срабатывания (SL/TP/OCO/PARTIAL/TRAILING). */
    AlgoOrder.ConditionType conditionType;

    /** Направление (BUY/SELL). */
    AlgoOrder.Direction direction;

    /** Инструмент на бирже (instId). */
    String instrumentExternalId;

    /** Доменное намерение reduce-only (для защитных почти всегда true). */
    Boolean positionReducingOnly;

    /** Рассчитанный размер в контрактах. */
    BigDecimal sizeContracts;

    /** Дерево условия (trigger/trailing с рассчитанными ценами). */
    Condition condition;
}
