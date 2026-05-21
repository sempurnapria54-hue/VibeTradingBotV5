# FSM этапы сделки

> Статус документа: подробный регламент FSM handlers.
>
> Документ описывает, как каждый `Deal.Status` обрабатывается внутри `DealStateMachine`.
>
> Общая карта процесса вынесена в документ: `Жизненный цикл сделки`.
>
> Модель стратегии вынесена в документ: `Strategy.md`.
>
> Command-layer вынесен в документ: `Сервисные команды`.
>
> Расчёт индикаторов, структуры рынка, фазы и freshness-check вынесен в документ: `04. Расчёт индикаторов и рыночных данных`.
>
> Аудит и timeline сделки вынесены в документ: `05. Аудит и история исполнения`.

> Оценка риска и `RiskValidator / RiskBlockResolver` описаны в документе: `Оценка рисков`.

---

# Главная идея

FSM отвечает за сопровождение уже созданной сделки.

FSM не создаёт `Deal`.

FSM не ищет новые входы.

FSM не исполняет REST-запросы напрямую.

FSM не рассчитывает индикаторы, структуру рынка, фазу рынка, цену, размер и риск вручную.

FSM работает по:

* `DealContext`;
* pinned `StrategyDetail`;
* `DealActionState`;
* `Deal` runtime graph: `deal.orders`, `deal.algoOrders`, `deal.position`;
* `BalanceContainer` из `DealContext`;
* результатам refresh-команд;
* свежему `CalculationContext`, который собирается внутри `StrategyActionCalculator` только для конкретного action.

Главное правило:

> FSM решает, какой этап сделки сейчас актуален, какие strategy steps можно проверить, какие service commands нужно создать и можно ли перейти в следующий `Deal.Status`.

# Архитектурные инварианты FSM

Эта часть фиксирует текущие правила работы FSM, а не историю обсуждения.

* `Strategy.INACTIVE` не меняет сопровождение уже открытой сделки: FSM продолжает работать по pinned `StrategyDetail`.
* `Strategy.DELETED` приводит уже открытые сделки к graceful shutdown, если это безопасно.
* Устаревание рыночных данных не меняет `Strategy.Status`. Handler проверяет свежесть данных через `MarketDataExpirationChecker.checkForStep(dealContext, step)`.
* `Deal.entryReason` и `Deal.entryStepType` не управляют FSM-переходами.
* `PROTECTION_SWITCHED` используется только если реально выполняется замена temporary attached protection на standalone main protection.
* Если switch не нужен, допустим переход `ENTRY_FINALIZED -> MANAGING`, но только если позиция безопасна для сопровождения.
* `CLOSED` и `EMERGENCY_CLOSED` — terminal-статусы. Для них не создаются FSM handlers.
* Финальную проверку штатного закрытия выполняет `ExitPendingHandler` перед переходом `EXIT_PENDING -> CLOSED`.
* Финальную проверку аварийного закрытия выполняет `ErrorHandler` перед переходом `ERROR -> EMERGENCY_CLOSED`.
* `ERROR -> CLOSED` запрещён.
* В `ERROR` не выполняются обычные strategy steps. Разрешены только safety / recovery / refresh / kill-switch действия.
* После restart FSM не ищет pending `ServiceCommand`. Она пересобирает `DealContext`, смотрит `Deal` runtime graph, `DealActionState`, `BalanceContainer` и exchange facts.
* `DealActionStateStatus.COMPLETED` ставится только после refresh/search/history facts.
* `ACKED` и `CONFIRMED` не являются runtime-статусами action.
* `CLOSE_POSITION` используется только для полного закрытия позиции.
* Direct partial close позиции запрещён. Partial exit выполняется через reduce-only `Order` / `AlgoOrder` actions.
* FSM напрямую не создаёт и не обновляет `Position`. Локальная `Position` создаётся и обновляется только через `REFRESH_POSITION`.
* Live risk по позиции считается вычисляемо: `Position.status == ACTIVE && Position.externalSize > 0`.
* `Position.status == ACTIVE && externalSize == 0` означает, что биржа всё ещё возвращает position record, но live market risk по размеру позиции отсутствует. Это cleanup / anomaly / retry case, а не normal `CLOSED`.
* После `CLOSE_POSITION` факт закрытия позиции подтверждается через `REFRESH_POSITION`.
* `REFRESH_FILLS` используется в финализации сделки для итогового подсчёта profit/loss.
* `RiskValidator` не вызывается для exit / risk-reducing / cleanup / safety-flow: reduce-only partial exit, `CANCEL_ORDER`, `CANCEL_ALGO_ORDER`, `CLOSE_POSITION`, `EXECUTE_KILL_SWITCH`.
* Для reduce-only partial exit handler не вызывает RiskValidator, а проверяет только safety/invariant условия: reduce-only intent, размер, направление уменьшения позиции, принадлежность к текущей Deal и актуальные exchange facts.
* Для cleanup / safety commands (`CANCEL_*`, `CLOSE_POSITION`, `EXECUTE_KILL_SWITCH`) RiskValidator не вызывается; handler выполняет minimal domain / exchange safety checks по refresh/search/history facts.
* `BalanceContainer` в `DealContext` — последняя persisted версия account snapshot, а не гарантия свежести.
* `BalanceContainer` не имеет собственного `Status`; stale определяется через freshness-check.
* `REFRESH_BALANCE` — единственный runtime-flow обновления `BalanceContainer`.
* FSM / handler обязан обеспечить fresh `BalanceContainer` при старте обработки сделки / `PRECHECK`, перед risk-sensitive action, при финализации выхода и при emergency/safety finalization.
* Если balance absent/stale перед risk-check, handler создаёт `REFRESH_BALANCE` и не вызывает `RiskValidator` на этой итерации.
* `REFRESH_BALANCE` после выхода не участвует в расчёте `Deal.resultProfit`; итоговый profit/loss считается через `REFRESH_FILLS`.

---

# 1. Три типа проверок FSM handler

У каждого FSM handler есть три логических блока.

## 1.1. Входные проверки

Входные проверки отвечают на вопрос:

> Можно ли вообще обрабатывать сделку в этом FSM-статусе?

Они выполняются в начале handler'а.

Они проверяют, что текущий `Deal.status` не противоречит фактам из `DealContext`.

Примеры:

* есть pinned `StrategyDetail`;
* есть нужный `DealActionState`;
* есть локальный entry order, если статус уже `ENTRY_SUBMITTED`;
* позиция не нарушает инвариант “не более одной позиции на инструмент”;
* нет чужого активного риска;
* локальные сущности не находятся в невозможных статусах.

Если входная проверка не прошла, handler может:

* остаться в текущем статусе и создать refresh-команды;
* сделать безопасный recovery;
* перевести сделку в `ERROR`;
* инициировать `EXECUTE_KILL_SWITCH`, если риск опасен.

В нормальном happy-path входные проверки не переводят сделку на следующий бизнес-этап.

Исключение — safe forward recovery после рестарта, если факты показывают, что сделка уже фактически ушла дальше.

## 1.2. Рабочая логика этапа

Рабочая логика отвечает на вопрос:

> Что нужно сделать сейчас, чтобы приблизиться к завершению этапа?

Рабочая логика может:

* создавать refresh-команды;
* создавать `CREATE_*` / `SUBMIT_*` / `AMEND_*` / `CANCEL_*` команды;
* создавать risk-reducing / cleanup / safety-команды без `RiskValidator`, если нужно снять или локализовать риск;
* проверять `StrategyCondition`;
* выбирать `StrategyAction`;
* вызывать `StrategyActionCalculator`;
* создавать команды через `ServiceCommandFactory`.

Рабочая логика сама по себе не означает, что этап завершён.

Например:

```text
ENTRY_SUBMITTED
  -> REFRESH_ORDER
  -> REFRESH_POSITION
  -> REFRESH_FILLS
```

Это рабочая логика.

Переход в `ENTRY_FINALIZED` произойдёт только после выходных проверок.

## 1.3. Выходные проверки

Выходные проверки отвечают на вопрос:

> Можно ли считать этот FSM-этап завершённым?

Именно выходные проверки отвечают за обычный переход между этапами.

Пример для `ENTRY_SUBMITTED`:

```text
entry Order финализирован
позиция открыта
позиция соответствует сделке / инструменту / направлению
attached protection не потеряна, если она была нужна
нет критичных аномалий
```

Если всё выполнено:

```text
ENTRY_SUBMITTED -> ENTRY_FINALIZED
```

Если не выполнено:

```text
остаёмся в ENTRY_SUBMITTED
и продолжаем refresh / retry / recovery
```

Если найдено опасное противоречие:

```text
ENTRY_SUBMITTED -> ERROR
```

---

# 2. Где проявляется влияние стратегии в FSM

Стратегия не переводит сделку из одного `Deal.Status` в другой напрямую.

Влияние стратегии проявляется в трёх местах:

```text
1. Какие условия проверять.
2. Какие actions выполнить.
3. Как рассчитать параметры команд.
```

## 2.1. StrategyDetail определяет допустимые steps для статуса

FSM handler берёт pinned `StrategyDetail` из `DealContext` и смотрит:

```text
StrategyDetail.stepsByStatus[Deal.status]
```

Пример:

```text
Deal.status = MANAGING

StrategyDetail.stepsByStatus[MANAGING]:
  - PROTECTION_ADJUSTMENT
  - PARTIAL_EXIT
  - GRID_MANAGEMENT
  - EXIT
  - FAIL_SAFE
```

Стратегия говорит:

> В этом статусе сделки вот какие шаги вообще могут быть применены.

## 2.2. Freshness-check выполняется перед StrategyCondition

Перед проверкой `StrategyCondition` для data-dependent step handler проверяет свежесть данных:

```text
handler
  -> берёт steps для текущего Deal.Status
  -> для каждого StrategyStep вызывает MarketDataExpirationChecker.checkForStep(dealContext, step)
  -> если данные свежие, проверяет StrategyCondition
  -> если данные устарели или отсутствуют, применяет StrategyStep.marketDataExpiredSetting
```

`MarketDataExpirationChecker` описан в документе `04. Расчёт индикаторов и рыночных данных`.

## 2.3. StrategyCondition решает, применим ли step

FSM handler не должен сам знать, когда переносить SL, делать partial exit или закрывать позицию по правилу стратегии.

Он делает так:

```text
handler
  -> берёт steps для текущего Deal.Status
  -> проверяет freshness нужных данных step
  -> для каждого fresh StrategyStep проверяет StrategyCondition
  -> если condition true, step применим
```

## 2.4. StrategyAction говорит, что именно сделать

После того как `StrategyCondition` прошла, FSM берёт:

```text
StrategyStep.actions
```

Например:

```text
StrategyAlgoOrderAction
  actionType = AMEND
  targetActionKey = main-stop-loss
  stopLossSettings = ATR / structure / entry percent
```

FSM сама не двигает SL.

FSM создаёт runtime-команды через калькулятор и command-layer.

## 2.5. StrategyActionCalculator применяет настройки стратегии

Стратегия влияет на расчёт через:

* `StrategyOrderAction.placement`;
* `StrategyOrderAction.allocationPercents`;
* `StrategyAlgoOrderAction.stopLossSettings`;
* `StrategyAlgoOrderAction.trailingSettings`;
* reduce-only `StrategyOrderAction` / `StrategyAlgoOrderAction` для partial exit;
* `StrategyDetail.riskPerTradePercent`;
* `StrategyDetail.maxLeverage`.

FSM вызывает:

```text
StrategyActionCalculator
  -> PriceCalculator
  -> SizeCalculator
  -> RiskCalculator
```

И только после этого создаётся `ServiceCommand`.

## 2.6. Что стратегия не делает

Стратегия не решает напрямую:

```text
Deal.status = ENTRY_FINALIZED
Deal.status = MANAGING
Deal.status = CLOSED
```

Переходы делают выходные проверки FSM по фактам.

---

# 3. Общие источники информации FSM

FSM handler не должен напрямую ходить на биржу.

FSM handler не должен сам считать индикаторы, структуру рынка или цены.

Он использует:

* `DealContext` — runtime-контекст сделки;
* pinned `StrategyDetail` — правила сделки;
* `DealActionState` — runtime-состояние strategy actions;
* `Deal` runtime graph: `deal.orders`, `deal.algoOrders`, `deal.position`;
* `BalanceContainer` из `DealContext`;
* результаты refresh-команд;
* `StrategyActionCalculator`, если нужно рассчитать параметры нового action.

Свежие рыночные данные не кладём в `DealContext` заранее.

Для расчёта конкретного действия используется свежий `CalculationContext`, который собирается внутри `StrategyActionCalculator`.


## 3.1. Balance freshness как precondition

`BalanceContainer` в `DealContext` используется как account-state snapshot для sizing и risk-policy, но сам факт его наличия не означает свежесть.

Правила:

```text
risk-sensitive flow
  -> проверить BalanceContainer freshness
  -> если absent/stale: создать REFRESH_BALANCE и остановить обработку action на этой итерации
  -> если fresh: разрешено собирать CalculationContext / вызывать RiskValidator
```

`RiskValidator` дополнительно защищается: если ему передали absent/stale/invalid `BalanceContainer`, он возвращает `BLOCKED` с кодом `BALANCE_NOT_FRESH` / `BALANCE_INVALID`.

---

# 4. PRECHECK

## 4.1. Назначение

`PRECHECK` готовит сделку к созданию entry order.

Сделка уже создана `DealOpeningService`, но runtime-сущности входа ещё не должны считаться подтверждёнными.

## 4.2. Источники информации

* `Deal`;
* pinned `StrategyDetail`;
* `StrategyStep` со `stepType = ENTRY` или `GRID_ENTRY`;
* `StrategyCondition` выбранного entry-step;
* `Instrument`;
* `BalanceContainer`;
* `Deal` runtime graph: `deal.position`, `deal.orders`, `deal.algoOrders`;
* актуальные instrument-level facts из refresh/search, если нужно проверить отсутствие чужой позиции или конфликтующих live-сущностей;
* `DealActionState`;
* свежий `CalculationContext`, если нужно рассчитать параметры entry action.

Через `CalculationContext` могут быть получены:

* `InstrumentExternalRules`;
* `MarketPriceData`;
* `IndicatorValue`;
* `MarketStructure`;
* `MarketPhase`;
* актуальный balance/risk snapshot, если перед входом нужен повторный risk-check.

## 4.3. Входные проверки

Проверяем:

* `Deal.status = PRECHECK`;
* есть pinned `StrategyDetail`;
* есть `Instrument`;
* есть `BalanceContainer` или можно создать `REFRESH_BALANCE`;
* refresh/search facts не показывают больше одной позиции по инструменту;
* нет активной позиции, если вход предполагает новую позицию;
* нет активной сделки по инструменту, если одновременно разрешена только одна сделка;
* нет конфликтующих live orders;
* нет конфликтующих live algo-orders;
* нет признаков borrow / debt;
* торговый режим соответствует проекту;
* ожидается isolated margin.

Если входные проверки не проходят безопасно:

* создать refresh-команды;
* остаться в `PRECHECK`;
* или перейти в `ERROR`, если обнаружен опасный риск.

## 4.4. Рабочая логика этапа

Перед созданием entry order handler обязан обеспечить fresh `BalanceContainer`:

```text
если BalanceContainer absent/stale
  -> создать REFRESH_BALANCE
  -> остаться в PRECHECK
  -> не вызывать RiskValidator и не создавать CREATE_ORDER на этой итерации
```

1. Найти `StrategyStep` со `stepType = ENTRY` или `GRID_ENTRY`.
2. Проверить свежесть данных step через `MarketDataExpirationChecker.checkForStep(dealContext, step)`.
3. Если данные устарели или отсутствуют — применить `StrategyStep.marketDataExpiredSetting`.
4. Если данные свежие — проверить `StrategyCondition` выбранного step.
5. Если condition не выполнен — проверить отсутствие live risk.
6. Если live risk отсутствует — закрыть candidate Deal без ошибки: `Deal.status = CLOSED`, `Deal.closeReason = ENTRY_CONDITION_EXPIRED`.
7. Если live risk уже создан или состояние неизвестно — перейти в recovery / safety flow.
8. Если condition выполнен — взять `StrategyAction` из step.
7. Для каждого action проверить `DealActionState`.
8. Если action ещё не материализован — вызвать `StrategyActionCalculator`.
9. Создать `CREATE_ORDER` через `ServiceCommandFactory`.
10. После создания локального order создать или запланировать `SUBMIT_ORDER`.
11. Если action уже был создан до рестарта — продолжить с refresh/submit по `DealActionState`.

## 4.5. Выходные проверки

Этап можно считать завершённым, если:

* entry action материализован в локальный `Order`;
* `DealActionState` по entry action указывает на `RuntimeTarget(ORDER, orderId)`;
* order создан локально и передан на отправку или уже отправлен;
* нет критичных конфликтов по позиции / ордерам / algo-orders;
* нет риска, требующего kill-switch.

## 4.6. Переходы

Обычные переходы:

```text
PRECHECK -> ENTRY_SUBMITTED
PRECHECK -> CLOSED, если entry condition стал false и live risk ещё не создан
```

Для раннего закрытия candidate Deal используется:

```text
Deal.closeReason = ENTRY_CONDITION_EXPIRED
```

Аварийные переходы:

```text
PRECHECK -> ERROR
```

Recovery-переходы:

```text
PRECHECK -> ENTRY_SUBMITTED
PRECHECK -> ENTRY_FINALIZED
```

Recovery-переход допустим только если факты показывают, что order уже был создан/отправлен или позиция уже открыта, и продолжать с более раннего этапа небезопасно или бессмысленно.

## 4.7. Допустимые StrategyStep

```text
ENTRY
GRID_ENTRY
FAIL_SAFE
```

## 4.8. Возможные ServiceCommand

```text
REFRESH_BALANCE
REFRESH_POSITION
REFRESH_PENDING_ORDERS
CREATE_ORDER
SUBMIT_ORDER
MARK_DEAL_ERROR
EXECUTE_KILL_SWITCH
```

---

# 5. ENTRY_SUBMITTED

## 5.1. Назначение

`ENTRY_SUBMITTED` подтверждает, что entry order отправлен, и определяет, появилась ли позиция.

## 5.2. Источники информации

* `Deal`;
* pinned `StrategyDetail`;
* `Instrument`;
* локальный entry `Order`;
* attached protection внутри entry `Order`, если она должна была быть создана;
* `Deal` runtime graph: `deal.position`, `deal.orders`, `deal.algoOrders`;
* `DealActionState` по entry action;
* результат `REFRESH_ORDER`;
* результат `REFRESH_PENDING_ORDERS`;
* результат `REFRESH_POSITION`;
* результат `REFRESH_FILLS`;
* результат `REFRESH_ORDER_HISTORY`, если order уже не найден среди pending.

## 5.3. Входные проверки

Проверяем:

* `Deal.status = ENTRY_SUBMITTED`;
* есть pinned `StrategyDetail`;
* есть `DealActionState` для entry action;
* есть локальный entry `Order`;
* entry `Order` относится к этой сделке;
* entry `Order` не в невозможном статусе;
* по инструменту нет больше одной позиции;
* нет чужого активного риска;
* если attached protection ожидалась, она присутствует в entry `Order` или есть понятный recovery-путь.

## 5.4. Рабочая логика этапа

1. Если entry order создан локально, но не отправлен — создать `SUBMIT_ORDER`.
2. Если entry order отправлен, но состояние не подтверждено — создать `REFRESH_ORDER`.
3. Если order не найден среди pending — создать `REFRESH_ORDER_HISTORY`.
4. Если order мог исполниться — создать `REFRESH_POSITION`.
5. Если нужна фактическая цена исполнения или итоговые факты исполнения — создать `REFRESH_FILLS`.
6. Если результат отправки неизвестен — перед повторным `SUBMIT_ORDER` сначала искать order по client order id.
7. Если order исполнен частично — определить, достаточно ли этого для появления позиции и продолжения этапа.
8. Если `REFRESH_POSITION` нашёл позицию, а локальной `Position` ещё нет — `RefreshPositionExecutor` создаёт `Position` и привязывает её к `Deal`.
9. Если entry order исполнился, но после рестарта `REFRESH_POSITION` уже не находит позицию, handler должен добрать order/algo/fills/history facts и перейти в `EXIT_PENDING`, если факты объясняют закрытие позиции по SL / TP / trailing.
10. Если факты противоречивы — перейти в recovery или `ERROR`.

### 5.4.1. Missing attached protection

Если attached protection ожидалась, но после `REFRESH_ORDER` не найдена внутри `OrderExternalSnapshot.attachedAlgoOrders` по `internalId`, отсутствие в одном snapshot не считается финальным фактом.

Базовая политика зависит от состояния parent `Order` и runtime facts:

```text
parent Order CREATED / PENDING
  -> attached остаётся PENDING, ждём следующий refresh / retry / recovery

parent Order ACTIVE / PARTIALLY_COMPLETED
  -> запускаем дополнительный search-cycle

parent Order COMPLETED
  -> проверяем позицию и standalone main protection
  -> если позиция active и standalone protection отсутствует, Deal -> ERROR

parent Order CANCELED
  -> attached -> CANCELED, closeReason = PARENT_ORDER_CANCELED

parent Order ERROR
  -> attached -> ERROR, closeReason = UNKNOWN
```

Подробная модель `Order` и `AttachedAlgoOrder` описана в документе `Order.md`.

## 5.5. Выходные проверки

Этап можно считать завершённым как успешный вход, если:

* entry order финализирован;
* позиция открыта и материализована через `REFRESH_POSITION`;
* позиция соответствует сделке, инструменту и направлению;
* если attached protection была нужна — она подтверждена или не потеряна;
* нет конфликтующих active orders;
* нет критичных аномалий.

Если после рестарта entry order исполнен, но позиция уже закрылась на стороне биржи по SL / TP / trailing, это не является anomaly при наличии active `Deal` и known entry order. Такой кейс переводится в `EXIT_PENDING` для добора history/fills facts и финализации сделки.

Именно эти выходные проверки отвечают за переход:

```text
ENTRY_SUBMITTED -> ENTRY_FINALIZED
```

## 5.6. Переходы

Обычный переход:

```text
ENTRY_SUBMITTED -> ENTRY_FINALIZED
```

Аварийный переход:

```text
ENTRY_SUBMITTED -> ERROR
```

Recovery-переходы:

```text
ENTRY_SUBMITTED -> ENTRY_FINALIZED
ENTRY_SUBMITTED -> PROTECTION_SWITCHED
ENTRY_SUBMITTED -> EXIT_PENDING
```

Recovery-переход дальше `ENTRY_FINALIZED` допустим, если после рестарта уже есть позиция и активная основная защита.

Recovery-переход в `EXIT_PENDING` допустим, если entry order исполнился, позиция успела появиться и закрыться на бирже, а history/fills/protection facts объясняют закрытие.

## 5.7. Допустимые StrategyStep

```text
FAIL_SAFE
```

Обычно новые торговые strategy actions на этом этапе не выбираются, потому что этап занимается подтверждением уже созданного входа.

## 5.8. Возможные ServiceCommand

```text
SUBMIT_ORDER
REFRESH_ORDER
REFRESH_PENDING_ORDERS
REFRESH_POSITION
REFRESH_FILLS
REFRESH_BALANCE
REFRESH_ORDER_HISTORY
REFRESH_ALGO_ORDER_HISTORY
FINALIZE_DEAL_ENTRY
MARK_DEAL_ERROR
EXECUTE_KILL_SWITCH
```

---

# 6. ENTRY_FINALIZED

## 6.1. Назначение

`ENTRY_FINALIZED` подтверждает, что вход завершён, позиция открыта, и определяет следующий безопасный путь сопровождения.

На этом этапе стратегия может требовать создать или подтвердить standalone main protection.

Но не каждая стратегия обязана заменять temporary attached protection на standalone main protection.

Поэтому `ENTRY_FINALIZED` не всегда ведёт в `PROTECTION_SWITCHED`.

Допустимые варианты:

```text
ENTRY_FINALIZED -> PROTECTION_SWITCHED -> MANAGING
ENTRY_FINALIZED -> MANAGING
ENTRY_FINALIZED -> ERROR
```

## 6.2. Источники информации

* `Deal`;
* pinned `StrategyDetail`;
* `StrategyStep` со `stepType = MAIN_PROTECTION`, если такой step есть;
* `StrategyCondition` protection-step;
* `Instrument`;
* `Deal` runtime graph: active `deal.position`, entry `Order`, attached protection внутри entry `Order`, `deal.algoOrders`;
* `DealActionState` по protection actions;
* результат refresh-команд по position/order/algo-orders;
* свежий `CalculationContext`, если нужно рассчитать SL / TP / OCO / trailing.

Через `CalculationContext` могут быть получены:

* `InstrumentExternalRules`;
* `MarketPriceData`;
* `IndicatorValue`, например ATR;
* `MarketStructure`, если защита считается от swing/range/support/resistance;
* `MarketPhase`, если защита зависит от текущей фазы;
* актуальный balance/risk snapshot.

Подробности по freshness и `CalculationContext` см. в документах:

```text
04. Расчёт индикаторов и рыночных данных
03. Калькуляторы действий стратегии
```

## 6.3. Входные проверки

Проверяем:

* `Deal.status = ENTRY_FINALIZED`;
* есть pinned `StrategyDetail`;
* позиция активна;
* позиция соответствует сделке, инструменту и направлению;
* entry order финализирован или есть достаточные факты исполнения;
* известна фактическая цена входа или есть путь получить её через `REFRESH_FILLS`;
* нет больше одной позиции по инструменту;
* нет критичного риска без возможности защиты;
* если стратегия стала `DELETED`, нужно идти в graceful shutdown, а не применять обычные data-dependent steps.

## 6.4. Рабочая логика этапа

1. Определить по pinned `StrategyDetail`, нужен ли фактический protection switch.
2. Если есть `StrategyStep` со `stepType = MAIN_PROTECTION`, проверить свежесть данных step через `MarketDataExpirationChecker.checkForStep(dealContext, step)`.
3. Если данные устарели или отсутствуют — применить `StrategyStep.marketDataExpiredSetting`.
4. Если данные свежие — проверить `StrategyCondition` protection-step.
5. Если condition выполнен — взять protection actions.
6. Для каждого action проверить `DealActionState`.
7. Если основной protection action ещё не материализован — вызвать `StrategyActionCalculator`.
8. Создать `CREATE_ALGO_ORDER`.
9. Создать или запланировать `SUBMIT_ALGO_ORDER`.
10. Создать refresh-команды для подтверждения active protection.
11. Если нужно снять attached protection после main protection — создать cancel-команду только после подтверждения main protection.
12. Если protection switch не нужен, проверить безопасное состояние позиции для перехода в `MANAGING`.

## 6.5. Выходные проверки

Этап можно считать завершённым, если:

* позиция активна;
* entry order финализирован;
* требования strategy / risk policy по защите выполнены;
* если strategy / risk policy требует защиту — активная защита подтверждена;
* если защита не обязательна — это явно разрешено strategy / risk policy;
* нет дублирующей конфликтующей защиты;
* нет orphan algo-orders;
* нет риска, требующего kill-switch.

Обычные переходы:

```text
ENTRY_FINALIZED -> PROTECTION_SWITCHED
ENTRY_FINALIZED -> MANAGING
```

`ENTRY_FINALIZED -> PROTECTION_SWITCHED` используется только если фактически нужен protection switch:

```text
temporary attached protection
  -> standalone main protection подтверждена active
  -> attached protection можно снять или уже снята
```

`ENTRY_FINALIZED -> MANAGING` разрешён, если switch не нужен и позиция безопасна для сопровождения.

## 6.6. Переходы

Обычные переходы:

```text
ENTRY_FINALIZED -> PROTECTION_SWITCHED
ENTRY_FINALIZED -> MANAGING
```

Аварийный переход:

```text
ENTRY_FINALIZED -> ERROR
```

Recovery-переходы:

```text
ENTRY_FINALIZED -> PROTECTION_SWITCHED
ENTRY_FINALIZED -> MANAGING
```

Recovery допустим, если после рестарта facts однозначно подтверждают более поздний безопасный этап.

## 6.7. Допустимые StrategyStep

```text
MAIN_PROTECTION
FAIL_SAFE
```

Если protection switch не нужен, `MAIN_PROTECTION` может отсутствовать или быть неприменимым для данной стратегии.

## 6.8. Возможные ServiceCommand

```text
REFRESH_BALANCE
CREATE_ALGO_ORDER
SUBMIT_ALGO_ORDER
REFRESH_ALGO_ORDER
REFRESH_ALGO_ORDERS
CANCEL_ALGO_ORDER
CANCEL_ORDER
REFRESH_POSITION
MARK_DEAL_ERROR
EXECUTE_KILL_SWITCH
```

Новые `ServiceCommandType` для Q4 не требуются.

# 7. PROTECTION_SWITCHED

## 7.1. Назначение

`PROTECTION_SWITCHED` подтверждает не просто факт наличия защиты.

Он подтверждает конкретный switch-сценарий:

```text
temporary attached protection
  -> standalone main protection подтверждена active
  -> temporary attached protection снята или больше не влияет на риск
```

Этот статус не является обязательным для всех сделок.

Если strategy steps не требуют замены temporary attached protection на standalone main protection, FSM не должна искусственно переводить сделку в `PROTECTION_SWITCHED`.

## 7.2. Источники информации

* `Deal`;
* pinned `StrategyDetail`;
* `Instrument`;
* `Deal` runtime graph: active `deal.position`, entry `Order`, attached protection внутри `Order`, standalone protective `AlgoOrder`, `deal.orders`, `deal.algoOrders`;
* `DealActionState` по attached/protection actions;
* результат `REFRESH_POSITION`;
* результат `REFRESH_ALGO_ORDERS`;
* результат `REFRESH_PENDING_ORDERS`;
* результат cancel-команд, если снимали attached/pending protection.

## 7.3. Входные проверки

Проверяем:

* `Deal.status = PROTECTION_SWITCHED`;
* позиция активна;
* main protection существует локально;
* main protection имеет связь через `DealActionState`;
* по инструменту нет больше одной позиции;
* нет критичного расхождения БД и биржи;
* этот статус действительно применим для текущего protection-switch flow.

Если после рестарта выяснилось, что switch не нужен, а позиция безопасна, допустим safe forward recovery в `MANAGING`.

## 7.4. Рабочая логика этапа

1. Создать `REFRESH_POSITION`, если позиция давно не обновлялась.
2. Создать `REFRESH_ALGO_ORDERS`, чтобы подтвердить active main protection.
3. Проверить, осталась ли attached protection.
4. Если attached protection ещё активна и main protection уже подтверждена — создать cancel-команду.
5. Проверить pending orders, которые могут конфликтовать с защитой.
6. Если есть конфликт — cancel или `ERROR` в зависимости от риска.

## 7.5. Выходные проверки

Этап можно считать завершённым, если:

* позиция активна;
* main protection активна;
* attached protection снята или больше не влияет на риск;
* нет дублирующей защиты;
* нет orphan algo-orders;
* нет pending orders, которые конфликтуют с текущей защитой;
* сделка готова к обычному сопровождению.

Именно эти выходные проверки отвечают за переход:

```text
PROTECTION_SWITCHED -> MANAGING
```

## 7.6. Переходы

Обычный переход:

```text
PROTECTION_SWITCHED -> MANAGING
```

Аварийный переход:

```text
PROTECTION_SWITCHED -> ERROR
```

Recovery-переход:

```text
PROTECTION_SWITCHED -> MANAGING
```

## 7.7. Допустимые StrategyStep

```text
FAIL_SAFE
```

Обычно этот этап технический: он не создаёт новую торговую логику, а подтверждает безопасное переключение защиты.

## 7.8. Возможные ServiceCommand

```text
REFRESH_POSITION
REFRESH_ALGO_ORDERS
REFRESH_PENDING_ORDERS
CANCEL_ALGO_ORDER
CANCEL_ORDER
MARK_DEAL_ERROR
EXECUTE_KILL_SWITCH
```

# 8. MANAGING

## 8.1. Назначение

`MANAGING` сопровождает открытую позицию по стратегии.

Это основной рабочий статус сделки после входа и защиты.

## 8.2. Источники информации

* `Deal`;
* pinned `StrategyDetail`;
* `StrategyStep` со step types:

  * `PROTECTION_ADJUSTMENT`;
  * `PARTIAL_EXIT`;
  * `GRID_MANAGEMENT`;
  * `EXIT`;
  * `FAIL_SAFE`;
* `StrategyCondition` выбранных managing-steps;
* `Instrument`;
* `Deal` runtime graph: active `deal.position`, `deal.orders`, `deal.algoOrders`;
* `DealActionState`;
* `BalanceContainer`;
* свежий `CalculationContext`, если нужно рассчитать параметры action.

Через `CalculationContext` могут быть получены:

* `InstrumentExternalRules`;
* `MarketPriceData`;
* `IndicatorValue`, например ATR / RSI / EMA / Bollinger Bands;
* `MarketStructure`, например RANGE_LOW / RANGE_HIGH / SWING_LOW / SWING_HIGH;
* `MarketPhase`;
* актуальный risk/balance snapshot.

## 8.3. Входные проверки

Проверяем:

* `Deal.status = MANAGING`;
* есть pinned `StrategyDetail`;
* позиция активна с live risk или есть факты, что позиция уже закрыта и нужен переход в `EXIT_PENDING`;
* если `Position.status == ACTIVE && externalSize == 0`, handler не считает это normal `CLOSED`, а переводит кейс в cleanup / retry / anomaly-разбор по контексту;
* main protection существует и актуальна;
* по инструменту нет больше одной позиции;
* нет чужих live orders/algo-orders;
* нет критичного расхождения БД и биржи;
* нет признаков borrow / debt.

## 8.4. Рабочая логика этапа

1. Обновить позицию и live-сущности, если это нужно для актуальности.
2. Взять из `StrategyDetail.stepsByStatus[MANAGING]` допустимые steps.
3. Для каждого data-dependent step проверить `MarketDataExpirationChecker.checkForStep(dealContext, step)`.
4. Если данные устарели или отсутствуют — применить `StrategyStep.marketDataExpiredSetting`.
5. Если данные свежие — проверить conditions для:

* `PROTECTION_ADJUSTMENT`;
* `PARTIAL_EXIT`;
* `GRID_MANAGEMENT`;
* `EXIT`;
* `FAIL_SAFE`.
6. Для каждого применимого step взять actions.
7. Для actions, которые создают или меняют сущности, проверить `DealActionState`.
8. Для нового или изменяемого action вызвать `StrategyActionCalculator`.
9. Создать нужные `ServiceCommand`.
10. Если condition требует полного выхода — создать `CLOSE_POSITION` или соответствующие cancel/close-команды.
11. Если `REFRESH_POSITION` показывает, что позиции больше нет, перейти в `EXIT_PENDING` для cleanup и финализации.
12. Если `REFRESH_POSITION` показывает `ACTIVE` с `externalSize == 0`, выполнить cleanup / retry / anomaly-разбор по контексту.
13. Если risk/fail-safe condition сработал — перейти к emergency flow.

## 8.5. Выходные проверки

Переход в `EXIT_PENDING` разрешён, если:

* стратегия инициировала выход;
* позиция закрывается или уже закрыта;
* создана команда закрытия позиции или есть факт закрытия через `REFRESH_POSITION`;
* нужно дочистить orders/algo-orders/fills/history.

Переход в `ERROR` нужен, если:

* позиция есть, но защита потеряна и не может быть безопасно восстановлена;
* есть активный риск без контроля;
* обнаружено опасное расхождение БД и биржи;
* есть больше одной позиции по инструменту;
* есть признаки borrow / debt;
* обычный recovery небезопасен.

Если ни одно условие выхода не выполнено, сделка остаётся в `MANAGING`.

## 8.6. Переходы

Обычные переходы:

```text
MANAGING -> MANAGING
MANAGING -> EXIT_PENDING
```

Аварийный переход:

```text
MANAGING -> ERROR
```

Recovery-переходы:

```text
MANAGING -> EXIT_PENDING
```

Recovery в `EXIT_PENDING` допустим, если после рестарта факты показывают, что позиция закрыта или закрывается, live risk по позиции отсутствует, но сделку ещё нужно дочистить и финализировать.

## 8.7. Допустимые StrategyStep

```text
PROTECTION_ADJUSTMENT
PARTIAL_EXIT
GRID_MANAGEMENT
EXIT
FAIL_SAFE
```

## 8.8. Возможные ServiceCommand

```text
REFRESH_BALANCE
AMEND_ALGO_ORDER
CREATE_ALGO_ORDER
SUBMIT_ALGO_ORDER
CANCEL_ALGO_ORDER
CREATE_ORDER
SUBMIT_ORDER
AMEND_ORDER
CANCEL_ORDER
CLOSE_POSITION
REFRESH_POSITION
REFRESH_PENDING_ORDERS
REFRESH_ALGO_ORDERS
REFRESH_FILLS
MARK_DEAL_ERROR
EXECUTE_KILL_SWITCH
```

---

# 9. EXIT_PENDING

## 9.1. Назначение

`EXIT_PENDING` дочищает сделку после инициированного выхода.

Это этап подтверждения закрытия позиции, отмены live-сущностей и финализации фактов сделки.

## 9.2. Источники информации

* `Deal`;
* pinned `StrategyDetail`;
* `Instrument`;
* `Deal` runtime graph: последняя известная `deal.position`, `deal.orders`, `deal.algoOrders`;
* `DealActionState`;
* результат `REFRESH_POSITION`;
* результат `REFRESH_PENDING_ORDERS`;
* результат `REFRESH_ALGO_ORDERS`;
* результат `REFRESH_FILLS`;
* результат `REFRESH_ORDER_HISTORY`;
* результат `REFRESH_ALGO_ORDER_HISTORY`;
* результат cancel-команд;
* результат `FINALIZE_DEAL_EXIT`.

## 9.3. Входные проверки

Проверяем:

* `Deal.status = EXIT_PENDING`;
* есть pinned `StrategyDetail`;
* есть факты инициированного выхода или закрытия позиции;
* нет больше одной позиции по инструменту;
* можно безопасно проверить live risk;
* локальные orders/algo-orders доступны для очистки.

## 9.4. Рабочая логика этапа

1. Создать `REFRESH_POSITION`, чтобы подтвердить отсутствие live-risk позиции.
2. Создать `REFRESH_PENDING_ORDERS`.
3. Создать `REFRESH_ALGO_ORDERS`.
4. Если есть live ordinary orders — создать `CANCEL_ORDER`.
5. Если есть live algo-orders — создать `CANCEL_ALGO_ORDER`.
6. Создать `REFRESH_FILLS`, если нужны факты исполнений для итогового profit/loss.
7. Создать `REFRESH_BALANCE` после снятия live risk и загрузки/сопоставления fills, чтобы обновить account snapshot.
8. Если нужна история ordinary orders — создать `REFRESH_ORDER_HISTORY`.
9. Если нужна история algo-orders — создать `REFRESH_ALGO_ORDER_HISTORY`.
10. Определить или подтвердить `Deal.CloseReason`.
11. Создать `FINALIZE_DEAL_EXIT`, если факты готовы.
12. Создать `MARK_DEAL_CLOSED`, если всё очищено.

## 9.5. Выходные проверки

Этап можно считать завершённым, если:

* `REFRESH_POSITION` подтвердил, что позиции на бирже нет, или локальная `Position` отсутствовала и собранные entry/exit facts доказывают отсутствие live risk;
* active position в домене переведена в `CLOSED`, если она была материализована;
* нет активного рыночного риска;
* нет live ordinary orders;
* нет live algo-orders;
* fills загружены, если нужны для финализации profit/loss;
* balance snapshot обновлён через `REFRESH_BALANCE` после выхода;
* order history загружена, если нужна;
* algo-order history загружена, если нужна;
* причина закрытия определена;
* финальные локальные сущности согласованы.

Именно эти выходные проверки отвечают за переход:

```text
EXIT_PENDING -> CLOSED
```

Если риск не снят или состояние противоречивое:

```text
EXIT_PENDING -> ERROR
```

## 9.6. Переходы

Обычный переход:

```text
EXIT_PENDING -> CLOSED
```

Аварийный переход:

```text
EXIT_PENDING -> ERROR
```

Recovery-переход:

```text
EXIT_PENDING -> CLOSED
```

## 9.7. Допустимые StrategyStep

```text
FAIL_SAFE
```

Обычно торговые strategy steps уже не применяются: этап занимается подтверждением выхода и очисткой риска.

## 9.8. Возможные ServiceCommand

```text
REFRESH_POSITION
REFRESH_PENDING_ORDERS
REFRESH_ALGO_ORDERS
CANCEL_ORDER
CANCEL_ALGO_ORDER
REFRESH_FILLS
REFRESH_ORDER_HISTORY
REFRESH_ALGO_ORDER_HISTORY
FINALIZE_DEAL_EXIT
MARK_DEAL_CLOSED
MARK_DEAL_ERROR
EXECUTE_KILL_SWITCH
```

---

# 10. CLOSED

## 10.1. Назначение

`CLOSED` — нормальное терминальное состояние сделки.

В этом статусе сделка больше не сопровождается как активная.

## 10.2. Источники информации

* `Deal`;
* финальные `Order`;
* финальные `AlgoOrder`;
* финальная `Position`;
* fills;
* order history;
* algo-order history;
* история команд;
* история изменений сущностей.

## 10.3. Входные проверки

Проверяем:

* `Deal.status = CLOSED`;
* позиция закрыта;
* live risk отсутствует;
* live orders отсутствуют;
* live algo-orders отсутствуют.

Если после рестарта выяснилось, что в `CLOSED` есть активный риск, это уже аномалия и должен сработать anomaly/recovery flow.

## 10.4. Рабочая логика этапа

Обычные FSM-команды сопровождения не создаются.

Разрешены только:

* чтение;
* отчётность;
* аналитика;
* аудит;
* построение timeline сделки.

## 10.5. Выходные проверки

Нет обычных выходных проверок, потому что `CLOSED` — терминальный нормальный статус.

## 10.6. Переходы

Обычных переходов нет.

---

# 11. ERROR

## 11.1. Назначение

`ERROR` означает, что обнаружена авария и обычная strategy/FSM-логика заблокирована.

В этом статусе risk может быть ещё живым.

Разрешены только действия безопасности, восстановления и проверки фактов.

## 11.2. Источники информации

* `Deal`;
* `DealContext`;
* `Deal` runtime graph: `deal.position`, live ordinary orders, live algo-orders;
* exchange snapshots;
* refresh/search/history facts;
* результат kill-switch-команд.

## 11.3. Входные проверки

Проверяем:

* `Deal.status = ERROR`;
* есть ли активный риск;
* есть ли позиция без защиты;
* есть ли live orders/algo-orders без связи со сделкой;
* есть ли расхождение между БД и биржей;
* нужна ли аварийная очистка риска.

## 11.4. Рабочая логика этапа

1. Создать refresh-команды, если состояние неактуально.
2. Если есть активный риск — создать `EXECUTE_KILL_SWITCH` или конкретные safety-команды.
3. Если live ordinary orders есть — отменить их через `CANCEL_ORDER`.
4. Если live algo-orders есть — отменить их через `CANCEL_ALGO_ORDER`.
5. Если позиция открыта — закрыть её через `CLOSE_POSITION`.
6. После safety-flow заново загрузить exchange facts.
7. Если live risk отсутствует и это подтверждено — подготовить переход в `EMERGENCY_CLOSED`.

Обычные strategy steps в `ERROR` не выполняются.

## 11.5. Выходные проверки

`ErrorHandler` может перевести сделку:

```text
ERROR -> EMERGENCY_CLOSED
```

только если подтверждено:

* позиция закрыта или отсутствует;
* live ordinary orders отсутствуют;
* live algo-orders отсутствуют;
* attached protection отсутствует или больше не влияет на риск;
* нет pending сущностей, которые могут создать или увеличить риск;
* финальные exchange facts подтверждены;
* сделка больше не требует runtime-сопровождения FSM.

Если хотя бы один пункт не подтверждён, сделка остаётся в `ERROR`.

Запрещённый переход:

```text
ERROR -> CLOSED
```

## 11.6. Переходы

```text
ERROR -> ERROR
ERROR -> EMERGENCY_CLOSED
```

`EMERGENCY_CLOSED` — terminal-статус. Handler для него не нужен.

## 11.7. Допустимые StrategyStep

Обычная стратегия не применяется.

Допустим только safety-flow, если он оформлен как системная логика, а не как обычная торговая стратегия.

## 11.8. Возможные ServiceCommand

```text
EXECUTE_KILL_SWITCH
MARK_DEAL_ERROR
REFRESH_POSITION
REFRESH_PENDING_ORDERS
REFRESH_ALGO_ORDERS
REFRESH_ORDER_HISTORY
REFRESH_ALGO_ORDER_HISTORY
REFRESH_FILLS
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
```

---

# 12. Общие правила recovery

## 12.1. Recovery не равен обычному переходу

Обычный переход между этапами делает выходная проверка текущего FSM-статуса.

Recovery-переход после рестарта может произойти на входе в handler, если факты показывают, что сделка уже ушла дальше.

Пример 1:

```text
Deal.status = ENTRY_SUBMITTED

Факты:
  entry order completed
  position active
  main protection active

Безопасный recovery:
  ENTRY_SUBMITTED -> PROTECTION_SWITCHED
```

Пример 2:

```text
Deal.status = ENTRY_SUBMITTED

Факты:
  entry order completed
  position уже появилась на бирже
  position уже закрылась по SL / TP / trailing
  REFRESH_POSITION не находит позицию
  order/algo/fills/history объясняют закрытие

Безопасный recovery:
  ENTRY_SUBMITTED -> EXIT_PENDING
```

Это не обычный бизнес-переход.

Это forward recovery по фактам.

## 12.2. Recovery должен быть безопасным

Forward recovery разрешён только если:

* факты однозначны;
* переход не скрывает активный риск;
* локальные сущности можно синхронизировать;
* exchange state подтверждает более поздний этап;
* нет критичной аномалии.

Если факты неясны:

```text
refresh / retry / stay in current status
```

Если факты опасны:

```text
ERROR / EXECUTE_KILL_SWITCH
```

---

---

# 13. Дополнение после Q2–Q8: обработка risk/calculation/command-flow

Этот раздел добавлен после решений Q2–Q8.

Он не заменяет регламент handlers выше, а уточняет общий порядок выполнения strategy actions внутри handler.

## 13.1. Общий runtime-flow одного StrategyAction

Для торгового `StrategyAction`, который может создать или изменить runtime-риск, handler работает по схеме:

```text
1. Проверить входные инварианты handler.
2. Обновить или запросить нужные exchange facts, если состояние неактуально.
3. Выбрать StrategyStep из pinned StrategyDetail.
4. Проверить freshness step через MarketDataExpirationChecker.
5. Проверить StrategyCondition.
6. Выбрать текущий StrategyAction.
7. Проверить DealActionState по этому action.
8. Собрать свежий CalculationContext именно для этого action.
9. Выполнить StrategyActionCalculator.
10. Если action создаёт, увеличивает или ослабляет контроль риска — выполнить RiskValidator.
11. Если action относится к exit / cleanup / safety-flow — выполнить minimal domain / exchange safety checks и invariant checks.
12. Если risk result = BLOCKED — вызвать RiskBlockResolver.
13. Если action разрешён или minimal checks пройдены — создать одну актуальную ServiceCommand через ServiceCommandFactory.
14. Передать ServiceCommand в ServiceCommandExecutor.
15. После выполнения обновить facts / DealActionState / runtime-сущности.
16. Только потом переходить к следующему action или выходной проверке этапа.
```

Важно:

```text
RiskValidator вызывается после расчёта price/size,
но до создания risk-creating / risk-modifying ServiceCommand.
```

`RiskValidator` не вызывается перед refresh/read-only командами и перед risk-reducing / cleanup / safety commands.

Risk-creating / risk-modifying actions обычно материализуются через:

```text
CREATE_ORDER
AMEND_ORDER
CREATE_ALGO_ORDER
AMEND_ALGO_ORDER
```

Но если эти же команды используются для reduce-only partial exit, они не проходят RiskValidator и проверяются через minimal safety / invariant checks.

Risk-reducing / cleanup / safety commands:

```text
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
EXECUTE_KILL_SWITCH
```

Для risk-reducing / cleanup / safety commands handler выполняет minimal domain / exchange safety checks:

```text
сущность существует или есть понятный recovery-path;
сущность относится к текущей сделке / инструменту;
для CLOSE_POSITION закрывается вся позиция, а не часть;
есть нужные internal/external identifiers или можно выполнить search по stable client id;
если сущность уже terminal — handler верит refresh/search/history facts и не создаёт лишний cancel/close;
команда не противоречит known exchange facts.
```

## 13.2. Один action = один CalculationContext

Handler не должен собирать один `CalculationContext` на весь `StrategyStep`.

Правило:

```text
один рассчитываемый StrategyAction = один свежий CalculationContext
```

Причина:

```text
StrategyStep не atomic transaction.
Actions выполняются последовательно.
После каждого action могут измениться Order / AlgoOrder / Position / Balance / market facts.
```

Если step содержит несколько actions:

```text
action1 -> CalculationContext #1 -> calculation -> risk -> command
action2 -> CalculationContext #2 -> calculation -> risk -> command
```

## 13.3. Step из нескольких actions

На первом этапе выбираем безопасное правило:

```text
если текущий action не завершён,
следующий action этого step не стартует.
```

Это относится к статусам:

```text
CREATED
SUBMITTED
RETRY_PENDING
FAILED
```

Если action ушёл в `RETRY_PENDING`:

```text
весь step ждёт retry текущего action;
следующие actions step не выполняются;
handler может выполнять refresh / safety commands.
```

Это снижает параллелизм, но делает flow предсказуемым и безопасным.

## 13.4. RiskValidator и RiskBlockResolver

`RiskValidator` применяется только к action, который создаёт, увеличивает или ослабляет контроль runtime-риска.

Он не применяется к exit / cleanup / safety-flow, потому что эти действия снимают, уменьшают или локализуют риск.

К exit / cleanup / safety-flow относятся:

```text
reduce-only partial exit через Order / AlgoOrder
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
EXECUTE_KILL_SWITCH
```

Для таких действий нужны minimal domain / exchange safety checks и invariant checks, а не risk-policy validation.

`RiskValidator` возвращает результат проверки риска по уже рассчитанному risk-creating / risk-modifying action.

Если результат блокирующий:

```text
RiskCheckResult = BLOCKED
```

handler вызывает:

```text
RiskBlockResolver.resolve(...)
```

`RiskBlockResolver` не исполняет команды.

Он возвращает доменное решение для handler:

```text
закрыть candidate Deal без ошибки;
перевести Deal в ERROR;
пропустить action;
ждать retry;
разрешить только safety-flow.
```

Именно handler принимает финальное runtime-решение:

```text
какой Deal.status поставить;
какие ServiceCommand создать;
какой transition вернуть.
```

## 13.5. Реакция FSM на BLOCKED risk result

### PRECHECK

Если `RiskValidator` блокирует entry / risk-creating action в `PRECHECK`, а live risk ещё не создан:

```text
Deal.status = CLOSED
Deal.closeReason = RISK_CONTROL
```

Это штатное закрытие candidate Deal без `ERROR`.

Отдельное правило `ENTRY_CONDITION_EXPIRED` остаётся в силе:

```text
ENTRY_CONDITION_EXPIRED
  -> condition входа стал false

RISK_CONTROL
  -> condition может быть true, но risk-layer запретил вход
```

### ENTRY_SUBMITTED / ENTRY_FINALIZED / PROTECTION_SWITCHED / MANAGING

Если risk-creating / risk-increasing / risk-weakening action заблокирован RiskValidator, а live risk уже есть, мог появиться или состояние неизвестно:

```text
Deal.status = ERROR
```

Дальше работает `ErrorHandler` / safety-flow.

Обычные strategy steps больше не выполняются.

В `EXIT_PENDING` RiskValidator обычно не вызывается: этот этап занимается cleanup/finalization.
Если exit / cleanup action не проходит minimal safety / invariant checks, handler действует через refresh / recovery / ERROR / safety-flow, но это не `RiskValidationResult`.

## 13.6. Неблокирующий risk result

Неблокирующий risk result не останавливает FSM.

Он может быть записан в:

```text
лог;
метрики;
будущую историю исполнения;
timeline warning.
```

Но если action разрешён, handler продолжает обычный flow:

```text
ServiceCommandFactory -> ServiceCommandExecutor
```

## 13.7. Controlled calculation errors

`StrategyActionCalculator` не должен превращать ожидаемые runtime-проблемы в random exception.

Для контролируемых ошибок используется calculation-result:

```text
TEMPORARY_ERROR
PERMANENT_ERROR
```

### TEMPORARY_ERROR

Примеры:

```text
нет свежей цены;
ещё не готов indicator value;
задержался market structure;
exchange facts временно не обновлены.
```

Реакция handler:

```text
DealActionState.status = RETRY_PENDING
nextRetryAt = now + retry policy delay
step ждёт retry текущего action
```

Если `nextRetryAt` ещё не наступил, handler не должен пытаться выполнить этот action повторно.

### PERMANENT_ERROR

Примеры:

```text
action невозможно рассчитать;
конфигурация не позволяет получить нужную цену/размер;
target отсутствует и это не временное состояние;
расчёт нарушает runtime-инвариант.
```

Реакция для активных runtime-статусов сделки:

```text
Deal.status = ERROR
```

## 13.8. Unexpected exceptions

Unexpected exceptions не превращаются в `CalculationError`.

Они ловятся на границе:

```text
DealOrchestratorJob / FSM execution boundary
```

Базовые коды:

```text
INTERNAL_ERROR
EXCHANGE_ERROR
VALIDATION_ERROR
```

Где:

```text
INTERNAL_ERROR
  -> баг приложения, NPE, mapper, неожиданное состояние

EXCHANGE_ERROR
  -> timeout, connection reset, gateway/API error, ошибка клиента биржи

VALIDATION_ERROR
  -> нарушение инварианта/валидации, которое не должно было попасть в runtime
```

Runtime-реакция:

```text
Deal.status = ERROR
```

Если ошибка связана с конкретным action:

```text
retryable EXCHANGE_ERROR -> DealActionState.RETRY_PENDING
non-retryable error      -> DealActionState.FAILED
```

## 13.9. EXCHANGE_ERROR

`UNKNOWN_RESULT` на первом этапе не используем.

`EXCHANGE_TIMEOUT` отдельно не используем.

Все проблемы взаимодействия с биржей классифицируем как:

```text
EXCHANGE_ERROR
```

FSM не выбирает отдельную ветку по этому коду.

Каждый проход FSM и так должен сначала опираться на:

```text
refresh/search/history facts
DealContext
DealActionState
Order / AlgoOrder / Position
```

`EXCHANGE_ERROR` нужен для:

```text
retry context;
логов;
метрик;
диагностики;
будущей истории исполнения.
```

## 13.10. ServiceCommandFactory создаёт одну актуальную команду

Handler не должен ожидать, что `ServiceCommandFactory` сразу создаст всю цепочку:

```text
CREATE_ORDER -> SUBMIT_ORDER -> REFRESH_ORDER
```

Правило:

```text
один проход / один текущий action state -> одна актуальная ServiceCommand
```

Пример:

```text
DealActionState отсутствует
  -> CREATE_ORDER

DealActionState = CREATED
  -> SUBMIT_ORDER

DealActionState = SUBMITTED
  -> REFRESH_ORDER / REFRESH_POSITION / history/fills
```

Это согласуется с тем, что `ServiceCommand` — runtime object, а не persisted queue.

## 13.11. CLOSE_POSITION и refresh после закрытия

`CLOSE_POSITION` используется только для полного закрытия позиции.

`RiskValidator` для `CLOSE_POSITION` не вызывается ни в normal-flow, ни в safety-flow.

Причина:

```text
CLOSE_POSITION не создаёт новый риск,
а убирает или локализует уже существующий live risk.
```

Перед созданием `CLOSE_POSITION` handler выполняет только minimal domain / exchange safety checks:

```text
позиция существует или подтверждена exchange facts;
позиция относится к текущей сделке / инструменту;
закрывается вся позиция, не partial;
есть необходимые данные для exchange adapter;
команда не противоречит текущему known exchange state.
```

Domain payload не содержит:

```text
autoCxl
autoCancelOrders
```

Exchange adapter для OKX может технически отправить `autoCxl=true`, но FSM не должна на это полагаться.

После `CLOSE_POSITION` handler подтверждает факт закрытия позиции через:

```text
REFRESH_POSITION
```

Дальше handler дочищает known live orders/algo-orders и добирает history/fills facts для финализации сделки.

Если остались известные сделке live orders/algo-orders:

```text
CANCEL_ORDER
CANCEL_ALGO_ORDER
```

Если найдены неизвестные live-сущности:

```text
это anomaly/safety-flow,
а не обычный cleanup сделки.
```

## 13.12. REFRESH_FILLS

`REFRESH_FILLS` — runtime read-only команда.

`REFRESH_FILLS` используется в финализации сделки для итогового подсчёта profit/loss.

На первом этапе `Fill` как отдельную persisted entity не храним.

Один общий `RefreshFillsExecutor`:

```text
загружает fills;
сопоставляет их с известными Order / AlgoOrder / Position facts;
обновляет вложенные runtime-сущности;
не обновляет Deal напрямую.
```

`Deal.resultProfit` считается на основании фактов исполнений, собранных через `REFRESH_FILLS`.

`Deal` обновляет handler по фактам вложенных сущностей.

Повторный `REFRESH_FILLS` должен быть идемпотентным:

```text
filled size / fee / pnl не должны задваиваться.
```

