# DOCS_CHECK_1 — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

На каком шаге мы в проверке целостности концепции доков под код
шага 1 (первая итерация) и какие пробелы найдены.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных (коннект к
  OKX, инструменты, цены/свечи, свежесть)».
- Под-шаг процесса: `DOCS_CHECK_1` (первая итерация проверки),
  `.claude/processes/roadmap-step-execution.md`.
- Тулинг: роль `reviewer` (`.claude/agents/reviewer.md`), фокус
  `concept-review` (`.claude/skills/concept-review.md`).
- Порог глубины — функциональный: фиксируем то, что нужно коду
  шага 1 (коннектор OKX к рыночным данным; загрузка/хранение
  свечей; инструменты и их правила; раздача текущих цен;
  свежесть).
- **Это первый прогон тулинга (обкатка).** Формулировки скилла и
  роли уточняются по итогам.

## Охват

### Проверено (в охвате)

- **Компоненты:** `ClientService`, `MarketPriceDataService`,
  `CandleJob`, `InstrumentExternalRulesSyncJob`,
  `InstrumentExternalRulesService`, `MarketDataExpirationChecker`,
  `components/models/MarketPriceData`.
- **Процесс:** `docs/processes/market-data-calculation.md`.
- **Модели (domain):** `docs/models/domain/other/InstrumentExternalRules.md`.
- **Mapping:** `MarketPriceData`, `Candle`, `TimeFrame`,
  `InstrumentExternalRules`.
- **externalSnapshot:** `README` (поля
  `MarketPriceDataExternalSnapshot` /
  `InstrumentExternalRulesExternalSnapshot` задокументированы в
  model/mapping — отдельные файлы не требуются по правилу слоя).
- **Интеграции OKX (contracts):** `market-price-data`, `candle`,
  `instrument`, `service-urls`.
- **Интеграции OKX (rules):** `adapter-constants`, `ws-limits`.
- **Правила:** `market-data-freshness`, `raw-exchange-dto-boundary`,
  `trading-constraints`, `exchange-hold`.
- **Словарь:** пуст (записи `time-frame` нет).

### Вне охвата (помечено, не проверялось)

Шаги-потребители рыночных данных, не нужные для кода шага 1:

- **Индикаторы (шаг 3):** `IndicatorValue`, `IndicatorJob`,
  `IndicatorService`.
- **Структура/фаза рынка (Фаза 4-5 / шаг 8):** `MarketStructure`,
  `MarketPhase`, `MarketPriceLevel`, соответствующие jobs/сервисы.
- **Торговые сущности и их обвязка (шаги 2,4-7):** `Order`,
  `AlgoOrder`, `Position`, `BalanceContainer`, `Deal`, `Strategy`
  (+ lifecycles, mapping, OKX responses), risk / команды /
  executor'ы / FSM / deal-компоненты.
- **Fills / bills / archive (шаг 7):** OKX-Q1/Q2/Q3.
- **Anomaly / kill-switch (шаг 8).**

Примечание: `market-data-calculation.md` объединяет в одну цепочку
job'ы шага 1 (`CandleJob`, `InstrumentExternalRulesSyncJob`) и
job'ы поздних шагов (indicator/structure/phase). Для шага 1
релевантны только первые два + `MarketPriceDataService`.

## Пробелы по типам

### 1. Несогласованности между доками

**Н1. Ссылка на механизм backfill ведёт не туда.**
`docs/processes/market-data-calculation.md` (раздел «Активация
стратегии и готовность данных»): «механизм backfill —
форвард-заметка backlog п.8». Но backlog п.8 — это «Strategy:
enforcement, валидатор, примеры», не backfill. Механизм backfill /
warmup свечной истории фактического адреса в backlog не имеет.

**Н2. Указатели backlog на `docs/deprecated/` — битые.**
Backlog п.5 ссылается на исходник `Candle` как
`docs/deprecated/.../Candle.md`, п.6 — на `TradeFill.md` /
`TradeFillsArchive.md` там же. Каталог `docs/deprecated/` пуст
(файлов нет). Исходный материал, если есть, — в
`.claude-archive/2026-05-21/`. Влияет на то, откуда `GAPS_CLOSE_1`
возьмёт поля `Candle` (см. эскалацию Э4). *(Пограничное: это
пайплайн-док, не `docs/`-концепция; зафиксировано, т.к. влияет на
закрытие пробела name-level по `Candle`.)*

### 2. Name-level без структуры (где структура нужна коду шага 1)

**N1. Доменная модель `Instrument` — отсутствует.**
`Instrument` фигурирует по имени во многих доках как доменный тип:
`CalculationContext.instrument` (`Instrument`),
`DealContext.instrument` (`Instrument`), `Deal.instrumentId`
(`Long`, «полный `Instrument` — в `DealContext`»);
`mapping/InstrumentExternalRules.md` явно говорит, что
`baseCcy`/`quoteCcy`/`settleCcy` «хранятся в domain `Instrument`,
не здесь». При этом файла модели `Instrument` в `docs/models/`
нет. Для шага 1 (инструменты) код обязан иметь сущность
инструмента: внутренний `instrumentId: Long`, внешний `instId`,
base/quote/settle ccy, привязку к `Exchange`, торгуемость/статус,
схему хранения. Сейчас источник внутреннего `instrumentId` (на
который ссылаются `MarketPriceData` и `InstrumentExternalRules`)
не определён.
Известно отслеживание: backlog п.9 («полная модель/lifecycle
`Exchange`/`Instrument`/`Account`», явно включает «standalone
модель `Instrument` для market-data (из п.5)»);
`docs/rules/exchange-hold.md` — «Полная модель/lifecycle
`Exchange`/`Instrument` — backlog п.9». То есть пробел известен и
отложен, но кодом шага 1, похоже, требуется → см. эскалацию Э3.

**N2. Persisted-модель `Candle` — отсутствует.**
`CandleJob` «сохраняет данные в доменные таблицы»;
`market-data-calculation.md` требует идемпотентности «уникальность
по instrument + … + candle/window timestamp; checkpoint по
последнему timestamp». Для кода шага 1 (хранение свечей)
функциональный порог = схема хранения: поля (`instrumentId`,
`timeFrame`, OHLC, объёмы, время открытия, признак закрытия),
типы, nullability, уникальность (instrument + timeframe + open ts),
индексы. В live-доках есть только формат OKX-массива
(`mapping/Candle.md`) и конвертация типов — нет доменной/persistence
модели свечи. Отслеживание: backlog п.5 («standalone модель
`Candle`», исходник помечен `docs/deprecated/` — пусто, см. Н2).

**N3 (пограничное). Инвентарь нативных DTO источника для
рыночных данных — отсутствует.**
`docs/models/integrations/okx/` содержит инвентари для
order/algo/position/balance/fill/fills-archive/account-bill, но
**нет** `OkxTickerResponse`, `OkxInstrumentResponse`,
`OkxCandleResponse`, хотя `OkxTickerResponse` и
`OkxInstrumentResponse` упоминаются по имени в mapping-доках.
Смягчающее: маппируемые и немаппируемые поля перечислены прямо в
mapping-доках (`mapping/MarketPriceData.md`,
`mapping/InstrumentExternalRules.md`, `mapping/Candle.md` —
9-элементный массив), и нативные типы OKX (строки) выводимы. Для
кода маппера, вероятно, достаточно; но онтология слоёв покрыта
неравномерно. Блокер или нет — см. эскалацию Э5.

### 3. Неотвеченные вопросы (open-questions)

**OKX-Q4 (WS-каналы OKX — отдельный заход).** Контракты
`market-price-data.md` / `candle.md` / `instrument.md` называют WS
(`tickers`, `candle<bar>`, `instruments`) «основным
runtime-источником», а REST — fallback. Протокол подписок, формат
push-сообщений, поведение каналов не задокументированы (только
`ws-limits.md` — лимиты соединения, и `service-urls.md` — base
URL). Блокер ли для шага 1 — зависит от того, входит ли WS-стрим в
скоуп шага 1 (см. эскалацию Э1).

**TIME-Q1 (размещение enum `TimeFrame`).** Значения enum и
OKX-маппинг полностью определены (`mapping/TimeFrame.md`); открыт
только вопрос, где живёт *дока* enum. Код enum написать ничто не
мешает. **Не блокер** для кода шага 1; уместно закрыть при
материализации enum, но не обязательное предусловие `CODE`.

## Блокирующие открытые вопросы

Отдельным проходом по `open-questions.md` — какие из 12 открытых
вопросов блокируют код шага 1:

- **OKX-Q4** — потенциальный блокер (через скоуп WS, Э1).
- **TIME-Q1** — релевантен, но не блокер (enum определён).
- DEAL-Q1/Q2/Q3, PROC-Q1, RISK-Q1, ENUM-Q1, CMD-Q1, OKX-Q1/Q2/Q3
  — относятся к стратегии / риску / командам / сделкам / fills
  (шаги 2,4-7). **Шаг 1 не блокируют.**

## Эскалации (решает пользователь на `GAPS_CLOSE_1`)

- **Э1. Скоуп шага 1: WS vs REST.** Входит ли в «коннект к OKX»
  WS-стрим тикеров/свечей, или шаг 1 — REST-first (запрос/поллинг),
  а WS откладывается? От этого зависит, блокер ли OKX-Q4.
- **Э2. Объём «свежести» в шаге 1.** В одной строке шага есть
  «свежесть», но документированный механизм
  (`MarketDataExpirationChecker.checkForEntry(Strategy)` /
  `checkForStep(DealContext, StrategyStep)`) завязан на
  `Strategy`/`StrategyStep`/`DealContext` (поздние шаги), а сроки
  (`expirationDuration`) — в settings `Strategy` (шаг 2). Что из
  свежести относится к шагу 1 (производящая сторона:
  timestamp'ы + работа только с закрытыми свечами `confirm=1`),
  что — к потребителям позже?
- **Э3. `Instrument`/`Exchange`: материализовать сейчас или
  держать отложенным (backlog п.9).** Шагу 1, похоже, нужна как
  минимум минимальная идентичность инструмента (внутренний
  `instrumentId` ↔ `instId`, base/quote/settle ccy, привязка к
  `Exchange`). Материализуем минимальную модель `Instrument` (и
  `Exchange`?) под шаг 1 или есть меньший стаб, разблокирующий
  рыночные данные? (Связано с N1.)
- **Э4. Где исходник полей `Candle` (и `Instrument`) для
  закрытия.** Указатели backlog на `docs/deprecated/` битые (Н2);
  материал, вероятно, в `.claude-archive/2026-05-21/`. Подтвердить
  источник перед `GAPS_CLOSE_1`. (Связано с N2/N1.)
- **Э5. Нужны ли отдельные инвентари `Okx*Response` для
  рыночных данных** (`OkxTickerResponse` / `OkxInstrumentResponse`
  / `OkxCandleResponse`) или покрытия в mapping-доках достаточно
  для кода шага 1? (Связано с N3.)
- **Э6. Backfill/warmup свечной истории — в скоупе шага 1?**
  `CandleJob` «обновляет историю свечей»; механика пагинации назад
  есть в `contracts/candle.md`, но «сколько истории» зависит от
  warmup индикаторов (шаг 3). Ссылка на механизм битая (Н1). Что
  из backfill относится к шагу 1?

## Сводка

- **Несогласованности:** 2 (Н1 битая ссылка backfill→п.8; Н2 битые
  указатели backlog на `docs/deprecated/`).
- **Name-level без структуры:** 2 явных блокер-кандидата (N1
  `Instrument`, N2 `Candle`) + 1 пограничный (N3 инвентари
  `Okx*Response` рыночных данных).
- **Открытые вопросы:** 1 потенциальный блокер (OKX-Q4 через
  скоуп), 1 не-блокер (TIME-Q1).
- **Эскалаций:** 6 (Э1-Э6).
- **Итог: не чисто.** Нужен `GAPS_CLOSE_1` (разбор эскалаций в
  чате + материализация концепции `Instrument`/`Candle` под скоуп
  шага 1), затем `DOCS_CHECK_2`.

## Размещение знания — не здесь

`concept-review` помечает пробелы, но не решает, где и как их
закрывать. Это `GAPS_CLOSE_1` (штатный поток
`recognize-knowledge` → классификаторы → `place-knowledge`).
Скоуп-развилки (Э1-Э3, Э6) — сначала в чат, затем размещение.
