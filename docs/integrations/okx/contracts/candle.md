# OKX contracts: candle

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по свечам: endpoint'ы, query, лимиты.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Market Data», секции «GET /
Candlesticks», «GET / Candlesticks history»). При расхождении с
офдоком побеждает офдок; синхронизация — перевыкачка + дифф при
каждом заходе интегратора
(`.claude/processes/api-docs-completion.md` §4a, канал —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(существование/путь по манифесту; поле-уровневая перевычитка — при
заходе по теме).

## Контекст

Mapping в свечи — `docs/models/mapping/Candle.md` (формат
9-элементного массива). Доменно свечи готовит
`docs/components/CandleJob.md`. Mapping таймфреймов —
`docs/models/mapping/TimeFrame.md`.

## Endpoints

- **Получить последние свечи:** `GET /api/v5/market/candles`.
  Permission: Public (auth не нужен). Rate limit: 40 req / 2 s по IP.
  Возвращает до 1440 последних свечей (ограничение endpoint).
- **Получить историю свечей:** `GET /api/v5/market/history-candles`.
  Permission: Public. Rate limit: 20 req / 2 s по IP. Основной
  endpoint для ETL истории и докачки «дырок».

## Query (одинаковые для обоих)

- `instId` (обяз.) — `ETH-USDT-SWAP` и т.п.
- `bar` (опц., default `1m`) — таймфрейм. Поддерживаемые: `1m/3m/5m/
  15m/30m/1H/2H/4H` и дневные/недельные/месячные `6H/12H/1D/2D/3D/1W/
  1M/3M` (открытие UTC+8) либо UTC+0-варианты `6Hutc/12Hutc/1Dutc/
  2Dutc/3Dutc/1Wutc/1Mutc/3Mutc`. **Регистр важен** (`1H` ≠ `1h`,
  `1Dutc` ≠ `1DUTC`). `history-candles` дополнительно поддерживает
  `1s` (только последние 3 месяца; не для OPTION).
- `after` (опц.) — пагинация: свечи **строго старше** `ts` (ms).
  Основной параметр для выкачки истории назад во времени.
- `before` (опц.) — свечи **строго новее** `ts` (ms). Если передать
  только `before` — биржа вернёт самые последние данные.
- `limit` (опц.) — для `market/candles` максимум 300 (default 100);
  для `history-candles` максимум 100 (default 100).

## Пагинация назад

1. Стартовый запрос с `after = now_ms` (или без `after`).
2. Из ответа берём `min(ts)`.
3. Следующий запрос с `after = min(ts)`.
4. Стоп: пустой `data` (начало истории биржи) ИЛИ
   `min(ts) ≤ plannedCandleStartDate` (достигнут плановый горизонт
   инструмента — `docs/models/domain/core/Instrument.md`).

## WS-альтернатива

Public канал `candle<bar>` (например `candle1m`, `candle1H`,
`candle1Dutc`). Полноценная WS-документация — отдельный заход
(OKX-Q4).
