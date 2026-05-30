# DOCS_CHECK_2 — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

На каком шаге мы во второй итерации проверки целостности концепции
доков под шаг 1 и какие пробелы найдены (gap-отчёт для
`GAPS_CLOSE_2`).

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных (коннект к
  OKX, инструменты, цены/свечи, свежесть)».
- Под-шаг: `DOCS_CHECK_2` (вторая итерация),
  `.claude/processes/roadmap-step-execution.md`; стадийный обход
  `concept-review` (`.claude/skills/concept-review.md`), роль
  `reviewer`.
- **Проверка — только по докам** (исправленная граница охвата
  скилла): doc↔doc несогласованности, name-level пробелы в доках,
  неотвеченные/отложенные вопросы. Код не читался и с кодом не
  сверялось.
- Вход: `snapshot-v17`, `phase-1-step-1-gaps-close-1.md` (4
  унесённых пункта — закрыты проверкой ниже, см. §«Унесённые
  пункты»).
- Порог глубины — функциональный: что доки должны
  специфицировать под потребность шага 1.

## Охват

### Проверено (доки)

- **Модели (domain):** `domain/core/Instrument.md`, `Exchange.md`;
  `domain/other/Candle.md`, `CandleGroup.md`, `Auditable.md`,
  `InstrumentExternalRules.md`; `components/models/MarketPriceData.md`.
- **Lifecycle:** `lifecycles/CandleGroup.md`.
- **Mapping:** `mapping/MarketPriceData.md`, `Candle.md`,
  `InstrumentExternalRules.md`, `TimeFrame.md`.
- **Инвентари источника (OKX):** `OkxInstrumentResponse.md`,
  `OkxCandleResponse.md`, `OkxTickerResponse.md`.
- **Компоненты:** `ClientService.md`, `CandleJob.md`,
  `InstrumentExternalRulesSyncJob.md`,
  `InstrumentExternalRulesService.md`, `MarketPriceDataService.md`,
  `MarketDataExpirationChecker.md`.
- **Процесс:** `processes/market-data-calculation.md`.
- **Правила:** `raw-exchange-dto-boundary.md`,
  `market-data-freshness.md`, `adapter-constants.md`.
- **Контракты OKX:** `contracts/candle.md`, `instrument.md`,
  `market-price-data.md`.
- **Open-questions:** проход по всем 12.

### Вне охвата (помечено, не проверялось)

- Доки потребителей рыночных данных поздних шагов: индикаторы
  (шаг 3), структура/фаза рынка (шаги 4-8), стратегия / сделки /
  риск / FSM (шаги 2,4-7). `MarketDataExpirationChecker.md` —
  потребительская сторона (свежесть проверяют потребители по
  `Strategy`/`StrategyStep`/`DealContext`), по Э2 вне шага 1;
  проверена только на «не блокирует шаг 1».
- `docs/models/rest/` — пустой скаффолд (REST-DTO нашего сервиса);
  по `structure.md` слой-скаффолд, под шаг 1 не требуется.

## Стадия остановки

Обход **прошёл все стадии** (на гейте не остановлен).

- **Стадия 0 (гейтящие технические / скоуп) — чиста.** WS/REST
  закрыт в `GAPS_CLOSE_1` (REST-first; контракты приведены),
  OKX-Q4 разблокирован для шага 1. Свежесть разведена (шаг 1
  производит таймстемпы + audit-поля; проверка устаревания — у
  потребителей позже). Гейтящих открытых вопросов нет.
- **Стадии 1-2 (процессы / компоненты + модели) — с пробелами.**

## Пробелы по типам

### 1. Несогласованности между доками

**Н1. Дублирование справочных полей инструмента между двумя
моделями.**

`InstrumentExternalRules.md` (модель) перечисляет
`externalBaseCurrency` / `externalQuoteCurrency` /
`externalSettleCurrency` среди **своих** полей. Одновременно
`mapping/InstrumentExternalRules.md` (§«Не маппимые поля OKX»),
`Instrument.md` (§«Биржевое воплощение и справочные поля») и
`OkxInstrumentResponse.md` утверждают, что base/quote/settle
приходят в `InstrumentExternalSnapshot`, **«не в этой модели»**.
Одни и те же справочные поля концепция помещает в две разные
модели — прямое doc↔doc противоречие. Затрагивает разграничение
`Instrument` ↔ `InstrumentExternalSnapshot` ↔
`InstrumentExternalRules` (= унесённый пункт #1). Тип:
несогласованность. См. эскалацию Э(2-1) (скоуп/блокер) и N1.

**Н2. Маппинг тикера ссылается на поле, которого нет в инвентаре
источника.**

`mapping/MarketPriceData.md` (§OKX) и
`components/models/MarketPriceData.md` берут snapshot-поле
`externalInstrumentType` из OKX `instType`. Но `OkxTickerResponse.md`
(инвентарь полей источника) `instType` не содержит — его нет ни
среди полей DTO, ни среди списка «не входят». Маппинг тянет
source-поле, отсутствующее в инвентаре. Тип: несогласованность.
Не блокер шага 1: раздача цены / тикер-фетч по докам отложены в
зону FSM/поздних шагов (`MarketPriceDataService.md`,
`OkxTickerResponse.md`), на онбординг инструментов и загрузку
свечей шага 1 не влияет. Чинится при материализации тикера.

**Н3. Универсальное правило DTO-границы расходится с маппингом
свечей.**

`raw-exchange-dto-boundary.md` (+ `ClientService.md`)
утверждают: за `ClientService` выходит **только**
`*ExternalSnapshot` («это и есть единственное, что выходит»). Но
`mapping/Candle.md` описывает прямой OKX-массив → доменная
`Candle` без какого-либо `CandleExternalSnapshot`; candle-snapshot
не упомянут ни в правиле (среди перечисленных граничных
снапшотов), ни в самом маппинге свечей. При этом `mapping/Candle.md`
ссылается на это правило как на сквозное. Концепция не
оговаривает, как свечи проходят границу (через snapshot или
доменными). Тип: несогласованность (спорная классификация — см.
эскалацию Э(2-3)).

### 2. Name-level без структуры (где структура нужна шагу)

**N1. Lifecycle онбординга `Instrument` — не описан.**

`Instrument.md` задаёт `Status` = `CREATED` / `HOLD` / `SYNC` /
`CANDLES_LOADING` / `ACTIVE` / `CLOSED` / `ERROR` —
последовательность, явно подразумевающая загрузочный жизненный
цикл (от создания и синхронизации спецификации к загрузке свечей
и `ACTIVE`). Но:

- нет `docs/lifecycles/Instrument.md` (у `CandleGroup` lifecycle
  есть, у `Instrument` — нет, хотя статус-набор богаче и
  ориентирован на загрузку);
- ни один процесс не описывает переходы
  `CREATED → SYNC → CANDLES_LOADING → ACTIVE`: триггеры, кто ведёт;
- не описана **координация** `Instrument.Status`
  (`SYNC`/`CANDLES_LOADING`) ↔ `CandleGroup.Status`
  (`BACKFILL`/`SYNC`/`CHECK`/`REPAIR`/`ACTIVE`):
  `market-data-calculation.md` говорит про «готовность данных»
  для активации **стратегии**, но не про онбординг/активацию
  **инструмента**.

Шаг 1 = онбординг инструментов, поэтому концепция должна задать
этот lifecycle. Тип: name-level. См. эскалацию Э(2-2) (глубина:
полный lifecycle vs минимальный онбординг; `Instrument.md` и
`Exchange.md` отсылают «полный lifecycle» в backlog п.9).

**N2. `mapping/Instrument.md` (snapshot↔domain) отсутствует;
персистентный дом справочных полей не задан.**

`Instrument.md` сам обещает «mapping snapshot↔domain — на
`DOCS_CHECK_2`», но файла `mapping/Instrument.md` нет (в
`docs/models/mapping/` для инструмента есть только
`InstrumentExternalRules.md`). Шире: концепция не специфицирует,
**где справочные поля инструмента живут персистентно**.
`Instrument.md` явно говорит, что base/quote/settle и sizes на
domain `Instrument` **не хранятся** и приходят в граничном
`InstrumentExternalSnapshot`; `InstrumentExternalRules` (persisted)
— отдельная модель, но с противоречием Н1. Итог: нет описания
перехода snapshot↔domain и нет однозначного персистентного дома
справочных полей. Тип: name-level / несогласованность (тесно
связан с Н1). = унесённый пункт #1.

### 3. Неотвеченные / отложенные вопросы

**Q1. Политика загрузки/целостности свечей не специфицирована
(= унесённый пункт #2).**

`lifecycles/CandleGroup.md` (§«Что отложено»), `CandleJob.md` и
`market-data-calculation.md` **сами** помечают как
недоспецифицированное:

- политику `REPAIR` при обнаружении дыры (размер окон, шаги
  бинарного поиска по count, число попыток до `ERROR`);
- глубину «всей» истории с учётом предела OKX (`contracts/candle.md`
  даёт примитивы: `market/candles` ≤ 1440, `limit`; пагинация
  назад по `history-candles`), но саму политику глубины — нет;
- условия и расписание переходов `SYNC → CHECK`.

`CandleJob` и lifecycle `CandleGroup` — ядро шага 1, поэтому
концепция должна задать политику до `CODE`. Тип: неотвеченный
вопрос (не-гейтящий: деталь внутри понятной REST-механики
пагинации). Блокирует специфицирование шага 1.

## Блокирующие открытые вопросы (проход по `open-questions.md`)

Гейтящих нет. По релевантности шагу 1:

- **OKX-Q4** (WS-каналы) — разблокирован для шага 1 (REST-first);
  не блокер.
- **TIME-Q1** — сужен (canon enum `TimeFrame` размещён в
  `CandleGroup.md`); не блокер. Хвост (свёртка раздела `TimeFrame`
  в `Strategy.md` до ссылки) — шаг 2, вне шага 1 (= унесённый
  пункт #4).
- Остальные 10 (DEAL-Q1/2/3, PROC-Q1, RISK-Q1, ENUM-Q1, CMD-Q1,
  OKX-Q1/2/3) — шаги 2-8, шаг 1 не блокируют.

## Эскалации (решает пользователь на `GAPS_CLOSE_2`)

- **Э(2-1). Скоуп `InstrumentExternalRules` [Н1, N2].** Входит ли
  `InstrumentExternalRules` в шаг 1 или это поздние шаги (4-5:
  округление/sizing/риск) / backlog п.9? От этого зависит, блокер
  ли дублирование справочных полей (Н1) для шага 1 и нужно ли уже
  сейчас разнести base/quote/settle между `InstrumentExternalRules`
  и `InstrumentExternalSnapshot` + задать персистентный дом.
- **Э(2-2). Глубина lifecycle инструмента [N1].** Материализовать
  lifecycle `Instrument` (отдельный lifecycle-док + переходы
  онбординга + координация с `CandleGroup`) под шаг 1, или для
  шага 1 достаточно минимального онбординга, а «полный lifecycle»
  остаётся в backlog п.9 (куда его отсылают `Instrument.md` /
  `Exchange.md`)? Функциональный порог трактуется на грани.
- **Э(2-3). Классификация Н3 [Н3].** Расхождение «правило
  `*ExternalSnapshot` ↔ прямой domain `Candle`» — это
  несогласованность, требующая ввести `CandleExternalSnapshot` в
  концепцию (или оговорить исключение в правиле), или допустимое
  умолчание, и тогда не пробел?

## Унесённые пункты `GAPS_CLOSE_1` — закрыты проверкой

1. **`Instrument` ↔ `InstrumentExternalSnapshot` ↔
   `InstrumentExternalRules` + `mapping/Instrument.md`** → **Н1 +
   N2.** Внутри доков: прямое противоречие по base/quote/settle
   (две модели держат одни поля) + отсутствует `mapping/Instrument.md`
   + не задан персистентный дом справочных полей. Не закрыт.
2. **Детали backfill/repair, глубина истории, `SYNC→CHECK`** →
   **Q1.** Доки сами помечают как отложенное; по-прежнему не
   специфицировано. Не закрыт.
3. **`CandleGroup.timeframe` → enum.** В доках enum `TimeFrame`
   специфицирован полно и консистентно (`CandleGroup.md`
   §TimeFrame, `mapping/TimeFrame.md`), расхождение «класс↔концепция»
   в `CandleGroup.md` явно помечено и отнесено на `CODE`. С точки
   зрения **доков пробела нет**; приведение типа в классе — вне
   охвата docs-only проверки (под-шаг `CODE`).
4. **Свёртка `TimeFrame` в `Strategy.md`** → TIME-Q1, шаг 2; вне
   шага 1 (корректно отложено).

## Сводка

- **Несогласованности:** 3 (Н1 дублирование справочных полей
  инструмента — блокер-кандидат шага 1; Н2 `instType` в маппинге
  тикера vs инвентарь — не блокер, тикер отложен; Н3 правило
  DTO-границы vs маппинг свечей — спорная классификация).
- **Name-level:** 2 (N1 lifecycle онбординга `Instrument`; N2
  отсутствует `mapping/Instrument.md` + персистентный дом
  справочных полей) — оба относятся к ядру шага 1 (онбординг
  инструментов).
- **Неотвеченные:** 1 (Q1 политика загрузки/целостности свечей =
  унесённый пункт #2).
- **Эскалаций:** 3 (Э(2-1) скоуп rules; Э(2-2) глубина lifecycle
  инструмента; Э(2-3) классификация Н3).
- **Стадия остановки:** прошёл все стадии (0 чиста — REST-first;
  1-2 с пробелами); на гейте не остановлен.
- **Итог: НЕ чисто.** Нужен `GAPS_CLOSE_2` (3 эскалации в чате +
  закрытие Н1/N1/N2/Q1 в доках), затем `DOCS_CHECK_3`.

## Размещение знания — не здесь

`concept-review` помечает пробелы, но не закрывает их. Разнесение
справочных полей, материализация lifecycle инструмента и
`mapping/Instrument.md`, политика свечей, согласование правила
DTO-границы со свечами — на `GAPS_CLOSE_2` (штатный поток
`recognize-knowledge` → классификаторы → `place-knowledge`).
Скоуп-развилки (Э(2-1)…Э(2-3)) — сначала в чат.
