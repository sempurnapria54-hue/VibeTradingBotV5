# AmendAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `AMEND_ALGO_ORDER` (компонент-executor): что делает, чем
не является.

## Назначение

Получает `AMEND_ALGO_ORDER` — обновление существующего `AlgoOrder` на
бирже. Загружает algo-order по `payload.algoOrderId`, отправляет amend,
сохраняет ACK / command result для диагностики; `AlgoOrder` не
финализирует, торговое решение не принимает; факт новых параметров
подтверждается refresh.

`AMEND_ALGO_ORDER` ≠ replace / protection switch: replace-flow
собирается отдельными атомарными командами вне этого executor'а. Если
после cancel/amend intent refresh/history показывает другой факт —
система верит exchange facts (effective→COMPLETED,
partially_effective→PARTIALLY_COMPLETED, order_failed/partially_failed →
`ExternalStatusException`, см.
`docs/models/mapping/AlgoOrder.md`).

ACK не runtime truth (см. `docs/rules/ack-not-runtime-truth.md`); общая
семантика `AMEND_*` — `docs/components/ServiceCommandExecutor.md`.
