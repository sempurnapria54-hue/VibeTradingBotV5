# Прогресс: миграция Order

## На какой вопрос отвечает этот файл

На каком шаге миграция архивной сущности Order и как
классифицирован каждый фрагмент.

## Статус

**Завершено.** Источник:
`.claude-archive/2026-05-21/docs/domain/models/Order.md` +
`.../mapping/okx/OKX_Order_mapping.md`.

`Order` + embedded `AttachedAlgoOrder` — обе со своими статусными
FSM. `AttachedAlgoOrder` — раздел внутри `Order` по
`model-granularity.md`; обе FSM — в одном `docs/lifecycles/Order.md`.

## Созданные / изменённые файлы

- `docs/models/core/Order.md` — модель (Order + AttachedAlgoOrder +
  external snapshots) (создан).
- `docs/lifecycles/Order.md` — lifecycle обеих FSM (создан).
- `docs/rules/external-status-resolution.md` — сквозное правило
  (создан; переиспользуется AlgoOrder/Deal).
- `docs/rules/exchange-hold.md` — сквозное правило (создан).
- `docs/client/okx/models/OkxOrderResponse.md` — поля OKX (создан).
- `docs/client/okx/rules/okx-order-mapping.md` — OKX mapping (создан).
- `.claude/work/questions/tasks/order.md` — форвард-заметки (создан).

Переиспользованы (без изменений): `docs/rules/no-partial-close.md`,
`docs/rules/ack-not-runtime-truth.md`,
`docs/rules/raw-exchange-dto-boundary.md`.

## Отчёт по фрагментам

Область у всех — **продукт**.

| # | Фрагмент | Тип | Размещение / диспозиция |
|---|---|---|---|
| Ф1 | Назначение `Order` (ordinary order в Deal, что хранит) | модель | `Order.md` §Назначение |
| Ф2 | Связь Order↔StrategyAction через DealActionState/RuntimeTarget; не хранит strategyActionId/role | модель + Deal/Strategy-runtime | Инвариант «не хранит» → `Order.md`; механизм связи → ORD-Q3 |
| Ф3 | Атрибуты `Order` | модель | `Order.md` §Структура Order |
| Ф4 | Енумы `Order` (Status, Type, CloseReason) | модель + lifecycle | Перечень → `Order.md` §Енумы; механика статусов → lifecycle |
| Ф5 | Методы `Order` (isLive/toCancel/toComplete/toError) | модель | `Order.md` §Структура (методы) |
| Ф6 | `positionReducingOnly` как доменное намерение | модель | `Order.md` §positionReducingOnly |
| Ф7 | Атрибуты + енумы + методы `AttachedAlgoOrder` | модель (раздел) | `Order.md` §AttachedAlgoOrder (по `model-granularity.md`) |
| Ф8 | Матрица переходов `AttachedAlgoOrder.canTransitionTo` | lifecycle | `Order lifecycle` §AttachedAlgoOrder матрица |
| Ф9 | `OrderExternalSnapshot` | модель (раздел, снапшот) | `Order.md` §External snapshots |
| Ф10 | `AttachedAlgoOrderExternalSnapshot` | модель (раздел, снапшот) | `Order.md` §External snapshots |
| Ф11 | `Order.Status` semantics (таблица active/final/live risk) | lifecycle | `Order lifecycle` §Order.Status |
| Ф12 | `AttachedAlgoOrder.Status` semantics | lifecycle | `Order lifecycle` §AttachedAlgoOrder.Status |
| Ф13 | `OrderExternalStatusResolver` контракт + не делает | компонент + сквозное правило | Правило (FSM не использует externalStatus напрямую, resolver-контракт) → `external-status-resolution.md`; компонент → ORD-Q2 |
| Ф14 | Unknown external status → exception → ERROR/Deal ERROR/Exchange HOLD | сквозное правило | `docs/rules/external-status-resolution.md` (safety-каскад) |
| Ф15 | OKX status mapping (live/partially_filled/filled/canceled/mmp_canceled/unknown) | правило биржи | `okx-order-mapping.md` §Резолвинг статуса |
| Ф16 | Exchange HOLD blocks/allows commands | сквозное правило | `docs/rules/exchange-hold.md` |
| Ф17 | Attached protection resolving по фактам, ключ матчинга internalId | lifecycle | `Order lifecycle` §Attached resolving |
| Ф18 | PENDING vs ACTIVE для attached | lifecycle | `Order lifecycle` §PENDING vs ACTIVE |
| Ф19 | Missing attached protection policy (по статусу parent) | lifecycle | `Order lifecycle` §Missing attached |
| Ф20 | Exchange facts обновляющие Order (REFRESH_ORDER/PENDING/HISTORY/FILLS) | lifecycle + команда | Что обновляет каждый → `Order lifecycle`; команды/executors → ORD-Q1 |
| Ф21 | `ExternalNotFoundException` / evidence-cycle / MISSING_AFTER_REFRESH | сквозное правило + правило биржи | Правило/каскад → `external-status-resolution.md`; OKX endpoints цикла → `okx-order-mapping.md` |
| Ф22 | Что не хранит Order (strategyActionId, tdMode/posSide, market price, command history, reduceOnly-факт) | модель | Позитив → `Order.md` §Что не хранит; отрицания свёрнуты; market price → ORD-Q6; history → ORD-Q7 |
| Ф23 | OKX endpoints (create/amend/cancel/details/pending/history/archive) | правило биржи | `okx-order-mapping.md` §Endpoints |
| Ф24 | OKX request DTO (Create/Amend/Cancel) + search params | правило биржи | `okx-order-mapping.md` §Domain Order → request (search-params свёрнуты в endpoints/маппинг) |
| Ф25 | OKX ClientService константы (tdMode=isolated, posSide=net) | правило биржи | `okx-order-mapping.md` §ClientService константы |
| Ф26 | OrderResponse → OrderExternalSnapshot mapping | правило биржи + модель API | `okx-order-mapping.md` + `OkxOrderResponse.md` |
| Ф27 | attachAlgoOrds → AttachedAlgoOrderExternalSnapshot mapping | модель API биржи | `OkxOrderResponse.md` §attachAlgoOrds |
| Ф28 | reduce-only invariant (positionReducingOnly vs reduceOnly) | правило биржи | `okx-order-mapping.md` §reduce-only invariant (`rule-source-of-truth.md`: маппинг-слой) |
| Ф29 | ACK policy create/amend/cancel | сквозное правило + правило биржи | `ack-not-runtime-truth.md` + `okx-order-mapping.md` §Endpoints |
| Ф30 | Attached protection остаётся embedded; не материализуется в standalone AlgoOrder | модель (инвариант) | `Order.md` §AttachedAlgoOrder; standalone-создание → форвард для AlgoOrder |
| Ф31 | §17 current code gaps / target refactoring | правило биржи (целевые расхождения) | `okx-order-mapping.md` §Целевые расхождения |

## Итог по Order

- Размещено в `docs/`: 6 новых файлов (модель, lifecycle, 2 сквозных
  правила, 2 client/okx); 3 ранее созданных сквозных правила
  переиспользованы.
- Свёрнуты к позитиву отрицательные перечни (Ф2, Ф22) по
  `negative-statements-not-fixated.md`.
- В форвард-заметки: ORD-Q1…Q7 (command-подсистема, resolver/mapper,
  DealActionState/RuntimeTarget, RiskValidator, Exchange-модель,
  CalculationContext, command history).
- Продуктовых открытых вопросов по Order нет.
