# Backlog

## На какой вопрос отвечает этот файл

Что мы планируем сделать.

## Статус

Миграция 6 торговых сущностей в `docs/` **завершена и закрыта**
(summary — `.claude/work/history/2026-05-27-миграция-торговых-сущностей.md`).
Ниже — планируемые миграции cross-cutting кластеров, накопленные как
форвард-заметки при той миграции (стратегия —
`.claude/decisions/cross-cutting-parking.md`; судьба заметок —
`.claude/decisions/forward-notes-after-task-closure.md`).

**Как читать пункты.** Каждый пункт — будущая миграция кластера:
источник (архивные доки) + краткая суть + указатель на архивные
форвард-заметки. Полные форвард-заметки разворачиваются при запуске
пункта из:
`.claude/work/history/2026-05-27-миграция-торговых-сущностей/tasks-<сущность>.md`.
Архивные торговые модели (`.claude-archive/.../docs/domain/models/`) и
процессные доки **не удалены** — источник для этих миграций.

## Cross-cutting миграции

### 1. Deal management: lifecycle, FSM, команды

**Источник:** `.claude-archive/2026-05-21/docs/domain/processes/Deal
management/` (`Жизненный цикл сделки.md`, `FSM этапы сделки.md`,
`Сервисные команды.md`, `Статусы торговых сущностей.md`); контекст —
`docs/context/comands/*`, `docs/context/Сопровождение сделки.md`.
**Суть:**
- `DealContext` (RVO) + `DealActionState` (модель) + `RuntimeTarget`
  — состав, сборка на проход FSM, recovery/idempotency.
- FSM handlers per-status (`PrecheckHandler`…`ErrorHandler`) →
  `docs/components/`; `DealStateMachine` + конструкция handler'а
  (3 проверки) — по `.claude/decisions/fsm-handler-as-component.md`.
- Подсистема `ServiceCommand`: executors, `ServiceCommandFactory`,
  dispatch; `REFRESH_*` / `SUBMIT_*` / `AMEND_*` / `CANCEL_*` /
  `CLOSE_POSITION`; retry/recovery boundary; «ServiceCommand —
  runtime object, не persisted queue».
- lifecycle/finalization commands (`FINALIZE_DEAL_EXIT`,
  `MARK_DEAL_CLOSED`, emergency) — связано с `open-questions.md`
  DEAL-Q1 (retry-state финализации).
- `Статусы торговых сущностей.md` — master-index; разбирать по
  владельцам (`.claude/decisions/master-index-not-fixated.md`),
  сверить с уже мигрированными lifecycle.
**Форвард-заметки:** `tasks-deal.md` (DEAL-FW1…FW4), `tasks-position.md`
(POS-Q1, POS-Q6), `tasks-order.md` (ORD-Q1, ORD-Q3), `tasks-algo-order.md`
(ALGO-Q1, ALGO-Q3), `tasks-balance.md` (BAL-Q1, BAL-Q4).

### 2. Resolver / mapper / checker компоненты

**Источник:** mapping-доки (уже мигрированы в `docs/client/okx/rules/`)
+ command-доки; `docs/context/comands/*`. **Суть:** компоненты
adapter/command-слоя, чьё доменное существо уже зафиксировано в
lifecycle/client-rules, но `docs/components/<X>.md` отложены:
`OrderExternalStatusResolver`, `AttachedAlgoOrderStateResolver`,
`PositionStatusResolver` (+ `PositionStatusResolveResult` RVO),
`AlgoOrderExternalStatusResolver` (`OkxAlgoOrderExternalStatusResolver`),
`OkxAlgoOrderTypeResolver`, `*Mapper` (`OrderMapper`, `PositionMapper`,
`AlgoOrderMapper`, `BalanceContainerMapper`), `BalanceFreshnessChecker`,
`Refresh*Executor`, `ClosePositionExecutor`. **Форвард-заметки:**
`tasks-order.md` (ORD-Q2), `tasks-position.md` (POS-Q2),
`tasks-algo-order.md` (ALGO-Q2), `tasks-balance.md` (BAL-Q6). Может
сливаться с п.1 (command-кластер).

### 3. Калькуляторы действий стратегии + RVO

**Источник:** `.claude-archive/.../processes/Calculation/Калькуляторы
действий стратегии.md`. **Суть:** `StrategyActionCalculator`,
`PriceCalculator`, `SizeCalculator` (вкл. `closeFractionPercents`/
`allocationPercents` → размер), `StrategyConditionEvaluator`; RVO
`CalculationContext`, `MarketPriceData`, `CalculatedStrategyAction`,
`InstrumentExternalRules` → `docs/components/models/`. **Форвард-заметки:**
`tasks-strategy.md` (STR-FW2), `tasks-order.md` (ORD-Q6),
`tasks-algo-order.md` (ALGO-Q4).

### 4. Risk-слой

**Источник:** `.claude-archive/.../processes/Calculation/Оценка
рисков.md`; `docs/context/TradeRuleValidator — модель, роль и flow.md`.
**Суть:** `RiskValidator` (после расчёта action, до торговой команды;
не перед read-only), `RiskCheckResult`/`RiskCheckCode`/`RiskDecision`
(RVO/енумы), `RiskBlockResolver`. **Форвард-заметки:**
`tasks-strategy.md` (STR-FW3), `tasks-balance.md` (BAL-Q2),
`tasks-position.md` (POS-Q3), `tasks-order.md` (ORD-Q4),
`tasks-algo-order.md` (ALGO-Q5), `tasks-deal.md` (DEAL-FW6).

### 5. Расчёт индикаторов и рыночных данных

**Источник:** `.claude-archive/.../processes/Calculation/Расчёт
индикаторов и рыночных данных.md`; `docs/deprecated/.../Candle.md`,
`PriceTicker.md`, `Instrument.md`. **Суть:**
- jobs: `IndicatorJob`, `MarketStructureJob`, `MarketPhaseJob`,
  `EntryScannerJob`, `DealOrchestratorJob`.
- `MarketDataExpirationChecker` (checker; статус стратегии не меняет).
- модели market-data (`docs/models/other/` или отдельный кластер):
  `MarketPhase` (+ `Type`), `MarketStructure` (+ `Type`),
  `MarketPriceLevel`, `IndicatorValue` (+ `Type`), свечи, `Instrument`.
- `TimeFrameMapper` (OKX timeframe ↔ доменный `TimeFrame`) →
  `docs/client/okx/`.
**Форвард-заметки:** `tasks-strategy.md` (STR-FW1, FW4, FW6, FW7).

### 6. Аудит и история исполнения; финализация PnL

**Источник:** `.claude-archive/.../processes/Audit/Аудит и история
исполнения.md`; `docs/deprecated/.../TradeFill.md`,
`TradeFillsArchive.md`. **Суть:** аудит/timeline/entry context;
breakdown PnL (fees, fundingFee, gross/net, fills, avg prices, partial
exits); `TradeFill`/`TradeFillsArchive` модели + `REFRESH_FILLS`
(`Deal.resultProfit` считается через них — правило в `Deal.md`);
история command execution. Связано с `open-questions.md` DEAL-Q2.
**Форвард-заметки:** `tasks-deal.md` (DEAL-FW5, FW9), `tasks-balance.md`
(BAL-Q7), `tasks-order.md` (ORD-Q7), `tasks-position.md` (POS-Q5).

### 7. Anomaly / safety / kill-switch

**Источник:** `docs/context/Аварийные executors — семантика статусов
и причин.md`, `docs/context/KillSwitchService — ...md`,
`docs/context/After-snapshot — ...md`. **Суть:** `AnomalyJob`,
`ReconciliationJob` (live risk после terminal / позиция без active
Deal), kill-switch flow. **Форвард-заметки:** `tasks-position.md`
(POS-Q7), `tasks-deal.md` (DEAL-FW7),
`history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`
(ANOM-Q1…Q3).
**Прогресс:** `AnomalyReport` модель+lifecycle мигрированы 2026-05-27
(`docs/models/other/AnomalyReport.md`,
`docs/lifecycles/AnomalyReport.md`); остаются компоненты `AnomalyJob`,
`KillSwitchExecutor`, `TradeRuleValidator`.

### 8. Strategy: enforcement, валидатор, примеры

**Источник:** `Strategy.md` (мигрирован), `Жизненный цикл сделки.md`,
`Strategy API examples.md`, `docs/api/API стратегии.md`. **Суть:**
- enforcement `Strategy.INACTIVE`/`DELETED` (блок новых /
  graceful shutdown) в `EntryScannerJob` + FSM/lifecycle Deal —
  тройная развилка B3 (`rule-source-of-truth.md`).
- валидатор стратегии (12-пунктная валидация key/targetActionKey/
  CLOSE_FULL/partial-exit) — компонент/процесс.
- `Strategy API examples.md` — JSON-примеры (тип reference; уточнить,
  воспроизводить ли как файл знания).
**Форвард-заметки:** `tasks-strategy.md` (STR-FW8, FW9, FW10),
`tasks-deal.md` (DEAL-FW8).

### 9. Exchange модель/lifecycle

**Источник:** упоминания `Exchange.HOLD` (правило —
`docs/rules/exchange-hold.md`); статусы Exchange/Instrument/Account.
**Суть:** полная модель/lifecycle `Exchange` (статус `HOLD` среди
прочих), Instrument/Account. В порядок 6 торговых сущностей не входила.
Сюда же — enforcement `AnomalyReport.Severity` (CRITICAL → торговля по
инструменту остаётся запрещённой; NON_CRITICAL → после kill-switch
может быть разрешена; блокировка живёт в статусе инструмента).
**Форвард-заметки:** `tasks-order.md` (ORD-Q5),
`history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`
(ANOM-Q4).

### 10. API-кластер OKX

**Источник:** `.claude-archive/2026-05-21/docs/api/okx/*` (endpoint-доки,
Playbooks), `docs/api/Справочник по API сервиса.md`. **Суть:** полная
миграция OKX endpoint-доков в `docs/client/okx/` (модели запросов/
ответов, лимиты, auth, особенности). Частично затронуто при миграции
сущностей (balance/position/order/algo mapping). При миграции —
сверить пути/поля, дополнить.

### Отложенные продуктовые вопросы (future)

- `linkedOrderExternalIds` — использование для fills/recovery/audit
  (`tasks-algo-order.md` ALGO-Q6).
- Стандарт описания персистентности доменных моделей: формат и
  версионирование jsonb-снимков (`AnomalyReport.internalBefore/After`,
  `externalBefore/After` и др.). Общий методологический/реализационный
  стандарт, шире одной модели.
  (`history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`
  ANOM-Q5).

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
модели по букве decision (отрицания убрать, оставить только позитив
там, где он живёт).

**Тип:** методологическая ревизия по итогам миграции.

**Сфера — накопительная** (пополняется по мере новых миграций).
Текущие затронутые модели:
- `docs/models/core/Position.md` (§Что Position не хранит);
- `docs/models/core/Order.md` (§Что Order не хранит);
- `docs/models/core/Deal.md` (§Runtime graph — «не входят / не
  хранятся»);
- `docs/models/core/Strategy.md` (§Что Strategy не хранит — через
  архитектурные инварианты);
- `docs/models/other/AnomalyReport.md` (§Чего не хранит).

(При появлении новых мигрированных моделей с таким разделом —
дописывать сюда.)

## Связанные открытые вопросы

Продуктовые открытые вопросы по финализации `Deal` (DEAL-Q1,
DEAL-Q2) — в `.claude/work/questions/open-questions.md`; разбираются
в рамках п.1 / п.6.
