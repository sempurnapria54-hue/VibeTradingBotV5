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
9. если условия выполнены — читает **серверное время источника**
   (`GET /api/v5/public/time`) и вызывает `DealOpeningService`.

Передаёт в `DealOpeningService`: `instrumentId`, `strategyDetailId`,
`marketPhase`, `entryReason` (`STRATEGY`), `entryStepType`
(`ENTRY`/`GRID_ENTRY`), направление (если определено), **биржевой момент
создания сделки** и минимальный entry context для аудита.

**Биржевой момент читает scanner, а не `DealOpeningService`** (П7-B,
ответ держателя, `GAPS_CLOSE_20`). Значение становится
`Deal.externalCreatedAt` и служит **нижней границей окна линковки bills**,
когда `billsWindowBegin` пуст (`docs/models/domain/aggregate/Deal.md`
§«Почему у нижней границы один писатель»). Читает scanner по двум
причинам:

- **`DealOpeningService` на биржу не ходит** по своему контракту
  (`docs/components/DealOpeningService.md` §«Не делает»), и его
  соблюдение сохраняется: он получает уже выбранные данные;
- **один запрос на проход скана, не на сделку.** Эндпоинт публичный,
  без подписи, лимит 10 req / 2 s по IP
  (`docs/integrations/okx/contracts/server-time.md`), а в фазе 1 проход
  открывает не более одной сделки — но амортизация записана сразу,
  чтобы при снятии ограничения «один инструмент» её не пришлось
  вводить заново.

**Отказ `public/time` ⇒ сделка не создаётся** — реджект, не подстановка
системных часов. Довод общий: подставленное число выглядело бы фактом,
не будучи им (`docs/rules/absent-value-semantics.md`), а системное время
в биржевом окне запрещено классом (`docs/rules/time-utc.md` §«Два
временных домена, и смешивать их запрещено»). Цена реджекта — пропуск
входа на этом тике: риск ещё не взят, отказ безопасен и симметричен
прочим gatekeeper-отказам скана.

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
требуют. Биржевые ступени режутся каскадом: инструменты биржи под
`Exchange.HOLD` (мягкий холд, ступень 1 — статус вводится на `CODE`) или
`Exchange.TRADE_BLOCKED` (ступень 2) пропускаются — новые сделки
блокируют **обе** ступени лестницы (`docs/rules/exchange-hold.md`). Это и
есть точка enforcement запрета новых входов для мягкого класса
(`docs/rules/instrument-hold.md` §Enforcement) — и **единственная** точка
enforcement биржевой ступени 1: командного блок-сета у неё нет, живые
сделки биржи под `Exchange.HOLD` сопровождаются полностью
(`docs/rules/exchange-hold.md` §«Что блокирует»).
