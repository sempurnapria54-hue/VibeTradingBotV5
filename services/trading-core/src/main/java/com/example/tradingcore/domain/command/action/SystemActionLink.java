package com.example.tradingcore.domain.command.action;

import com.example.tradingcore.domain.command.ServiceCommandPayload;
import com.example.tradingcore.domain.command.ServiceCommandType;
import lombok.Value;

/**
 * Звено системного действия — пара «команда + её параметры», выведенная из
 * подтверждённого факта сделки.
 *
 * <p>Отдельный тип, а не рекорд внутри сервиса
 * (.claude/rules/codestyle.md §«Строгие правила»): звено возвращают
 * несколько ветвей вывода, и без имени пара читалась бы позиционно.
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией у неё нет.
 *
 * <p>См. docs/components/SystemActionExecutor.md.
 */
@Value
public class SystemActionLink {

    /** Команда звена. */
    ServiceCommandType type;

    /** Параметры звена; пусто — команда адресует саму сделку. */
    ServiceCommandPayload payload;
}
