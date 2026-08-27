# Position lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит `Position`, кто и при каких событиях их
меняет.

Структура модели и атрибуты — в `docs/models/domain/core/Position.md`.

## Кто управляет

Статусы `Position` меняет только `REFRESH_POSITION_COMMAND` flow:
`IntegrationService` отдаёт snapshot (или `null`), `PositionStatusResolver`
возвращает `status + closeReason` candidate, `RefreshPositionExecutor`
применяет результат. FSM напрямую `Position` не создаёт и не меняет.

`REFRESH_POSITION_COMMAND` — **двуногая** refresh-команда (evidence-cycle внутри
одной команды, H1/H3 `GAPS_CLOSE_7`): live `/account/positions` → при
not-found `/account/positions-history` по `posId`. Вторая нога статус не
меняет (его уже определил not-found первой ноги) — она **наполняет поля
положения закрытия** на строках эпизодов сделки, у которых их ещё нет
(`docs/models/domain/core/Position.md` §«Положение закрытия»,
§«Смена эпизода» ниже).

> Компоненты `PositionStatusResolver`, `RefreshPositionExecutor`,
> `ClosePositionExecutor` — command-подсистема (шаг 4),
> `docs/components/`; `AnomalyJob` / `DealOrchestratorJob` —
> orchestration/anomaly (шаги 6-8). Здесь — только статусная механика,
> которой владеет сама `Position`.

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
через `REFRESH_POSITION_COMMAND`; ACK от `CLOSE_POSITION_COMMAND` не runtime truth;
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

## Переходы (через `REFRESH_POSITION_COMMAND`)

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
- snapshot найден, живой `Position` у сделки нет (active Deal flow):
  создать `Position`, `dealId = Deal.id`, `status = ACTIVE`,
  `externalId = posId`, `direction = resolved`, заполнить `external*`
  поля. Штатный сценарий после исполнения entry order — и он же
  сценарий **второго эпизода** (§«Смена эпизода»).
- snapshot найден, живой `Position` есть **и `posId` совпадает**:
  `status = ACTIVE`, обновить `external*`, проверить, что `direction` не
  изменилась.
- snapshot найден, живой `Position` есть, **но `posId` другой** — смена
  эпизода, §ниже.
- snapshot не найден, живой `Position` есть: `status = CLOSED`,
  `closeReason = EXTERNAL_CLOSE` (если был null); **дальше — вторая нога**:
  запись positions-history по `Position.externalId` наполняет поля
  положения закрытия и `Deal.billsWindowEnd` (§«Кто управляет»). Запись не
  нашлась — поля остаются `null`, статус всё равно `CLOSED`; отсутствие
  факта — не отказ команды, добычу ретраит `REFRESH_DEAL_CONTEXT_ACTION`
  (узел 4 `DOCS_CHECK_8`, вариант (а)); тропа «неисчислимо» — аварийный
  контур (`docs/lifecycles/Deal.md` §«Терминальный контракт финализации»).
- snapshot не найден, `Position` нет: **новую `CLOSED` не создавать**;
  FSM/handler дальше анализирует `DealContext` и статус сделки. Вторая
  нога **не идёт**: адресовать запись нечем (`posId` не наблюдался).
  Что делать на этой тропе — открытая развилка H6/H7 `DOCS_CHECK_7`
  (адресация записи и допустимость поиска по одному инструменту);
  в `GAPS_CLOSE_7` не закрыта, предложение вынесено владельцу.

`direction` при обновлении active `Position` меняться не должен. Смена
направления live position — нарушение инварианта → error/safety-flow.
При создании `direction` сверяется с expected из `DealContext` / entry
action / entry order.

## Смена эпизода (многоэпизодная сделка)

Сделка многоэпизодна (`docs/decisions/multi-episode-deal.md`): позиция
может схлопнуться в ноль и открыться заново — новой ногой входа,
оставшейся живой в `MANAGING`. Биржа даёт новой позиции **новый
`posId`**, и это единственный наблюдаемый признак смены.

**Дискриминатор — `posId`, не размер.** `externalSize = 0` эпизод не
закрывает (§«Status vs live risk»), а «позиция снова ненулевая» без
смены `posId` — это тот же эпизод.

```text
live-нога вернула snapshot с posId ≠ Position.externalId живой строки:
  1) живая строка -> CLOSED, closeReason = EXTERNAL_CLOSE (если был null)
  2) создаётся новая строка эпизода: posId из snapshot, status = ACTIVE
  3) вторая нога добывает положение закрытия для КАЖДОЙ строки сделки,
     которая CLOSED и своего положения закрытия ещё не несёт
```

- **Шаг 3 — не «для закрывшейся сейчас», а для всех должников.**
  Предикат добычи (`status = CLOSED` **и** `externalRealizedProfit`
  пуст) делает ногу идемпотентной и покрывает эпизод, схлопнувшийся и
  переоткрывшийся **между тиками**: его строка создаётся из записи
  positions-history окна, а не из live-ответа.
- **Эпизод, не наблюдавшийся живым**, материализуется тем же
  механизмом, которым материализуется позиция, не наблюдённая вовсе:
  запись адресуется инструментом и окном сделки
  (`docs/models/domain/core/Position.md` §Инварианты), и **несколько
  записей окна — несколько эпизодов**, каждая своей строкой по своему
  `posId`.
- **Стратегия переоткрытие не объявила** (`StrategyDetail
  .positionReopenAllowed = false`) — второго эпизода не возникает по
  построению: наблюдение `externalSize -> 0` в `MANAGING` снимает живые
  входные ноги тем же порядком, что действует на выходе
  (`docs/rules/exit-teardown-order.md` §«Гейт в `MANAGING`»). Смена
  `posId` у такой сделки — аномалия, а не режим.

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
ещё нет -> следующий REFRESH_POSITION_COMMAND находит -> executor создаёт
Position, Position.dealId = Deal.id
```

Не anomaly, если есть active `Deal`, entry order и факты, объясняющие
появление позиции. Если на бирже active position, но нет active
`Deal`, объясняющего её, — зона `AnomalyJob` / safety-flow.

## Recovery после рестарта

Если приложение упало, entry order исполнился, позиция открылась и
закрылась по SL/TP/trailing, после рестарта локальной `Position`
может ещё не быть. Это не anomaly при active `Deal` и известном entry
order. Recovery-контур (`REFRESH_ORDER_COMMAND` → `REFRESH_POSITION_COMMAND` (null) →
`REFRESH_ALGO_ORDER_COMMAND`) — Deal-lifecycle/orchestration;
полный flow — `docs/processes/deal-management.md` /
`docs/lifecycles/Deal.md` (шаги 6-7). P&L сделки финализация собирает
не через fills (число — net из positions-history, разбивка — из bills;
`docs/decisions/result-profit-source.md`,
`docs/decisions/pnl-finalization-mechanics.md`).
Position-правило: локальную `CLOSED Position` можно не создавать, если
её ещё не было; `Deal` финализируется по собранным фактам. **Цена этого
правила названа** (`GAPS_CLOSE_7`; узел 1 `DOCS_CHECK_8`): без строки
`Position` положению закрытия негде приземлиться, а наблюдатель
`Deal.billsWindowEnd` (вторая нога) не срабатывает — число и привязка
bills на этой тропе **ждут** до исчерпания бюджета добычи (дальше —
ошибочная тропа + холд); см. развилку H6/H7 выше.

## Position not found vs Order/AlgoOrder

Для `Position` not found после успешного запроса по инструменту —
нормальный closed-on-exchange факт (`null` → `CLOSED` +
`EXTERNAL_CLOSE`). Это отличается от `Order` / `AlgoOrder`, где not
found после evidence-cycle может быть problem-flow, если финал нельзя
объяснить.
