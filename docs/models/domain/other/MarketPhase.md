# MarketPhase

## На какой вопрос отвечает этот файл

Что это за модель `MarketPhase`: структура, енум `Type`, правила
хранения и актуальности.

## Назначение

`MarketPhase` — готовая фаза рынка, рассчитанная `MarketPhaseJob` по
`StrategyMarketPhaseSetting` на основе готовых `IndicatorValue` и
`MarketStructure`. Persisted-модель рыночных данных, не про бизнес-цикл
сделки → `other` (см. `.claude/decisions/models-core-vs-other.md`).

`Type` определяется **авторскими условиями**, не скоринговым алгоритмом:
`MarketPhaseJob` через `docs/components/MarketPhaseClassifier.md` исполняет
упорядоченный first-match-список клауз `StrategyMarketPhaseRule`
(`StrategyMarketPhaseSetting.phaseRules`) — первая клауза с истинным
`condition` задаёт `Type`, ни одна → `UNKNOWN` (см.
`docs/decisions/market-phase-conditional-classification.md`).

`EntryScannerJob` по `MarketPhase.Type` выбирает `StrategyDetail`
(`MarketPhase.Type → StrategyDetail.marketPhaseType`). Раздачей актуальной
фазы занимается `docs/components/MarketPhaseService.md`.

## Структура

Java-класс, наследует `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID результата расчёта. |
| `instrumentId` | `Long` | Внутренний ID инструмента. |
| `setting` | `StrategyMarketPhaseSetting` | Настройка стратегии, по которой рассчитана фаза. |
| `type` | `Type` | Тип рассчитанной фазы. |
| `candleTimestamp` | `OffsetDateTime` | Время свечи расчёта. |
| `confirmedAt` | `OffsetDateTime` | Время, с которого фазу можно использовать без look-ahead (деривация — ниже). |

## Деривация `confirmedAt`

`MarketPhaseClassifier` выводит `confirmedAt` фазы как консервативный
`max` по гейт-операндам **сработавшей клаузы** (first-match): структурный
операнд → его `confirmedAt`; индикаторный → `candleTimestamp`; клауза без
гейт-операндов → `candleTimestamp` бара оценки. Прежний скоринговый
`confirmationBars` распущен редизайном условной фазы
(`docs/decisions/market-phase-conditional-classification.md`) — роль
«гейта использования без look-ahead» теперь несёт этот производный
`confirmedAt`.

Свойства: отдельного состояния не вводит, согласуется со
stateless-контрактом классификатора (история прошлых фаз не нужна).
Дебаунс-семантику `confirmedAt` фазы **не несёт** — анти-whipsaw остаётся
операнд-уровневым (сглаживающие периоды индикаторов, структурный
`breakoutConfirmationBars`). Точная арифметика (какой именно timestamp
берётся для каждого типа операнда) — деталь реализации (`CODE`).

## Енум `Type`

`BULL_TREND`, `BEAR_TREND`, `RANGE`, `UNKNOWN`.

Отдельного `Status` нет. При смене фазы создаётся новый актуальный
результат (например, `type = UNKNOWN`). Актуальность проверяется через
`StrategyMarketPhaseSetting.expirationDuration`: точка отсчёта свежести
(`referencePoint`) — **`candleTimestamp`**, а `confirmedAt` — гейт
использования без look-ahead, **не** точка отсчёта (правило —
`docs/rules/market-data-freshness.md`).

## Правила хранения

- Считается только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, strategy_market_phase_setting_id,
  candle_timestamp)`.
- **Ключ — контейнер (per-strategy), осознанное исключение** из шаринга
  результатов по идентичности (по которому индикатор/структура шарятся по
  `config_id`): правила определения фазы авторские и специфичны для
  стратегии, фаза тянет меньше данных — выигрыш шаринга не оправдывает
  усложнение. Основание — `docs/decisions/market-data-result-identity-keying.md`
  §Исключение — `MarketPhase`.
- **Свежесть на чтение:** `expiredAt = candleTimestamp +
  askingSetting.expirationDuration` считается в runtime, колонкой не
  хранится (единый механизм, без хранимого состояния свежести;
  `docs/rules/market-data-freshness.md`).
- **Retention:** результаты не чистятся (нет потребителя истории) —
  `docs/rules/market-data-retention.md`.
- Хранится **история** — строка на свечу (`UNIQUE` по `candle_timestamp`),
  а не upsert одной строки. «Актуальная фаза» = **последняя по
  `candle_timestamp`** (её отдаёт `MarketPhaseService.getLatestPhase`).
  Наследование `Auditable` и идемпотентность по `candle_timestamp`
  осмысленны только при истории; формулировка согласована с соседними
  моделями результатов (`IndicatorValue`, `MarketStructure` тоже хранят
  ряд, а не правят строку).
- Одна `StrategyMarketPhaseSetting` (на уровне `Strategy`, т.к. фаза нужна
  до выбора `StrategyDetail`) несёт авторские правила (`phaseRules`)
  классификации во все `Type`; `MarketPhaseJob` пишет новый актуальный
  результат поверх истории.
