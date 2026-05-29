# Exchange

## На какой вопрос отвечает этот файл

Что это за доменная модель `Exchange`: структура, енумы,
персистентность, связи.

## Назначение

`Exchange` — биржа, с которой работает бот. Несёт идентичность
(`id`, `internalId`, уникальное `name`), точку подключения
(`baseUrl`) и статус использования. Инструменты ссылаются на биржу
через `Instrument.exchangeId`.

Слой — `domain/core` (модель с биржевым воплощением: сама биржа —
её прямое воплощение). Java-класс —
`domain.model.core.exchange.Exchange`, наследует `Auditable` (см.
`docs/models/domain/other/Auditable.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор биржи. |
| `internalId` | `String` | Межсервисный идентификатор биржи. |
| `name` | `String` | Уникальное имя биржи (например, `OKX`). |
| `baseUrl` | `String` | Базовый URL API биржи. |
| `status` | `Status` | Текущий статус подключения/использования биржи. |

Поля аудита — из `Auditable`.

## Енумы

### `Status`

`CREATED`, `PENDING`, `ACTIVE`, `CLOSED`, `ERROR`. Статус
подключения/использования биржи.

> Сквозное правило `Exchange.HOLD`/`DISABLED` (блокировка создания
> новых `Deal` при проблемах биржи) — `docs/rules/exchange-hold.md`.
> Полный lifecycle `Exchange` (включая `HOLD`/`DISABLED` среди
> прочих состояний) — backlog п.9; здесь зафиксирован набор
> статусов доменного класса как есть.

## Персистентность

Хранится в БД (entity `ExchangeEntity`, таблица `exchanges`),
наследует audit-поля. Ограничения схемы:

- `id` — identity (autoincrement).
- Уникальность: `internal_id` (uk_exchange_internal_id);
  `name` (uk_exchange_name).
- `internal_id`, `name`, `base_url`, `status` — `NOT NULL`.
- `internal_id` — `updatable = false`.

## Связи

- Инструменты биржи — `docs/models/domain/core/Instrument.md`
  (`Instrument.exchangeId` → `Exchange.id`).
- Правило блокировки торговли при проблемах биржи —
  `docs/rules/exchange-hold.md`.
- Audit-база — `docs/models/domain/other/Auditable.md`.
