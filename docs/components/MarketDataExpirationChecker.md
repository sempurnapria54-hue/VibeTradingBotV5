# MarketDataExpirationChecker

## На какой вопрос отвечает этот файл

Кто проверяет свежесть рыночных данных (компонент-сервис): контракт, что
проверяет, чем не управляет.

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
`StrategyMarketStructureSetting`, `StrategyMarketPhaseSetting`.

## Вычисление свежести (на чтение)

Свежесть вычисляется на чтение, в БД не хранится:
`expiredAt = referencePoint + askingSetting.expirationDuration`,
свежо ⟺ `now < expiredAt`. `referencePoint` — `windowEndAt` (структура) /
`candleTimestamp` (фаза, индикатор); `confirmedAt` — гейт без look-ahead,
не точка отсчёта. Для шаримых результатов (ключ по `config_id`) свежесть
оценивается под каждую запрашивающую настройку — единого `expiredAt` на
общей строке нет. Правило — `docs/rules/market-data-freshness.md`.

## Граница ответственности

Поведение при expired/missing задаётся не здесь, а в
`StrategyStep.marketDataExpiredSetting` (`WAIT` / `BLOCK_STEP` /
`GRACEFUL_CLOSE` / `KILL_SWITCH`, см. `docs/models/domain/aggregate/Strategy.md`).
Применение результата в FSM — `docs/processes/deal-management.md`,
`docs/lifecycles/Deal.md`. Сквозное правило свежести —
`docs/rules/market-data-freshness.md`.
