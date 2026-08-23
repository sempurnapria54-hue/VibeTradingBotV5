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

**Входа среди применений нет** — все типы условия protective/closing
(`ConditionType`: семь значений, входного не существует; подтверждено по
коду, `RISK-Q4`). **Условие возврата `RISK-Q4`:** появление входного
condition-type (условный вход алго-ордером, входной
`StrategyAlgoOrderAction`) переоткрывает состав таблиц планового риска и
его операндов — сегодня они живут только в `orders`
(`docs/models/domain/core/Order.md` §«Плановый риск и его операнды»).

`AlgoOrder` **не** является действием стратегии. Связь
`StrategyAction` ↔ `AlgoOrder` — через `DealActionState` →
`RuntimeTarget(entityType = ALGO_ORDER, entityId)`, поэтому
`AlgoOrder` не хранит `strategyActionId`, `strategyActionKey`,
`role`, `level` (механизм связи —
`docs/models/domain/other/DealActionState.md`).

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
| `replacesInternalId` | `String` | `internalId` предшественника в цепочке REPLACE (nullable; append-only след — обратная ссылка не хранится, выводится запросом). См. `docs/decisions/replace-not-amend.md`. |
| `externalStatus` | `String` | Сырой статус биржи (OKX `state`) — диагностика, FSM напрямую не использует. |
| `failCode` | `String` | Код ошибки биржи (OKX `failCode`). |
| `externalSize` | `BigDecimal` | Фактический размер срабатывания (OKX `actualSz`) — не исходный `size`. |
| `externalPrice` | `BigDecimal` | Фактическая цена срабатывания (OKX `actualPx`). |
| `externalTriggerTime` | `Instant` | Время срабатывания (OKX `triggerTime`). |
| `linkedOrderExternalIds` | `List<String>` | Связанные ordinary order ids (OKX `ordId`/`ordIdList`) — храним как внешний факт, runtime на них не опирается. |

Доменные методы: `isLive()` (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED),
`toPending/toActive/toComplete/toCancel(reason)/toError(reason)` (через
строгий `transitTo` с матрицей переходов — см. lifecycle),
`validateConditionProjection()` (сверяет `conditionType == condition.type`,
оба не null). Статус `PARTIALLY_COMPLETED` достижим только по матрице
переходов (exchange-driven recovery), отдельного transition-хелпера нет.

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

### Плановый риск и его операнды — дом не здесь (`RISK-Q4` закрыт)

Плановый риск ноги (`plannedRiskAmount`, `plannedRiskCurrency` — переехали
на ногу H6/H11 `DOCS_CHECK_15`) и его операнды (`plannedEntryPrice`,
`plannedSizeContracts`) — атрибуты **ноги входа**, а входной тропы
алго-ордером не существует (§Назначение): дом — **только `Order`**
(`docs/models/domain/core/Order.md` §«Плановый риск и его операнды»);
`algo_orders` этих колонок не получает — это была бы мёртвая
четвёрка с живым именем, читающаяся как «тропа есть». Прежняя
формулировка «если вход исполнился алго-ордером» снята вместе с
посылкой; условие возврата — §Назначение.

**Ссылки на ногу у `AlgoOrder` нет, и это несущий факт** (H4
`DOCS_CHECK_16`, верифицировано по `AlgoOrder.java`): связь — только
`dealId`. Отсюда standalone-алго-сущность **не может** быть носителем
операнда `stop_i` конкретной ноги входа: «защита **той же** ноги»
standalone-сущностью не адресуема ничем. Поэтому форма защиты **доборной**
ноги сужена до собственного attached SL
(`docs/rules/risk-creating-entry-protection.md` §«Форма защиты у доборной
ноги»), а финализатор резолвит `stop_i` только через
`Order.attachedAlgoOrders`
(`docs/components/FinalizeDealExitExecutor.md` §epsilon). Заводить обратную
ссылку `AlgoOrder → Order` под это **не требуется**: привязка к ноге уже
есть у attached-коллекции, а вторая связь дублировала бы её.

### Поля фактического срабатывания — есть, и они операнд калибровки

`externalPrice` (`actualPx`), `externalSize` (`actualSz`),
`externalTriggerTime` — **фактические** факты срабатывания, не заявленные.
`externalPrice` по стоповым типам условия (`STOP_LOSS` / `OCO_FULL` /
`PARTIAL_STOP_LOSS` при `closeReason = TRIGGERED`) — **основной операнд
калибровки запаса на проскок**, а второй операнд (уровень стопа) живёт на
**той же** строке в `condition.trigger.stopLoss.value`, поэтому смешения
partial-выходов и не-стоповых закрытий не возникает (H21 `DOCS_CHECK_11`;
`docs/models/domain/core/Position.md` §«Цена фактического выхода»).
У `AttachedAlgoOrder` аналога **нет** — там только заявленный
`stopLossTriggerPrice`.

**Хвост `integrator`:** означает ли `actualPx` цену исполнения
сработавшего ордера или цену его выставления после триггера
(`.claude/tests/source-api/okx/plan.md` §M15.7).

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
  хранится) — см. `docs/models/mapping/AlgoOrder.md`.

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
`posSide=net` — константы `OkxIntegrationService`. `reduceOnly` —
проверяется adapter-layer как invariant, не хранится.

## Отличие от attached protection

`AttachedAlgoOrder` (внутри `Order`) — embedded protection parent
order; standalone `AlgoOrder` — отдельная runtime-сущность. Attached
protection **не** материализуется автоматически в standalone
`AlgoOrder`, даже если биржа вернула algo identifiers внутри attached
snapshot. Standalone `AlgoOrder` создаётся только отдельным
`StrategyAlgoOrderAction` через `CREATE_ALGO_ORDER_COMMAND`.
