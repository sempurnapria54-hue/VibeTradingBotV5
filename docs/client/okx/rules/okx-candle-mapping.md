# OKX candle mapping

## На какой вопрос отвечает этот файл

Как OKX-свечи (`market/candles`, `market/history-candles`) маппятся в
доменные свечные данные и какие особенности их формата.

## Контекст

Exchange-specific mapping для OKX. Доменно свечи готовит
`docs/components/CandleJob.md`; потребители — `IndicatorJob` и др.
(`docs/processes/market-data-calculation.md`). Mapping таймфреймов в
строки OKX — `docs/client/okx/rules/okx-timeframe-mapping.md`.

Сырой OKX DTO за `ClientService` не выходит
(`docs/rules/raw-exchange-dto-boundary.md`).

> Альтернатива при размещении: расширить `okx-market-price-data-mapping.md`
> до tickers + candles. Не выбрана, чтобы сохранить «один mapping —
> одна доменная роль» (`MarketPriceData` ≠ исторические OHLC).

## Endpoints

- **Получить последние свечи:** `GET /api/v5/market/candles`. Permission:
  Public (auth не нужен). Rate limit: 40 req / 2 s по IP. Возвращает до
  1440 последних свечей (ограничение endpoint).
- **Получить историю свечей:** `GET /api/v5/market/history-candles`.
  Permission: Public. Rate limit: 20 req / 2 s по IP. Основной endpoint
  для ETL истории и докачки «дырок».

Query (одинаковые для обоих):
- `instId` (обяз.) — `ETH-USDT-SWAP` и т.п.
- `bar` (опц., default `1m`) — таймфрейм. Поддерживаемые: `1m/3m/5m/
  15m/30m/1H/2H/4H` и дневные/недельные/месячные `6H/12H/1D/2D/3D/1W/
  1M/3M` (открытие UTC+8) либо UTC+0-варианты `6Hutc/12Hutc/1Dutc/2Dutc/
  3Dutc/1Wutc/1Mutc/3Mutc`. **Регистр важен** (`1H` ≠ `1h`,
  `1Dutc` ≠ `1DUTC`). Domain `TimeFrame` ↔ OKX bar — см.
  `okx-timeframe-mapping.md`. `history-candles` дополнительно поддерживает
  `1s` (только последние 3 месяца; не для OPTION).
- `after` (опц.) — пагинация: свечи **строго старше** `ts` (ms).
  Основной параметр для выкачки истории назад во времени.
- `before` (опц.) — свечи **строго новее** `ts` (ms). Если передать
  только `before` — биржа вернёт самые последние данные.
- `limit` (опц.) — для `market/candles` максимум 300 (default 100); для
  `history-candles` максимум 100 (default 100).

WS-альтернатива (вне scope) — public канал `candle<bar>` (например
`candle1m`, `candle1H`, `candle1Dutc`); полноценная WS-документация —
отдельный заход (см. open-questions).

## Особенности response

Поле `data[]` — массив свечей; каждая свеча — **массив из 9 элементов**
(не объект), порядок строго фиксирован:

```text
[ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]
```

| Индекс | Поле | Назначение |
|---|---|---|
| 0 | `ts` | время **открытия** свечи (Unix ms) |
| 1 | `o` | open |
| 2 | `h` | high |
| 3 | `l` | low |
| 4 | `c` | close |
| 5 | `vol` | объём (SWAP/FUTURES — обычно контракты; SPOT — базовая валюта) |
| 6 | `volCcy` | объём в котируемой валюте (SPOT) |
| 7 | `volCcyQuote` | объём в котируемой валюте (например USDT) |
| 8 | `confirm` | `0` — свеча не закрыта; `1` — закрыта (финальная) |

Порядок свечей в `data` — обычно **от новых к старым**. Размер массива
должен быть **строго 9** — adapter валидирует длину (защита от
несоответствия формату).

В `market/candles` первая свеча в ответе часто неполная (`confirm=0`);
для индикаторов используются только `confirm=1` свечи
(`docs/components/CandleJob.md` §Правило). В `history-candles` чаще
приходят уже закрытые (`confirm=1`).

## Маппинг

Числовые поля (`o`/`h`/`l`/`c`/`vol`/`volCcy`/`volCcyQuote`) → `BigDecimal`;
`ts` → epoch millis (`Instant`/`OffsetDateTime` в домене); `confirm`
парсится как `boolean` (`"1"` → `true`).

## Пагинация назад

1. Стартовый запрос с `after = now_ms` (или без `after`).
2. Из ответа берём `min(ts)`.
3. Следующий запрос с `after = min(ts)`.
4. Стоп: пустой `data` ИЛИ `min(ts) < coverage_start`.
