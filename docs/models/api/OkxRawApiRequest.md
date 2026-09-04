# OkxRawApiRequest (конверт generic raw-passthrough)

## На какой вопрос отвечает этот файл

Какие поля у `OkxRawApiRequest` — конверта запроса generic-эндпоинта
`POST /api/proxy/okx/raw`.

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
DTO: контур проверяет контракт биржи, не наш слой.

## Связи

- Эндпоинт / делегирование — `OkxProxyController`, `OkxRestClient.dispatch`.
- Обёртка ответа — `docs/models/integrations/okx/` (OKX native DTO),
  конверт ответа `OkxApiResponse<JsonNode>`.

## Режим поверхности

**Она закрыта дважды и в иерархию исполнения не входит.** Дом правила —
`docs/rules/api-access-policy.md` §«Поверхность сырого вызова источника»:
принципал плюс вывод из боевого профиля; там же сказано, почему детектор
чужой заявки объявляет её записи чужими **верно**, а не ошибочно.
Прежде ограничение жило только в javadoc контроллера, то есть домом не
являлось (`.claude/rules/parking-address.md`).
