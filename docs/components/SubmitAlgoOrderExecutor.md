# SubmitAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `SUBMIT_ALGO_ORDER` (компонент-executor): что делает.

## Назначение

Получает `SUBMIT_ALGO_ORDER`. Загружает локальный `AlgoOrder`; если
`externalId` есть — команда выполнена или требует refresh; если пуст —
ищет на бирже по `algoClOrdId = algoOrder.internalId`. Найден → обновляет
локальное состояние; не найден → отправляет на биржу. Обновляет
`DealActionState.status`.

ACK не runtime truth (см. `docs/rules/ack-not-runtime-truth.md`); общая
семантика `SUBMIT_*` — `docs/components/ServiceCommandExecutor.md`.

> Гранулярность executor-файлов под вопросом — CMD-Q1.
