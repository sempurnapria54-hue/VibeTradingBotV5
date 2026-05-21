# Position

> Статус документа: финальная модель runtime-сущности `Position` для торгового движка.
>
> Документ фиксирует доменную модель позиции, статусы, причины закрытия, live-risk semantics, refresh policy, связь с `DealContext`, `CLOSE_POSITION` и роль `Position` в финализации сделки.
>
> Exchange-specific mapping для OKX должен быть вынесен в отдельный документ: `OKX_Position_mapping.md`.

---

# 1. Назначение

`Position` — runtime-сущность позиции внутри `Deal`.

Она отражает текущее состояние позиции, которую торговый движок сопровождает в рамках сделки.

`Position` отвечает за вопрос:

```text
есть ли live-risk позиция по сделке прямо сейчас
```

`Position` не отвечает за:

```text
итоговый profit/loss сделки;
историю команд;
историю strategy actions;
сырые exchange responses;
полную копию response конкретной биржи.
```

Главное правило:

```text
Position хранит только данные, нужные для сопровождения live-risk позиции.
```

---

# 2. Главные инварианты

* `Position` принадлежит `Deal` через `dealId`.
* В рамках одной `Deal` допускается максимум одна `Position`.
* `Position` не хранит `instrumentId`.
* `Position` не хранит `exchangeId`.
* `Position` не хранит `internalId`.
* `Position` не хранит `strategyActionId`.
* `Position` не хранит `strategyActionKey`.
* `Position` создаётся и обновляется только через `REFRESH_POSITION` executor.
* FSM напрямую не создаёт `Position` и не заполняет её поля.
* `Position` не является client-created entity и не имеет stable client id.
* `Position.externalId` хранит биржевой ID позиции, если биржа его отдаёт.
* Для OKX `externalId` соответствует `posId`.
* `externalId` не считается вечным stable id, потому что конкретная биржа может очищать id позиции после закрытия.
* `Position.direction` — доменное направление позиции: `LONG` или `SHORT`.
* `Position.Direction.NET` не используется.
* Live risk по позиции определяется вычисляемо:

```text
Position.status == ACTIVE && Position.externalSize > 0
```

* `CLOSE_POSITION` используется только для полного закрытия позиции.
* Direct partial close через `Position` запрещён.
* Частичное уменьшение позиции выполняется только через reduce-only `Order` / `AlgoOrder` actions.
* ACK от `CLOSE_POSITION` не является runtime truth.
* Факт закрытия позиции подтверждается через `REFRESH_POSITION`.
* `Position` не используется для итогового расчёта PnL сделки.
* Итоговый `Deal.resultProfit` считается на основании фактов исполнений, собранных через `REFRESH_FILLS`.

---

# 3. Доменная модель `Position`

```java
package com.example.tradingbot.domain.model.core.position;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Runtime-сущность позиции внутри сделки.
 *
 * Простыми словами:
 * - позиция появляется на бирже как результат исполнения ордера;
 * - локально она материализуется через REFRESH_POSITION;
 * - она показывает, есть ли live-risk по сделке;
 * - она не является копией exchange response;
 * - она не хранит strategy/action/audit history.
 */
@Getter
@Setter
public class Position extends Auditable {

    /**
     * Внутренний технический идентификатор позиции в БД.
     */
    private Long id;

    /**
     * Идентификатор сделки, в рамках которой сопровождается позиция.
     */
    private Long dealId;

    /**
     * Биржевой ID позиции, если конкретная биржа его отдаёт.
     *
     * Для OKX соответствует posId.
     *
     * Не считается stable client id и не используется как единственный источник
     * идемпотентности, потому что позиция не создаётся клиентом напрямую.
     */
    private String externalId;

    /**
     * Текущий доменный статус позиции.
     */
    private Status status;

    /**
     * Причина закрытия позиции или problem reason для ошибочного состояния.
     *
     * Не дублирует Deal.CloseReason.
     */
    private CloseReason closeReason;

    /**
     * Доменное направление позиции.
     *
     * Используются только LONG / SHORT.
     *
     * NET не используется как direction, потому что net — это режим/сторона
     * позиции на бирже, а не направление рыночного риска.
     */
    private Direction direction;

    /**
     * Размер позиции по данным биржи.
     *
     * Хранится как нормализованное абсолютное значение.
     *
     * Для OKX в net-mode:
     * - pos > 0 -> direction = LONG, externalSize = abs(pos);
     * - pos < 0 -> direction = SHORT, externalSize = abs(pos).
     */
    private BigDecimal externalSize;

    /**
     * Средняя цена входа по данным биржи.
     */
    private BigDecimal externalAverageEntryPrice;

    /**
     * Mark price позиции по данным биржи.
     */
    private BigDecimal externalMarkPrice;

    /**
     * Расчётная цена ликвидации по данным биржи.
     */
    private BigDecimal externalLiquidationPrice;

    /**
     * Маржа позиции по данным биржи.
     */
    private BigDecimal externalMargin;

    /**
     * Нереализованный PnL по данным биржи.
     */
    private BigDecimal externalUnrealizedProfit;

    public enum Status {

        /**
         * Позиция существует на бирже и сопровождается системой.
         *
         * Важно:
         * ACTIVE сам по себе ещё не означает live market risk.
         * Live risk есть только если externalSize > 0.
         */
        ACTIVE,

        /**
         * Позиция закрыта.
         *
         * Для OKX это означает, что REFRESH_POSITION не нашёл позицию
         * в актуальном snapshot по инструменту.
         */
        CLOSED,

        /**
         * Ошибочное/problem состояние позиции.
         *
         * Используется, если exchange facts нельзя безопасно интерпретировать
         * или client/adapter-layer обнаружил нарушение exchange invariant.
         */
        ERROR
    }

    public enum CloseReason {

        /**
         * Бот сам инициировал штатное закрытие позиции как действие стратегии.
         *
         * Детальная бизнес-причина завершения сделки хранится в Deal.CloseReason.
         */
        CLOSED_BY_STRATEGY,

        /**
         * Позиция закрыта аварийным safety-flow / kill-switch.
         */
        KILL_SWITCH,

        /**
         * Пользователь вручную инициировал закрытие позиции.
         */
        MANUAL_CLOSE,

        /**
         * Позиция закрылась на стороне биржи без текущей команды close-position.
         *
         * Примеры:
         * - сработал наш SL;
         * - сработал наш TP;
         * - сработал trailing;
         * - liquidation;
         * - ADL;
         * - иной exchange-side event.
         */
        EXTERNAL_CLOSE,

        /**
         * Problem reason для Position.ERROR.
         *
         * Используется, если client/adapter-layer обнаружил нарушение
         * exchange-specific invariant.
         */
        EXCHANGE_INVARIANT_VIOLATION,

        /**
         * Fallback, если причину безопасно определить не удалось.
         */
        UNKNOWN
    }

    public enum Direction {

        /**
         * Long position.
         */
        LONG,

        /**
         * Short position.
         */
        SHORT
    }

    /**
     * Есть ли у позиции live market risk.
     */
    public boolean hasLiveRisk() {
        return status == Status.ACTIVE
                && externalSize != null
                && externalSize.compareTo(BigDecimal.ZERO) > 0;
    }
}
```

`externalCreatedAt` и `externalModifiedAt` наследуются от `Auditable`.

---

# 4. `Position.Status`

Используется минимальный набор статусов:

```java
public enum Status {
    ACTIVE,
    CLOSED,
    ERROR
}
```

Не добавляем:

```text
CREATED
PENDING
OPENING
CLOSING
PARTIALLY_CLOSED
```

Причины:

* `Position` не создаётся локально до биржи.
* `Position` появляется на бирже как результат исполнения `Order`.
* Локально `Position` материализуется через `REFRESH_POSITION`.
* ACK от `CLOSE_POSITION` не является runtime truth.
* Для закрытия других runtime-сущностей тоже не вводится отдельный промежуточный close-status.
* Частичное уменьшение позиции — это `ACTIVE` с обновлённым `externalSize`, а не отдельный статус.

---

# 5. `Position.CloseReason`

`Position.CloseReason` не дублирует `Deal.CloseReason`.

Разделение ответственности:

```text
Position.CloseReason
  -> каким механизмом/типом была закрыта позиция

Deal.CloseReason
  -> почему завершилась сделка с точки зрения торговой логики и аналитики
```

Примеры:

```text
Сработал Stop-Loss:
  Position.CloseReason = EXTERNAL_CLOSE
  Deal.CloseReason = STOP_LOSS

Стратегия явно закрыла позицию через CLOSE_POSITION:
  Position.CloseReason = CLOSED_BY_STRATEGY
  Deal.CloseReason = STRATEGY_EXIT

Kill-switch закрыл позицию:
  Position.CloseReason = KILL_SWITCH
  Deal.CloseReason = EMERGENCY_CLOSE
```

`closeReason` не должен перетираться.

Правило применения:

```text
если Position.closeReason уже заполнен:
  не менять

если Position.closeReason == null:
  можно применить candidate из resolver / executor context
```

---

# 6. `Position.Direction`

`Direction` хранит доменное направление позиции:

```java
public enum Direction {
    LONG,
    SHORT
}
```

`NET` в `Direction` не используется.

Для OKX net-mode:

```text
pos > 0 -> Direction.LONG
pos < 0 -> Direction.SHORT
externalSize = abs(pos)
```

`posSide = net` — это exchange-specific факт, который валидируется в `ClientService` / adapter-layer и не хранится в `Position`.

Заполнение `direction` выполняется только в `REFRESH_POSITION` executor через direction resolver.

При создании `Position` direction сверяется с expected direction из `DealContext` / entry action / entry order.

При обновлении active `Position` direction не должен меняться. Смена направления live position считается нарушением инварианта и ведёт в error/safety-flow.

---

# 7. Active / Closed / Live risk semantics

Нужно различать:

```text
Position.status
и
live market risk
```

Правила:

```text
Position.status == ACTIVE
  -> позиция существует на бирже / сопровождается системой

Position.status == ACTIVE && externalSize > 0
  -> есть live market risk

Position.status == ACTIVE && externalSize == 0
  -> позиция всё ещё возвращается биржей, но live market risk нет
  -> cleanup / anomaly / retry case

Position.status == CLOSED
  -> позиции на бирже нет

Position.status == ERROR
  -> problem state, не является normal closed
```

Для OKX:

```text
snapshot == null
  -> позиции на бирже нет
  -> Position.status = CLOSED

snapshot != null
  -> позиция на бирже есть
  -> Position.status = ACTIVE
```

Если `snapshot.externalSize == 0`, позиция остаётся `ACTIVE`, потому что биржа всё ещё возвращает позиционный snapshot.

Такой случай не считается normal `CLOSED`, но не создаёт live market risk.

---

# 8. `PositionExternalSnapshot`

`PositionExternalSnapshot` — это не raw/diagnostic exchange response.

Это нормализованный объект, который нужен только для обновления `Position`.

Если поле не обновляет `Position`, оно не должно попадать в `PositionExternalSnapshot`.

```java
package com.example.tradingbot.domain.model.core.position.external_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Нормализованный внешний snapshot позиции.
 *
 * Создаётся только если позиция реально найдена на бирже.
 *
 * Если позиция не найдена, ClientService возвращает null,
 * а не пустой snapshot.
 */
@Getter
@Setter
public class PositionExternalSnapshot extends Auditable {

    /**
     * Биржевой ID позиции, если биржа его отдаёт.
     */
    private String externalId;

    /**
     * Размер позиции по данным биржи.
     */
    private BigDecimal externalSize;

    /**
     * Средняя цена входа по данным биржи.
     */
    private BigDecimal externalAverageEntryPrice;

    /**
     * Mark price позиции по данным биржи.
     */
    private BigDecimal externalMarkPrice;

    /**
     * Расчётная цена ликвидации по данным биржи.
     */
    private BigDecimal externalLiquidationPrice;

    /**
     * Маржа позиции по данным биржи.
     */
    private BigDecimal externalMargin;

    /**
     * Нереализованный PnL по данным биржи.
     */
    private BigDecimal externalUnrealizedProfit;
}
```

Правило:

```text
позиция найдена     -> PositionExternalSnapshot
позиция не найдена  -> null
```

---

# 9. `PositionStatusResolver`

`PositionStatusResolver` возвращает result-object:

```java
public class PositionStatusResolveResult {

    /**
     * Доменный статус позиции.
     */
    private Position.Status status;

    /**
     * Candidate причины закрытия / problem reason.
     *
     * Executor применяет его только если Position.closeReason ещё null.
     */
    private Position.CloseReason closeReason;
}
```

Базовая логика:

```text
snapshot == null
  -> status = CLOSED
  -> closeReason = EXTERNAL_CLOSE

snapshot != null
  -> status = ACTIVE
  -> closeReason = null
```

Если `snapshot.externalSize == 0`:

```text
status = ACTIVE
closeReason = null
live risk = false
```

Потому что snapshot найден, значит биржа всё ещё возвращает позицию.

---

# 10. `REFRESH_POSITION` policy

`REFRESH_POSITION` — единственный штатный способ создать или обновить локальную `Position`.

Flow:

```text
REFRESH_POSITION
  -> ClientService получает position snapshot с биржи
  -> если позиция найдена, возвращает PositionExternalSnapshot
  -> если позиция не найдена, возвращает null
  -> PositionStatusResolver возвращает status + closeReason candidate
  -> RefreshPositionExecutor применяет result к Position
```

Правила executor:

* применяет `status` из resolver всегда;
* `closeReason` заполняет только если текущий `Position.closeReason == null`;
* если `snapshot != null`, обновляет external-поля позиции;
* если `snapshot == null` и локальная `Position` уже есть, переводит её в `CLOSED`;
* если `snapshot == null` и локальной `Position` ещё нет, новую `CLOSED Position` не создаёт;
* если `snapshot != null` и локальной `Position` ещё нет в рамках active Deal flow, создаёт `Position` и привязывает её к `Deal`;
* обычный `REFRESH_POSITION` не получает `requestedCloseReason`, потому что refresh сам по себе не означает намерение закрытия.

Для OKX `REFRESH_POSITION` делает один логический exchange-запрос по instrument:

```text
GET /api/v5/account/positions?instType=SWAP&instId=...
```

Дополнительно по `posId` не ищем, потому что цель `REFRESH_POSITION` — доказать наличие или отсутствие live position по инструменту.

Ретраи выполняются только при технических/API проблемах.

---

# 11. Легитимное окно появления позиции

`Position` — единственная runtime-сущность, которая может сначала появиться на бирже, а потом локально в БД.

Нормальный сценарий:

```text
entry Order исполнен на бирже
  -> биржа создала позицию
  -> локальной Position ещё нет
  -> следующая REFRESH_POSITION находит позицию
  -> executor создаёт Position в БД
  -> Position.dealId = Deal.id
```

Это не anomaly, если есть active `Deal`, entry order и факты, которые объясняют появление позиции.

Если на бирже есть active position, но в БД нет active `Deal`, который может объяснить её появление, это зона `AnomalyJob` / safety-flow.

---

# 12. `CLOSE_POSITION` semantics

`CLOSE_POSITION` — атомарная команда полного закрытия позиции.

Правила:

* `CLOSE_POSITION` закрывает только всю позицию.
* Partial close через `CLOSE_POSITION` запрещён.
* Partial exit выполняется через reduce-only `Order` / `AlgoOrder` actions.
* `RiskValidator` для `CLOSE_POSITION` не вызывается.
* Handler / executor выполняет только minimal domain / exchange safety checks.
* ACK от биржи не является runtime truth.
* `ClosePositionExecutor` после ACK не переводит `Position` в `CLOSED`.
* Факт закрытия подтверждается через `REFRESH_POSITION`.

Минимальные проверки перед вызовом биржи:

```text
позиция существует локально;
позиция относится к текущей Deal;
Position.status = ACTIVE;
команда закрывает всю позицию;
есть DealContext с Exchange и Instrument;
есть данные для exchange request;
актуальные facts не доказывают, что позиции уже нет.
```

---

# 13. `Position` и fills / PnL

`Position` не хранит:

```text
fills
realizedPnl
fee
fundingFee
closePrice
```

`Position` отвечает только за live-risk состояние позиции:

```text
наличие / отсутствие позиции;
размер;
направление;
средняя цена входа;
mark price;
liquidation price;
margin;
unrealized PnL.
```

После `CLOSE_POSITION` для подтверждения закрытия позиции требуется `REFRESH_POSITION`.

`REFRESH_FILLS` используется в финализации сделки для итогового подсчёта profit/loss.

`Deal.resultProfit` считается на основании фактов исполнений, собранных через `REFRESH_FILLS`.

---

# 14. `Position` и `DealContext`

`Position` не хранит `instrumentId` и `exchangeId`.

Поэтому `DealContext` на каждой итерации FSM должен содержать:

```text
Deal
Exchange
Instrument
Position текущей Deal, если она уже материализована
```

По `Position` в `DealContext` нужна только одна текущая позиция сделки.

`relatedPositions` не нужны, потому что в рамках одной `Deal` допускается максимум одна `Position`.

`exchangePositionFact` не хранится в `DealContext`.

Правильная цепочка:

```text
exchange facts
  -> REFRESH_POSITION
  -> обновлённая Position в БД
  -> DealContext
  -> FSM decision
```

Отсутствие локальной `Position` допустимо в `ENTRY_SUBMITTED` до успешного `REFRESH_POSITION`.

---

# 15. Что `Position` принципиально хранит

`Position` хранит только данные, нужные для сопровождения live-risk позиции.

`Position` не является копией exchange response.

`Position` не хранит strategy/action/audit history.

`Position` не используется для итогового PnL сделки.

Exchange-specific поля, которые нужны только для request или validation, обрабатываются в `ClientService` / adapter-layer и не добавляются в `Position` автоматически.

---

# 16. Recovery-сценарий после падения приложения

Возможный штатный recovery-сценарий:

```text
1. Бот успел создать entry order с attached protection.
2. Приложение упало или потеряло связь с биржей.
3. На бирже entry order исполнился.
4. Биржа создала позицию.
5. Затем позиция закрылась по SL / TP / trailing.
6. После рестарта локальной Position может ещё не быть.
```

Это не anomaly, если есть active `Deal` и известный entry order, который объясняет появление позиции.

Recovery-flow:

```text
DealOrchestratorJob
  -> собирает DealContext
  -> видит Deal в recovery-compatible статусе
  -> запускает refresh-контур

REFRESH_ORDER / REFRESH_ORDER_HISTORY
  -> подтверждает, что entry order был исполнен

REFRESH_POSITION
  -> позиции уже нет

REFRESH_ALGO_ORDER_HISTORY / attached protection facts
  -> подтверждает, что сработал SL / TP / trailing

REFRESH_FILLS
  -> собирает факты исполнений для итогового PnL
```

Итог:

```text
Position может не создаваться как CLOSED, если локальной Position ещё не было.
Deal переходит в EXIT_PENDING и финализируется по собранным фактам.
Deal.closeReason = STOP_LOSS / TAKE_PROFIT / другое значение.
Deal.resultProfit считается через REFRESH_FILLS.
```

---

# 17. Связанные документы

* `OKX_Position_mapping.md`
* `Статусы торговых сущностей.md`
* `Сервисные команды.md`
* `FSM этапы сделки.md`
* `Жизненный цикл сделки.md`
* `Strategy.md`
* `Order.md`
* `AlgoOrder.md`
* `Оценка рисков.md`
* `Аудит и история исполнения.md`
