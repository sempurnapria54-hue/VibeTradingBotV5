# OKX algo-order mapping

## На какой вопрос отвечает этот файл

Как OKX standalone algo-order (request/response) маппится в доменный
`AlgoOrder`, как резолвится статус и какие invariants проверяются.

## Контекст

Exchange-specific mapping для OKX. Доменная модель/статусы — в
`docs/models/core/AlgoOrder.md` и `docs/lifecycles/AlgoOrder.md`,
эта дока их не заменяет. Поля raw response — в
`docs/client/okx/models/OkxAlgoOrderResponse.md`. Command-flow
(`CREATE_ALGO_ORDER → SUBMIT_ALGO_ORDER → REFRESH_*`) — cross-cutting
command-подсистема (форвард-заметки в `.claude/work/questions/tasks/algo-order.md`).

## Endpoints

- **Create** (`SUBMIT_ALGO_ORDER`): `POST /api/v5/trade/order-algo`.
  Permission `Trade`; rate limit 20 req / 2 s по User ID + Instrument
  ID. Body — общие поля (`instId`, `tdMode`, `side`, `ordType`, `sz`,
  `posSide`, `reduceOnly`, `algoClOrdId`) + ordType-specific (см.
  «Create request mapping» и «ordType-specific» ниже).
- **Amend** (`AMEND_ALGO_ORDER`): `POST /api/v5/trade/amend-algos`.
  Permission `Trade`; rate limit как `cancel-algos`. Body — `instId`,
  `algoId` (если известен), `algoClOrdId`, новые trigger/trailing
  значения.
- **Cancel** (`CANCEL_ALGO_ORDER`): `POST /api/v5/trade/cancel-algos`.
  Permission `Trade`; rate limit 20 req / 2 s по User ID + Instrument
  ID. Body — `instId` + `algoId` (обязателен; `algoClOrdId` опц.,
  диагностика). Отказ через `sCode != 0` (algo уже сработал/закрыт/
  отменён/не найден).
- **Details** (`REFRESH_ALGO_ORDER`): `GET /api/v5/trade/order-algo`.
  Permission `Read`. Query: одно из `algoId` (приоритет) /
  `algoClOrdId`; `instId` опц. Ответ — массив `data`, ожидается 0 или
  1 элемент.
- **Pending** (`REFRESH_ALGO_ORDERS`): `GET /api/v5/trade/orders-algo-pending`.
  Permission `Read`. Фильтры по `ordType`, `instType`, `instId`,
  `algoId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.
- **History** (`REFRESH_ALGO_ORDER_HISTORY`):
  `GET /api/v5/trade/orders-algo-history`. Permission `Read`; rate
  limit 20 req / 2 s по User ID. История доступна за последние 3
  месяца. Query: **`ordType` обязателен** (вычисляется из
  `conditionType`); + одно из `state` (`effective`/`canceled`/
  `order_failed`/`partially_failed`) или `algoId`; опц. `instType`,
  `instId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.

ACK любой create/amend/cancel (`sCode=0`) не является runtime truth
(`docs/rules/ack-not-runtime-truth.md`). `CANCEL_ALGO_ORDER` не ставит
`CANCELED`, `AMEND_ALGO_ORDER` не подтверждён — без refresh/search/
history факта. Submit использует stable client id (`internalId →
algoClOrdId`); перед retry — refresh/search по `algoClOrdId` (найден
→ обновить из snapshot; не найден → отправить create).

### Create response (ACK)

`POST /trade/order-algo` возвращает `data[0]` с `algoId`, `algoClOrdId`,
`clOrdId` (deprecated), `sCode`, `sMsg`, `tag`. После successful submit
`algoId` можно сохранить как `AlgoOrder.externalId`; статус — `PENDING`
до refresh/search/history.

### Cancel response (ACK)

`POST /trade/cancel-algos` возвращает `data[0]` с `algoId`,
`algoClOrdId`, `sCode`, `sMsg`. `sCode != 0` — отказ (уже сработал/не
найден/нельзя отменить); финальное `CANCELED` — через refresh.

## ordType-specific create body (по архиву)

- `conditional` — TP **или** SL (если оба в `conditional` в net-режиме,
  биржа может проигнорировать TP — для одновременных TP+SL использовать
  `oco`).
- `oco` — TP и SL вместе; срабатывание одного отменяет другой.
- `trigger` — `triggerPx` + `orderPx` (`-1` = market) + опц.
  `triggerPxType` (default `last`). Активы при постановке обычно **не
  морозятся** (проверка баланса в момент срабатывания).
- `move_order_stop` — trailing: ровно одно из `callbackRatio`
  (`0.01` = 1%) / `callbackSpread` (абсолют); опц. `activePx` — без
  него трейлинг включается сразу. Расчёт триггера: long → min + spread/
  ratio; short → max − spread/ratio.

`closeFraction` (доля позиции при срабатывании, `1` = 100%) на первом
этапе не используем — см. §Create request mapping. Для protective
обычно `reduceOnly=true`; для SWAP рекомендуется `tpTriggerPxType=mark`
(не ловит шпильки `last`).

## ClientService константы

`tdMode = isolated`, `posSide = net` — задаются в `OkxClientService`/
adapter-layer, в `AlgoOrder` не хранятся.

## Create request mapping

| Domain source | OKX field |
|---|---|
| `Instrument.externalId` | `instId` |
| const `isolated` | `tdMode` |
| const `net` | `posSide` |
| `AlgoOrder.direction` (`BUY`/`SELL`) | `side` (`buy`/`sell`) |
| `AlgoOrder.conditionType` (через resolver) | `ordType` |
| `AlgoOrder.size` | `sz` (контракты для SWAP/FUTURES) |
| `AlgoOrder.internalId` | `algoClOrdId` |
| `AlgoOrder.positionReducingOnly` | `reduceOnly` (если OKX поддерживает; для protective обычно `true`) |
| `Condition.trigger.stopLoss.value` / `.type` | `slTriggerPx` / `slTriggerPxType` |
| const (первый этап) | `slOrdPx = -1` (market after trigger) |
| `Condition.trigger.takeProfit.value` / `.type` | `tpTriggerPx` / `tpTriggerPxType` |
| const (первый этап) | `tpOrdPx = -1` (market after trigger) |
| `Condition.trailing.trailingPercents` | `callbackRatio` (`TRAILING_PERCENTS`) |
| `Condition.trailing.trailingStepValue` | `callbackSpread` (`TRAILING_VALUE`) |
| `Condition.trailing.activationPrice.value` | `activePx` (если задан) |

OKX `closeFraction` на первом этапе не используем: размер считает
`SizeCalculator` (`closeFractionPercents + Position +
InstrumentExternalRules → AlgoOrder.size → sz`).

### `conditionType → ordType`

```text
STOP_LOSS / TAKE_PROFIT / PARTIAL_STOP_LOSS / PARTIAL_TAKE_PROFIT -> conditional
OCO_FULL                                                          -> oco
TRAILING_PERCENTS / TRAILING_VALUE                                -> move_order_stop
```

Маппинг односторонний (`conditionType → ordType`): обратный не
делаем, т.к. `conditional` покрывает несколько `ConditionType`.

## Amend / cancel request

- **Amend**: `instId`, `algoId` (если известен), `algoClOrdId`,
  `newSz` и новые trigger/trailing значения (зависят от OKX amend
  DTO).
- **Cancel**: `instId`, `algoId` (предпочтительно) / `algoClOrdId`.
  Если `externalId` неизвестен — сначала refresh/search по
  `algoClOrdId`, затем cancel по найденному `algoId`.

## Response → snapshot → domain

Поля → `AlgoOrderExternalSnapshot` см. `OkxAlgoOrderResponse.md`.
Обновление domain: `internalId` (сверка), `externalId`,
`externalStatus` (raw diagnostic), `failCode`, `externalSize`,
`externalPrice`, `externalTriggerTime`, condition external fields,
`linkedOrderExternalIds` (только сохраняем). `state=canceled` →
`CANCELED` + `closeReason` из cancel intent (не из state).

## Резолвинг статуса

`externalStatus` → `AlgoOrder.Status` (FSM напрямую не использует,
`docs/rules/external-status-resolution.md`). Таблица — в
`docs/lifecycles/AlgoOrder.md` §Резолвинг (`live`/`pause`→ACTIVE;
`partially_effective`→PARTIALLY_COMPLETED; `effective`→COMPLETED/
TRIGGERED; `canceled`→CANCELED; `order_failed`/`partially_failed`/
unknown → `ExternalStatusException` → safety-каскад).

## Exchange invariant checks (adapter-layer)

Сверка (mismatch → `ExternalInvariantViolationException` →
`AlgoOrder.ERROR` + `closeReason = EXCHANGE_INVARIANT_VIOLATION` →
`Deal.ERROR` → `Exchange.HOLD`):

```text
tdMode    == isolated
posSide   == net
side      == AlgoOrder.direction → OKX side
ordType   == AlgoOrder.conditionType → OKX ordType (одностороннe)
reduceOnly == AlgoOrder.positionReducingOnly (если OKX вернул)
```

Размер **не** валидируется как hard invariant (`size` — intent,
`actualSz` — внешний факт, могут отличаться). `actualSide` не
хранится. Если биржа не поддерживает reduce-only/close-only — adapter
может проигнорировать `positionReducingOnly`; unsupported exchange
на первом этапе не блокируем.

## Algo evidence-cycle / not found

`ExternalNotFoundException` — только после **полного** цикла
algo-sources: `GET /trade/order-algo` → `orders-algo-pending` →
`orders-algo-history`. Поиск: есть `externalId` → по `algoId`; нет →
по `algoClOrdId`. Пустой `data=[]` одного endpoint — не финал. После
полного цикла без находки → `AlgoOrder.ERROR` +
`MISSING_AFTER_REFRESH` → safety-каскад
(`docs/rules/external-status-resolution.md`).

## Целевые изменения кода (checklist, не runtime-логика)

`AlgoOrder`: убрать `strategyActionId`/`externalType`/
`externalDirection`/`externalPositionSide`; добавить
`positionReducingOnly`, `PARTIALLY_COMPLETED`, `linkedOrderExternalIds`,
`externalSize`/`externalPrice`/`externalTriggerTime`, строгие
transition-методы. `Condition`: убрать `closeFraction`, оставить
`type`/`trigger`/`trailing`. `*Condition` constructors: убрать
`closeFraction`. `AlgoOrderConditionValidator`: валидировать
`type → trigger/trailing`, не `closeFraction`. `SizeCalculator`:
`closeFractionPercents + position + instrument rules → size`. OKX
create algo mapper: `size → sz`, `closeFraction` не как основной
механизм первого этапа.
