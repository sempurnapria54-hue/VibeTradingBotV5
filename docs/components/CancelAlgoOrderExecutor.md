# CancelAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CANCEL_ALGO_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CANCEL_ALGO_ORDER_COMMAND`. Загружает algo-order по
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

## Числа риска на сделке пересчитываются здесь

**Отмена защиты меняет операнд «действующая защита»**, поэтому executor
той же транзакцией, в которой проставлен терминальный `closeReason`
защитной ноги, пересчитывает **все четыре** числа риска на `Deal` — по
общему правилу «кто меняет любой операнд, пересчитывает всю четвёрку»
(`docs/models/domain/aggregate/Deal.md` §«Взятый риск», таблица
триггеров — место истины; формулы здесь не пересказываются).

**Асимметрия с `CancelOrderExecutor` снята** (A7 `DOCS_CHECK_19`):
отмена ноги входа пересчёт несла, отмена защиты — нет, при том что
объявленное «действующей защиты нет ⇒ поле пусто» без этого писателя
было недостижимым состоянием. Троп исчезновения защиты три: teardown
выхода, kill-switch и снятие встроенной защиты доборной ноги после
подтверждения новой основной.
