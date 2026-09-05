# CandleGroup

## На какой вопрос отвечает этот файл

Что это за доменная модель `CandleGroup`.

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
| `internalId` | `String` | Межсервисный идентификатор (наружу отдаётся вместо `id`); генерируется системой из `internalId` инструмента и таймфрейма, уникален. |
| `instrumentId` | `Long` | Инструмент-владелец (`Instrument.id`). |
| `timeframe` | `TimeFrame` | Канонический таймфрейм группы (enum, см. ниже). |
| `externalTimeframe` | `String` | Таймфрейм в формате биржи (сырой, например `1H`) — **носитель донора**: market-data его не пишет и не хранит, перевод делает коннектор (`docs/models/mapping/TimeFrame.md`). |
| `status` | `Status` | Статус жизненного цикла загрузки свечей. |
| `plannedFirstUtcMillis` | `Long` | **Горизонт бэкфилла**: нижняя граница истории, заказанная потребителем, UTC мс. Пусто — глубина не названа, и выкачка идёт до конца истории площадки. |
| `actualFirstUtcMillis` | `Long` | Время открытия **первой** фактически загруженной свечи группы, UTC мс. |
| `actualLastUtcMillis` | `Long` | Время открытия **последней** фактически загруженной свечи группы, UTC мс. |
| `count` | `Long` | Поддерживаемое число свечей в группе (обновляется при записи; основа проверки целостности). |

Пара `(instrumentId, timeframe)` уникальна. `actualFirst`/`actualLast`
— фактические границы загруженной истории. `count` поддерживается при
записи свечей и на старте реконсилируется реальным `COUNT(*)` (защита от
рассинхрона после рестарта в середине пачки). Поля аудита — из
`Auditable`.

## Горизонт бэкфилла принадлежит группе

**Дом величины — здесь**; остальные носители ссылаются, форму не
переписывают (`.claude/rules/policy-home.md`).

**Глубину называет требование потребителя вместе с таймфреймом**
(`docs/architecture/market-data-collection.md` — дом контракта
требования), а у 1m и 1D одного инструмента она разная: общий горизонт на
инструмент качал бы годы минутных баров ради дневного требования.
Поэтому `plannedFirstUtcMillis` — колонка **группы**, а не инструмента.

**Повтор требования глубже стоящего расширяет горизонт**; мельче —
не сужает: собранное заказал кто-то другой, и выбрасывать его нельзя
(`docs/rules/idempotency-via-unique.md`).

**Углублённый горизонт возвращает группу к `BACKFILL` из любого живого
статуса** — дотягивать нижнюю границу умеет только он. Терминальные
`ERROR` и `DELETED` требованием не оживляются: иначе чужая команда
обнуляла бы исчерпанные попытки докачки. Переход —
`docs/lifecycles/CandleGroup.md`.

**Горизонта на инструменте нет.** Поле `Instrument.plannedCandleStartDate`
— носитель донора: действующей величиной оно не является, и market-data
его не читает и не пишет.

## Енум `TimeFrame`

`TimeFrame` — доменный enum таймфреймов свечей/индикаторов.
Первоисточник — свечная подсистема: `CandleGroup` структурно
определяется таймфреймом (`timeframe: TimeFrame`). Значения:

`ONE_SECOND`, `ONE_MINUTE`, `THREE_MINUTES`, `FIVE_MINUTES`,
`FIFTEEN_MINUTES`, `ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `ONE_DAY`.

**Легальность `ONE_SECOND` ограничена явно:**

- **в группах свечей — не допускается.** `CandleGroup` секундного
  таймфрейма не заводится: свечная подсистема грузит и хранит историю от
  минуты и выше, и секундная группа означала бы объём хранения, под
  который ретеншен и density-инвариант не проектировались
  (`docs/rules/market-data-retention.md`).
  Гейт — валидация при заведении группы;
- **в дереве стратегии — не допускается.** Настройки индикаторов и
  структуры (`docs/models/domain/aggregate/Strategy.md`) читают
  свечи из групп, которых с секундным таймфреймом не существует; значение
  реджектится валидацией дерева
  (`docs/rules/strategy-validation.md`);
- **единственный легальный потребитель** — координата 2 ссылки на свечу
  курса на строке `DealCashFlow`. Она свечу не хранит и группы не
  заводит: свеча котировки добывается у источника и локально не
  персистится (`docs/components/RefreshBillsExecutor.md`).

**Доступность секундного разрешения у источника — открытый факт**, и
решением он не закрыт: неизвестно, отдаёт ли источник секундный бар на
нужных парах котировки. Енум обязан уметь выразить разрешение независимо
от того, какое из них окажется доступным, — иначе лестница огрубления
разрешения (`docs/components/RefreshBillsExecutor.md`) не выразима
вовсе.

OKX-строк enum не хранит; маппинг доменного значения ↔ строка
биржи (`ONE_HOUR ↔ "1H"`) живёт в `docs/models/mapping/TimeFrame.md`
и `TimeFrameMapper`.

## Целостность по count (density-инвариант)

Здоровая группа полностью **плотна** на `[actualFirst, actualLast]`:

```text
count == (actualLast − actualFirst) / step + 1
```

где `step` — длительность бара таймфрейма (`timeframe`). Плотность
меряется на фактических границах `[actualFirst, actualLast]`;
дотягивание нижней границы до планового горизонта
(`plannedFirstUtcMillis`) — забота `BACKFILL`, не
density-инварианта. Идемпотентность записи и index-only `COUNT` —
по уникальному `(candle_group_id, open_timestamp)`. Дефицит `count`
относительно ожидаемого по формуле → локализация дыры бинарным
поиском по count и точечная докачка в lifecycle
(`docs/lifecycles/CandleGroup.md`). Реестра известных пропусков нет: постоянная
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
  инструмент + таймфрейм; `internal_id`
  (uk_candle_group_internal_id) — уникален.
- `internal_id`, `instrument_id`, `timeframe`, `status`, `count` —
  `NOT NULL` (`count` default `0`);
  `planned_first_utc_millis` — nullable (глубина не названа);
  `actual_first_utc_millis`/`actual_last_utc_millis` — nullable
  (null при `count = 0`). `internal_id` — `updatable = false`.
- Связь с инструментом — **внешний ключ колонкой** (`instrument_id`,
  `updatable = false`), не объектной связью: ряд свечей группы читают
  по её идентификатору, а сам инструмент — своим чтением.
- `timeframe` и `status` хранятся строкой (имя enum); enum — только
  в домене (codestyle: enum'ы объявляются в доменном слое).
- **`external_timeframe` в схеме нет.** Бар площадки — словарь
  коннектора, и перевод доменного таймфрейма в него живёт на границе
  источника (`docs/models/mapping/TimeFrame.md`); хранить копию чужого
  словаря на своей строке значило бы завести второй носитель, который
  разойдётся с первым при второй площадке. Поле `externalTimeframe` на
  доменной форме — носитель донора.

## Связи

- Инструмент-владелец — `docs/models/domain/core/Instrument.md`.
- Свеча — `docs/models/domain/other/Candle.md`.
- Lifecycle загрузки — `docs/lifecycles/CandleGroup.md`.
- Производитель / оркестрация — `docs/components/CandleJob.md`,
  `docs/processes/candle-loading.md`.
- Mapping таймфреймов — `docs/models/mapping/TimeFrame.md`.
- Audit-база — `docs/models/domain/other/Auditable.md`.
