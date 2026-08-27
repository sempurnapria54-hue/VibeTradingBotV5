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

`CREATED`, `PENDING`, `ACTIVE`, `HOLD`, `TRADE_BLOCKED`, `CLOSED`,
`ERROR`. Статус подключения/использования биржи.

Safety-состояния — **двухступенчатая лестница**
(`docs/rules/exchange-hold.md`,
`docs/decisions/exchange-safety-ladder.md`):

- `HOLD` — **мягкий холд** (ступень 1): биржа выпадает из entry-скана ⇒
  новые сделки не создаются; командного блок-сета нет, живые сделки
  сопровождаются полностью; без kill-switch. Ставится **вручную**
  (автоматических триггеров нет) либо спуском со ступени 2; вход — из
  `ACTIVE`; снятие — **вручную** в `ACTIVE`. В коде статуса ещё нет —
  вводится на `CODE` (`docs/rules/exchange-hold.md` §«Состояние
  носителей»).
- `TRADE_BLOCKED` — **критическая реакция** (ступень 2): kill-switch
  flatten всей биржи + каскад активных сделок в `ERROR`, после teardown —
  блок всех команд permission `Trade` (открыт только read). Триггеры:
  неожиданное поведение биржи либо живой риск без защиты. Вход — из
  любого статуса (авария застаёт биржу в любом состоянии — зеркалит
  `docs/rules/instrument-hold.md` §«Множества входа»); снятие —
  **вручную и только в `HOLD`**.

> **Мягким классом холда биржевые статусы не используются**
> (H3, `GAPS_CLOSE_6`). Аккаунт-радиус мягкой реакции (несвежесть ставки,
> режим «вызов не прошёл») выражается набором строк инструментов
> (`Instrument.Status.ENTRY_BLOCKED` по всем инструментам контура), а не
> биржевой строкой: биржевые статусы несут биржевой блок-сет —
> превышение радиуса (`docs/rules/instrument-hold.md` §Enforcement).

> Сквозное правило блокировки создания новых `Deal` при проблемах биржи —
> `docs/rules/exchange-hold.md`. Полный lifecycle `Exchange` (включая
> `DISABLED` среди прочих состояний) — backlog п.9.
>
> **Прежний разнобой имён safety-статуса биржи снят лестницей**
> (2026-08-26, `docs/decisions/exchange-safety-ladder.md`): `HOLD` и
> `TRADE_BLOCKED` — не два имени одного состояния, а две ступени.
> Доки, звавшие статус `Exchange.HOLD` (мягкий холд), и код/аппарат
> шага 6, звавшие `Exchange.TRADE_BLOCKED` (flatten), описывали каждый
> свою ступень; долг «переименовать `HOLD` → `TRADE_BLOCKED`» снят.

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
