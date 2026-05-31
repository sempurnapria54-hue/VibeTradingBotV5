# Auditable

## На какой вопрос отвечает этот файл

Какие общие audit-поля несут доменные сущности и откуда берётся
свежесть данных биржи.

## Назначение

`Auditable` — базовый класс audit-полей доменных сущностей
(`domain.model.Auditable`). Доменные модели, которым нужна
аудируемость и отметки свежести (`Instrument`, `Exchange`,
`Candle`, `CandleGroup`, `InstrumentExternalRules` и др.),
наследуют его. Слой — `domain/other` (прочая хранимая модель;
аудит).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `createdAt` | `OffsetDateTime` | Время создания записи в системе. |
| `createdBy` | `String` | Пользователь/сервис, создавший запись. |
| `modifiedAt` | `OffsetDateTime` | Время последнего изменения в системе. |
| `modifiedBy` | `String` | Пользователь/сервис последнего изменения. |
| `externalCreatedAt` | `OffsetDateTime` | Время создания записи на стороне биржи. |
| `externalModifiedAt` | `OffsetDateTime` | Время последнего обновления на стороне биржи. |

Системные `createdAt`/`modifiedAt`/`createdBy`/`modifiedBy`
проставляются persistence-слоем (JPA auditing); биржевые
`externalCreatedAt`/`externalModifiedAt` заполняет код,
производящий данные, из таймстемпов источника.

## Свежесть рыночных данных

`externalCreatedAt`/`externalModifiedAt` — носители свежести
данных от биржи. Производящая сторона (например, загрузка свечей)
обязана проставлять корректные биржевые таймстемпы; на них потом
опирается проверка устаревания у потребителей
(`docs/components/MarketDataExpirationChecker.md`,
`docs/rules/market-data-freshness.md`). Сама по себе модель
`Auditable` срок годности не проверяет — только хранит отметки.

## Персистентность

В persistence-слое соответствует базовому `AuditableEntity`,
от которого наследуются entity доменных моделей. Отдельной таблицы
не имеет — поля встраиваются в таблицы наследников.

Auditable заводится **в каждом слое отдельно, с постфиксом слоя**
(codestyle: Auditable по слоям): домен — `Auditable`, persistence —
`AuditableEntity`, api — `AuditableApiResponse` (api-ответы сущностей
наследуют его, отдавая поля аудита наружу). Доменный `Auditable` в
других слоях не переиспользуется.

## Связи

- Модели-наследники: `docs/models/domain/core/Instrument.md`,
  `docs/models/domain/core/Exchange.md`,
  `docs/models/domain/other/Candle.md`,
  `docs/models/domain/other/CandleGroup.md`,
  `docs/models/domain/other/InstrumentExternalRules.md`.
- Свежесть — `docs/rules/market-data-freshness.md`,
  `docs/components/MarketDataExpirationChecker.md`.
