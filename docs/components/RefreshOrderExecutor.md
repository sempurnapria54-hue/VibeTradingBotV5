# RefreshOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_ORDER` (компонент-executor): что делает, границы.

## Назначение

Получает `REFRESH_ORDER`. Загружает локальный order, получает актуальное
состояние с биржи, обновляет order и статусы исполнения, при
необходимости обновляет `DealActionState`. Резолвинг внешнего статуса —
`OrderExternalStatusResolver` (см.
`docs/rules/external-status-resolution.md`,
`docs/models/mapping/Order.md`).

Исполняет order-refresh-семейство: `REFRESH_ORDER` + recovery-варианты
`REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` (отдельных
executor-файлов не имеют — разные endpoint'ы того же executor'а, см.
`.claude/decisions/executor-payload-file-granularity.md`). Обновляет
только `Order`, сделку целиком не сопровождает; cross-entity refresh
(`REFRESH_FILLS`, `REFRESH_POSITION`) выбирает FSM / `DealOrchestratorJob`.
`ExternalNotFoundException` — только после полного order evidence-cycle
(см. `docs/models/mapping/Order.md`).
Общая семантика `REFRESH_*` — `docs/components/ServiceCommandExecutor.md`.
