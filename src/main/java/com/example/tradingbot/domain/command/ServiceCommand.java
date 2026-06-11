package com.example.tradingbot.domain.command;

import lombok.Builder;
import lombok.Value;

/**
 * Атомарная runtime-команда над runtime-сущностью, передаваемая
 * executor'у. RVO — не persisted entity и не durable command queue:
 * после рестарта команды как очередь не восстанавливаются, нужная
 * пересобирается по фактам (docs/rules/command-lifecycle.md).
 * Происхождение восстанавливается через DealActionState — strategyId/
 * strategyDetailId не хранит. Отвечает «какую простую операцию
 * выполнить»; executor — «как технически», FSM — «зачем сейчас». См.
 * docs/components/models/ServiceCommand.md.
 */
@Value
@Builder
public class ServiceCommand {

    /** Тип атомарной операции. */
    ServiceCommandType type;

    /** Сделка, в рамках которой выполняется команда. */
    Long dealId;

    /** Runtime-состояние action стратегии (связь со StrategyAction); null для не-action команд. */
    Long dealActionStateId;

    /** Параметры выполнения. */
    ServiceCommandPayload payload;
}
