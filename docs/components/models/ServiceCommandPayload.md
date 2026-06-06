# ServiceCommandPayload

## На какой вопрос отвечает этот файл

Что это за runtime value object `ServiceCommandPayload` и какие
payload-подтипы команд существуют (разделами).

## Назначение

`ServiceCommandPayload` — параметры конкретной `ServiceCommand` (см.
`docs/components/models/ServiceCommand.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`). Подтипы описаны разделами:
без своей команды payload смысла не имеет (см.
`.claude/decisions/model-granularity.md`).

> По решению `.claude/decisions/executor-payload-file-granularity.md`
> payload документируется разделом в доке своего executor'а; общий
> файл-агрегат отменён. Судьба этого файла и вопрос базового
> типа/дискриминатора — CMD-Q2
> (`.claude/work/questions/open-questions.md`), горизонт — шаг 4. До
> переноса разделы остаются здесь.

Общий принцип payload'ов: хранят минимум — обычно локальный ID сущности,
остальное (client id, external id, инструмент) executor берёт из
загруженной сущности. `positionSide`/`marginMode` в payload — generic
command-level intent; OKX adapter всё равно ставит `tdMode=isolated`,
`posSide=net` и валидирует response (см.
`docs/models/mapping/Order.md`).

## CreateOrderCommandPayload

`orderType` (`Order.Type`), `strategyDirection` (`StrategyTradeDirection`),
`side` (buy/sell), `positionSide`, `instrumentExternalId`, `marginMode`,
`executionType`, `sizeContracts`, `price`, `sendPriceToExchange`,
`positionReducingOnly` (доменное намерение → OKX `reduceOnly` в adapter),
`attachedProtection` (`AttachedProtectionPayload`, если order создаётся со
стартовым SL/TP).

## SubmitOrderCommandPayload

Только `orderId` (executor сам берёт `internalId` как `clOrdId`,
`externalId` если есть).

## AmendOrderCommandPayload

`orderId`, `newPrice`, `newSizeContracts`, `cancelOnFail` (опасная
настройка, задаётся явно execution policy/стратегией). External/client id
не передаются — executor берёт из order.

## CancelOrderCommandPayload

`orderId`, `cancelReason` (`CancelReason`).

## CreateAlgoOrderCommandPayload

`conditionType` (`ConditionType`: SL/OCO_FULL/PARTIAL_TAKE_PROFIT/TRAILING
и т.д.), `side`, `positionSide`, `instrumentExternalId`, `marginMode`,
`positionReducingOnly` (для защитных почти всегда `true`), `sizeContracts`,
`stopLossPrice` (`ResolvedStopLossPrice`), `takeProfitPrice`
(`ResolvedTakeProfitPrice`), `trailingPrice` (`ResolvedTrailingPrice`).
`closeFraction` не передаётся — остаётся sizing intent; command-layer
получает готовый `sizeContracts`.

## SubmitAlgoOrderCommandPayload

Только `algoOrderId` (executor сам берёт internal/client/external id,
инструмент, параметры).

## AmendAlgoOrderCommandPayload

`algoOrderId`, `conditionType`, `newStopLossPrice`, `newTakeProfitPrice`,
`newTrailingPrice`, `newSizeContracts` (доля закрытия пересчитана в размер
до создания команды).

## CancelAlgoOrderCommandPayload

`algoOrderId`, `cancelReason`.

## ClosePositionCommandPayload

`positionId`, `requestedCloseReason` (`Position.CloseReason`). Не
содержит `closeFraction` — `CLOSE_POSITION` всегда full close (см.
`docs/rules/no-partial-close.md`). Не содержит `autoCancelOrders`/`autoCxl`
— это OKX-specific флаг adapter (см.
`docs/models/mapping/Position.md`). `instrumentExternalId`/
`positionSide`/`marginMode` не нужны — приходят из `DealContext` /
adapter.

## AttachedProtectionPayload

Параметры attached protection при создании order со стартовым SL/TP
(вложен в `CreateOrderCommandPayload.attachedProtection`). Структура
attached protection — `docs/models/domain/core/Order.md` (`AttachedAlgoOrder`).
