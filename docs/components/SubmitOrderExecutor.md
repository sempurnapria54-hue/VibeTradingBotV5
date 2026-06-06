# SubmitOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `SUBMIT_ORDER` (компонент-executor): что делает,
recoverability.

## Назначение

Получает `SUBMIT_ORDER`. Загружает локальный `Order`; если `externalId`
есть — команда выполнена или требует refresh; если пуст — ищет order на
бирже по `clOrdId = order.internalId`. Найден → обновляет локальный order
из snapshot; не найден → отправляет на биржу. Обновляет
`DealActionState.status`.

Recoverability: если приложение упало после отправки, но до сохранения
`externalId`, следующий submit найдёт order по client id и восстановит
состояние. ACK не runtime truth (см.
`docs/rules/ack-not-runtime-truth.md`); общая семантика `SUBMIT_*` —
`docs/components/ServiceCommandExecutor.md`.
