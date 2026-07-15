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
>
> **Известный разнобой имён safety-статуса биржи — запаркован до
> backlog п.9, не пропущен.** Три источника расходятся:
> (1) часть доков зовёт статус `Exchange.HOLD`
> (`docs/rules/exchange-hold.md`, `docs/rules/external-status-resolution.md`,
> `docs/lifecycles/Order.md`, `docs/lifecycles/AlgoOrder.md`,
> `docs/models/mapping/AlgoOrder.md`, `docs/models/mapping/Balance.md`,
> `docs/integrations/okx/rules/reduce-only-invariant.md`);
> (2) аппарат шага 6 зовёт его `Exchange.TRADE_BLOCKED`
> (`docs/decisions/controlled-violation-exchange-wide-hold.md`,
> `docs/rules/controlled-exchange-exceptions.md`,
> `docs/components/SafetyHoldCoordinator.md`);
> (3) в енуме выше **нет ни того, ни другого**.
>
> **Имя в обороте — `TRADE_BLOCKED`.** Долг **унаследованный** (шагом 7
> не введён и не расширен): шаг 7 писателя `Exchange.HOLD` **не
> вводит** — холд по несвежести ставки комиссии уехал на **инструмент**
> (`GAPS_CLOSE_4`, `docs/rules/instrument-hold.md` §«Несвежесть ставки
> комиссии»; критерий — `docs/rules/error-handling-policy.md`
> §«Радиус ущерба задаёт scope»). Сведение имени и материализация
> статуса — **сознательно оставлены** на backlog п.9 (полный lifecycle
> `Exchange`), где решается весь набор состояний разом; точечное
> переименование до того расщепило бы долг.

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
