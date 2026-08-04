# SubmitOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `SUBMIT_ORDER_COMMAND` (компонент-executor): что делает,
recoverability.

## Назначение

Получает `SUBMIT_ORDER_COMMAND`. Загружает локальный `Order`; если `externalId`
есть — команда выполнена или требует refresh; если пуст — ищет order на
бирже по `clOrdId = order.internalId`. Найден → обновляет локальный order
из snapshot; не найден → отправляет на биржу. Обновляет
`DealActionState.status`.

Recoverability: если приложение упало после отправки, но до сохранения
`externalId`, следующий submit найдёт order по client id и восстановит
состояние. ACK не runtime truth (см.
`docs/rules/ack-not-runtime-truth.md`); общая семантика `SUBMIT_*` —
`docs/components/ServiceCommandExecutor.md`.

## Нижняя граница окна линковки bills — условный писатель (H27 `DOCS_CHECK_10`)

**Ветка условная и зависит от рантайм-ответа §AG1.5** (сравнение
`ts(entry-fee)` с `cTime(position)`,
`.claude/tests/source-api/okx/plan.md`). Записана здесь **превентивно**,
чтобы у зафиксированного заранее дефолта был дом: сам дефолт объявлен в
`docs/models/domain/other/DealCashFlow.md` §«Нижняя граница под
наблюдением», а грепом по `docs/` `billsWindowBegin` не встречался у этого
executor'а **ни разу** — предполагаемый писатель дома не имел.

- **Штатная ветка (ответ AG1.5 положительный — entry-fee не раньше
  `cTime`):** `Deal.billsWindowBegin` пишет live-нога
  `REFRESH_POSITION_COMMAND` (`cTime` позиции при её материализации);
  этот executor поля не касается.
- **Ветка дефолта (ответ отрицательный — entry-fee штампуется раньше
  `cTime`):** `Deal.billsWindowBegin` пишет **этот executor** —
  `externalCreatedAt` **первого отправленного `Order`** сделки, **всегда**
  (а не только при отсутствии позиции), той же транзакцией, что и
  обновление `Order`/`DealActionState`. Write-once: уже заполненная нижняя
  граница не перетирается последующими постановками.
- **Почему окно шире, но безопасно:** лишних движений оно не захватывает —
  активная сделка на инструмент одна, тот же инвариант слота, что держит
  верхнюю границу (`docs/models/domain/other/DealCashFlow.md` §«Линковка к
  `Deal`»).
- **Дом поля не меняется ни в одной ветке** — оно остаётся собственным
  полем `Deal` (`docs/models/domain/aggregate/Deal.md` §«Окно линковки
  bills»); меняется только писатель.

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
