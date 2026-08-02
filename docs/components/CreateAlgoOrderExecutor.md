# CreateAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ALGO_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ALGO_ORDER_COMMAND`. Создаёт локальный `AlgoOrder` со статусом
`CREATED`, генерирует `internalId`, сохраняет рассчитанные
SL/TP/trailing-параметры, обновляет `DealActionState.target =
RuntimeTarget(ALGO_ORDER, algoOrderId)` и `DealActionState.status =
CREATED`. На биржу не ходит.

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
