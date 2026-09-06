package com.example.tradingcore.domain.command.executor;

import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;

/**
 * Исполнитель одной атомарной команды. Каждый поддерживает ровно один тип
 * — по нему маршрутизирует диспетчер.
 *
 * <p><b>Торговых решений исполнитель не принимает</b>, преконтроль риска
 * не зовёт (тот отработал до создания команды) и статус сделки не двигает
 * — кроме звеньев, чьё ребро едет их же транзакцией. Ловлю ошибок и
 * повторы держит диспетчер
 * (docs/components/ServiceCommandExecutor.md).
 *
 * <p>Строка исполнения пуста у дочисток: анкера у них нет, и бюджета
 * отказов тоже.
 */
public interface CommandExecutor {

    /** Тип команды, который исполняет этот исполнитель. */
    ServiceCommandType supportedType();

    /** Исполнить команду: обновляет сущности и строку исполнения, возвращает исход. */
    ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                          DealContext dealContext);
}
