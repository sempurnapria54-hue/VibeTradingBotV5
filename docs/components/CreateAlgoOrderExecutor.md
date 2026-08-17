# CreateAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ALGO_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ALGO_ORDER_COMMAND`. Создаёт локальный `AlgoOrder` со статусом
`CREATED`, генерирует `internalId`, сохраняет рассчитанные
SL/TP/trailing-параметры, обновляет `DealActionState.target =
RuntimeTarget(ALGO_ORDER, algoOrderId)` и `DealActionState.status =
CREATED`. На биржу не ходит.

## Плановый риск сделки — парная клауза к `CreateOrderExecutor`

**Писатель операндов планового риска — тот же per-leg executor** (H5
`DOCS_CHECK_12`, решение пользователя). Дом операндов — **сущность ноги
входа** (H3 `GAPS_CLOSE_11`), поэтому когда вход исполняется алго-ордером,
`plannedEntryPrice` и `plannedSizeContracts` пишет **этот** executor, на
создаваемый `AlgoOrder`, той же транзакцией, что создаёт сущность —
симметрично `CreateOrderExecutor` (§«Плановый риск сделки» там). Знаменатель
`Deal.plannedRiskAmount` остаётся на `Deal`; на `AlgoOrder` едут только
операнды (`docs/models/domain/core/AlgoOrder.md` §«Операнды планового
риска»).

- **Канал доставки — тот же**, что у обычной ноги: четыре числа едут полями
  payload'а (§`CreateAlgoOrderCommandPayload`), потому что в сущности они не
  остаются, а `RiskValidator` на этой тропе **уже вызывается**
  (`docs/rules/risk-validator-scope.md` включает
  `CREATE_ALGO_ORDER_COMMAND` в множество валидируемых risk-creating
  действий) — метрика посчитана, нового расчёта и вызовов биржи правка не
  добавляет.
- **Write-once и «только у входного действия»** — как у обычной ноги: для
  защитных `CREATE_ALGO_ORDER_COMMAND` (standalone SL/TP, OCO, trailing,
  partial exit) поля пусты, там нет преконтроля, который их производит.
- **Достижима ли входная тропа алго-ордером — открытый `RISK-Q4`**
  (гейтит `CODE`): от него зависит, у какой из двух сущностей заводятся
  колонки — `orders`, `algo_orders` или обеих; §Назначение `AlgoOrder`
  входа среди применений не перечисляет, а `risk-validator-scope.md`
  предполагает (H11 `DOCS_CHECK_12`). **Назначение писателя от этого исхода
  не зависит** — оно per-leg при любом; открыт только состав таблиц.

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` / `RuntimeTarget` — `docs/models/domain/other/DealActionState.md`.

## CreateAlgoOrderCommandPayload

`conditionType` (`ConditionType`: SL/OCO_FULL/PARTIAL_TAKE_PROFIT/TRAILING
и т.д.), `side`, `positionSide`, `instrumentExternalId`, `marginMode`,
`positionReducingOnly` (для защитных почти всегда `true`), `sizeContracts`,
`stopLossPrice` (`ResolvedStopLossPrice`), `takeProfitPrice`
(`ResolvedTakeProfitPrice`), `trailingPrice` (`ResolvedTrailingPrice`).
`closeFraction` не передаётся — остаётся sizing intent; command-layer
получает готовый `sizeContracts`.

**Поля планового риска — только у входного действия** (симметрично
`CreateOrderCommandPayload`, H5 `DOCS_CHECK_12`): `plannedRiskAmount`,
`plannedRiskCurrency`, `plannedEntryPrice`, `plannedSizeContracts`. У
защитных `CREATE_ALGO_ORDER_COMMAND` они пусты. Это то же единственное
исключение из «payload хранит минимум»: четыре числа в сущности не
остаются, а преконтроль их уже посчитал.
