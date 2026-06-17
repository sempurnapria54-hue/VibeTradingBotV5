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

- Миграция 6 торговых сущностей в `docs/` **завершена и закрыта**
  (`.claude/work/history/2026-05-27-миграция-торговых-сущностей.md`).
- Миграция архивных процессов (8 доков `Audit/`/`Calculation/`/`Deal
  management/`) **завершена и закрыта 2026-05-28**
  (`.claude/work/history/2026-05-28-миграция-процессов.md`). Покрытые
  cross-cutting пункты ниже свёрнуты как закрытые; частично покрытые —
  обновлены.
- Миграция API-кластера OKX (26 REST endpoint-доков) **завершена и
  закрыта 2026-05-28**
  (`.claude/work/history/2026-05-28-миграция-api-okx.md`). П.10 ниже
  свёрнут как закрытый.

**Как читать пункты.** Каждый пункт — будущая или завершённая миграция
кластера: источник + суть + указатель на архивные форвард-заметки. Полные
форвард-заметки разворачиваются из соответствующей подпапки `history/`:
`2026-05-27-миграция-торговых-сущностей/tasks-<сущность>.md` (модельный
кластер) или `2026-05-28-миграция-процессов/tasks-<док>.md` (процессы).
Архивные модели и процессные доки в `.claude-archive/` **не удалены** —
источник для оставшихся миграций.

## Cross-cutting миграции

### 1. Deal management: lifecycle, FSM, команды — ✅ ЗАКРЫТО (2026-05-28)

Мигрировано: `DealContext` (RVO) + `DealContextService`; FSM handlers
per-status (`PrecheckHandler`…`ErrorHandler`), `DealStateMachine` (+3
проверки), `StrategyConditionEvaluator`; подсистема `ServiceCommand`
(`ServiceCommand`+`ServiceCommandType`, `ServiceCommandPayload`,
`ServiceCommandExecutor`, `ServiceCommandFactory`, `ClientService`,
`RetryPolicyService`, 14 executor'ов); процесс `deal-management`; правила
`command-lifecycle`, `runtime-error-classification`,
`controlled-exchange-exceptions`, `trading-constraints`. Master-index
«Статусы торговых сущностей» разобран по владельцам. Детали —
`history/2026-05-28-миграция-процессов.md`.
**Осталось:** финализационные executor'ы (`FINALIZE_*`/`MARK_*`) — DEAL-Q1;
`DealActionState`/`Retryable`/`RuntimeTarget` модель — DEAL-Q3
(`open-questions.md`).

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

### 3. Калькуляторы действий стратегии + RVO — ✅ ЗАКРЫТО (2026-05-28)

Мигрировано: `StrategyActionCalculator`, `CalculationContextFactory`,
`PriceCalculator`, `SizeCalculator`, `MarketPriceDataService`,
`InstrumentExternalRulesService`; RVO `CalculationContext`,
`MarketPriceData`, `CalculatedStrategyAction`,
`StrategyActionCalculationResult`, `CalculationError`, `CalculatedPrice`,
`CalculatedSize`; процесс `strategy-action-calculation`.
`StrategyConditionEvaluator` — в п.1. `InstrumentExternalRules` модель — в
п.5. **Осталось:** `RiskSettings` (RISK-Q1) — `open-questions.md`
(`PositionContext` — PROC-Q1 закрыт 2026-06-06: рудимент, не
материализуется).

### 4. Risk-слой — ✅ ЗАКРЫТО (2026-05-28)

Мигрировано: `RiskValidator`, `RiskBlockResolver`; RVO
`RiskValidationResult`, `RiskCheckResult` (+ `RiskCheckCode`),
`RiskBlockAction`; правило `risk-validator-scope`; процесс
`risk-evaluation`. `TradeRuleValidator` (контекст-док) — см. п.7.

### 5. Расчёт индикаторов и рыночных данных — ✅ ЗАКРЫТО (2026-05-28)

Мигрировано: jobs `CandleJob`, `InstrumentExternalRulesSyncJob`,
`IndicatorJob`, `MarketStructureJob`, `MarketPhaseJob`; сервисы
`Indicator`/`MarketStructure`/`MarketPhase` Service;
`MarketDataExpirationChecker` (+ RVO `MarketDataExpirationResult`);
market-data модели `InstrumentExternalRules`, `IndicatorValue`,
`MarketStructure` (+ `MarketPriceLevel`), `MarketPhase`; правило
`market-data-freshness`; процесс `market-data-calculation`; OKX
`okx-timeframe-mapping`/`okx-instrument-mapping`/`okx-market-price-data-mapping`.
**Осталось (вне процессных доков):** standalone модели `Candle` и
`Instrument` материализованы в `GAPS_CLOSE_1` шага 1
(`docs/models/domain/other/Candle.md`, `.../CandleGroup.md` +
`docs/lifecycles/CandleGroup.md`; `docs/models/domain/core/Instrument.md`);
`TimeFrame` размещён в `CandleGroup.md` (TIME-Q1 закрыт на
`GAPS_CLOSE_1` шага 2: раздел в `Strategy.md` сведён к ссылке).
Архивный исходник (легаси) —
`.claude-archive/2026-05-21/docs/deprecated/models/domain/old/Candle.md`.

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
timeline, snapshot-формат; breakdown PnL (fees/fundingFee/gross-net/
fills/avg prices/partial exits); `TradeFill`/`TradeFillsArchive` +
`REFRESH_FILLS`; ~30 подвопросов. Связано с DEAL-Q1/DEAL-Q2.
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
статусе инструмента) и standalone модель `Instrument` для market-data
(из п.5).

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
`CLOSED`), `Account`; материализация отложенной rules-подсистемы
(`InstrumentExternalRules` + `InstrumentExternalRulesSyncJob`;
округление/sizing/риск — поздние шаги) и её соотнесение со
снапшот-концепцией / возможный ренейм — INSTR-Q1
(`open-questions.md`).

**Форвард-заметки:** `2026-05-27-.../tasks-order.md` (ORD-Q5),
`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` (ANOM-Q4).

### 10. API-кластер OKX — ✅ ЗАКРЫТО (2026-05-28)

REST endpoint-доки из `.claude-archive/2026-05-21/docs/api/okx/*`
мигрированы в `docs/integrations/okx/`. Что покрыто: order/algo/position/
balance/instrument/market-price-data — дополнены endpoint'ами,
rate-limit, permission, response-полями (`cTime`/`uTime` и др.);
candle (`okx-candle-mapping.md`), fills (`okx-fills-mapping.md` +
`OkxFillResponse`), fills-archive async-флоу
(`okx-fills-archive-mapping.md` + `OkxFillsArchiveResponse`), bills
(`okx-account-bills-mapping.md` + `OkxAccountBillResponse`),
connectivity (`okx-ws-limits.md` + `okx-service-urls.md`). Не
мигрировано: устаревший раздел «Реализация в коде (Stage 02)» обзорного
файла; полноценная WS-документация (OKX-Q4). Playbooks v1 — вне
скоупа. Детали — `history/2026-05-28-миграция-api-okx.md`.
**Связанные open-questions:** OKX-Q1 (persisted `TradeFill`),
OKX-Q2 (`TradeFillsArchive` + async-флоу), OKX-Q3 (bills как источник
`DealCashFlow` / финализации `Deal`), OKX-Q4 (WS-каналы отдельным
заходом).

### Отложенные продуктовые вопросы (future)

- `linkedOrderExternalIds` — использование для fills/recovery/audit
  (`2026-05-27-.../tasks-algo-order.md` ALGO-Q6).
- Стандарт описания персистентности доменных моделей: формат и
  версионирование jsonb-снимков (`AnomalyReport.internalBefore/After` и
  др.). Шире одной модели.
  (`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` ANOM-Q5).

## Подготовка перед написанием кода

### P1. Код-шаблоны для `code-writer` — ✅ ЗАКРЫТО (2026-05-31)

Прежняя формулировка (отложенные «референс-доки» как источник
примеров для `code-writer`) пересмотрена. Решённая модель:
код-шаблоны (абстрактные паттерны) — тир `.claude/templates/code/`,
вход для написания; `find-code-examples` — пост-код-скилл подбора
примеров из реального кода для доков. Отдельного слоя «референс-доков»
нет. Материализован первый шаблон —
`.claude/templates/code/Java/Controller.md`. Обоснование и закрытие
REF-Q1 — `.claude/decisions/code-templates-vs-examples.md`.

## Шаг «Безопасность» (Фаза 1, шаг 9) — форвард-материал

Материал, отложенный до шага «Безопасность» роадмапа
(`.claude/work/roadmap/phase-1.md`, шаг 9). Содержание шага
прорабатывается docs-first на самом шаге; здесь — что туда заведомо
идёт.

### S1. Конфигурация секретов через Vault — ✅ базовая привязка ЗАКРЫТА (2026-06-12)

**Сделано на инфра-шаге** (раньше планового шага 9, см. снапшот v47):
Vault-привязка секретов через `spring.config.import: vault://` per-profile —
datasource (`tradingbot/postgres[-test]`) и OKX-креды
(`tradingbot/okx[-test]`); env-плейсхолдеры значений; `vault://` только в
`spring.config.import` (бизнес-поля Vault не знают); реальные секреты не
коммитятся (`.env.*.local` gitignored; Vault-токен — через IDEA run-config
env). Это переносит сюда ранее отложенный тезис «секреты через Vault».

**Остаётся на шаг 9 (остаточный хардненинг):** политики/approle вместо
root/dev-token, ротация секретов, unseal/инициализация Vault не в dev-режиме,
вынос Vault-токена из run-config. Auth-инфраструктура (Spring Security) — S2.

### S2. Auth-инфраструктура

Spring Security, `@PreAuthorize`, `SecurityFilterChain`. На этом
шаге **реактивируется** фокус `security-review`
(`.claude/skills/security-review.md`), деактивированный на текущих
шагах.

## Форвард-материал шагов 5 / 7 / 8 (скан интегратора, прогоны 2-3, 2026-06-11)

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
  `maxLever`/`maxSz` по тирам как вход RISK-Q2
  (`contracts/position-tiers.md`).

### Шаг 7 (сделки и P&L)

- **В-3 `positions-history`** — P&L закрытых позиций с разложением
  `realizedPnl = pnl + fee + fundingFee + liqPenalty`
  (`contracts/position.md` §История).
- **В-6 `funding-rate(-history)`** — funding-компонент P&L SWAP.
  **Лежит рядом с OKX-Q3:** два пути к funding (bills subType
  173/174 vs `funding-rate-history.realizedRate`) — шаг 7 выбирает
  осознанно, не ведёт два параллельных трека
  (`contracts/funding-rate.md`, `open-questions.md` §OKX-Q3).
- **В-7 `trade-fee`** — ставки комиссий для прогноза/сверки
  (фактические комиссии — fills/bills); знак: минус = комиссия
  (`contracts/trade-fee.md`).

### Шаг 8 (safety / AnomalyJob)

- **В-1 `cancel-all-after`** — dead-man's switch: серверная
  страховка на потерю связи **поверх** явного
  `EXECUTE_KILL_SWITCH`, не вместо него
  (`contracts/cancel-all-after.md`; heartbeat раз в секунду,
  timeOut 0|[10,120] с). Покрытие algo-ордеров CAA офдоком не
  специфицировано — уточнить на шаге.

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
  CREATE/SUBMIT/REFRESH/CLOSE). Концепция — `replace-not-amend`,
  `DealActionState` §REPLACE.
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
  Сериализация per-deal (`JobExecutionGuard` / row-lock на
  `DealActionState`) или `@Version` оптимистик-лок. Владелец —
  оркестрационная петля step-6/7.
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
- **Сквозное.** Error-конвенция (`codestyle.md` §«Обработка ошибок —
  TBD») гейтит часть major'ов шага 2 (и шага 4): коды, `@ControllerAdvice`
  vs per-endpoint, трансляция нарушений констрейнтов в 4xx. Усиление
  приоритета существующего TBD.

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

### I3. `OkxSigningInterceptor` — внятная ошибка на пустых кредах

При незаполненных OKX-кредах (`api-key/secret/passphrase` = null)
интерсептор падает NPE на приватных вызовах вместо внятной ошибки
«OKX credentials not configured». Добавить fail-fast / осмысленное
сообщение (валидация при старте либо явная проверка в интерсепторе перед
подписью).

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
`.claude/work/progress/source-api-pilot-run-log.md`; снапшот v48).

> Примечание: ре-база контура на сырьё
> (`.claude/decisions/source-api-target-rebase.md`) делает контур
> demo/non-prod и убирает prod из контура; prod read-only — больше не
> хвост контура, а ад-хок ручная проверка пользователя вне контура.
> Demo-бут по-прежнему нужен для автономного RUN код-тестов.

## Ре-база source-api: снятие mapped-поверхности (§«Неиспользуемый код»)

При приземлении **A2 raw-passthrough** (новая поверхность контура,
отдаёт сырой `OkxApiResponse<T>`) — снять mapped-поверхность, ставшую
неиспользуемой (`codestyle` §«Неиспользуемый код»). Делается **вместе с
приземлением passthrough** (не раньше — чтобы не снять источник до
замены), не методологической правкой:

- **mapped-эндпоинты `OkxProxyController`** (отдают снапшоты/`ExchangeAck`)
  — старая поверхность контура, замещается A2-passthrough'ем;
- **mapped-цепочка `getMarketPriceData`** (`IntegrationService` →
  `OkxIntegrationService` → `MarketPriceDataMapper` →
  `MarketPriceDataExternalSnapshot`) — **нет доменного потребителя**
  (только прокси; сборка `MarketPriceData` — шаг 5, `HOLD`); под сырьё
  live-цена цепочки идёт от сырого `getTicker`. **Оговорка:** если шаг 5
  (доменная сборка `MarketPriceData`) близко — оставить как forward-код;
  решение — на шаге реализации passthrough.

Источник — `.claude/decisions/source-api-target-rebase.md` §Следствия.

## Связанные открытые вопросы

`.claude/work/questions/open-questions.md`:
- **Продуктовые финализации `Deal`:** DEAL-Q1 (retry-state финализации;
  п.1/п.6), DEAL-Q2 (resultProfit при исчерпании retry; п.6).
- **Из миграции процессов:** RISK-Q1 (`RiskSettings`; п.3/п.4), DEAL-Q3
  (`DealActionState` core/other + lifecycle; п.1), CMD-Q2 (базовый
  тип/дискриминатор payload'ов, судьба `ServiceCommandPayload.md`; п.1).
  PROC-Q1 закрыт 2026-06-06 (рудимент), CMD-Q1 закрыт 2026-06-06
  (`.claude/decisions/executor-payload-file-granularity.md`), ENUM-Q1
  снят 2026-06-06 (архивный артефакт).
- **Из миграции API-кластера OKX (п.10):** OKX-Q1 (persisted
  `TradeFill` модель и executor финализации; п.6), OKX-Q2
  (`TradeFillsArchive` async-флоу), OKX-Q3 (bills как источник
  `DealCashFlow` / финализации `Deal`; п.6, DEAL-Q1/Q2), OKX-Q4
  (WS-каналы OKX отдельным заходом).
- **Из шага 1 Фазы 1 (поток рыночных данных):** INSTR-Q1
  (соотнесение снапшот-концепции с `InstrumentExternalRules` /
  возможный ренейм; п.9); ORCH-Q1 (владелец оркестрации онбординга
  инструмента и загрузки свечей — `candle-loading`).
