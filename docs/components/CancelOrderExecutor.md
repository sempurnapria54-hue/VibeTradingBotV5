# CancelOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CANCEL_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CANCEL_ORDER_COMMAND`. Загружает локальный order по `payload.orderId`,
берёт cancel reason из payload / current flow, отправляет cancel на
биржу, сохраняет ACK / технический результат; факт отмены подтверждается
refresh/search/history. Не переводит order в `CANCELED` по ACK;
`closeReason` не перетирается, если уже установлен.

**Суммы риска сделки пересчитываются, когда отменяется нога входа** (H2/H3
`DOCS_CHECK_16`, решения пользователя). Отмена меняет состав слагаемых:
**всякая снятая** нога входа выходит из **заявленной** суммы
(`Deal.plannedRiskAmount` берёт только живые и исполнившиеся ноги —
`docs/models/domain/aggregate/Deal.md` §«Предикат отбора слагаемых»;
замещение — частный случай, отдельного операнда `closeReason` предикат не
читает), а её исполненная доля остаётся во **взятой**
(`Deal.incurredRiskAmount`). **Все три числа риска** — заявленный, взятый
на входе и текущий — пересчитываются **целиком** той же транзакцией, в
которой проставлен терминальный `closeReason` ноги входа. Для ноги
`Type.REDUCE_ONLY` числа не трогаются: планового риска у неё нет, а её
отмена размер позиции не меняет.

После рестарта pending cancel в очереди не восстанавливается: FSM заново
собирает факты и решает по ним (см. `docs/rules/command-lifecycle.md`).
ACK не runtime truth (см. `docs/rules/ack-not-runtime-truth.md`); общая
семантика `CANCEL_*` — `docs/components/ServiceCommandExecutor.md`.

## CancelOrderCommandPayload

`orderId`, `cancelReason` (`CancelReason`).
