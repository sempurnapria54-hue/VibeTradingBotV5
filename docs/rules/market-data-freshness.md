# Свежесть рыночных данных

## На какой вопрос отвечает этот файл

Какое у нас правило свежести рыночных данных и как оно ограничивает
data-dependent действия.

## Правило

- Срок свежести каждого вида рыночных данных задаётся через
  `expirationDuration` в strategy settings (`StrategyIndicatorSetting`,
  `StrategyMarketStructureSetting`, `StrategyMarketPhaseSetting`, см.
  `docs/models/core/Strategy.md`). `maxAgeBars` не используется.
- Устаревание вычисляется в runtime сервисом
  `MarketDataExpirationChecker` (см.
  `docs/components/MarketDataExpirationChecker.md`); состояние свежести в
  БД не хранится.
- **Jobs рыночных данных не меняют `Strategy.Status`.** `Strategy.ACTIVE`
  — административное разрешение, не гарантия runtime-ready данных. Если
  свежих входных данных нет, job не создаёт новый result, а старый
  постепенно становится expired.
- **Data-dependent действие не выполняется по устаревшим данным.** Если
  данные, нужные для входа или для конкретного `StrategyStep`, устарели
  или отсутствуют — это блокирующее условие; реакция задаётся
  `StrategyStep.marketDataExpiredSetting` (`WAIT` / `BLOCK_STEP` /
  `GRACEFUL_CLOSE` / `KILL_SWITCH`). `BLOCK_STEP` не блокирует
  refresh/cancel/close/safety.
- Если проблема со свежестью обнаружена уже при сборе
  `CalculationContext`, калькулятор возвращает controlled calculation
  error, а не считает по старым данным (см.
  `docs/components/models/CalculationError.md`).

## Первоисточник и смежное

Правило сквозное (применимо к нескольким сущностям и процессам, см.
`.claude/decisions/rule-source-of-truth.md`). Срок свежести как атрибут —
у settings в `Strategy`; вычисление — у `MarketDataExpirationChecker`;
реакция FSM — `docs/processes/deal-management.md` и
`docs/lifecycles/Deal.md`. Эффект graceful shutdown по policy →
`Deal.shutdownReason = MARKET_DATA_EXPIRED` (см. `docs/lifecycles/Deal.md`).
