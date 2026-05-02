# Order

> Статус документа: актуальная модель ordinary order и attached protection.
>
> Документ создан на базе текущих договорённостей по runtime-движку, Java-классов `Order.java`, `AttachedAlgoOrder.java`, `OrderExternalSnapshot.java`, `AttachedAlgoOrderExternalSnapshot.java` и старой доки `Order.md` как legacy-источника полей.
>
> Приоритет источников:
>
> 1. текущие договорённости по движку;
> 2. Java-классы;
> 3. старая дока `Order.md`.
>
> Связанные документы:
>
> * `Статусы торговых сущностей.md`;
> * `Сервисные команды.md`;
> * `FSM этапы сделки.md`;
> * `Жизненный цикл сделки.md`;
> * `Strategy.md`;
> * `Оценка рисков.md`.
> * `OKX_Order_mapping.md`.

---

# 1. Назначение

`Order` — ordinary exchange order, связанный с конкретной `Deal`.

Он хранит:

* локальный intent: что бот хотел создать;
* идентификаторы: наш `internalId` и биржевой `externalId`;
* актуальный доменный статус;
* сырой внешний статус биржи;
* параметры цены и размера;
* факты исполнения;
* attached protection, если она была создана вместе с parent order.

`Order` не является действием стратегии.

Связь между `StrategyAction` и `Order` хранится через:

```text
StrategyAction.id
  -> DealActionState.strategyActionId
     -> RuntimeTarget(entityType = ORDER, entityId = order.id)
```

Поэтому `Order` не хранит:

```text
strategyActionId
strategyActionKey
role
level стратегии
```

---

# 2. Главные инварианты

* `Order` связан с `Deal` через `dealId`.
* `Order` не хранит `strategyActionId`.
* `Order.internalId` используется как stable client id для OKX `clOrdId`.
* `Order.externalId` хранит биржевой ID ordinary order.
* `Order.externalStatus` хранит сырой статус биржи как диагностический факт.
* FSM и handlers не используют `externalStatus` напрямую.
* Внешний статус сначала проходит через `OrderExternalStatusResolver`.
* Unknown external status не маппится в `Order.Status.UNKNOWN`.
* Unknown external status приводит к controlled exception, `Deal -> ERROR` и `Exchange HOLD`.
* `tdMode = isolated` и `posSide = net` не хранятся в `Order` на первом этапе.
* Для OKX `tdMode = isolated` и `posSide = net` задаются константами в `OkxClientService`.
* `reduceOnly` хранится в `Order`, потому что это runtime-свойство исполнения и обязательный признак для partial exit через ordinary order.
* Attached protection остаётся embedded-частью parent `Order`.
* Attached protection не материализуется автоматически в standalone `AlgoOrder`, даже если в snapshot есть attached/algo identifiers.
* Standalone `AlgoOrder` создаётся только отдельным `StrategyAlgoOrderAction` через `CREATE_ALGO_ORDER`.

---

# 3. Доменная модель `Order`

```java
package com.example.tradingbot.domain.model.core.order;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Обычный биржевой ордер, связанный со сделкой.
 *
 * Простыми словами:
 * - это ordinary order на бирже;
 * - он хранит локальное намерение и факты с биржи;
 * - он может быть entry order, reduce-only order или order другой runtime-роли;
 * - роль в стратегии не хранится в самом Order, а определяется через DealActionState.
 */
@Getter
@Setter
public class Order extends Auditable {

    /**
     * Внутренний технический идентификатор ордера в БД.
     */
    private Long id;

    /**
     * Идентификатор сделки, к которой относится ордер.
     */
    private Long dealId;

    /**
     * Межсервисный идентификатор ордера.
     *
     * Для OKX используется как stable client id: clOrdId.
     */
    private String internalId;

    /**
     * Идентификатор ordinary order на бирже.
     *
     * Для OKX соответствует ordId.
     */
    private String externalId;

    /**
     * Текущий доменный статус ордера.
     */
    private Status status;

    /**
     * Причина финализации ордера или перевода в ошибочное состояние.
     */
    private CloseReason closeReason;

    /**
     * Тип ordinary order в бизнес-терминах приложения.
     *
     * Тип не должен подменять роль StrategyAction.
     */
    private Type type;

    /**
     * Сторона ордера на бирже.
     *
     * Для OKX это строка side: buy / sell.
     */
    private String side;

    /**
     * Сырой внешний статус ордера на стороне биржи.
     *
     * Для OKX это state: live, partially_filled, filled, canceled и т.п.
     *
     * FSM не должна использовать это поле напрямую.
     */
    private String externalStatus;

    /**
     * Цена ордера.
     *
     * Для market-like order может быть null, если цена не отправляется на биржу.
     */
    private BigDecimal price;

    /**
     * Размер ордера.
     *
     * Для OKX SWAP/FUTURES размер указывается в контрактах.
     */
    private BigDecimal size;

    /**
     * Накопленный исполненный объём ордера.
     */
    private BigDecimal accumulatedFillSize;

    /**
     * Средняя цена исполнения ордера.
     */
    private BigDecimal averagePrice;

    /**
     * Накопленная комиссия по ордеру.
     */
    private BigDecimal fee;

    /**
     * Признак, что ордер может только уменьшить уже существующую позицию.
     *
     * Важно для partial exit: частичное уменьшение позиции разрешено только через
     * reduce-only Order / AlgoOrder actions.
     */
    private Boolean reduceOnly;

    /**
     * Прикреплённые защитные algo-orders, созданные вместе с parent order.
     *
     * На первом этапе это embedded-часть Order, а не standalone AlgoOrder.
     */
    private List<AttachedAlgoOrder> attachedAlgoOrders;

    /**
     * Доменный статус ordinary order.
     */
    public enum Status {

        /**
         * Запись создана локально, но ордер ещё не подтверждён на бирже.
         */
        CREATED,

        /**
         * Ордер отправлен или мог быть отправлен на биржу, но active/final факт ещё не подтверждён.
         *
         * ACK от биржи не является runtime-truth.
         */
        PENDING,

        /**
         * Ордер подтверждён как активный на бирже и ещё может исполниться.
         */
        ACTIVE,

        /**
         * Ордер частично исполнен, но ещё может иметь live-остаток.
         */
        PARTIALLY_COMPLETED,

        /**
         * Ордер полностью исполнен на бирже.
         */
        COMPLETED,

        /**
         * Ордер отменён и больше не может исполниться.
         */
        CANCELED,

        /**
         * Ордер находится в ошибочном состоянии.
         *
         * Например, его невозможно безопасно синхронизировать или внешний статус нельзя распознать.
         */
        ERROR
    }

    /**
     * Тип ordinary order в бизнес-терминах приложения.
     *
     * Не описывает strategy role вроде grid-entry / partial-exit / full-exit.
     */
    public enum Type {

        /**
         * Ордер на вход в сделку.
         */
        ENTRY,

        /**
         * Ордер на вход в сделку, созданный вместе с attached stop-loss.
         */
        ENTRY_ATTACHED_STOP_LOSS
    }

    /**
     * Причина финализации ordinary order.
     */
    public enum CloseReason {

        /**
         * Ордер полностью исполнен на бирже.
         */
        FILLED,

        /**
         * Ордер отменён штатной логикой стратегии или FSM.
         */
        CANCELED_BY_STRATEGY,

        /**
         * Ордер отменён в рамках kill-switch или аварийного cleanup.
         */
        KILL_SWITCH,

        /**
         * Ордер отменён вручную.
         */
        MANUAL_CANCEL,

        /**
         * Ордер больше не нужен, потому что связанный runtime-контекст изменился.
         *
         * Например, позиция уже закрыта другим способом или step больше не актуален.
         */
        NO_LONGER_RELEVANT,

        /**
         * Ордер ожидался, но после refresh/search/history цикла не найден.
         */
        MISSING_AFTER_REFRESH,

        /**
         * Биржа вернула неизвестный или неподдержанный внешний статус.
         */
        UNKNOWN_EXTERNAL_STATUS,

        /**
         * Причина не определена.
         */
        UNKNOWN
    }

    /**
     * Проверяет, считается ли ордер runtime-active в домене.
     *
     * Runtime-active означает, что ордер ещё требует сопровождения,
     * refresh, recovery или cleanup.
     */
    public boolean isLive() {
        return status == Status.CREATED
                || status == Status.PENDING
                || status == Status.ACTIVE
                || status == Status.PARTIALLY_COMPLETED;
    }

    /**
     * Проверяет, что ордер больше не считается runtime-active.
     */
    public boolean isNotLive() {
        return isFalse(isLive());
    }

    /**
     * Переводит ордер в статус отмены и сохраняет причину.
     */
    public void toCancel(CloseReason reason) {
        setStatus(Status.CANCELED);
        setCloseReason(reason);
    }

    /**
     * Переводит ордер в статус полного исполнения и сохраняет причину.
     */
    public void toComplete() {
        setStatus(Status.COMPLETED);
        setCloseReason(CloseReason.FILLED);
    }

    /**
     * Переводит ордер в ошибочное состояние и сохраняет причину.
     */
    public void toError(CloseReason reason) {
        setStatus(Status.ERROR);
        setCloseReason(reason);
    }
}
```

---

# 4. Доменная модель `AttachedAlgoOrder`

```java
package com.example.tradingbot.domain.model.core.order;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

import static io.micrometer.common.util.StringUtils.isNotBlank;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Прикреплённый защитный algo-order обычного order.
 *
 * Простыми словами:
 * - создаётся вместе с parent Order через attachAlgoOrds;
 * - живёт внутри parent Order;
 * - не является standalone AlgoOrder нашей системы;
 * - используется как temporary attached protection до решения FSM/strategy flow.
 */
@Getter
@Setter
public class AttachedAlgoOrder extends Auditable {

    private static final Set<String> ACTIVE_LIKE_STATUS_NAMES = Set.of(
            Status.PENDING.name(),
            Status.ACTIVE.name()
    );

    /**
     * Внутренний технический идентификатор attached protection в БД.
     */
    private Long id;

    /**
     * Идентификатор parent Order.
     */
    private Long orderId;

    /**
     * Межсервисный идентификатор attached protection.
     *
     * Для OKX используется как attachAlgoClOrdId.
     */
    private String internalId;

    /**
     * Идентификатор прикреплённого algo-order на бирже, пока он существует как attached entity.
     *
     * Для OKX соответствует attachAlgoId.
     */
    private String externalAttachedId;

    /**
     * Внешний идентификатор algo-order, если биржа его возвращает для attached protection.
     *
     * Даже при наличии этого поля attached protection не материализуется автоматически
     * в standalone AlgoOrder.
     */
    private String externalId;

    /**
     * Текущий доменный статус attached protection.
     */
    private Status status;

    /**
     * Причина финализации attached protection.
     */
    private CloseReason closeReason;

    /**
     * Внутренний тип attached protection.
     */
    private Type type;

    /**
     * Сырой внешний статус, если биржа когда-либо начнёт возвращать его для attached protection.
     *
     * Для текущего OKX attachAlgoOrds отдельного полноценного state нет.
     */
    private String externalStatus;

    /**
     * Биржевой тип attached protection.
     */
    private String externalType;

    /**
     * Размер attached protection.
     */
    private BigDecimal size;

    /**
     * Триггерная цена stop-loss.
     *
     * Для текущего проекта attached protection используется как attached stop-loss.
     */
    private BigDecimal stopLossTriggerPrice;

    /**
     * Внутренний тип attached protection.
     */
    public enum Type {

        /**
         * Attached stop-loss, созданный вместе с parent Order.
         */
        ATTACHED_STOP_LOSS
    }

    /**
     * Доменный статус attached protection.
     */
    public enum Status {

        /**
         * Запись создана локально как intent attached protection.
         */
        CREATED,

        /**
         * Parent Order отправлен или мог быть отправлен на биржу,
         * но active-факт attached protection ещё не подтверждён refresh-фактами.
         */
        PENDING,

        /**
         * Attached protection подтверждена refresh-фактами и может сработать.
         */
        ACTIVE,

        /**
         * Attached protection штатно сработала.
         */
        COMPLETED,

        /**
         * Attached protection отменена или снята и больше не влияет на риск.
         */
        CANCELED,

        /**
         * Attached protection находится в ошибочном состоянии.
         */
        ERROR
    }

    /**
     * Причина финализации attached protection.
     */
    public enum CloseReason {

        /**
         * Attached protection штатно сработала.
         */
        TRIGGERED,

        /**
         * Attached protection снята по strategy/FSM flow.
         *
         * Например, после подтверждения standalone main protection.
         */
        SWITCHED_BY_STRATEGY,

        /**
         * Parent Order был отменён, поэтому attached protection больше не актуальна.
         */
        PARENT_ORDER_CANCELED,

        /**
         * Attached protection отменена в рамках kill-switch или аварийного cleanup.
         */
        KILL_SWITCH,

        /**
         * Attached protection отменена вручную.
         */
        MANUAL_CANCEL,

        /**
         * Attached protection ожидалась, но после refresh/search/history цикла не найдена.
         */
        MISSING_AFTER_REFRESH,

        /**
         * Attached protection была активной защитой, но больше не подтверждается,
         * и standalone protection отсутствует.
         */
        PROTECTION_LOST,

        /**
         * Пришёл неизвестный или неподдержанный внешний факт.
         */
        UNKNOWN_EXTERNAL_STATUS,

        /**
         * Причина не определена.
         */
        UNKNOWN
    }

    /**
     * Возвращает имена статусов, которые считаются active-like для attached protection.
     */
    public static Set<String> activeLikeStatusNames() {
        return ACTIVE_LIKE_STATUS_NAMES;
    }

    /**
     * Проверяет, что attached protection ещё может быть активной или ожидает подтверждения.
     */
    public boolean isActiveLike() {
        return Objects.equals(status, Status.PENDING) || Objects.equals(status, Status.ACTIVE);
    }

    /**
     * Проверяет, что attached protection достигла финального статуса.
     */
    public boolean isTerminal() {
        return Objects.equals(status, Status.COMPLETED)
                || Objects.equals(status, Status.CANCELED)
                || Objects.equals(status, Status.ERROR);
    }

    /**
     * Проверяет допустимость перехода в новый статус.
     */
    public boolean canTransitionTo(Status targetStatus) {
        if (Objects.isNull(targetStatus)) {
            return false;
        }
        if (Objects.equals(status, targetStatus)) {
            return true;
        }
        if (Objects.isNull(status)) {
            return Objects.equals(targetStatus, Status.CREATED);
        }

        return switch (status) {
            case CREATED -> Objects.equals(targetStatus, Status.PENDING)
                    || Objects.equals(targetStatus, Status.ERROR);
            case PENDING -> Objects.equals(targetStatus, Status.ACTIVE)
                    || Objects.equals(targetStatus, Status.CANCELED)
                    || Objects.equals(targetStatus, Status.ERROR);
            case ACTIVE -> Objects.equals(targetStatus, Status.COMPLETED)
                    || Objects.equals(targetStatus, Status.CANCELED)
                    || Objects.equals(targetStatus, Status.ERROR);
            case COMPLETED, CANCELED, ERROR -> false;
        };
    }

    /**
     * Переводит attached protection в PENDING.
     */
    public void toPending() {
        transitionTo(Status.PENDING);
    }

    /**
     * Переводит attached protection в ACTIVE.
     */
    public void toActive() {
        transitionTo(Status.ACTIVE);
    }

    /**
     * Переводит attached protection в COMPLETED и сохраняет причину срабатывания.
     */
    public void toComplete() {
        transitionTo(Status.COMPLETED);
        this.closeReason = CloseReason.TRIGGERED;
    }

    /**
     * Переводит attached protection в CANCELED и сохраняет причину отмены.
     */
    public void toCancel(CloseReason reason) {
        transitionTo(Status.CANCELED);
        this.closeReason = reason;
    }

    /**
     * Переводит attached protection в ERROR и сохраняет причину ошибки.
     */
    public void toError(CloseReason reason) {
        transitionTo(Status.ERROR);
        this.closeReason = reason;
    }

    /**
     * Проверяет, что биржевой тип attached protection заполнен.
     */
    public boolean hasExternalType() {
        return isNotBlank(externalType);
    }

    private void transitionTo(Status targetStatus) {
        if (isFalse(canTransitionTo(targetStatus))) {
            throw new IllegalStateException(
                    "Illegal AttachedAlgoOrder transition: "
                            + status
                            + " -> "
                            + targetStatus
                            + " for internalId="
                            + internalId
                            + ", externalId="
                            + externalId
                            + ", externalAttachedId="
                            + externalAttachedId
            );
        }

        this.status = targetStatus;
    }
}
```

---

# 5. External snapshots

- `OKX_Order_mapping.md` — маппинг OKX request/response DTO, `OrderExternalSnapshot`, `AttachedAlgoOrderExternalSnapshot` и правил заполнения `Order` / `AttachedAlgoOrder`.

## 5.1. `OrderExternalSnapshot`

`OrderExternalSnapshot` — нормализованный snapshot ordinary order, полученный из OKX REST GET-методов.

```java
package com.example.tradingbot.domain.model.core.order.external_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Внешний snapshot ordinary order, полученный из OKX REST GET-методов.
 *
 * Это не persisted runtime-сущность, а входной факт для refresh/search/history flow.
 */
@Getter
@Setter
public class OrderExternalSnapshot extends Auditable {

    /**
     * Клиентский идентификатор ордера.
     *
     * Для OKX соответствует clOrdId.
     */
    private String internalId;

    /**
     * Биржевой идентификатор ордера.
     *
     * Для OKX соответствует ordId.
     */
    private String externalId;

    /**
     * Тип ордера на стороне биржи.
     *
     * Для OKX соответствует ordType.
     */
    private String type;

    /**
     * Сторона ордера на бирже.
     *
     * Для OKX соответствует side: buy / sell.
     */
    private String side;

    /**
     * Сырой внешний статус ордера.
     *
     * Для OKX соответствует state.
     */
    private String externalStatus;

    /**
     * Цена ордера.
     */
    private BigDecimal price;

    /**
     * Размер ордера.
     */
    private BigDecimal size;

    /**
     * Накопленный исполненный объём ордера.
     */
    private BigDecimal accumulatedFillSize;

    /**
     * Средняя цена исполнения ордера.
     */
    private BigDecimal averagePrice;

    /**
     * Накопленная комиссия по ордеру.
     */
    private BigDecimal fee;

    /**
     * Snapshots attached protection из блока attachAlgoOrds.
     */
    private List<AttachedAlgoOrderExternalSnapshot> attachedAlgoOrders;

    /**
     * Клиентский идентификатор attached protection из top-level order snapshot.
     */
    private String attachedAlgoInternalId;

    /**
     * Top-level trigger take-profit из order snapshot.
     */
    private BigDecimal takeProfitTriggerPrice;

    /**
     * Top-level trigger stop-loss из order snapshot.
     */
    private BigDecimal stopLossTriggerPrice;
}
```

## 5.2. `AttachedAlgoOrderExternalSnapshot`

`AttachedAlgoOrderExternalSnapshot` — snapshot одного элемента `attachAlgoOrds` внутри parent `OrderExternalSnapshot`.

```java
package com.example.tradingbot.domain.model.core.order.external_snapshot;

import lombok.Getter;
import lombok.Setter;

/**
 * Snapshot attached protection из блока attachAlgoOrds в ответе OKX trade/order.
 */
@Getter
@Setter
public class AttachedAlgoOrderExternalSnapshot {

    /**
     * Идентификатор attached protection на стороне OKX.
     *
     * Для OKX соответствует attachAlgoId.
     */
    private String externalAttachedId;

    /**
     * Клиентский идентификатор attached protection.
     *
     * Для OKX соответствует attachAlgoClOrdId.
     */
    private String internalId;

    /**
     * Внешний идентификатор algo-order, если биржа его возвращает.
     */
    private String externalId;

    /**
     * Тип attached protection на стороне биржи.
     */
    private String externalType;

    /**
     * Размер attached protection.
     */
    private String size;

    /**
     * Триггерная цена stop-loss.
     */
    private String stopLossTriggerPrice;

    /**
     * Код ошибки создания или привязки attached protection на стороне OKX.
     */
    private String failCode;

    /**
     * Текст причины ошибки создания или привязки attached protection на стороне OKX.
     */
    private String failReason;
}
```

---

# 6. Status semantics

## 6.1. `Order.Status`

| Статус | Runtime-active | Closed/final | Live risk | Комментарий |
|---|---:|---:|---:|---|
| `CREATED` | да | нет | нет на бирже | Локальная сущность создана, ещё требует submit/recovery. |
| `PENDING` | да | нет | неизвестно | Submit был выполнен или мог быть выполнен, нужен refresh/search. |
| `ACTIVE` | да | нет | да | Ордер live на бирже и может исполниться. |
| `PARTIALLY_COMPLETED` | да | нет | да | Ордер частично исполнен, live-остаток может исполниться. |
| `COMPLETED` | нет | да | нет | Ордер полностью исполнен. |
| `CANCELED` | нет | да | нет | Ордер отменён. |
| `ERROR` | нет как штатный active | да как problem-final | неизвестно | Сущность в ошибочном состоянии; сделка должна идти через safety/recovery flow. |

## 6.2. `AttachedAlgoOrder.Status`

| Статус | Runtime-active | Closed/final | Комментарий |
|---|---:|---:|---|
| `CREATED` | да | нет | Local intent created вместе с parent Order. |
| `PENDING` | да | нет | Parent Order отправлен/мог быть отправлен, active-факт ещё не подтверждён. |
| `ACTIVE` | да | нет | Attached protection подтверждена refresh-фактами. |
| `COMPLETED` | нет | да | Attached protection сработала. |
| `CANCELED` | нет | да | Attached protection отменена/снята. |
| `ERROR` | нет как штатный active | да как problem-final | Attached protection в ошибочном состоянии. |

---

# 7. External status resolver

## 7.1. `OrderExternalStatusResolver`

`OrderExternalStatusResolver` переводит внешний snapshot ordinary order в доменный статус `Order.Status`.

Контракт:

```text
OrderExternalSnapshot + ExchangeCode -> Order.Status
```

Resolver не должен:

* сохранять `Order`;
* менять `Deal`;
* создавать commands;
* принимать FSM-решения.

Он только нормализует внешний статус.

## 7.2. OKX mapping для ordinary order

| OKX source | OKX raw status | Domain status | Комментарий |
|---|---|---|---|
| order details / pending | `live` | `ACTIVE` | Ордер live на бирже. |
| order details / pending | `partially_filled` | `PARTIALLY_COMPLETED` | Ордер частично исполнен и может иметь live-остаток. |
| order details / history | `filled` | `COMPLETED` | Ордер полностью исполнен. |
| order details / history | `canceled` | `CANCELED` | Ордер отменён. |
| order history | `mmp_canceled` | `CANCELED` | Ордер отменён механизмом MMP; на первом этапе считаем отменой. |
| any | unknown value | throws `UnknownExternalStatusException` | Refresh-flow переводит локальный `Order` в `ERROR` с `closeReason = UNKNOWN_EXTERNAL_STATUS`. После этого `Deal` переводится в `ERROR`, а `Exchange` — в `HOLD`. |

## 7.3. Unknown external status policy

Если resolver получил неизвестный внешний статус ordinary order:

```text
UnknownExternalStatusException
  -> RefreshExecutor / refresh boundary ловит controlled exception
  -> локальный Order переводится в ERROR
  -> Order.closeReason = UNKNOWN_EXTERNAL_STATUS
  -> текущий Deal переводится в ERROR
  -> включается ErrorHandler / safety-flow
  -> Exchange переводится в HOLD
```

Важно:

```text
Resolver не возвращает Order.Status.ERROR как обычный mapping-result.

ERROR — это не распознанный биржевой статус, а safety-состояние локального Order,
которое выставляется refresh-flow после перехвата UnknownExternalStatusException.
```

`Exchange HOLD` блокирует normal trading commands:

```text
SUBMIT_ORDER
SUBMIT_ALGO_ORDER
AMEND_ORDER
AMEND_ALGO_ORDER
```

`Exchange HOLD` не блокирует safety/read commands:

```text
REFRESH_*
SEARCH / HISTORY
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
EXECUTE_KILL_SWITCH
```

---

# 8. Attached protection resolving

У OKX `attachAlgoOrds` не имеет полноценного отдельного `state`, аналогичного ordinary order `state`.

Поэтому attached protection обновляется не через простой external status resolver, а через resolver по фактам:

```text
OrderExternalSnapshot
  -> attachedAlgoOrders
  -> match by AttachedAlgoOrder.internalId == AttachedAlgoOrderExternalSnapshot.internalId
  -> AttachedAlgoOrderStateResolver
```

Основной ключ сопоставления:

```text
AttachedAlgoOrder.internalId == AttachedAlgoOrderExternalSnapshot.internalId
```

Для OKX:

```text
AttachedAlgoOrder.internalId -> attachAlgoClOrdId
AttachedAlgoOrderExternalSnapshot.externalAttachedId -> attachAlgoId
```

## 8.1. `PENDING` vs `ACTIVE`

```text
PENDING
  -> ставится после SUBMIT_ORDER parent order,
     когда attached protection могла быть отправлена вместе с parent order,
     но active-факт ещё не подтверждён.

ACTIVE
  -> ставится только после REFRESH_ORDER / REFRESH_PENDING_ORDERS,
     если attached protection найдена в OrderExternalSnapshot.attachedAlgoOrders по internalId
     и нет failCode / failReason.
```

---

# 9. Missing attached protection policy

Базовое правило:

> Отсутствие `AttachedAlgoOrderExternalSnapshot` внутри одного `OrderExternalSnapshot.attachedAlgoOrders` не является финальным фактом.

Если после `REFRESH_ORDER` attached protection не найдена по `internalId`, решение зависит от состояния parent `Order` и runtime facts.

## 9.1. Parent Order `CREATED` / `PENDING`

```text
AttachedAlgoOrder остаётся PENDING.
FSM ждёт следующий refresh / retry / recovery.
```

## 9.2. Parent Order `ACTIVE` / `PARTIALLY_COMPLETED`

```text
Запускается дополнительный search-cycle.
Не делаем финальный вывод по одному snapshot.
```

Дополнительные факты:

```text
REFRESH_PENDING_ORDERS
REFRESH_ORDER_HISTORY
REFRESH_FILLS
REFRESH_POSITION
```

## 9.3. Parent Order `COMPLETED`

Если позиция active и standalone main protection отсутствует:

```text
AttachedAlgoOrder -> ERROR
closeReason = PROTECTION_LOST
Deal -> ERROR
```

Если позиция закрыта, нужно анализировать fills/history:

```text
attached сработал -> COMPLETED / TRIGGERED
позиция закрыта другим способом -> CANCELED / UNKNOWN
непонятно -> ERROR / UNKNOWN
```

## 9.4. Parent Order `CANCELED`

```text
AttachedAlgoOrder -> CANCELED
closeReason = PARENT_ORDER_CANCELED
```

## 9.5. Parent Order `ERROR`

```text
AttachedAlgoOrder -> ERROR
closeReason = UNKNOWN
```

---

# 10. Exchange facts, которые обновляют `Order`

`Order` может обновляться из нескольких источников exchange facts.

## 10.1. `REFRESH_ORDER`

Обновляет конкретный parent `Order` из `OrderExternalSnapshot`.

Может обновить:

```text
externalId
externalStatus
status через OrderExternalStatusResolver
side
price
size
accumulatedFillSize
averagePrice
fee
attachedAlgoOrders
```

## 10.2. `REFRESH_PENDING_ORDERS`

Обновляет список live/pending ordinary orders по инструменту.

Если parent `Order` найден среди pending orders, он обновляется из snapshot.

Если parent `Order` не найден среди pending orders, это не является финальным фактом отмены или исполнения.

## 10.3. `REFRESH_ORDER_HISTORY`

Используется, когда ordinary order уже не найден среди pending или нужен terminal-факт.

Подтверждает:

```text
COMPLETED
CANCELED
```

Если history snapshot содержит неизвестный внешний статус, `OrderExternalStatusResolver` бросает
`UnknownExternalStatusException`, а refresh-flow переводит локальный `Order` в:

```text
Order.status = ERROR
Order.closeReason = UNKNOWN_EXTERNAL_STATUS
```

## 10.4. `REFRESH_FILLS`

Обновляет execution facts у известных runtime-сущностей.

Для `Order` может уточнить:

```text
accumulatedFillSize
averagePrice
fee
```

`REFRESH_FILLS` не обновляет `Deal` напрямую.

---

# 11. Связь с `DealActionState`

`Order` не хранит ссылку на `StrategyAction`.

Связь хранится так:

```text
DealActionState(
  dealId,
  strategyActionId,
  target = RuntimeTarget(ORDER, order.id)
)
```

Для `AMEND_ORDER` / `CANCEL_ORDER` текущий action находит target action через `targetActionKey`, затем через `DealActionState` получает конкретный `Order`.

Подробный command-flow описан в документе `Сервисные команды.md`.

---

# 12. Что специально не хранится в `Order`

На первом этапе `Order` не хранит:

```text
strategyActionId
strategyActionKey
marginMode / tradeMode
positionSide
instrument external rules
fresh market price
raw command result history
```

Причины:

* `strategyActionId` хранится в `DealActionState`.
* `tdMode = isolated` и `posSide = net` задаются константами в `OkxClientService`.
* Внешние правила инструмента и текущая цена собираются в `CalculationContext` перед расчётом action.
* История command execution проектируется отдельно и не является runtime state.

---

# 13. Ссылки на связанные документы

* `Статусы торговых сущностей.md` — общий master index по статусам и active/closed/live risk semantics.
* `Сервисные команды.md` — command-layer, `CREATE -> SUBMIT -> REFRESH`, retry/recovery, executor boundaries.
* `FSM этапы сделки.md` — поведение handlers, protection switch и reaction на missing attached protection.
* `Жизненный цикл сделки.md` — общий lifecycle сделки, `DealContext`, terminal statuses.
* `Strategy.md` — strategy actions, `targetActionKey`, запрет direct partial close.
* `Оценка рисков.md` — risk validation, reduce-only checks для partial exit.
