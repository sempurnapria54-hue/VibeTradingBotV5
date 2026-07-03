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

## Рабочее плечо перед постановкой (set-leverage, INSTR-Q2)

Перед постановкой **открывающего** ордера executor inline-write'ит рабочее
плечо на биржу (`ensureLeverage`, прямо перед place-вызовом):

- **только для открывающих** — reduce-only/закрывающий ордер плечо не трогает
  (`positionReducingOnly` → пропуск: для reduce-only плечо бессмысленно);
- значение берётся из `Instrument.leverage`; `null` → пропуск;
- запись через `IntegrationService.setLeverage(instId, leverage)` (граница к
  бирже), **idempotent** (биржевой set-leverage идемпотентен: совпадает с уже
  выставленным → пустая операция);
- неуспешный ACK → `ExchangeIntegrationException`.

Это **не отдельная команда `SET_LEVERAGE`**: inline-write co-located с
place-вызовом — атомарно, непропускаемо, покрывает и наращивание позиции в
`MANAGING`. Тайминг/владелец и роль `Instrument.leverage`
(потолок/умолчание) — `docs/components/PrecheckHandler.md`,
`docs/decisions/per-trade-risk-policy.md`,
`docs/decisions/instrument-external-rules-materialization.md` (INSTR-Q2 закрыт).

## SubmitOrderCommandPayload

Только `orderId` (executor сам берёт `internalId` как `clOrdId`,
`externalId` если есть).
