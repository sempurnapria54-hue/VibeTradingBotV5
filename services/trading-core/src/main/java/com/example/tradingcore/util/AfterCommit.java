package com.example.tradingcore.util;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import lombok.experimental.UtilityClass;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Откладывает действие до коммита текущей транзакции.
 *
 * <p><b>Действие не откатывает вызывающего и не пишется при его откате:</b>
 * оно исполняется после коммита, поэтому отказ внутри него коммит уже не
 * отменит, а откат вызывающего не оставит следа. Данные действие обязано
 * писать СВОЕЙ транзакцией ({@code REQUIRES_NEW}): после коммита ресурсы
 * прежней ещё привязаны, и обычный вызов участвовал бы в ней без коммита.
 * Отказ действия ловит оно само — брошенный отсюда, он дошёл бы до
 * вызывающего после того, как его транзакция уже зафиксирована.
 *
 * <p>Без активной транзакции действие исполняется сразу: откладывать не до
 * чего.
 */
@UtilityClass
public class AfterCommit {

    /** Исполнить действие после коммита текущей транзакции либо сразу, если её нет. */
    public static void run(Runnable action) {
        if (isFalse(TransactionSynchronizationManager.isSynchronizationActive())) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
