# docs/domain/ — инвентарь для миграции в docs/spec/

> Дата отчёта: 2026-05-15.
> Источник: содержимое `docs/domain/` на момент HEAD = `086ea65`.
> Назначение: рабочий артефакт сессии для подготовки массовой миграции в `docs/spec/` под стандарт ADR-0002.
> Не Project Knowledge. Не основание для правок.

---

## 1. Структура папки `docs/domain/`

```
docs/domain/                                                                  [1 файл]
├── Открытые вопросы по движку.md                                            (root)
├── generated/                                                                [0 файлов]   ← пустая
├── models/                                                                   [8 файлов]
│   ├── AlgoOrder.md
│   ├── Balance.md
│   ├── Deal.md
│   ├── Order.md
│   ├── Position.md
│   ├── Strategy.md
│   ├── Strategy API examples.md
│   ├── Справочник по доменным моделям.md
│   └── mapping/                                                              [4 файла]
│       └── okx/
│           ├── OKX_AlgoOrder_mapping.md
│           ├── OKX_Balance_mapping.md
│           ├── OKX_Order_mapping.md
│           └── OKX_Position_mapping.md
└── processes/                                                                [8 файлов]
    ├── Audit/                                                                [1 файл]
    │   └── Аудит и история исполнения.md
    ├── Calculation/                                                          [3 файла]
    │   ├── Калькуляторы действий стратегии.md
    │   ├── Оценка рисков.md
    │   └── Расчёт индикаторов и рыночных данных.md
    └── Deal management/                                                      [4 файла]
        ├── FSM этапы сделки.md
        ├── Жизненный цикл сделки.md
        ├── Сервисные команды.md
        └── Статусы торговых сущностей.md
```

Итого: **21 markdown-файл**, 1 пустая подпапка (`generated/`).

---

## 2. Инвентарь файлов

Маркер `[MIGRATED → ...]` **не найден ни в одном файле**.

| # | Путь | Строк | Last commit | [MIGRATED]? | Тип |
|---|------|-------|-------------|-------------|------|
| 1 | `docs/domain/Открытые вопросы по движку.md` | 306 | 2026-05-01 | нет | содержательный |
| 2 | `docs/domain/models/AlgoOrder.md` | 1373 | 2026-05-04 | нет | содержательный |
| 3 | `docs/domain/models/Balance.md` | 693 | 2026-05-05 | нет | содержательный |
| 4 | `docs/domain/models/Deal.md` | 1031 | 2026-05-07 | нет | содержательный |
| 5 | `docs/domain/models/Order.md` | 1262 | 2026-05-04 | нет | содержательный |
| 6 | `docs/domain/models/Position.md` | 780 | 2026-05-07 | нет | содержательный |
| 7 | `docs/domain/models/Strategy.md` | 3080 | 2026-05-04 | нет | содержательный |
| 8 | `docs/domain/models/Strategy API examples.md` | 915 | 2026-04-25 | нет | содержательный (примеры) |
| 9 | `docs/domain/models/Справочник по доменным моделям.md` | 447 | 2026-04-24 | нет | справочник / навигация |
| 10 | `docs/domain/models/mapping/okx/OKX_AlgoOrder_mapping.md` | 892 | 2026-05-04 | нет | содержательный |
| 11 | `docs/domain/models/mapping/okx/OKX_Balance_mapping.md` | 567 | 2026-05-05 | нет | содержательный |
| 12 | `docs/domain/models/mapping/okx/OKX_Order_mapping.md` | 898 | 2026-05-04 | нет | содержательный |
| 13 | `docs/domain/models/mapping/okx/OKX_Position_mapping.md` | 747 | 2026-05-05 | нет | содержательный |
| 14 | `docs/domain/processes/Audit/Аудит и история исполнения.md` | 409 | 2026-05-01 | нет | содержательный |
| 15 | `docs/domain/processes/Calculation/Калькуляторы действий стратегии.md` | 1804 | 2026-05-05 | нет | содержательный |
| 16 | `docs/domain/processes/Calculation/Оценка рисков.md` | 816 | 2026-05-07 | нет | содержательный |
| 17 | `docs/domain/processes/Calculation/Расчёт индикаторов и рыночных данных.md` | 1649 | 2026-05-01 | нет | содержательный |
| 18 | `docs/domain/processes/Deal management/FSM этапы сделки.md` | 1863 | 2026-05-07 | нет | содержательный |
| 19 | `docs/domain/processes/Deal management/Жизненный цикл сделки.md` | 2005 | 2026-05-07 | нет | содержательный |
| 20 | `docs/domain/processes/Deal management/Сервисные команды.md` | 2919 | 2026-05-07 | нет | содержательный |
| 21 | `docs/domain/processes/Deal management/Статусы торговых сущностей.md` | 1654 | 2026-05-07 | нет | содержательный |

Чисто навигационных файлов (README/index) **в `docs/domain/` нет**. `Справочник по доменным моделям.md` имеет смешанный характер: это короткий глоссарий с пояснениями инвариантов — содержательный, но генрово выпадает (см. §3).

Содержательных файлов: **20** (все, кроме `generated/` — пустая папка).

---

## 3. Характеристика содержательных файлов

> Используемая раскладка жанров ADR-0002: `model` / `lifecycle` / `process` / `integration mapping` / `reference` / `invariant`.

Глобальные наблюдения по всему массиву:

* **Ни в одном файле нет ссылок на ADR.** Грэп по `ADR` дал 0 совпадений по всей `docs/domain/`.
* **Ни в одном файле нет markdown-link синтаксиса `[text](url)`.** Все перекрёстные ссылки оформлены как текстовые: «см. в документе `Имя.md`».
* Перекрёстные ссылки идут по голому имени файла без пути (например, `см. Order.md`), что станет проблемой при переезде в `docs/spec/...` — потребуется массовое обновление.
* В большинстве содержательных документов есть секция «Архитектурные инварианты ...» — инварианты вшиты в model/lifecycle/process документы и нигде не вынесены в отдельный invariant-файл по ADR-0002.

---

### 3.1. `docs/domain/Открытые вопросы по движку.md` (306 строк)

* **Тема:** рабочий список нерешённых вопросов по runtime-движку (Active/Closed semantics, ErrorHandler/safety-flow, AnomalyJob, порядок actions в шаге, refresh baseline по статусам, protection policy, validation matrix, executor idempotency, конкурентность, DealContextService, CalculationError, RiskCheckCode).
* **Жанр ADR-0002:** **вне жанров ADR-0002** — это backlog / список открытых вопросов, не model / lifecycle / process / mapping / reference / invariant.
* **Доменные модели:** Order, AlgoOrder, AttachedAlgoOrder, Position, Deal, BalanceContainer, BalanceSnapshot, DealActionState, RefreshExecutor, ExitPendingHandler, ErrorHandler, KillSwitchService, AnomalyJob, DealContextService, ReconciliationJob, DealOrchestratorJob.
* **Внешние ссылки:** упоминает `05. Аудит и история исполнения` как контекст. Без `docs/...` путей.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** нет (только text-fenced блоки).
* **Addenda / открытые вопросы:** **сам по себе** — это весь файл. Уровень: 13 пронумерованных открытых вопросов.

---

### 3.2. `docs/domain/models/AlgoOrder.md` (1373 строки)

* **Тема:** целевая доменная модель standalone `AlgoOrder` (статусы, причины финализации, condition-модель, external snapshot, refresh/recovery, cancel/amend, связь с `DealActionState`).
* **Жанр ADR-0002:** **model**, уверенно. С большим блоком встроенных invariants («Главные инварианты» — §2).
* **Доменные модели:** AlgoOrder, Condition, Trigger, Trailing, TriggerPrice, AlgoOrderExternalSnapshot, AlgoOrderExternalStatusResolver, DealActionState, RuntimeTarget, StrategyAlgoOrderAction.
* **Внешние ссылки:** Статусы торговых сущностей.md, Сервисные команды.md, FSM этапы сделки.md, Жизненный цикл сделки.md, Strategy.md, Order.md, OKX_AlgoOrder_mapping.md (все textually).
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** есть, ~13 блоков (доменные классы, enums, Condition/Trigger полная иерархия).
* **Addenda / открытые вопросы:** нет.

---

### 3.3. `docs/domain/models/Balance.md` (693 строки)

* **Тема:** доменные модели `BalanceContainer` и `Balance` (account-state snapshot), freshness-policy, `REFRESH_BALANCE`, участие в `DealContext`, `CalculationContext`, `RiskValidator`.
* **Жанр ADR-0002:** **model + invariant**, под вопрос — много process-вкраплений про `REFRESH_BALANCE` runtime-flow.
* **Доменные модели:** BalanceContainer, Balance, BalanceExternalSnapshot, BalanceContainerExternalSnapshot, DealContext, RiskValidator, CalculationContext, RefreshBalanceExecutor.
* **Внешние ссылки:** OKX_Balance_mapping.md (textually).
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~6 блоков.
* **Addenda / открытые вопросы:** нет явных «Дополнение после Q...» секций.

---

### 3.4. `docs/domain/models/Deal.md` (1031 строка)

* **Тема:** финальная доменная модель `Deal` — lifecycle root, runtime graph, `Deal.Status`, `EntryReason`, `entryStepType`, `CloseReason`, `ShutdownReason`, `resultProfit`, runtime graph, граница с `DealContext` / `DealActionState`.
* **Жанр ADR-0002:** **model**, уверенно. Сильный invariant-блок («Главные инварианты»), есть мини-lifecycle-фрагмент по `Deal.Status` (§5) — формально model, но близко граничит с lifecycle.
* **Доменные модели:** Deal, DealContext, DealActionState, Order, AlgoOrder, Position, BalanceContainer, TradeFill, Strategy, StrategyDetail.
* **Внешние ссылки:** Жизненный цикл сделки.md, FSM этапы сделки.md, Сервисные команды.md, Статусы торговых сущностей.md, Order.md, AlgoOrder.md, Position.md, Balance.md, Оценка рисков.md, Аудит и история исполнения.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~7 блоков (доменный класс `Deal`, enums статусов и причин).
* **Addenda / открытые вопросы:** нет.

---

### 3.5. `docs/domain/models/Order.md` (1262 строки)

* **Тема:** актуальная модель ordinary `Order` и `AttachedAlgoOrder`; external snapshots, status resolvers, attached protection resolving, missing-protection-policy, OKX-status mapping.
* **Жанр ADR-0002:** **model**, уверенно. Содержит секции, которые тяготеют к **integration mapping** (OKX status mapping — §7.2, exchange invariants — §2.1) — смешанный жанр.
* **Доменные модели:** Order, AttachedAlgoOrder, OrderExternalSnapshot, AttachedAlgoOrderExternalSnapshot, OrderExternalStatusResolver, ExternalStatusException.
* **Внешние ссылки:** Статусы торговых сущностей.md, Сервисные команды.md, FSM этапы сделки.md, Жизненный цикл сделки.md, Strategy.md, Оценка рисков.md, OKX_Order_mapping.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~4 крупных блока (доменные классы, enums).
* **Addenda / открытые вопросы:** нет.

---

### 3.6. `docs/domain/models/Position.md` (780 строк)

* **Тема:** доменная модель `Position` — live-risk semantics, refresh policy, связь с `CLOSE_POSITION`, роль в финализации сделки.
* **Жанр ADR-0002:** **model**, уверенно. Invariant-блок в §2.
* **Доменные модели:** Position, PositionExternalSnapshot, PositionStatusResolver, RefreshPositionExecutor, ClosePositionExecutor.
* **Внешние ссылки:** OKX_Position_mapping.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~5 блоков.
* **Addenda / открытые вопросы:** нет.

---

### 3.7. `docs/domain/models/Strategy.md` (3080 строк)

* **Тема:** strategy-layer как целое: `Strategy`, `StrategyDetail`, `StrategyMarketPhaseSetting`, `MarketPhaseParams`, `StrategyIndicatorSetting`, `IndicatorParams`, `StrategyMarketStructureSetting`, `MarketStructureParams`, `StrategyStep`, `StrategyStepType`, `StrategyMarketDataExpiredSetting`, `StrategyCondition`, `StrategyConditionRule`, `StrategyConditionRuleType`, `StrategyConditionSourceType`, `StrategyConditionOperator`, `StrategyConditionOperand`, `StrategyAction`, `StrategyOrderAction`, `StrategyAlgoOrderAction`, `StrategyPositionAction`, `StrategyTradeDirection`, `StrategyPricePlacement`, `StrategyAttachedProtectionSettings`, `StopLossSettings`, `TrailingSettings`. ~34 пронумерованных секции верхнего уровня.
* **Жанр ADR-0002:** **model**, **смешанный жанр** — фактически это «куча моделей в одном файле». По ADR-0002 должен быть распилен на несколько model-документов, плюс отдельный invariant-документ.
* **Доменные модели:** все strategy-layer (см. выше), плюс ссылки на DealActionState, RuntimeTarget, ServiceCommand, StrategyActionCalculator, RiskValidator, RiskBlockResolver, CalculationContext, PriceCalculator, SizeCalculator, RiskCalculator, MarketPriceData, InstrumentExternalRules, RiskCheckResult, RiskCheckCode, RiskDecision, IndicatorJob, IndicatorValue, MarketStructureJob, MarketStructure, MarketPriceLevel, MarketPhaseJob, MarketPhase, MarketPhaseService, EntryScannerJob.
* **Внешние ссылки:** очень много — Жизненный цикл сделки, Сервисные команды, Калькуляторы действий стратегии, Расчёт индикаторов и рыночных данных, Аудит и история исполнения, Оценка рисков (множественные textually).
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** **~44 блока** — самая «кодовая» дока (доменные классы, enums, jsonb-параметры). Сильно увеличивает stop-the-world нагрузку для миграции.
* **Addenda / открытые вопросы:** есть подсекции «Уточнение после Q...» в §33 (§33.1 после Q2-Q8, §33.2 после Q7). Полноценного финального блока «Дополнение после Q2-Q8» — нет.

---

### 3.8. `docs/domain/models/Strategy API examples.md` (915 строк)

* **Тема:** JSONC-примеры strategy-layer для трёх сценариев (BULL_TREND/FOLLOW_PHASE, BEAR_TREND/FOLLOW_PHASE с partial exit, RANGE/GRID).
* **Жанр ADR-0002:** **вне жанров ADR-0002** — это аннотированные примеры конфигурации. Ближайший подходящий жанр — «reference» (как примеры/use-cases), но содержательно это example annex для Strategy.md, не самостоятельная reference.
* **Доменные модели:** StrategyPricePlacement, StrategyConditionRule, StopLossSettings, StopLossCalculationType, StrategyAction (через JSONC).
* **Внешние ссылки:** нет явных textual cross-doc references.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** нет (это JSONC).
* **Addenda / открытые вопросы:** нет.

---

### 3.9. `docs/domain/models/Справочник по доменным моделям.md` (447 строк)

* **Тема:** короткий справочник по доменным моделям по пакетам: `domain.model.trade` (Strategy, StrategyDetails, MarketPhase, Candle, CandleGroup, PriceTicker), `domain.model.core` (Deal, Order, AlgoOrder, Position и т.д.), `domain.model.core` snapshot, `Auditable`, anomaly/kill-switch (AnomalyReport, ServiceCommandExecutionHistory).
* **Жанр ADR-0002:** **reference**, под вопрос — смешан с короткими invariant-листами для каждой модели. Альтернатива — это глоссарий, который мог бы жить как `docs/GLOSSARY.md` (упоминается в `README.md`).
* **Доменные модели:** **очень много, ~30+ моделей** упомянуто (Strategy, StrategyDetails, MarketPhase, Candle, CandleGroup, PriceTicker, Deal, Order, AttachedAlgoOrder, AlgoOrder, Position, Instrument, BalanceContainer, Balance, TradeFill, MarketStructure, MarketPriceLevel, IndicatorValue, MarketPriceData, InstrumentExternalRules, Auditable, AnomalyReport, ServiceCommandExecutionHistory).
* **Внешние ссылки:** нет.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** нет.
* **Addenda / открытые вопросы:** нет; есть §1.8 «Что дальше шлифовать» — мета-комментарий о ToDo шлифовки.

---

### 3.10. `docs/domain/models/mapping/okx/OKX_AlgoOrder_mapping.md` (892 строки)

* **Тема:** mapping OKX request/response DTO в доменный `AlgoOrder`; endpoints, request/response DTO, snapshots, status resolver, invariant checks.
* **Жанр ADR-0002:** **integration mapping**, уверенно.
* **Доменные модели:** AlgoOrder, AlgoOrderResponse, AlgoOrderExternalSnapshot, AlgoOrderExternalStatusResolver, Condition, Trigger, TriggerPrice, Trailing.
* **Внешние ссылки:** AlgoOrder.md, Жизненный цикл сделки.md, FSM этапы сделки.md, Сервисные команды.md, Оценка рисков.md, Аудит и история исполнения.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** есть (точно не сосчитан, но >0; в общем подсчёте не показался в первом грэпе — вероятно встречаются ниже).
* **Addenda / открытые вопросы:** нет.

---

### 3.11. `docs/domain/models/mapping/okx/OKX_Balance_mapping.md` (567 строк)

* **Тема:** OKX balance endpoint, raw response → `BalanceContainerExternalSnapshot` → доменный `BalanceContainer / Balance`. Mapping политика, validation-only поля, error policy.
* **Жанр ADR-0002:** **integration mapping**, уверенно.
* **Доменные модели:** BalanceContainer, Balance, BalanceContainerExternalSnapshot, BalanceExternalSnapshot, RefreshBalanceExecutor.
* **Внешние ссылки:** Balance.md, Статусы торговых сущностей.md, Сервисные команды.md, FSM этапы сделки.md, Жизненный цикл сделки.md, Оценка рисков.md, Калькуляторы действий стратегии.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~2 блока.
* **Addenda / открытые вопросы:** нет.

---

### 3.12. `docs/domain/models/mapping/okx/OKX_Order_mapping.md` (898 строк)

* **Тема:** OKX request/response DTO → доменные `Order` / `AttachedAlgoOrder`; endpoints, snapshots, status resolver, attached-protection mapping.
* **Жанр ADR-0002:** **integration mapping**, уверенно.
* **Доменные модели:** Order, AttachedAlgoOrder, OrderResponse, OrderExternalSnapshot, AttachedAlgoOrderExternalSnapshot, OrderExternalStatusResolver, CreateOrderRequest, AmendOrderRequest, CancelOrderRequest, GetOrderDetailsSearchParams, GetOrdersPendingSearchParams, GetOrdersHistorySearchParams, GetOrdersHistoryArchiveSearchParams.
* **Внешние ссылки:** Order.md, Жизненный цикл сделки.md, FSM этапы сделки.md, Сервисные команды.md, Оценка рисков.md, Аудит и история исполнения.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~6 блоков.
* **Addenda / открытые вопросы:** нет.

---

### 3.13. `docs/domain/models/mapping/okx/OKX_Position_mapping.md` (747 строк)

* **Тема:** OKX positions endpoint → `PositionExternalSnapshot` → доменная `Position`; close-position request mapping, ACK semantics.
* **Жанр ADR-0002:** **integration mapping**, уверенно.
* **Доменные модели:** Position, PositionExternalSnapshot, PositionStatusResolver, RefreshPositionExecutor, ClosePositionExecutor.
* **Внешние ссылки:** Position.md, Статусы торговых сущностей.md, Сервисные команды.md, FSM этапы сделки.md, Жизненный цикл сделки.md, Оценка рисков.md, Аудит и история исполнения.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~1 блок.
* **Addenda / открытые вопросы:** нет.

---

### 3.14. `docs/domain/processes/Audit/Аудит и история исполнения.md` (409 строк)

* **Тема:** черновик / каркас аудита и истории исполнения сделки; общие договорённости, инварианты, открытые вопросы.
* **Жанр ADR-0002:** **смешанный жанр** — частично **process** (что аудит делает), частично **invariant** («инварианты аудита» §2), частично backlog (§5 «Открытые вопросы», §8 «Открытые вопросы после Q2-Q8»). Статус документа — «рабочий каркас», не финализирован.
* **Доменные модели:** ServiceCommandExecutionHistory, DealActionState, DealContext, AnomalyReport, Deal, Order, AlgoOrder, Position, BalanceContainer.
* **Внешние ссылки:** Strategy.md, 01. Жизненный цикл сделки, FSM этапы сделки, 02. Сервисные команды, 03. Калькуляторы действий стратегии, 04. Расчёт индикаторов и рыночных данных, Оценка рисков.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** нет (отсутствует в счётчике).
* **Addenda / открытые вопросы:** **да**, обширно — §5 «Открытые вопросы» (3 подсекции), §7 «Дополнение после Q2-Q8» (6 подсекций), §8 «Открытые вопросы после Q2-Q8» (3 подсекции).

---

### 3.15. `docs/domain/processes/Calculation/Калькуляторы действий стратегии.md` (1804 строки)

* **Тема:** `StrategyActionCalculator`, `PriceCalculator`, `SizeCalculator`, `CalculationContext`, `InstrumentExternalRules`, `MarketPriceData`, `IndicatorValue`, `MarketStructure`, `MarketPriceLevel`, `MarketPhase`, формулы расчёта цены, размера; controlled calculation errors.
* **Жанр ADR-0002:** **process**, **смешанный жанр** — содержит mini-models для CalculatedPrice, CalculatedSize, CalculationContext (фактически model-фрагменты), invariant-блок («Архитектурные инварианты calculator-layer»), процессный flow.
* **Доменные модели:** StrategyActionCalculator, CalculationContext, CalculatedStrategyAction, CalculatedPrice, CalculatedSize, CalculationError, PriceCalculator, SizeCalculator, RiskCalculator, InstrumentExternalRules, MarketPriceData, IndicatorValue, MarketStructure, MarketPriceLevel, MarketPhase, BalanceContainer, ServiceCommand, ServiceCommandFactory, RiskValidator, RiskBlockResolver, StrategyAction, StrategyPricePurpose, StopLossSettings, TrailingSettings.
* **Внешние ссылки:** 01. Жизненный цикл сделки, 02. Сервисные команды, Оценка рисков, 04. Расчёт индикаторов и рыночных данных, FSM этапы сделки, 05. Аудит и история исполнения.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~26 блоков.
* **Addenda / открытые вопросы:** есть «Уточнение после Q3» в §3.1, «Scope CalculationContext после Q7» в §4.1, «Controlled errors vs unexpected exceptions после Q8» в §20.1, «ServiceCommandFactory после Q4» в §21.1.

---

### 3.16. `docs/domain/processes/Calculation/Оценка рисков.md` (816 строк)

* **Тема:** risk-layer — `RiskValidator`, `RiskValidationResult`, `RiskCheckResult`, `RiskCheckCode`, `RiskBlockResolver`, `RiskBlockAction`, политика реакции FSM на `BLOCKED` по статусам, связь с `CalculationError`, unexpected exceptions.
* **Жанр ADR-0002:** **process**, под вопрос — много model-фрагментов (RiskValidationResult/RiskCheckResult — фактически models), invariants по правилам risk-layer вшиты в §12.
* **Доменные модели:** RiskValidator, RiskValidationResult, RiskCheckResult, RiskCheckCode, RiskBlockResolver, RiskBlockAction, RiskDecision, CalculatedStrategyAction, BalanceContainer, DealContext, ServiceCommandFactory.
* **Внешние ссылки:** Strategy.md, 01. Жизненный цикл сделки, FSM этапы сделки, 02. Сервисные команды, 03. Калькуляторы действий стратегии, 04. Расчёт индикаторов и рыночных данных.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~7 блоков.
* **Addenda / открытые вопросы:** нет явных «Дополнение после Q...» секций.

---

### 3.17. `docs/domain/processes/Calculation/Расчёт индикаторов и рыночных данных.md` (1649 строк)

* **Тема:** job-layer подготовки рыночных данных: `CandleJob`, `InstrumentExternalRulesSyncJob`, `IndicatorJob`, `MarketStructureJob`, `MarketPhaseJob`; доменные модели результатов `IndicatorValue`, `MarketStructure`, `MarketPriceLevel`, `MarketPhase`, `MarketPriceData`, `InstrumentExternalRules`, `TimeFrame`; `MarketDataExpirationChecker`; использование данных в `EntryScannerJob`, `StrategyConditionEvaluator`, `StrategyActionCalculator`.
* **Жанр ADR-0002:** **смешанный жанр** — наполовину **process** (jobs, freshness check, EntryScannerJob flow), наполовину **model** (множество моделей: IndicatorValue, MarketStructure, MarketPriceLevel, MarketPhase, MarketPriceData, InstrumentExternalRules, TimeFrame, IndicatorParams, MarketStructureParams, MarketPhaseParams). По ADR-0002 это полноценный кандидат на расщепление.
* **Доменные модели:** CandleJob, InstrumentExternalRulesSyncJob, InstrumentExternalRulesExternalSnapshot, InstrumentExternalRules, InstrumentType, ContractType, MarketPriceData, MarketPriceDataExternalSnapshot, TimeFrame, IndicatorJob, StrategyIndicatorSetting, IndicatorParams, IndicatorValue, IndicatorService, MarketStructureJob, StrategyMarketStructureSetting, MarketStructureParams, MarketStructure, MarketPriceLevel, MarketStructureService, MarketPhaseJob, StrategyMarketPhaseSetting, MarketPhaseParams, MarketPhase, MarketPhaseService, MarketDataExpirationChecker, EntryScannerJob, StrategyConditionEvaluator, StrategyActionCalculator.
* **Внешние ссылки:** Strategy.md, 01. Жизненный цикл сделки, 03. Калькуляторы действий стратегии, 02. Сервисные команды, FSM этапы сделки, 05. Аудит и история исполнения, Оценка рисков.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~37 блоков (доменные классы моделей результатов).
* **Addenda / открытые вопросы:** §34 «Дополнение после Q2-Q8: граница рыночных данных, риска и ошибок».

---

### 3.18. `docs/domain/processes/Deal management/FSM этапы сделки.md` (1863 строки)

* **Тема:** подробный регламент FSM handlers по каждому `Deal.Status` (PRECHECK, ENTRY_SUBMITTED, ENTRY_FINALIZED, PROTECTION_SWITCHED, MANAGING, EXIT_PENDING, ERROR, CLOSED, EMERGENCY_CLOSED): входные/рабочие/выходные проверки, переходы, допустимые steps, возможные ServiceCommand.
* **Жанр ADR-0002:** **lifecycle**, уверенно. Содержит свой блок «Архитектурные инварианты FSM».
* **Доменные модели:** DealStateMachine, Deal.Status, DealContext, DealActionState, StrategyDetail, StrategyStep, StrategyAction, StrategyCondition, StrategyActionCalculator, RiskValidator, RiskBlockResolver, MarketDataExpirationChecker, ServiceCommand, REFRESH_*, CREATE_*, SUBMIT_*, CANCEL_*, CLOSE_POSITION, EXECUTE_KILL_SWITCH, ExitPendingHandler, ErrorHandler, AnomalyJob.
* **Внешние ссылки:** Жизненный цикл сделки, Strategy.md, Сервисные команды, 04. Расчёт индикаторов и рыночных данных, 05. Аудит и история исполнения, Оценка рисков.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** в общем грэпе по `^```java` не показался — кодовые блоки скорее всего text-fenced (схемы flow, не классы).
* **Addenda / открытые вопросы:** §13 «Дополнение после Q2-Q8: обработка risk/calculation/command-flow».

---

### 3.19. `docs/domain/processes/Deal management/Жизненный цикл сделки.md` (2005 строк)

* **Тема:** основная процессная дока — общая карта жизненного цикла сделки: 4 больших процесса (расчёт данных, lifecycle, калькуляторы, команды), зоны ответственности (IndicatorJob, EntryScannerJob, DealOpeningService, DealOrchestratorJob, DealStateMachine, StrategyActionCalculator), `Deal`, `DealContext`, `CalculationContext`, `DealActionState`, тезисы по ServiceCommand, FSM статусы (краткие профили), восстановление после рестарта, AnomalyJob/kill-switch, торговые ограничения проекта.
* **Жанр ADR-0002:** **lifecycle** + **process**, смешанный жанр. Это «зонтичный» документ — даёт обзорную карту, частично пересекается с FSM этапы сделки.md (тоже lifecycle) и Сервисные команды.md (тоже process).
* **Доменные модели:** Deal, DealContext, CalculationContext, DealActionState, ServiceCommand, IndicatorJob, MarketStructureJob, MarketPhaseJob, EntryScannerJob, DealOpeningService, DealOrchestratorJob, DealStateMachine, StrategyActionCalculator, PriceCalculator, SizeCalculator, RiskCalculator, RiskValidator, RiskBlockResolver, ServiceCommandFactory, ServiceCommandExecutor, AnomalyJob, KillSwitch.
* **Внешние ссылки:** Deal.md, 02. Сервисные команды, 03. Калькуляторы действий стратегии, 04. Расчёт индикаторов и рыночных данных, 05. Аудит и история исполнения, Оценка рисков.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~3 блока.
* **Addenda / открытые вопросы:** §14 «Дополнение после Q2-Q8: risk-layer, calculation-flow и command-flow» с подсекциями.

---

### 3.20. `docs/domain/processes/Deal management/Сервисные команды.md` (2919 строк — крупнейший файл)

* **Тема:** command-layer — `ServiceCommand`, payload каждого типа (CREATE_*, SUBMIT_*, AMEND_*, CANCEL_*, CLOSE_POSITION, REFRESH_*), `ServiceCommandFactory`, `ServiceCommandExecutor`, executor'ы, CREATE→SUBMIT→REFRESH цепочка, `DealActionState`, retry policy, политика по controlled exchange exceptions, статусы runtime-сущностей (дублирует часть Статусы.md).
* **Жанр ADR-0002:** **process** + **integration mapping** + **reference**, **смешанный жанр**. Внутри документа есть полные каталоги payloads (фактически reference), модель `DealActionState` (фактически model), процессные правила, retry-policy (фактически invariant-set), и **§12 «Статусы runtime-сущностей»** дублирует Статусы торговых сущностей.md (см. §7 рисков).
* **Доменные модели:** ServiceCommand, ServiceCommandType (вся таблица), ServiceCommandFactory, ServiceCommandExecutor, CREATE_ORDER/SUBMIT_ORDER/AMEND_ORDER/CANCEL_ORDER, CREATE_ALGO_ORDER/SUBMIT_ALGO_ORDER/AMEND_ALGO_ORDER/CANCEL_ALGO_ORDER, CLOSE_POSITION, REFRESH_BALANCE/REFRESH_POSITION/REFRESH_ORDER/REFRESH_ALGO_ORDER/REFRESH_FILLS, EXECUTE_KILL_SWITCH, DealActionState, RuntimeTarget, TargetEntityType, DealActionStateStatus, RetryPolicyService, RetryError, MarketDataExpirationChecker, RiskValidator, RiskBlockResolver, ServiceCommandExecutionHistory, OrderExternalStatusResolver, AlgoOrderExternalStatusResolver, PositionStatusResolver, BalanceContainerExternalSnapshot.
* **Внешние ссылки:** Жизненный цикл сделки, FSM этапы сделки, Strategy.md, 04. Расчёт индикаторов и рыночных данных, 05. Аудит и история исполнения, Оценка рисков.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~33 блока.
* **Addenda / открытые вопросы:** «Дополнение после Q5:» (§11.4 после Q6 и Q8, §11.7, §18 «Дополнение после Q2-Q8»).

---

### 3.21. `docs/domain/processes/Deal management/Статусы торговых сущностей.md` (1654 строки)

* **Тема:** статусная семантика всех торговых сущностей (Exchange, Instrument, Strategy, Deal, DealActionState, Order, AlgoOrder, Position, BalanceContainer); resolver внешних статусов, external exchange exception policy, cleanup rules (EXIT_PENDING, ErrorHandler, KillSwitch), anomaly rules, component impact matrix, минимальные правила для кода.
* **Жанр ADR-0002:** **reference** + **invariant**, **смешанный жанр**. Если строго следовать ADR-0002, это должен быть отдельный reference (status taxonomy) + invariant (правила) + интеграционные ссылки на mapping-документы. Документ сам себя называет «основным источником истины по статусам торговых сущностей».
* **Доменные модели:** Exchange, ExchangeAccount, Instrument, Strategy, Deal, DealActionState, Order, AlgoOrder, Position, BalanceContainer (плюс все их Status enums), external status resolvers, AnomalyJob, ErrorHandler, KillSwitch, ExternalStatusException, ControlledExchangeException.
* **Внешние ссылки:** Strategy.md, Deal.md, Жизненный цикл сделки.md, FSM этапы сделки.md, Сервисные команды.md, Оценка рисков.md, Position.md, OKX_Position_mapping.md, Balance.md, OKX_Balance_mapping.md, Открытые вопросы по движку.md.
* **Ссылки на ADR:** нет.
* **Java-сниппеты:** ~3 блока.
* **Addenda / открытые вопросы:** нет явных Q-секций.

---

## 4. Срез по подпапкам `docs/domain/`

### 4.1. `docs/domain/` (root)

Только один файл — `Открытые вопросы по движку.md`. Это backlog нерешённых вопросов по runtime-движку. Содержательно не повторяет ни одной из подпапок, но ссылается на темы из `processes/`.

### 4.2. `docs/domain/generated/`

Пустая папка. Содержательно ничего нет, скорее всего placeholder для будущих авто-сгенерированных артефактов. **Кандидат на не-миграцию.**

### 4.3. `docs/domain/models/`

8 файлов: 6 доменных моделей (AlgoOrder, Balance, Deal, Order, Position, Strategy), 1 файл JSONC-примеров (Strategy API examples), 1 справочник (Справочник по доменным моделям). Внутренне умеренно связана: модели хорошо изолированы по сущностям, но Deal.md, Order.md, AlgoOrder.md, Position.md повторно ссылаются друг на друга и на process-доки в `processes/Deal management/`. Strategy.md заметно «толще» остальных и содержит большую группу sub-models, которые по ADR-0002 должны жить как отдельные документы. `Справочник по доменным моделям.md` — глоссарий, очевидной границы с другими файлами `models/` нет: дублирует кратко содержание других файлов.

### 4.4. `docs/domain/models/mapping/okx/`

4 файла, по одному на сущность (AlgoOrder/Balance/Order/Position). Внутренне очень связаны структурно (все следуют одинаковому шаблону: «Назначение → Приоритет источников → Границы ответственности → endpoints → request DTO → response → snapshot → status resolver»). Чёткая граница с `docs/domain/models/` (доменная модель vs. exchange mapping) и с `docs/domain/processes/` (mapping vs. process).

### 4.5. `docs/domain/processes/Audit/`

1 файл — `Аудит и история исполнения.md`. Документ помечен «рабочий каркас», много open-questions. По ADR-0002 это пограничный документ: должен либо стать частью lifecycle-документации (если аудит/timeline войдут как persisted history), либо отдельным process-документом. Граница с `Deal management/` тонкая: аудит явно касается жизненного цикла сделки.

### 4.6. `docs/domain/processes/Calculation/`

3 файла: Калькуляторы действий стратегии, Оценка рисков, Расчёт индикаторов и рыночных данных. Внутренне сильно связаны: калькуляторы используют рыночные данные, риск работает после калькулятора. Однако `Расчёт индикаторов и рыночных данных.md` содержит **большое количество доменных моделей** (IndicatorValue, MarketStructure, MarketPhase, MarketPriceData, InstrumentExternalRules), что выглядит как перетекание содержания из `models/` в `processes/`.

### 4.7. `docs/domain/processes/Deal management/`

4 файла: FSM этапы сделки, Жизненный цикл сделки, Сервисные команды, Статусы торговых сущностей. Это самая «толстая» подпапка (8441 строк суммарно из 21300 общих, ~40%). Внутренне связаны, но местами **дублируют друг друга**: статусы Order/AlgoOrder/Position описаны и в `Статусы торговых сущностей.md`, и в `Сервисные команды.md` (§12), и в самих model-документах (`Order.md`/`AlgoOrder.md`/`Position.md`). FSM этапы сделки и Жизненный цикл сделки тоже частично пересекаются (FSM-статусы кратко описаны в Жизненный цикл сделки, подробно — в FSM этапы сделки).

---

## 5. Кластеризация по содержанию (9 кластеров)

### Кластер C1: Deal core + lifecycle (FSM)

* **Файлы:**
  * `docs/domain/models/Deal.md`
  * `docs/domain/processes/Deal management/Жизненный цикл сделки.md`
  * `docs/domain/processes/Deal management/FSM этапы сделки.md`
* **Доминирующая тема:** `Deal`, `Deal.Status`, FSM handlers, `DealContext`, `DealActionState`, lifecycle переходы, recovery.
* **Жанровая раскладка:** 1 model (`Deal.md`) + 1 lifecycle (`FSM этапы сделки.md`) + 1 lifecycle+process (`Жизненный цикл сделки.md`, смешанный).
* **Внешние связи:** ссылается на C2 (Order/AlgoOrder/Position), C4 (Balance), C6 (market data), C7 (calculator/risk/command), C8 (status taxonomy + audit).

### Кластер C2: Trading entities (Order, AlgoOrder, Position)

* **Файлы:**
  * `docs/domain/models/Order.md`
  * `docs/domain/models/AlgoOrder.md`
  * `docs/domain/models/Position.md`
* **Доминирующая тема:** runtime-сущности биржи: ordinary order + attached protection, standalone algo-order, позиция, external snapshots, status resolvers, live-risk semantics.
* **Жанровая раскладка:** 3 model. Order.md дополнительно несёт integration mapping fragments (§7.2 OKX status mapping) — частично смешанный.
* **Внешние связи:** C1 (Deal lifecycle), C3 (OKX mapping), C7 (command-layer), C8 (status taxonomy).

### Кластер C3: OKX integration mappings

* **Файлы:**
  * `docs/domain/models/mapping/okx/OKX_Order_mapping.md`
  * `docs/domain/models/mapping/okx/OKX_AlgoOrder_mapping.md`
  * `docs/domain/models/mapping/okx/OKX_Position_mapping.md`
  * `docs/domain/models/mapping/okx/OKX_Balance_mapping.md`
* **Доминирующая тема:** маппинг OKX endpoints/DTO в доменные модели; rate limits, ACK policy, status resolver, error policy.
* **Жанровая раскладка:** 4 integration mapping, уверенно.
* **Внешние связи:** C2 (Order/AlgoOrder/Position модели), C4 (Balance модель), C7 (command-layer для request flow), C8 (status taxonomy через resolver).

### Кластер C4: Balance / account snapshot

* **Файлы:**
  * `docs/domain/models/Balance.md`
  * `docs/domain/models/mapping/okx/OKX_Balance_mapping.md` (физически лежит в C3, но содержательно теснее связан с Balance)
* **Доминирующая тема:** `BalanceContainer`, `Balance`, freshness-policy, `REFRESH_BALANCE`, участие в `DealContext` / `CalculationContext` / `RiskValidator`.
* **Жанровая раскладка:** 1 model + 1 integration mapping. Balance.md содержит process-вкрапления.
* **Внешние связи:** C1 (DealContext freshness check в FSM), C7 (RiskValidator, REFRESH_BALANCE командой).
* *Заметка:* OKX_Balance_mapping.md одновременно входит в C3 как integration mapping и в C4 содержательно — границы перекрываются.

### Кластер C5: Strategy model

* **Файлы:**
  * `docs/domain/models/Strategy.md`
  * `docs/domain/models/Strategy API examples.md`
* **Доминирующая тема:** strategy-layer как immutable конфигурация: `Strategy`, `StrategyDetail`, `StrategyAction` (Order/AlgoOrder/Position), conditions, pricing/sizing settings, market phase settings, indicator settings, JSONC примеры.
* **Жанровая раскладка:** 1 model (Strategy.md, но он смешанный — фактически коллекция моделей) + 1 «вне жанров ADR-0002» (Strategy API examples — example annex / reference).
* **Внешние связи:** C1 (Deal через pinned StrategyDetail), C6 (рыночные данные/индикаторы), C7 (калькуляторы используют strategy settings).

### Кластер C6: Market data jobs and models

* **Файлы:**
  * `docs/domain/processes/Calculation/Расчёт индикаторов и рыночных данных.md`
* **Доминирующая тема:** job-уровень подготовки рыночных данных: CandleJob, IndicatorJob, MarketStructureJob, MarketPhaseJob, InstrumentExternalRulesSyncJob; модели результатов (IndicatorValue, MarketStructure, MarketPriceLevel, MarketPhase, MarketPriceData, InstrumentExternalRules, TimeFrame); MarketDataExpirationChecker.
* **Жанровая раскладка:** 1 файл, **смешанный жанр** — process (jobs + flow) + множество model-фрагментов. Кандидат на разделение.
* **Внешние связи:** C5 (strategy settings определяют, что считать), C7 (калькуляторы потребляют результаты), C1 (FSM проверяет freshness через MarketDataExpirationChecker).

### Кластер C7: Calculator + Risk + Command layer

* **Файлы:**
  * `docs/domain/processes/Calculation/Калькуляторы действий стратегии.md`
  * `docs/domain/processes/Calculation/Оценка рисков.md`
  * `docs/domain/processes/Deal management/Сервисные команды.md`
* **Доминирующая тема:** цепочка `StrategyAction → StrategyActionCalculator → RiskValidator → ServiceCommandFactory → ServiceCommand → Executor`; CalculationContext, CalculatedPrice/Size, RiskValidationResult, RiskBlockResolver, retry policy, payloads команд.
* **Жанровая раскладка:** 3 process, все три смешанные с model/invariant фрагментами. Сервисные команды.md дополнительно содержит status-таксономию runtime-сущностей (дубль).
* **Внешние связи:** C1 (FSM вызывает калькулятор и формирует команды), C2 (команды модифицируют Order/AlgoOrder/Position), C3 (executor работает через OKX adapter), C5 (Strategy задаёт risk policy и action sizing), C6 (CalculationContext тянет market data).

### Кластер C8: Status taxonomy + Audit

* **Файлы:**
  * `docs/domain/processes/Deal management/Статусы торговых сущностей.md`
  * `docs/domain/processes/Audit/Аудит и история исполнения.md`
* **Доминирующая тема:** доменные статусы всех торговых сущностей и общий resolver-pattern для external status; аудит и история исполнения, timeline сделки.
* **Жанровая раскладка:** 1 reference+invariant (смешанный) + 1 process-черновик с большим количеством open questions. Жанр «invariant» по ADR-0002 ярко представлен.
* **Внешние связи:** все остальные кластеры (ссылка на статусную семантику пронизывает весь docs/domain).
* *Заметка:* Status taxonomy и Audit можно было бы разделить как два кластера. Объединены, потому что оба обслуживают «как объяснить и распознать состояние» — meta-слой над основными моделями и процессами.

### Кластер C9: Глоссарий + backlog (не-ADR-0002)

* **Файлы:**
  * `docs/domain/models/Справочник по доменным моделям.md`
  * `docs/domain/Открытые вопросы по движку.md`
* **Доминирующая тема:** quick-reference глоссарий доменных моделей; нерешённые вопросы по runtime-движку.
* **Жанровая раскладка:** оба **вне жанров ADR-0002**. Глоссарий ближе всего к reference, но содержательно дублирует другие модели. Backlog — это не документ спецификации, а рабочий лист.
* **Внешние связи:** глоссарий упоминает почти все модели; backlog ссылается на C1/C7/C8 темы.
* *Заметка:* Эти два файла — кандидаты на не-миграцию в `docs/spec/`. Глоссарий логичнее переехать в `docs/GLOSSARY.md` (упомянут в `README.md` как TBD). Backlog логичнее переехать в `.claude/planning/`.

---

## 6. Сводная таблица

| Файл | Строк | Жанр (оценка) | Уверенность | Кластер | [MIGRATED]? |
|------|-------|---------------|-------------|---------|-------------|
| `docs/domain/Открытые вопросы по движку.md` | 306 | вне ADR-0002 (backlog) | уверенно | C9 | нет |
| `docs/domain/models/AlgoOrder.md` | 1373 | model | уверенно | C2 | нет |
| `docs/domain/models/Balance.md` | 693 | model + invariant + process-фрагменты | под вопрос | C4 | нет |
| `docs/domain/models/Deal.md` | 1031 | model | уверенно | C1 | нет |
| `docs/domain/models/Order.md` | 1262 | model + integration mapping fragments | смешанный | C2 | нет |
| `docs/domain/models/Position.md` | 780 | model | уверенно | C2 | нет |
| `docs/domain/models/Strategy.md` | 3080 | model (коллекция sub-models) | смешанный | C5 | нет |
| `docs/domain/models/Strategy API examples.md` | 915 | вне ADR-0002 (examples annex / reference) | под вопрос | C5 | нет |
| `docs/domain/models/Справочник по доменным моделям.md` | 447 | reference (глоссарий, ближе к GLOSSARY.md) | под вопрос | C9 | нет |
| `docs/domain/models/mapping/okx/OKX_AlgoOrder_mapping.md` | 892 | integration mapping | уверенно | C3 | нет |
| `docs/domain/models/mapping/okx/OKX_Balance_mapping.md` | 567 | integration mapping | уверенно | C3 (+C4) | нет |
| `docs/domain/models/mapping/okx/OKX_Order_mapping.md` | 898 | integration mapping | уверенно | C3 | нет |
| `docs/domain/models/mapping/okx/OKX_Position_mapping.md` | 747 | integration mapping | уверенно | C3 | нет |
| `docs/domain/processes/Audit/Аудит и история исполнения.md` | 409 | process + invariant + backlog | смешанный | C8 | нет |
| `docs/domain/processes/Calculation/Калькуляторы действий стратегии.md` | 1804 | process + model-фрагменты | смешанный | C7 | нет |
| `docs/domain/processes/Calculation/Оценка рисков.md` | 816 | process + model-фрагменты | смешанный | C7 | нет |
| `docs/domain/processes/Calculation/Расчёт индикаторов и рыночных данных.md` | 1649 | process + большой блок моделей | смешанный | C6 | нет |
| `docs/domain/processes/Deal management/FSM этапы сделки.md` | 1863 | lifecycle | уверенно | C1 | нет |
| `docs/domain/processes/Deal management/Жизненный цикл сделки.md` | 2005 | lifecycle + process (overview) | смешанный | C1 | нет |
| `docs/domain/processes/Deal management/Сервисные команды.md` | 2919 | process + model + reference (status dup) | смешанный | C7 | нет |
| `docs/domain/processes/Deal management/Статусы торговых сущностей.md` | 1654 | reference + invariant | смешанный | C8 | нет |

Итого: **21 файл, 25430 строк суммарно**, ни одного `[MIGRATED]`.

---

## 7. Замеченные риски / странности

### 7.1. Документы, трудно классифицируемые по ADR-0002

* **`Открытые вопросы по движку.md`** — это backlog, не спецификация. По ADR-0002 не подходит ни одному жанру. Кандидат на переезд в `.claude/planning/` или на разбор-и-роспуск по соответствующим спецификациям (с привязкой к ADR).
* **`Strategy API examples.md`** — JSONC-примеры. Ближайший жанр — reference (как примеры), но содержательно это example annex для Strategy. Возможно, должен жить как `docs/spec/strategy/examples.md` или быть встроен в strategy spec через ссылки.
* **`Справочник по доменным моделям.md`** — глоссарий с короткими описаниями ~30+ моделей. По ADR-0002 это reference, но фактически он дублирует материал из остальных model-документов в короткой форме. Возможно, его место — в `docs/GLOSSARY.md` (уже TBD по `docs/README.md`).

### 7.2. Документы, явно смешивающие жанры

* **`Strategy.md` (3080 строк)** — это **коллекция моделей** (Strategy, StrategyDetail, StrategyAction, StrategyOrderAction, StrategyAlgoOrderAction, StrategyPositionAction, StrategyCondition, StrategyConditionRule, StrategyPricePlacement, StopLossSettings, TrailingSettings, и др.) в одном файле. По ADR-0002 это должно стать **несколькими model-документами** + отдельным invariant-документом. **Самый дорогой кандидат на расщепление.**
* **`Расчёт индикаторов и рыночных данных.md` (1649 строк)** — половина файла — процесс (jobs + flow), половина — модели (IndicatorValue, MarketStructure, MarketPriceLevel, MarketPhase, MarketPriceData, InstrumentExternalRules, TimeFrame, IndicatorParams и т.п.). Кандидат на расщепление на process-документ (jobs) и набор model-документов.
* **`Сервисные команды.md` (2919 строк, самый большой файл)** — process + integration patterns + reference (catalog of payloads) + status taxonomy (dup §12).
* **`Жизненный цикл сделки.md` (2005 строк)** — lifecycle overview + process overview + краткий повтор FSM-статусов (которые подробно в FSM этапы сделки.md).
* **`Калькуляторы действий стратегии.md`** и **`Оценка рисков.md`** — process с большим количеством model-фрагментов (CalculationContext, CalculatedPrice/Size, RiskValidationResult, RiskCheckResult и т.п.). По ADR-0002 эти модели должны жить отдельно.
* **`Статусы торговых сущностей.md`** — reference (status taxonomy) + invariant (cleanup rules, anomaly rules) + ещё политика по controlled exchange exceptions. Часть может уйти в отдельный invariant-документ.
* **Все model-файлы (`AlgoOrder.md`, `Balance.md`, `Deal.md`, `Order.md`, `Position.md`) и `Strategy.md`** имеют встроенную секцию «Главные инварианты». По ADR-0002 invariants — отдельный жанр.
* **`Аудит и история исполнения.md`** — рабочий каркас, ~25% документа — open questions, остальное смешано (process + invariant).

### 7.3. Документы, потенциально дублирующие `docs/spec/models/AnomalyReport.md` или ADR

* **`AnomalyReport`** упоминается в:
  * `docs/domain/processes/Audit/Аудит и история исполнения.md` (1 совпадение);
  * `docs/domain/models/Справочник по доменным моделям.md` (2 совпадения).
* **Потенциальный конфликт с `docs/spec/models/AnomalyReport.md`** (уже migrated согласно commit history) — нужно проверять, что описание AnomalyReport в Справочнике и в Audit не противоречит финальной спецификации в `docs/spec/`. По правилу CLAUDE.md, `docs/spec/` приоритетнее.
* **Никаких ссылок на ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0005 в `docs/domain/` нет**, хотя ADR-0002 (жанры спецификации) и ADR-0001 (AnomalyReport) уже приняты. Это нормально для legacy-документов, но при миграции нужно проставить связи.

### 7.4. Битые / неоднозначные ссылки

* **Все cross-references — textual** (например, `см. Order.md`), без markdown link и без пути. После переезда в `docs/spec/` (где, вероятно, будут другие имена/пути) **все ~50+ текстовых ссылок придётся переписать**.
* **Нет ни одной markdown-ссылки `[text](path.md)`** во всём `docs/domain/`. Это упрощает поиск (нет битых ссылок), но и значит, что инфраструктура linkов отсутствует — переход на нормальные ссылки тоже будет частью миграции.
* В нескольких документах ссылки идут на номерные имена («`01. Жизненный цикл сделки`», «`02. Сервисные команды`», «`03. Калькуляторы действий стратегии`», «`04. Расчёт индикаторов и рыночных данных`», «`05. Аудит и история исполнения`») — это формат, отличный от фактических имён файлов (где «01. » есть только в Жизненный цикл сделки, а в Сервисные команды.md, Калькуляторы.md его нет). Не битые, но непоследовательные.

### 7.5. Подпапки / файлы, потенциально не для миграции в `docs/spec/`

* **`docs/domain/generated/`** — пустая папка, не несёт содержания. Не нужно мигрировать.
* **`docs/domain/Открытые вопросы по движку.md`** — backlog, не спецификация. Логично перенести в `.claude/planning/` или разнести по соответствующим спецификациям как открытые ADR.
* **`docs/domain/models/Справочник по доменным моделям.md`** — глоссарий, кандидат на переезд в `docs/GLOSSARY.md` (TBD по `docs/README.md`).
* **`docs/domain/models/Strategy API examples.md`** — example annex, не самостоятельная спецификация. Кандидат на встройку в strategy spec через ссылки или хранение как `docs/spec/strategy/examples/...`.

### 7.6. Прочие наблюдения, влияющие на стратегию миграции

1. **Объём.** 25430 строк суммарно. Топ-3 самых тяжёлых файла (`Сервисные команды`, `Strategy`, `Жизненный цикл сделки`) составляют ~32% объёма. Эти три файла нужно планировать как отдельные мини-этапы миграции, а не как «один обычный документ».
2. **Содержательные дубликаты.** Статусы Order/AlgoOrder/Position описаны минимум в трёх местах: в model-документах (`Order.md`/`AlgoOrder.md`/`Position.md`), в `Статусы торговых сущностей.md` и в `Сервисные команды.md` §12. После миграции должен остаться один источник истины — это требует решений до миграции.
3. **«Архитектурные инварианты» как сквозной паттерн.** Почти в каждом крупном документе есть раздел «Архитектурные инварианты ...» (lifecycle/FSM, calculator-layer, command-layer, risk-layer, market-data). По ADR-0002 это самостоятельный жанр. Стратегия миграции должна решить: оставлять инварианты внутри основных документов или массово выносить.
4. **Addenda «Дополнение после Q2-Q8».** Семь документов содержат такие секции (Аудит, Жизненный цикл сделки, FSM этапы, Сервисные команды, Расчёт индикаторов, плюс «Уточнения после Q...» в Калькуляторах). Это исторические правки после раунда вопросов. При миграции их нужно либо вмержить в основной поток, либо явно отметить как historical notes.
5. **Open questions.** В `Открытые вопросы по движку.md` лежит 13 формальных вопросов; в Аудит/Сервисные команды/Калькуляторы/FSM есть собственные open-questions блоки. До миграции нужно решить: открытые вопросы переходят как есть, превращаются в issues/backlog или порождают ADR.
6. **OKX-mapping-документы очень шаблонны.** Структура всех 4 mapping-файлов почти идентична. После миграции стоит подумать о шаблоне, чтобы будущие mapping-документы (для других бирж) собирались по одной схеме.
7. **`Расчёт индикаторов и рыночных данных.md` фактически содержит ~10+ моделей**, которые при правильной декомпозиции должны превратиться в полноценные model-документы (IndicatorValue, MarketStructure, MarketPriceLevel, MarketPhase, MarketPriceData, InstrumentExternalRules, TimeFrame, IndicatorParams, MarketStructureParams, MarketPhaseParams). Это самый сильный кандидат на «один документ → много документов» после Strategy.md.
8. **Strategy и Strategy API examples версионируются по-разному.** `Strategy.md` — 2026-05-04; `Strategy API examples.md` — 2026-04-25. Возможно, примеры отстают от модели. При миграции — проверить consistency.
9. **Нет тестов / нет git-blame истории по доменам.** В `src/test/java/` пусто (по `CLAUDE.md`), так что внешней проверки реальности этих описаний нет. Миграция в `docs/spec/` не может опираться на тестовое подтверждение.

---

## 8. Что НЕ входило в задачу (для прозрачности)

* Не предложен порядок миграции — это решается отдельно после разбора этого отчёта.
* Не предложены содержательные правки документов.
* Не затронуты `docs/api/`, `docs/spec/`, `docs/conventions/`, остальные `docs/...`.

---

## Summary

* Всего файлов в `docs/domain/`: **21** (плюс одна пустая подпапка `generated/`).
* Содержательных файлов: **20**.
* Файлов с маркером `[MIGRATED → ...]`: **0**.
* Файлов с ссылками на ADR: **0**.
* Файлов с markdown-link синтаксисом `[text](path)`: **0**.
* Файлов с addenda «Дополнение после Q2-Q8» или подобными: **7**.
* Файлов с собственными open-questions блоками: **4** (Audit, Сервисные команды, FSM, Жизненный цикл сделки) плюс корневой `Открытые вопросы по движку.md`.
* Файлов вне жанров ADR-0002: **3** (`Открытые вопросы по движку.md`, `Strategy API examples.md`, `Справочник по доменным моделям.md`).
* Файлов смешанного жанра: **9** (Balance, Order, Strategy, Аудит, Калькуляторы, Оценка рисков, Расчёт индикаторов, Жизненный цикл сделки, Сервисные команды, Статусы торговых сущностей — фактически 10, но Balance и Order — пограничные).
* Кластеров: **9** (C1 Deal lifecycle + FSM, C2 trading entities, C3 OKX mappings, C4 Balance, C5 Strategy, C6 Market data, C7 Calculator+Risk+Command, C8 Status taxonomy + Audit, C9 Reference+backlog не-ADR-0002).
* Замеченных рисков / странностей: **6 крупных групп** (труднокласифицируемые, смешанные жанры, потенциальные дубликаты, текстовые cross-refs вместо ссылок, кандидаты на не-миграцию, прочие наблюдения о стратегии миграции).
