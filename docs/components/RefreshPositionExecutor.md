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

## Evidence-cycle: live → positions-history

**Обход внутри одной команды** (H1/H3, `GAPS_CLOSE_7`), по той же модели,
которой `REFRESH_ORDER_COMMAND` эскалирует live → pending → history
(`docs/decisions/refresh-evidence-cycle-ownership.md`;
`docs/rules/command-lifecycle.md` §«Команды атомарны — на уровне команды,
не HTTP-запроса»):

```text
1) GET /account/positions            (live)
   найдено       -> Position.status = ACTIVE, обновить external-поля;
                    первое наблюдение позиции -> Deal.billsWindowBegin = cTime -> стоп
   не найдено    -> Position.status = CLOSED -> нога 2
2) GET /account/positions-history    (положение закрытия, по posId)
   найдено       -> наполнить поля §«Положение закрытия» на той же Position
                    + Deal.billsWindowEnd = uTime записи (одной транзакцией)
   не найдено    -> поля и billsWindowEnd остаются null -> привязка bills и
                    число ждут (ретрай добычи), статус CLOSED
```

- **Нога 2 идёт только при not-found ноги 1** и только когда локальная
  `Position` есть (иначе `posId` не наблюдался — развилка H6/H7
  `DOCS_CHECK_7`, в `GAPS_CLOSE_7` не закрыта).
- **Окно линковки bills пишет наблюдатель** (узел 1 `DOCS_CHECK_8`):
  live-нога при первом наблюдении позиции — `Deal.billsWindowBegin`
  (write-once, `cTime`); нога 2 при приземлении записи закрытия —
  `Deal.billsWindowEnd` (`uTime` записи, той же транзакцией, что поля
  положения закрытия). Прежняя реконструкция окна из
  `Position.externalModifiedAt` снята — колонку писали обе ноги, предикат
  добытости был невыразим.
- **Терминал команды нога 2 не выносит.** Отсутствие записи закрытия —
  не `MISSING_AFTER_REFRESH`: статус позиции уже определён ногой 1, а
  недобытый факт ретраится бюджетом `REFRESH_DEAL_CONTEXT_ACTION`
  (`docs/components/SystemActionExecutor.md`; узел 4 `DOCS_CHECK_8`,
  вариант (а)). Этим `REFRESH_POSITION_COMMAND` отличается от
  `REFRESH_ORDER_COMMAND`, где исчерпанный цикл и есть основание терминала.
- **Отдельной команды `REFRESH_POSITIONS_HISTORY` нет** — сущность одна
  (`Position`), refresh-набор держит по одной команде на сущность
  (`docs/components/models/ServiceCommand.md`).
- Идемпотентность — обычная командная: повторный проход перечитывает обе
  ноги и приводит поля к состоянию биржи.

Нормализация ответа второй ноги —
`docs/models/mapping/PositionCloseResult.md`; контракт эндпоинта —
`docs/integrations/okx/contracts/position.md` §«История закрытых позиций».

## Политика результата

```text
snapshot == null -> позиции на бирже нет -> Position.status = CLOSED
snapshot != null -> позиция есть         -> Position.status = ACTIVE
```

- snapshot найден → обновляет external-поля `Position`;
- snapshot не найден → существующую `Position` → `CLOSED` + нога 2
  (положение закрытия); новой `CLOSED` не создаёт;
- локальной `Position` нет, snapshot найден в active Deal flow → создаёт
  `Position` и привязывает к `Deal`.

`externalSize == 0` при `ACTIVE` — exchange record есть, live risk нет
(cleanup/anomaly/retry). Live risk: `status == ACTIVE && externalSize > 0`
(см. `docs/models/domain/core/Position.md`). При обычном запуске
`requestedCloseReason` не получает. Общая семантика `REFRESH_*` —
`docs/components/ServiceCommandExecutor.md`.
