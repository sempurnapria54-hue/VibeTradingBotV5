# MarketPriceDataService

## На какой вопрос отвечает этот файл

Кто отдаёт runtime-цены инструмента.

## Назначение

`MarketPriceDataService` отдаёт `MarketPriceData` (см.
`docs/components/models/MarketPriceData.md`) — runtime-данные текущих цен
инструмента (last/bid/ask + время тикера). Не persisted, историю тикеров
не ведёт, кэш на первом этапе не использует.

## Поведение

`MarketPriceData` не считается заранее: нужен прямо перед расчётом
параметров действия. Flow: client-модель OKX ticker →
`MarketPriceDataExternalSnapshot` → `MarketPriceData` (OKX-маппинг —
`docs/models/mapping/MarketPriceData.md`).

В рамках одного `CalculationContext` `MarketPriceData` получается один
раз и переиспользуется, чтобы не плодить REST-запросы.
