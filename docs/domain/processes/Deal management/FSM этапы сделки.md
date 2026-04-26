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

## 2.2. StrategyCondition решает, применим ли step

FSM handler не должен сам знать, когда переносить SL, делать partial exit или закрывать позицию по правилу стратегии.

Он делает так:

```text
handler
  -> берёт steps для текущего Deal.Status
  -> для каждого StrategyStep проверяет StrategyCondition
  -> если condition true, step применим
```

## 2.3. StrategyAction говорит, что именно сделать

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

## 2.4. StrategyActionCalculator применяет настройки стратегии

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

## 2.5. Что стратегия не делает

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
2. Проверить `StrategyCondition` выбранного step.
3. Если condition не выполнен — не создавать entry action и оставить сделку в текущем статусе или завершить по отдельной политике.
4. Если condition выполнен — взять `StrategyAction` из step.
5. Для каждого action проверить `DealActionState`.
6. Если action ещё не материализован — вызвать `StrategyActionCalculator`.
7. Создать `CREATE_ORDER` через `ServiceCommandFactory`.
8. После создания локального order создать или запланировать `SUBMIT_ORDER`.
9. Если action уже был создан до рестарта — продолжить с refresh/submit по `DealActionState`.

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

`ENTRY_FINALIZED` создаёт или подтверждает основную standalone-защиту позиции.

## 6.2. Источники информации

* `Deal`;
* pinned `StrategyDetail`;
* `StrategyStep` со `stepType = MAIN_PROTECTION`;
* `StrategyCondition` protection-step;
* `Instrument`;
* `PositionContext`;
* активная `Position`;
* entry `Order`;
* attached protection внутри entry `Order`;
* локальные `AlgoOrder`;
* `DealActionState` по protection actions;
* свежий `CalculationContext`, если нужно рассчитать SL / TP / OCO / trailing.

Через `CalculationContext` могут быть получены:

* `InstrumentExternalRules`;
* `MarketPriceData`;
* `IndicatorValue`, например ATR;
* `MarketStructure`, если защита считается от swing/range/support/resistance;
* `MarketPhase`, если защита зависит от текущей фазы;
* актуальный balance/risk snapshot.

## 6.3. Входные проверки

Проверяем:

* `Deal.status = ENTRY_FINALIZED`;
* есть pinned `StrategyDetail`;
* позиция активна;
* позиция соответствует сделке, инструменту и направлению;
* entry order финализирован или есть достаточные факты исполнения;
* известна фактическая цена входа или есть путь получить её через `REFRESH_FILLS`;
* нет больше одной позиции по инструменту;
* нет критичного риска без возможности защиты.

## 6.4. Рабочая логика этапа

1. Найти `StrategyStep` со `stepType = MAIN_PROTECTION`.
2. Проверить `StrategyCondition` protection-step.
3. Если condition выполнен — взять protection actions.
4. Для каждого action проверить `DealActionState`.
5. Если основной protection action ещё не материализован — вызвать `StrategyActionCalculator`.
6. Создать `CREATE_ALGO_ORDER`.
7. Создать или запланировать `SUBMIT_ALGO_ORDER`.
8. Создать refresh-команды для подтверждения active protection.
9. Если нужно снять attached protection после main protection — создать cancel-команду только после подтверждения main protection.

## 6.5. Выходные проверки

Этап можно считать завершённым, если:

* standalone protection создана;
* standalone protection подтверждена как active;
* позиция активна;
* временная attached protection больше не нужна или безопасно снята;
* нет дублирующей защиты;
* нет orphan algo-orders;
* риск позиции защищён.

Именно эти выходные проверки отвечают за переход:

```text
ENTRY_FINALIZED -> PROTECTION_SWITCHED
```

## 6.6. Переходы

Обычный переход:

```text
ENTRY_FINALIZED -> PROTECTION_SWITCHED
```

Аварийный переход:

```text
ENTRY_FINALIZED -> ERROR
```

Recovery-переход:

```text
ENTRY_FINALIZED -> PROTECTION_SWITCHED
```

Recovery допустим, если после рестарта main protection уже активна.

## 6.7. Допустимые StrategyStep

```text
MAIN_PROTECTION
FAIL_SAFE
```

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

---

# 7. PROTECTION_SWITCHED

## 7.1. Назначение

`PROTECTION_SWITCHED` подтверждает, что основная защита активна, а временная attached-защита больше не создаёт конфликт.

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
* нет критичного расхождения БД и биржи.

## 7.4. Рабочая логика этапа

1. Создать `REFRESH_POSITION`, если позиция давно не обновлялась.
2. Создать `REFRESH_ALGO_ORDERS`, чтобы подтвердить active main protection.
3. Проверить, осталась ли attached protection.
4. Если attached protection ещё активна и main protection уже подтверждена — создать cancel-команду.
5. Проверить pending orders, которые могут конфликтовать с защитой.
6. Если есть конфликт — cancel или ERROR в зависимости от риска.

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

---

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
3. Проверить conditions для:

    * `PROTECTION_ADJUSTMENT`;
    * `PARTIAL_EXIT`;
    * `GRID_MANAGEMENT`;
    * `EXIT`;
    * `FAIL_SAFE`.
4. Для каждого применимого step взять actions.
5. Для actions, которые создают или меняют сущности, проверить `DealActionState`.
6. Для нового или изменяемого action вызвать `StrategyActionCalculator`.
7. Создать нужные `ServiceCommand`.
8. Если condition требует полного выхода — создать `CLOSE_POSITION` или соответствующие cancel/close-команды.
9. Если risk/fail-safe condition сработал — перейти к emergency flow.

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

`ERROR` — аварийное состояние сделки.

В `ERROR` обычная торговая стратегия не применяется.

Здесь приоритет:

* безопасность;
* снятие риска;
* фиксация состояния;
* расследование.

## 11.2. Источники информации

* `Deal`;
* `DealContext`;
* `PositionContext`;
* все локальные `Order`;
* все локальные `AlgoOrder`;
* `DealActionState`;
* exchange snapshots:

    * positions;
    * pending orders;
    * pending algo-orders;
    * fills/history, если нужно;
* anomaly report, если ошибка пришла из `AnomalyJob`;
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
2. Если есть активный риск — создать `EXECUTE_KILL_SWITCH`.
3. Если риск уже снят — оставить сделку в `ERROR` для ручного разбора или отдельной финализации.
4. Зафиксировать состояние для расследования через аудит/историю.

## 11.5. Выходные проверки

Обычного автоматического перехода из `ERROR` в рабочий статус нет.

Можно проверить:

* активный риск снят;
* позиция закрыта;
* live orders отсутствуют;
* live algo-orders отсутствуют;
* состояние зафиксировано для расследования.

Вопрос о переводе из `ERROR` в другой статус должен решаться отдельной политикой.

## 11.6. Переходы

Обычные переходы не фиксируем.

Команды безопасности возможны:

```text
ERROR -> ERROR
```

## 11.7. Допустимые StrategyStep

Обычная стратегия не применяется.

Допустим только аварийный safety-flow, если он оформлен как системная логика, а не как обычная торговая стратегия.

## 11.8. Возможные ServiceCommand

```text
EXECUTE_KILL_SWITCH
MARK_DEAL_ERROR
REFRESH_POSITION
REFRESH_PENDING_ORDERS
REFRESH_ALGO_ORDERS
```

---

# 12. Общие правила recovery

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

# 13. Вопросы к обсуждению

1. Нужно ли разрешать автоматический переход `ERROR -> CLOSED`, если kill-switch снял весь риск и сделка фактически закрыта?
2. Нужно ли для `PRECHECK` поддерживать сценарий “entry condition больше не актуален — закрыть Deal без ошибки”, или созданный `Deal` всегда должен либо пойти дальше, либо стать `ERROR`?
3. Нужно ли выделить отдельный статус между `ENTRY_FINALIZED` и `PROTECTION_SWITCHED`, если позже захотим явно разделить “main protection established” и “attached protection cancelled”?
4. Нужно ли `CLOSED` дополнительно валидировать периодическим job'ом, или это зона `AnomalyJob`?
