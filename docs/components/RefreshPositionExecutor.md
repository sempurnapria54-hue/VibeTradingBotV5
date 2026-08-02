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

- **Нога 2 идёт при not-found ноги 1 — всегда, а не только при наличии
  локальной `Position`.** Это прямое следствие принципа класса
  (`docs/rules/command-lifecycle.md` §Правило, «Принцип обхода»): факт не
  добыт ⇒ команда продолжает спрашивать. Прежняя редакция ограничивала
  ногу 2 наличием локальной `Position` (то есть наблюдавшимся `posId`) и
  тем самым делала недостижимым число для сделки, чью позицию не застали
  живой между тиками оркестратора — а это ровно тропа быстрого стопа и
  ликвидации, то есть **левый хвост** распределения (H9 `DOCS_CHECK_10`).
  Ограничение снято: недостаёт не ноги, а **оси адресации** записи.
  - **`posId` — не единственная ось.** Когда его нет, запись положения
    закрытия адресуется инструментом и окном сделки; однозначность держит
    тот же инвариант слота, на котором стоит линковка bills («одна активная
    сделка на инструмент»). Конкретные оси запроса, принимаемые источником,
    и его поведение при нескольких записях в окне — **хвост `integrator`**
    (сверка по контракту, `docs/integrations/okx/contracts/position.md`).
  - **Позиция, впервые увиденная уже закрытой, заводится и финализирует
    сделку тем же проходом.** Запись из истории материализует локальную
    `Position` (статус `CLOSED`, поля положения закрытия, `posId` из самой
    записи) и заполняет **обе** границы окна: `billsWindowBegin` из `cTime`
    записи, `billsWindowEnd` из её `uTime`. Ждать наблюдения «живой»
    незачем — его уже не будет. Это единственная тропа, где `Position`
    создаётся ногой 2; прочие доки описывают только путь «видели живой →
    потом увидели закрытой», и он остаётся основным.
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
  (положение закрытия);
- локальной `Position` нет, snapshot найден в active Deal flow → создаёт
  `Position` и привязывает к `Deal`;
- локальной `Position` нет и snapshot не найден → нога 2 по инструменту и
  окну сделки: найденная запись **создаёт** `Position` сразу в `CLOSED` с
  положением закрытия и обеими границами окна (H9 `DOCS_CHECK_10`); не
  найдена — факт не добыт, звено повторяется бюджетом действия.

`externalSize == 0` при `ACTIVE` — exchange record есть, live risk нет
(cleanup/anomaly/retry). Live risk: `status == ACTIVE && externalSize > 0`
(см. `docs/models/domain/core/Position.md`). При обычном запуске
`requestedCloseReason` не получает. Общая семантика `REFRESH_*` —
`docs/components/ServiceCommandExecutor.md`.
