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
not-found `/account/positions-history`. Вторая нога статус не
меняет (его уже определил not-found первой ноги) — она **наполняет поля
положения закрытия** на строках эпизодов сделки, у которых их ещё нет
(`docs/models/domain/core/Position.md` §«Положение закрытия»,
§«Смена эпизода» ниже).

**Ось адресации второй ноги — `posId`, а при его отсутствии инструмент
и окно сделки.** Нога 2 идёт при not-found ноги 1 **всегда**, в том
числе когда локальной строки `Position` нет вовсе: ограничение «только
при наблюдавшемся `posId`» снято (`docs/components/
RefreshPositionExecutor.md` §«Нога 2 идёт при not-found ноги 1 —
всегда»). Оно делало недостижимым число для сделки, чью позицию не
застали живой между тиками, — то есть на тропе быстрого стопа и
ликвидации.

**Верхняя граница окна двигается монотонно вперёд.** Нога 2 ставит
`Deal.billsWindowEnd = uTime` **последней** записи закрытия: окно
обязано накрывать движения всех эпизодов
(`docs/models/domain/aggregate/Deal.md` §«Верхняя граница монотонна, а
не write-once»).

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
  положения закрытия и двигает `Deal.billsWindowEnd` (§«Кто управляет»).
  Запись не
  нашлась — поля остаются `null`, статус всё равно `CLOSED`; отсутствие
  факта — не отказ команды, добычу ретраит `REFRESH_DEAL_CONTEXT_ACTION`
  (узел 4 `DOCS_CHECK_8`, вариант (а)); тропа «неисчислимо» — аварийный
  контур (`docs/lifecycles/Deal.md` §«Терминальный контракт финализации»).
- snapshot не найден, `Position` нет: **вторая нога идёт всё равно** —
  запись адресуется инструментом и окном сделки, и найденная запись
  **материализует** строку (`status = CLOSED`, поля положения закрытия,
  `posId` → `externalId` и `direction` из самой записи;
  `docs/components/RefreshPositionExecutor.md` §«Позиция, впервые
  увиденная уже закрытой»). Записей в окне несколько — эпизодов
  несколько, каждая своей строкой. Запись не нашлась — строка не
  создаётся, FSM/handler дальше анализирует `DealContext` и статус
  сделки.
  - **Развилка H6/H7 `DOCS_CHECK_7` закрыта** (`docs/decisions/
    multi-episode-deal.md` §«Что это закрывает попутно»): ось
    адресации названа, поиск по инструменту и окну разрешён, инвариант
    слота («одна активная сделка на инструмент») держит однозначность.
  - **Известное ограничение.** Тропа адресуема только когда вход дошёл
    до биржи: у позиции вокруг чужого риска отправленной ноги входа
    нет, значит нет и операнда нижней границы
    (`docs/components/RefreshPositionExecutor.md` §«Известное
    ограничение»).

`direction` при обновлении active `Position` меняться не должен. Смена
направления live position — нарушение инварианта → error/safety-flow.
При создании `direction` сверяется с expected из `DealContext` / entry
action / entry order.

## Смена эпизода (многоэпизодная сделка)

Сделка многоэпизодна (`docs/decisions/multi-episode-deal.md`): позиция
может схлопнуться в ноль и открыться заново — новой ногой входа,
оставшейся живой в `MANAGING`. Биржа даёт новой позиции **новый
`posId`**, и это единственный наблюдаемый признак смены.

**«Новый `posId`» — предположение до рантайм-верификации** (B11
`DOCS_CHECK_20`). Офдок говорит только, что `posId` истекает через ~30
дней после полного закрытия; переиспользуется ли он **внутри** окна, он
не утверждает (`docs/integrations/okx/contracts/position.md`
§«Идентификация записи»). Проверка — `.claude/tests/source-api/okx/plan.md`
§AG1.9. Если источник `posId` переиспользует, меняются **оба**: этот
дискриминатор и ключ `uk_position_deal_external`. Посылка
**зарегистрирована предусловием `CODE`** (п. 15,
`docs/decisions/pnl-finalization-mechanics.md` §«Предусловия `CODE`
шага 7») — до B6 `DOCS_CHECK_21` кейс её покрывал, а реестр не
регистрировал, и клейм «реестр сквозной» был ложен.

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
  построению: `ManagingHandler` по наблюдению `externalSize -> 0`
  снимает живые входные ноги тем же порядком, что действует на выходе
  (`docs/components/ManagingHandler.md` §«Входные проверки»,
  `docs/rules/exit-teardown-order.md` §«Гейт в `MANAGING`»). Смена
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
Position-правило: локальная `CLOSED Position` **материализуется ногой 2
из записи positions-history**, если её ещё не было; `Deal`
финализируется по собранным фактам тем же проходом
(`docs/components/RefreshPositionExecutor.md` §«Позиция, впервые
увиденная уже закрытой»). Прежняя редакция («новую `CLOSED` не
создавать, вторая нога не идёт, число и привязка bills ждут») **снята**
вместе с ограничением ноги 2 по наблюдавшемуся `posId`: она делала
число недостижимым ровно на левом хвосте распределения — тропе быстрого
стопа и ликвидации.

## Position not found vs Order/AlgoOrder

Для `Position` not found после успешного запроса по инструменту —
нормальный closed-on-exchange факт (`null` → `CLOSED` +
`EXTERNAL_CLOSE`). Это отличается от `Order` / `AlgoOrder`, где not
found после evidence-cycle может быть problem-flow, если финал нельзя
объяснить.
