# AlgoOrder

> Статус документа: целевая модель standalone `AlgoOrder` для runtime-движка.
>
> Документ описывает доменную модель, статусы, причины финализации, condition-модель, external snapshot, refresh/recovery, cancel/amend semantics и связь с `DealActionState`.
>
> Exchange-specific mapping для OKX вынесен в отдельный документ: `OKX_AlgoOrder_mapping.md`.
>
> Связанные документы:
>
> * `Статусы торговых сущностей.md`
> * `Сервисные команды.md`
> * `FSM этапы сделки.md`
> * `Жизненный цикл сделки.md`
> * `Strategy.md`
> * `Order.md`
> * `OKX_AlgoOrder_mapping.md`

---

# 1. Назначение

`AlgoOrder` — standalone algo-order, связанный с конкретной `Deal`.

Он используется для:

* standalone stop-loss;
* standalone take-profit;
* OCO-сценариев;
* trailing stop;
* partial exit через reduce-only / position-reducing-only semantics.

`AlgoOrder` хранит:

* локальный intent: что бот хотел создать или изменить;
* идентификаторы: наш `internalId` и биржевой `externalId`;
* доменный статус;
* сырой внешний статус биржи;
* параметры условия срабатывания;
* рассчитанный размер;
* факты срабатывания, которые вернула биржа;
* diagnostic/future facts вроде связанных ordinary order ids.

`AlgoOrder` не является действием стратегии.

Связь между `StrategyAction` и `AlgoOrder` хранится через:

```text
StrategyAction.id
  -> DealActionState.strategyActionId
     -> RuntimeTarget(entityType = ALGO_ORDER, entityId = algoOrder.id)
```

Поэтому `AlgoOrder` не хранит:

```text
strategyActionId
strategyActionKey
role
level стратегии
```

---

# 2. Главные инварианты

* `AlgoOrder` связан с `Deal` через `dealId`.
* `AlgoOrder` не хранит `strategyActionId`.
* `AlgoOrder.internalId` используется как stable client id.
* Для OKX `AlgoOrder.internalId` маппится в `algoClOrdId`.
* `AlgoOrder.externalId` хранит биржевой id standalone algo-order.
* Для OKX `AlgoOrder.externalId` соответствует `algoId`.
* `AlgoOrder.externalStatus` хранит сырой статус биржи как диагностический факт.
* FSM и handlers не используют `externalStatus` напрямую.
* Внешний статус сначала проходит через exchange-specific `AlgoOrderExternalStatusResolver`.
* Unknown/problem external status не маппится в `AlgoOrder.Status.UNKNOWN`.
* Unknown/problem external status приводит к `ExternalStatusException`.
* `tdMode = isolated` и `posSide = net` не хранятся в `AlgoOrder` на первом этапе.
* Для OKX `tdMode = isolated` и `posSide = net` задаются константами в `OkxClientService` / adapter-layer.
* `positionReducingOnly` хранится в `AlgoOrder` как доменное намерение: algo-order должен только уменьшать позицию и не должен увеличивать / открывать новую.
* `positionReducingOnly` не является внешним фактом биржи и не заполняется из `AlgoOrderExternalSnapshot`.
* Если конкретная биржа поддерживает reduce-only / close-only механизм, client-layer маппит `positionReducingOnly` в соответствующее поле request.
* Для OKX `positionReducingOnly` маппится в `reduceOnly`.
* Если биржа не поддерживает reduce-only / close-only механизм, adapter может проигнорировать `positionReducingOnly`; unsupported exchange на первом этапе не блокируем.
* `conditionType` является денормализованной проекцией `condition.type`.
* `conditionType` обязателен и должен совпадать с `condition.type`.
* `condition` хранится как `jsonb` / вложенный object и содержит только trigger/trailing-семантику.
* `closeFraction` не хранится в `Condition`.
* `closeFraction` живёт в strategy/action sizing intent, например в `StrategyAlgoOrderAction.closeFractionPercents`.
* `AlgoOrder.size` хранит рассчитанный materialized размер, который посчитал `SizeCalculator`.
* Для OKX SWAP/FUTURES `AlgoOrder.size` маппится в `sz` и означает количество контрактов.
* `linkedOrderExternalIds` просто храним как внешний факт; на первом этапе доменная логика на них не опирается.

---

# 3. Доменная модель `AlgoOrder`

```java
package com.example.tradingbot.domain.model.core.algo_order;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Standalone algo-order, связанный со сделкой.
 *
 * Простыми словами:
 * - это отдельный условный ордер на бирже;
 * - он может быть SL, TP, OCO, trailing или partial-exit защитой;
 * - он хранит локальное намерение и факты с биржи;
 * - роль в стратегии не хранится в самом AlgoOrder, а определяется через DealActionState.
 */
@Getter
@Setter
public class AlgoOrder extends Auditable {

    /**
     * Внутренний технический идентификатор algo-order в БД.
     */
    private Long id;

    /**
     * Идентификатор сделки, к которой относится algo-order.
     */
    private Long dealId;

    /**
     * Межсервисный идентификатор algo-order.
     *
     * Для OKX используется как stable client id: algoClOrdId.
     */
    private String internalId;

    /**
     * Идентификатор standalone algo-order на бирже.
     *
     * Для OKX соответствует algoId.
     */
    private String externalId;

    /**
     * Текущий доменный статус algo-order.
     */
    private Status status;

    /**
     * Причина финализации algo-order или перевода в ошибочное состояние.
     */
    private CloseReason closeReason;

    /**
     * Денормализованная проекция condition.type.
     *
     * Нужна для jsonb, query, indexing, logging и поиска без разбора condition-json.
     *
     * Должна совпадать с condition.type.
     */
    private ConditionType conditionType;

    /**
     * Условие срабатывания algo-order.
     *
     * Хранит только trigger / trailing параметры.
     *
     * Не хранит размер, closeFraction, strategy-level role или level.
     */
    private Condition condition;

    /**
     * Рассчитанный materialized размер algo-order.
     *
     * Это результат SizeCalculator.
     *
     * Для OKX SWAP/FUTURES размер указывается в контрактах.
     */
    private BigDecimal size;

    /**
     * Доменная сторона algo-order.
     *
     * Для closing/protective algo-order:
     * - LONG position close обычно маппится в SELL;
     * - SHORT position close обычно маппится в BUY.
     *
     * Для OKX маппится в side.
     */
    private Direction direction;

    /**
     * Доменное намерение, что algo-order должен только уменьшать уже существующую позицию.
     *
     * Это не внешний факт биржи и не OKX-specific поле.
     *
     * Важно для protective / closing / partial-exit algo-orders.
     *
     * Client-layer получает это значение и, если конкретная биржа поддерживает
     * reduce-only / close-only механизм, маппит его в соответствующее поле request.
     */
    private Boolean positionReducingOnly;

    /**
     * Сырой внешний статус algo-order на стороне биржи.
     *
     * Для OKX это state: live, pause, effective, canceled, partially_effective,
     * order_failed, partially_failed и т.п.
     *
     * FSM не должна использовать это поле напрямую.
     */
    private String externalStatus;

    /**
     * Код ошибки, который вернула биржа по algo-order, если он есть.
     *
     * Для OKX соответствует failCode.
     *
     * Особенно полезен для diagnostics при order_failed / partially_failed.
     */
    private String failCode;

    /**
     * Фактический размер срабатывания algo-order, который вернула биржа.
     *
     * Для OKX соответствует actualSz.
     *
     * Это не исходный рассчитанный size. Исходный рассчитанный размер хранится в size.
     */
    private BigDecimal externalSize;

    /**
     * Фактическая цена срабатывания algo-order, которую вернула биржа.
     *
     * Для OKX соответствует actualPx.
     *
     * Это не trigger price, не activePx и не order price.
     */
    private BigDecimal externalPrice;

    /**
     * Время срабатывания algo-order на бирже.
     *
     * Для OKX соответствует triggerTime.
     */
    private Instant externalTriggerTime;

    /**
     * Ordinary order ids, которые биржа связала с этим algo-order.
     *
     * Для OKX соответствует ordId / ordIdList.
     *
     * На первом этапе просто храним как внешний факт.
     *
     * В доменной логике, FSM, recovery и DealActionState на них пока не опираемся.
     *
     * Отдельное решение о том, как использовать эти ids для fills/recovery/audit,
     * принимается позже.
     */
    private List<String> linkedOrderExternalIds;

    /**
     * Доменный статус standalone algo-order.
     */
    public enum Status {

        /**
         * Запись создана локально, но algo-order ещё не подтверждён на бирже.
         */
        CREATED,

        /**
         * Algo-order отправлен или мог быть отправлен на биржу,
         * но active/final факт ещё не подтверждён.
         *
         * ACK от биржи не является runtime-truth.
         */
        PENDING,

        /**
         * Algo-order подтверждён как активный на бирже и ещё может сработать.
         */
        ACTIVE,

        /**
         * Algo-order частично сработал на бирже.
         *
         * Это exchange-driven recovery status, а не целевой сценарий стратегии.
         *
         * Требует добора фактов и аккуратного решения FSM.
         */
        PARTIALLY_COMPLETED,

        /**
         * Algo-order штатно сработал на бирже.
         */
        COMPLETED,

        /**
         * Algo-order отменён и больше не может сработать.
         */
        CANCELED,

        /**
         * Algo-order находится в ошибочном состоянии.
         */
        ERROR
    }

    /**
     * Причина финализации standalone algo-order.
     */
    public enum CloseReason {

        /**
         * Algo-order штатно сработал на бирже.
         */
        TRIGGERED,

        /**
         * Algo-order отменён штатной логикой стратегии или FSM.
         */
        CANCELED_BY_STRATEGY,

        /**
         * Algo-order отменён потому, что стратегия заменила его другим algo-order.
         */
        REPLACED_BY_STRATEGY,

        /**
         * Algo-order отменён в рамках kill-switch или аварийного cleanup.
         */
        KILL_SWITCH,

        /**
         * Algo-order отменён вручную.
         */
        MANUAL_CANCEL,

        /**
         * Algo-order ожидался, но после полного algo evidence-cycle не найден.
         *
         * Это ERROR + Exchange HOLD.
         */
        MISSING_AFTER_REFRESH,

        /**
         * Биржа вернула problem-status order_failed.
         */
        ORDER_FAILED,

        /**
         * Биржа вернула problem-status partially_failed.
         */
        PARTIALLY_FAILED,

        /**
         * Биржа вернула неизвестный или неподдержанный внешний статус.
         */
        UNKNOWN_EXTERNAL_STATUS,

        /**
         * Client / adapter-layer обнаружил нарушение exchange-specific invariant.
         *
         * Примеры:
         * - expected tdMode=isolated, actual=cross;
         * - expected posSide=net, actual=long;
         * - expected side=sell, actual=buy;
         * - expected ordType=oco, actual=conditional;
         * - expected positionReducingOnly=true, actual reduceOnly=false.
         */
        EXCHANGE_INVARIANT_VIOLATION,

        /**
         * Причина не определена.
         *
         * Это маркер для расследования и возможного добавления новой CloseReason.
         */
        UNKNOWN
    }

    /**
     * Доменная сторона algo-order.
     */
    public enum Direction {

        /** Купить / закрыть short. */
        BUY,

        /** Продать / закрыть long. */
        SELL
    }

    /**
     * Проверяет, считается ли algo-order runtime-active в домене.
     *
     * Runtime-active означает, что algo-order ещё требует сопровождения,
     * refresh, recovery или cleanup.
     */
    public boolean isLive() {
        return status == Status.CREATED
                || status == Status.PENDING
                || status == Status.ACTIVE
                || status == Status.PARTIALLY_COMPLETED;
    }

    /**
     * Проверяет, что algo-order больше не считается runtime-active.
     */
    public boolean isNotLive() {
        return isFalse(isLive());
    }

    /**
     * Переводит algo-order в PENDING.
     */
    public void toPending() {
        transitTo(Status.PENDING, null);
    }

    /**
     * Переводит algo-order в ACTIVE.
     */
    public void toActive() {
        transitTo(Status.ACTIVE, null);
    }

    /**
     * Переводит algo-order в PARTIALLY_COMPLETED.
     */
    public void toPartiallyComplete() {
        transitTo(Status.PARTIALLY_COMPLETED, null);
    }

    /**
     * Переводит algo-order в COMPLETED и ставит TRIGGERED.
     */
    public void toComplete() {
        transitTo(Status.COMPLETED, CloseReason.TRIGGERED);
    }

    /**
     * Переводит algo-order в CANCELED и сохраняет причину отмены.
     */
    public void toCancel(CloseReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("AlgoOrder cancel reason is null");
        }
        transitTo(Status.CANCELED, reason);
    }

    /**
     * Переводит algo-order в ERROR и сохраняет причину ошибки.
     */
    public void toError(CloseReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("AlgoOrder error reason is null");
        }
        transitTo(Status.ERROR, reason);
    }

    /**
     * Проверяет и выполняет статусный переход.
     */
    private void transitTo(Status targetStatus, CloseReason targetCloseReason) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("targetStatus is null");
        }

        if (isTransitionForbidden(status, targetStatus)) {
            throw new IllegalStateException("Forbidden AlgoOrder status transition: " + status + " -> " + targetStatus);
        }

        this.status = targetStatus;

        if (targetCloseReason != null) {
            this.closeReason = targetCloseReason;
        }
    }

    /**
     * Проверяет, запрещён ли переход между статусами.
     */
    private boolean isTransitionForbidden(Status currentStatus, Status targetStatus) {
        if (isNull(currentStatus)) {
            return targetStatus != Status.CREATED;
        }

        return switch (currentStatus) {
            case CREATED -> !Set.of(Status.PENDING, Status.ERROR).contains(targetStatus);
            case PENDING -> !Set.of(Status.ACTIVE, Status.COMPLETED, Status.CANCELED, Status.ERROR).contains(targetStatus);
            case ACTIVE -> !Set.of(Status.PARTIALLY_COMPLETED, Status.COMPLETED, Status.CANCELED, Status.ERROR).contains(targetStatus);
            case PARTIALLY_COMPLETED -> !Set.of(Status.COMPLETED, Status.CANCELED, Status.ERROR).contains(targetStatus);
            case COMPLETED, CANCELED, ERROR -> true;
        };
    }

    /**
     * Проверяет внутреннюю консистентность conditionType и condition.type.
     */
    public void validateConditionProjection() {
        if (conditionType == null) {
            throw new IllegalStateException("AlgoOrder.conditionType is null");
        }

        if (condition == null) {
            throw new IllegalStateException("AlgoOrder.condition is null");
        }

        if (condition.getType() == null) {
            throw new IllegalStateException("AlgoOrder.condition.type is null");
        }

        if (conditionType != condition.getType()) {
            throw new IllegalStateException("AlgoOrder.conditionType != AlgoOrder.condition.type");
        }
    }
}
```

---

# 4. Статусы и live semantics

## 4.1. Runtime-live статусы

```text
CREATED
PENDING
ACTIVE
PARTIALLY_COMPLETED
```

Эти статусы означают, что `AlgoOrder` ещё участвует в runtime-процессе и требует refresh, recovery или cleanup.

## 4.2. Terminal / problem-final статусы

```text
COMPLETED
CANCELED
ERROR
```

`COMPLETED` и `CANCELED` — нормальные terminal-состояния сущности.

`ERROR` — problem-final для самой сущности. Сделка обычно переходит в `ERROR`, после чего работает `ErrorHandler` / safety-flow.

## 4.3. Семантика статусов

| Статус | Runtime-live | Live on exchange | Смысл |
|---|---:|---:|---|
| `CREATED` | да | нет | Локальный intent создан, на бирже не подтверждён. |
| `PENDING` | да | неизвестно | Submit/amend/cancel мог быть выполнен, нужен refresh/search/history. |
| `ACTIVE` | да | да | Algo-order активен или ожидает срабатывания. |
| `PARTIALLY_COMPLETED` | да | требует выяснения | Algo-order частично сработал; это recovery-status. |
| `COMPLETED` | нет | нет | Algo-order сработал. |
| `CANCELED` | нет | нет | Algo-order отменён. |
| `ERROR` | нет как штатный active | неизвестно до safety-flow | Ошибочное состояние, нужен safety/error-flow. |

---

# 5. `Condition`, `Trigger`, `Trailing`

## 5.1. Общий принцип

`Condition` отвечает только за условие срабатывания algo-order:

```text
когда и по какой trigger/trailing механике должен сработать algo-order
```

`Condition` не отвечает за размер.

Размер хранится в `AlgoOrder.size` как рассчитанный materialized size.

`closeFraction` живёт в strategy/action sizing intent и используется `SizeCalculator`.

## 5.2. `Condition`

```java
@Getter
@NoArgsConstructor
public class Condition {

    /**
     * Тип условия.
     *
     * Source внутри jsonb.
     * Должен совпадать с AlgoOrder.conditionType.
     */
    private ConditionType type;

    /**
     * Триггеры SL/TP.
     *
     * Используется для:
     * - STOP_LOSS;
     * - TAKE_PROFIT;
     * - OCO_FULL;
     * - PARTIAL_TAKE_PROFIT;
     * - PARTIAL_STOP_LOSS.
     */
    private Trigger trigger;

    /**
     * Параметры trailing.
     *
     * Используется для:
     * - TRAILING_PERCENTS;
     * - TRAILING_VALUE.
     */
    private Trailing trailing;
}
```

Инварианты:

```text
ровно один механизм:
  trigger XOR trailing

Condition.type должен соответствовать заполненным полям:
  STOP_LOSS / TAKE_PROFIT / OCO_FULL / PARTIAL_* -> trigger
  TRAILING_* -> trailing

AlgoOrder.conditionType == AlgoOrder.condition.type
```

## 5.3. `Trigger`

```java
public class Trigger {

    /**
     * Триггер stop-loss.
     *
     * Если null — SL не используется.
     */
    private TriggerPrice stopLoss;

    /**
     * Триггер take-profit.
     *
     * Если null — TP не используется.
     */
    private TriggerPrice takeProfit;
}
```

## 5.4. `TriggerPrice`

```java
public class TriggerPrice {

    /**
     * Внутренний тип цены для сравнения триггера: LAST / INDEX / MARK.
     */
    private TriggerPriceType type;

    /**
     * Внутреннее значение триггерной цены.
     */
    private BigDecimal value;

    /**
     * Биржевой тип цены триггера, как вернула биржа.
     *
     * Для OKX: last / index / mark.
     */
    private String externalType;

    /**
     * Биржевое значение триггерной цены, как вернула биржа.
     *
     * Может отличаться от value из-за округления или формата.
     */
    private BigDecimal externalValue;
}
```

`TriggerPrice.type/value` описывает **цену срабатывания**.

На первом этапе SL/TP/OCO legs исполняются market-like после trigger. Для OKX это означает `slOrdPx = -1` / `tpOrdPx = -1`.

Limit execution после trigger не моделируем на первом этапе.

## 5.5. `TriggerPriceType`

```java
public enum TriggerPriceType {

    /** Последняя цена. */
    LAST,

    /** Индексная цена. */
    INDEX,

    /** Mark price. */
    MARK
}
```

## 5.6. `Trailing`

```java
public class Trailing {

    /**
     * Trailing по проценту отката.
     *
     * Для OKX маппится в callbackRatio.
     */
    private BigDecimal trailingPercents;

    /**
     * Trailing по абсолютному шагу отката.
     *
     * Для OKX маппится в callbackSpread.
     */
    private BigDecimal trailingStepValue;

    /**
     * Цена активации trailing.
     *
     * Если null — trailing активен сразу.
     */
    private TriggerPrice activationPrice;

    /**
     * Биржевое текущее значение trailing, если биржа его вернула.
     *
     * Для OKX обычно соответствует moveTriggerPx.
     */
    private BigDecimal externalPrice;
}
```

---

# 6. `ConditionType`

```java
public enum ConditionType {

    /** Одиночный conditional stop-loss. */
    STOP_LOSS,

    /** Одиночный conditional take-profit. */
    TAKE_PROFIT,

    /** OCO-связка TP + SL. */
    OCO_FULL,

    /** Trailing stop по проценту отката. */
    TRAILING_PERCENTS,

    /** Trailing stop по абсолютному шагу отката. */
    TRAILING_VALUE,

    /** Частичный take-profit. */
    PARTIAL_TAKE_PROFIT,

    /** Частичный stop-loss. */
    PARTIAL_STOP_LOSS
}
```

На уровне OKX mapping:

```text
STOP_LOSS / TAKE_PROFIT / PARTIAL_* -> conditional
OCO_FULL                            -> oco
TRAILING_*                          -> move_order_stop
```

`externalType / ordType` не храним в `AlgoOrder` как отдельное поле.

OKX `ordType` вычисляет client-layer resolver из `AlgoOrder.conditionType`.

---

# 7. `AlgoOrderExternalSnapshot`

`AlgoOrderExternalSnapshot` — normalized snapshot внешних фактов по standalone algo-order.

Он нужен refresh/service layer для обновления domain `AlgoOrder`.

```java
public class AlgoOrderExternalSnapshot {

    /**
     * Stable client id на стороне биржи.
     *
     * Для OKX: algoClOrdId.
     */
    private String internalId;

    /**
     * Биржевой id standalone algo-order.
     *
     * Для OKX: algoId.
     */
    private String externalId;

    /**
     * Сырой внешний статус algo-order.
     *
     * Для OKX: state.
     */
    private String externalStatus;

    /**
     * Код ошибки algo-order, если биржа его вернула.
     *
     * Для OKX: failCode.
     */
    private String failCode;

    /**
     * Фактический размер срабатывания.
     *
     * Для OKX: actualSz.
     */
    private BigDecimal externalSize;

    /**
     * Фактическая цена срабатывания.
     *
     * Для OKX: actualPx.
     */
    private BigDecimal externalPrice;

    /**
     * Время срабатывания algo-order на бирже.
     *
     * Для OKX: triggerTime.
     */
    private Instant externalTriggerTime;

    /**
     * Внешние значения condition / trailing,
     * которые нужны для обновления domain condition external fields.
     */
    private ConditionExternalSnapshot condition;

    /**
     * Ordinary order ids, которые биржа связала с этим algo-order.
     *
     * Для OKX: ordId / ordIdList.
     *
     * На первом этапе просто храним как внешний факт.
     * В доменной логике на них не опираемся.
     */
    private List<String> linkedOrderExternalIds;
}
```

В snapshot не храним:

```text
externalType / ordType
externalDirection / side
externalPositionSide / posSide
actualSide
reduceOnly
tdMode
posSide
```

Эти поля относятся к client/adapter validation или raw audit, а не к domain snapshot.

---

# 8. `ConditionExternalSnapshot`

```java
public class ConditionExternalSnapshot {

    /**
     * Внешние значения SL/TP triggers.
     */
    private TriggerExternalSnapshot trigger;

    /**
     * Внешние значения trailing.
     */
    private TrailingExternalSnapshot trailing;
}
```

```java
public class TriggerExternalSnapshot {

    /** Внешние значения stop-loss trigger. */
    private TriggerPriceExternalSnapshot stopLoss;

    /** Внешние значения take-profit trigger. */
    private TriggerPriceExternalSnapshot takeProfit;
}
```

```java
public class TriggerPriceExternalSnapshot {

    /**
     * Биржевой тип trigger price.
     *
     * Для OKX: last / index / mark.
     */
    private String externalType;

    /**
     * Биржевое значение trigger price.
     */
    private BigDecimal externalValue;
}
```

```java
public class TrailingExternalSnapshot {

    /**
     * Внешние значения activation price, если есть.
     */
    private TriggerPriceExternalSnapshot activationPrice;

    /**
     * Биржевое текущее значение trailing.
     *
     * Для OKX: moveTriggerPx.
     */
    private BigDecimal externalPrice;
}
```

---

# 9. Связь с `DealActionState`

`AlgoOrder` не хранит `strategyActionId`.

Связь строится так:

```text
StrategyAction.id
  -> DealActionState.strategyActionId
     -> RuntimeTarget(entityType = ALGO_ORDER, entityId = algoOrder.id)
```

Правила:

* `CREATE_ALGO_ORDER` создаёт локальный `AlgoOrder` и `DealActionState.target` в одной транзакции.
* `SUBMIT_ALGO_ORDER` использует `AlgoOrder.internalId` как stable client id.
* `AMEND_ALGO_ORDER` и `CANCEL_ALGO_ORDER` находят target через `targetActionKey -> target StrategyAction -> DealActionState -> RuntimeTarget`.
* Runtime работает через `strategyActionId`, а не через `strategyActionKey`.
* Аудит не является источником runtime-логики.

---

# 10. Exchange exceptions policy для `AlgoOrder`

Политика исключений должна быть единообразной с `Order` и другими trading runtime-сущностями.

## 10.1. `ExternalStatusException`

Кто бросает:

```text
AlgoOrderExternalStatusResolver
```

Когда:

```text
внешний статус получен, но:
  - неизвестен;
  - или известен, но означает problem-state.
```

Примеры OKX:

```text
order_failed
partially_failed
unknown state
```

Runtime-реакция:

```text
AlgoOrder -> ERROR
closeReason = ORDER_FAILED / PARTIALLY_FAILED / UNKNOWN_EXTERNAL_STATUS
Deal -> ERROR
HOLD по severity / safetyImpact
```

## 10.2. `ExternalInvariantViolationException`

Кто бросает:

```text
OkxClientService / exchange adapter-layer
```

Когда:

```text
response получен, но нарушает ожидаемый exchange invariant.
```

Примеры:

```text
tdMode != isolated
posSide != net
side != expected direction
ordType != expected conditionType mapping
reduceOnly != expected positionReducingOnly
```

Runtime-реакция:

```text
AlgoOrder -> ERROR
closeReason = EXCHANGE_INVARIANT_VIOLATION
Deal -> ERROR
Exchange -> HOLD
```

## 10.3. `ExternalNotFoundException`

Кто бросает:

```text
RefreshExecutor / recovery-search boundary
```

Когда:

```text
после полного algo evidence-cycle сущность не найдена
и её финал нельзя объяснить через algo-order sources.
```

Runtime-реакция:

```text
AlgoOrder -> ERROR
closeReason = MISSING_AFTER_REFRESH
Deal -> ERROR
Exchange -> HOLD
```

Важно:

```text
ExternalNotFoundException нельзя бросать после одного пустого response.
Сначала нужно проверить все релевантные algo-order sources.
```

---

# 11. Status resolver

Для каждой биржи должна быть своя реализация:

```text
AlgoOrderExternalStatusResolver
  -> OkxAlgoOrderExternalStatusResolver
```

Resolver отвечает только за:

```text
external status -> domain status
```

Resolver не должен:

* сохранять сущность;
* принимать FSM-решения;
* создавать команды;
* запускать cleanup;
* запускать kill-switch.

OKX mapping:

| OKX state | Доменная реакция |
|---|---|
| `live` | `ACTIVE` |
| `pause` | `ACTIVE` |
| `partially_effective` | `PARTIALLY_COMPLETED` |
| `effective` | `COMPLETED`, `closeReason = TRIGGERED` |
| `canceled` | `CANCELED`, `closeReason` берётся из cancel intent |
| `order_failed` | `ExternalStatusException(reasonCode = ORDER_FAILED)` |
| `partially_failed` | `ExternalStatusException(reasonCode = PARTIALLY_FAILED)` |
| unknown | `ExternalStatusException(reasonCode = UNKNOWN_EXTERNAL_STATUS)` |

`pause` трактуется как active external status: algo-order ещё существует на бирже и может влиять на риск/cleanup.

`partially_effective` не является штатной целью стратегии. Это технический recovery-status: нужно добрать факты и понять итоговый риск.

`partially_failed` — problem-state. Часть сценария могла успеть выполниться, но OKX сообщает частичный сбой; обычный flow прерывается через `ExternalStatusException`.

---

# 12. Client / adapter validation

Client / adapter-layer проверяет exchange invariants.

Для OKX проверяем:

```text
tdMode == isolated
posSide == net
side == expected from AlgoOrder.direction
ordType == expected from AlgoOrder.conditionType
reduceOnly == expected from AlgoOrder.positionReducingOnly
```

Если mismatch:

```text
throw ExternalInvariantViolationException
```

и далее:

```text
AlgoOrder -> ERROR
closeReason = EXCHANGE_INVARIANT_VIOLATION
Deal -> ERROR
Exchange -> HOLD
```

Размеры не валидируем как hard invariant:

```text
AlgoOrder.size = наше calculated intent
AlgoOrder.externalSize = биржевой actualSz
```

`externalSize` может отличаться от `size` из-за частичного срабатывания/исполнения.

`actualSide` не храним в `AlgoOrder` и `AlgoOrderExternalSnapshot`. Если client-layer может однозначно вывести ожидаемое значение, он может проверить `actualSide` как invariant. Для аудита raw response можно сохранить в истории команд / raw exchange response, когда аудит будет финализирован.

---

# 13. Refresh / recovery

## 13.1. Граница ответственности

`RefreshAlgoOrderExecutor` обновляет только `AlgoOrder`.

Он использует только algo-order endpoints конкретной биржи.

Он не должен сам ходить в:

```text
ordinary orders
fills
positions
```

Эти команды создаёт FSM / DealOrchestrator, если после анализа `DealContext` нужны дополнительные факты.

## 13.2. Algo evidence-cycle

Для OKX algo-order sources:

```text
GET /api/v5/trade/order-algo
GET /api/v5/trade/orders-algo-pending
GET /api/v5/trade/orders-algo-history
```

Поиск:

```text
если externalId есть:
  искать по algoId

если externalId нет:
  искать по internalId / algoClOrdId
```

`ExternalNotFoundException` можно бросить только после полного algo evidence-cycle по algo-order sources.

Пустой `data=[]` из одного endpoint не является финальным доказательством missing.

## 13.3. Что обновляет refresh

Refresh/service layer обновляет:

```text
externalId
externalStatus
failCode
externalSize
externalPrice
externalTriggerTime
condition external fields
linkedOrderExternalIds
status через AlgoOrderExternalStatusResolver
```

`linkedOrderExternalIds` только сохраняются.

На первом этапе они не запускают автоматическое создание `Order`, не создают `DealActionState` и не являются target для FSM.

---

# 14. Cancel semantics

Общее правило command-layer:

```text
ACK не является runtime-truth.
```

`CANCEL_ALGO_ORDER` не переводит `AlgoOrder` в `CANCELED`.

`CANCELED` ставится только после refresh/history-факта.

Flow:

```text
FSM / handler:
  сначала refresh;
  потом, если AlgoOrder всё ещё live и должен быть снят, создаёт CANCEL_ALGO_ORDER.

CancelAlgoOrderExecutor:
  технически отправляет cancel;
  сохраняет ACK / command result;
  не финализирует AlgoOrder.

После restart:
  система собирает DealContext;
  делает refresh/history;
  верит exchange facts;
  если AlgoOrder всё ещё live и cancel всё ещё нужен — cancel можно повторить.
```

Если после cancel intent биржа показывает `effective`, верим exchange fact:

```text
effective -> COMPLETED / TRIGGERED
```

Если показывает `canceled`, тогда:

```text
canceled -> CANCELED / closeReason из cancel intent
```

`CloseReason` для canceled берётся из нашего намерения отмены:

```text
CANCELED_BY_STRATEGY
REPLACED_BY_STRATEGY
KILL_SWITCH
MANUAL_CANCEL
```

---

# 15. Amend semantics

`AMEND_ALGO_ORDER` обновляет существующий `AlgoOrder` на бирже.

Amend response / ACK не является runtime-truth.

После `AMEND_ALGO_ORDER` состояние и параметры `AlgoOrder` подтверждаются только через refresh-факты.

`AmendAlgoOrderExecutor`:

* технически отправляет amend;
* сохраняет ACK / command result;
* не финализирует `AlgoOrder`;
* не принимает торговое решение.

---

# 16. Связь с ordinary `Order`

OKX может вернуть связанные ordinary order ids:

```text
ordId
ordIdList
```

В домене они сохраняются в:

```java
private List<String> linkedOrderExternalIds;
```

Правила первого этапа:

* не создаём domain `Order` автоматически;
* не создаём `DealActionState` автоматически;
* не строим FSM-логику на `linkedOrderExternalIds`;
* не используем их как action target;
* просто храним как внешний факт / diagnostic / future research hook.

Отдельное решение о том, как использовать `linkedOrderExternalIds` для fills / recovery / audit, принимается позже.

---

# 17. Отличие от attached protection

`AttachedAlgoOrder` внутри `Order` — embedded protection parent order.

Standalone `AlgoOrder` — отдельная runtime-сущность.

Правила:

* Attached protection остаётся частью `Order`.
* Attached protection не материализуется автоматически в standalone `AlgoOrder`.
* Standalone `AlgoOrder` создаётся только отдельным `StrategyAlgoOrderAction` через `CREATE_ALGO_ORDER`.
* Если биржа возвращает algo identifiers внутри attached snapshot, это не означает автоматическое создание standalone `AlgoOrder`.

---

# 18. Impact на текущий код

Этот раздел нужен как implementation checklist, но не должен становиться отдельной runtime-логикой.

Целевые изменения:

```text
AlgoOrder:
  убрать strategyActionId
  убрать externalType
  убрать externalDirection
  убрать externalPositionSide
  добавить positionReducingOnly
  добавить PARTIALLY_COMPLETED
  добавить linkedOrderExternalIds
  добавить externalSize / externalPrice / externalTriggerTime
  оставить failCode 
  добавить строгие transition methods

Condition:
  убрать closeFraction
  оставить type / trigger / trailing

StopLossCondition / TakeProfitCondition / Partial* / OcoFullCondition / Trailing*:
  убрать closeFraction из constructors

AlgoOrderConditionValidator:
  больше не валидирует closeFraction
  валидирует type -> trigger/trailing

StrategyAlgoOrderAction:
  closeFractionPercents остаётся strategy/action sizing intent

SizeCalculator:
  closeFractionPercents + position + instrument rules -> AlgoOrder.size

OKX create algo mapper:
  AlgoOrder.size -> sz
  OKX closeFraction не используем как основной механизм первого этапа
```

---
