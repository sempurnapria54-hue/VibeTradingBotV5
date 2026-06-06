# CancelOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CANCEL_ORDER` (компонент-executor): что делает.

## Назначение

Получает `CANCEL_ORDER`. Загружает локальный order по `payload.orderId`,
берёт cancel reason из payload / current flow, отправляет cancel на
биржу, сохраняет ACK / технический результат; факт отмены подтверждается
refresh/search/history. Не переводит order в `CANCELED` по ACK;
`closeReason` не перетирается, если уже установлен.

После рестарта pending cancel в очереди не восстанавливается: FSM заново
собирает факты и решает по ним (см. `docs/rules/command-lifecycle.md`).
ACK не runtime truth (см. `docs/rules/ack-not-runtime-truth.md`); общая
семантика `CANCEL_*` — `docs/components/ServiceCommandExecutor.md`.
