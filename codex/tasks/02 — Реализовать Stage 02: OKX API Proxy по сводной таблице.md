# Task 020 — Реализовать Stage 02: OKX API Proxy по сводной таблице

## Контекст

Нужно реализовать **Stage 02** из `codex/stage/02_okx_api_proxy.md`.
Список методов берём из `docs/api/okx/okx_api_for_trading_bot_v5.md` (таблица). Реализуем **прокси**: OKX client DTO → domain model → REST model (пока REST model = domain model).

---

## Важное требование

**Не придумывай методы.**

1. Прочитай `docs/api/okx/okx_api_for_trading_bot_v5.md`.
2. Возьми **все** методы, перечисленные в таблице.
3. Реализуй их как REST proxy endpoints в нашем сервисе.

---

## Что нужно сделать

### 0) Цепочка ответственности (обязательно)

Должно быть ровно так:

1. **Controller** получает REST request → мапит **REST → Domain** → вызывает Service.
2. **Service** делает прикладную/доменную логику → вызывает Client Service.
3. **Client Service** мапит **Domain → Client DTO**, вызывает OKX через OkxRestClient, мапит **Client DTO → Domain**, возвращает Domain.
4. **Service** возвращает Domain в Controller.
5. **Controller** мапит **Domain → REST** и возвращает ответ.

Запрещено:

* Контроллеру напрямую вызывать `OkxRestClient`.
* Протаскивать client DTO наружу (в REST) или использовать client DTO в контроллере.

---

### 1) OKX клиент (низкоуровневый HTTP)

Создай пакет `com.example.tradingbot.client.okx`:

1. `OkxAuthSigner`

* Делает подпись приватных запросов OKX v5 (HMAC‑SHA256 Base64).
* Использует `OkxConfig` (apiKey/secretKey/passphrase/baseUrl).

2. `OkxRestClient`

* На базе `RestTemplate`.
* Для каждого метода из таблицы:

  * делает HTTP вызов на OKX (GET/POST и т.д.)
  * для приватных — добавляет заголовки OK-ACCESS-* через signer
  * парсит ответ в **client response DTO**

3. `client.okx.dto.*`

* Создай DTO под каждый эндпоинт (request/response), ориентируясь на описание в `docs/api/okx/`.
* Если для эндпоинта есть уже «подробный файл» — следуй ему.
* Если подробного файла ещё нет — DTO делай минимальным, по фактическим полям ответа, которые нужны для прокси.

### 2) Domain models

Создай пакет `com.example.tradingbot.domain.model.okxproxy` (или аналогичный):

* Доменные модели результата по каждому методу.
* Нормальные имена полей.
* На этом этапе допускается хранить большинство числовых значений как `String`, если типы ещё не зафиксированы в доках.

### 3) REST models

Создай пакет `com.example.tradingbot.rest.model.okxproxy`:

* Пока REST модель **1:1** с domain.
* Можно технически переиспользовать domain классы в REST ответах, но предпочтительно держать отдельный пакет, даже если классы одинаковые.

### 4) MapStruct маппинг (по сущностям)

Создай пакет `com.example.tradingbot.mapping.okxproxy`.

Правила:

* Для **каждой доменной сущности** отдельный маппер (например `OrderMapper`, `PositionMapper`, `AlgoOrderMapper`, и т.д.).
* В каждом маппере методы с говорящими названиями:

  * `restToDomain(...)` / `domainToRest(...)`
  * `clientToDomain(...)` / `domainToClient(...)`
* Методы могут называться одинаково, но иметь разные входные параметры.
* **Client DTO наружу не возвращаем.**
* Маппинг должен идти строго по цепочке: **REST↔Domain** (Controller) и **Domain↔Client** (Client Service).

### 5) Client Service (domain-in / domain-out)

Создай пакет `com.example.tradingbot.domain.service.okxproxy` (или `application.service`):

* Для каждой группы методов (trade/account/market) — отдельный сервис (например `OkxTradeClientService`).
* Сервис принимает **Domain request/params**, мапит в client DTO, вызывает `OkxRestClient`, мапит response в Domain и возвращает Domain.
* Этот слой инкапсулирует детали OKX DTO.

### 6) Service (прикладной уровень)

Создай пакет `com.example.tradingbot.domain.service` (или `application.service`):

* Service вызывает соответствующий Client Service.
* Здесь может быть прикладная логика (пока минимальная, Stage 02 — прокси).

### 7) REST контроллеры

Создай контроллеры в `com.example.tradingbot.rest.controller.okxproxy`:

* Сгруппируй эндпоинты логично (например: account/market/trade/public).
* Для каждого метода из таблицы:

  * сделай endpoint в нашем сервисе
  * проксируй параметры запроса
  * маппинг **REST→Domain**
  * вызови Service
  * маппинг **Domain→REST**
  * верни REST response

Рекомендация по неймингу:

* Базовый префикс: `/api/okx/v5/...`
* Дальше — повтори структуру OKX пути, чтобы было очевидно соответствие.

### 8) Единообразная обработка ошибок

* Если OKX вернул `code != "0"` — верни 4xx/5xx с телом ошибки (минимум `code`, `msg`).
* Если HTTP ошибка/таймаут — верни 502/504 (или 500) с понятным сообщением.
* Секреты в логах запрещены.

### 7) Документация

Обнови (если нужно):

* `docs/api/okx/okx_api_for_trading_bot_v5.md`

  * добавь/проверь ссылки на:

    * подробные описания
    * доменные модели (`docs/models/domain/...` или если добавишь отдельный раздел)

На этапе 02 допустимо просто добавить раздел «Реализация в коде» с путями пакетов.

---

## Инварианты и стиль

* Соблюдай `codex/Code style.md`:

  * не объявлять несколько переменных через запятую
  * всегда `{}` после `if/for/while`
* Время: UTC.
* Таймфреймы OKX: только `OkxTimeframes`.

---

## Definition of Done

1. Все методы из таблицы `okx_api_for_trading_bot_v5.md` доступны через REST нашего сервиса.
2. Для каждого метода есть client DTO → domain model → REST model (с мапперами).
3. Для приватных методов корректно ставится подпись (OK-ACCESS-*).
4. `/actuator/health` остаётся `UP`.
5. Проект компилируется.

---

## Что НЕ делать

* Не добавлять торговую стратегию, оркестрацию, state machine.
* Не добавлять БД-модели/репозитории для этих методов.
* Не добавлять «лишние» эндпоинты вне таблицы.
