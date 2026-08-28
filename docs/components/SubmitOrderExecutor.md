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

## Нижняя граница окна линковки bills — единственный писатель

**Этот executor — единственный писатель `Deal.billsWindowBegin`**. Условная развилка («штатная ветка — live-нога `REFRESH_POSITION_COMMAND`,
ветка дефолта — этот executor») **снята**: писатель один на всех тропах,
безусловно.

- **Что пишется:** `externalCreatedAt` **первого отправленного `Order`**
  сделки — **всегда** при постановке, той же транзакцией, что и обновление
  `Order`/`DealActionState`.
- **Write-once — условным `UPDATE`** (`where bills_window_begin is null`):
  уже заполненная нижняя граница не перетирается последующими
  постановками. Механизм — `docs/models/domain/aggregate/Deal.md` (строка `deals` существует раньше значения, поэтому
  `updatable = false` неприменим).
- **Почему окно шире, но безопасно:** лишних движений оно не захватывает —
  активная сделка на инструмент одна, тот же инвариант слота, что держит
  верхнюю границу (`docs/models/domain/other/DealCashFlow.md`).
- **Известное ограничение сохраняется, но новым не становится.** У
  позиции, возникшей вокруг **чужого** риска (создана вне приложения),
  отправленной ноги входа нет — значит нет и операнда границы. Та же
  подтропа уже не адресуема и по временной оси запроса
  positions-history (`docs/components/RefreshPositionExecutor.md`), то есть выбор писателя её достижимость не
  меняет.
- **Дом поля не меняется** — оно остаётся собственным полем `Deal`
  (`docs/models/domain/aggregate/Deal.md`).

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
`docs/rules/risk-policy.md`,
`docs/models/domain/other/InstrumentExternalRules.md` (INSTR-Q2 закрыт).

## SubmitOrderCommandPayload

Только `orderId` (executor сам берёт `internalId` как `clOrdId`,
`externalId` если есть).
