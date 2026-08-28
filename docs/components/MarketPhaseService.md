# MarketPhaseService

## На какой вопрос отвечает этот файл

Кто отдаёт актуальную фазу рынка (компонент-сервис): контракт, как
вычисляет, поведение при отсутствии/устаревании входов.

## Назначение

## Контракт (пример метода)

- `Optional<MarketPhase> getCurrentPhase(Instrument instrument,
  Strategy strategy)` — вычисляет фазу из текущих входов (`Instrument`
  нужен для резолва цены по внешнему тикеру).

## Использование

`EntryScannerJob` (см. `docs/components/EntryScannerJob.md`) использует
`MarketPhaseService`, чтобы по `MarketPhase.Type` выбрать `StrategyDetail`.

## Поведение при отсутствии / устаревании входов

Своего срока свежести у фазы нет — свежесть наследуется от входов. Если
индикатор/структура, нужные сработавшей клаузе, отсутствуют или устарели
по `expirationDuration` своих `StrategyIndicatorSetting` /
`StrategyMarketStructureSetting` (проверяет `MarketDataExpirationChecker`,
правило — `docs/rules/market-data-freshness.md`) — операнд недоступен, и
фаза консервативно `UNKNOWN`. По `UNKNOWN`-фазе торгуемая detail не
выбирается (матрица `UNKNOWN → NO_TRADE`), новые сделки не создаются.
