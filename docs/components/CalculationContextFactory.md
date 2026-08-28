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

`MarketPriceDataService`, `InstrumentExternalRulesDataService`
(`findByInstrumentId`), `StrategyDataService`
(`findActiveByInstrumentIdWithSettings` — каталоги настроек
индикаторов/структуры стратегии для резолва готовых значений по
ключу), `IndicatorService`, `MarketStructureService`, balance (из
`DealContext`). Тяжёлые данные не считает — читает готовые
результаты.

`MarketPhaseService` фабрика **не** вызывает: фаза 1 `marketPhase`
не потребляет, поле остаётся `null` (заполнится с появлением
потребителя — форвард).

## Границы и защитное поведение

- Собирает отдельный context на каждый action (один action = один свежий
  context); общий context на step/handler/проход не собирает.
- `MarketPriceData` в рамках одного context получает один раз и
  переиспользует.
- **Не** вызывает `IntegrationService` / OKX adapter и **не** создаёт
  `REFRESH_BALANCE_COMMAND`.
- Работает защитно: если обязательное готовое значение отсутствует, явно
  устарело, или сервис рыночных данных не может вернуть актуальную цену —
  сигнализирует контролируемую ошибку расчёта **броском** `CalculationException`
  (несёт `CalculationError`; `StrategyActionCalculator` перехватывает →
  `ERROR`-результат, см. `docs/components/models/CalculationError.md`), а не считает по старым данным
  (`docs/rules/market-data-freshness.md`). Конкретно: при отсутствии рыночной
  цены — **temporary** `CalculationException` с кодом `NO_MARKET_PRICE`.
