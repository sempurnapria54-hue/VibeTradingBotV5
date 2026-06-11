package com.example.tradingbot.domain.model.core.algo_order;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Standalone algo-order, связанный с Deal: standalone SL/TP, OCO,
 * trailing stop, partial exit (reduce-only). Хранит локальный intent,
 * идентификаторы, доменный статус, сырой статус (диагностика), условие
 * срабатывания, рассчитанный размер, факты срабатывания, diagnostic
 * facts (связанные ordinary order ids). Не действие стратегии: связь
 * StrategyAction ↔ AlgoOrder — через DealActionState. См.
 * docs/models/domain/core/AlgoOrder.md, docs/lifecycles/AlgoOrder.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class AlgoOrder extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Сделка. */
    private Long dealId;

    /** stable client id (OKX algoClOrdId). */
    private String internalId;

    /** биржевой id (OKX algoId). */
    private String externalId;

    /** Доменный статус. */
    private Status status;

    /** Причина финализации / ERROR. */
    private CloseReason closeReason;

    /** Денормализованная проекция condition.type (обязательна, должна совпадать). */
    private ConditionType conditionType;

    /** Условие срабатывания (jsonb; только trigger/trailing). */
    private Condition condition;

    /** Рассчитанный materialized размер (для SWAP/FUTURES — контракты). */
    private BigDecimal size;

    /** Направление (closing long → SELL, short → BUY). */
    private Direction direction;

    /** Доменное намерение: только уменьшать позицию. */
    private Boolean positionReducingOnly;

    /** internalId предшественника в цепочке REPLACE (nullable; обратная ссылка выводится запросом). */
    private String replacesInternalId;

    /** Сырой статус биржи (OKX state) — диагностика, FSM напрямую не использует. */
    private String externalStatus;

    /** Код ошибки биржи (OKX failCode). */
    private String failCode;

    /** Фактический размер срабатывания (OKX actualSz) — не исходный size. */
    private BigDecimal externalSize;

    /** Фактическая цена срабатывания (OKX actualPx). */
    private BigDecimal externalPrice;

    /** Время срабатывания (OKX triggerTime). */
    private Instant externalTriggerTime;

    /** Связанные ordinary order ids (OKX ordId/ordIdList) — внешний факт, runtime на них не опирается. */
    private List<String> linkedOrderExternalIds;

    private static final Set<Status> LIVE_STATUSES =
            EnumSet.of(Status.CREATED, Status.PENDING, Status.ACTIVE, Status.PARTIALLY_COMPLETED);

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.CREATED, EnumSet.of(Status.PENDING, Status.ERROR),
            Status.PENDING, EnumSet.of(Status.ACTIVE, Status.COMPLETED, Status.CANCELED, Status.ERROR),
            Status.ACTIVE, EnumSet.of(Status.PARTIALLY_COMPLETED, Status.COMPLETED, Status.CANCELED, Status.ERROR),
            Status.PARTIALLY_COMPLETED, EnumSet.of(Status.COMPLETED, Status.CANCELED, Status.ERROR));

    /** Live: ещё существует / влияет на risk (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED). */
    public Boolean isLive() {
        return LIVE_STATUSES.contains(status);
    }

    /** Не live (терминальный статус). */
    public Boolean isNotLive() {
        return isFalse(isLive());
    }

    /** Отправлен на биржу; факт не подтверждён. */
    public void toPending() {
        transitTo(Status.PENDING);
    }

    /** Подтверждён активным фактом refresh (не по ACK). */
    public void toActive() {
        transitTo(Status.ACTIVE);
    }

    /** Частично сработал (recovery-status). */
    public void toPartiallyComplete() {
        transitTo(Status.PARTIALLY_COMPLETED);
    }

    /** Сработал полностью: COMPLETED + closeReason TRIGGERED (write-once). */
    public void toComplete() {
        transitTo(Status.COMPLETED);
        applyCloseReason(CloseReason.TRIGGERED);
    }

    /** Отменён: требует ненулевой reason. */
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

    /** Денормализованный conditionType должен совпадать с condition.type (оба не null). */
    public void validateConditionProjection() {
        if (isNull(conditionType) || isNull(condition) || isNull(condition.getType())
                || isFalse(Objects.equals(conditionType, condition.getType()))) {
            throw new IllegalStateException("AlgoOrder conditionType must match condition.type");
        }
    }

    private void transitTo(Status target) {
        Set<Status> allowed = isNull(status)
                ? EnumSet.of(Status.CREATED)
                : ALLOWED_TRANSITIONS.getOrDefault(status, EnumSet.noneOf(Status.class));
        if (isFalse(allowed.contains(target))) {
            throw new IllegalStateException("Illegal AlgoOrder transition " + status + " -> " + target);
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

    /** Направление algo-order. */
    public enum Direction {

        /** Покупка. */
        BUY,

        /** Продажа. */
        SELL
    }

    /** Доменный статус algo-order. Значения и переходы — docs/lifecycles/AlgoOrder.md. */
    public enum Status {

        /** Локальная сущность создана, на биржу не отправлена. */
        CREATED,

        /** Отправлен на биржу, факт не подтверждён. */
        PENDING,

        /** Активен на бирже. */
        ACTIVE,

        /** Частично сработал (exchange-driven recovery-status, не целевой сценарий). */
        PARTIALLY_COMPLETED,

        /** Сработал полностью. */
        COMPLETED,

        /** Отменён. */
        CANCELED,

        /** Ошибка. */
        ERROR
    }

    /** Причина финализации algo-order / ERROR. */
    public enum CloseReason {

        /** Сработал (triggered). */
        TRIGGERED,

        /** Отменён стратегией. */
        CANCELED_BY_STRATEGY,

        /** Стратегия заменила другим algo-order (REPLACE-ремодел). */
        REPLACED_BY_STRATEGY,

        /** Аварийный safety-flow / kill-switch. */
        KILL_SWITCH,

        /** Ручная отмена. */
        MANUAL_CANCEL,

        /** Не найден после refresh/search/history цикла. */
        MISSING_AFTER_REFRESH,

        /** Постановка ордера на бирже не удалась. */
        ORDER_FAILED,

        /** Частичный отказ. */
        PARTIALLY_FAILED,

        /** Неизвестный внешний статус. */
        UNKNOWN_EXTERNAL_STATUS,

        /** Нарушение exchange-инварианта. */
        EXCHANGE_INVARIANT_VIOLATION,

        /** Fallback. */
        UNKNOWN
    }

    /** Тип условия срабатывания algo-order. */
    public enum ConditionType {

        /** Стоп-лосс на полное закрытие. */
        STOP_LOSS,

        /** Тейк-профит на полное закрытие. */
        TAKE_PROFIT,

        /** OCO: стоп-лосс + тейк-профит на полное закрытие. */
        OCO_FULL,

        /** Трейлинг-стоп с callback в процентах. */
        TRAILING_PERCENTS,

        /** Трейлинг-стоп с callback в абсолютном значении. */
        TRAILING_VALUE,

        /** Частичный тейк-профит (reduce-only доля позиции). */
        PARTIAL_TAKE_PROFIT,

        /** Частичный стоп-лосс (reduce-only доля позиции). */
        PARTIAL_STOP_LOSS
    }

    /** Внутренний тип trigger-цены биржи. */
    public enum TriggerPriceType {

        /** Последняя цена сделки (last). */
        LAST,

        /** Индексная цена (index). */
        INDEX,

        /** Марк-цена (mark). */
        MARK
    }
}
