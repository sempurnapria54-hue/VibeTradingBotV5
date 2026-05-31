# InstrumentExternalRules

## На какой вопрос отвечает этот файл

Что это за модель `InstrumentExternalRules`: структура, енумы,
персистентность, как обновляется.

## Назначение

`InstrumentExternalRules` — актуальные внешние правила инструмента,
полученные от биржи и сохранённые в БД как persisted snapshot. Не про
бизнес-цикл сделки, а хранимые справочные правила инструмента → модель
`other` (см. `.claude/decisions/models-core-vs-other.md`).

> **Отложено за пределы шага 1.** Модель нужна поздним шагам
> (округление цены/размера, sizing в контрактах, риск-преконтроль,
> проверка торгуемости инструмента) — backlog п.9; в шаге 1 (поток
> рыночных данных) она не материализуется. Справочные валюты
> инструмента (base/quote/settle) эта модель **не держит** — они
> приходят в транзиентном `InstrumentExternalSnapshot` (см.
> `docs/models/domain/core/Instrument.md`,
> `docs/models/mapping/Instrument.md`). Биржевые `state`/`lever`
> (OKX) с шага 1 также живут на `Instrument`
> (`externalStatus`/`externalLeverage`) и через эту модель не идут;
> соотнесение с rules-полями `externalState`/`externalMaxLeverage`/
> `Status` (в т.ч. сорсинг при материализации rules) и роль
> `externalLeverage` как биржевого потолка плеча — открытый вопрос
> INSTR-Q2. Как снапшот-концепция ляжет на эту модель и не
> потребуется ли ренейм — открытый вопрос INSTR-Q1
> (`.claude/work/questions/open-questions.md`).

Используется для:

- округления цены;
- округления размера;
- расчёта размера в контрактах;
- проверки min/max limits;
- проверки биржевого max leverage;
- проверки, можно ли торговать инструмент (`status`).

Правила меняются редко, поэтому хранение актуального snapshot в БД
оправдано. Обновляется `InstrumentExternalRulesSyncJob` (см.
`docs/components/InstrumentExternalRulesSyncJob.md`). OKX-маппинг —
`docs/models/mapping/InstrumentExternalRules.md`.

## Структура

Java-класс, наследует `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID записи. |
| `instrumentId` | `Long` | Внутренний ID инструмента в системе. |
| `instrumentType` | `InstrumentType` | Нормализованный тип инструмента. |
| `contractType` | `ContractType` | Нормализованный тип контракта. |
| `status` | `Status` | Нормализованный статус инструмента. |
| `externalInstrumentType` | `String` | Сырой тип инструмента биржи. |
| `externalInstrumentId` | `String` | ID инструмента на бирже. |
| `externalContractType` | `String` | Сырой тип контракта. |
| `externalContractValue` | `String` | Стоимость контракта (`ctVal`). |
| `externalContractValueCurrency` | `String` | Валюта стоимости контракта. |
| `externalTickSize` | `String` | Шаг цены (`tickSize`). |
| `externalLotSize` | `String` | Шаг размера (`lotSize`). |
| `externalMinSize` | `String` | Минимальный размер (`minSize`). |
| `externalMaxLimitSize` | `String` | Максимальный размер limit-ордера. |
| `externalMaxMarketSize` | `String` | Максимальный размер market-ордера. |
| `externalMaxTriggerSize` | `String` | Максимальный размер trigger-ордера. |
| `externalMaxStopSize` | `String` | Максимальный размер stop-ордера. |
| `externalMaxLeverage` | `String` | Биржевой максимум плеча. |
| `externalState` | `String` | Сырой статус инструмента биржи. |

`external*`-поля хранят сырые строковые значения биржи; нормализованные
`instrumentType` / `contractType` / `status` — доменные проекции для
runtime-логики.

## Енумы

### `Status`
`LIVE`, `SUSPEND`, `PREOPEN`, `EXPIRED`, `TEST`, `UNKNOWN`.
Нормализованный статус инструмента; источник — `externalState`.
Биржевой `state` в шаге 1 потребляется доменным `Instrument`
(`externalStatus`, сырой); сорсинг `externalState`/`Status` при
материализации rules и соотнесение с `Instrument.externalStatus` —
открытый вопрос INSTR-Q2 (см.
`docs/models/mapping/InstrumentExternalRules.md`).

### `InstrumentType`
`SWAP` (бессрочный своп / perpetual), `FUTURES` (фьючерс с датой
экспирации), `SPOT`, `MARGIN` (для текущего бота не используется),
`OPTION` (для текущего бота не используется), `UNKNOWN` (тип не удалось
нормализовать).

### `ContractType`
`LINEAR`, `INVERSE`, `UNKNOWN` (тип не удалось нормализовать).

## InstrumentExternalRulesExternalSnapshot (boundary)

Выход маппера из client-модели биржи: external-поля модели до записи в
БД (`raw-exchange-dto-boundary.md`). Поля совпадают с `external*`-полями
модели (`externalInstrumentType` … `externalState`), без доменных
enum-полей `instrumentType`/`contractType`/`status` — они резолвятся уже
при материализации `InstrumentExternalRules`. Сырой OKX DTO за
`IntegrationService` не выходит; конкретный OKX-маппинг —
`docs/models/mapping/InstrumentExternalRules.md`.

## Персистентность

Хранится в БД как актуальный snapshot правил инструмента (один актуальный
набор на инструмент). Наследует `Auditable`. Обновляется только через
`InstrumentExternalRulesSyncJob` по REST (на первом этапе без WebSocket).
