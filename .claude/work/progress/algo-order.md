# Прогресс: миграция AlgoOrder

## На какой вопрос отвечает этот файл

На каком шаге миграция архивной сущности AlgoOrder и как
классифицирован каждый фрагмент.

## Статус

**Завершено.** Источник:
`.claude-archive/2026-05-21/docs/domain/models/AlgoOrder.md` +
`.../mapping/okx/OKX_AlgoOrder_mapping.md`.

`AlgoOrder` со статусной FSM + condition-дерево (Condition, Trigger,
TriggerPrice, TriggerPriceType, Trailing, ConditionType) — разделы
модели по `model-granularity.md`. FSM → lifecycle.

## Созданные / изменённые файлы

- `docs/models/core/AlgoOrder.md` — модель (создан).
- `docs/lifecycles/AlgoOrder.md` — lifecycle (создан).
- `docs/client/okx/models/OkxAlgoOrderResponse.md` — поля OKX
  (создан).
- `docs/client/okx/rules/okx-algo-order-mapping.md` — OKX mapping
  (создан).
- `.claude/work/questions/tasks/algo-order.md` — форвард-заметки
  (создан).

Переиспользованы (без изменений): `docs/rules/external-status-resolution.md`,
`docs/rules/exchange-hold.md`, `docs/rules/ack-not-runtime-truth.md`,
`docs/rules/no-partial-close.md`, `docs/rules/raw-exchange-dto-boundary.md`.

## Отчёт по фрагментам

Область у всех — **продукт**.

| # | Фрагмент | Тип | Размещение / диспозиция |
|---|---|---|---|
| Ф1 | Назначение `AlgoOrder` (SL/TP/OCO/trailing/partial exit) | модель | `AlgoOrder.md` §Назначение |
| Ф2 | Связь через DealActionState/RuntimeTarget; не хранит strategyActionId | модель + Deal/Strategy-runtime | Инвариант → `AlgoOrder.md`; механизм → ALGO-Q3 |
| Ф3 | Атрибуты `AlgoOrder` | модель | `AlgoOrder.md` §Структура |
| Ф4 | Енумы (Status, CloseReason, Direction) | модель + lifecycle | Перечень → `AlgoOrder.md`; механика → lifecycle |
| Ф5 | Методы + строгий transitTo + isTransitionForbidden | lifecycle | `AlgoOrder lifecycle` §Матрица переходов |
| Ф6 | `conditionType` денормализованная проекция + validateConditionProjection | модель | `AlgoOrder.md` §Структура / Condition-модель |
| Ф7 | `positionReducingOnly` доменное намерение | модель | `AlgoOrder.md` §Структура; reduce-only mapping → `okx-algo-order-mapping.md` |
| Ф8 | `size` materialized; closeFraction не в Condition | модель | `AlgoOrder.md` §Condition-модель; SizeCalculator → ALGO-Q4 |
| Ф9 | Condition / Trigger / TriggerPrice / TriggerPriceType / Trailing | модель (разделы) | `AlgoOrder.md` §Condition-модель (по `model-granularity.md`) |
| Ф10 | `ConditionType` (7 значений) | модель (раздел) | `AlgoOrder.md` §Condition-модель; →ordType → `okx-algo-order-mapping.md` |
| Ф11 | Статусы и live semantics (таблица) | lifecycle | `AlgoOrder lifecycle` §Статусы |
| Ф12 | `AlgoOrderExternalSnapshot` + Condition*ExternalSnapshot дерево | модель (разделы, снапшоты) | `AlgoOrder.md` §External snapshots |
| Ф13 | Exchange exceptions policy (ExternalStatus/Invariant/NotFound) | сквозное правило | `docs/rules/external-status-resolution.md` (каскад) + lifecycle ERROR-переходы |
| Ф14 | Status resolver (контракт + не делает) | компонент + сквозное правило | Контракт → lifecycle/`external-status-resolution.md`; компонент → ALGO-Q2 |
| Ф15 | OKX status mapping (live/pause/effective/canceled/failed/unknown) | правило биржи | `okx-algo-order-mapping.md` + lifecycle §Резолвинг |
| Ф16 | Client/adapter invariant validation (tdMode/posSide/side/ordType/reduceOnly) | правило биржи | `okx-algo-order-mapping.md` §Invariant checks |
| Ф17 | Refresh/recovery, algo evidence-cycle, что обновляет refresh | lifecycle + команда | Граница/что обновляет → lifecycle; evidence-cycle endpoints → `okx-algo-order-mapping.md`; команды → ALGO-Q1 |
| Ф18 | Cancel semantics (ACK не truth, по фактам) | lifecycle + сквозное правило | `AlgoOrder lifecycle` §Cancel/amend + `ack-not-runtime-truth.md` |
| Ф19 | Amend semantics | lifecycle + сквозное правило | `AlgoOrder lifecycle` §Cancel/amend |
| Ф20 | Связь с ordinary Order (linkedOrderExternalIds) | модель + будущий вопрос | `AlgoOrder.md` §Структура; использование → ALGO-Q6 |
| Ф21 | Отличие от attached protection (standalone vs embedded) | модель (инвариант) | `AlgoOrder.md` §Отличие от attached |
| Ф22 | Impact на код (§18) | правило биржи (checklist) | `okx-algo-order-mapping.md` §Целевые изменения |
| Ф23 | OKX endpoints (create/amend/cancel/details/pending/history) | правило биржи | `okx-algo-order-mapping.md` §Endpoints |
| Ф24 | OKX request mapping + conditionType→ordType | правило биржи | `okx-algo-order-mapping.md` §Create request / conditionType→ordType |
| Ф25 | OKX search params DTO | правило биржи | `okx-algo-order-mapping.md` (свёрнуто в endpoints/маппинг) |
| Ф26 | OKX response → snapshot mapping (вкл. condition external) | правило биржи + модель API | `okx-algo-order-mapping.md` + `OkxAlgoOrderResponse.md` |
| Ф27 | Поля не в snapshot/домене (ordType/side/actualSide/tdMode/reduceOnly/closeFraction) | модель API биржи | `OkxAlgoOrderResponse.md` §Поля для validation |
| Ф28 | Submit semantics (stable client id, refresh перед retry) | lifecycle + команда | `okx-algo-order-mapping.md` §Endpoints (submit); команды → ALGO-Q1 |
| Ф29 | Сводка полей (§18 храним/не храним) | модель | `AlgoOrder.md` §Что не хранит + §Структура |

## Итог по AlgoOrder

- Размещено в `docs/`: 4 новых файла (модель, lifecycle, 2
  client/okx); 5 сквозных правил переиспользованы без изменений.
- Свёрнуты к позитиву отрицательные перечни (Ф2, Ф27, Ф29).
- В форвард-заметки: ALGO-Q1…Q6 (command-подсистема, resolver/mapper,
  DealActionState/RuntimeTarget, SizeCalculator/closeFraction,
  RiskValidator, linkedOrderExternalIds-future).
- Продуктовых открытых вопросов, блокирующих миграцию, нет; ALGO-Q6
  — отложенный вопрос на будущее.
