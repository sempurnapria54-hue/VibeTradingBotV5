# CreateOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ORDER` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ORDER`. Создаёт локальный `Order` со статусом `CREATED`,
генерирует `internalId`, сохраняет рассчитанные параметры, создаёт
attached protection внутри order (если есть), обновляет
`DealActionState.target = RuntimeTarget(ORDER, orderId)` и
`DealActionState.status = CREATED` — всё одной транзакцией. На биржу не
ходит, цену не пересчитывает, условия не проверяет.

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` — DEAL-Q3 (`.claude/work/questions/open-questions.md`).

> Гранулярность executor-файлов под вопросом — CMD-Q1.
