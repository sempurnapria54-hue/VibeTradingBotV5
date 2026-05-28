# MarketStructureService

## На какой вопрос отвечает этот файл

Кто отдаёт готовую структуру рынка (компонент-сервис): контракт,
поведение при отсутствии/устаревании.

## Назначение

`MarketStructureService` отдаёт готовую `MarketStructure` и нужные
`MarketPriceLevel` (см. `docs/models/other/MarketStructure.md`). Сам
уровни по свечам не ищет — их заранее считает
`docs/components/MarketStructureJob.md`.

## Контракт (примеры методов)

- `MarketStructure getLatestStructure(Long instrumentId,
  StrategyMarketStructureSetting setting)`;
- `MarketPriceLevel getRequiredLevel(MarketStructure structure,
  MarketPriceLevel.Type levelType)`.

## Поведение при отсутствии / устаревании

Если нужная структура отсутствует или устарела по
`StrategyMarketStructureSetting.expirationDuration` (правило —
`docs/rules/market-data-freshness.md`) — это блокирующее условие для
активации стратегии, входа и выполнения action, зависящего от структуры.
