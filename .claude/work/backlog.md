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
методологические ревизии и пайплайн-задачи. Обоснование
связи — `.claude/decisions/product-roadmap-type.md`.

Файл держит только живое (правило —
`.claude/rules/closed-work-transfer.md`); итоги закрытого — в
`history/` (последние чистки:
`2026-07-13-backlog-phase-1-closed-cleanup.md`,
`2026-07-14-claude-docs-curation.md`). Нумерация секций — с
пропусками (номера закрытых не переиспользуются). Полные
форвард-заметки миграций — в подпапках `history/`
(`tasks-<сущность>.md` / `tasks-<док>.md`); архивные доки в
`.claude-archive/` не удалены — источник для оставшихся миграций.

## Cross-cutting миграции

### 2. Resolver / mapper / checker компоненты — частично

**Осталось:** `*Mapper` (`OrderMapper`, `PositionMapper`,
`AlgoOrderMapper`, `BalanceContainerMapper`), `BalanceFreshnessChecker`,
`OkxAlgoOrderTypeResolver`, `AttachedAlgoOrderStateResolver`.
**Форвард-заметки:** `2026-05-27-.../tasks-order.md` (ORD-Q2),
`tasks-position.md` (POS-Q2), `tasks-algo-order.md` (ALGO-Q2),
`tasks-balance.md` (BAL-Q6); `2026-05-28-.../tasks-статусы-торговых-сущностей.md`
(Mappers — Решения прохода 2).

### 6. Аудит и история исполнения — частично

**Источник:** `.claude-archive/.../processes/Audit/Аудит и история
исполнения.md`;
`.claude-archive/2026-05-21/docs/deprecated/models/domain/old/TradeFill.md`,
`TradeFillsArchive.md`. Архивный док — рабочий каркас, **выведен из
миграции процессов** (`.claude/decisions/process-materialization-criterion.md`):
модели истории/timeline не спроектированы, ~30 подвопросов.
**Осталось:** модели `ServiceCommandExecutionHistory`, entity history,
timeline, snapshot-формат; ~30 подвопросов. Связано с DEAL-Q1/DEAL-Q2.
Финализация PnL закрыта отдельно
(`docs/decisions/result-profit-source.md`); пофилловый аудит
(`TradeFill`/`TradeFillsArchive`) — вне фазы 1.
**Форвард-заметки:** `2026-05-28-.../tasks-аудит-и-история-исполнения.md`
(§5/§8 подвопросы + Решения прохода 2); `2026-05-27-.../tasks-deal.md`
(DEAL-FW5, FW9), `tasks-balance.md` (BAL-Q7), `tasks-order.md` (ORD-Q7),
`tasks-position.md` (POS-Q5).

### 7. Anomaly / safety / kill-switch — частично

**Осталось:** `ReconciliationJob` (в архиве только название —
live risk после terminal / позиция без active Deal), полный kill-switch
flow (`KillSwitchService`, kill-switch report, after-snapshot,
`Position.CloseReason = KILL_SWITCH`), `TradeRuleValidator`.
**Форвард-заметки:** `2026-05-28-.../tasks-жизненный-цикл-сделки.md`
(ReconciliationJob), `2026-05-27-.../tasks-position.md` (POS-Q7),
`tasks-deal.md` (DEAL-FW7),
`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` (ANOM-Q1…Q3).

### 8. Strategy: enforcement, валидатор, примеры — частично

**Осталось:**
- **runtime-прогон Strategy API** (хвост scope шага 2, PostgreSQL не
  был поднят): миграция `V2` → `POST` `trend-following-ema.json` →
  `GET` → `PUT`-переходы статуса. Выполнить при поднятом PostgreSQL.
- `Strategy API examples.md` (JSON-примеры, тип reference —
  воспроизводить ли как файл знания) — **остаётся открытым**.

Построенное (Strategy API, create-валидатор, «одна реализация») и
scope-решения — `history/2026-06-05-phase-1-step-2-strategy.md`,
`docs/decisions/strategy-materialization-and-validation.md`.
**Форвард-заметки:** `2026-05-27-.../tasks-strategy.md` (STR-FW8, FW9,
FW10), `tasks-deal.md` (DEAL-FW8).

### 9. Exchange модель/lifecycle

**Суть:** полная модель/lifecycle `Exchange` (`HOLD`/`DISABLED` среди
прочих; правило — `docs/rules/exchange-hold.md`), `Instrument`,
`Account`. Сюда же — enforcement `AnomalyReport.Severity` (CRITICAL →
торговля по инструменту запрещена; NON_CRITICAL → после kill-switch
может быть разрешена; блокировка в статусе инструмента) и standalone
модель `Instrument` для market-data.

**Осталось:** полный lifecycle `Exchange`, периферийные статусы
`Instrument` (`HOLD`, `ERROR`-recovery, повторный онбординг,
`CLOSED`), `Account`. Минимальные модели `Instrument`/`Exchange` и
онбординг-путь lifecycle уже материализованы
(`docs/models/domain/core/`, `docs/lifecycles/Instrument.md`);
INSTR-Q1 закрыт
(`docs/decisions/instrument-external-rules-materialization.md`).
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

## Форвард-материал шагов 7 / 8 и фазы 3 (скан интегратора + ревью)

Заметки владельцам шагов, **не действия сейчас**; решения — на самих
шагах. Поле-уровневые контракты кандидатов скана готовы (см.
`docs/integrations/okx/coverage-manifest.md`, прогон 3). Кандидаты,
рассмотренные и не взятые (В-4 batch-write, В-5 STP), — итог в
`history/2026-07-14-claude-docs-curation.md`.

### Риск-преконтроль — остаточные кандидаты (вернуться по наблюдениям)

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
  2026-06-20,** `docs/decisions/per-trade-risk-policy.md`**).**
  Остаточный зазор: **узкий стоп → высокое плечо** при малом денежном
  убытке по стопу (риск на сделку умещается в лимит, но нотинал/плечо
  большие). **Вернуться после наблюдений** (бэктест / живые прогоны),
  когда станет видно, материализуется ли зазор на практике.

### Шаг 8 (safety / AnomalyJob)

- **Остаток холдов L3/L4** (реактивный enforcement построен на шаге 6 —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-holds-design.md`):
  (1) **проактивная детекция** аномалий (`AnomalyJob`/`TradeRuleValidator`
  + численный порог «серия неудач» STRUCT-Q1) — **шаг 8**;
  (2) **точный локальный after через REFRESH_*** + **биржа-широкая
  L4-реконсиляция** (внешний слепок читает только instId триггера) —
  **шаг 8**; (3) **аудит ручного un-hold** (кем/когда) — **шаг 9 / п.9**
  (сама операция un-hold построена).
- **В-1 `cancel-all-after`** — dead-man's switch: серверная
  страховка на потерю связи **поверх** явного kill-switch, не вместо
  него (`contracts/cancel-all-after.md`; heartbeat раз в секунду,
  timeOut 0|[10,120] с). Покрытие algo-ордеров CAA офдоком не
  специфицировано — уточнить на шаге.
- **Остаток kill-switch (ANOM-Q2):** per-инструмент контур построен,
  декларативный kill-switch откачён (семантика —
  `docs/components/KillSwitchExecutor.md`). **Остаётся форвардом:**
  (1) **AnomalyJob-путь** (проактивная детекция → зов executor'а) +
  общебиржевая **orphan-сверка** (сущности вне модели сделки) +
  перевод залипших L4-отчётов + порог «серия неудач» STRUCT-Q1 —
  **шаг 8**; (2) **PnL-финализация `EMERGENCY_CLOSED`** (остаток
  DEAL-Q2 закрыт G5: число = фактический realized net вкл.
  `liqPenalty`; расчёт — шаг 7). Связано с **ANOM-Q2**
  (`history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`).
- **Остаток Stage 3 FSM/action слоистости** (решение —
  `docs/decisions/fsm-execution-layering.md`; Stage 1-2 построены на
  шаге 6): transition-conditions в модели стратегии +
  exit-as-transition (`MANAGING→EXIT_PENDING` без `DEAL_EXIT`) + снять
  вырожденный `CLOSE_FULL` — сверить остаток с as-built шага 6.

### Перф-форвард (порог актуальности — фаза 3)

- **[MAJOR, perf] L4 `fireExchange` — небанженный O(сделок) burst под
  guard прохода (ревью холд-дельты, 2026-06-24).**
  `KillSwitchService.fireExchange` итерирует небанженный
  `DealDataService.findActiveByExchangeId` и на **каждую** сделку строит
  `DealContext` (~9 запросов) + kill-switch REST — внутри guard-прохода.
  Распухает по **памяти / стоимости запроса** линейно по числу
  одновременных сделок биржи, без потолка. В фазе 1 объём мал — не
  нагружено. **Починка (когда возьмём): перебор пачками (bounded) —
  полный свип сохранён**, режется не скорость, а ограниченный аппетит
  (память/стоимость). **`LIMIT` небезопасен** (отрезал бы несвёрнутый
  live risk); альтернатива — off-lock dispatch L4-teardown. Источник —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-code.md`
  §Доработка холд-дельты.
- **[MINOR, perf] Дублирующий тикер-REST в entry-скане (повторное ревью
  фикс-дельты M4, 2026-07-02).** `MarketPhaseService.buildContext` тянет
  тикер (`MarketPriceDataService.getMarketPriceData`) для классификации
  фазы по каждому ACTIVE-инструменту без активной сделки за проход;
  квалифицированный инструмент затем тянет тот же тикер повторно в
  `MarketConditionContextFactory.build`. Итог: +1 тикер-REST на каждый
  скан-инструмент, 2 идентичных вызова на квалифицированный — линейно к
  числу инструментов, давление на rate-limit OKX. Функционально
  корректно, согласуется с stage-1 no-cache. **Починка:** тянуть
  `MarketPriceData` один раз в `EntryScannerJob.scanInstrument` и
  прокинуть в оба контекста (фазовый + condition), либо короткоживущий
  per-tick кэш в `MarketPriceDataService`. Кросс-коллаборатор:
  `MarketConditionContextFactory.build` шарится с FSM
  (`DealFsmSupport.conditionContext`).
- **Унификация инфраструктуры джоб — горизонт фаза 3 (код-ревью заход 2,
  2026-07-01).** Доработка механизма замыкания под
  мультиинстанс/микросервисы. Состав: абстрактный `ScheduledJob`-родитель
  (шаблон `enabled → lock → run`) + единый `JobLock`-интерфейс с двумя
  реализациями (`InProcessJobLock` поверх `JobExecutionGuard`,
  `AdvisoryJobLock` — БД advisory-замок; raw-JDBC advisory —
  ратифицированное исключение: замок держит **одно соединение** весь
  проход). В фазе 1 все джобы — на in-process `JobExecutionGuard`;
  БД-замок вернётся с мультиинстансом (см.
  `.claude/rules/tech-radar.md` строка Raw-JDBC → `hold`,
  `docs/components/DealOrchestratorJob.md` §Concurrency-guard).

## Шаг 7 (сделки и P&L) — исполнительный хвост

**Концепция закрыта:** источник числа —
`docs/decisions/result-profit-source.md`; механика/носители стадий 1-2 —
`docs/decisions/pnl-finalization-mechanics.md`. Ниже — **исполнительный
хвост (CODE) + рантайм-верификация + форвард**, не выбор пути. Гейт
`CODE` — после чистого `DOCS_CHECK_3`.

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

## Хвост шага 4 (CODE-отложения, 2026-06-11)

Refinements, сознательно отложенные при `CODE` шага 4 (код — первый
проход, доки описывают целевой дизайн). Источник —
`.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-sync-docs-from-code.md`
(§DEFER). Берутся при доведении командного слоя / на смежных шагах.
Гейтовые D-B3 (SUBMIT recovery-by-clientId) и D-M1 (concurrency-guard)
закрыты на шаге 6 — итог в
`history/2026-07-14-claude-docs-curation.md`.

- **ClosePosition settle ccy** — `ClosePositionExecutor`/
  `IntegrationService.closePosition`: передавать settle currency в
  close-request (сейчас `null`).
- **`ServiceCommandFactory`: REPLACE-оркестрация + CANCEL-резолюция
  цели.** Порядок ног REPLACE по риск-классу (place→факт→cancel для
  protective; cancel→факт→place для entry) и резолюция цели CANCEL по
  цепочке `replacesInternalId` — не реализованы (фабрика покрывает
  CREATE/SUBMIT/REFRESH/CLOSE). Владелец оркестрации: секвенс ног ведёт
  петля/`DealStateMachine` по фактам, фабрика остаётся «одна команда за
  проход» (`docs/decisions/action-orchestration-vs-command.md`);
  концепция — `replace-not-amend`, `DealActionState` §REPLACE.
  **Re-deferred за `CODE` шага 6 (deferral D1, 2026-06-22):** фабрика
  REPLACE-ног возвращает `empty`, `ManagingHandler` стоит в `MANAGING`;
  самостоятельный объёмный refinement, не нужен базовой петле фазы 1.
- **Refresh algo: external-поля дерева `condition`.** `updateFromSnapshot`
  игнорит `condition`; обновляются только top-level факты срабатывания.
  Обновление trigger/trailing external-цен из снапшота — добрать.
- **Evidence-cycle пагинация.** Order/algo pending/history — сейчас одна
  страница на звено; добрать пагинацию назад до пустого `data`
  (владение циклом — `refresh-evidence-cycle-ownership`). Плюс
  order-цикл не доходит до `orders-history-archive` (последнее звено по
  докам) — добрать. Актуально и для новых `REFRESH_POSITIONS_HISTORY` /
  `REFRESH_BILLS` (шаг 7; `REFRESH_FILLS` снят).
- **Рантайм-прогон через `OkxProxyController`** — отдельно, при
  поднятом PostgreSQL + demo-кредах (вкл. И-2: подтверждение
  `cancel-advance-algos` для trailing в demo trading).

### Из адверсариального ревью (2026-06-11) — неблокирующий остаток

Источник —
`.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-adversarial-review.md`.
Гейтовые D-B3/D-M1 закрыты на шаге 6 (итог —
`history/2026-07-14-claude-docs-curation.md`).

- **[MAJOR] D-M5/R5 — пагинация evidence-цикла + orders-history-archive.**
  Одна страница на звено цикла (недобор фактов → искажение P&L-разбивки);
  пагинация назад по `billId` / добрать archive-звено (см. «Evidence-cycle
  пагинация» выше; fills-часть снята вместе с `REFRESH_FILLS`).
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
  ордера в один `saveAll`.
- **[MINOR] D-m1/D-m2 — подпись/эхо.** Clock-skew tolerance подписи OKX;
  лишние циклы refresh при пустом clOrdId-эхе.
- **[MINOR] conventions m2 — `getRequiredByInternalId`** (Order/AlgoOrder)
  сейчас не вызывается; если step-6/7 lookup так и не появится — удалить.

## Ретро-ревью шагов 1-3 (2026-06-11) — неблокирующий форвард-долг

Независимый адверсариальный code-review, ретроспективно достроенный по
шагам 1-3 (источник —
`.claude/work/history/2026-06-11-phase-1-steps-1-3-retro-adversarial-review.md`).
Блокеров нет, статусы `DONE` валидны.

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
- **Сквозное.** Ретро-майоры шагов 2 и 4 (коды ошибок) закрываются в
  одном месте по error-политике (`docs/rules/error-handling-policy.md`;
  кратко — `codestyle.md` §«Обработка ошибок»); конкретный набор
  HTTP-кодов и 409-vs-идемпотентность — провизорны (хвост пользователя).

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

## Инфра-долг (Boot 4 миграция / рантайм-робастность)

Вскрыто на первом реальном рантайм-старте (dev/test-сплит БД + Vault,
2026-06-12). Не cross-cutting миграция из архива — инфра/рантайм-долг
переезда стека.

### I1. Boot 3→4 split-autoconfig: durable-проверка

Переезд Boot 3→4 / Spring 7 / Hibernate 7 / JDK 25 раньше не гонялся в
рантайме — компиляция пробелы не ловит. Вскрыто 3 пробела
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

### I4. Jackson 3 × Lombok beanspec мангли́нг в OKX-DTO — защита от рецидива

Системный класс, смежный I2. **Корень:** Jackson 3 (`tools.jackson`,
дефолт RestClient в SB4/Spring 7) выводит имя свойства из Lombok
beanspec-аксессора поля «строчная-первая/заглавная-вторая»
(`sCode`→`getsCode()`, `cTime`→`getcTime()`) иначе, чем JSON-ключ → поле
биндится в **null**. Точечная защита текущих OKX-DTO поставлена
(`@JsonProperty` на 7 полях + round-trip тесты `OkxAckDeserializationTest` /
`OkxReadDtoDeserializationTest`; итог —
`history/2026-07-14-claude-docs-curation.md`, run-log
`history/2026-06-20-source-api-contour/source-api-pilot-run-log.md` F3a/F4).

**Открыто (integrator, routing — НЕ в этом заходе):**
- **Защита от рецидива:** глобальный конфиг Jackson 3 (вернуть
  legacy-мангли́нг — широкий blast radius на всю десериализацию) **vs**
  конвенция «OKX-DTO аннотируют поля `@JsonProperty` + round-trip тест».
- **Репо-wide sweep:** эвристика lower-upper по текущим OKX-DTO
  исчерпана, но (а) не гарантирует все Jackson-3 edge-cases (аббревиатуры
  / all-caps), (б) будущие и иные источники DTO — на конвенцию/sweep.

**Влияние:** гейтит корректность любого read-снапшота с таймстампами
(`cTime`/`uTime`) → относится к **Фазе 3** prod read-only. Связано с I2
(миграция кода на Jackson 3 — целевой end-state).

## Средовой дефицит автономного RUN тестов (контур source-api)

Вскрыто эскалацией RUN пилота `source-api-testing` (2026-06-12):
`tester` не может прогнать demo-фазу автономно из shell CC — нет
headless-бута приложения и нет доступа к Vault-токену. Снять дефицит,
чтобы demo-прогоны не зависели от ручного бута. Состав:

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
  автономно**, только под пользователем.

Снимает зависимость demo-прогонов от ручного бута. Источник — пауза RUN
пилота (run-log
`history/2026-06-20-source-api-contour/source-api-pilot-run-log.md`).

> Примечание: ре-база контура на сырьё
> (`.claude/decisions/source-api-target-rebase.md`) делает контур
> demo/non-prod и убирает prod из контура; prod read-only — ад-хок
> ручная проверка пользователя вне контура. Demo-бут по-прежнему нужен
> для автономного RUN код-тестов.
