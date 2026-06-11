# CancelAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CANCEL_ALGO_ORDER` (компонент-executor): что делает.

## Назначение

Получает `CANCEL_ALGO_ORDER`. Загружает algo-order по
`payload.algoOrderId`, отправляет cancel — endpoint ветвится по
семье algo (ordinary → `cancel-algos`, advance/trailing →
`cancel-advance-algos`; И-1 исход (а), семья из `conditionType` —
`docs/models/mapping/AlgoOrder.md`), сохраняет ACK / command result;
`AlgoOrder` в `CANCELED` по ACK не переводит — факт отмены подтверждается
refresh/search/history. Если refresh/history показывает другой факт,
верим exchange facts (см.
`docs/models/mapping/AlgoOrder.md`).

После рестарта pending cancel в очереди не восстанавливается (см.
`docs/rules/command-lifecycle.md`). ACK не runtime truth (см.
`docs/rules/ack-not-runtime-truth.md`); общая семантика `CANCEL_*` —
`docs/components/ServiceCommandExecutor.md`.

## CancelAlgoOrderCommandPayload

`algoOrderId`, `cancelReason`.
