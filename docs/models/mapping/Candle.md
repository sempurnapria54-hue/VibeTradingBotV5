# Candle — mapping между слоями

## На какой вопрос отвечает этот файл

Как нативные представления свечей источников ложатся на доменные
свечные данные и какие особенности их формата.

## Контекст

Mapping-слой для свечей. Доменно свечи добывает
`docs/components/CandleJob.md` (процесс
`docs/processes/candle-loading.md`); потребители — `IndicatorJob`
и др. (`docs/processes/market-data-calculation.md`).
Mapping таймфреймов — `docs/models/mapping/TimeFrame.md`. Сквозные
правила — `docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт endpoint'ов
— `docs/integrations/<name>/contracts/candle.md`.

Текущие источники: **OKX**.

> Альтернатива размещению: расширить `mapping/MarketPriceData.md`
> до tickers + candles. Не выбрана, чтобы сохранить «один файл —
> одна доменная роль» (`MarketPriceData` ≠ исторические OHLC).

## OKX

### Формат свечи

`data[]` — массив свечей; каждая свеча — **массив из 9 элементов**
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

Порядок свечей в `data` — обычно **от новых к старым**. Размер
массива должен быть **строго 9** — adapter валидирует длину (защита
от несоответствия формату).

### Конвертация

Числовые поля (`o`/`h`/`l`/`c`/`vol`/`volCcy`/`volCcyQuote`) →
`BigDecimal`; `ts` → epoch millis (`Instant`/`OffsetDateTime` в
домене); `confirm` парсится как `boolean` (`"1"` → `true`).

### Граница: `CandleExternalSnapshot`

Свеча проходит границу `IntegrationService`/adapter как нормализованный
`CandleExternalSnapshot`, не сырым OKX-массивом — сквозное правило
`docs/rules/raw-exchange-dto-boundary.md`. Путь:

```text
OKX-массив [9] → CandleExternalSnapshot → domain Candle
```

`IntegrationService` валидирует длину массива (строго 9), парсит
элементы и отдаёт `CandleExternalSnapshot` с runtime-useful
полями: `openTimestamp` (`ts`), `open`/`high`/`low`/`close`,
`volume` (`vol`), `confirm`. Validation-only объёмы
`volCcy`/`volCcyQuote` за границу не выходят (в снапшот не входят).
Доменные enum/нормализации в снапшоте не резолвятся.

### → domain `Candle`

Из `CandleExternalSnapshot`: `openTimestamp` →
`Candle.openTimestamp`; `open`/`high`/`low`/`close` → одноимённые;
`volume` → `volume` (домен хранит один объём). `confirm` в домен
**не пишется** — это фильтр закрытых свечей (см. ниже). Доменная
модель — `docs/models/domain/other/Candle.md`; группа и lifecycle
загрузки — `docs/models/domain/other/CandleGroup.md`,
`docs/lifecycles/CandleGroup.md`.

### `confirm` policy

В `/market/candles` первая свеча часто неполная (`confirm=0`); для
индикаторов используются только `confirm=1` свечи
(`docs/components/CandleJob.md` §Правило). В `/market/history-candles`
обычно приходят уже закрытые (`confirm=1`).
