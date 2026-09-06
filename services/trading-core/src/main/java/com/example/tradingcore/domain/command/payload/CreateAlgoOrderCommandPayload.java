package com.example.tradingcore.domain.command.payload;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Параметры создания отдельной условной заявки: род условия, направление,
 * рассчитанный размер и готовое дерево условия.
 *
 * <p><b>Планового риска здесь нет</b>, и это не пропуск: входной тропы
 * условной заявкой не существует — все роды условия защитные либо
 * закрывающие (docs/components/CreateAlgoOrderExecutor.md §«Плановый риск
 * здесь не пишется»).
 */
@Value
@Builder
public class CreateAlgoOrderCommandPayload implements ServiceCommandPayload {

    /**
     * Транш, которому принадлежит условная заявка. Обязателен наравне со
     * сделкой: покрытие считается потраншево, и защита без транша не
     * атрибутируема, оставаясь при этом живым риском
     * (docs/models/domain/core/AlgoOrder.md).
     */
    Long dealTrancheId;

    /** Род условия срабатывания. */
    AlgoOrder.ConditionType conditionType;

    /** Направление заявки. */
    AlgoOrder.Direction direction;

    /** Доменное намерение «только уменьшать позицию». */
    Boolean positionReducingOnly;

    /** Рассчитанный размер в контрактах. */
    BigDecimal sizeContracts;

    /** Дерево условия с рассчитанными ценами. */
    Condition condition;
}
