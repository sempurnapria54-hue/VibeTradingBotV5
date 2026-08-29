# CandleOkxResponse (OKX candle)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели свечи источника.

## Формат провода

На проводе свеча OKX — **позиционный массив из 9 элементов** (не
объект): `[ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]`.
Adapter разбирает массив в именованные поля coded DTO
`CandleOkxResponse`. Размер строго 9 — adapter валидирует длину.

## Поля DTO

| Поле DTO | Индекс в массиве | Тип (raw) | Используется | Назначение |
|---|---|---|---|---|
| `ts` | 0 | string (epoch millis) | да | Время **открытия** свечи → `Candle.openTimestamp`. |
| `open` | 1 | string (decimal) | да | → `Candle.open`. |
| `high` | 2 | string (decimal) | да | → `Candle.high`. |
| `low` | 3 | string (decimal) | да | → `Candle.low`. |
| `close` | 4 | string (decimal) | да | → `Candle.close`. |
| `volume` | 5 | string (decimal) | да | → `Candle.volume`. |
| `volumeCurrency` | 6 | string (decimal) | нет | Объём в базовой валюте (SPOT); доменно не хранится. |
| `volumeCurrencyQuote` | 7 | string (decimal) | нет | Объём в котируемой валюте; доменно не хранится. |
| `confirm` | 8 | string (`0`/`1`) | да (фильтр) | Признак закрытия: `0` — не закрыта, `1` — закрыта. |

Числа OKX приходят строками; в домене — `BigDecimal`, `ts` — epoch
millis.

## `confirm` — фильтр, не поле домена

`confirm` в доменную `Candle` не пишется: только закрытые
(`confirm=1`) свечи сохраняются (правило производящей стороны, см.
`docs/models/domain/other/Candle.md` закрытых свечей,
`docs/models/mapping/Candle.md`). В `market/candles` первая свеча
часто неполная (`confirm=0`); `market/history-candles` обычно
отдаёт уже закрытые.
