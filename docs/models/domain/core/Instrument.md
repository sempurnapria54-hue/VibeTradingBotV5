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
| `externalSettlementCurrency` | `String` | **Расчётная валюта** инструмента (OKX `settleCcy`). Операнд ветки cross-ccy на записи `DealCashFlow`, источник `Deal.plannedRiskCurrency` и авторитет `Deal.resultProfitCurrency` (см. §«Валюты инструмента»). |
| `externalBaseCurrency` | `String` | Базовая валюта инструмента (OKX `baseCcy`). |
| `externalQuoteCurrency` | `String` | Котировочная валюта инструмента (OKX `quoteCcy`). |
| `marginMode` | `MarginMode` | Режим маржи (нормализованный enum). |
| `externalMarginMode` | `String` | Сырой режим маржи биржи (`cross`/`isolated`). |
| `leverage` | `Integer` | Рабочее плечо инструмента; **задаётся при создании инструмента**, не из снапшота. |
| `externalLeverage` | `String` | Биржевое значение плеча (сырое). Источник — OKX `lever` из снапшота. |
| `plannedCandleStartDate` | `Long` | Плановая нижняя граница истории свечей (UTC, мс): до неё пагинирует `BACKFILL` свечей. Конфигурируемый горизонт, **общий для всех таймфреймов** инструмента (не на группу); дефолт — из конфига (на `CODE`). См. `docs/models/domain/other/CandleGroup.md`, `docs/lifecycles/CandleGroup.md`. |
| `candleGroups` | `List<CandleGroup>` | Группы свечей по таймфреймам инструмента (1:many, без промежуточного объекта; см. `docs/models/domain/other/CandleGroup.md`). |

Поля аудита (`createdAt`/`modifiedAt`/`externalCreatedAt`/… ) —
из `Auditable`.

Наружу (API) инструмент адресуется `internalId`, его биржа —
`exchangeInternalId`; числовые `id`/`exchangeId` — внутренняя деталь
связей и наружу не отдаются (см. `docs/models/api/README.md`).

## Енумы

### `Status`

`CREATED`, `HOLD`, `TRADE_BLOCKED`, `ENTRY_BLOCKED`, `SYNC`,
`CANDLES_LOADING`, `ACTIVE`, `CLOSED`, `ERROR`. Статус жизненного цикла
инструмента в системе (готовность к торговле: от создания и синхронизации
спецификации к загрузке свечей и `ACTIVE`). Онбординг-путь
(`CREATED → SYNC → CANDLES_LOADING → ACTIVE`), триггеры и
координация с готовностью групп свечей — `docs/lifecycles/Instrument.md`;
периферийные статусы (`HOLD`, `ERROR`-recovery, повторный
онбординг) для шага 1 отложены (backlog п.9).

**Три статуса-паузы с разным смыслом — не синонимы** (H3, `GAPS_CLOSE_6`).
Состояние носителей на момент записи: `HOLD` и `TRADE_BLOCKED` **в коде
есть** (инвентарь приведён к коду — прежде перечень нёс 7 членов и
safety-статуса не содержал вовсе); `ENTRY_BLOCKED` — **целевой**, вводится
на `CODE` шага 7 (`.claude/work/backlog.md` §Шаг 7):

| Статус | Смысл | Что делает enforcement |
|---|---|---|
| `HOLD` | **онбординговый**: инструмент придержан, не вовлекается в онбординг | не участвует в синке/загрузке свечей |
| `TRADE_BLOCKED` | **safety-холд с kill-switch** (риск-триггер уровня 3, управление-сайд серия неудач; `docs/rules/instrument-hold.md`) | выпадает из entry-скана **и** активные сделки уводятся в `ERROR` (`shutdownReason = RISK_POLICY`) → teardown live risk |
| `ENTRY_BLOCKED` | **мягкий запрет новых входов** (несвежесть ставки/ключа группы, вход-сайд серия неудач) | выпадает из entry-скана; активные сделки **не трогаются**, ведутся штатным FSM |

**Множества входа — разные у двух классов** (H13, `GAPS_CLOSE_7`):

| Целевой статус | Из каких статусов достижим |
|---|---|
| `ENTRY_BLOCKED` (мягкая блокировка) | **только из `ACTIVE`** |
| `TRADE_BLOCKED` | **из любого статуса** |
| `CLOSED`, `ERROR` | **из любого статуса** |

Снятие обоих safety-статусов — **вручную**, в `ACTIVE`; `ENTRY_BLOCKED`
эскалируется в `TRADE_BLOCKED` при kill-switch-триггере, обратной эскалации
нет (`docs/rules/instrument-hold.md` §Enforcement).

**Почему множества разные.** Мягкая блокировка — про **новые входы**, а
входы и так возможны только из `ACTIVE`: ставить её на онбординговый
статус бессмысленно (входов там нет) и вредно (затёрла бы онбординг —
инструмент вернулся бы из ручного снятия в `ACTIVE` с недогруженными
свечами). `TRADE_BLOCKED` / `CLOSED` / `ERROR` — реакции на аварию и
терминальные состояния: авария может застать инструмент **в любом**
статусе, и ограничение входа сделало бы реакцию **пропускаемой** —
охраняемое обновление вернуло бы «не применено», а координатор счёл бы
реакцию отработанной. Прежняя редакция распространяла «вход только из
`ACTIVE`» на **оба** класса и тем противоречила соседней клаузе об
эскалации `ENTRY_BLOCKED → TRADE_BLOCKED`.

> **Расхождение с кодом (CODE-пункт, `.claude/work/backlog.md` §Шаг 7).**
> `InstrumentDataService.blockTrade` — охраняемый `UPDATE … WHERE
> status = 'ACTIVE'`: из `ENTRY_BLOCKED` он вернёт «не применено», то есть
> эскалация мягкого холда в полный сегодня **не сработает**. Приведение
> кода к решению — на под-шаге `CODE`, не здесь.

**Статус онбординга после ручного снятия жёсткой блокировки** — открытый
вопрос `HOLD-Q2` (`.claude/work/questions/open-questions.md`): снятие
возвращает в `ACTIVE`, а инструмент мог быть заблокирован из
`CANDLES_LOADING`.

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
при создании инструмента, не из снапшота. Размерные поля спецификации
биржи (`lotSz`, `minSz`, `ctVal`, `ctMult`, `tickSz`) на доменном
`Instrument` **не хранятся** — их дом `InstrumentExternalRules` (шаг 5).
**Валюты (base/quote/settle) — хранятся** (с шага 7, §ниже); в шаге 1
они приходили транзиентно и персистентного дома не имели. Mapping
snapshot↔domain — `docs/models/mapping/Instrument.md` (для шага 1 =
идентичность + `externalStatus` + `externalLeverage`).

## Валюты инструмента

**Расчётная, базовая и котировочная валюты — поля `Instrument`**, с
шага 7 (H6 `DOCS_CHECK_11`, решение пользователя —
`docs/decisions/instrument-currencies-home.md`). Источник —
`/public/instruments` (`settleCcy`/`baseCcy`/`quoteCcy`), канал —
`InstrumentExternalSnapshot`, писатель — тот же синк спецификации.

- **Почему на сущности, а не в навесе.** Валюта расчёта — свойство
  самого контракта и меняется редко или не меняется вовсе; навес
  `InstrumentExternalRules` заведён под **волатильную** часть
  спецификации. Побочно снимается третья тропа чтения навеса: все три
  потребителя шага 7 держат `Instrument` через `DealContext`
  (`docs/components/models/DealContext.md`), и резолв у них уже есть.
- **Потребители расчётной валюты** (шаг 7): ветка cross-ccy на записи
  движения (`docs/components/RefreshBillsExecutor.md`), писатель
  `Deal.plannedRiskCurrency` (`docs/components/CreateOrderExecutor.md`),
  финализация — штатная и аварийная
  (`docs/components/FinalizeDealExitExecutor.md`,
  `docs/components/MarkDealEmergencyClosedExecutor.md`).
- **Авторитет валюты результата — эта колонка**, а `ccy` записи
  positions-history — проверяемый признак
  (`docs/models/domain/aggregate/Deal.md` §«Валюта результата: один
  авторитет»).
- **Ветка «операнд пуст».** Колонки новые: на существующих строках
  значение появляется после ближайшего тика синка. Реакции по точкам —
  `docs/models/domain/aggregate/Deal.md` §«Ветка "операнд пуст"».
- **`CCY-Q2` закрыт** этим решением (обе позиции — именование и область
  модели); имена полей больше не предварительные.

> **Разграничение `Instrument` ↔ `InstrumentExternalSnapshot` ↔
> `InstrumentExternalRules` (шаг 1).** Биржевые `externalStatus`
> (OKX `state`) и `externalLeverage` (OKX `lever`) приходят в
> транзиентном `InstrumentExternalSnapshot` и **персистятся** на
> `Instrument`. Размерные поля спецификации (sizes, `ctVal`, тики) для
> шага 1 живут только в транзиентном `InstrumentExternalSnapshot` —
> персистентный дом появляется у них на шаге 5 (`InstrumentExternalRules`);
> **валюты (base/quote/settle) персистятся на `Instrument` с шага 7**
> (§«Валюты инструмента»).
> Модель `InstrumentExternalRules` (sizing/rounding-правила:
> tick/lot/min sizes, max-order sizes, `ctVal`, max leverage,
> торгуемость) **материализуется на шаге 5** (риск-преконтроль) и на
> base/quote/settle не претендует
> (`docs/models/domain/other/InstrumentExternalRules.md`,
> `docs/decisions/instrument-external-rules-materialization.md`,
> закрыт INSTR-Q1). Авторитетный для преконтроля биржевой потолок плеча
> и торгуемость живут в rules (`externalMaxLeverage`/`Status`);
> одноимённые сырые поля на `Instrument` (`externalStatus`/
> `externalLeverage`) несут то же значение, но для преконтроля не
> авторитетны (дубль — мелкая чистка). Нашего кэпа плеча нет (плечо
> связано лимитом риска, `docs/decisions/per-trade-risk-policy.md`),
> поэтому `HOLD` инструмента по нарушению плеча не вводится: единственное
> правило плеча — биржевой максимум (precontrol-блок
> `EXCHANGE_MAX_LEVERAGE_EXCEEDED`).

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
  `external_status`, `external_leverage`,
  `external_settlement_currency`, `external_base_currency`,
  `external_quote_currency` — nullable
  (`planned_candle_start_date` проставляется при онбординге из
  конфига; `external_status`/`external_leverage`/валюты — при
  синхронизации спецификации, переход `SYNC`).
- **Колонки шага 7 — `ALTER`** (H6 `DOCS_CHECK_11`):
  `external_settlement_currency`, `external_base_currency`,
  `external_quote_currency` добавляются миграцией шага 7; бэкфилл не
  нужен (`null` = «до ближайшего тика синка»). Полная schema-дельта
  шага — `docs/decisions/pnl-finalization-mechanics.md` §Следствия.
- `internal_id`, `exchange_id`, `margin_mode` — `updatable = false`
  (неизменны после создания).
- `status` и `margin_mode` хранятся строкой (имя enum); enum — только
  в домене (codestyle: enum'ы — в доменном слое).
- `candleGroups` — `OneToMany` (mappedBy `instrument`), cascade
  `ALL`, `orphanRemoval = true`.

## Связи

- Биржа-владелец — `docs/models/domain/core/Exchange.md`.
- Группы свечей — `docs/models/domain/other/CandleGroup.md`.
- Lifecycle онбординга — `docs/lifecycles/Instrument.md`.
- Mapping snapshot↔domain — `docs/models/mapping/Instrument.md`.
- Торговые правила инструмента (материализуются на шаге 5) —
  `docs/models/domain/other/InstrumentExternalRules.md`,
  `docs/decisions/instrument-external-rules-materialization.md`.
- Роль `externalLeverage`/потолок плеча, кэп плеча — закрыты решениями
  выше + `docs/decisions/per-trade-risk-policy.md`; INSTR-Q2 закрыт на
  шаге 6: рабочее плечо пишется inline в `SubmitOrderExecutor` перед
  постановкой открывающего ордера (`docs/components/SubmitOrderExecutor.md`).
- Audit-база — `docs/models/domain/other/Auditable.md`.
