package com.example.tradingbot.domain.model.core.order;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Attached protection — embedded защитный algo-order, созданный вместе с
 * parent Order (OKX attachAlgoOrds). На первом этапе — embedded-часть
 * Order, не standalone AlgoOrder; в standalone автоматически не
 * материализуется (даже если биржа вернула algo identifiers). См.
 * docs/models/domain/core/Order.md (§AttachedAlgoOrder),
 * docs/lifecycles/Order.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class AttachedAlgoOrder extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Parent Order. */
    private Long orderId;

    /** Межсервисный id (OKX attachAlgoClOrdId). Ключ матчинга. */
    private String internalId;

    /** Id attached algo на бирже, пока он attached (OKX attachAlgoId). */
    private String externalAttachedId;

    /** Внешний id algo-order, если биржа возвращает (не материализует в standalone). */
    private String externalId;

    /** Доменный статус. */
    private Status status;

    /** Причина финализации. */
    private CloseReason closeReason;

    /** Внутренний тип attached-защиты. */
    private Type type;

    /** Сырой внешний статус (у OKX attachAlgoOrds полноценного state нет). */
    private String externalStatus;

    /** Биржевой тип attached protection. */
    private String externalType;

    /** Размер. */
    private BigDecimal size;

    /** Триггерная цена SL (текущий проект — attached SL). */
    private BigDecimal stopLossTriggerPrice;

    private static final Set<Status> ACTIVE_LIKE_STATUSES = EnumSet.of(Status.PENDING, Status.ACTIVE);

    private static final Set<Status> TERMINAL_STATUSES =
            EnumSet.of(Status.COMPLETED, Status.CANCELED, Status.ERROR);

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.CREATED, EnumSet.of(Status.PENDING, Status.ERROR),
            Status.PENDING, EnumSet.of(Status.ACTIVE, Status.CANCELED, Status.ERROR),
            Status.ACTIVE, EnumSet.of(Status.COMPLETED, Status.CANCELED, Status.ERROR));

    /** Active-like (PENDING/ACTIVE): ещё существует, влияет на защиту. */
    public Boolean isActiveLike() {
        return ACTIVE_LIKE_STATUSES.contains(status);
    }

    /** Терминальный статус (COMPLETED/CANCELED/ERROR). */
    public Boolean isTerminal() {
        return TERMINAL_STATUSES.contains(status);
    }

    /** Биржевой тип attached задан. */
    public Boolean hasExternalType() {
        return isNotBlank(externalType);
    }

    /** Допустим ли переход в target по матрице. */
    public Boolean canTransitionTo(Status target) {
        Set<Status> allowed = isNull(status)
                ? EnumSet.of(Status.CREATED)
                : ALLOWED_TRANSITIONS.getOrDefault(status, EnumSet.noneOf(Status.class));
        return allowed.contains(target);
    }

    /** Отправлена вместе с parent; active-факт не подтверждён. */
    public void toPending() {
        transitTo(Status.PENDING);
    }

    /** Подтверждена refresh-фактами. */
    public void toActive() {
        transitTo(Status.ACTIVE);
    }

    /** Сработала: COMPLETED + closeReason TRIGGERED (write-once). */
    public void toComplete() {
        transitTo(Status.COMPLETED);
        applyCloseReason(CloseReason.TRIGGERED);
    }

    /** Отменена/снята: требует ненулевой reason. */
    public void toCancel(CloseReason reason) {
        requireReason(reason);
        transitTo(Status.CANCELED);
        applyCloseReason(reason);
    }

    /** Ошибочное состояние: требует ненулевой reason. */
    public void toError(CloseReason reason) {
        requireReason(reason);
        transitTo(Status.ERROR);
        applyCloseReason(reason);
    }

    private void transitTo(Status target) {
        if (isFalse(canTransitionTo(target))) {
            throw new IllegalStateException("Illegal AttachedAlgoOrder transition " + status + " -> " + target);
        }
        this.status = target;
    }

    private void applyCloseReason(CloseReason reason) {
        if (isNull(closeReason)) {
            this.closeReason = reason;
        }
    }

    private void requireReason(CloseReason reason) {
        if (isNull(reason)) {
            throw new IllegalArgumentException("closeReason is required");
        }
    }

    /** Внутренний тип attached-защиты. */
    public enum Type {

        /** Встроенный stop-loss входного ордера. */
        ATTACHED_STOP_LOSS
    }

    /** Доменный статус attached protection. Переходы — docs/lifecycles/Order.md. */
    public enum Status {

        /** Создана локально. */
        CREATED,

        /** Отправлена/ожидает подтверждения. */
        PENDING,

        /** Активна. */
        ACTIVE,

        /** Завершена (сработала). */
        COMPLETED,

        /** Отменена. */
        CANCELED,

        /** Ошибка. */
        ERROR
    }

    /** Причина финализации attached protection. */
    public enum CloseReason {

        /** Сработала (triggered). */
        TRIGGERED,

        /** Снята после подтверждения standalone main protection. */
        SWITCHED_BY_STRATEGY,

        /** Parent order отменён. */
        PARENT_ORDER_CANCELED,

        /** Аварийный safety-flow / kill-switch. */
        KILL_SWITCH,

        /** Ручная отмена. */
        MANUAL_CANCEL,

        /** Не найдена после refresh. */
        MISSING_AFTER_REFRESH,

        /** Была активной, больше не подтверждается, и standalone protection отсутствует. */
        PROTECTION_LOST,

        /** Неизвестный внешний статус. */
        UNKNOWN_EXTERNAL_STATUS,

        /** Fallback. */
        UNKNOWN
    }
}
