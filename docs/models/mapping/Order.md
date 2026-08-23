# Order — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменный `Order` (+ `AttachedAlgoOrder`) ложится на нативные модели
источников, нормализуется через `OrderExternalSnapshot` и как
резолвится его статус.

## Контекст

Mapping-слой для `Order`. Доменная модель — `docs/models/domain/core/Order.md`;
lifecycle — `docs/lifecycles/Order.md`. Сквозные правила,
управляющие этим маппингом, — `docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/ack-not-runtime-truth.md`,
`docs/rules/external-status-resolution.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт endpoint'ов
конкретного источника — в `docs/integrations/<name>/contracts/`.

Текущие источники: **OKX** (раздел ниже).

## Source-agnostic ядро

### `OrderExternalSnapshot` → `Order`

Snapshot — нормализованный граничный объект; единственное, что
выходит из adapter (`raw-exchange-dto-boundary.md`).

| Snapshot field | Domain | Семантика |
|---|---|---|
| `internalId` | `Order.internalId` | stable client id (сверка) |
| `externalId` | `Order.externalId` | биржевой id (сохраняется при первом известном значении) |
| `type` | `Order.type` | тип ордера (источник-нейтральный) |
| `side` | `Order.side` | `BUY`/`SELL` |
| `externalStatus` | — | raw статус, режим diagnostic; в FSM не используется (`external-status-resolution.md`) |
| `price` | `Order.price` | empty→null |
| `size` | `Order.size` | размер (в единицах источника; для SWAP/FUTURES — контракты) |
| `accumulatedFillSize` | `Order.accumulatedFillSize` | исполнено накопленно |
| `averagePrice` | `Order.averagePrice` | средняя цена исполнения |
| `fee` | `Order.fee` | комиссия |
| `externalCreatedAt` | `Order.externalCreatedAt` | |
| `externalModifiedAt` | `Order.externalModifiedAt` | |
| `attachedAlgoInternalId` | `Order.attachedAlgoInternalId` | top-level attached client id |
| `takeProfitTriggerPrice` (future) | — | top-level TP trigger (для entry-with-attached-SL) |
| `stopLossTriggerPrice` | `Order.stopLossTriggerPrice` | top-level SL trigger |
| `attachedAlgoOrders[]` | `Order.attachedAlgoOrders[]` | список `AttachedAlgoOrder` (см. ниже) |

**Поля планового риска в снапшот не входят и им не перезаписываются.**
`plannedRiskAmount`, `plannedRiskCurrency`, `plannedEntryPrice`,
`plannedSizeContracts`, `plannedContractValue` — **наши** величины,
произведённые риск-преконтролем и доставленные
`CreateOrderCommandPayload`'ом (`docs/components/CreateOrderExecutor.md`
§«Куда пишутся пять чисел»); источник таких фактов не отдаёт, и эхо
рефреша их не трогает — все пять write-once (`updatable = false`).
Строка заведена H5 `DOCS_CHECK_16` структурным свипом: пятая колонка
`orders` вводилась, и mapping-таблица обязана была сказать, что её здесь
нет **намеренно**, — иначе отсутствие читается как пропуск.

### `Domain Order → request`

- **Create**: `Instrument.externalId → instId`; `isolated → tdMode`
  (adapter-константа); `net → posSide` (adapter-константа);
  `Order.side → side`; `Order.type/exec settings → ordType`;
  `Order.size → sz`; `Order.price → px` (если нужен типу);
  `Order.internalId → clOrdId`; `Order.positionReducingOnly →
  reduceOnly`; `Order.attachedAlgoOrders → attachAlgoOrds` (future
  DTO-поле для entry-with-attached-SL). После successful submit
  `ordId` (если вернулся) сохраняется как `Order.externalId`; статус
  — `PENDING` до refresh/search/history.
- **Cancel**: `instId` + одно из `ordId` (предпочтительно) /
  `clOrdId`.

Амендного request-mapping **нет**: домен не амендит
(`docs/decisions/replace-not-amend.md`) — ремоделирование ордера =
REPLACE-оркестрация (cancel-нога → подтверждение терминала с
разбором fill-race → place новой сущности с `replacesInternalId`).
Биржевой amend-контракт OKX задокументирован как поверхность
(`docs/integrations/okx/contracts/order.md` §Amend), доменом не
используется.

Per-item error классифицируется: retryable → `RETRY_PENDING`;
non-retryable → `Order.ERROR`/`Deal.ERROR` (`docs/rules/runtime-error-classification.md`).

### Status resolver (source-agnostic интерфейс)

`externalStatus` (raw из источника) → `Order.Status` через
`OrderExternalStatusResolver` (`docs/components/OrderExternalStatusResolver.md`).
FSM раз не использует raw status (`external-status-resolution.md`).
Таблица соответствий — per-source (см. подразделы).

### Order evidence-cycle / not found

`ExternalNotFoundException` — только после **полного** order
evidence-cycle (специфика per-source — см. подразделы). Пустой ответ
одного endpoint не даёт `MISSING_AFTER_REFRESH`. После полного цикла
без находки → `Order.ERROR` + `MISSING_AFTER_REFRESH` → safety-каскад
(`external-status-resolution.md`).

### `AttachedAlgoOrder` (attached protection)

Один элемент `attachedAlgoOrders[*]` → `AttachedAlgoOrderExternalSnapshot`;
матчинг по `internalId` (client id вложенного TP/SL). Status: `PENDING`
после `SUBMIT_ORDER_COMMAND`; `ACTIVE` только после `REFRESH_ORDER_COMMAND`, если
найден по `internalId` и нет
`failCode`/`failReason`; заполненные `failCode`/`failReason` →
`ERROR`. Missing-policy по статусу parent — `docs/lifecycles/Order.md`.

## OKX

### `OkxOrderResponse` → `OrderExternalSnapshot`

См. инвентарь полей нативной модели —
`docs/models/integrations/okx/OkxOrderResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `clOrdId` | `internalId` |
| `ordId` | `externalId` |
| `ordType` | `type` |
| `side` | `side` |
| `state` | `externalStatus` (raw, не для FSM напрямую) |
| `px` | `price` (empty→null) |
| `sz` | `size` |
| `accFillSz` | `accumulatedFillSize` |
| `avgPx` | `averagePrice` |
| `fee` | `fee` |
| `cTime` | `externalCreatedAt` |
| `uTime` | `externalModifiedAt` |
| `attachAlgoOrds` | `attachedAlgoOrders` |
| `attachAlgoClOrdId` | `attachedAlgoInternalId` |
| `tpTriggerPx` | `takeProfitTriggerPrice` (future) |
| `slTriggerPx` | `stopLossTriggerPrice` |
| `reduceOnly` | **не маппится** — только invariant validation в adapter (см. правила OKX) |

### `OkxOrderResponse.attachAlgoOrds[*]` → `AttachedAlgoOrderExternalSnapshot`

| OKX field | Snapshot field | Комментарий |
|---|---|---|
| `attachAlgoId` | `externalAttachedId` | attached algo id из embedded block |
| `attachAlgoClOrdId` | `internalId` | client id — основной ключ матчинга |
| `algoId` | `externalId` | algo id после trigger/создания |
| `algoClOrdId` | не маппится | diagnostic/future |
| `tpOrdKind` | `externalType` / future | для SL-only можно не использовать |
| `sz` | `size` | string→`BigDecimal` |
| `slTriggerPx` | `stopLossTriggerPrice` | trigger SL |
| `failCode` | `failCode` | если заполнен → attached ERROR |
| `failReason` | `failReason` | диагностика ошибки |

У `attachAlgoOrds` нет полноценного `state` как у ordinary order —
attached резолвится по набору фактов (`docs/lifecycles/Order.md`).

### Конвертация (OKX)

`empty string → null`; numeric string → `BigDecimal`; `state`
остаётся raw в `externalStatus` (резолвинг — позже).

### `Domain Order → OKX request`

См. source-agnostic секцию выше. OKX-специфичные дополнения к create
body (через adapter, не из domain): `ccy` (валюта маржи — для
USDT-SWAP `USDT`); `tag` (метка, `tb`); `stpMode`
(`cancel_maker`/`cancel_taker`/`cancel_both`); `expTime` (header,
ms — «срок годности запроса»).

**Attached TP/SL при create (`attachAlgoOrds[*]`):**
`attachAlgoClOrdId` (client id), `tpTriggerPx`/`tpTriggerRatio`,
`tpOrdPx` (`-1` = market), `tpOrdKind` (`condition`/`limit`, default
`condition`), `slTriggerPx`/`slTriggerRatio`, `slOrdPx` (`-1` =
market), `tpTriggerPxType`/`slTriggerPxType` (`last`/`index`/`mark`,
default `last`), `sz` (для split-TP), `amendPxOnTriggerType` (`0`/`1`
cost-price SL для split). `tpTriggerPx` vs `tpTriggerRatio` —
взаимоисключимо; аналогично SL.

**Amend OKX-specific — доменом не используется** (REPLACE-only,
`docs/decisions/replace-not-amend.md`): амендные поля биржи
(`reqId`/`cxlOnFail`/`pxAmendType`/`attachAlgoOrds[*]` с
`new*`-полями) остаются описанными в контракте поверхности
(`docs/integrations/okx/contracts/order.md` §Amend,
`OkxOrderResponse.md`), в request-mapping домена не входят.
Ремодел attached protection: до fill родителя — REPLACE
родительского ордера вместе с attach-настройками; после fill
attached материализуется в standalone algo —
обычный algo-REPLACE.

### OKX status resolver

| OKX raw `state` | Domain status | closeReason |
|---|---|---|
| `live` | `ACTIVE` | `null` |
| `partially_filled` | `PARTIALLY_COMPLETED` | `null` |
| `filled` | `COMPLETED` | `FILLED` |
| `canceled` | `CANCELED` | context-dependent |
| `mmp_canceled` | `CANCELED` | context-dependent (резолвер closeReason не ставит — как `canceled`; заполняет handler) |
| unknown value | — | бросает `ExternalStatusException(UNKNOWN_EXTERNAL_STATUS)` → safety-каскад |

### OKX evidence-cycle / not found

Полный цикл: `GET /trade/order` → `orders-pending` →
`orders-history` → `orders-history-archive` (если history не
покрывает период). Поиск: есть `externalId` → по `ordId`; нет → по
`clOrdId = internalId`. Цикл обходит `RefreshOrderExecutor` **внутри одной
команды** `REFRESH_ORDER_COMMAND` (обрыв на первом успешном эндпоинте; терминал
`MISSING_AFTER_REFRESH` выносит он же — см.
`docs/decisions/refresh-evidence-cycle-ownership.md`). Order-fill-метрики
(`accFillSz` → `accumulatedFillSize`, `avgPx` → `averagePrice`, `fee`)
приходят готовыми из того же `OkxOrderResponse` — отдельной fill-команды нет.
Доп. факты сделки (`REFRESH_POSITION_COMMAND`) запрашиваются отдельной командой;
`RefreshOrderExecutor` не сопровождает сделку целиком.

### OKX pagination

`after`/`before` — якорь по `ordId` (не времени), `limit ≤ 100`.
Глубокая выкачка: `after = min(ordId)` → следующая страница. История
7 дней дополнительно поддерживает `begin`/`end` по `cTime` (ms).

## Целевые расхождения с текущим кодом (target refactoring)

- `createOrder` не должен принимать `tradeMode`/`positionSide`
  аргументами — `OkxIntegrationService` сам ставит `isolated`/`net`.
- `CreateOrderRequest` должен принимать `reduceOnly` и
  `attachAlgoOrds` (для `ENTRY_ATTACHED_STOP_LOSS`).
- `OrderResponse.state` комментарий: raw статус OKX; pending —
  `live`/`partially_filled`; details/history — `filled`/`canceled`/
  `mmp_canceled` и др. terminal.
- `OrderResponse.reduceOnly` → только adapter invariant validation,
  не в `OrderExternalSnapshot`.
