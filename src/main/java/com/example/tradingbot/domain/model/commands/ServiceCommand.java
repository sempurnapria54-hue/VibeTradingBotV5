package com.example.tradingbot.domain.model.commands;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.trade.strategy.StrategyActionType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStepType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCommand {

    /**
     * Тип атомарной сервисной команды.
     */
    private ServiceCommandType type;

    /**
     * Сделка, в рамках которой выполняется команда.
     */
    private Long dealId;

    /**
     * Инструмент сделки. Нужен для refresh/submit команд и трассировки.
     */
    private Long instrumentId;

    /**
     * Стратегия, из которой пришла команда.
     */
    private Long strategyId;

    /**
     * Детали стратегии для текущей фазы рынка. Nullable, используется только для трассировки.
     */
    private Long strategyDetailsId;

    /**
     * Статус сделки, из которого handler сгенерировал команду.
     */
    private Deal.Status sourceDealStatus;

    /**
     * Шаг стратегии, разрешённый текущим StateHandler.
     */
    private StrategyStepType sourceStepType;

    /**
     * Действие стратегии, из которого получена команда.
     */
    private StrategyActionType sourceActionType;

    /**
     * Стабильный id StrategyAction для восстановления после рестарта.
     */
    private Long strategyActionId;

    /**
     * Типизированные параметры конкретной команды.
     */
    private ServiceCommandPayload payload;
}
