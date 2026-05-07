# Deal

> Статус документа: финальная доменная модель `Deal` для runtime-движка.
>
> Документ фиксирует назначение сделки, доменную модель, статусы, причины создания и закрытия, graceful shutdown semantics, итоговый profit/loss, runtime graph, границы ответственности с `DealContext`, `DealActionState`, FSM и recovery.
>
> `Deal` не является биржевой сущностью. Для `Deal` не нужен OKX mapping-документ.
>
> Связанные документы:
>
> * `Жизненный цикл сделки.md`
> * `FSM этапы сделки.md`
> * `Сервисные команды.md`
> * `Статусы торговых сущностей.md`
> * `Order.md`
> * `AlgoOrder.md`
> * `Position.md`
> * `Balance.md`
> * `Оценка рисков.md`
> * `Аудит и история исполнения.md`

---

# 1. Назначение

`Deal` — lifecycle root и runtime graph торговой сделки.

Она фиксирует, что система начала сопровождать конкретный торговый сценарий:

* по конкретному `Instrument`;
* по pinned `StrategyDetail`;
* в ожидаемом направлении `LONG` / `SHORT`;
* с конкретным FSM-статусом;
* с понятной причиной создания;
* с понятной причиной завершения;
* с итоговым profit/loss после финализации.

Простыми словами:

```text
Deal отвечает за жизненный цикл сделки.
```

`Deal` не отвечает за:

```text
сырые exchange responses;
полную историю команд;
историю изменений сущностей;
подробный entry context;
risk-check details;
свежие market data;
calculation data;
raw fills archive;
полную финансовую отчётность.
```

---

# 2. Главные инварианты

* `Deal` не является биржевой сущностью.
* У `Deal` нет external id на бирже.
* У `Deal` нет external status.
* Для `Deal` не нужен OKX mapping-документ.
* `Deal` является lifecycle root сделки.
* `Deal` содержит runtime graph сделки: `orders`, `algoOrders`, `position`.
* `Deal` не содержит `DealActionState` как поле модели.
* `DealActionState` связан с `Deal` через `dealId` и загружается в `DealContext` как operational FSM-state.
* `Deal` не хранит `marketPhaseId`.
* Фаза рынка входа выводится через pinned `StrategyDetail.marketPhaseType`.
* Точный `MarketPhase` result, timestamp, confidence и расчётное окно относятся к entry context / audit, а не к runtime-needed полям `Deal`.
* `Deal` не хранит `openedAt`, `closedAt`, `errorAt`.
* Базовые даты создания и изменения записи покрываются `Auditable.createdAt` / `Auditable.modifiedAt`.
* Фактические торговые моменты открытия / закрытия позиции выводятся через `Order`, `Position`, `TradeFill` facts и audit/timeline.
* `Deal.direction` — expected direction сделки, зафиксированный при создании.
* `Deal.entryReason` и `Deal.entryStepType` не управляют FSM.
* Подробный entry context хранится в `Аудит и история исполнения.md`, а не в `Deal`.
* `shutdownReason` и `closeReason` — разные поля.
* `shutdownReason` заполняется только если реально запускается graceful shutdown / controlled close-flow активной сделки.
* `closeReason` объясняет итоговую бизнес-причину завершения сделки.
* `Deal.Status` описывает бизнес-этап сделки, а не техническое состояние `Order`, `AlgoOrder`, `Position`, command execution или exchange ACK.
* `CLOSED` и `EMERGENCY_CLOSED` — terminal statuses.
* Terminal statuses не имеют FSM handlers.
* `ERROR` — non-terminal runtime state для safety / recovery / refresh / kill-switch flow.
* `ERROR -> CLOSED` запрещён.
* `ERROR -> EMERGENCY_CLOSED` разрешён только после safety-flow и подтверждения отсутствия live risk.
* Для terminal statuses `CLOSED` и `EMERGENCY_CLOSED` обязательны `resultProfit` и `resultProfitCurrency`.
* `resultProfit` считается через `REFRESH_FILLS` / `TradeFill` facts, а не через `BalanceContainer` diff.
* `resultProfit = 0` допустим только как результат расчёта, а не fallback при ошибке.

---

# 3. Границы ответственности

## 3.1. Что делает `Deal`

`Deal` хранит:

* идентичность сделки;
* ссылку на инструмент;
* ссылку на pinned `StrategyDetail`;
* текущий FSM-статус;
* expected direction;
* короткую причину создания;
* тип entry-step;
* причину graceful shutdown, если он был запущен;
* причину финального закрытия;
* итоговый profit/loss;
* runtime graph сделки: `Order`, `AlgoOrder`, `Position`.

## 3.2. Что делают связанные runtime-сущности

`Order`, `AlgoOrder`, `Position` связаны с `Deal` через `dealId`.

Они отвечают за exchange-bound runtime facts:

* создание / отправку / отмену / исполнение orders;
* standalone algo-order protection / TP / SL / trailing / OCO / partial exit;
* наличие и размер live-risk позиции;
* внешние IDs и доменные статусы биржевых сущностей;
* refresh/search/history recovery.

## 3.3. Что делает `DealActionState`

`DealActionState` не является частью модели `Deal`.

Он является persisted operational FSM-state конкретного `StrategyAction` внутри сделки.

Он нужен для:

* recovery после рестарта;
* retry;
* idempotency;
* понимания, какой action уже материализован;
* связи `StrategyAction -> runtime target`.

Связь:

```text
Deal.id
  -> DealActionState.dealId
     -> DealActionState.strategyActionId
        -> RuntimeTarget(entityType, entityId)
```

## 3.4. Граница с `DealContext`

`DealContext` не является частью модели `Deal`.

`DealContext` — процессный runtime-context одного прохода FSM. Он добавляет к `Deal` внешние зависимости обработки и operational state, которые нужны FSM:

* `Exchange`;
* `Instrument`;
* pinned `StrategyDetail`;
* последний persisted `BalanceContainer`;
* `DealActionState` list.

Полная модель и правила сборки `DealContext` описаны в документе `Жизненный цикл сделки.md`.

---

# 4. Доменная модель `Deal`

```java
package com.example.tradingbot.domain.model.core.deal;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lifecycle root и runtime graph сделки.
 *
 * Простыми словами:
 * - Deal создаётся локально, когда система решила сопровождать торговый сценарий;
 * - Deal не существует на бирже;
 * - Deal управляется FSM;
 * - связанные Order / AlgoOrder / Position материализуют runtime-состояние сделки;
 * - итоговый profit/loss считается через fills, а не через balance diff.
 */
@Getter
@Setter
public class Deal extends Auditable {

    /**
     * Внутренний технический идентификатор сделки в БД.
     */
    private Long id;

    /**
     * Безопасный внешний / межсервисный идентификатор сделки.
     *
     * Используется в API, логах, timeline и межслойных ссылках.
     */
    private String internalId;

    /**
     * Инструмент сделки.
     *
     * Полный Instrument подгружается в DealContext как внешняя зависимость обработки.
     */
    private Long instrumentId;

    /**
     * Pinned StrategyDetail, по которому живёт сделка.
     *
     * Даже если Strategy позже изменится / станет INACTIVE / DELETED,
     * открытая сделка сопровождается по этой pinned-версии StrategyDetail.
     */
    private Long strategyDetailId;

    /**
     * Текущий FSM-статус сделки.
     */
    private Status status;

    /**
     * Expected trading direction сделки.
     *
     * Фиксируется при создании Deal и используется для invariant checks:
     * Position.direction должен соответствовать Deal.direction.
     */
    private StrategyTradeDirection direction;

    /**
     * Короткая причина создания сделки.
     *
     * Подробный entry context хранится в audit/history, а не в Deal.
     */
    private EntryReason entryReason;

    /**
     * Тип entry-step, по которому была создана сделка.
     *
     * Допустимые значения:
     * - ENTRY;
     * - GRID_ENTRY;
     * - null, если сделка создана не через strategy entry-step.
     *
     * Не управляет runtime FSM.
     */
    private EntryStepType entryStepType;

    /**
     * Причина запуска graceful shutdown / controlled close-flow.
     *
     * Заполняется только если активную сделку реально переводят
     * из обычного сопровождения в controlled shutdown / controlled exit.
     *
     * Не заменяет closeReason.
     */
    private ShutdownReason shutdownReason;

    /**
     * Итоговая бизнес-причина завершения сделки.
     *
     * Не является техническим механизмом закрытия конкретной позиции.
     */
    private CloseReason closeReason;

    /**
     * Итоговый profit/loss сделки.
     *
     * Считается через REFRESH_FILLS / TradeFill facts,
     * а не через BalanceContainer diff.
     *
     * Для terminal statuses CLOSED / EMERGENCY_CLOSED обязателен.
     */
    private BigDecimal resultProfit;

    /**
     * Валюта итогового результата.
     *
     * Для текущего ETH-USDT-SWAP обычно USDT.
     *
     * Для terminal statuses CLOSED / EMERGENCY_CLOSED обязательна.
     */
    private String resultProfitCurrency;

    /**
     * Ordinary orders, связанные со сделкой.
     *
     * Attached protection хранится внутри Order.
     */
    private List<Order> orders;

    /**
     * Standalone algo-orders, связанные со сделкой.
     */
    private List<AlgoOrder> algoOrders;

    /**
     * Текущая позиция сделки.
     *
     * В рамках одной Deal допускается максимум одна Position.
     */
    private Position position;
}
```

---

# 5. `Deal.Status`

`Deal.Status` описывает lifecycle-этап сделки внутри FSM.

Он не описывает:

* статус ordinary order;
* статус standalone algo-order;
* статус позиции;
* статус command execution;
* exchange ACK.

```java
public enum Status {

    /**
     * Сделка создана локально, но live risk ещё не создан.
     *
     * На этом этапе FSM повторно проверяет входные условия,
     * свежесть данных, баланс, risk-policy и готовность к entry action.
     */
    PRECHECK,

    /**
     * Entry-flow начат.
     *
     * Локальный entry Order / AlgoOrder мог быть создан или отправлен,
     * но факт открытия позиции ещё не финализирован.
     *
     * ACK от биржи не считается завершением этапа.
     */
    ENTRY_SUBMITTED,

    /**
     * Вход в сделку подтверждён.
     *
     * Entry order / fills / position facts подтверждают,
     * что сделка получила позицию или корректный entry-result.
     */
    ENTRY_FINALIZED,

    /**
     * Temporary attached protection была заменена на основную standalone protection.
     *
     * Используется только если реально был protection switch.
     * Если switch не нужен, сделка может перейти из ENTRY_FINALIZED в MANAGING.
     */
    PROTECTION_SWITCHED,

    /**
     * Основное сопровождение открытой сделки.
     *
     * На этом этапе FSM применяет managing steps стратегии:
     * перенос SL, trailing, partial exit через reduce-only Order / AlgoOrder,
     * grid management, strategy exit и другие разрешённые действия.
     */
    MANAGING,

    /**
     * Запущен штатный выход из сделки.
     *
     * FSM снимает/обновляет защитные сущности, закрывает live risk,
     * refresh-ит Position / Orders / AlgoOrders / fills
     * и готовит сделку к CLOSED.
     */
    EXIT_PENDING,

    /**
     * Штатный terminal-финал сделки.
     *
     * Live risk отсутствует и это подтверждено runtime facts.
     * Для статуса обязательны resultProfit и resultProfitCurrency.
     * После CLOSED FSM handler не запускается.
     */
    CLOSED,

    /**
     * Ошибочное runtime-состояние.
     *
     * Это не terminal-финал и не закрытая сделка.
     * Обычные strategy steps больше не выполняются.
     * Разрешены только safety / recovery / refresh / kill-switch действия.
     */
    ERROR,

    /**
     * Аварийный terminal-финал после safety-flow.
     *
     * Используется, когда сделка была в ERROR,
     * система сняла или доказала отсутствие live risk,
     * рассчитала resultProfit,
     * и сделку можно безопасно завершить аварийно.
     */
    EMERGENCY_CLOSED
}
```

## 5.1. Active / terminal groups

Active / runtime statuses:

```text
PRECHECK
ENTRY_SUBMITTED
ENTRY_FINALIZED
PROTECTION_SWITCHED
MANAGING
EXIT_PENDING
ERROR
```

Terminal statuses:

```text
CLOSED
EMERGENCY_CLOSED
```

Важно:

```text
ERROR — active runtime status, но не normal active trading status.
```

`ERROR` означает, что сделка ещё требует обработки, но не через обычные strategy steps.

## 5.2. Status invariants

```text
CLOSED — только штатное завершение.

EMERGENCY_CLOSED — только аварийное завершение после safety-flow.

ERROR -> CLOSED запрещён.

ERROR -> EMERGENCY_CLOSED разрешён только после подтверждения отсутствия live risk
и расчёта resultProfit.

Terminal statuses не имеют FSM handlers.

Если после terminal status найден live risk — это зона AnomalyJob / ReconciliationJob,
а не обычный FSM-flow.
```

---

# 6. `EntryReason` и `entryStepType`

`entryReason` отвечает на вопрос:

```text
почему вообще появилась Deal?
```

`entryStepType` отвечает на вопрос:

```text
из какого типа entry-step она появилась?
```

Они не управляют FSM.

## 6.1. `EntryReason`

```java
public enum EntryReason {

    /**
     * Сделка создана EntryScannerJob по условиям стратегии.
     */
    STRATEGY,

    /**
     * Сделка создана вручную пользователем / оператором.
     */
    MANUAL,

    /**
     * Сделка создана как часть recovery-flow,
     * когда система восстанавливает уже существующий runtime risk.
     */
    RECOVERY,

    /**
     * Fallback, если причина создания не определена.
     * Не должен использоваться в normal flow.
     */
    UNKNOWN
}
```

## 6.2. `EntryStepType`

`entryStepType` оставляем как в lifecycle-доке.

Допустимые значения:

```text
ENTRY
GRID_ENTRY
null
```

```java
public enum EntryStepType {

    /**
     * Сделка создана из обычного entry-step стратегии.
     */
    ENTRY,

    /**
     * Сделка создана из grid-entry step стратегии.
     */
    GRID_ENTRY
}
```

Правила:

```text
entryReason = STRATEGY, entryStepType = ENTRY
  -> обычная сделка по стратегии

entryReason = STRATEGY, entryStepType = GRID_ENTRY
  -> grid-сделка по стратегии

entryReason = MANUAL, entryStepType = null
  -> ручная сделка

entryReason = RECOVERY, entryStepType = ENTRY / GRID_ENTRY / null
  -> восстановленная сделка, если исходный entry-step известен
```

---

# 7. `CloseReason`

`Deal.CloseReason` описывает бизнес-причину завершения сделки.

Он не описывает технический механизм закрытия позиции.

Например, trailing stop считается механизмом `STOP_LOSS`, а конкретный механизм определяется через `Order` / `AlgoOrder` / `DealActionState` / audit.

```java
public enum CloseReason {

    /**
     * Candidate Deal закрыт в PRECHECK,
     * потому что входное условие стратегии больше не актуально
     * до создания live risk.
     */
    ENTRY_CONDITION_EXPIRED,

    /**
     * Сделка закрыта по правилу стратегии.
     */
    STRATEGY_EXIT,

    /**
     * Сделка закрыта по take-profit.
     */
    TAKE_PROFIT,

    /**
     * Сделка закрыта по stop-loss.
     *
     * Включает fixed SL и trailing SL.
     * Конкретный механизм определяется через Order / AlgoOrder / DealActionState / audit.
     */
    STOP_LOSS,

    /**
     * Сделка закрыта по time-stop.
     */
    TIME_STOP,

    /**
     * Сделка штатно завершена risk-control логикой.
     *
     * Например:
     * - risk-layer заблокировал вход в PRECHECK до создания live risk;
     * - controlled risk exit без аварийного ERROR-flow.
     */
    RISK_CONTROL,

    /**
     * Сделка закрыта вручную пользователем / оператором.
     */
    MANUAL_CLOSE,

    /**
     * Сделка аварийно завершена после ERROR / safety-flow / kill-switch,
     * когда отсутствие live risk подтверждено.
     */
    EMERGENCY_CLOSE,

    /**
     * Причину безопасно определить не удалось.
     */
    UNKNOWN
}
```

Правила:

```text
ENTRY_RISK_BLOCKED не используем.
TRAILING_STOP не используем.

RISK_CONTROL используется для штатного risk-control завершения без аварийного ERROR-flow,
включая risk-layer block в PRECHECK до создания live risk.

EMERGENCY_CLOSE используется только для аварийного финала EMERGENCY_CLOSED.
```

---

# 8. `ShutdownReason`

`shutdownReason` объясняет, почему активную сделку перевели в graceful shutdown / controlled close-flow.

`shutdownReason` не означает, что сделка уже закрылась.

`shutdownReason` не заменяет `closeReason`.

```java
public enum ShutdownReason {

    /**
     * Стратегия была логически удалена.
     * Новые сделки запрещены, открытые сделки ведём к graceful shutdown.
     */
    STRATEGY_DELETED,

    /**
     * Устаревшие market data привели к graceful shutdown.
     *
     * Важно: ставится не при любом stale market data,
     * а только если policy обработки устаревших данных решила
     * завершать сделку controlled-exit flow.
     */
    MARKET_DATA_EXPIRED,

    /**
     * Пользователь / оператор вручную запросил остановку сделки.
     */
    MANUAL_STOP,

    /**
     * Risk-policy решила перевести сделку в graceful shutdown,
     * но без аварийного ERROR / kill-switch flow.
     */
    RISK_POLICY,

    /**
     * Биржа / аккаунт / инструмент переведены в HOLD.
     *
     * Новые risk-creating / risk-increasing действия запрещены,
     * но refresh / cleanup / controlled exit ещё допустимы.
     */
    EXCHANGE_HOLD,

    /**
     * Причина graceful shutdown не определена.
     */
    UNKNOWN
}
```

## 8.1. Когда заполняется `shutdownReason`

Заполняем только если реально запускается graceful shutdown / controlled close-flow активной сделки.

Примеры:

```text
Strategy.DELETED
  -> запускаем graceful shutdown
  -> shutdownReason = STRATEGY_DELETED
```

```text
Exchange / Instrument / Account HOLD
  -> policy решила выводить активную сделку
  -> shutdownReason = EXCHANGE_HOLD
```

```text
Market data expired
  -> policy решила закрывать сделку
  -> shutdownReason = MARKET_DATA_EXPIRED
```

## 8.2. Когда `shutdownReason` не заполняется

Обычный strategy exit:

```text
strategy exit condition true
  -> EXIT_PENDING
  -> shutdownReason = null
  -> closeReason = STRATEGY_EXIT
```

TP / SL:

```text
TP или SL закрыл позицию
  -> shutdownReason = null
  -> closeReason = TAKE_PROFIT / STOP_LOSS
```

Закрытие candidate deal в `PRECHECK`:

```text
entry condition expired до live risk
  -> status = CLOSED
  -> shutdownReason = null
  -> closeReason = ENTRY_CONDITION_EXPIRED
```

Risk block в `PRECHECK` до live risk:

```text
risk-policy заблокировала вход
  -> status = CLOSED
  -> shutdownReason = null
  -> closeReason = RISK_CONTROL
```

---

# 9. `resultProfit` и финализация

В `Deal` храним:

```java
private BigDecimal resultProfit;
private String resultProfitCurrency;
```

Правила:

```text
Для CLOSED и EMERGENCY_CLOSED оба поля обязательны.

resultProfit считается через REFRESH_FILLS / TradeFill facts,
а не через BalanceContainer diff.

Если resultProfit временно нельзя посчитать,
финализация retry-ится по общей retry-policy.

resultProfit = 0 допустим только как результат расчёта,
а не как fallback при ошибке.
```

`REFRESH_BALANCE` после выхода нужен для актуального account snapshot, но не для расчёта profit/loss сделки.

Детальный breakdown на первом этапе не храним в `Deal`:

```text
fees;
fundingFee;
grossProfit;
netProfit;
entry fills;
exit fills;
average entry price;
average exit price;
partial exits.
```

Эти данные должны восстанавливаться через `TradeFill` facts / финализационный расчёт / отчёты / audit.

---

# 10. Runtime graph `Deal`

`Deal` содержит runtime graph торговых сущностей сделки:

```text
Deal
  -> orders
  -> algoOrders
  -> position
```

## 10.1. `orders`

`orders` — ordinary orders, связанные со сделкой.

Подробная модель описана в `Order.md`.

`Order` не хранит:

```text
strategyActionId
strategyActionKey
role
level стратегии
```

Связь с action стратегии идёт через `DealActionState`.

## 10.2. `algoOrders`

`algoOrders` — standalone algo-orders, связанные со сделкой.

Подробная модель описана в `AlgoOrder.md`.

Attached protection не является standalone `AlgoOrder` и хранится внутри parent `Order`.

## 10.3. `position`

`position` — текущая позиция сделки, если она материализована.

Подробная модель описана в `Position.md`.

Правила:

* в рамках одной `Deal` допускается максимум одна `Position`;
* `Position` создаётся и обновляется только через `REFRESH_POSITION`;
* FSM напрямую не создаёт `Position`;
* live risk по позиции считается вычисляемо: `Position.status == ACTIVE && Position.externalSize > 0`.

## 10.4. Что не входит в runtime graph `Deal`

В `Deal` не включаем:

```text
DealActionState;
Exchange;
Instrument;
StrategyDetail;
BalanceContainer;
TradeFill archive;
raw exchange facts;
CalculationContext;
MarketPriceData;
IndicatorValue;
MarketStructure;
MarketPhase runtime data;
audit/history;
pending ServiceCommand.
```

---

# 11. Связь с `DealContext`

`DealContext` не является доменной моделью сделки и не должен подробно описываться в `Deal.md`.

В рамках модели `Deal` важно только зафиксировать границу:

```text
Deal
  -> хранит lifecycle-поля сделки
  -> содержит runtime graph: orders, algoOrders, position

DealContext
  -> содержит Deal
  -> добавляет внешние зависимости обработки
  -> добавляет DealActionState как operational FSM-state
```

Полное описание `DealContext`, его состава и правил сборки находится в документе `Жизненный цикл сделки.md`.

---

# 12. Terminal semantics и live risk

## 12.1. Active Deal

`Deal` считается active, если он не находится в terminal status.

Terminal statuses:

```text
CLOSED
EMERGENCY_CLOSED
```

`ERROR` не считается terminal status.

## 12.2. Terminal Deal

`Deal` считается terminal, если:

* `status = CLOSED`;
* или `status = EMERGENCY_CLOSED`.

Terminal `Deal` не имеет FSM handler.

Для terminal `Deal` обязательны:

```text
resultProfit
resultProfitCurrency
```

## 12.3. Deal live risk

`Deal` имеет live risk, если через связанные сущности есть хотя бы одно:

* active `Position` с live market risk;
* live `Order`;
* live `AlgoOrder`;
* unknown external live-сущность на бирже;
* расхождение, из-за которого невозможно доказать отсутствие live risk.

Live risk не хранится в `Deal` отдельным boolean-полем.

Он вычисляется через `Deal` runtime graph, `DealActionState`, refresh/search/history facts и anomaly/safety context.

Если после terminal status найден live risk, это зона `AnomalyJob / ReconciliationJob`, а не обычный FSM-flow.

---

# 13. Restart / recovery semantics

После рестарта система не ищет pending `ServiceCommand`.

`ServiceCommand` — runtime object, а не persisted queue.

FSM восстанавливает состояние по:

```text
Deal runtime graph;
DealContext external dependencies;
DealActionState;
exchange refresh/search/history facts.
```

`DealActionState` нужен, чтобы понять:

* какой `StrategyAction` уже был материализован;
* какой runtime target был создан;
* какой action находится в retry;
* какой action completed / failed / skipped;
* какой order/algoOrder нужно amend/cancel.

Audit/history не является runtime-source для FSM.

---

# 14. Что не хранится в `Deal`

В `Deal` не храним:

```text
marketPhaseId;
openedAt;
closedAt;
errorAt;
полный entry context;
полный StrategyCondition snapshot;
MarketPriceData;
IndicatorValue snapshot;
MarketStructure snapshot;
MarketPhase runtime result;
raw OKX responses;
exchange snapshots;
fills archive;
ServiceCommand history;
RiskValidationResult;
CalculationContext;
BalanceContainer;
Balance snapshot;
runtime locks;
pending ServiceCommand;
before/after snapshots;
audit timeline.
```

Причина:

```text
Deal хранит lifecycle-значимые поля и runtime graph сделки.
Остальное относится к runtime dependencies, calculation context, exchange facts или audit/history.
```

---

# 15. Открытые вопросы

## 15.1. Retry-state финализации сделки

Нужно отдельно решить, где хранить persisted retry-state для lifecycle / finalization commands:

* `REFRESH_FILLS`;
* `FINALIZE_DEAL_EXIT`;
* `MARK_DEAL_CLOSED`;
* emergency finalization.

Проблема:

```text
DealActionState относится к StrategyAction,
а финализация сделки — это lifecycle/system action.
```

Audit/history не должен быть runtime-source, поэтому retry-state финализации нельзя хранить только в истории.

## 15.2. Что делать, если resultProfit нельзя посчитать после исчерпания retry attempts

Зафиксировано:

```text
resultProfit и resultProfitCurrency обязательны для CLOSED / EMERGENCY_CLOSED.
resultProfit = 0 допустим только как результат расчёта, а не fallback.
```

Но пока не решено, что делать, если после всех retry итоговый profit/loss всё ещё нельзя безопасно посчитать.

Варианты для будущего обсуждения:

* оставить сделку в отдельном finalization state;
* перевести сделку в `ERROR`;
* ввести отдельный `DealFinalizationState`;
* требовать ручной разбор;
* добавить специальный operational flag, не нарушая terminal semantics.
