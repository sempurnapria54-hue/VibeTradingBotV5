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
| `confirmedAt` | `OffsetDateTime` | Время, с которого фазу можно использовать без look-ahead. |

## Енум `Type`

`BULL_TREND`, `BEAR_TREND`, `RANGE`, `UNKNOWN`.

Отдельного `Status` нет. При смене фазы создаётся новый актуальный
результат (например, `type = UNKNOWN`). Актуальность проверяется через
`StrategyMarketPhaseSetting.expirationDuration` и
`candleTimestamp` / `confirmedAt` (правило —
`docs/rules/market-data-freshness.md`).

## Правила хранения

- Считается только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, strategy_market_phase_setting_id,
  candle_timestamp)`.
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
