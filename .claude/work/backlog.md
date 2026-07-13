# Backlog

## На какой вопрос отвечает этот файл

Что мы планируем сделать.

## Связь с роадмапом

Продуктовое движение по фазам и шагам ведётся отдельно —
`.claude/work/roadmap/roadmap.md` (главный) и
`.claude/work/roadmap/phase-N.md` (детальные). Когда шаг роадмапа
берётся в работу, через процесс
`.claude/processes/roadmap-step-execution.md` он порождает
конкретные задачи в этот backlog. Backlog шире роадмапа: помимо
шагов фаз сюда попадают cross-cutting миграции из архива,
методологические ревизии и пайплайн-задачи. Сверка существующих
пунктов backlog с шагами роадмапа («где мы сейчас по карте») —
отдельная будущая активность, пока не выполнена. Обоснование
связи — `.claude/decisions/product-roadmap-type.md`.

## Статус

Полные форвард-заметки пунктов миграций — в подпапках `history/`
(`tasks-<сущность>.md` / `tasks-<док>.md`); архивные доки в
`.claude-archive/` не удалены — источник для оставшихся миграций.
Закрытые пункты вычищены — их итоги в `history/` и `decisions/`
(нумерация оставшихся секций сохранена, с пропусками).

## Cross-cutting миграции

### 2. Resolver / mapper / checker компоненты — частично

**Мигрировано:** `OrderExternalStatusResolver`,
`AlgoOrderExternalStatusResolver`, `PositionStatusResolver` (+
`PositionStatusResolveResult` RVO), refresh/close executor'ы.
**Осталось (backlog):** `*Mapper` (`OrderMapper`, `PositionMapper`,
`AlgoOrderMapper`, `BalanceContainerMapper`), `BalanceFreshnessChecker`,
`OkxAlgoOrderTypeResolver`, `AttachedAlgoOrderStateResolver`.
**Форвард-заметки:** `2026-05-27-.../tasks-order.md` (ORD-Q2),
`tasks-position.md` (POS-Q2), `tasks-algo-order.md` (ALGO-Q2),
`tasks-balance.md` (BAL-Q6); `2026-05-28-.../tasks-статусы-торговых-сущностей.md`
(Mappers — Решения прохода 2).

### 6. Аудит и история исполнения; финализация PnL — частично

**Источник:** `.claude-archive/.../processes/Audit/Аудит и история
исполнения.md`;
`.claude-archive/2026-05-21/docs/deprecated/models/domain/old/TradeFill.md`,
`TradeFillsArchive.md`. Архивный док — рабочий каркас, **выведен из
миграции процессов** (`.claude/decisions/process-materialization-criterion.md`):
модели истории/timeline не спроектированы, ~30 подвопросов.
**Мигрировано:** только сквозное правило `docs/rules/audit-not-runtime-source.md`
(аудит не runtime-source FSM; `REFRESH_BALANCE` в истории; CLOSED vs
EMERGENCY_CLOSED различимы; partial exit объясним).
**Осталось:** модели `ServiceCommandExecutionHistory`, entity history,
timeline, snapshot-формат; ~30 подвопросов. Связано с DEAL-Q1/DEAL-Q2.
**Финализация PnL — закрыта отдельно** на `GAPS_CLOSE_1` шага 7
(`docs/decisions/result-profit-source.md`): число = net `realizedPnl` из
positions-history, breakdown = `DealCashFlow` (bills); **OKX-Q1 закрыт**
(persisted `TradeFill` не вводится), `REFRESH_FILLS` — кандидат на снятие.
Пофилловый аудит (`TradeFill`/`TradeFillsArchive`) — вне фазы 1.
**Форвард-заметки:** `2026-05-28-.../tasks-аудит-и-история-исполнения.md`
(§5/§8 подвопросы + Решения прохода 2); `2026-05-27-.../tasks-deal.md`
(DEAL-FW5, FW9), `tasks-balance.md` (BAL-Q7), `tasks-order.md` (ORD-Q7),
`tasks-position.md` (POS-Q5).

### 7. Anomaly / safety / kill-switch — частично

**Источник:** `docs/context/Аварийные executors …`, `KillSwitchService …`,
`After-snapshot …`. **Мигрировано:** `AnomalyReport` модель+lifecycle
(2026-05-27); `AnomalyJob`, `KillSwitchExecutor` (компоненты,
2026-05-28). **Осталось:** `ReconciliationJob` (в архиве только название —
live risk после terminal / позиция без active Deal), полный kill-switch
flow (`KillSwitchService`, kill-switch report, after-snapshot,
`Position.CloseReason = KILL_SWITCH`), `TradeRuleValidator`.
**Форвард-заметки:** `2026-05-28-.../tasks-жизненный-цикл-сделки.md`
(ReconciliationJob), `2026-05-27-.../tasks-position.md` (POS-Q7),
`tasks-deal.md` (DEAL-FW7),
`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` (ANOM-Q1…Q3).

### 8. Strategy: enforcement, валидатор, примеры — частично

**Мигрировано:** enforcement `Strategy.INACTIVE`/`DELETED` (блок новых /
graceful shutdown) — в `docs/lifecycles/Strategy.md` (+ проверка в
`EntryScannerJob`, реакция в `deal-management`/lifecycle Deal).
**Scope валидатора/материализации решён (STRAT-Q3, 2026-06-02):**
материализация «одной реализации» — через Strategy API
(`POST`/`GET`/`PUT`); валидатор расщеплён по линии create
(структурно-ссылочные пункты, 400) / activate (семантика действий,
422 — отложено); см.
`docs/decisions/strategy-materialization-and-validation.md`.
**Построено (`CODE` шага 2, DONE 2026-06-05):** Strategy API
(`StrategyController`: `POST`/`GET`/`PUT …/status`), create-валидатор
(`StrategyCreateRequestValidator`), полное дерево домен/persistence/
маппинг (Flyway `V2`), «одна реализация»
(`src/main/resources/strategy-examples/trend-following-ema.json`).
Детали — `history/2026-06-05-phase-1-step-2-strategy.md`.
**Осталось:**
- **runtime-прогон Strategy API** (хвост scope шага 2, PostgreSQL не
  был поднят): миграция `V2` → `POST` `trend-following-ema.json` →
  `GET` → `PUT`-переходы статуса. Выполнить при поднятом PostgreSQL;
  естественная точка — старт шага 3 (ему нужна живая стратегия).
- `Strategy API examples.md` (JSON-примеры, тип reference —
  воспроизводить ли как файл знания) — **остаётся открытым**.
**Форвард-заметки:** `2026-05-27-.../tasks-strategy.md` (STR-FW8, FW9,
FW10), `tasks-deal.md` (DEAL-FW8).

### 9. Exchange модель/lifecycle

**Источник:** `Exchange.HOLD`/`DISABLED` (правило —
`docs/rules/exchange-hold.md`, дополнено DISABLED 2026-05-28); статусы
Exchange/Instrument/Account. **Суть:** полная модель/lifecycle `Exchange`
(`HOLD` среди прочих), `Instrument`, `Account`. Сюда же — enforcement
`AnomalyReport.Severity` (CRITICAL → торговля по инструменту запрещена;
NON_CRITICAL → после kill-switch может быть разрешена; блокировка в
статусе инструмента) и standalone модель `Instrument` для market-data.

**Статус (GAPS_CLOSE_1, 2026-05-29):** материализованы минимальные
доменные модели под шаг 1 — `Instrument`
(`docs/models/domain/core/Instrument.md`) и `Exchange`
(`docs/models/domain/core/Exchange.md`), набор статусов обеих как в
классах.

**Статус (GAPS_CLOSE_2, 2026-05-30):** материализован онбординг-путь
lifecycle `Instrument` (`docs/lifecycles/Instrument.md`:
`CREATED → SYNC → CANDLES_LOADING → ACTIVE` + координация
`Instrument.Status` ↔ `CandleGroup.Status`); разграничение
`Instrument` ↔ `InstrumentExternalSnapshot` ↔ `InstrumentExternalRules`
для шага 1 **закрыто** (справочные поля живут только в транзиентном
снапшоте; `InstrumentExternalRules` отложена за пределы шага 1 и на
base/quote/settle больше не претендует — снят дубль Н1; см.
`docs/models/mapping/Instrument.md`). **Осталось:** полный lifecycle
`Exchange` (`HOLD`/`DISABLED` среди состояний), периферийные статусы
`Instrument` (`HOLD`, `ERROR`-recovery, повторный онбординг,
`CLOSED`), `Account`. Соотнесение rules-подсистемы со
снапшот-концепцией — INSTR-Q1 — **закрыто** на `GAPS_CLOSE_1` шага 5
(материализована JSONB-навесом на `Instrument`, без ренейма —
`docs/decisions/instrument-external-rules-materialization.md`).
Связанный открытый вопрос: ORCH-Q1 (владелец оркестрации онбординга
инструмента и загрузки свечей; ось владения `Instrument.Status`) —
`open-questions.md`.

**Форвард-заметки:** `2026-05-27-.../tasks-order.md` (ORD-Q5),
`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` (ANOM-Q4).

### Отложенные продуктовые вопросы (future)

- `linkedOrderExternalIds` — использование для fills/recovery/audit
  (`2026-05-27-.../tasks-algo-order.md` ALGO-Q6).
- Стандарт описания персистентности доменных моделей: формат и
  версионирование jsonb-снимков (`AnomalyReport.internalBefore/After` и
  др.). Шире одной модели.
  (`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` ANOM-Q5).

## Шаг «Безопасность» (Фаза 1, шаг 9) — форвард-материал

Материал, отложенный до шага «Безопасность» роадмапа
(`.claude/work/roadmap/phase-1.md`, шаг 9). Содержание шага
прорабатывается docs-first на самом шаге; здесь — что туда заведомо
идёт.

### S1. Конфигурация секретов через Vault — остаточный хардненинг

Vault-привязка секретов per-profile введена
(`.claude/rules/tech-radar.md`, строка spring-cloud-vault).

**Остаётся на шаг 9 (остаточный хардненинг):** политики/approle вместо
root/dev-token, ротация секретов, unseal/инициализация Vault не в dev-режиме,
вынос Vault-токена из run-config. Auth-инфраструктура (Spring Security) — S2.

### S2. Auth-инфраструктура

Spring Security, `@PreAuthorize`, `SecurityFilterChain`. На этом
шаге **реактивируется** фокус `security-review`
(`.claude/skills/security-review.md`), деактивированный на текущих
шагах.

## Форвард-материал шагов 5 / 6 / 7 / 8 (скан интегратора, прогоны 2-3, 2026-06-11)

Кандидаты со скана поверхности OKX против command-layer. Заметки
владельцам шагов, **не действия сейчас**; решения — на самих шагах.
Поле-уровневые контракты всех кандидатов готовы (см.
`docs/integrations/okx/coverage-manifest.md`, прогон 3).

### Шаг 5 (риск-преконтроль)

- **В-2 `order-precheck`** — серверная пре-валидация ордера
  (`contracts/order-precheck.md`). ⚠ Ограничение офдока: только
  режимы счёта MCM/PM (`acctLv` 3/4) — для Spot/Futures mode
  неприменим; не замена собственному преконтролю.
- **В-8 `mark-price` / `price-limit`** — дистанция ликвидации от
  mark price; границы допустимой цены ордера до постановки
  (`contracts/mark-price.md`, `contracts/price-limit.md`).
- **В-9 `account/config` + `set-leverage`/`set-position-mode`** —
  bootstrap-валидация посылок адаптера (`isolated`/`net`, плечо):
  старт-проверка `posMode`/`acctLv`/`perm` вместо принятия как
  данности (`contracts/account-config.md`); смежно INSTR-Q2 (кто и
  когда выставляет плечо).
- Рядом (без номера): `max-size`/`max-avail-size` — серверные
  потолки размера (`contracts/max-size.md`); `position-tiers` —
  `maxLever`/`maxSz` по тирам — форвард к **риску на биржу/портфель**
  (фаза 3; экспозиционные лимиты,
  `docs/decisions/per-trade-risk-policy.md`), не в валидаторе фазы 1
  (`contracts/position-tiers.md`).
- **Простой жёсткий предел плеча на сделку — отложен (ратифицировано
  2026-06-20).** На `GAPS_CLOSE_1` шага 5 принят вариант (а): в фазе 1
  отдельного потолка по плечу/экспозиции нет, плечо связано лимитом риска
  на сделку (`docs/decisions/per-trade-risk-policy.md`). При этом виден
  остаточный зазор: **узкий стоп → высокое плечо** при малом денежном
  убытке по стопу (риск на сделку умещается в лимит, но нотинал/плечо
  большие). Простой жёсткий кэп плеча на сделку этот зазор закрыл бы; в
  фазе 1 сознательно отложен. **Вернуться после наблюдений** (бэктест /
  живые прогоны), когда станет видно, материализуется ли зазор на
  практике.

### Шаг 6 (FSM)

- **Error-градация уровни 3-4: реактивный enforcement холдов — ✅ ПОСТРОЕН
  (CODE-делта холдов шага 6, 2026-06-23/24; D2-реактивный снят).** Детали —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-holds-design.md`
  и там же `phase-1-step-6-code.md` (§Реактивные холды L3/L4, §Доработка
  холд-дельты). **Остаётся форвардом (узко):** (1) **проактивная детекция**
  аномалий (`AnomalyJob`/`TradeRuleValidator` + численный порог «серия
  неудач» STRUCT-Q1) — **шаг 8**; (2) **точный локальный after через
  REFRESH_*** + **биржа-широкая L4-реконсиляция** (внешний слепок читает
  только instId триггера) — **шаг 8**; (3) **аудит ручного un-hold**
  (кем/когда) — **шаг 9 / п.9** (сама операция un-hold построена).
- **[MAJOR, perf] L4 `fireExchange` — небанженный O(сделок) burst под D-M1
  (ревью холд-дельты, 2026-06-24; порог актуальности — фаза 3).**
  `KillSwitchService.fireExchange` итерирует небанженный
  `DealDataService.findActiveByExchangeId` и на **каждую** сделку строит
  `DealContext` (~9 запросов) + kill-switch REST — под advisory-локом прохода
  (держит соединение). Распухает по **памяти / стоимости запроса** линейно по
  числу одновременных сделок биржи, без потолка; хуже принятого M4 (REST под
  локом на одну сделку). **Порог актуальности — фаза 3** (реальный объём
  одновременных сделок); в фазе 1 объём мал — не нагружено. **Починка (когда
  возьмём): перебор пачками (bounded) — полный свип сохранён**, режется не
  скорость, а ограниченный аппетит (память/стоимость). **`LIMIT` небезопасен**
  (отрезал бы несвёрнутый live risk); альтернатива — off-lock dispatch
  L4-teardown. Источник — `phase-1-step-6-code.md` §Доработка холд-дельты.
- **[MINOR, perf] Дублирующий тикер-REST в entry-скане (повторное ревью фикс-дельты
  M4, 2026-07-02).** После фикса M4 `MarketPhaseService.buildContext` тянет тикер
  (`MarketPriceDataService.getMarketPriceData`) для классификации фазы по каждому
  ACTIVE-инструменту без активной сделки за проход; квалифицированный инструмент
  затем тянет тот же тикер повторно в `MarketConditionContextFactory.build`. Итог:
  +1 тикер-REST на каждый скан-инструмент, 2 идентичных вызова на квалифицированный
  — линейно к числу инструментов, давление на rate-limit OKX. Функционально
  корректно, согласуется с stage-1 no-cache. **Починка:** тянуть `MarketPriceData`
  один раз в `EntryScannerJob.scanInstrument` и прокинуть в оба контекста (фазовый +
  condition), либо короткоживущий per-tick кэш в `MarketPriceDataService`.
  Кросс-коллаборатор: `MarketConditionContextFactory.build` шарится с FSM
  (`DealFsmSupport.conditionContext`). Источник — повторное ревью фикс-дельты (v63).
- **Унификация инфраструктуры джоб — форвард, горизонт фаза 3 (код-ревью заход 2,
  2026-07-01).** Ревью-замечания «общий родитель джоб» (п.4) и «единый механизм
  локов» (п.5) — доработка механизма замыкания под мультиинстанс/микросервисы,
  относится к фазе 3. Состав: абстрактный `ScheduledJob`-родитель (шаблон
  `enabled → lock → run`) + единый `JobLock`-интерфейс с двумя реализациями
  (`InProcessJobLock` поверх `JobExecutionGuard`, `AdvisoryJobLock` — БД
  advisory-замок) — единый API замыкания. **В фазе 1 оркестратор выровнен на
  `JobExecutionGuard`** (как остальные 5 джоб): единственное требование фазы 1 —
  внутрипроцессная не-реентрантность, один экземпляр монолита, межэкземплярной
  конкуренции нет. Прежний `OrchestratorPassLock` (БД advisory-замок, raw-JDBC)
  **удалён как преждевременный** (2026-07-01) — advisory несущий только с фазы 3
  (мультиинстанс), вернётся тогда (код в git-истории). Тем самым п.6 «SQL только
  в репозиториях» снят из фазы 1 (raw-SQL ушёл); при возврате в фазе 3 raw-JDBC
  advisory — ратифицированное исключение (замок держит **одно соединение** весь
  проход, JPA-репозиторий этого не гарантирует). **Спека D-M1 пересмотрена:**
  фаза 1 — in-process guard; БД-замок — форвард фазы 3 (см.
  `.claude/rules/tech-radar.md` строка Raw-JDBC → `hold`,
  `docs/components/DealOrchestratorJob.md` §Concurrency-guard).

### Шаг 7 (сделки и P&L)

**Концепция закрыта:** источник числа — `GAPS_CLOSE_1` (2026-07-03,
`docs/decisions/result-profit-source.md`); механика/носители стадий 1-2 —
`GAPS_CLOSE_2` (2026-07-04, `docs/decisions/pnl-finalization-mechanics.md`).
Ниже — **исполнительный хвост (CODE) + рантайм-верификация + форвард**, не выбор
пути. Гейт `CODE` — после чистого `DOCS_CHECK_3`.

- **CODE стадий 1-2 (доспецифицировано, писать код):** носители
  `OkxPositionsHistoryResponse` / `PositionCloseResultExternalSnapshot`
  (`mapping/PositionCloseResult.md`) + `DealCashFlow` (модель+mapping+таблица
  `deal_cash_flows`); команды/executor'ы `REFRESH_POSITIONS_HISTORY` /
  `REFRESH_BILLS` / `MARK_DEAL_EMERGENCY_CLOSED`; расчёт+запись `resultProfit` на
  `Deal` в `FinalizeDealExitExecutor` (N7); сверка bills↔net → `AnomalyReport`
  (N10); ставка `trade-fee` на `InstrumentExternalRules` + wiring сайзинга (N9);
  снятие `REFRESH_FILLS` (N12, доки закрыты — код-удаление на CODE).
- **N11 — рантайм-верификация инварианта агрегации positions-history** (гейтит
  корректность числа, **до CODE**): партиал-выходы одного `posId` → одна
  финализированная запись, `realizedPnl` кумулятивен. Test-план —
  `.claude/tests/source-api/okx/plan.md` §AG1.5 (⏳ PENDING; интегратор/тестер:
  фикстура-цепочка на demo). Если OKX не агрегирует — путь корректируется.
- **N13 — funding как holding-cost (форвард, фаза 2 / шаг ожидаемости):** в
  число funding учтён; на форварде издержка удержания без дома — разделяющий
  довод «комиссию в R, funding в post-cost expectancy» зафиксирован
  (`per-trade-risk-policy.md` §«Учёт комиссий»); завести форвард-дом на шаге
  ожидаемости/бэктеста. Scope (фаза 2 vs step-7-adjacent) — хвост пользователя.
- **Epsilon сверки bills↔net (N10)** — провизорная величина
  (max(0.01 settle-ccy, 0.5%·|net|)); подтверждение/калибровка — пользователь/бэктест.

### Шаг 8 (safety / AnomalyJob)

- **В-1 `cancel-all-after`** — dead-man's switch: серверная
  страховка на потерю связи **поверх** явного
  `EXECUTE_KILL_SWITCH`, не вместо него
  (`contracts/cancel-all-after.md`; heartbeat раз в секунду,
  timeOut 0|[10,120] с). Покрытие algo-ордеров CAA офдоком не
  специфицировано — уточнить на шаге.
- **Kill-switch: ретрай-до-закрытия + сверка реального состояния биржи
  (ANOM-Q2).** **Per-инструмент контур — ✅ ПОСТРОЕН**, **декларативный
  kill-switch (Scope A/B) — ✅ ОТКАЧЁН** (код-ревью заход 2, 2026-07-01):
  kill-switch — аварийный side-executor с bounded-ретраем и подтверждением
  flat, не команда и не действие стратегии. Семантика —
  `docs/components/KillSwitchExecutor.md`; детали захода —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-code.md`
  §Заход 2 разбора находок.
  **Остаётся форвардом:** (1) **AnomalyJob-путь** (проактивная детекция → зов
  executor'а) + общебиржевая **orphan-сверка** (сущности вне модели сделки) +
  перевод залипших L4-отчётов + порог «серия неудач» STRUCT-Q1 — **шаг 8**;
  (2) **PnL-финализация `EMERGENCY_CLOSED`** (остаток DEAL-Q2 закрыт G5:
  число = фактический realized net вкл. `liqPenalty`; расчёт — шаг 7).
  Связано с **ANOM-Q2**
  (`history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`).
- **FSM/action слоистость — decision + Stage 1 — ✅ ПОСТРОЕНЫ (2026-07-01).**
  Решение — `docs/decisions/fsm-execution-layering.md` (в т.ч. §Handler — 3
  метода); детали Stage 1 —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-code.md`
  §Заход 2 разбора находок. Stage 2/3-рефактор (`StrategyActionOrchestrator`
  + per-type executor'ы) выполнен на `SYNC_DOCS_FROM_CODE` шага 6 —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration.md`. **Форвардом
  (остаток Stage 3):** transition-conditions в модели стратегии +
  exit-as-transition (`MANAGING→EXIT_PENDING` без `DEAL_EXIT`) + снять
  вырожденный `CLOSE_FULL` — сверить остаток с as-built шага 6.

### Рассмотрено, не берём (прогоны 2-3)

- **В-4 batch-write** (`batch-orders`/`cancel-batch-orders`/
  `amend-batch-orders`) — конфликт с гранулярностью «одна команда —
  одна сущность» (CMD-Q3); новой фактуры под пересмотр нет.
  Контракт задокументирован (`contracts/batch-operations.md`),
  `mass-cancel` ушёл вне периметра (MMP/Option-only).
- **В-5 STP** (`stpMode`/`stpId`) — сознательно не используется;
  новой фактуры нет. Действует биржевой default
  (`acctStpMode=cancel_maker` — `contracts/account-config.md`).

## Хвост шага 4 (CODE-отложения, 2026-06-11)

Refinements, сознательно отложенные при `CODE` шага 4 (код — первый
проход, доки описывают целевой дизайн). Источник —
`.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-sync-docs-from-code.md`
(§DEFER). Не блокируют `DONE` шага 4; берутся при доведении командного
слоя / на смежных шагах.

- **SUBMIT recovery-by-clientId** — `SubmitOrderExecutor`/
  `SubmitAlgoOrderExecutor`: при пустом `externalId` искать сущность на
  бирже по client id (recovery после краха между send и сохранением
  `externalId`) до повторного place. Сейчас — place-if-blank.
- **ClosePosition settle ccy** — `ClosePositionExecutor`/
  `IntegrationService.closePosition`: передавать settle currency в
  close-request (сейчас `null`).
- **`ServiceCommandFactory`: REPLACE-оркестрация + CANCEL-резолюция
  цели.** Порядок ног REPLACE по риск-классу (place→факт→cancel для
  protective; cancel→факт→place для entry) и резолюция цели CANCEL по
  цепочке `replacesInternalId` — не реализованы (фабрика покрывает
  CREATE/SUBMIT/REFRESH/CLOSE). **Владелец оркестрации решён (`GAPS_CLOSE_1`
  шага 6, CMD-Q5/CMD-Q6):** секвенс ног ведёт петля/`DealStateMachine` по
  фактам, фабрика остаётся «одна команда за проход»
  (`docs/decisions/action-orchestration-vs-command.md`). Концепция —
  `replace-not-amend`, `DealActionState` §REPLACE. **Re-deferred за `CODE`
  шага 6 (deferral D1, 2026-06-22):** на `CODE` шага 6 фабрика REPLACE-ног
  оставлена возвращающей `empty`, `ManagingHandler` стоит в `MANAGING`;
  обоснование — самостоятельный объёмный refinement, не нужен базовой петле
  фазы 1, `DONE` шага 6 не гейтит. Сверка —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-code.md`
  §Сверка scope.
- **Refresh algo: external-поля дерева `condition`.** `updateFromSnapshot`
  игнорит `condition`; обновляются только top-level факты срабатывания.
  Обновление trigger/trailing external-цен из снапшота — добрать.
- **Evidence-cycle пагинация по `billId`.** `REFRESH_FILLS` и
  order/algo pending/history — сейчас одна страница на звено; добрать
  пагинацию назад до пустого `data` (владение циклом —
  `refresh-evidence-cycle-ownership`).
- **Рантайм-прогон через `OkxProxyController`** — отдельно, при
  поднятом PostgreSQL + demo-кредах (вкл. И-2: подтверждение
  `cancel-advance-algos` для trailing в demo trading).

### Из адверсариального ревью (2026-06-11)

Источник —
`.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-adversarial-review.md`.
Деньги-латентные (D-B3, D-M1) материализуются только с оркестрационной
петлёй — **gating для step-6/7**, не для DONE шага 4.

- **[BLOCKER, gating step-6/7] D-B3 — SUBMIT recovery-by-clientId.** Краш
  между `placeOrder` и сохранением `externalId` → дубль ордера при
  ресабмите. `SubmitOrderExecutor`/`SubmitAlgoOrderExecutor`: перед place
  при пустом `externalId` искать сущность на бирже по client id и
  принять найденную. Требует `getOrder` null-on-not-found (OKX `51603`).
  Обязательно **до** включения авто-реплея SUBMIT (FSM/оркестратор).
- **[MAJOR, gating step-6/7] D-M1 — concurrency-guard исполнения
  команды.** Перекрытие тика/ручного триггера → двойной SUBMIT.
  **Спека выбрана (`GAPS_CLOSE_1` шага 6, 2026-06-22):** блокировка на
  уровне базы на **весь проход** `DealOrchestratorJob` (и таймерный, и
  ручной заход под одну блокировку) — сериализует проходы, per-deal-защита
  не нужна; in-memory guard отвергнут как небезопасный на мультиинстансе
  (`docs/components/DealOrchestratorJob.md` §Concurrency-guard). **Реализация
  — на `CODE`/`DONE` шага 6** (жёсткий гейт `DONE`).
- **[MAJOR] D-M5/R5 — fills-пагинация + orders-history-archive.**
  `RefreshFillsExecutor` берёт одну страницу `getFills`/`getFillsHistory`
  (недобор fills → искажение PnL, step 7); пагинация назад по `billId`.
  Плюс order-цикл не доходит до `orders-history-archive` (последнее звено
  по докам) — добрать.
- **[MAJOR] perf P-M3 — `getRequiredById` грузит attached** даже для
  submit/cancel. Разделить: лёгкий load без attached vs граф-load для
  refresh.
- **[MAJOR, design] D-M4 — корроборация RefreshPosition.** Пустой
  positions-ответ → CLOSED от одного чтения (соответствует докам, но
  транзиентные пустые ответы → ложный CLOSED). Рассмотреть корроборацию
  (повторное чтение / cross-check fills) до объявления close. Форвард-
  вопрос дизайна позиции.
- **[MINOR] perf — батчи/churn.** `saveAll` для attached/balances вместо
  per-row; upsert баланса вместо delete+insert; собрать изменённые
  ордера в RefreshFills в один `saveAll`.
- **[MINOR] D-m1/D-m2 — подпись/эхо.** Clock-skew tolerance подписи OKX;
  лишние циклы refresh при пустом clOrdId-эхе.
- **[MINOR] conventions m2 — `getRequiredByInternalId`** (Order/AlgoOrder)
  сейчас не вызывается; если step-6/7 lookup так и не появится — удалить.

## Ретро-ревью шагов 1-3 (2026-06-11)

Независимый адверсариальный code-review, ретроспективно достроенный по
шагам 1-3 (источник —
`.claude/work/history/2026-06-11-phase-1-steps-1-3-retro-adversarial-review.md`).
Блокеров нет, статусы `DONE` валидны. Неблокирующий форвард-долг:

- **Шаг 1.** `[MAJOR]` SYNC-overlap не реализован (`syncOverlapBars`
  объявлен, не зовётся; overlap от `pageSize`) — реализовать или удалить
  свойство. `[MINOR]` `repairAttempts` in-memory → поле на `CandleGroup`
  (гарантия «N попыток → ERROR» через рестарт); отброшенный return
  `saveCandles`; двойной `findByStatusIn` за тик.
- **Шаг 2.** `[MAJOR][PERF]` декартов join-fetch дерева
  (`StrategyRepository.findByInternalIdWithTree`) — разнести на 2
  fetch / `@EntityGraph`+`@BatchSize` (should-fix, бьёт с ростом дерева).
  `[MAJOR, error-convention]` 500 вместо 422 при гонке «одна ACTIVE» и
  500 вместо 409/идемпотентности при повторном POST — ловить нарушения
  `uk_strategy_active_per_instrument` / `uk_strategy_internal_id`
  (развилка 409-vs-идемпотентность — продуктовая). `[MINOR]`
  неиндексированные FK `strategy_actions.strategy_step_id` /
  `target_action_id`.
- **Шаг 3.** `[MINOR]` провизорные пороги-дефолты резолвера применяются
  молча (сигнал «дефолт применён»); двойная owner-простановка на
  UNKNOWN-ветке `MarketStructureJob`; `lookbackBars` без нижней границы
  перед `PageRequest.of`. `[NIT]` N+1 по таймфреймам (повторная загрузка
  окна для настроек одного инструмента).
- **Сквозное.** Error-политика зафиксирована
  (`docs/rules/error-handling-policy.md`; кратко — `codestyle.md`
  §«Обработка ошибок»): коды, единый `@ControllerAdvice`, трансляция
  нарушений констрейнтов в 4xx. Ретро-майоры шагов 2 и 4 закрываются
  в одном месте по этой политике; конкретный набор HTTP-кодов и
  409-vs-идемпотентность — провизорны (хвост пользователя).

## Методологические задачи (по итогам миграции)

Не cross-cutting миграции, а ревизии методологии по итогам прогонов.

### M1. Ревизия разделов «Чего не хранит» в мигрированных моделях

**Суть.** Decision `.claude/decisions/negative-statements-not-fixated.md`
отвергает раздел «Чего не хранит» как альтернативу C (отрицания
отбрасываются, позитив фиксируется там, где живёт). На практике при
миграции CC последовательно применяет более мягкое прочтение
(«отрицание + указатель на позитив = оставить») — разделы «Чего не
хранит» / «Что не хранит» появились в моделях. **Перед запуском
задачи решить:** либо уточнить decision и зафиксировать практику
(раздел разрешён в формате «отрицание + позитив»), либо почистить
модели по букве decision.

**Тип:** методологическая ревизия по итогам миграции.

**Сфера — накопительная.** Текущие затронутые модели:
- `docs/models/domain/core/Position.md` (§Что Position не хранит);
- `docs/models/domain/core/Order.md` (§Что Order не хранит);
- `docs/models/domain/aggregate/Deal.md` (§Runtime graph — «не входят / не
  хранятся»);
- `docs/models/domain/aggregate/Strategy.md` (§Что Strategy не хранит — через
  архитектурные инварианты);
- `docs/models/domain/other/AnomalyReport.md` (§Чего не хранит).

В миграции процессов (2026-05-28) разделы «Чего не хранит» в новых
файлах **не создавались** (новых затронутых моделей нет).

## Инфра-долг (Boot 4 миграция / рантайм-робастность, сессия 2026-06-12)

Вскрыто на первом реальном рантайм-старте (dev/test-сплит БД + Vault,
снапшот v47). Не cross-cutting миграция из архива — инфра/рантайм-долг
переезда стека.

### I1. Boot 3→4 split-autoconfig: durable-проверка

Переезд Boot 3→4 / Spring 7 / Hibernate 7 / JDK 25 раньше не гонялся в
рантайме — компиляция пробелы не ловит. За сессию вскрыто 3 пробела
split-autoconfig: `RestClient.Builder` (→ `spring-boot-starter-restclient`),
Jackson 2 `ObjectMapper` (→ `spring-boot-jackson2`), Flyway
(→ `spring-boot-starter-flyway`) — все по шаблону «библиотека на classpath
есть, её `spring-boot-*` автоконфиг-модуль не подтянут → бин/фича молча не
активируется». **Durable-проверка на будущее** (при добавлении/обновлении
зависимости): «библиотека на classpath → её `spring-boot-*`
автоконфиг-модуль подтянут?». Особо коварны «тихие» стартовые автоконфиги
без инжекта бина (Flyway): без модуля не падают, просто ничего не делают.

### I2. Миграция кода на Jackson 3

Код на Jackson 2 (`com.fasterxml.jackson`); Boot 4 / Spring 7 дефолтят
Jackson 3 (`tools.jackson`). Бин `ObjectMapper` сейчас даём
совместимостным `spring-boot-jackson2` (интерим-adopt, `tech-radar`).
**Чистый end-state:** миграция кода (`RuntimeJsonConverter` /
`StrategyJsonConverter`, DTO-аннотации; `ObjectMapper.copy()` /
`setDefaultPropertyInclusion`, `JsonProcessingException`) на Jackson 3 и
снятие `jackson2`. Радар — Jackson 3 = `assess`.

### I4. Jackson 3 × Lombok beanspec мангли́нг в OKX-DTO (находка F4)

Системный класс, смежный I2. **Корень:** Jackson 3 (`tools.jackson`,
дефолт RestClient в SB4/Spring 7) выводит имя свойства из Lombok
beanspec-аксессора поля «строчная-первая/заглавная-вторая»
(`sCode`→`getsCode()`, `cTime`→`getcTime()`) иначе, чем JSON-ключ → поле
биндится в **null**. Jackson 2 (legacy-mangling) с ключом совпадал →
до перехода стека на Jackson 3 дефект не проявлялся. Подтверждено
эмпирикой (юнит-срезы десериализации под обоими Jackson).

**Симптом:** write-ack терял per-element `sCode` (реджект-код уходил в
top-level fallback, F3a); read-снапшоты теряли таймстампы —
probe-`getBalance → externalUpdatedAt:null` из `uTime`.

**Сделано (interim, сессия 2026-06-12):**
- **F3a** — `@JsonProperty` на `sCode`/`sMsg` в `OrderAckOkxResponse`/
  `AlgoOrderAckOkxResponse`; тест `OkxAckDeserializationTest` (Jackson
  2/3); live re-place подтвердил ack `code="51010"` (per-element), не
  top-level `"1"`.
- **F4** — `@JsonProperty` на `cTime`/`uTime` в `OkxBalanceResponse`,
  `OkxBalanceDetailResponse`, `OkxPositionResponse`, `OrderOkxResponse`,
  `OkxAlgoOrderResponse`; тест `OkxReadDtoDeserializationTest` (полный
  per-field бинд каждого DTO под Jackson 3, зелёный).
- Grep текущих OKX-DTO на точный паттерн lower-upper: ровно эти 7 полей
  (4 ack + cTime/uTime) — иного остатка по OKX нет (request/response/
  nested `AttachAlgoOrdOkxResponse` чисты).

**Открыто (integrator, routing — НЕ в этом заходе):**
- **Защита от рецидива:** глобальный конфиг Jackson 3 (вернуть
  legacy-мангли́нг — широкий blast radius на всю десериализацию) **vs**
  конвенция «OKX-DTO аннотируют поля `@JsonProperty` + round-trip тест».
- **Репо-wide sweep:** эвристика lower-upper по текущим OKX-DTO
  исчерпана, но (а) не гарантирует все Jackson-3 edge-cases (аббревиатуры
  / all-caps), (б) будущие и иные источники DTO — на конвенцию/sweep.

**Влияние:** гейтит корректность любого read-снапшота с таймстампами
(`cTime`/`uTime`) → относится к **Фазе 3** prod read-only. Связано с I2
(миграция кода на Jackson 3 — целевой end-state; F4 — точечная защита до
неё). Источник — run-log `source-api-pilot-run-log.md` (F3a/F4).

## Средовой дефицит автономного RUN тестов (контур source-api, 2026-06-12)

Вскрыто эскалацией RUN пилота `source-api-testing`: `tester` не может
прогнать demo-фазу автономно из shell CC — нет headless-бута приложения
и нет доступа к Vault-токену. Снять дефицит, чтобы demo-прогоны не
зависели от ручного бута. Состав:

- **`mvnw`/wrapper в репо** — headless-бут из shell (сейчас нет
  `mvn`/`mvnw`/собранного jar — приложение поднимает только пользователь
  через IDEA run-config).
- **Проброс Vault-токена в окружение прогона** — токен живёт только в
  IDEA run-config env; shell CC его не видит → `spring.config.import:
  vault://` из CC не проходит. Дать токен окружению автономного прогона
  (test-профиль).
- **Правило безопасности (инвариант роли `tester`):** автономно
  бутается **только `test`-профиль** (demo-креды, `x-simulated-trading=1`
  — prod-write технически невозможен). **`prod`-профиль — никогда
  автономно**, только под пользователем (prod read-only остаётся хвостом
  пользователя навсегда).

Снимает зависимость demo-прогонов от ручного бута; prod-фаза остаётся за
пользователем. Источник — пауза RUN пилота (run-log
`history/2026-06-20-source-api-contour/source-api-pilot-run-log.md`;
снапшот v48).

> Примечание: ре-база контура на сырьё
> (`.claude/decisions/source-api-target-rebase.md`) делает контур
> demo/non-prod и убирает prod из контура; prod read-only — больше не
> хвост контура, а ад-хок ручная проверка пользователя вне контура.
> Demo-бут по-прежнему нужен для автономного RUN код-тестов.

