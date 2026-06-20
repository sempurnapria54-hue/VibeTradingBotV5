# MarketPriceData — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменный `MarketPriceData` ложится на нативные модели источников
и нормализуется через `MarketPriceDataExternalSnapshot`.

## Контекст

Mapping-слой для `MarketPriceData`. RVO — `docs/components/models/MarketPriceData.md`.
Раздачей `MarketPriceData` занимается
`docs/components/MarketPriceDataService.md`. Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт endpoint'а —
`docs/integrations/<name>/contracts/market-price-data.md`.

Текущие источники: **OKX** (REST ticker). WS-канал `tickers` —
планируемый realtime-источник, отложен до рефакторинга на
микросервисы (OKX-Q4); до тех пор рантайм — REST.

## Source-agnostic ядро

### Поля snapshot

| Snapshot field | Семантика |
|---|---|
| `externalInstrumentType` | тип инструмента |
| `externalInstrumentId` | имя инструмента |
| `externalLastPrice` | last traded price |
| `externalAskPrice` | best ask |
| `externalBidPrice` | best bid |
| `externalTimestamp` | время тикера (ms) |

Числовые цены → `BigDecimal`. Внутренний `instrumentId` добавляется
при сборке `MarketPriceData`.

`MID_PRICE` источником не передаётся и не хранится — вычисляется
доменно: `(externalBidPrice + externalAskPrice) / 2`.

### Замечания

Историю тикеров не ведём; кэш на первом этапе не используем (см.
`docs/components/models/MarketPriceData.md`).

## OKX

`MarketPriceDataMapper` маппит в два шага: `integrationToSnapshot`
(OKX ticker → snapshot, сырые decimal-строки → `BigDecimal`,
epoch-millis-строка `ts` → `OffsetDateTime` UTC) и
`snapshotToDomain(snapshot, Long instrumentId)` (snapshot + внутренний
ID → доменный `MarketPriceData`).

### `OkxTickerResponse` → snapshot

| OKX field | Snapshot field |
|---|---|
| `instType` | `externalInstrumentType` |
| `instId` | `externalInstrumentId` |
| `last` | `externalLastPrice` |
| `askPx` | `externalAskPrice` |
| `bidPx` | `externalBidPrice` |
| `ts` | `externalTimestamp` |

Не маппится: `lastSz`, `askSz`/`bidSz` (глубина стакана), `open24h`,
`high24h`/`low24h`, `vol24h`/`volCcy24h`, `sodUtc0`/`sodUtc8` (24h-
агрегаты и SOD-метрики — доменно не используются).
