# MarketPhaseService

## На какой вопрос отвечает этот файл

Кто отдаёт актуальную фазу рынка (компонент-сервис): контракт, поведение
при отсутствии/устаревании.

## Назначение

`MarketPhaseService` отдаёт актуальную `MarketPhase` (см.
`docs/models/other/MarketPhase.md`). Сам фазу не считает — её заранее
считает `docs/components/MarketPhaseJob.md`.

## Контракт (пример метода)

- `MarketPhase getLatestPhase(Long instrumentId,
  StrategyMarketPhaseSetting setting)`.

## Использование

`EntryScannerJob` (см. `docs/components/EntryScannerJob.md`) использует
`MarketPhaseService`, чтобы по `MarketPhase.Type` выбрать `StrategyDetail`.
Если фаза отсутствует или устарела по
`StrategyMarketPhaseSetting.expirationDuration` (правило —
`docs/rules/market-data-freshness.md`) — новые сделки не создаются.
