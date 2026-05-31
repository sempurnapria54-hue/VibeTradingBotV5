# OkxTickerResponse (OKX market ticker)

## На какой вопрос отвечает этот файл

Какие поля у OKX ticker response — что приходит от биржи и что из
этого используется.

## Контекст

Нативная модель источника OKX (Java `PriceTickerResponse`).
Возвращается `GET /api/v5/market/ticker`. Не выходит за
`IntegrationService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

Mapping в `MarketPriceDataExternalSnapshot` и далее в
`MarketPriceData` — `docs/models/mapping/MarketPriceData.md`
(раздел `## OKX`). Контракт endpoint'а / rate limit —
`docs/integrations/okx/contracts/market-price-data.md`.

> Раздача текущей цены (`MarketPriceDataService`) и тикер-фетч —
> зона FSM/потребителей более поздних шагов, вне кода шага 1.
> Здесь — инвентарь полей DTO.

## Поля DTO

| OKX field | Тип (raw) | Используется | Назначение |
|---|---|---|---|
| `instId` | string | да | Имя инструмента (`ETH-USDT-SWAP`). |
| `last` | string (decimal) | да | Last traded price. |
| `askPx` | string (decimal) | да | Best ask. |
| `bidPx` | string (decimal) | да | Best bid. |
| `ts` | string (epoch millis) | да | Время тикера. |
| `markPx` | string (decimal) | нет | Mark price (в snapshot не маппится). |
| `idxPx` | string (decimal) | нет | Index price (в snapshot не маппится). |

Числа OKX приходят строками; обязательные числовые строки парсятся
в `BigDecimal`. `MID_PRICE` источником не передаётся — вычисляется
доменно как `(bidPx + askPx) / 2` (см.
`docs/models/mapping/MarketPriceData.md`).

## Поля, которые НЕ входят в DTO

OKX `market/ticker` отдаёт больше полей, чем содержит coded DTO:
`lastSz`, `askSz`/`bidSz` (глубина стакана), `open24h`,
`high24h`/`low24h`, `vol24h`/`volCcy24h`, `sodUtc0`/`sodUtc8` (24h-
агрегаты и SOD-метрики). Доменно не используются и в DTO не
заведены.
