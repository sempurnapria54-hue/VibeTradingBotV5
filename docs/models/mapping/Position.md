# Position — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменная `Position` ложится на нативные модели источников,
нормализуется через `PositionExternalSnapshot` и какие invariants
проверяются.

## Контекст

Mapping-слой для `Position`. Доменная модель —
`docs/models/domain/core/Position.md`; lifecycle —
`docs/lifecycles/Position.md`. Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/ack-not-runtime-truth.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт endpoint'ов
— `docs/integrations/<name>/contracts/position.md`. Правила
источника — `docs/integrations/<name>/rules/`.

Текущие источники: **OKX**.

## Source-agnostic ядро

### `PositionExternalSnapshot` → `Position`

| Snapshot field | Domain | Семантика |
|---|---|---|
| `externalId` | `Position.externalId` | биржевой id позиции |
| `externalSize` | `Position.externalSize` | размер по модулю |
| `direction` | `Position.direction` | `LONG`/`SHORT` (из знака) |
| `externalAverageEntryPrice` | `Position.externalAverageEntryPrice` | средняя цена входа |
| `externalMarkPrice` | `Position.externalMarkPrice` | mark price |
| `externalLiquidationPrice` | `Position.externalLiquidationPrice` | цена ликвидации |
| `externalMargin` | `Position.externalMargin` | маржа позиции |
| `externalUnrealizedProfit` | `Position.externalUnrealizedProfit` | нереализованный PnL |
| `externalCreatedAt` | `Position.externalCreatedAt` | |
| `externalModifiedAt` | `Position.externalModifiedAt` | |

### Direction mapping

```text
pos > 0  → Direction.LONG
pos < 0  → Direction.SHORT
externalSize = abs(pos)
```

Если direction ≠ expected direction текущей сделки — нарушение
invariant.

### `IntegrationService` контракт (snapshot / null / exception)

```text
позиция найдена         -> PositionExternalSnapshot
позиция не найдена      -> null (успешный запрос; позиции нет — нормальный
                           closed-on-exchange факт)
API / parse / invariant -> exception
```

Пустой snapshot не создаём; `data=[]` не маппим в snapshot с
null-полями.

### Position not found vs Order/AlgoOrder

Для `Position` not found после успешного запроса по инструменту — не
ошибка (`null` → `CLOSED` + `EXTERNAL_CLOSE`). Отличается от
`Order`/`AlgoOrder`, где not found после evidence-cycle может быть
problem-flow.

### Invariant checks (общая идея)

Перед созданием `PositionExternalSnapshot` adapter проверяет (если
поля есть в источнике):

```text
instId    == expected Instrument.externalId
posSide   == net (если применимо)
mgnMode   == isolated (если применимо)
lever     <= биржевой максимум (externalMaxLeverage)
```

Нарушение invariant → `ExternalInvariantViolationException`
(`posSide != net`, `mgnMode != isolated`, `instId != expected`,
`lever > allowed`, direction нельзя определить) → `Position.status =
ERROR`, `closeReason = EXCHANGE_INVARIANT_VIOLATION`, `Deal → ERROR /
safety-flow`. `lever` не хранится в `Position` /
`PositionExternalSnapshot`. Проверка leverage может выполняться при
создании сделки/расчёте action и дополнительно при `REFRESH_POSITION`.

### Close-position request

`Domain → request` (поля **не** из `Position`, а из
`DealContext`/`Instrument`/Exchange-Account settings/adapter policy):

```text
Instrument.externalId    → instId
adapter const isolated   → mgnMode (если поддерживается источником)
adapter const net        → posSide (если применимо)
settle currency / USDT   → ccy
adapter technical policy → autoCxl
```

Response — ACK, не финальный статус (`ack-not-runtime-truth.md`).

### Close reason при close-position

`CLOSE_POSITION` payload несёт `requestedCloseReason`. Допустимы:
`CLOSED_BY_STRATEGY`, `KILL_SWITCH`, `MANUAL_CLOSE`. Не используются
как requested reason: `EXTERNAL_CLOSE` (закрытие на стороне источника
без команды), `EXCHANGE_INVARIANT_VIOLATION` (problem reason),
`UNKNOWN` (fallback). `RefreshPositionExecutor` не перетирает уже
заполненный `Position.closeReason` (write-once).

## OKX

### `OkxPositionResponse` → `PositionExternalSnapshot`

См. инвентарь — `docs/models/integrations/okx/OkxPositionResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `posId` | `externalId` |
| `pos` | `abs(pos)` → `externalSize`; знак → `direction` |
| `avgPx` | `externalAverageEntryPrice` |
| `markPx` | `externalMarkPrice` |
| `liqPx` | `externalLiquidationPrice` |
| `margin` | `externalMargin` |
| `upl` | `externalUnrealizedProfit` |
| `cTime` | `externalCreatedAt` |
| `uTime` | `externalModifiedAt` |

`instId`, `instType`, `mgnMode`, `posSide`, `lever` — adapter use
(validation / request constants), в `Position` /
`PositionExternalSnapshot` не хранятся.

### OKX response validation (adapter-layer)

```text
instId  == expected Instrument.externalId
posSide == net
mgnMode == isolated
lever   <= биржевой максимум (externalMaxLeverage)
```

### OKX close-position request body

`POST /api/v5/trade/close-position`: `instId`, `mgnMode`, `posSide`,
`ccy` (опц.), `autoCxl` (опц.). Берутся **не** из `Position`, а из
`DealContext` / `Instrument` / Exchange-Account settings /
`OkxIntegrationService` policy:

```text
Instrument.externalId     → instId
adapter constant isolated → mgnMode
adapter constant net      → posSide
settle currency / USDT    → ccy
adapter technical policy  → autoCxl
```

`autoCxl=true` рекомендуется — снижает риск, что активный ордер
снова откроет позицию.
