# OKX market price data mapping

## На какой вопрос отвечает этот файл

Как OKX ticker маппится в `MarketPriceDataExternalSnapshot` /
`MarketPriceData`.

## Контекст

Exchange-specific mapping для OKX. RVO — в
`docs/components/models/MarketPriceData.md`, эта дока её не заменяет.
Раздачей `MarketPriceData` занимается
`docs/components/MarketPriceDataService.md`.

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

## Замечания

Историю тикеров не ведём; кэш на первом этапе не используем (см.
`docs/components/models/MarketPriceData.md`).
