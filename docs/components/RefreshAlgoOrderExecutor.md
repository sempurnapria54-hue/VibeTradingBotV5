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

Исполняет algo-refresh-семейство: `REFRESH_ALGO_ORDER` + recovery-варианты
`REFRESH_ALGO_ORDERS` / `REFRESH_ALGO_ORDER_HISTORY` (отдельных
executor-файлов не имеют — разные endpoint'ы того же executor'а, см.
`.claude/decisions/executor-payload-file-granularity.md`). Обновляет
только `AlgoOrder`; cross-entity refresh (`REFRESH_ORDER` / `REFRESH_FILLS`
/ `REFRESH_POSITION`) выбирает FSM / `DealOrchestratorJob`. Общая семантика
`REFRESH_*` — `docs/components/ServiceCommandExecutor.md`.
