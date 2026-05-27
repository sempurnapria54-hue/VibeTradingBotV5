# AlgoOrder

## На какой вопрос отвечает этот файл

Что это за торговая модель `AlgoOrder` (standalone algo-order):
структура, condition-модель, external snapshot, что хранит и что нет.

Статусы и переходы — в `docs/lifecycles/AlgoOrder.md`.

## Назначение

`AlgoOrder` — standalone algo-order, связанный с `Deal`. Применяется
для standalone stop-loss / take-profit, OCO, trailing stop, partial
exit (reduce-only). Хранит локальный intent, идентификаторы
(`internalId`/`externalId`), доменный статус, сырой внешний статус
(диагностика), параметры условия срабатывания, рассчитанный размер,
факты срабатывания с биржи, diagnostic facts (связанные ordinary
order ids).

`AlgoOrder` **не** является действием стратегии. Связь
`StrategyAction` ↔ `AlgoOrder` — через `DealActionState` →
`RuntimeTarget(entityType = ALGO_ORDER, entityId)`, поэтому
`AlgoOrder` не хранит `strategyActionId`, `strategyActionKey`,
`role`, `level` (механизм связи — Deal/Strategy-runtime, форвард-
заметка в `.claude/work/questions/tasks/algo-order.md`).

## Структура `AlgoOrder`

Java-класс `...core.algo_order.AlgoOrder`, расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор в БД. |
| `dealId` | `Long` | Сделка. |
| `internalId` | `String` | stable client id (OKX `algoClOrdId`). |
| `externalId` | `String` | биржевой id (OKX `algoId`). |
| `status` | `Status` | Доменный статус (см. lifecycle). |
| `closeReason` | `CloseReason` | Причина финализации / ERROR. |
| `conditionType` | `ConditionType` | Денормализованная проекция `condition.type` (обязательна, должна совпадать). |
| `condition` | `Condition` | Условие срабатывания (jsonb; только trigger/trailing). |
| `size` | `BigDecimal` | Рассчитанный materialized размер (результат `SizeCalculator`; для SWAP/FUTURES — контракты). |
| `direction` | `Direction` | `BUY` / `SELL` (closing long → SELL, short → BUY). |
| `positionReducingOnly` | `Boolean` | Доменное намерение: только уменьшать позицию. |
| `externalStatus` | `String` | Сырой статус биржи (OKX `state`) — диагностика, FSM напрямую не использует. |
| `failCode` | `String` | Код ошибки биржи (OKX `failCode`). |
| `externalSize` | `BigDecimal` | Фактический размер срабатывания (OKX `actualSz`) — не исходный `size`. |
| `externalPrice` | `BigDecimal` | Фактическая цена срабатывания (OKX `actualPx`). |
| `externalTriggerTime` | `Instant` | Время срабатывания (OKX `triggerTime`). |
| `linkedOrderExternalIds` | `List<String>` | Связанные ordinary order ids (OKX `ordId`/`ordIdList`) — храним как внешний факт, runtime на них не опирается. |

Доменные методы: `isLive()` (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED),
`isNotLive()`, `toPending/toActive/toPartiallyComplete/toComplete/
toCancel(reason)/toError(reason)` (через строгий `transitTo` с
матрицей переходов — см. lifecycle), `validateConditionProjection()`
(сверяет `conditionType == condition.type`, оба не null).

### Енумы `AlgoOrder`

- **`Direction`**: `BUY`, `SELL`.
- **`Status`**: `CREATED`, `PENDING`, `ACTIVE`, `PARTIALLY_COMPLETED`
  (exchange-driven recovery-status, не целевой сценарий),
  `COMPLETED`, `CANCELED`, `ERROR` (переходы — lifecycle).
- **`CloseReason`**: `TRIGGERED`, `CANCELED_BY_STRATEGY`,
  `REPLACED_BY_STRATEGY` (стратегия заменила другим algo-order),
  `KILL_SWITCH`, `MANUAL_CANCEL`, `MISSING_AFTER_REFRESH`,
  `ORDER_FAILED`, `PARTIALLY_FAILED`, `UNKNOWN_EXTERNAL_STATUS`,
  `EXCHANGE_INVARIANT_VIOLATION`, `UNKNOWN`.

## Condition-модель (разделы `AlgoOrder`)

Дерево условий — разделы внутри `AlgoOrder` по
`.claude/decisions/model-granularity.md` (не самостоятельные
сущности). `Condition` отвечает только за **условие срабатывания**;
размер — в `AlgoOrder.size`; `closeFraction` **не** в `Condition`
(живёт в strategy/action sizing intent, например
`StrategyAlgoOrderAction.closeFractionPercents`, используется
`SizeCalculator`).

- **`Condition`**: `type: ConditionType`, `trigger: Trigger`,
  `trailing: Trailing`. Инвариант: ровно один механизм —
  `trigger XOR trailing`; `type` соответствует заполненным полям
  (SL/TP/OCO/PARTIAL_* → trigger; TRAILING_* → trailing);
  `AlgoOrder.conditionType == condition.type`.
- **`Trigger`**: `stopLoss: TriggerPrice`, `takeProfit: TriggerPrice`
  (null — соответствующая нога не используется).
- **`TriggerPrice`**: `type: TriggerPriceType` (внутренний),
  `value: BigDecimal` (внутреннее значение), `externalType: String`
  (биржевой тип цены), `externalValue: BigDecimal` (биржевое
  значение, может отличаться округлением). На первом этапе SL/TP/OCO
  legs исполняются market-like после trigger (OKX `slOrdPx=-1`/
  `tpOrdPx=-1`); limit-execution после trigger не моделируем.
- **`TriggerPriceType`**: `LAST`, `INDEX`, `MARK`.
- **`Trailing`**: `trailingPercents` (OKX `callbackRatio`),
  `trailingStepValue` (OKX `callbackSpread`), `activationPrice:
  TriggerPrice` (null — активен сразу), `externalPrice` (текущее
  биржевое значение trailing, OKX `moveTriggerPx`).
- **`ConditionType`**: `STOP_LOSS`, `TAKE_PROFIT`, `OCO_FULL`,
  `TRAILING_PERCENTS`, `TRAILING_VALUE`, `PARTIAL_TAKE_PROFIT`,
  `PARTIAL_STOP_LOSS`. OKX `ordType` вычисляет client-layer resolver
  из `conditionType` (`externalType`/`ordType` в `AlgoOrder` не
  хранится) — см. `docs/client/okx/rules/okx-algo-order-mapping.md`.

## External snapshots (разделы `AlgoOrder`)

Нормализованные snapshots для refresh/service layer (не persisted;
разделы по `model-granularity.md`). Raw DTO не выходит за adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`); OKX mapping — в
`okx-algo-order-mapping.md`.

- **`AlgoOrderExternalSnapshot`**: `internalId`, `externalId`,
  `externalStatus`, `failCode`, `externalSize`, `externalPrice`,
  `externalTriggerTime`, `condition: ConditionExternalSnapshot`,
  `linkedOrderExternalIds`.
- **`ConditionExternalSnapshot`** → `TriggerExternalSnapshot`
  (`stopLoss`, `takeProfit`: `TriggerPriceExternalSnapshot` с
  `externalType`/`externalValue`) + `TrailingExternalSnapshot`
  (`activationPrice: TriggerPriceExternalSnapshot`, `externalPrice`).
- В snapshot **не** хранятся: `externalType`/`ordType`,
  `externalDirection`/`side`, `externalPositionSide`/`posSide`,
  `actualSide`, `reduceOnly`, `tdMode` — это client/adapter
  validation или raw audit, не domain snapshot.

## Что AlgoOrder не хранит

`strategyActionId`/`strategyActionKey`/`role`/`level` (в
`DealActionState`), `externalType`/`ordType`, `externalDirection`/
`side`, `externalPositionSide`/`posSide`, `tdMode`, `reduceOnly`
(как факт), `actualSide`, `closeFraction`. OKX `tdMode=isolated`/
`posSide=net` — константы `OkxClientService`. `reduceOnly` —
проверяется adapter-layer как invariant, не хранится.

## Отличие от attached protection

`AttachedAlgoOrder` (внутри `Order`) — embedded protection parent
order; standalone `AlgoOrder` — отдельная runtime-сущность. Attached
protection **не** материализуется автоматически в standalone
`AlgoOrder`, даже если биржа вернула algo identifiers внутри attached
snapshot. Standalone `AlgoOrder` создаётся только отдельным
`StrategyAlgoOrderAction` через `CREATE_ALGO_ORDER`.
