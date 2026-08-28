# RefreshPositionExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_POSITION_COMMAND` (компонент-executor): что делает,
политика null/externalSize, evidence-cycle live → positions-history.

## Назначение

Получает `REFRESH_POSITION_COMMAND`. Берёт `Exchange`/`Instrument` из
`DealContext` / command context, вызывает `IntegrationService` для текущей
позиции по инструменту, получает `PositionExternalSnapshot` или `null`,
прогоняет через `PositionStatusResolver`, применяет `status`, заполняет
`closeReason candidate` только если текущий `== null`. Для OKX live-нога —
один логический запрос `GET /account/positions` по instType+instId (см.
`docs/models/mapping/Position.md`).

## Писатель `Order.positionId`

**Той же транзакцией, в которой эпизод материализован или наблюдён,
executor проставляет `orders.position_id`** всем ногам сделки с непустым
`accumulated_fill_size` и пустым `position_id`; значение — `id` живого
эпизода, **write-once** (guard `where position_id is null`). Разбор,
довод и пустые ветки — место истины
`docs/models/domain/core/Order.md`, здесь не
пересказываются.

## Evidence-cycle: live → positions-history

**Обход внутри одной команды**, по той же модели,
которой `REFRESH_ORDER_COMMAND` эскалирует live → pending → history
(`docs/rules/command-lifecycle.md`;
`docs/rules/command-lifecycle.md` — «Атомарность не означает
"один HTTP-запрос"»):

```text
1) GET /account/positions            (live)
   найдено, posId == живой строки -> ACTIVE, обновить external-поля -> стоп
                    (Deal.billsWindowBegin эта нога НЕ пишет — писатель один,
                     SubmitOrderExecutor; H9 DOCS_CHECK_16)
   найдено, posId ДРУГОЙ          -> смена эпизода: живую строку -> CLOSED,
                    завести строку нового эпизода (ACTIVE) -> нога 2
   не найдено                     -> живую строку -> CLOSED -> нога 2
2) GET /account/positions-history    (положения закрытия эпизодов)
   для КАЖДОЙ строки сделки, которая CLOSED и своего положения закрытия
   ещё не несёт:
   найдено       -> наполнить поля на своей строке
                    + Deal.billsWindowEnd = uTime ПОСЛЕДНЕЙ записи,
                      МОНОТОННО ВПЕРЁД (одной транзакцией)
   не найдено    -> поля и billsWindowEnd остаются null -> ЗВЕНО добычи не
                    завершается, число ждёт (ретрай добычи), статус CLOSED
                    (линковка bills при этом идёт — по подвижной границе)
```

**Нога 2 адресует эпизоды, а не одну позицию** (многоэпизодная сделка,
`docs/models/domain/aggregate/Deal.md`): её предикат — «строка `CLOSED`
без добытого положения закрытия», поэтому она идемпотентна и покрывает
эпизод, схлопнувшийся и переоткрывшийся **между тиками** (такой эпизод
живым не наблюдался, и его строка заводится **из записи окна**). Верхняя
граница окна линковки — `uTime` **последней** записи: окно обязано
накрывать движения всех эпизодов.

- **Терминал команды нога 2 не выносит.** Отсутствие записи закрытия —
  не `MISSING_AFTER_REFRESH`: статус позиции уже определён ногой 1, а
  недобытый факт ретраится бюджетом `REFRESH_DEAL_CONTEXT_ACTION`. Этим `REFRESH_POSITION_COMMAND` отличается от
  `REFRESH_ORDER_COMMAND`, где исчерпанный цикл и есть основание терминала.
- **Контракт добытой записи проверяется на границе, не финализатором**. Обязательные поля записи
  закрытия (`realizedPnl`, `ccy`, `type` внутри `1..6`, четыре правых
  операнда пар сверки, резолвимый `direction`) валидируются при разборе
  ответа в `IntegrationService`; нарушение — `ExternalInvariantViolationException`
  (`docs/models/mapping/PositionCloseResult.md`). Для этой команды следствие прямое: исключение
  доходит до учёта отказов, строка исполнения переводится в `FAILED`, и
  исключение **пробрасывается** — оркестратор поднимает полный биржевой
  холд (`docs/components/ServiceCommandExecutor.md`).
  Это **не** противоречит пункту выше: «терминал команды нога 2 не
  выносит» про **отсутствие** записи, а здесь запись есть и нарушает
  контракт.
- **Отдельной команды `REFRESH_POSITIONS_HISTORY` нет** — сущность одна
  (`Position`), refresh-набор держит по одной команде на сущность
  (`docs/components/models/ServiceCommand.md`).
- Идемпотентность — обычная командная: повторный проход перечитывает обе
  ноги и приводит поля к состоянию биржи.

Нормализация ответа второй ноги —
`docs/models/mapping/PositionCloseResult.md`; контракт эндпоинта —
`docs/integrations/okx/contracts/position.md`.

## Политика результата

```text
snapshot == null -> позиции на бирже нет -> Position.status = CLOSED
snapshot != null -> позиция есть         -> Position.status = ACTIVE
```

- snapshot найден → обновляет external-поля `Position`;
- snapshot не найден → существующую `Position` → `CLOSED` + нога 2
  (положение закрытия);
- локальной `Position` нет, snapshot найден в active Deal flow → создаёт
  `Position` и привязывает к `Deal`;

`externalSize == 0` при `ACTIVE` — exchange record есть, live risk нет
(cleanup/anomaly/retry). Live risk: `status == ACTIVE && externalSize > 0`
(см. `docs/models/domain/core/Position.md`). При обычном запуске
`requestedCloseReason` не получает. Общая семантика `REFRESH_*` —
`docs/components/ServiceCommandExecutor.md`.
