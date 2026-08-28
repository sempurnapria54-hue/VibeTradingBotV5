# OkxTickerResponse (OKX market ticker)

## На какой вопрос отвечает этот файл

Какие поля у OKX ticker response — что приходит от биржи и что из
этого используется.

## Контекст

Нативная модель источника OKX (Java `OkxTickerResponse`).
Возвращается `GET /api/v5/market/ticker`. Не выходит за
`IntegrationService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

`getTicker` (сырой) сейчас потребляется **A2 raw-passthrough**
контура тестов (`OkxProxyController`). Mapping в
`MarketPriceDataExternalSnapshot` и далее в `MarketPriceData` —
forward-дизайн шага 5 (`docs/models/mapping/MarketPriceData.md`,
раздел `## OKX`); **код маппинга/снапшота снят** под §«Неиспользуемый
код» при ре-базе контура на сырьё
(`.claude/decisions/source-api-target-rebase.md`), вернётся со сборкой
рыночных данных на шаге 5. Контракт endpoint'а / rate limit —
`docs/integrations/okx/contracts/market-price-data.md`.

> Раздача текущей цены (`MarketPriceDataService`) и тикер-фетч —
> зона FSM/потребителей более поздних шагов, вне кода шага 1.
> Здесь — инвентарь полей DTO.

## Поля DTO

| OKX field | Тип (raw) | Используется | Назначение |
|---|---|---|---|
| `instType` | string | нет | Тип инструмента; в coded DTO есть, в snapshot не маппится. |
| `instId` | string | да | Имя инструмента (`ETH-USDT-SWAP`). |
| `last` | string (decimal) | да | Last traded price. |
| `askPx` | string (decimal) | да | Best ask. |
| `bidPx` | string (decimal) | да | Best bid. |
| `askSz` | string (decimal) | да | Объём на лучшем ask — **вводится шагом 7** (P10 `DOCS_CHECK_24`): операнд измерителя ёмкости `Order.bookDepthAtPlacement`. |
| `bidSz` | string (decimal) | да | Объём на лучшем bid. Там же. |
| `ts` | string (epoch millis) | да | Время тикера. |

Таблица выровнена под **худой coded DTO** (`OkxTickerResponse.java`:
`instType`/`instId`/`last`/`askPx`/`bidPx`/`ts`; шаг 7 добавляет
`askSz`/`bidSz`) — decision о ре-базе
контура на сырьё (`.claude/decisions/source-api-target-rebase.md`,
§«doc-sync»): держим только заведённые поля, карваута на полное зеркало
биржи нет.

Числа OKX приходят строками; обязательные числовые строки парсятся
в `BigDecimal`. `MID_PRICE` источником не передаётся — вычисляется
доменно как `(bidPx + askPx) / 2` (см.
`docs/models/mapping/MarketPriceData.md`).

## Поля, которые НЕ входят в DTO

OKX `market/ticker` отдаёт больше полей, чем содержит coded DTO:
`lastSz`, `open24h`, `high24h`/`low24h`, `vol24h`/`volCcy24h`,
`sodUtc0`/`sodUtc8` (24h-агрегаты и SOD-метрики). Доменно не
используются и в DTO не заведены. **`askSz`/`bidSz` из этого перечня
выведены шагом 7** — они переехали в таблицу используемых (P10
`DOCS_CHECK_24`).

`markPx`/`idxPx` ранее значились в таблице полей DTO — в coded DTO их
**нет** (перенесены сюда при doc-sync). Возвращает ли `market/ticker`
их вообще — **требует офдок-сверки** (`integrator`): mark/index price
отдаются отдельными эндпоинтами (`public/mark-price`,
`market/index-tickers`). До сверки как факт ticker'а не утверждается.
