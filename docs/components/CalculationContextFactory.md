# CalculationContextFactory

## На какой вопрос отвечает этот файл

Кто собирает `CalculationContext` (компонент-фабрика): что собирает,
из каких сервисов, какие границы соблюдает.

## Назначение

`CalculationContextFactory` собирает свежий `CalculationContext` (см.
`docs/components/models/CalculationContext.md`) для одного
рассчитываемого `StrategyAction` из `DealContext + StrategyAction +
свежие runtime-data`.

## Источники данных

`MarketPriceDataService`, `InstrumentExternalRulesService`,
`IndicatorService`, `MarketStructureService`, `MarketPhaseService`,
balance (из `DealContext` / repository). Тяжёлые данные не считает —
читает готовые результаты.

## Границы и защитное поведение

- Собирает отдельный context на каждый action (один action = один свежий
  context); общий context на step/handler/проход не собирает.
- `MarketPriceData` в рамках одного context получает один раз и
  переиспользует.
- **Не** вызывает `IntegrationService` / OKX adapter и **не** создаёт
  `REFRESH_BALANCE`.
- Работает защитно: если обязательное готовое значение отсутствует, явно
  устарело, или сервис рыночных данных не может вернуть актуальную цену —
  возвращает controlled error/result (`CalculationError`), а не считает по
  старым данным (см. `docs/components/models/CalculationError.md`,
  `docs/rules/market-data-freshness.md`).
