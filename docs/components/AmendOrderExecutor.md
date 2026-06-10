# AmendOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `AMEND_ORDER` (компонент-executor): что делает.

## Назначение

Получает `AMEND_ORDER`. Загружает локальный order по `payload.orderId`,
отправляет amend на биржу, сохраняет ACK / технический результат для
диагностики; факт новых параметров подтверждается refresh-командой. Не
решает, хорошая ли новая цена.

ACK не runtime truth (см. `docs/rules/ack-not-runtime-truth.md`); общая
семантика `AMEND_*` — `docs/components/ServiceCommandExecutor.md`.

## AmendOrderCommandPayload

`orderId`, `newPrice`, `newSizeContracts`, `cancelOnFail` (опасная
настройка, задаётся явно execution policy/стратегией). External/client id
не передаются — executor берёт из order.
