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
| `status` | `Status` | Нормализованный статус жизненного цикла инструмента (онбординг-статус системы). |
| `externalStatus` | `String` | Биржевой статус инструмента (сырой). Источник — OKX `state` из снапшота. Не путать с онбординг-`status`. |
| `marginMode` | `MarginMode` | Режим маржи (нормализованный enum). |
| `externalMarginMode` | `String` | Сырой режим маржи биржи (`cross`/`isolated`). |
| `leverage` | `Integer` | Рабочее плечо инструмента; **задаётся при создании инструмента**, не из снапшота. |
| `externalLeverage` | `String` | Биржевое значение плеча (сырое). Источник — OKX `lever` из снапшота. |
| `plannedCandleStartDate` | `Long` | Плановая нижняя граница истории свечей (UTC, мс): до неё пагинирует `BACKFILL` свечей. Конфигурируемый горизонт, **общий для всех таймфреймов** инструмента (не на группу); дефолт — из конфига (на `CODE`). См. `docs/models/domain/other/CandleGroup.md`, `docs/lifecycles/CandleGroup.md`. |
| `candleGroups` | `List<CandleGroup>` | Группы свечей по таймфреймам инструмента (1:many, без промежуточного объекта; см. `docs/models/domain/other/CandleGroup.md`). |

Поля аудита (`createdAt`/`modifiedAt`/`externalCreatedAt`/… ) —
из `Auditable`.

## Енумы

### `Status`

`CREATED`, `HOLD`, `SYNC`, `CANDLES_LOADING`, `ACTIVE`, `CLOSED`,
`ERROR`. Статус жизненного цикла инструмента в системе (готовность
к торговле: от создания и синхронизации спецификации к загрузке
свечей и `ACTIVE`). Онбординг-путь
(`CREATED → SYNC → CANDLES_LOADING → ACTIVE`), триггеры и
координация с готовностью групп свечей — `docs/lifecycles/Instrument.md`;
периферийные статусы (`HOLD`, `ERROR`-recovery, повторный
онбординг) для шага 1 отложены (backlog п.9).

### `MarginMode`

`ISOLATED`, `CROSS`. Нормализованный режим маржи; сырой биржевой
режим хранится отдельно в `externalMarginMode` (String).

## Биржевое воплощение и справочные поля

`Instrument` несёт идентичность, торговую конфигурацию (`externalId`,
`externalType`, `marginMode`, `externalMarginMode`, `leverage`),
биржевые поля из снапшота (`externalStatus`, `externalLeverage`) и
плановый горизонт свечей (`plannedCandleStartDate`). Биржевые
`externalStatus` (OKX `state`) и `externalLeverage` (OKX `lever`)
приходят в граничном `InstrumentExternalSnapshot` (класс
`domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot`)
и **персистятся** на домен; рабочее `leverage` (`Integer`) задаётся
при создании инструмента, не из снапшота. Справочные поля
спецификации биржи (base/quote/settle currency, `lotSz`, `minSz`,
`ctVal`, `ctMult`, `tickSz`) на доменном `Instrument` **не
хранятся** — они приходят в `InstrumentExternalSnapshot`
**транзиентно** и в шаге 1 персистентного дома не имеют. Mapping
snapshot↔domain — `docs/models/mapping/Instrument.md` (для шага 1 =
идентичность + `externalStatus` + `externalLeverage`).

> **Разграничение `Instrument` ↔ `InstrumentExternalSnapshot` ↔
> `InstrumentExternalRules` (шаг 1).** Биржевые `externalStatus`
> (OKX `state`) и `externalLeverage` (OKX `lever`) приходят в
> транзиентном `InstrumentExternalSnapshot` и **персистятся** на
> `Instrument`. Справочные поля спецификации (base/quote/settle,
> sizes) для шага 1 живут только в транзиентном
> `InstrumentExternalSnapshot`; персистентного дома у них нет.
> Модель `InstrumentExternalRules` (sizing/rounding-правила:
> tick/lot/min sizes, max-order sizes, `ctVal`, max leverage,
> торгуемость) **отложена** за пределы шага 1 (округление/sizing/
> риск — поздние шаги; backlog п.9) и на base/quote/settle больше
> не претендует (`docs/models/domain/other/InstrumentExternalRules.md`).
> Как снапшот-концепция ляжет на `InstrumentExternalRules` и не
> потребуется ли ренейм — открытый вопрос INSTR-Q1; как биржевые
> `externalStatus`/`externalLeverage` на `Instrument` соотносятся с
> rules-полями (`externalState`/`externalMaxLeverage`/`Status`),
> роль `externalLeverage` как биржевого потолка плеча и валидация
> рабочего `leverage` (нарушение → `HOLD`) — открытый вопрос
> INSTR-Q2 (`.claude/work/questions/open-questions.md`).

## Персистентность

Хранится в БД (entity `InstrumentEntity`, таблица `instruments`),
наследует audit-поля. Ограничения схемы:

- `id` — identity (autoincrement).
- Уникальность: `internal_id` (uk_instrument_internal_id);
  пара `(exchange_id, external_id)`
  (uk_instrument_exchange_id_external_id).
- `internal_id`, `exchange_id`, `external_id`, `external_type`,
  `status`, `margin_mode`, `leverage` — `NOT NULL`;
  `external_margin_mode`, `planned_candle_start_date`,
  `external_status`, `external_leverage` — nullable
  (`planned_candle_start_date` проставляется при онбординге из
  конфига; `external_status`/`external_leverage` — при синхронизации
  спецификации, переход `SYNC`).
- `internal_id`, `exchange_id`, `margin_mode` — `updatable = false`
  (неизменны после создания).
- `candleGroups` — `OneToMany` (mappedBy `instrument`), cascade
  `ALL`, `orphanRemoval = true`.

## Связи

- Биржа-владелец — `docs/models/domain/core/Exchange.md`.
- Группы свечей — `docs/models/domain/other/CandleGroup.md`.
- Lifecycle онбординга — `docs/lifecycles/Instrument.md`.
- Mapping snapshot↔domain — `docs/models/mapping/Instrument.md`.
- Торговые правила инструмента (отложены за пределы шага 1) —
  `docs/models/domain/other/InstrumentExternalRules.md`.
- Валидация рабочего `leverage` и роль `externalLeverage` как
  биржевого потолка (отложено за пределы шага 1) — открытый вопрос
  INSTR-Q2 (`.claude/work/questions/open-questions.md`).
- Audit-база — `docs/models/domain/other/Auditable.md`.
