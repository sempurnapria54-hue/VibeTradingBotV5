# `SYNC_DOCS_FROM_CODE` шага 6 фазы 2

## На какой вопрос отвечает этот файл

Каков зафиксированный исход фокуса `divergence` шага 6 фазы 2 и чем
расхождения реконсилированы.

## Направление и предмет

Направление — **docs←code**: код утверждён аппрувом `CODE`
(`phase-2-step-6-code-focuses.md`), и вопрос «код или доки неверны» снят.
Предмет — доки по оси владельца `market-data`
(`.claude/rules/knowledge-ownership-by-service.md`) плюс носители,
которых задели правки границы коннектора и общей модели.

**Метод сверки механический.** Для моделей — поимённое сличение полей
Java-класса с таблицей §Структура дока. Для компонентов — сличение
объявленного контракта (сигнатуры в доке) с фактическими публичными
методами класса. Для процессов — сличение названных операндов и путей
запуска с конфигурацией и контроллерами.

## Список расхождений

Двадцать две позиции: 3 add, 17 change, 2 remove.

### add

| # | Где | Что в коде есть, а в доке нет |
|---|---|---|
| A1 | `docs/models/domain/core/Instrument.md` | поле `exchangeCode` — площадка адресуется кодом; числовой `exchangeId` остался названным долгом донора |
| A2 | `docs/models/domain/other/CandleGroup.md`, `docs/lifecycles/CandleGroup.md` | поле `plannedFirstUtcMillis` и ребро «углублённое требование → `BACKFILL` из любого живого статуса» |
| A3 | `docs/processes/snapshot-collection.md` | отказ **письма** одной строки прохода не роняет (закрытие D3) |

### change

| # | Где | Расхождение |
|---|---|---|
| C1 | `docs/models/domain/core/Instrument.md` | `plannedCandleStartDate` описан как действующий горизонт; в коде горизонт — у группы, а поле живёт только в доноре |
| C2 | `docs/models/domain/other/CandleGroup.md` | «плановый горизонт задаётся **на инструмент**… не на группу» — прямо обратное коду (2 пассажа) |
| C3 | `docs/lifecycles/CandleGroup.md` | горизонт назван `Instrument.plannedCandleStartDate` в таблице статусов, в §«Общий поток» и в §«Глубина и покрытие» |
| C4 | `docs/lifecycles/Instrument.md` | то же в §готовности |
| C5 | `docs/processes/candle-loading.md` | горизонт на инструмент; путь ручного запуска `POST /api/jobs/candle-loading/trigger` (в коде — `/api/v1/market-data/jobs/candles`) |
| C6 | `docs/components/CandleJob.md` | не называет ни горизонт группы, ни пересчёт готовности, ни прекращение тика на отказе доступа |
| C7 | `docs/components/IndicatorJob.md` | «считает по настройкам стратегий», «читает стратегии всех статусов» — в коде обходятся заказанные идентичности, а инструменты приносит сбор |
| C8 | `docs/components/MarketStructureJob.md` | то же плюс «скаляры **своей стратегии**» — в коде входы адресуются идентичностями вычисления |
| C9 | `docs/components/IndicatorService.md` | контракт объявляет `StrategyIndicatorSetting` операндом; в коде — `Long indicatorConfigId` + `Duration tolerance` |
| C10 | `docs/components/MarketStructureService.md` | то же |
| C11 | `docs/components/MarketPhaseService.md` | контракт `getCurrentPhase(Instrument, Strategy)`; в коде — `(Instrument, MarketPhaseRequest)`. Не названы условность чтения цены и её деградация в `UNKNOWN` (закрытия P1/D4) |
| C12 | `docs/components/MarketPhaseResolver.md` | «клаузы настройки фазы» — в коде клаузы приносит потребитель операндом |
| C13 | `docs/components/MarketDataExpirationChecker.md` | контракт объявляет `stepDataFresh(StrategyStep, …)`; в коде market-data этого метода нет — он принадлежит ядру |
| C14 | `docs/components/InstrumentExternalRulesSyncJob.md` | описывает класс, которого нет: в коде `InstrumentSyncJob`, он ведёт **листинг и правила**, ставок комиссии не читает, холда не пишет, правила обходит окном за курсором |
| C15 | `docs/components/MarketPriceDataService.md` | поток описан через клиент OKX напрямую; в коде — через коннектор |
| C16 | `docs/models/domain/other/CandleGroup.md` §Персистентность | объявляет `external_timeframe NOT NULL` и связь `ManyToOne`; в схеме `V1` колонки нет вовсе, а связь — внешним ключом колонкой |
| C17 | `docs/models/domain/other/InstrumentExternalRules.md` | не называет ни писателя ряда ставок (уехал к владельцу счёта), ни то, чем поле исключается из навеса: в коде это примесь хранилищного слоя, а не аннотация на доменной форме (Д668) |

### remove

| # | Где | Чего в коде больше нет |
|---|---|---|
| R1 | `docs/components/IndicatorService.md`, `MarketStructureService.md` | методы `getLatestValues(…)` и `getRequiredLevel(…)` — первого не было, второй удалён закрытием K2 |
| R2 | `docs/processes/candle-loading.md` §«Глубина под прогрев индикаторов — вход от стратегии (запарковано)» | парковка закрыта: глубину называет команда требования, перекладывать warmup в горизонт инструмента больше нечего |

### Проверено и расхождения не дало

- `docs/models/domain/other/IndicatorValue.md` §«Ключевание — идентичностью
  вычисления», `docs/rules/market-data-freshness.md`,
  `docs/architecture/market-data-collection.md`,
  `docs/models/domain/other/MarketOrderBook.md`, `MarketTicker.md`,
  `docs/models/mapping/TimeFrame.md` — приведены к целевой конструкции
  ещё на `DOCS_CHECK`, код им соответствует.
- `docs/models/domain/other/MarketStructure.md` упоминает
  `StrategyMarketStructureSetting` как **авторскую сторону** контракта
  (кто объявляет ключи входов), а не как ключ хранения; это не
  расхождение с кодом market-data.
- Схема `V1` сверена с сущностями поимённо: расхождений имён и типов нет.

## Реконсиляция

Все двадцать две позиции закрыты правкой носителей (`reconcile-knowledge` для
change/remove, штатное размещение для add). Существенное:

- **Горизонт бэкфилла переехал на группу** — правка в пяти носителях
  (C1-C5) сведена к одному дому: величину объявляет
  `docs/models/domain/other/CandleGroup.md`, остальные ссылаются
  (`.claude/rules/policy-home.md`).
- **`InstrumentExternalRulesSyncJob.md` переименован в
  `InstrumentSyncJob.md`** — предмет дока сменил и имя, и охват; половина
  про ставку комиссии не правится на месте, а **удаляется**: ставка ушла
  к владельцу счёта (`docs/models/domain/other/TradeFeeRate.md`), и копия
  её механики у market-data была бы вторым носителем чужой истины.
- **Референтный каскад переименования пройден:** 12 живых носителей
  ссылались на снятый док. Ссылки разошлись на два класса и починены
  по-разному — те, что про **синк правил**, переадресованы на новое имя;
  те, что про **ставку комиссии**, переадресованы на её парковку
  (`.claude/work/backlog.md` §«Ставка комиссии — реестр переезжает к
  владельцу счёта»), потому что писателя у неё в market-data нет вовсе.
  Механическая замена имени во втором классе была бы ложью: док с новым
  именем ставок не собирает.
- **`.claude/knowledge-tree.md`** обновлён тем же ходом
  (`.claude/rules/curation.md`): строка переименована вместе с вопросом
  узла, порядок сохранён.

## Прогоны после реконсиляции

- Девять команд корпуса — все 0.
- Сплошной прогон существования файловых указателей по живым носителям
  (`docs/**`, `.claude/rules`, `processes`, `skills`, `decisions`,
  `backlog.md`): битых ссылок, введённых реконсиляцией, — 0. Восемь
  оставшихся неразрешимых адресов предсуществуют и принадлежат уже
  зарегистрированным классам (`.claude/work/backlog.md` §«Указатели
  javadoc в несуществующие доки», §«Исполнимая форма предиката свечной
  целостности») либо являются иллюстративными плейсхолдерами
  (`docs/spec/x.json`, `docs/x/y.md`).

**Аппрув `SYNC_DOCS_FROM_CODE` получен.**
