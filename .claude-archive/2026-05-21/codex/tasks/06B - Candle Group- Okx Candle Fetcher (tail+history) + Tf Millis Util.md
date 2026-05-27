# Task 06B — CandleGroup: OKX candle fetcher (tail+history) + tfMillis util

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Сделать единый модуль загрузки свечей с OKX:

* tail (последние N баров)
* history (пагинация назад от cursor)

И утилиту `tfMillis(timeframe)` на базе `OkxTimeframes.*`.

---

## 1) Компоненты

Package: `com.example.tradingbot.domain.service.candles.okx` (или аналогично)

* `OkxCandleFetcher`

    * `List<ClientCandle> fetchTail(String instId, String timeframe, int bars)`
    * `List<ClientCandle> fetchHistoryBackward(String instId, String timeframe, int limit, Long afterTsExclusive)`

Требования:

* timeframe передавать строго как `OkxTimeframes.*`.
* использовать существующий OKX client слой (RestTemplate + подпись) если он уже есть.

---

## 2) Выбор эндпоинтов

* Tail: можно использовать `market/candles` или `market/history-candles` (выбери один и используй консистентно).
* Backfill/repair: `market/history-candles` (как исторический источник).

---

## 3) Парсер

* `OkxCandleDataParser` (если в проекте уже есть CandleDataParser — переиспользовать/расширить)

Валидации:

* размер массива OKX = ожидаемый
* `timestamp` парсится в long
* OHLC/volume парсятся безопасно

---

## 4) Timeframe millis util

Package: `com.example.tradingbot.util`

* `TimeframeMillis`

    * `static long toMillis(String timeframe)`

Реализация:

* строго маппинг по значениям `OkxTimeframes.*`
* без lower-case

---

## 5) Модели

Client-модель свечи допускается в виде DTO:

* `timestampMillis`
* OHLC
* volumes

Не мешать с persistence/entity.

---

## DoD

* Можно получить tail/historical батчи по instId+timeframe.
* `toMillis()` покрывает все TF из `OkxTimeframes.RECOMMENDED_TIMEFRAMES`.
