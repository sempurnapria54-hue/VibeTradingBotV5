# OkxRawApiRequest (конверт generic raw-passthrough)

## На какой вопрос отвечает этот файл

Какие поля у `OkxRawApiRequest` — конверта запроса generic-эндпоинта
`POST /api/proxy/okx/raw`, и как эндпоинт их читает.

## Контекст

Request-DTO нашего API (Java `OkxRawApiRequest`,
`api.model.request`). Тело запроса единственного эндпоинта контура
тестов API источника — `POST /api/proxy/okx/raw` на
`OkxProxyController`. Эндпоинт десериализует тело в этот DTO, читает
поля и делегирует в универсальный `OkxRestClient.dispatch`
(маршрутизация public/signed RestClient, подпись и креды — на стороне
app), возвращая **сырой** `OkxApiResponse<JsonNode>` (контракт биржи
без нашей типизации).

Это **тест-обращённый конверт**, не продуктовая сущность: им контур
достаёт любой in-perimeter эндпоинт OKX (тело/`query` строятся руками
по контракту OKX). Конвенция api-слоя «наружу `internalId`, не `id`»
к нему неприменима — доменной сущности за ним нет. Эндпоинт закрыт
`@Profile("!prod")` (шлёт write'ы в произвольный path → в prod
недоступен).

Дизайн контура и роль `/raw` — `.claude/decisions/source-api-target-rebase.md`
(раздел D). Универсальный механизм отправки —
`OkxRestClient.dispatch`.

## Поля DTO

| Поле | Тип | Обяз. | Назначение |
|---|---|---|---|
| `method` | String | да (`@NotBlank`) | HTTP-метод вызова OKX (`GET` / `POST`); эндпоинт переводит в `HttpMethod` через `HttpMethod.valueOf(method)`. |
| `path` | String | да (`@NotBlank`) | Путь эндпоинта OKX (например `/api/v5/account/config`). |
| `query` | `Map<String, Object>` | нет | Query-параметры запроса (имя → значение). `dispatch` опускает `null`/blank-значения. |
| `body` | `JsonNode` | нет | Сырое тело запроса для write-вызовов (JSON по контракту OKX); сериализуется как есть. |
| `signed` | Boolean | да (`@NotNull`) | `true` → подписанный приватный вызов (`okxAuthRestClientHttp`), `false` → публичный (`okxRestClientHttp`). |

`signed` обязателен — конверт явно указывает приватность вызова;
`dispatch` дополнительно null-безопасен (`isTrue(signed)` трактует
`null` как public). `body` сырой (`JsonNode`), а не типизированный
DTO: контур проверяет контракт биржи, не наш слой
(`.claude/decisions/source-api-target-rebase.md`, §«контур проверяет
контракт биржи, не наш код»).

## Связи

- Эндпоинт / делегирование — `OkxProxyController`, `OkxRestClient.dispatch`.
- Решение о ре-базе контура на сырьё —
  `.claude/decisions/source-api-target-rebase.md`.
- Обёртка ответа — `docs/models/integrations/okx/` (OKX native DTO),
  конверт ответа `OkxApiResponse<JsonNode>`.
- Конвенции api-слоя — `docs/models/api/README.md`.
