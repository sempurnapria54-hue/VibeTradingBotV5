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

### S1. Конфигурация секретов через Vault

- Секреты конфигурируются через Vault.
- В коде и бинах — стандартные Spring properties (бизнес-поля не
  знают про Vault).
- `vault://` допускается **только** в `spring.config.import`, не в
  бизнес-полях.
- Env-плейсхолдеры для значений.
- Реальные секреты не коммитим.

### S2. Auth-инфраструктура

Spring Security, `@PreAuthorize`, `SecurityFilterChain`. На этом
шаге **реактивируется** фокус `security-review`
(`.claude/skills/security-review.md`), деактивированный на текущих
шагах.

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
