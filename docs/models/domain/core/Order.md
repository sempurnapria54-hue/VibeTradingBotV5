# Order

## На какой вопрос отвечает этот файл

Что это за торговая модель `Order` (ordinary order) и вложенная
`AttachedAlgoOrder` (attached protection): структура, атрибуты,
енумы, external snapshots.

Статусы и переходы — в `docs/lifecycles/Order.md`.

## Назначение

`Order` — ordinary exchange order, связанный с конкретной `Deal`.
Хранит: локальный intent (что бот хотел создать), идентификаторы
(`internalId` + биржевой `externalId`), актуальный доменный статус,
сырой внешний статус биржи, параметры цены/размера, факты исполнения,
attached protection (если создана вместе с parent order).

`Order` **не** является действием стратегии. Связь
`StrategyAction` ↔ `Order` хранится через
`DealActionState` → `RuntimeTarget(entityType = ORDER, entityId)`,
поэтому `Order` не хранит `strategyActionId`, `strategyActionKey`,
`role`, `level` стратегии (механизм связи —
`docs/models/domain/other/DealActionState.md`).

## Структура `Order`

Java-класс `com.example.tradingbot.domain.model.core.order.Order`,
расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор в БД. |
| `dealId` | `Long` | Сделка, к которой относится ордер. |
| `internalId` | `String` | Межсервисный id; stable client id (OKX `clOrdId`). |
| `externalId` | `String` | Биржевой id ordinary order (OKX `ordId`). |
| `status` | `Status` | Доменный статус (см. lifecycle). |
| `closeReason` | `CloseReason` | Причина финализации / перевода в ERROR. |
| `type` | `Type` | Бизнес-тип ordinary order (не подменяет strategy role). |
| `side` | `String` | Сторона (OKX `buy` / `sell`). |
| `externalStatus` | `String` | Сырой статус биржи (OKX `state`) — **диагностический факт**, FSM напрямую не использует. |
| `price` | `BigDecimal` | Цена (для market-like может быть null). |
| `size` | `BigDecimal` | Размер (для SWAP/FUTURES — контракты). |
| `accumulatedFillSize` | `BigDecimal` | Накопленный исполненный объём. |
| `averagePrice` | `BigDecimal` | Средняя цена исполнения. |
| `fee` | `BigDecimal` | Накопленная комиссия. |
| `positionReducingOnly` | `Boolean` | Доменное намерение: ордер только уменьшает позицию. |
| `replacesInternalId` | `String` | `internalId` предшественника в цепочке REPLACE (nullable; append-only след — обратная ссылка не хранится, выводится запросом). См. `docs/decisions/replace-not-amend.md`. |
| `plannedEntryPrice` | `BigDecimal` | Reference-цена входа **этой ноги**, по которой считался её риск (для market-входа — `ORDER_MARKET_REFERENCE_PRICE` калькулятора, на биржу не отправляемая). Write-once. |
| `plannedSizeContracts` | `BigDecimal` | Заявленный размер **этой ноги** (контракты). Write-once. |
| `plannedRiskAmount` | `BigDecimal` | **Плановый риск этой ноги** — убыток на её стопе, посчитанный при постановке ноги. Слагаемое знаменателя `R` сделки (H6/H11 `DOCS_CHECK_15`). Write-once. |
| `plannedRiskCurrency` | `String` | Валюта планового риска ноги (расчётная валюта инструмента). Write-once. |
| `attachedAlgoOrders` | `List<AttachedAlgoOrder>` | Embedded attached protection. |

Доменные методы: `isLive()` (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED),
`hasActiveAttachedProtection()` (есть ≥1 active-like (PENDING/ACTIVE)
attached-защита), `toCancel(reason)`, `toComplete()` (→ COMPLETED/FILLED),
`toError(reason)`.

### `positionReducingOnly`

Доменное намерение (не внешний факт биржи, не заполняется из
`OrderExternalSnapshot`): ordinary order должен только уменьшать
позицию, не открывать/не увеличивать. Важно для partial exit
(частичное уменьшение — только через reduce-only `Order`/`AlgoOrder`,
см. `docs/rules/no-partial-close.md`). Маппинг в OKX `reduceOnly` и
invariant-проверка — в `docs/models/mapping/Order.md`.
Если биржа не поддерживает reduce-only/close-only — adapter может
проигнорировать; unsupported exchange на первом этапе не блокируем.

### Енумы `Order`

- **`Type`**: `ENTRY`, `ENTRY_ATTACHED_STOP_LOSS`. Бизнес-тип, не
  описывает strategy role (grid-entry / partial-exit / full-exit).
- **`Status`**: `CREATED`, `PENDING`, `ACTIVE`, `PARTIALLY_COMPLETED`,
  `COMPLETED`, `CANCELED`, `ERROR` (значения/переходы — в lifecycle).
- **`CloseReason`**: `FILLED`, `CANCELED_BY_STRATEGY`,
  `REPLACED_BY_STRATEGY` (стратегия заменила другим ордером —
  REPLACE-ремодел, симметрично `AlgoOrder`), `KILL_SWITCH`,
  `MANUAL_CANCEL`, `CONDITION_EXPIRED` (условие создания/ожидания
  ордера больше неактуально — штатная причина, не ошибка),
  `MISSING_AFTER_REFRESH` (не найден после refresh/search/history
  цикла), `UNKNOWN_EXTERNAL_STATUS`, `EXCHANGE_INVARIANT_VIOLATION`,
  `UNKNOWN`.

### Плановый риск и его операнды — дом здесь (`RISK-Q4` закрыт; H6/H11 `DOCS_CHECK_15`)

`plannedEntryPrice` (reference-цена входа, по которой считался риск),
`plannedSizeContracts` (заявленный размер) и **`plannedRiskAmount` /
`plannedRiskCurrency`** (сам плановый риск) — **атрибуты ноги входа**, не
сделки: при многоногом входе их несколько.

**Риск переехал на ногу вместе со своими операндами** (H6/H11
`DOCS_CHECK_15`, решение пользователя). Прежде на `Deal` жило одно
write-once-число, а операнды сравнения — на ногах; тождество, на котором
стоит омиссионный член epsilon
(`plannedRiskAmount − |plannedEntryPrice − stop| × plannedSizeContracts ×
ctVal` = ожидаемая комиссия), верно **только в одной точке** — там, где
все три операнда принадлежат одной ноге. При многоногом входе обе
возможные ветки были односторонними: агрегат по сделке против ценовой
части одной ноги даёт **отрицательное** вычитаемое (допуск схлопывается до
шум-флора на самых крупных сделках), первая нога — вычитаемое ~1/N
(допуск систематически уже режима отказа). Держа риск на ноге, тождество
восстанавливается **поногово**, а сделке достаётся **сумма** — и та же
сумма закрывает второй дефект: знаменатель `R` перестаёт быть числом
первой ноги (`docs/models/domain/aggregate/Deal.md` §«Плановый риск»).

**Все четыре — write-once** (`updatable = false`). REPLACE-нога не
переписывает ни reference-цену, ни риск: иначе разрыв «заявлено ↔ взято»
становится неизмеримым, а слагаемое знаменателя перестаёт быть тем
числом, под которое сайзились
(`docs/decisions/per-trade-risk-policy.md` §«Асимметрия»).

**Дом — только `orders`** (`RISK-Q4` закрыт 2026-08-20): входной тропы
алго-ордером не существует (вход — ordinary `Order`; у
`AlgoOrder.ConditionType` входного значения нет — проверено по коду),
поэтому вторая четвёрка колонок в `algo_orders` была бы мёртвой схемой с
живым именем. Пишет их исполнитель `CREATE_ORDER_COMMAND` входного
действия той же транзакцией, что создание ноги; переиспользовать
`orders.size`/`price` нельзя — это колонки биржевой стороны,
перезаписываемые эхом ответа при каждом рефреше. Условие возврата вопроса
— `docs/models/domain/core/AlgoOrder.md` §Назначение.

**Пусты у не-входных ног.** Поля заполняются только у ног входа
(`Type.ENTRY`); у reduce-only и защитных ordinary-ордеров планового риска
нет по построению — они риска не создают
(`docs/rules/risk-validator-scope.md`). Пустота здесь — «признак
неприменим», а не «операнд не добыт»
(`docs/rules/absent-value-semantics.md`).

## Персистентность

Хранится в БД (entity `OrderEntity`, таблица `orders`, создана
`V6__create_deal_runtime_tables.sql`), наследует audit-поля
(`AuditableEntity`). Раздел заведён H16 `DOCS_CHECK_14` — **место истины
схемы сущности** (`docs/rules/persistence-representation.md` §«Место
истины схемы»); schema-дельта шага — сборка-указатель.

- Состав `V6`: `id` (identity, PK), `deal_id` (`NOT NULL`, FK →
  `deals`), `internal_id` (`varchar(64)` `NOT NULL`,
  `uk_order_internal_id`), `external_id` (`varchar(64)`), `status`
  (`varchar(32)` `NOT NULL`), `close_reason` (`varchar(32)`), `type`
  (`varchar(32)` `NOT NULL`), `side` (`varchar(16)`), `external_status`
  (`varchar(32)`), `price`, `size`, `accumulated_fill_size`,
  `average_price`, `fee` (все `numeric(36,18)`, nullable),
  `position_reducing_only` (`boolean`), `replaces_internal_id`
  (`varchar(64)`), шесть audit-колонок (`AuditableEntity`, nullable).
- **Колонки шага 7 — `ALTER`**: `planned_entry_price`,
  `planned_size_contracts`, **`planned_risk_amount`** (все три
  `numeric(36,18)`) и **`planned_risk_currency`** (`varchar(64)` —
  строковая колонка по правилу длин,
  `docs/rules/persistence-representation.md` §«Строковые колонки: длины»);
  все nullable (пусты у не-входных ног и у ордеров, заведённых вне нашего
  входа), write-once на уровне entity (`updatable = false`). Пара
  `planned_risk_*` добавлена H6/H11 `DOCS_CHECK_15` — дом планового риска
  переехал с `Deal` на ногу, на сделке остаётся сумма. Бэкфилл не нужен
  (`.claude/rules/pre-launch-schema-changes.md`).
- Enum-поля хранятся строкой (имя enum; codestyle §Слои моделей и
  enum'ы).

## Структура `AttachedAlgoOrder` (раздел `Order`)

Embedded защитный algo-order, созданный вместе с parent `Order` (OKX
`attachAlgoOrds`). На первом этапе — embedded-часть `Order`, **не**
standalone `AlgoOrder` (раздел модели по
`.claude/decisions/model-granularity.md`). Не материализуется
автоматически в standalone `AlgoOrder`, даже если в snapshot есть
attached/algo identifiers; standalone `AlgoOrder` создаётся только
отдельным `StrategyAlgoOrderAction` через `CREATE_ALGO_ORDER_COMMAND`.

Java-класс `...core.order.AttachedAlgoOrder`, расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор в БД. |
| `orderId` | `Long` | Parent `Order`. |
| `internalId` | `String` | Межсервисный id (OKX `attachAlgoClOrdId`). Ключ матчинга. |
| `externalAttachedId` | `String` | Id attached algo на бирже, пока он attached (OKX `attachAlgoId`). |
| `externalId` | `String` | Внешний id algo-order, если биржа возвращает (не материализует в standalone). |
| `status` | `Status` | Доменный статус (см. lifecycle). |
| `closeReason` | `CloseReason` | Причина финализации. |
| `type` | `Type` | Внутренний тип (`ATTACHED_STOP_LOSS`). |
| `externalStatus` | `String` | Сырой внешний статус (у OKX attachAlgoOrds полноценного state нет). |
| `externalType` | `String` | Биржевой тип attached protection. |
| `size` | `BigDecimal` | Размер. |
| `stopLossTriggerPrice` | `BigDecimal` | Триггерная цена SL (текущий проект — attached SL). |

Доменные методы: `isActiveLike()` (PENDING/ACTIVE),
`canTransitionTo(target)` (явная матрица — см. lifecycle),
`toPending/toActive/toComplete/toCancel/toError`.

### Енумы `AttachedAlgoOrder`

- **`Type`**: `ATTACHED_STOP_LOSS`.
- **`Status`**: `CREATED`, `PENDING`, `ACTIVE`, `COMPLETED`,
  `CANCELED`, `ERROR` (переходы — в lifecycle).
- **`CloseReason`**: `TRIGGERED`, `SWITCHED_BY_STRATEGY` (снята после
  подтверждения standalone main protection), `PARENT_ORDER_CANCELED`,
  `KILL_SWITCH`, `MANUAL_CANCEL`, `MISSING_AFTER_REFRESH`,
  `PROTECTION_LOST` (была активной, больше не подтверждается, и
  standalone protection отсутствует), `UNKNOWN_EXTERNAL_STATUS`,
  `UNKNOWN`.

## External snapshots

Нормализованные snapshots для refresh/search/history flow, не
persisted runtime-сущности (разделы модели по `model-granularity.md`).
Raw DTO не выходит за adapter-layer (`docs/rules/raw-exchange-dto-boundary.md`).
OKX mapping — в `docs/models/mapping/Order.md`.

- **`OrderExternalSnapshot`**: `internalId`, `externalId`, `type`,
  `side`, `externalStatus`, `price`, `size`, `accumulatedFillSize`,
  `averagePrice`, `fee`, `attachedAlgoOrders:
  List<AttachedAlgoOrderExternalSnapshot>`, `attachedAlgoInternalId`
  (top-level attached client id), `takeProfitTriggerPrice`,
  `stopLossTriggerPrice` (top-level triggers).
- **`AttachedAlgoOrderExternalSnapshot`**: `externalAttachedId`,
  `internalId` (ключ матчинга), `externalId`, `externalType`, `size`,
  `stopLossTriggerPrice`, `failCode`, `failReason` (заполненный
  failCode/failReason → attached ERROR).

## Что Order не хранит

На первом этапе не хранит: `strategyActionId`/`strategyActionKey`
(в `DealActionState`), `marginMode`/`tradeMode`/`positionSide` (OKX
`tdMode=isolated`/`posSide=net` — константы `OkxIntegrationService`),
external rules инструмента и fresh market price (собираются в
`CalculationContext` перед расчётом action), raw command result
history (проектируется отдельно, не runtime state), `reduceOnly` как
отдельный external snapshot-факт (проверяется adapter-layer прямо из
`OrderResponse.reduceOnly`).
