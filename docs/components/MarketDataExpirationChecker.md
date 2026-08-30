# MarketDataExpirationChecker

## На какой вопрос отвечает этот файл

Кто проверяет свежесть рыночных данных.

## Назначение

`MarketDataExpirationChecker` — runtime-сервис проверки свежести рыночных
данных. Не хранит состояние в БД и **не** меняет `Strategy.Status`.
Отвечает только на вопрос: нужные данные свежие, частично устарели,
полностью устарели или отсутствуют — результатом
`MarketDataExpirationResult` (см.
`docs/components/models/MarketDataExpirationResult.md`).

## Контракт

- `MarketDataExpirationResult checkForEntry(Strategy strategy)` — данные
  для поиска нового входа;
- `MarketDataExpirationResult checkForStep(DealContext dealContext,
  StrategyStep step)` — данные, нужные конкретному `StrategyStep`.

## Источник сроков

`expirationDuration` из `StrategyIndicatorSetting`,
`StrategyMarketStructureSetting`. У `StrategyMarketPhaseSetting`
`expirationDuration` **нет** — `MarketPhase` не персистируется, свежесть
фазы наследуется от свежести её входов (индикаторов/структур; см.
`docs/models/domain/other/MarketPhase.md`).

## Вычисление свежести (на чтение)

Свежесть вычисляется на чтение, в БД не хранится:
форма — `docs/spec/market-data-freshness.json` (`expiredAt`,
`referencePoint`), здесь она не переписывается. `confirmedAt` — гейт без
look-ahead, не точка отсчёта. Результат ключуется настройкой-владельцем (owner-ключевание,
`docs/rules/market-data-freshness.md`): у строки один
владелец, под его `expirationDuration` и оценивается свежесть — общей
строки с несколькими запрашивающими больше нет. Правило —
`docs/rules/market-data-freshness.md`.

## Граница ответственности

Поведение при expired/missing задаётся не здесь, а в
`StrategyStep.marketDataExpiredSetting` (`WAIT` / `BLOCK_STEP` /
`GRACEFUL_CLOSE` / `KILL_SWITCH`, см. `docs/models/domain/aggregate/Strategy.md`).
Применение результата в FSM — `docs/processes/deal-management.md`,
`docs/lifecycles/Deal.md`. Сквозное правило свежести —
`docs/rules/market-data-freshness.md`.
