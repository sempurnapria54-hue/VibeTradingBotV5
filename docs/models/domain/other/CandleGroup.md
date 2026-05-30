# CandleGroup

## На какой вопрос отвечает этот файл

Что это за доменная модель `CandleGroup`: структура, енум
`TimeFrame`, целостность по count, персистентность; где описан её
lifecycle.

## Назначение

`CandleGroup` — группа свечей одного инструмента и одного
таймфрейма. Несёт привязку к инструменту (`instrumentId`),
таймфрейм, статус жизненного цикла загрузки свечей, фактические
границы загруженной истории (`actualFirstUtcMillis`/
`actualLastUtcMillis`) и поддерживаемый `count`. Единица, вокруг
которой идёт загрузка/докачка/проверка целостности свечной
истории. Слой — `domain/other` (прочая хранимая модель;
свечи). Java-класс — `domain.model.trade.candle.CandleGroup`,
наследует `Auditable` (см. `docs/models/domain/other/Auditable.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор группы. |
| `instrumentId` | `Long` | Инструмент-владелец (`Instrument.id`). |
| `timeframe` | `TimeFrame` | Канонический таймфрейм группы (enum, см. ниже). |
| `externalTimeframe` | `String` | Таймфрейм в формате биржи (сырой, например `1H`). |
| `status` | `Status` | Статус жизненного цикла загрузки свечей. |
| `actualFirstUtcMillis` | `Long` | Время открытия **первой** фактически загруженной свечи группы, UTC мс. |
| `actualLastUtcMillis` | `Long` | Время открытия **последней** фактически загруженной свечи группы, UTC мс. |
| `count` | `Long` | Поддерживаемое число свечей в группе (обновляется при записи; основа проверки целостности). |

Пара `(instrumentId, timeframe)` уникальна. `actualFirst`/`actualLast`
— фактические границы загруженной истории (per-ТФ); плановый
горизонт «до куда грузить» задаётся **на инструмент**
(`Instrument.plannedCandleStartDate`), общий для всех таймфреймов,
не на группу. `count` поддерживается при записи свечей и на старте
реконсилируется реальным `COUNT(*)` (защита от рассинхрона после
рестарта в середине пачки). Поля аудита — из `Auditable`.

> **Расхождение класс↔концепция.** В Java-классе `timeframe` пока
> `String`; целевой вид — канонический enum `TimeFrame` (как
> `Instrument.marginMode` — нормализованный enum при сыром
> `externalMarginMode: String`). Здесь зафиксирован целевой вид;
> приведение типа в классе — на этапе `CODE` шага 1.

## Енум `TimeFrame`

`TimeFrame` — доменный enum таймфреймов свечей/индикаторов.
Первоисточник — свечная подсистема: `CandleGroup` структурно
определяется таймфреймом (`timeframe: TimeFrame`). Значения:

`ONE_MINUTE`, `THREE_MINUTES`, `FIVE_MINUTES`, `FIFTEEN_MINUTES`,
`ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `ONE_DAY`.

OKX-строк enum не хранит; маппинг доменного значения ↔ строка
биржи (`ONE_HOUR ↔ "1H"`) живёт в `docs/models/mapping/TimeFrame.md`
и `TimeFrameMapper`.

> `TimeFrame` используется и настройками strategy-tree
> (`docs/models/domain/aggregate/Strategy.md` §TimeFrame). По
> критерию первоисточника каноническое описание enum — здесь;
> раздел в `Strategy.md` должен стать ссылкой сюда (свёртка — на
> шаге 2, см. backlog). Остаточная развилка размещения — TIME-Q1.

## Целостность по count (density-инвариант)

Здоровая группа полностью **плотна** на `[actualFirst, actualLast]`:

```text
count == (actualLast − actualFirst) / step + 1
```

где `step` — длительность бара таймфрейма (`timeframe`). Плотность
меряется на фактических границах `[actualFirst, actualLast]`;
дотягивание нижней границы до планового горизонта
(`Instrument.plannedCandleStartDate`) — забота `BACKFILL`, не
density-инварианта. Идемпотентность записи и index-only `COUNT` —
по уникальному `(candle_group_id, open_timestamp)`. Дефицит `count`
относительно ожидаемого по формуле → локализация дыры бинарным
поиском по count и точечная докачка в lifecycle
(`docs/lifecycles/CandleGroup.md` §«Политика загрузки и
целостности»). Реестра известных пропусков нет: постоянная
внутренняя дыра (биржа реально пуста) после исчерпания попыток
переводит группу в `ERROR`.

## Жизненный цикл загрузки

`Status` (`CREATED`/`BACKFILL`/`SYNC`/`CHECK`/`REPAIR`/`ACTIVE`/
`ERROR`/`DELETED`) — отдельный lifecycle:
`docs/lifecycles/CandleGroup.md` (назначение состояний, кто
управляет, общий поток загрузки/докачки/проверки). Оркестрация
переходов — `docs/components/CandleJob.md` в процессе
`docs/processes/candle-loading.md`.

## Персистентность

Хранится в БД (entity `CandleGroupEntity`, таблица `candle_groups`),
наследует audit-поля. Ограничения схемы:

- `id` — identity (autoincrement).
- Уникальность: `(instrument_id, timeframe)`
  (uk_candle_group_instrument_timeframe) — одна группа на
  инструмент + таймфрейм.
- `instrument_id`, `timeframe`, `external_timeframe`, `status`,
  `count` — `NOT NULL` (`count` default `0`);
  `actual_first_utc_millis`/`actual_last_utc_millis` — nullable
  (null при `count = 0`).
- Связь с инструментом — `ManyToOne` (`instrument_id`,
  `updatable = false`).
- `status` хранится строкой (имя enum).

## Связи

- Инструмент-владелец — `docs/models/domain/core/Instrument.md`.
- Свеча — `docs/models/domain/other/Candle.md`.
- Lifecycle загрузки — `docs/lifecycles/CandleGroup.md`.
- Производитель / оркестрация — `docs/components/CandleJob.md`,
  `docs/processes/candle-loading.md`.
- Mapping таймфреймов — `docs/models/mapping/TimeFrame.md`.
- Audit-база — `docs/models/domain/other/Auditable.md`.
