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

`CREATED`, `PENDING`, `ACTIVE`, `TRADE_BLOCKED`, `CLOSED`, `ERROR`. Статус
подключения/использования биржи.

`TRADE_BLOCKED` — **safety-холд биржи** (уровень 4,
`docs/rules/exchange-hold.md`): каскадно блокирует входы по всем
инструментам биржи, safety/read разрешены; вход — только из `ACTIVE` по
аварии, снятие — **вручную** в `ACTIVE`.

> **Множество входа не пересматривалось** (свип `GAPS_CLOSE_7`). Для
> `Instrument` ратифицировано, что `TRADE_BLOCKED`/`CLOSED`/`ERROR`
> достижимы **из любого статуса** (`docs/rules/instrument-hold.md`
> §«Множества входа», H13): авария может застать сущность в любом
> состоянии, а ограничение входа делает реакцию пропускаемой. Для
> `Exchange` тот же вопрос **не обсуждался** — формулировка оставлена как
> есть, чтобы не решать за владельца; зарегистрирована в backlog п.9
> вместе с прочим долгом биржевого статуса.

> **Мягким классом холда `Exchange.TRADE_BLOCKED` не используется**
> (H3, `GAPS_CLOSE_6`). Аккаунт-радиус мягкой реакции (несвежесть ставки,
> режим «вызов не прошёл») выражается набором строк инструментов
> (`Instrument.Status.ENTRY_BLOCKED` по всем инструментам контура), а не
> биржевой строкой: биржевой статус несёт биржевой блок-сет и каскадный
> перехват активных сделок — превышение радиуса
> (`docs/rules/instrument-hold.md` §Enforcement).

> Сквозное правило блокировки создания новых `Deal` при проблемах биржи —
> `docs/rules/exchange-hold.md`. Полный lifecycle `Exchange` (включая
> `DISABLED` среди прочих состояний) — backlog п.9.
>
> **Известный разнобой имён safety-статуса биржи — запаркован до
> backlog п.9, не пропущен.** Источники расходятся:
> (1) часть доков зовёт статус `Exchange.HOLD`
> (`docs/rules/exchange-hold.md`, `docs/rules/external-status-resolution.md`,
> `docs/lifecycles/Order.md`, `docs/lifecycles/AlgoOrder.md`,
> `docs/models/mapping/AlgoOrder.md`, `docs/models/mapping/Balance.md`,
> `docs/integrations/okx/rules/reduce-only-invariant.md`);
> (2) аппарат шага 6 и **код** зовут его `Exchange.TRADE_BLOCKED`
> (`docs/decisions/controlled-violation-exchange-wide-hold.md`,
> `docs/rules/controlled-exchange-exceptions.md`,
> `docs/components/SafetyHoldCoordinator.md`; `Exchange.java` —
> `TRADE_BLOCKED` + `isTradeBlocked()`).
>
> Прежняя редакция этой ноты добавляла третьим источником «в енуме нет ни
> того, ни другого» — **инвентарь был стейл**: статус в коде есть,
> перечень выше приведён к коду на `GAPS_CLOSE_6` (H3). Долг сузился до
> **переименования `Exchange.HOLD` → `TRADE_BLOCKED` в доках группы (1)**.
>
> **Имя в обороте — `TRADE_BLOCKED`.** Долг **унаследованный** (шагом 7
> не введён и не расширен): шаг 7 писателя `Exchange.HOLD` **не
> вводит** — холд по несвежести ставки комиссии уехал на **инструмент**
> (`GAPS_CLOSE_4`, `docs/rules/instrument-hold.md` §«Несвежесть ставки
> комиссии»; критерий — `docs/rules/error-handling-policy.md`
> §«Радиус ущерба задаёт scope»). Сведение имени — **сознательно
> оставлено** на backlog п.9 (полный lifecycle
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
