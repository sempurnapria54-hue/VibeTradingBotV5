# api — модели API нашего сервиса

## На какой вопрос отвечает этот файл

Что это за слой `docs/models/api/` и когда здесь появляются файлы.

## Назначение

Тир `api` хранит модели API нашего сервиса: request / response DTO
наших endpoint'ов. Сюда уходит знание о том, **что наружу отдаёт
наш API** — в отличие от `integrations/{name}` (что приходит снаружи
от внешних источников).

## Статус

API введён в шаге 1 (endpoint'ы для `Exchange` / `Instrument` /
`CandleGroup` + триггер джобы). Отдельные per-DTO файлы здесь пока
не заводятся: step-1 response-DTO просты (зеркало domain без `id`),
под критерий ниже не подпадают. Конвенции слоя — ниже.

## Конвенции api-слоя

- Наружу отдаётся `internalId`, **не** `id` из БД; ссылки на
  связанные сущности — их `internalId` (`exchangeInternalId`,
  `instrumentInternalId`); path-параметры — `internalId`.
- Каждое поле api-модели — со Swagger `@Schema(description = ...)`.
- Enum'ы только в домене; в api поля под enum — `String`.
- Auditable по слоям: api-ответы наследуют `AuditableApiResponse`
  (см. `docs/models/domain/other/Auditable.md`).

## Когда заводить файл

- Появился request/response DTO API-операции, у которого
  нетривиальная структура / документация полей / валидации.
- Структура отличается от domain (например, наружу выходит
  read-model без чувствительных полей).

## Что не место здесь

- Доменные модели (`docs/models/domain/...`).
- Native-модели внешних источников (`docs/models/integrations/{name}/`).
- Mapping между API DTO и domain (это `docs/models/mapping/<Сущность>.md`
  — раздел про api, по аналогии с integration-разделами).
