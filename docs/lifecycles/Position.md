# Position lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит `Position`, кто и при каких событиях их
меняет.

Структура модели и атрибуты — в `docs/models/domain/core/Position.md`.

## Кто управляет

Статусы `Position` меняет только `REFRESH_POSITION` flow:
`IntegrationService` отдаёт snapshot (или `null`), `PositionStatusResolver`
возвращает `status + closeReason` candidate, `RefreshPositionExecutor`
применяет результат. FSM напрямую `Position` не создаёт и не меняет.

> Компоненты `PositionStatusResolver`, `RefreshPositionExecutor`,
> `ClosePositionExecutor`, `AnomalyJob`, `DealOrchestratorJob` —
> часть cross-cutting command/orchestration-подсистемы, мигрируются
> отдельно (форвард-заметки — в `.claude/work/questions/tasks/position.md`).
> Здесь — только статусная механика, которой владеет сама `Position`.

## Статусы

- **`ACTIVE`** — позиция существует на бирже и сопровождается
  системой. Сам по себе live market risk не гарантирует.
- **`CLOSED`** — позиции на бирже нет.
- **`ERROR`** — problem state; не является normal closed. Exchange
  facts нельзя безопасно интерпретировать, либо adapter обнаружил
  нарушение exchange invariant.

Промежуточные статусы (`CREATED`/`PENDING`/`OPENING`/`CLOSING`/
`PARTIALLY_CLOSED`) не вводятся: `Position` не создаётся локально до
биржи, появляется как результат исполнения `Order`, материализуется
через `REFRESH_POSITION`; ACK от `CLOSE_POSITION` не runtime truth;
частичное уменьшение — это `ACTIVE` с обновлённым `externalSize`, не
отдельный статус.

## Status vs live risk

```text
ACTIVE                       -> позиция есть на бирже / сопровождается
ACTIVE && externalSize > 0   -> есть live market risk
ACTIVE && externalSize == 0  -> биржа ещё возвращает позицию, live
                                risk нет (cleanup / anomaly / retry)
CLOSED                       -> позиции на бирже нет
ERROR                        -> problem state, не normal closed
```

Формула live risk — в `docs/models/domain/core/Position.md`.

## Переходы (через `REFRESH_POSITION`)

`PositionStatusResolver`:

```text
snapshot == null  -> status = CLOSED,  closeReason = EXTERNAL_CLOSE
snapshot != null  -> status = ACTIVE,  closeReason = null
snapshot.externalSize == 0 -> status = ACTIVE (не CLOSED, пока биржа
                              возвращает snapshot), live risk = false
```

`RefreshPositionExecutor` применяет результат:

- **status** из resolver применяется всегда.
- **closeReason** заполняется только если текущий
  `Position.closeReason == null` (write-once: уже заполненный не
  перетирается).
- snapshot найден, локальной `Position` нет (active Deal flow):
  создать `Position`, `dealId = Deal.id`, `status = ACTIVE`,
  `direction = resolved`, заполнить `external*` поля. Штатный сценарий
  после исполнения entry order.
- snapshot найден, `Position` есть: `status = ACTIVE`, обновить
  `external*`, проверить, что `direction` не изменилась.
- snapshot не найден, `Position` есть: `status = CLOSED`,
  `closeReason = EXTERNAL_CLOSE` (если был null).
- snapshot не найден, `Position` нет: **новую `CLOSED` не создавать**;
  FSM/handler дальше анализирует `DealContext` и статус сделки.

`direction` при обновлении active `Position` меняться не должен. Смена
направления live position — нарушение инварианта → error/safety-flow.
При создании `direction` сверяется с expected из `DealContext` / entry
action / entry order.

## ERROR-переход (exchange invariant violation)

Если response нарушает ожидаемый invariant (`posSide != net`,
`mgnMode != isolated`, `instId != expected`, `lever > allowed`,
direction нельзя безопасно определить или ≠ expected):

```text
Position.status = ERROR
Position.closeReason = EXCHANGE_INVARIANT_VIOLATION
Deal -> ERROR / safety-flow
```

Unknown / невозможно распарсить response — controlled exception,
`Deal -> ERROR / safety-flow`.

## Легитимное окно появления позиции

`Position` — единственная runtime-сущность, которая может сначала
появиться на бирже, а потом локально в БД:

```text
entry Order исполнен -> биржа создала позицию -> локальной Position
ещё нет -> следующий REFRESH_POSITION находит -> executor создаёт
Position, Position.dealId = Deal.id
```

Не anomaly, если есть active `Deal`, entry order и факты, объясняющие
появление позиции. Если на бирже active position, но нет active
`Deal`, объясняющего её, — зона `AnomalyJob` / safety-flow.

## Recovery после рестарта

Если приложение упало, entry order исполнился, позиция открылась и
закрылась по SL/TP/trailing, после рестарта локальной `Position`
может ещё не быть. Это не anomaly при active `Deal` и известном entry
order. Recovery-контур (`REFRESH_ORDER`/`REFRESH_ORDER_HISTORY` →
`REFRESH_POSITION` (null) → `REFRESH_ALGO_ORDER_HISTORY` →
`REFRESH_FILLS`) — Deal-lifecycle/orchestration; полный flow —
форвард-заметка для миграции Deal (`.claude/work/questions/tasks/position.md`).
Position-правило: локальную `CLOSED Position` можно не создавать, если
её ещё не было; `Deal` финализируется по собранным фактам.

## Position not found vs Order/AlgoOrder

Для `Position` not found после успешного запроса по инструменту —
нормальный closed-on-exchange факт (`null` → `CLOSED` +
`EXTERNAL_CLOSE`). Это отличается от `Order` / `AlgoOrder`, где not
found после evidence-cycle может быть problem-flow, если финал нельзя
объяснить.
