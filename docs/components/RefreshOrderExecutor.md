# RefreshOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_ORDER` (компонент-executor): что делает, границы.

## Назначение

Получает `REFRESH_ORDER`. Загружает локальный order, получает актуальное
состояние с биржи, обновляет order и статусы исполнения, при
необходимости обновляет `DealActionState`. Резолвинг внешнего статуса —
`OrderExternalStatusResolver` (см.
`docs/rules/external-status-resolution.md`,
`docs/client/okx/rules/okx-order-mapping.md`).

Обновляет только `Order`; не сопровождает сделку целиком — другие
refresh-команды (`REFRESH_FILLS`, `REFRESH_POSITION`,
`REFRESH_PENDING_ORDERS`, `REFRESH_ORDER_HISTORY`) выбирает FSM /
`DealOrchestratorJob`. `ExternalNotFoundException` — только после полного
order evidence-cycle (см. `docs/client/okx/rules/okx-order-mapping.md`).
Общая семантика `REFRESH_*` — `docs/components/ServiceCommandExecutor.md`.

> Гранулярность executor-файлов под вопросом — CMD-Q1.
