package com.example.tradingbot.domain.command.resolve;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import lombok.Value;

/**
 * Исход резолва встроенной защиты. Отличается от общего
 * {@link StatusResolveResult} третьим состоянием, которого у прочих
 * резолверов нет: ИСХОД НЕ ОПРЕДЕЛЁН — пустой разбор истории есть
 * отсутствие факта, а не факт. Терминал на нём не ставится, причина не
 * пишется, статус остаётся прежним, а проход обязан поднять сигнал.
 * Флаг явный, а не выводимый из пустого статуса: обязанность поднять
 * сигнал должна быть видна на call-site, а не подразумеваться. RVO. См.
 * docs/lifecycles/Order.md §«Пустой разбор истории».
 */
@Value
public class AttachedProtectionResolution {

    /** Доменный статус; пусто — статус не меняется. */
    AttachedAlgoOrder.Status status;

    /** Candidate причины (nullable); применяется write-once. */
    AttachedAlgoOrder.CloseReason closeReason;

    /**
     * Исход НЕ ОПРЕДЕЛЁН: разбор истории не дал записи ни одной ногой.
     * Оснований четыре, и ни одно не отличимо от «записи не было».
     */
    Boolean outcomeUndetermined;

    /** Резолв дал состояние: статус применяется, причина — write-once. */
    public static AttachedProtectionResolution of(AttachedAlgoOrder.Status status,
                                                  AttachedAlgoOrder.CloseReason closeReason) {
        return new AttachedProtectionResolution(status, closeReason, false);
    }

    /** Исход не определён: терминал не ставится, поднимается сигнал. */
    public static AttachedProtectionResolution undetermined() {
        return new AttachedProtectionResolution(null, null, true);
    }

    /** Есть ли что применять к сущности. */
    public Boolean hasStatus() {
        return !isNull(status);
    }
}
