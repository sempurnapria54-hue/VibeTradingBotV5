# Прогресс: миграция Strategy

## На какой вопрос отвечает этот файл

На каком шаге миграция архивной сущности Strategy и как
классифицирован каждый фрагмент.

## Статус

**Завершено.** Источник:
`.claude-archive/2026-05-21/docs/domain/models/Strategy.md` (3080
строк) + `Strategy API examples.md` (форвард).

Весь strategy-tree (~30 типов) — разделы внутри
`docs/models/core/Strategy.md` по `model-granularity.md`.
`Strategy.Status` (административный) → lifecycle. Не биржевая сущность
→ OKX client-доков нет.

## Созданные / изменённые файлы

- `docs/models/core/Strategy.md` — модель (весь tree, создан).
- `docs/lifecycles/Strategy.md` — административный статус (создан).
- `.claude/work/questions/tasks/strategy.md` — форвард-заметки
  (создан).

Переиспользован (ссылка): `docs/rules/no-partial-close.md`. Новых
сквозных правил нет; OKX-доков нет.

## Отчёт по фрагментам

Область у всех — **продукт**. Strategy-tree разнесён по разделам
одной модели; ниже — по группам фрагментов.

| # | Фрагмент | Тип | Размещение / диспозиция |
|---|---|---|---|
| Ф1 | Главная идея + архитектурные инварианты strategy-layer | модель (инварианты) | `Strategy.md` §Архитектурные инварианты |
| Ф2 | Immutable; наследование Auditable; key только у Action; объектные связи | модель (инварианты) | `Strategy.md` §Архитектурные инварианты |
| Ф3 | `Strategy` (root) + `Status` | модель + lifecycle | Структура → `Strategy.md`; статус → `Strategy lifecycle` |
| Ф4 | `StrategyMarketPhaseSetting` | модель (раздел) | `Strategy.md` §Настройки рыночных данных |
| Ф5 | `MarketPhaseParams` + `AlgorithmType` | модель (раздел) | `Strategy.md` §Настройки рыночных данных |
| Ф6 | `StrategyDetail` + `PhaseEntryPolicy` + матрица | модель (раздел) | `Strategy.md` §StrategyDetail |
| Ф7 | Волатильность через индикаторы (нет VolatilitySetting) | модель | `Strategy.md` §IndicatorParams (ATR/Bollinger) |
| Ф8 | `StrategyIndicatorSetting` + `Destiny` | модель (раздел) | `Strategy.md` §Настройки |
| Ф9 | `IndicatorParams` (abstract) + наследники | модель (раздел) | `Strategy.md` §IndicatorParams |
| Ф10 | `StrategyMarketStructureSetting` + `Destiny` | модель (раздел) | `Strategy.md` §Настройки |
| Ф11 | `MarketStructureParams` | модель (раздел) | `Strategy.md` §Настройки |
| Ф12 | `StrategyStep` + `StrategyStepType` + связь с Deal.entryStepType | модель (раздел) | `Strategy.md` §StrategyStep |
| Ф13 | `StrategyMarketDataExpiredSetting` + `MarketDataExpiredAction` | модель (раздел) | `Strategy.md` §StrategyStep |
| Ф14 | `StrategyCondition` / `Rule` / `RuleType` / `SourceType` / `Operator` / `Operand` | модель (разделы) | `Strategy.md` §Условия |
| Ф15 | `StrategyAction` интерфейс + `StrategyActionType` + actionKind | модель (раздел) | `Strategy.md` §Действия |
| Ф16 | `StrategyOrderAction` (+ positionReducingOnly) | модель (раздел) | `Strategy.md` §StrategyOrderAction |
| Ф17 | `StrategyTradeDirection` | модель (раздел) | `Strategy.md` §StrategyTradeDirection |
| Ф18 | `StrategyPricePlacement` + 3 енума + разделение смыслов | модель (раздел) | `Strategy.md` §StrategyPricePlacement |
| Ф19 | `StrategyAttachedProtectionSettings` | модель (раздел) | `Strategy.md` §StrategyAttachedProtectionSettings |
| Ф20 | `StrategyAlgoOrderAction` (+ убранные settings, OCO_FULL, reducingOnly) | модель (раздел) | `Strategy.md` §StrategyAlgoOrderAction |
| Ф21 | `StrategyPositionAction` (CLOSE_FULL only) | модель (раздел) + сквозное правило | `Strategy.md` §StrategyPositionAction; ban → `no-partial-close.md` |
| Ф22 | `StopLossSettings` + `StopLossCalculationType` | модель (раздел) | `Strategy.md` §StopLossSettings |
| Ф23 | `TrailingSettings` | модель (раздел) | `Strategy.md` §TrailingSettings |
| Ф24 | key / targetActionKey семантика + 12-пунктная валидация | модель (правила) + компонент-валидатор | Правила → `Strategy.md` §key/валидация; валидатор → STR-FW8 |
| Ф25 | Связь с DealActionState/RuntimeTarget, UNIQUE-инварианты | модель (граница) + Deal-runtime | `Strategy.md` §Связь с DealActionState; модели → STR-FW5/Deal |
| Ф26 | `TimeFrame` enum + TimeFrameMapper | модель (раздел) + правило биржи | Enum → `Strategy.md` §TimeFrame; mapper → STR-FW7 |
| Ф27 | Связь с калькуляторами (StrategyActionCalculator/Price/Size/Risk) | компоненты + RVO | Ссылки в `Strategy.md` §Связи; → STR-FW2/FW3 |
| Ф28 | Связь с jobs (Indicator/MarketStructure/MarketPhase/EntryScanner) | компоненты/процессы | Ссылки в `Strategy.md`; → STR-FW1 |
| Ф29 | risk-layer (RiskValidator/RiskBlockResolver, после расчёта до команды) | компоненты + сквозное правило | Ссылки в `Strategy.md` §Архитектурные инварианты; → STR-FW3 |
| Ф30 | `Strategy.Status` эффекты (INACTIVE/DELETED → блок/graceful shutdown) | lifecycle | `Strategy lifecycle`; enforcement → STR-FW10 |
| Ф31 | Устаревшие данные не меняют Status; MarketDataExpirationChecker | модель/lifecycle + checker | Инвариант → `Strategy.md`/lifecycle; checker → STR-FW4 |
| Ф32 | Загрузка из БД (@EntityGraph/JOIN FETCH, LAZY, pinned detail) | реализация | Инварианты (объектные связи, pinned) → `Strategy.md`; детали загрузки — реализация, не доменное знание |
| Ф33 | JSON-примеры / Strategy API examples.md | reference/пример | Отложено → STR-FW9 |
| Ф34 | Связь с другими доками (§38) + ServiceCommandFactory | навигация + компонент | Ссылки по владельцам; ServiceCommandFactory → STR-FW5; навигация-агрегатор не воспроизводится (`master-index-not-fixated.md`) |

## Итог по Strategy

- Размещено в `docs/`: 2 файла (модель со всем strategy-tree,
  lifecycle). OKX-доков нет; новых сквозных правил нет
  (`no-partial-close.md` переиспользован).
- Весь strategy-tree (~30 типов) — разделы одной модели по
  `model-granularity.md` (не ~30 файлов).
- В форвард-заметки: STR-FW1…FW10 (jobs, калькуляторы+RVO, risk-layer,
  freshness-checker, ServiceCommandFactory/commands, модели
  market-data, TimeFrameMapper, валидатор, API examples,
  INACTIVE/DELETED enforcement). Консолидируют RiskValidator-форвард
  всех сущностей.
- Продуктовых открытых вопросов по Strategy нет.
