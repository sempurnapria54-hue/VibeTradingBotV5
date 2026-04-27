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
* runtime-сущностям `Order`, `AlgoOrder`, `Position`, `BalanceContainer`;
* результатам refresh-команд;
* свежему `CalculationContext`, который собирается внутри `StrategyActionCalculator` только для конкретного action.

Главное правило:

> FSM решает, какой этап сделки сейчас актуален, какие strategy steps можно проверить, какие service commands нужно создать и можно ли перейти в следующий `Deal.Status`.

# Зафиксированные решения lifecycle / FSM

В эту версию документа встроены решения, принятые в `01. Жизненный цикл сделки` и `Strategy.md`:

```text
Q1. Strategy.INACTIVE / Strategy.DELETED / market data expired
Q2. entryReason: короткий код в Deal, подробности в `05. Аудит и история исполнения`
Q3. entryStepType: ENTRY / GRID_ENTRY хранится в Deal
Q4. PROTECTION_ESTABLISHED не вводим; PROTECTION_SWITCHED условный
Q5. ERROR -> CLOSED запрещён; добавлен EMERGENCY_CLOSED
Q6. PRECHECK может закрыть candidate Deal с ENTRY_CONDITION_EXPIRED, если live risk ещё не создан
Q7. CLOSED / EMERGENCY_CLOSED — terminal-статусы без handlers; CloseHandler переименовывается в ExitPendingHandler
Q8. ACKED не добавляем; CONFIRMED убираем; успешный terminal-статус action — COMPLETED
Q9. ServiceCommand — runtime object; persisted queue не вводим; direct partial close запрещён
```

Для FSM это означает:

* `Strategy.INACTIVE` не меняет сопровождение уже открытой сделки: FSM продолжает работать по pinned `StrategyDetail`.
* `Strategy.DELETED` не запускает новые сделки и для уже открытых сделок приводит к graceful shutdown, если это безопасно.
* Устаревание рыночных данных не меняет `Strategy.Status`; перед проверкой data-dependent `StrategyStep` handler вызывает `MarketDataExpirationChecker.checkForStep(dealContext, step)`.
* `Deal.entryReason` и `Deal.entryStepType` не управляют FSM-переходами, но используются для API/UI/аналитики/аудита.
* `PROTECTION_SWITCHED` используется только если реально выполняется замена temporary attached protection на standalone main protection.
* Если switch не нужен, допустим прямой переход `ENTRY_FINALIZED -> MANAGING`, но только если позиция безопасна для сопровождения.
* `ERROR -> CLOSED` запрещён; аварийная финализация идёт через `ERROR -> EMERGENCY_CLOSED`.
* `PRECHECK -> CLOSED` с `ENTRY_CONDITION_EXPIRED` разрешён только если live risk ещё не создан.
* `CLOSED` и `EMERGENCY_CLOSED` — terminal-статусы без handlers.
* `CloseHandler` переименовывается в `ExitPendingHandler`.
* `DealActionStateStatus.CONFIRMED` не используется; action success terminal status — `COMPLETED`.
* `ServiceCommand` — runtime object; FSM после рестарта не ищет pending commands в БД.
* Direct partial close запрещён; partial exit выполняется через reduce-only `Order` / `AlgoOrder`.


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
* `StrategyPositionAction.closeFractionPercents`;
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

* `DealContext` — факты сделки;
* pinned `StrategyDetail` — правила сделки;
* `DealActionState` — runtime-состояние strategy actions;
* локальные `Order`, `AlgoOrder`, `Position`, `BalanceContainer`;
* результаты refresh-команд;
* `StrategyActionCalculator`, если нужно рассчитать параметры нового action.

Свежие рыночные данные не кладём в `DealContext` заранее.

Для расчёта конкретного действия используется свежий `CalculationContext`, который собирается внутри `StrategyActionCalculator`.

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
* `PositionContext`;
* локальные `Order`, если они уже есть по сделке или инструменту;
* локальные `AlgoOrder`, если они уже есть по сделке или инструменту;
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
* `PositionContext` не содержит больше одной позиции по инструменту;
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

1. Найти `StrategyStep` со `stepType = ENTRY` или `GRID_ENTRY`.
2. Проверить свежесть данных step через `MarketDataExpirationChecker.checkForStep(dealContext, step)`.
3. Если данные устарели или отсутствуют — применить `StrategyStep.marketDataExpiredSetting`.
4. Если данные свежие — проверить `StrategyCondition` выбранного step.
5. Если condition не выполнен — проверить, создан ли live risk.
    * Если live risk отсутствует, закрыть candidate Deal без ошибки:
      `Deal.status = CLOSED`, `Deal.closeReason = ENTRY_CONDITION_EXPIRED`.
    * Если live risk уже есть или состояние неизвестно, прямое `CLOSED` запрещено: нужен recovery / safety-flow.
6. Если condition выполнен — взять `StrategyAction` из step.
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

Обычный переход:

```text
PRECHECK -> ENTRY_SUBMITTED
PRECHECK -> CLOSED (только ENTRY_CONDITION_EXPIRED без live risk)
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
* `PositionContext`;
* локальные `Order`;
* локальные `AlgoOrder`;
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
5. Если нужна фактическая цена исполнения — создать `REFRESH_FILLS`.
6. Если результат отправки неизвестен — перед повторным `SUBMIT_ORDER` сначала искать order по client order id.
7. Если order исполнен частично — определить, достаточно ли этого для появления позиции и продолжения этапа.
8. Если факты противоречивы — перейти в recovery или `ERROR`.

## 5.5. Выходные проверки

Этап можно считать завершённым, если:

* entry order финализирован;
* позиция открыта;
* позиция соответствует сделке, инструменту и направлению;
* если attached protection была нужна — она подтверждена или не потеряна;
* нет конфликтующих active orders;
* нет критичных аномалий.

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
```

Recovery-переход дальше `ENTRY_FINALIZED` допустим, если после рестарта уже есть позиция и активная основная защита.

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
REFRESH_ORDER_HISTORY
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
* `PositionContext`;
* активная `Position`;
* entry `Order`;
* attached protection внутри entry `Order`;
* локальные `AlgoOrder`;
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
12. `DealActionStateStatus.COMPLETED` для protection action ставится только после refresh/search/history facts.
13. ACK от биржи не считается runtime-truth.
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
* `PositionContext`;
* активная `Position`;
* entry `Order`;
* attached protection внутри `Order`;
* standalone protective `AlgoOrder`;
* все локальные `AlgoOrder` сделки;
* все локальные `Order` сделки;
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
* `PositionContext`;
* активная `Position`;
* локальные `Order`;
* локальные `AlgoOrder`;
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
* позиция активна или есть факты, что она уже закрыта и нужен переход в `EXIT_PENDING`;
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
11. Если risk/fail-safe condition сработал — перейти к emergency flow.

## 8.5. Выходные проверки

Переход в `EXIT_PENDING` разрешён, если:

* стратегия инициировала выход;
* позиция закрывается или уже закрыта;
* создана команда закрытия позиции или есть факт закрытия;
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
MANAGING -> CLOSED
```

Recovery в `CLOSED` допустим только если после рестарта факты показывают, что позиция закрыта, live risk отсутствует, а финализация уже может быть выполнена безопасно.

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
* `PositionContext`;
* последняя известная `Position`;
* локальные `Order`;
* локальные `AlgoOrder`;
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

1. Создать `REFRESH_POSITION`.
2. Создать `REFRESH_PENDING_ORDERS`.
3. Создать `REFRESH_ALGO_ORDERS`.
4. Если есть live ordinary orders — создать `CANCEL_ORDER`.
5. Если есть live algo-orders — создать `CANCEL_ALGO_ORDER`.
6. Если нужны fills — создать `REFRESH_FILLS`.
7. Если нужна история ordinary orders — создать `REFRESH_ORDER_HISTORY`.
8. Если нужна история algo-orders — создать `REFRESH_ALGO_ORDER_HISTORY`.
9. Определить или подтвердить `Deal.CloseReason`.
10. Создать `FINALIZE_DEAL_EXIT`, если факты готовы.
11. Создать `MARK_DEAL_CLOSED`, если всё очищено.

## 9.5. Выходные проверки

Этап можно считать завершённым, если:

* позиция закрыта;
* нет активного рыночного риска;
* нет live ordinary orders;
* нет live algo-orders;
* fills загружены, если нужны для финализации;
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

`CLOSED` — terminal-статус штатного закрытия.

Для `CLOSED` отдельный FSM handler не нужен.

Финальную проверку перед `CLOSED` выполняет `ExitPendingHandler` на переходе:

```text
EXIT_PENDING -> CLOSED
```

## 10.1. Назначение

`CLOSED` означает:

```text
штатная ветка закрытия завершена;
position закрыта или отсутствует;
live ordinary orders отсутствуют;
live algo-orders отсутствуют;
attached protection больше не влияет на риск;
финальные факты подтверждены refresh/search/history;
Deal больше не требует runtime-сопровождения FSM.
```

## 10.2. Поведение после CLOSED

Обычная FSM сделку больше не сопровождает.

Если после `CLOSED` найден live risk, это зона `AnomalyJob / ReconciliationJob`, а не `ClosedHandler`.

---

# 11. ERROR

## 11.1. Назначение

`ERROR` означает:

```text
обнаружена авария;
обычная strategy/FSM-логика заблокирована;
risk может быть живой;
нужен safety-flow / recovery / kill-switch.
```

`ErrorHandler` не выполняет обычные strategy steps.

В `ERROR` запрещено:

```text
проверять обычные strategy steps;
создавать новые entry/grid orders;
делать data-dependent protection adjustment;
делать partial exit по обычным strategy conditions;
продолжать MANAGING-flow.
```

## 11.2. Источники информации

* `Deal`;
* `DealContext`;
* `PositionContext`;
* все локальные `Order`;
* все локальные `AlgoOrder`;
* `DealActionState`;
* результаты refresh/search/history;
* результат kill-switch / safety-flow.

## 11.3. Рабочая логика этапа

`ErrorHandler` выполняет только safety/recovery-логику:

```text
REFRESH_POSITION;
REFRESH_PENDING_ORDERS;
REFRESH_ALGO_ORDERS;
REFRESH_ORDER_HISTORY;
REFRESH_ALGO_ORDER_HISTORY;
REFRESH_FILLS;
CANCEL_ORDER;
CANCEL_ALGO_ORDER;
CLOSE_POSITION;
EXECUTE_KILL_SWITCH;
MARK_DEAL_ERROR.
```

## 11.4. Выходные проверки

`ErrorHandler` может перевести сделку:

```text
ERROR -> EMERGENCY_CLOSED
```

только если подтверждено:

```text
1. Position закрыта или отсутствует.
2. Live ordinary orders отсутствуют.
3. Live algo-orders отсутствуют.
4. Attached protection отсутствует или больше не влияет на риск.
5. Нет pending сущностей, которые могут создать или увеличить риск.
6. Финальные exchange facts подтверждены.
7. Сделка больше не требует runtime-сопровождения FSM.
```

Если хотя бы один пункт не подтверждён:

```text
сделка остаётся в ERROR;
ErrorHandler продолжает safety-flow;
или требуется ручной разбор.
```

Запрещённый переход:

```text
ERROR -> CLOSED
```

## 11.5. Возможные ServiceCommand

```text
REFRESH_POSITION
REFRESH_PENDING_ORDERS
REFRESH_ALGO_ORDERS
REFRESH_ORDER_HISTORY
REFRESH_ALGO_ORDER_HISTORY
REFRESH_FILLS
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
EXECUTE_KILL_SWITCH
MARK_DEAL_ERROR
```

---

# 12. EMERGENCY_CLOSED

`EMERGENCY_CLOSED` — terminal-статус аварийного закрытия после подтверждённого снятия live risk.

Для `EMERGENCY_CLOSED` отдельный FSM handler не нужен.

## 12.1. Назначение

`EMERGENCY_CLOSED` означает:

```text
аварийный сценарий завершён;
kill-switch / safety-flow снял live risk;
отсутствие live risk подтверждено refresh/search/history facts;
сделка terminal, но не считается штатно закрытой.
```

Если после `EMERGENCY_CLOSED` найден live risk, это глобальная аномалия и зона `AnomalyJob / ReconciliationJob`.

---

# 13. Общие правила recovery

## 12.1. Recovery не равен обычному переходу

Обычный переход между этапами делает выходная проверка текущего FSM-статуса.

Recovery-переход после рестарта может произойти на входе в handler, если факты показывают, что сделка уже ушла дальше.

Пример:

```text
Deal.status = ENTRY_SUBMITTED

Факты:
  entry order completed
  position active
  main protection active

Безопасный recovery:
  ENTRY_SUBMITTED -> PROTECTION_SWITCHED
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

# 14. Открытые вопросы

Открытые вопросы после применения Q1–Q9:

```text
Q1. AMEND_ALGO_ORDER: direct amend или safe replace для protection-сценариев.
Q2. ServiceCommandFactory создаёт цепочку команд сразу или по одной.
Q3. Разделять ли EXCHANGE_TIMEOUT и UNKNOWN_RESULT.
Q4. Scope CalculationContext: на action, step или проход FSM.
Q5. Ошибки калькуляторов: controlled exception или error-result.
Q6. RiskCalculator: blocking result или warning mode.
Q7. Timeline сделки и группировка технических шагов в `05. Аудит и история исполнения`.
```

---

# 15. Что изменено в этой редакции

Изменения внесены точечно, без удаления существующих разделов и подробных комментариев, если они не противоречили принятым решениям.

## Добавлено

* Решения Q5–Q9 в зафиксированные lifecycle/FSM правила.
* `EMERGENCY_CLOSED` как terminal-статус без handler.
* Ветка `PRECHECK -> CLOSED` с `ENTRY_CONDITION_EXPIRED`, если live risk ещё не создан.
* Runtime-only recovery для `ServiceCommand`.


```text
1. Раздел с зафиксированными решениями Q1–Q4.
2. Проверка MarketDataExpirationChecker перед StrategyCondition для data-dependent steps.
3. Условная семантика PROTECTION_SWITCHED.
4. Прямой безопасный переход ENTRY_FINALIZED -> MANAGING, если protection switch не нужен.
5. Ссылки на документы по рыночным данным, калькуляторам и аудиту.
```

## Изменено

* `CLOSED` и `EMERGENCY_CLOSED` описаны как terminal-статусы без handlers.
* `ERROR` больше не может переходить в `CLOSED`; только в `EMERGENCY_CLOSED` после safety-flow.
* Открытые вопросы обновлены и перенумерованы по критичности после Q1–Q9.


```text
1. ENTRY_FINALIZED больше не обязан вести только в PROTECTION_SWITCHED.
2. PROTECTION_SWITCHED теперь описан как статус только фактического protection-switch flow.
3. MANAGING проверяет freshness конкретного StrategyStep перед выполнением data-dependent actions.
4. Раздел вопросов обновлён: Q1–Q4 считаются применёнными, остались только нерешённые вопросы.
```

## Удалено / заменено

```text
1. Старая обязательная цепочка ENTRY_FINALIZED -> PROTECTION_SWITCHED -> MANAGING заменена на условную:
   - ENTRY_FINALIZED -> PROTECTION_SWITCHED -> MANAGING;
   - ENTRY_FINALIZED -> MANAGING.

2. Вопрос про отдельный PROTECTION_ESTABLISHED убран из открытых, потому что решение принято:
   новый статус не вводим.
```

Другие разделы, модели и комментарии сохранены.
