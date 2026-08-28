# Candle

## На какой вопрос отвечает этот файл

Что это за доменная модель `Candle`: структура, персистентность,
правило закрытых свечей.

## Назначение

`Candle` — одна свеча рынка (OHLCV) в UTC, относящаяся к группе
свечей одного инструмента и таймфрейма (`CandleGroup`). База для
расчёта индикаторов и анализа рынка. Слой — `domain/other` (прочая
хранимая модель; свечи). Java-класс —
`domain.model.trade.candle.Candle`, наследует `Auditable` (см.
`docs/models/domain/other/Auditable.md`). Готовит свечи
`docs/components/CandleJob.md`; OKX-формат и конвертация —
`docs/models/mapping/Candle.md`.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор свечи. |
| `candleGroupId` | `Long` | ID группы свечей (`CandleGroup.id`). |
| `openTimestamp` | `Long` | Время **открытия** свечи, UTC миллисекунды. |
| `open` | `BigDecimal` | Цена открытия. |
| `high` | `BigDecimal` | Максимум за интервал. |
| `low` | `BigDecimal` | Минимум за интервал. |
| `close` | `BigDecimal` | Цена закрытия. |
| `volume` | `BigDecimal` | Объём торгов за интервал. |

Таймфрейм у свечи отдельным полем не хранится — он атрибут группы
(`CandleGroup.timeframe`). Свеча идентифицируется парой
`(candleGroupId, openTimestamp)`. OKX отдаёт несколько объёмов
(`vol`/`volCcy`/`volCcyQuote`) — домен хранит один `volume` (см.
`docs/models/mapping/Candle.md`). Поля аудита — из `Auditable`.

## Правило закрытых свечей

Признак закрытия (`confirm`) на доменной `Candle` **не хранится**:
правило производящей стороны — в БД попадают только закрытые
(`confirm=1`) свечи. Незакрытую свечу `CandleJob` не сохраняет (см.
`docs/components/CandleJob.md`, `docs/models/mapping/Candle.md`
§`confirm` policy). Так все расчёты идут без look-ahead.

## Персистентность

Хранится в БД (entity `CandleEntity`, таблица `candles`),
наследует audit-поля. Ограничения схемы:

- `id` — identity (autoincrement).
- Уникальность: `(candle_group_id, open_timestamp)`
  (uk_candle_group_id_open_timestamp) — идемпотентность загрузки и
  основа проверки целостности по count.
- `candle_group_id`, `open_timestamp`, `open`, `high`, `low`,
  `close` — `NOT NULL`; `volume` — nullable.
- `open`/`high`/`low`/`close`/`volume` — `precision = PRICE_PRECISION`,
  `scale = PRICE_SCALE` (общие константы цены).
- `candle_group_id` управляется через связь группы
  (`insertable = false`, `updatable = false`).

## Связи

- Группа-владелец и lifecycle загрузки —
  `docs/models/domain/other/CandleGroup.md`,
  `docs/lifecycles/CandleGroup.md`.
- Производитель — `docs/components/CandleJob.md`.
- Mapping и OKX-формат — `docs/models/mapping/Candle.md`.
- Audit-база — `docs/models/domain/other/Auditable.md`.
