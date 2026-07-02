# IndicatorService

## На какой вопрос отвечает этот файл

Кто отдаёт готовые значения индикаторов (компонент-сервис): контракт,
поведение при отсутствии/устаревании.

## Назначение

`IndicatorService` отдаёт готовые `IndicatorValue` (см.
`docs/models/domain/other/IndicatorValue.md`). Сам индикаторы не считает — их
заранее считает `docs/components/IndicatorJob.md`.

## Контракт (примеры методов)

- `Optional<IndicatorValue> getLatestValue(Long instrumentId,
  StrategyIndicatorSetting setting)`;
- `List<IndicatorValue> getLatestValues(Long instrumentId,
  Collection<StrategyIndicatorSetting> settings)`;
- `Optional<IndicatorValue> getPreviousValue(Long instrumentId,
  StrategyIndicatorSetting setting)` — предыдущее значение (slope/
  crossover); свежесть не гейтит.

## Поведение при отсутствии / устаревании

Если нужного значения нет или оно устарело по
`StrategyIndicatorSetting.expirationDuration` (проверяет
`MarketDataExpirationChecker`, правило —
`docs/rules/market-data-freshness.md`) — это блокирующее условие для:

- активации стратегии;
- создания новой сделки;
- выполнения action, если значение нужно для расчёта.
