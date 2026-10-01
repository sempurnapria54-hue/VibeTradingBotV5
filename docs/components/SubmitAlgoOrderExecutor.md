# SubmitAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `SUBMIT_ALGO_ORDER_COMMAND`.

## Назначение

Получает `SUBMIT_ALGO_ORDER_COMMAND`. Загружает локальный `AlgoOrder`; если
`externalId` есть — команда выполнена или требует refresh; если пуст —
ищет на бирже по `algoClOrdId = algoOrder.internalId`. Найден → обновляет
локальное состояние — только идентификатор и факт отправки: факты записи
и сверку её эха с нашей строкой несёт следующая добыча
(`docs/components/RefreshAlgoOrderExecutor.md`); не найден → отправляет на
биржу. Обновляет `DealActionState.status`.

**Терминальная заявка не отправляется** — тот же довод, что у обычной
(`docs/components/SubmitOrderExecutor.md`): добыча, исчерпавшая цикл у
неотправленной заявки, снимает её как не дошедшую до площадки
(`docs/lifecycles/AlgoOrder.md`), и строка исполнения доводится фактом
терминала.

ACK не runtime truth (см. `docs/rules/ack-not-runtime-truth.md`); общая
семантика `SUBMIT_*` — `docs/components/ServiceCommandExecutor.md`.

## SubmitAlgoOrderCommandPayload

Только `algoOrderId` (executor сам берёт internal/client/external id,
инструмент, параметры).
