package com.example.tradingbot.domain.model.core.order;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Attached protection — embedded защитный algo-order, созданный вместе с
 * parent Order (OKX attachAlgoOrds). На первом этапе — embedded-часть
 * Order, не standalone AlgoOrder; в standalone автоматически не
 * материализуется (даже если биржа вернула algo identifiers). См.
 * docs/models/domain/core/Order.md (§«Встроенная защита (AttachedAlgoOrder)»),
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

    /**
     * Сырой внешний статус — {@code state} самостоятельной записи цикла
     * добычи; у элемента attachAlgoOrds родителя пуст (своего статуса тот
     * не несёт). Диагностика: исход кодирует нога разбора, не этот статус.
     */
    private String externalStatus;

    /**
     * Код отказа источника — операнд разбора того, какая из троп потери
     * покрытия сработала. Пишут обе несущие его тропы: отказ постановки и
     * найденная разбором запись state=order_failed.
     */
    private String failCode;

    /** Биржевой тип attached protection. */
    private String externalType;

    /** Размер. */
    private BigDecimal size;

    /** Триггерная цена SL (текущий проект — attached SL). */
    private BigDecimal stopLossTriggerPrice;

    /**
     * Ценовая база триггера. У нашей строки — <b>объявленная</b> база:
     * пишет её создатель ноги из объявления стратегии, и колонка обязательна.
     * У прочитанной копии — <b>эхо</b> базы, которую площадка применила,
     * переведённое коннектором в словарь домена; пустое эхо — недобытый
     * факт, а не разрешение (docs/rules/absent-value-semantics.md). Эхо на
     * строку не переносится: перенос стёр бы то, с чем оно сверяется
     * ({@link #matchesEcho}; docs/models/domain/core/Order.md).
     */
    private AlgoOrder.TriggerPriceType triggerPriceType;

    private static final Set<Status> ACTIVE_LIKE_STATUSES = EnumSet.of(Status.PENDING, Status.ACTIVE);

    private static final Set<Status> TERMINAL_STATUSES = EnumSet.of(Status.COMPLETED, Status.CANCELED, Status.ERROR);

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.CREATED, EnumSet.of(Status.PENDING, Status.CANCELED, Status.ERROR),
            Status.PENDING, EnumSet.of(Status.ACTIVE, Status.CANCELED, Status.ERROR),
            Status.ACTIVE, EnumSet.of(Status.COMPLETED, Status.CANCELED, Status.ERROR));

    /**
     * Active-like (PENDING/ACTIVE): ещё существует, влияет на защиту.
     * Изъят из сериализации: форма едет полем заявки, телом команды и ответом
     * коннектора, а предикат — не её данные (.claude/rules/codestyle.md
     * §«Предикат формы, пересекающей сериализацию, не является её свойством»).
     */
    @JsonIgnore
    public Boolean isActiveLike() {
        return ACTIVE_LIKE_STATUSES.contains(status);
    }

    /**
     * Защита терминальна: сработала, снята либо в ошибочном состоянии —
     * рёбер из этих статусов матрица не содержит
     * (docs/spec/order-lifecycle.json, величина {@code attachedTransitionAllowed}).
     * Резолв по фактам родителя такую защиту не двигает: терминал запечатан, и
     * исход резолва на ней переписывал бы стоящий факт задним числом.
     *
     * <p>Изъят из сериализации тем же доводом, что {@link #isActiveLike()}.
     */
    @JsonIgnore
    public Boolean isTerminal() {
        return TERMINAL_STATUSES.contains(status);
    }

    /**
     * Эхо площадки совпадает с объявленной базой триггера. Ось сверки у
     * встроенной защиты одна — база: признака «только уменьшать» мы у неё не
     * объявляем, и сверять его эхо не с чем
     * (docs/integrations/okx/rules/reduce-only-invariant.md). Сверка идёт на
     * обеих тропах предъявления защиты — телом родителя и самостоятельной
     * записью цикла добычи материализованной защиты
     * (docs/models/mapping/Order.md).
     *
     * <p><b>Пустое эхо либо пустая декларация сверку не запускают:</b>
     * молчание источника нарушением не является, а реакция на расхождение —
     * аварийный контур всего счёта.
     *
     * @param echo копия этой защиты, прочитанная у площадки
     */
    public Boolean matchesEcho(AttachedAlgoOrder echo) {
        return isNull(echo) || isNull(triggerPriceType) || isNull(echo.getTriggerPriceType())
                || Objects.equals(triggerPriceType, echo.getTriggerPriceType());
    }

    /** Допустим ли переход в target по матрице. */
    public Boolean canTransitionTo(Status target) {
        Set<Status> allowed = isNull(status)
                ? EnumSet.of(Status.CREATED)
                : ALLOWED_TRANSITIONS.getOrDefault(status, EnumSet.noneOf(Status.class));
        return allowed.contains(target);
    }

    /**
     * Сколько эта защита реально закрывает: не больше налива своей
     * родительской заявки (docs/spec/protection-coverage.json, величина
     * {@code coveredSize} носителя ATTACHED). Уровень остановки убытка
     * встроенная несёт всегда — тип у неё один.
     */
    public BigDecimal coveredSize(BigDecimal parentFillSize) {
        BigDecimal declared = isNull(size) ? BigDecimal.ZERO : size;
        BigDecimal filled = isNull(parentFillSize) ? BigDecimal.ZERO : parentFillSize;
        return declared.min(filled);
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

    /**
     * Применить НАБЛЮДЁННЫЙ терминал: защита, ещё стоящая в PENDING,
     * активируется тем же наблюдением (PENDING -> ACTIVE -> терминал одной
     * транзакцией). Ребра PENDING -> COMPLETED в матрице нет намеренно —
     * терминал по найденному факту без активации применить было бы нечем.
     * См. docs/lifecycles/Order.md §«Разбор истории».
     */
    public void applyObservedTerminal(Status terminal, CloseReason reason) {
        if (Objects.equals(Status.PENDING, status) && isFalse(canTransitionTo(terminal))) {
            transitTo(Status.ACTIVE);
        }
        transitTo(terminal);
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

        /**
         * Снята штатным выходом транша, когда его экспозиция стала нулём:
         * материализованная защита переживает терминал родителя и без
         * снятия осталась бы на бирже за закрытым траншем.
         */
        CANCELED_BY_STRATEGY,

        /**
         * Родитель отменён ПРИ НУЛЕВОМ НАЛИВЕ — защита ушла вместе с ним.
         * Производитель один — исполнитель добычи родителя, тропы две: исход
         * CANCEL_BY_PARENT и неотправленный родитель, не найденный полным
         * циклом (намерение защиты снимается с ним из CREATED). Исполненный
         * родитель этого исхода не даёт.
         */
        PARENT_ORDER_CANCELED,

        /** Аварийный safety-flow / kill-switch. */
        KILL_SWITCH,

        /**
         * У встроенной защиты НЕ производится: цикл её добычи — второй и
         * свой, и его исчерпание даёт исход второй ступени, а не терминал
         * заявки (docs/models/domain/core/Order.md).
         */
        MISSING_AFTER_REFRESH,

        /**
         * Тропа 1 из трёх: защита НЕ ВСТАЛА — код отказа постановки
         * заполнен, на бирже её никогда не было.
         */
        PROTECTION_PLACEMENT_FAILED,

        /**
         * Тропа 2 из трёх: защита ПРОПАЛА — нога живых пуста при
         * положительной экспозиции транша без отдельной защиты того же
         * транша; разбор истории не ждётся.
         */
        PROTECTION_LOST,

        /**
         * Тропа 3 из трёх: защита СРАБОТАЛА, а результирующая заявка не
         * исполнилась (нога разбора state=order_failed).
         */
        PROTECTION_TRIGGER_FAILED,

        /** Fallback: причина не резолвится ни одной ветвью. */
        UNKNOWN
    }
}
