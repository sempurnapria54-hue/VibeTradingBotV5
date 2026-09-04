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
 * выполнить»; executor — «как технически», FSM — «зачем сейчас».
 *
 * <p><b>Анкер учёта один</b> — строка исполнения. Отдельного анкера под
 * финализацию нет: системное действие несёт свою строку наравне со
 * стратегийным (docs/models/domain/other/DealActionState.md). Пусто
 * ровно у дочистки: отмены и закрытие позиции эмитируются напрямую, без
 * анкера (docs/components/SystemActionExecutor.md §«Дочистка звеном не
 * является»).
 *
 * <p>См. docs/components/models/ServiceCommand.md.
 */
@Value
@Builder
public class ServiceCommand {

    /** Тип атомарной операции. */
    ServiceCommandType type;

    /** Сделка, в рамках которой выполняется команда. */
    Long dealId;

    /** Строка исполнения — анкер идемпотентности, повторов и цели; null у дочистки. */
    Long dealActionStateId;

    /** Параметры выполнения. */
    ServiceCommandPayload payload;
}
