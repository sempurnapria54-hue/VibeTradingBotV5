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
7. проверяет gatekeeper — **две проверки, разные радиусы**:
   - **по инструменту:** нет активной сделки/позиции по нему (при
     максимуме одной);
   - **по контуру:** нет активной сделки **ни по одному** инструменту —
     энфорсмент ограничения «в фазе 1 торгуется один инструмент» (H8
     `DOCS_CHECK_10`, `docs/rules/trading-constraints.md` §Инструменты).
     Без этой проверки ограничение осталось бы операционной дисциплиной,
     а на нём стоит отсрочка уровней 2-3 риск-модели
     (`docs/decisions/per-trade-risk-policy.md`). Контурная проверка —
     **app-only**: DB-инварианта у неё нет (у `deals` нет колонки биржи),
     гонку тиков закрывает `JobExecutionGuard`; снимается в фазе 3 вместе
     с вводом уровня 2;
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
ордеров. Gatekeeper по холдам биржи/инструмента, `Strategy` и risk — см.
`docs/rules/exchange-hold.md`, `docs/rules/instrument-hold.md`,
`docs/lifecycles/Strategy.md`.

**Предусловие «ключ комиссионной группы подтверждён»** (H21
`DOCS_CHECK_10`). Инструмент с пустым `Instrument.externalModifiedAt`
(измеритель свежести ключа группы ни разу не проставлялся) в скан **не
попадает**. Это не холд: статус не меняется, отчёт не заводится, оператор
не привлекается — предусловие снимается само первым успешным тиком
`InstrumentExternalRulesSyncJob`
(`docs/models/domain/other/InstrumentExternalRules.md` §«Начальное
состояние измерителя»).

**Механика гейта холдов — выборка скана.** Скан идёт по инструментам в
статусе `ACTIVE`, поэтому оба safety-статуса (`TRADE_BLOCKED` — холд с
kill-switch; `ENTRY_BLOCKED` — мягкий запрет новых входов, H3
`GAPS_CLOSE_6`) выпадают из скана **самой выборкой**, отдельной проверки не
требуют. Биржевой холд (`Exchange.TRADE_BLOCKED`) режется каскадом:
инструменты такой биржи пропускаются. Это и есть точка enforcement запрета
новых входов для мягкого класса (`docs/rules/instrument-hold.md`
§Enforcement).
