# Stage 02 — OKX API Proxy (по сводной таблице)

## Цель

Реализовать «прокси слой» для всех методов, перечисленных в `docs/api/okx/okx_api_for_trading_bot_v5.md` (сводная таблица).

Прокси означает:

1. REST контроллеры нашего сервиса принимают запрос.
2. Дёргают OKX API v5.
3. OKX отдаёт **client model** (DTO ответа биржи).
4. Мы мапим: **client DTO → domain model → REST model**.
5. Пока что **REST model = domain model** (1:1), но слой должен быть выделен.

---

## Область работ

### 1) Источник правды по списку методов

* Список эндпоинтов/методов берём **строго из таблицы** `docs/api/okx/okx_api_for_trading_bot_v5.md`.
* Для каждого метода из таблицы должен появиться:

    * client DTO (request/response при необходимости)
    * method в OKX client service
    * domain model (если отличается от client)
    * REST endpoint в нашем сервисе

---

## Архитектура и слои

### Пакеты (рекомендация)

* `com.example.tradingbot.client.okx.*`

    * `OkxRestClient` (вызовы OKX)
    * `OkxAuthSigner` (подпись приватных запросов)
    * `dto.*` (client models: request/response)

* `com.example.tradingbot.domain.model.*`

    * доменные модели результата (нейтральные к OKX формату)

* `com.example.tradingbot.rest.model.*`

    * REST модели ответа (пока 1:1 с domain)

* `com.example.tradingbot.mapping.*`

    * MapStruct mapper’ы: `Client→Domain`, `Domain→Rest`

* `com.example.tradingbot.rest.controller.*`

    * контроллеры (группировать по домену: account/trade/market/public и т.п.)

---

## Правила маппинга

* Не «протаскивать» наружу сырые client DTO OKX.
* Доменная модель должна иметь понятные типы и названия.
* На этапе 02 допустимо:

    * хранить числовые значения как `String`, если это соответствует описанию доков, и мы ещё не определили точные типы.
    * либо сразу использовать `BigDecimal`/`long` там, где очевидно (например, timestamps), **если это не усложняет**.

---

## Конфигурация и безопасность

* Публичные методы OKX — без подписи.
* Приватные методы OKX — подписываем через `OkxAuthSigner` (HMAC‑SHA256 Base64) на основе `OkxConfig`.
* Логи: секреты не логировать.

---

## Definition of Done

1. Для **каждого** метода из таблицы `okx_api_for_trading_bot_v5.md` есть соответствующий REST endpoint в нашем сервисе.
2. Каждый endpoint вызывает OKX и возвращает REST response (пока = domain) с маппингом client→domain→rest.
3. Единообразная обработка ошибок OKX (`code`, `msg`) и HTTP ошибок.
4. Код соответствует `codex/Code style.md`.

---

## Примечание

Stage 02 — это **прокси**, без торговой логики, без state machine, без БД и без сценариев.
