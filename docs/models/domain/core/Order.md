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
| `attachedAlgoOrders` | `List<AttachedAlgoOrder>` | Embedded attached protection. |

Доменные методы: `isLive()` (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED),
`isNotLive()`, `toCancel(reason)`, `toComplete()` (→ COMPLETED/FILLED),
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

## Структура `AttachedAlgoOrder` (раздел `Order`)

Embedded защитный algo-order, созданный вместе с parent `Order` (OKX
`attachAlgoOrds`). На первом этапе — embedded-часть `Order`, **не**
standalone `AlgoOrder` (раздел модели по
`.claude/decisions/model-granularity.md`). Не материализуется
автоматически в standalone `AlgoOrder`, даже если в snapshot есть
attached/algo identifiers; standalone `AlgoOrder` создаётся только
отдельным `StrategyAlgoOrderAction` через `CREATE_ALGO_ORDER`.

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

Доменные методы: `isActiveLike()` (PENDING/ACTIVE), `isTerminal()`
(COMPLETED/CANCELED/ERROR), `canTransitionTo(target)` (явная
матрица — см. lifecycle), `toPending/toActive/toComplete/toCancel/
toError`, `hasExternalType()`.

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
