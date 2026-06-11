package com.example.tradingbot.domain.command.executor;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;

/**
 * Исполнитель одной атомарной ServiceCommand. Каждый поддерживает один
 * {@link ServiceCommandType}; диспетчер {@code ServiceCommandExecutor}
 * маршрутизирует по нему. Executor не принимает торговых решений; ловлю
 * ошибок и retry держит диспетчер. {@code actionState} — null для
 * не-action команд (REFRESH_BALANCE). См.
 * docs/components/ServiceCommandExecutor.md.
 */
public interface CommandExecutor {

    /** Тип команды, который исполняет этот executor. */
    ServiceCommandType supportedType();

    /** Исполнить команду; обновляет сущности/DealActionState, возвращает исход. */
    ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState, DealContext dealContext);
}
