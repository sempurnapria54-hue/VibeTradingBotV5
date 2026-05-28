# EntryScannerJob

## На какой вопрос отвечает этот файл

Кто ищет возможность создать новую сделку (компонент-job): шаги, что
передаёт в `DealOpeningService`, чего не делает.

## Назначение

`EntryScannerJob` ищет возможность создать новую сделку. Ордера не
выставляет и позицию не открывает — создаёт только `Deal` через
`DealOpeningService`. Не рынок «дал сигнал», а scanner увидел, что
текущее состояние подходит под `ENTRY`/`GRID_ENTRY` condition активной
стратегии. `Strategy.ACTIVE` не означает, что можно открыть сделку прямо
сейчас.

## Шаги

1. читает активные стратегии по инструментам;
2. `MarketDataExpirationChecker.checkForEntry(strategy)`; устарели/нет
   данных → `Deal` не создаётся (см.
   `docs/rules/market-data-freshness.md`);
3. получает актуальную `MarketPhase` по `Strategy.marketPhaseSetting`;
4. по `MarketPhase.Type` выбирает pinned `StrategyDetail`;
5. проверяет `phaseEntryPolicy` (`NO_TRADE` → стоп; иначе дальше);
6. читает данные для входа (`MarketPriceData`, `IndicatorValue`,
   `MarketStructure`, `MarketPhase`, balance/risk при необходимости);
7. проверяет gatekeeper: нет активной сделки/позиции по инструменту (при
   максимуме одной);
8. находит `StrategyStep` с `stepType = ENTRY`/`GRID_ENTRY`, проверяет
   `StrategyCondition`;
9. если условия выполнены — вызывает `DealOpeningService`.

Передаёт в `DealOpeningService`: `instrumentId`, `strategyDetailId`,
`marketPhase`, `entryReason` (`STRATEGY`), `entryStepType`
(`ENTRY`/`GRID_ENTRY`), направление (если определено), минимальный entry
context для аудита.

## Не делает

Не создаёт order/algo-order, не открывает позицию, не исполняет
`StrategyAction`, не запускает FSM, не ходит на биржу за выставлением
ордеров. Gatekeeper по `Exchange HOLD` / `Instrument` / `Strategy` /
risk — см. `docs/rules/exchange-hold.md`, `docs/lifecycles/Strategy.md`.
