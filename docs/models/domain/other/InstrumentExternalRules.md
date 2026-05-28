# InstrumentExternalRules

## На какой вопрос отвечает этот файл

Что это за модель `InstrumentExternalRules`: структура, енумы,
персистентность, как обновляется.

## Назначение

`InstrumentExternalRules` — актуальные внешние правила инструмента,
полученные от биржи и сохранённые в БД как persisted snapshot. Не про
бизнес-цикл сделки, а хранимые справочные правила инструмента → модель
`other` (см. `.claude/decisions/models-core-vs-other.md`).

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
| `externalBaseCurrency` | `String` | Базовая валюта. |
| `externalQuoteCurrency` | `String` | Котируемая валюта. |
| `externalSettleCurrency` | `String` | Валюта расчётов. |
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
Нормализованный статус инструмента; источник — `externalState`. Маппинг
сырого статуса OKX — `docs/models/mapping/InstrumentExternalRules.md`.

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
`ClientService` не выходит; конкретный OKX-маппинг —
`docs/models/mapping/InstrumentExternalRules.md`.

## Персистентность

Хранится в БД как актуальный snapshot правил инструмента (один актуальный
набор на инструмент). Наследует `Auditable`. Обновляется только через
`InstrumentExternalRulesSyncJob` по REST (на первом этапе без WebSocket).
