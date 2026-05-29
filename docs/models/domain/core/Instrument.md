# Instrument

## На какой вопрос отвечает этот файл

Что это за доменная модель `Instrument`: структура, енумы,
персистентность, связи с биржевым воплощением.

## Назначение

`Instrument` — торговый инструмент биржи, базовая идентичность для
рыночных данных и торговли. Несёт внутренний `id`, межсервисный
`internalId`, привязку к `Exchange` (`exchangeId`) и биржевое
воплощение (`externalId` = OKX `instId`, `externalType`,
`externalMarginMode`). Владеет группами свечей (`candleGroups`).

Слой — `domain/core` (торговая модель с биржевым воплощением): у
инструмента есть прямое биржевое воплощение (`instId`), и он —
точка привязки рыночных данных и ордеров. Java-класс —
`domain.model.core.instrument.Instrument`, наследует `Auditable`
(см. `docs/models/domain/other/Auditable.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор инструмента. |
| `internalId` | `String` | Межсервисный идентификатор инструмента. |
| `exchangeId` | `Long` | Внутренний ID биржи (`Exchange.id`). |
| `externalId` | `String` | Имя инструмента на бирже (OKX `instId`), например `ETH-USDT-SWAP`. |
| `externalType` | `String` | Тип инструмента на бирже: `SPOT`/`MARGIN`/`SWAP`/`FUTURES`/`OPTION`. |
| `status` | `Status` | Нормализованный статус жизненного цикла инструмента. |
| `marginMode` | `MarginMode` | Режим маржи (нормализованный enum). |
| `externalMarginMode` | `String` | Сырой режим маржи биржи (`cross`/`isolated`). |
| `leverage` | `Integer` | Плечо. |
| `candleGroups` | `List<CandleGroup>` | Группы свечей по таймфреймам инструмента (см. `docs/models/domain/other/CandleGroup.md`). |

Поля аудита (`createdAt`/`modifiedAt`/`externalCreatedAt`/… ) —
из `Auditable`.

## Енумы

### `Status`

`CREATED`, `HOLD`, `SYNC`, `CANDLES_LOADING`, `ACTIVE`, `CLOSED`,
`ERROR`. Статус жизненного цикла инструмента в системе (готовность
к торговле: от создания и синхронизации спецификации к загрузке
свечей и `ACTIVE`).

### `MarginMode`

`ISOLATED`, `CROSS`. Нормализованный режим маржи; сырой биржевой
режим хранится отдельно в `externalMarginMode` (String).

## Биржевое воплощение и справочные поля

`Instrument` несёт только идентичность и торговую конфигурацию
(`externalId`, `externalType`, `marginMode`, `leverage`). Справочные
поля спецификации биржи (base/quote/settle currency, `lotSz`,
`minSz`, `ctVal`, `ctMult`, `tickSz`) на доменном `Instrument` **не
хранятся** — они приходят в граничном `InstrumentExternalSnapshot`
(класс
`domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot`;
mapping snapshot↔domain — на `DOCS_CHECK_2`, см. ниже).
Торговые ограничения инструмента (tick/lot/min/max sizes, max
leverage, статус) — отдельная модель
`docs/models/domain/other/InstrumentExternalRules.md`.

> Разграничение `Instrument` ↔ `InstrumentExternalSnapshot` ↔
> `InstrumentExternalRules` (где именно живут base/quote/settle и
> прочие справочные поля, нет ли дублирования) — на доработке;
> отслеживается для `DOCS_CHECK_2` (см. backlog п.9).

## Персистентность

Хранится в БД (entity `InstrumentEntity`, таблица `instruments`),
наследует audit-поля. Ограничения схемы:

- `id` — identity (autoincrement).
- Уникальность: `internal_id` (uk_instrument_internal_id);
  пара `(exchange_id, external_id)`
  (uk_instrument_exchange_id_external_id).
- `internal_id`, `exchange_id`, `external_id`, `external_type`,
  `status`, `margin_mode`, `leverage` — `NOT NULL`;
  `external_margin_mode` — nullable.
- `internal_id`, `exchange_id`, `margin_mode` — `updatable = false`
  (неизменны после создания).
- `candleGroups` — `OneToMany` (mappedBy `instrument`), cascade
  `ALL`, `orphanRemoval = true`.

## Связи

- Биржа-владелец — `docs/models/domain/core/Exchange.md`.
- Группы свечей — `docs/models/domain/other/CandleGroup.md`.
- Торговые правила инструмента —
  `docs/models/domain/other/InstrumentExternalRules.md`.
- Audit-база — `docs/models/domain/other/Auditable.md`.
