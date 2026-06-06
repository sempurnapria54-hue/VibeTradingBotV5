# CreateAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ALGO_ORDER` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ALGO_ORDER`. Создаёт локальный `AlgoOrder` со статусом
`CREATED`, генерирует `internalId`, сохраняет рассчитанные
SL/TP/trailing-параметры, обновляет `DealActionState.target =
RuntimeTarget(ALGO_ORDER, algoOrderId)` и `DealActionState.status =
CREATED`. На биржу не ходит.

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` — DEAL-Q3 (`.claude/work/questions/open-questions.md`).
