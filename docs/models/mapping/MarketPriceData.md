# MarketPriceData — mapping между слоями

## На какой вопрос отвечает этот файл

Как runtime-цены переходят между слоями.

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

### `TickerOkxResponse` → snapshot

| OKX field | Snapshot field |
|---|---|
| `instType` | `externalInstrumentType` |
| `instId` | `externalInstrumentId` |
| `last` | `externalLastPrice` |
| `askPx` | `externalAskPrice` |
| `bidPx` | `externalBidPrice` |
| `ts` | `externalTimestamp` |
| `askSz` | `externalAskSize` |
| `bidSz` | `externalBidSize` |

**`askSz`/`bidSz` вводятся шагом 7**:
глубина топа стакана — **измеритель ёмкости инструмента**, фиксируемый в
момент постановки ноги. Он ничего не блокирует: по его распределению после
первого периода живой торговли назначается (или отклоняется) ёмкостный
потолок — тем же приёмом, что уже применён к
`Order.liquidationDistanceRatio`. Довод и условие пересмотра —
`docs/rules/risk-policy.md`.

Не маппится: `lastSz`, `open24h`, `high24h`/`low24h`,
`vol24h`/`volCcy24h`, `sodUtc0`/`sodUtc8` (24h-агрегаты и SOD-метрики —
доменно не используются).
