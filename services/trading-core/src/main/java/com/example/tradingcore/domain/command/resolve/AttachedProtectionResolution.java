package com.example.tradingcore.domain.command.resolve;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import lombok.Value;

/**
 * Исход резолва встроенной защиты. Отличается от общего
 * {@code StatusResolveResult} третьим состоянием, которого у прочих
 * резолверов нет: ИСХОД НЕ ОПРЕДЕЛЁН — пустой разбор истории есть
 * отсутствие факта, а не факт. Терминал на нём не ставится, причина не
 * пишется, статус остаётся прежним, а проход обязан поднять сигнал.
 *
 * <p>Флаг явный, а не выводимый из пустого статуса: обязанность поднять
 * сигнал должна быть видна на call-site, а не подразумеваться. RVO
 * прохода (docs/lifecycles/Order.md §«Пустой разбор истории»).
 */
@Value
public class AttachedProtectionResolution {

    /** Доменный статус; пусто — статус не меняется. */
    AttachedAlgoOrder.Status status;

    /** Кандидат причины; применяется write-once. */
    AttachedAlgoOrder.CloseReason closeReason;

    /**
     * Исход НЕ ОПРЕДЕЛЁН: разбор истории не дал записи ни одной ногой.
     * Оснований несколько, и ни одно не отличимо от «записи не было».
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

    /**
     * Исход «ждать»: защита не двигается — статус прежний, причины нет, — и
     * сигнала нет. Тропы две, и обе не дают факта о защите: у родителя без
     * наблюдения площадка о нём не показала ничего
     * (docs/spec/order-lifecycle.json, {@code attachedOutcomeByParent}); у
     * терминала родителя, впервые показанного этой добычей, пустой разбор
     * мерит задержку постановки ({@code searchExhaustedOutcome}). Отличается
     * от {@link #undetermined()} тем, что сигнала не требует: судьбу защиты
     * выводит следующее наблюдение, а не человек.
     */
    public static AttachedProtectionResolution waiting() {
        return new AttachedProtectionResolution(null, null, false);
    }

    /** Есть ли что применять к сущности. */
    public Boolean hasStatus() {
        return nonNull(status);
    }
}
