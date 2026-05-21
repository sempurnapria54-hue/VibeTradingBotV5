# OKX API для торгового бота — операции, каналы (REST/WS), лимиты, примеры

> **Фокус документа:** операции, которые нужны торговому боту (баланс/позиции/свечи/ордера/TP&SL), и что лучше делать
> через **REST**, а что — через **WebSocket**.

---

## 1) Таблица операций

**Обозначения:**

- **REST base**: `https://www.okx.com` (может отличаться по региону/окружению — см. секцию про сервис-URL).
- **WS**:
    - Public WS: `.../ws/v5/public`
    - Private WS: `.../ws/v5/private` (нужен login)
    - Business WS: `.../ws/v5/business` (часть приватных каналов, напр. algo)

> В таблице намеренно указаны *минимально достаточные* операции. Если захочешь — можно расширить (fills, funding, mark
> price, order book и т.д.).

| Операция                                      | Канал                 | Адрес (endpoint / WS channel)                                       | Auth    | Для чего в боте                                                            | Примечания                                                                      | Описание запроса                                                                                         | Доменная модель                                                           |
|-----------------------------------------------|-----------------------|---------------------------------------------------------------------|---------|----------------------------------------------------------------------------|---------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| Получить баланс (snapshot)                    | REST                  | `GET /api/v5/account/balanceExternalSnapshot?ccy=USDT`              | Private | стартовая синхронизация окружения (деньги), контроль риска                 | Можно брать только нужные валюты через `ccy`                                    | [Получить баланс](Получить%20баланс%20REST.md)                                                               | [Маппинг в доменную модель](../../deprecated/models/domain/old/Balance.md)           |
| Подписка на баланс                            | WS (private)          | `channel=account` или `channel=balance_and_position`                | Private | реактивные обновления equity/маржи                                         | `balance_and_position` удобно, если хочешь одним стримом и баланс, и позиции    |
| Получить позиции (snapshot)                   | REST                  | `GET /api/v5/account/positions?instId=ETH-USDT-SWAP`                | Private | стартовая синхронизация окружения (позиции), восстановление после рестарта | Для проекта: “не больше 1 позиции на инструмент” — это основной источник правды | [Получить позиции](Получить%20позиции%20REST.md)                                                             | [Маппинг в доменную модель](../../deprecated/models/domain/old/Position.md)          |
| Подписка на позиции                           | WS (private)          | `channel=positions` или `channel=balance_and_position`              | Private | обновления позиции в реальном времени                                      | Часто удобнее для триггеров “появилась позиция”/“закрылась позиция”             |
| Получить открытые ордера (snapshot)           | REST                  | `GET /api/v5/trade/orders-pending?instId=ETH-USDT-SWAP`             | Private | синхронизация ордеров после рестарта                                       | Чтобы понимать “что уже выставлено”                                             | [Получить открытые ордера](Получить%20открытые%20ордера%20REST.md)                                             | [Маппинг в доменную модель](../../deprecated/models/domain/old/Order.md)             |
| Подписка на ордера                            | WS (private)          | `channel=orders`                                                    | Private | стрим статусов (live/partially_filled/filled/canceled)                     | Это основной стрим для управления ордерами/фактами исполнения                   |
| Получить детали ордера                        | REST                  | `GET /api/v5/trade/order?instId=...&ordId=...`                      | Private | точечная проверка “что сейчас с ордером”                                   | Полезно при спорных ситуациях/ретраях                                           | [Получить детали ордера](Получить%20детали%20ордера%20REST.md)                                                 | [Маппинг в доменную модель](../../deprecated/models/domain/old/Order.md)             |
| Получить историю ордеров (последние 7 дней)   | REST                  | `GET /api/v5/trade/orders-history`                                  | Private | восстановление после даунтайма: понять финальный статус ордеров            | Важно: ордера “canceled without fills” не появятся в fills                      | [Получить историю ордеров за последние 7 дней](Получить%20историю%20ордеров%20за%20последние%207%20дней%20REST.md)     | [Маппинг в доменную модель](../../deprecated/models/domain/old/Order.md)             |
| Получить историю ордеров (последние 3 месяца) | REST                  | `GET /api/v5/trade/orders-history-archive`                          | Private | более глубокая история ордеров                                             | Для разбора инцидентов/аудита                                                   | [Получить историю ордеров за последние 3 месяца](Получить%20историю%20ордеров%20за%20последние%203%20месяца%20REST.md) | [Маппинг в доменную модель](../../deprecated/models/domain/old/Order.md)             |
| Получить незавершённые algo-ордера            | REST                  | `GET /api/v5/trade/orders-algo-pending`                             | Private | возвращает список не сработавших (untriggered) algo‑ордеров.               | Чтобы понимать “что уже выставлено”                                             | [Получить незавершённые algo-ордера](Получить%20незавершённые%20algo-ордера%20REST.md)                         | [Маппинг в доменную модель](../../deprecated/models/domain/old/AlgoOrder.md)         |
| Получить детали algo-ордера                   | REST                  | `GET /api/v5/trade/order-algo`                                      | Private | точечная проверка “что сейчас с algo‑ордером”                              | Полезно при спорных ситуациях/ретраях                                           | [Получить детали algo-ордера](Получить%20детали%20algo‑ордера%20REST.md)                                       | [Маппинг в доменную модель](../../deprecated/models/domain/old/AlgoOrder.md)         |
| Получить историю algo-ордеров                 | REST                  | `GET /api/v5/trade/orders-algo-history`                             | Private | восстановление после даунтайма: понять финальный статус algo‑ордеров       | Полезно при спорных ситуациях/ретраях                                           | [Получить историю algo-ордеров](Получить%20историю%20algo-ордеров%20REST.md)                                   | [Маппинг в доменную модель](../../deprecated/models/domain/old/AlgoOrder.md)         |
| Получить сделки (fills, последние 3 дня)      | REST                  | `GET /api/v5/trade/fills`                                           | Private | источник правды по факту исполнения (что реально произошло)                | Удобно после рестарта/обрыва WS                                                 | [Получить сделки за последние 3 дня](Получить%20сделки%20за%20последние%203%20дня%20REST.md)                         | [Маппинг в доменную модель](../../deprecated/models/domain/old/TradeFill.md)         |
| Получить сделки (fills, последние 3 месяца)   | REST                  | `GET /api/v5/trade/fills-history`                                   | Private | история сделок глубже 3 дней                                               | Фильтруй по instId/instType, пагинация billId                                   | [Получить сделки за последние 3 месяца](Получить%20сделки%20за%20последние%203%20месяца%20REST.md)                   | [Маппинг в доменную модель](../../deprecated/models/domain/old/TradeFill.md)         |
| Архив сделок: запросить генерацию             | REST                  | `POST /api/v5/trade/fills-archive`                                  | Private | редкий кейс: восстановление/аудит глубоко (до 2 лет)                       | Генерирует архив, потом забираем ссылку                                         | [Архив Сделок: Запросить Генерацию](Запрос%20генерации%20файла%20из%20архива%20сделок%20REST.md)                     | [Маппинг в доменную модель](../../deprecated/models/domain/old/TradeFillsArchive.md) |
| Архив сделок: получить ссылку                 | REST                  | `GET /api/v5/trade/fills-archive`                                   | Private | получить fileHref на архив сделок                                          | Возвращает fileHref/state/ts                                                    | [Архив сделок: получить ссылку](Получить%20ссылку%20на%20файл%20из%20архива%20сделок%20REST.md)                        | [Маппинг в доменную модель](../../deprecated/models/domain/old/TradeFillsArchive.md) |
| Получить свечи (последние)                    | REST                  | `GET /api/v5/market/candles?instId=...&bar=1m`                      | Public  | фоллбэк или периодический снапшот                                          | Возвращает до 1440 последних свечей (ограничение endpoint)                      | [Получить последние свечи](Получить%20последние%20свечи%20REST.md)                                             | [Маппинг в доменную модель](../../deprecated/models/domain/old/Candle.md)            |
| Получить свечи (история)                      | REST                  | `GET /api/v5/market/history-candles?instId=...&bar=1m&after=...`    | Public  | закачка истории, догрузка дырок                                            | Именно это обычно нужно для ETL истории                                         | [Получить историю свечей](Получить%20историю%20свечей%20REST.md)                                               | [Маппинг в доменную модель](../../deprecated/models/domain/old/Candle.md)            |
| Подписка на свечи                             | WS (public)           | `channel=candle1m` (и др. таймфреймы)                               | Public  | low-latency свечи для сигналов/трейлинга                                   | Для “live” логики лучше WS, REST — как запасной                                 |
| Создать ордер                                 | REST                  | `POST /api/v5/trade/order`                                          | Private | выставить entry/exit                                                       | Для SWAP размер `sz` — **в контрактах**                                         | [Создать ордер](Создать%20ордер%20REST.md)                                                                   | [Маппинг в доменную модель](../../deprecated/models/domain/old/Order.md)             |
| Создать ордер                                 | WS (private)          | `op=order`                                                          | Private | то же, но через WS                                                         | Обычно REST проще отлаживать, WS — быстрее/меньше RTT                           |
| Обновить (amend) ордер                        | REST                  | `POST /api/v5/trade/amend-order`                                    | Private | изменить цену/размер/параметры                                             | Делай через `ordId` или `clOrdId`                                               | [Обновить ордер](Обновить%20ордер%20REST.md)                                                                 | [Маппинг в доменную модель](../../deprecated/models/domain/old/Order.md)             |
| Отменить ордер                                | REST                  | `POST /api/v5/trade/cancel-order`                                   | Private | снять висящий ордер                                                        | Нужен для “CancelUnknownOrders=true” политики                                   | [Отменить ордер](Отменить%20ордер%20REST.md)                                                                 | [Маппинг в доменную модель](../../deprecated/models/domain/old/Order.md)             |
| TP/SL/Trailing: создать алгоритмический ордер | REST                  | `POST /api/v5/trade/order-algo`                                     | Private | выставить TP/SL, триггер, trailing stop                                    | Для трейлинга используют `ordType=move_order_stop`                              | [Создать algo ордер](Создать%20algo%20ордер%20REST.md)                                                         | [Маппинг в доменную модель](../../deprecated/models/domain/old/AlgoOrder.md)         |
| TP/SL/Trailing: отменить algo                 | REST                  | `POST /api/v5/trade/cancel-algos`                                   | Private | снять TP/SL/trailing                                                       | Важная операция при ручном выходе/реверсе                                       | [Отменить algo ордер](Отменить%20algo%20ордер%20REST.md)                                                       | [Маппинг в доменную модель](../../deprecated/models/domain/old/AlgoOrder.md)         |
| TP/SL/Trailing: подписка на algo-ордера       | WS (business/private) | `channel=algo-orders` / `algo-advance`                              | Private | видеть изменения TP/SL/Trailing в реальном времени                         | Зависит от типа каналов/версии                                                  |
| Закрыть позицию (маркетом)                    | REST                  | `POST /api/v5/trade/close-position`                                 | Private | аварийный выход / “закрыть всё по инструменту”                             | Удобно как safety-кнопка                                                        | [Закрыть позицию по рынку](Закрыть%20позицию%20по%20рынку%20REST.md)                                             | [Маппинг в доменную модель](../../deprecated/models/domain/old/Position.md)          |
| Получить спецификацию инструмента             | REST                  | `GET /api/v5/public/instruments?instType=SWAP&instId=ETH-USDT-SWAP` | Public  | конвертация USDT→контракты, lotSz/minSz                                    | Нужно для корректного расчёта `sz`                                              | [Получить спецификацию инструмента](Получить%20спецификацию%20инструмента%20REST.md)                           | [Маппинг в доменную модель](../../deprecated/models/domain/old/Instrument.md)        |
| Получить цену (тикер)                         | REST                  | `GET /api/v5/market/ticker`                                         | Public  | расчёт размера/контрактов и трейлинг                                       | Fallback, если WS нет/не подключён                                              | [Получить цену тикер](Получить%20цену%20тикер%20REST.md)                                                       | [Маппинг в доменную модель](../../deprecated/models/domain/old/PriceTicker.md)       |
| Получить цену (тикер)                         | WS (public)           | `channel=tickers`                                                   | Public  | расчёт размера/контрактов и трейлинг                                       | Основной realtime‑канал                                                         |

---

## 2) Лимиты по каналам общения

### WebSocket (WS)

- **Connection limit:** 3 подключения в секунду (по IP).
- **Request limit (per connection):** суммарно `subscribe/unsubscribe/login` ≤ **480 раз в час** на соединение.
- **Timeout / keep-alive:** если подписка не установлена или нет пуша > 30 секунд — соединение может разорваться;
  рекомендуется `ping` и ждать `pong`.
- **Connection count limit (per sub-account, per channel):** до **30 WS-коннектов** на *один и тот же* канал из списка (
  orders/account/positions/balanceExternalSnapshot+positions и др.).  
  При превышении, обычно “последняя” подписка будет отклонена, возможен `channel-conn-count-error`.  
  Важно: **операции ордеров через WS (place/amend/cancel) этим лимитом не затронуты**.

**Service URLs (пример из документации):**

- Production (EEA): REST `https://eea.okx.com`; WS public `wss://wseea.okx.com:8443/ws/v5/public`; WS private
  `wss://wseea.okx.com:8443/ws/v5/private`; WS business `wss://wseea.okx.com:8443/ws/v5/business`.
- Demo: WS public/private/business — `wss://wseeapap.okx.com:8443/...` (и REST `https://eea.okx.com`).

> На практике многие используют `https://www.okx.com` и `wss://ws.okx.com:8443/ws/v5/...`, но лучше выбирать URL из
> раздела “Services” в документации под свой регион.

### REST

У REST лимиты **заданы по каждому endpoint**. Примеры важных для бота:

- `GET /market/candles` — 40 req / 2s (по IP)
- `GET /market/history-candles` — 20 req / 2s (по IP)
- `GET /account/balanceExternalSnapshot` — 10 req / 2s (по User ID)
- `GET /account/positions` — 10 req / 2s (по User ID)
- `POST /trade/order-algo` — 20 orders / 2s (по User ID + Instrument ID)
- `GET /trade/fills` — 60 req / 2s (по User ID)
- `GET /trade/fills-history` — 20 req / 2s (по User ID)
- `GET /trade/orders-history` — 40 req / 2s (по User ID)
- `GET /trade/orders-history-archive` — 20 req / 2s (по User ID)
- `POST /trade/fills-archive` — 1 req / 2s (по User ID)
- `GET /trade/fills-archive` — 5 req / 2s (по User ID)

---

## Реализация в коде (Stage 02)

- Низкоуровневый OKX клиент и подпись: `com.example.tradingbot.client.okx`.
- DTO клиента OKX: `com.example.tradingbot.client.okx.dto`.
- Доменные модели прокси: `com.example.tradingbot.domain.model.okxproxy`.
- Client services (domain in/out): `com.example.tradingbot.domain.service.okxproxy`.
- Прикладные proxy services: `com.example.tradingbot.domain.service`.
- REST модели: `com.example.tradingbot.rest.model.okxproxy`.
- MapStruct маппинг: `com.example.tradingbot.mapping.okxproxy`.
- REST контроллеры proxy: `com.example.tradingbot.rest.controller.okxproxy`.
- Единая обработка ошибок: `com.example.tradingbot.rest.error`.
