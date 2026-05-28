# OKX market price data mapping

## На какой вопрос отвечает этот файл

Как OKX ticker маппится в `MarketPriceDataExternalSnapshot` /
`MarketPriceData`.

## Контекст

Exchange-specific mapping для OKX. RVO — в
`docs/components/models/MarketPriceData.md`, эта дока её не заменяет.
Раздачей `MarketPriceData` занимается
`docs/components/MarketPriceDataService.md`.

## Endpoint

- **Snapshot тикера:** `GET /api/v5/market/ticker`. Permission: Public
  (auth не нужен). Rate limit: 20 req / 2 s по IP + Instrument ID.
  Query: `instId` обязателен (`ETH-USDT-SWAP`).

WS-альтернатива: public канал `tickers` (URL — `/ws/v5/public`),
основной runtime-источник; REST — fallback при отсутствии WS / при
старте до подъёма WS.

## Маппинг

OKX ticker (client-модель) → `MarketPriceDataExternalSnapshot` →
`MarketPriceData`. Сырой OKX DTO за `ClientService` не выходит
(`docs/rules/raw-exchange-dto-boundary.md`).

Поля snapshot: `externalInstrumentType`, `externalInstrumentId`,
`externalLastPrice` (last), `externalAskPrice` (best ask),
`externalBidPrice` (best bid), `externalTimestamp` (время тикера).
Числовые цены → `BigDecimal`. Внутренний `instrumentId` добавляется уже
при сборке `MarketPriceData`.

`MID_PRICE` биржей не передаётся и не хранится — вычисляется доменно:
`(externalBidPrice + externalAskPrice) / 2`.

## Поля response (по архивному источнику)

| OKX field | Назначение | Snapshot field |
|---|---|---|
| `instType` | тип инструмента | `externalInstrumentType` |
| `instId` | имя инструмента | `externalInstrumentId` |
| `last` | последняя цена сделки | `externalLastPrice` |
| `askPx` | лучший ask | `externalAskPrice` |
| `bidPx` | лучший bid | `externalBidPrice` |
| `ts` | время тикера (ms) | `externalTimestamp` |

Поля, не маппимые: `lastSz`, `askSz`/`bidSz` (глубина стакана),
`open24h`, `high24h`/`low24h`, `vol24h`/`volCcy24h`, `sodUtc0`/`sodUtc8`
— 24h-агрегаты и SOD-метрики, доменно не используются.

## Замечания

Историю тикеров не ведём; кэш на первом этапе не используем (см.
`docs/components/models/MarketPriceData.md`).
