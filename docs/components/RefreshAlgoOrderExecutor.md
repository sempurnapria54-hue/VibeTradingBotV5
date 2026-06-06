# RefreshAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_ALGO_ORDER` (компонент-executor): что делает,
границы.

## Назначение

Получает `REFRESH_ALGO_ORDER`. Загружает локальный algo-order, получает
актуальное состояние с биржи, обновляет сущность, прогоняет внешний
статус через `AlgoOrderExternalStatusResolver`, при необходимости
обновляет `DealActionState` (см.
`docs/rules/external-status-resolution.md`,
`docs/models/mapping/AlgoOrder.md`).

Обновляет только `AlgoOrder`; `REFRESH_ORDER` / `REFRESH_PENDING_ORDERS`
/ `REFRESH_ORDER_HISTORY` / `REFRESH_FILLS` / `REFRESH_POSITION` выбирает
FSM / `DealOrchestratorJob`. Общая семантика `REFRESH_*` —
`docs/components/ServiceCommandExecutor.md`.
