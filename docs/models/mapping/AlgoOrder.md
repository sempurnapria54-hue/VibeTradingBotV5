# AlgoOrder — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменный `AlgoOrder` ложится на нативные модели источников,
нормализуется через `AlgoOrderExternalSnapshot` и как резолвится его
статус.

## Контекст

Mapping-слой для `AlgoOrder`. Доменная модель —
`docs/models/domain/core/AlgoOrder.md`; lifecycle —
`docs/lifecycles/AlgoOrder.md`. Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/ack-not-runtime-truth.md`,
`docs/rules/external-status-resolution.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт endpoint'ов
— `docs/integrations/<name>/contracts/`.

Текущие источники: **OKX**.

## Source-agnostic ядро

### `AlgoOrderExternalSnapshot` → `AlgoOrder`

| Snapshot field | Domain | Семантика |
|---|---|---|
| `internalId` | `AlgoOrder.internalId` | stable client id |
| `externalId` | `AlgoOrder.externalId` | биржевой algo id |
| `externalStatus` | — | raw статус (diagnostic, не FSM) |
| `failCode` | `AlgoOrder.failCode` | внешний код ошибки |
| `externalSize` | `AlgoOrder.externalSize` | фактический размер срабатывания |
| `externalPrice` | `AlgoOrder.externalPrice` | фактическая цена срабатывания |
| `externalTriggerTime` | `AlgoOrder.externalTriggerTime` | время срабатывания |
| `linkedOrderExternalIds` | `AlgoOrder.linkedOrderExternalIds` | связанные ordinary order ids (сохраняем; использование — ALGO-Q6) |
| `condition.trigger.stopLoss.externalValue` | — | внешнее значение SL trigger |
| `condition.trigger.stopLoss.externalType` | — | тип цены SL (`last`/`index`/`mark`) |
| `condition.trigger.takeProfit.externalValue` | — | внешнее значение TP trigger |
| `condition.trigger.takeProfit.externalType` | — | тип цены TP |
| `condition.trailing.activationPrice.externalValue` | — | цена активации trailing |
| `condition.trailing.externalPrice` | — | текущее значение trailing |
| `externalCreatedAt` | `AlgoOrder.externalCreatedAt` | |
| `externalModifiedAt` | `AlgoOrder.externalModifiedAt` | (есть в history) |

Если источник не возвращает тип цены активации trailing,
`condition.trailing.activationPrice.externalType` остаётся `null` —
не нарушение invariant.

### `Domain AlgoOrder → request`

| Domain source | Request field |
|---|---|
| `Instrument.externalId` | `instId` |
| const `isolated` (adapter) | `tdMode` |
| const `net` (adapter) | `posSide` |
| `AlgoOrder.direction` (`BUY`/`SELL`) | `side` |
| `AlgoOrder.conditionType` (через resolver) | `ordType` |
| `AlgoOrder.size` | `sz` (контракты для SWAP/FUTURES) |
| `AlgoOrder.internalId` | client algo id |
| `AlgoOrder.positionReducingOnly` | `reduceOnly` |
| `Condition.trigger.stopLoss.value/.type` | SL trigger / type |
| `Condition.trigger.takeProfit.value/.type` | TP trigger / type |
| `Condition.trailing.trailingPercents` | trailing-percents поле источника |
| `Condition.trailing.trailingStepValue` | trailing-value поле источника |
| `Condition.trailing.activationPrice.value` | trailing activation price |

`closeFraction` (доля позиции при срабатывании) на первом этапе не
используется: размер считает `SizeCalculator`
(`closeFractionPercents + Position + InstrumentExternalRules →
AlgoOrder.size → sz`).

### Status resolver (source-agnostic)

`externalStatus` → `AlgoOrder.Status` через
`AlgoOrderExternalStatusResolver`
(`docs/components/AlgoOrderExternalStatusResolver.md`). FSM raw не
использует. Таблица — per-source.

### Evidence-cycle / not found

`ExternalNotFoundException` — только после полного цикла per-source
(см. подразделы). Пустой `data=[]` одного endpoint — не финал. После
полного цикла без находки → `AlgoOrder.ERROR` + `MISSING_AFTER_REFRESH`.

### Exchange invariant checks (общая идея)

Сверка expected vs actual (mismatch → `ExternalInvariantViolationException`
→ `AlgoOrder.ERROR` + `closeReason = EXCHANGE_INVARIANT_VIOLATION` →
`Deal.ERROR` → `Exchange.HOLD`):

```text
tdMode    == isolated
posSide   == net
side      == AlgoOrder.direction → source side
ordType   == AlgoOrder.conditionType → source ordType (одностороннe)
reduceOnly == AlgoOrder.positionReducingOnly (если источник вернул)
```

Размер не валидируется как hard invariant (`size` — intent,
`externalSize` — внешний факт). `actualSide` не хранится.

## OKX

### `OkxAlgoOrderResponse` → `AlgoOrderExternalSnapshot`

См. инвентарь нативных полей —
`docs/models/integrations/okx/OkxAlgoOrderResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `algoClOrdId` | `internalId` |
| `algoId` | `externalId` |
| `state` | `externalStatus` (raw) |
| `failCode` | `failCode` |
| `actualSz` | `externalSize` |
| `actualPx` | `externalPrice` |
| `triggerTime` | `externalTriggerTime` |
| `ordId` / `ordIdList` | `linkedOrderExternalIds` |
| `slTriggerPx` / `slTriggerPxType` | `condition.trigger.stopLoss.externalValue` / `externalType` |
| `tpTriggerPx` / `tpTriggerPxType` | `condition.trigger.takeProfit.externalValue` / `externalType` |
| `activePx` | `condition.trailing.activationPrice.externalValue` |
| `moveTriggerPx` | `condition.trailing.externalPrice` |
| `cTime` | `externalCreatedAt` |
| `uTime` | `externalModifiedAt` (есть в history) |

`ordType`, `side`, `actualSide`, `tdMode`, `posSide`, `reduceOnly`,
`closeFraction` — не маппятся; проверяются adapter'ом как invariant.

### `conditionType → ordType` (OKX)

```text
STOP_LOSS / TAKE_PROFIT / PARTIAL_STOP_LOSS / PARTIAL_TAKE_PROFIT -> conditional
OCO_FULL                                                          -> oco
TRAILING_PERCENTS / TRAILING_VALUE                                -> move_order_stop
```

Маппинг односторонний (`conditionType → ordType`): обратный не
делаем (`conditional` покрывает несколько `ConditionType`).

### OKX status resolver

`externalStatus` → `AlgoOrder.Status` (FSM напрямую не использует):
- `live`/`pause` → `ACTIVE`;
- `partially_effective` → `PARTIALLY_COMPLETED`;
- `effective` → `COMPLETED`/`TRIGGERED`;
- `canceled` → `CANCELED`;
- `order_failed`/`partially_failed`/unknown →
  `ExternalStatusException` → safety-каскад.

Подробности — `docs/lifecycles/AlgoOrder.md` §Резолвинг.

### OKX request mapping — дополнения

OKX-специфичные поля create body (через adapter): `algoClOrdId` ←
`AlgoOrder.internalId`; `callbackRatio` ←
`Condition.trailing.trailingPercents`; `callbackSpread` ←
`Condition.trailing.trailingStepValue`; `activePx` ←
`Condition.trailing.activationPrice.value` (если задан); SL/TP
параметры — `slTriggerPx`/`slTriggerPxType`/`slOrdPx` (`-1` =
market), `tpTriggerPx`/`tpTriggerPxType`/`tpOrdPx` (`-1` = market).

**Amend**: `instId`, `algoId` (если известен), `algoClOrdId`,
`newSz`, новые trigger/trailing значения.

**Cancel**: `instId`, `algoId` (предпочтительно) / `algoClOrdId`.
Если `externalId` неизвестен — сначала refresh/search по
`algoClOrdId`, затем cancel.

### OKX evidence-cycle / not found

Полный цикл: `GET /trade/order-algo` → `orders-algo-pending` →
`orders-algo-history`. Поиск: есть `externalId` → по `algoId`; нет →
по `algoClOrdId`. Пустой `data=[]` одного endpoint — не финал.

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
