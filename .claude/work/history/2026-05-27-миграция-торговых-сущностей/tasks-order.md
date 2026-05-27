# Локальные вопросы: миграция Order

## На какой вопрос отвечает этот файл

Что неясно / отложено по миграции архивной сущности Order
(`Order` + embedded `AttachedAlgoOrder`).

## Контекст

Источник: `.claude-archive/2026-05-21/docs/domain/models/Order.md` +
`.../mapping/okx/OKX_Order_mapping.md`. Стратегия: парковать
cross-cutting, создавать владение Order, ссылаться по имени.

## Форвард-заметки (мигрируются с владельцем)

- **ORD-Q1. Подсистема ServiceCommand (order-flow).** Команды
  `CREATE_ORDER → SUBMIT_ORDER`, `AMEND_ORDER`, `CANCEL_ORDER`,
  `REFRESH_ORDER`, `REFRESH_PENDING_ORDERS`, `REFRESH_ORDER_HISTORY`,
  `REFRESH_FILLS` и их executors — command-подсистема, отложено.
  Доменная статусная механика (что обновляет каждый refresh, missing-
  attached policy, ERROR-каскад) — в `docs/lifecycles/Order.md`.
  Retry/recovery executor-boundary и per-item error классификация
  (retryable → `DealActionState.RETRY_PENDING`; non-retryable →
  `Order.ERROR`/`DealActionState.FAILED`/`Deal.ERROR`) — для
  command-миграции.

- **ORD-Q2. Resolver-компоненты.** `OrderExternalStatusResolver`
  (ordinary, status mapping) и `AttachedAlgoOrderStateResolver`
  (attached, по фактам) — компоненты. Их доменная логика захвачена:
  правила резолвинга — в `docs/rules/external-status-resolution.md`;
  OKX status-таблица — в `okx-order-mapping.md`; attached-логика — в
  `docs/lifecycles/Order.md`. Сами `docs/components/<Resolver>.md`,
  а также `OrderMapper` — отложены до command/adapter-миграции.

- **ORD-Q3. Связь `Order ↔ StrategyAction` через `DealActionState`
  + `RuntimeTarget`.** `Order` не хранит `strategyActionId`; связь:
  `DealActionState(dealId, strategyActionId, target =
  RuntimeTarget(ORDER, order.id))`; `AMEND_ORDER`/`CANCEL_ORDER`
  находят target через `targetActionKey` → `DealActionState` →
  `Order`. `DealActionState` (модель) и `RuntimeTarget` (RVO/value) —
  Deal/Strategy-runtime; мигрируются с Deal/Strategy. Статусы
  `DealActionState` (`SUBMITTED`, `RETRY_PENDING`, `FAILED`) —
  упоминаются в order-flow, владелец — Deal.

- **ORD-Q4. RiskValidator (shared).** reduce-only checks для partial
  exit (`Оценка рисков.md`). Зафиксировать при миграции RiskValidator.

- **ORD-Q5. `Exchange` модель/lifecycle.** `Exchange.HOLD` —
  зафиксировано только как правило gating команд
  (`docs/rules/exchange-hold.md`); полная модель/lifecycle `Exchange`
  (статус HOLD среди прочих) в backlog-порядке из 6 сущностей нет —
  отдельная задача.

- **ORD-Q6. CalculationContext (shared RVO).** Внешние правила
  инструмента и fresh market price собираются в `CalculationContext`
  перед расчётом action (поэтому не в `Order`). Создаётся с расчётным
  слоем (Strategy/Calculation).

- **ORD-Q7. История command execution.** «raw command result
  history» проектируется отдельно, не runtime state; не в `Order`.
  Мигрируется с аудитом / command-подсистемой.

## Открытые вопросы

Открытых вопросов, требующих решения, по Order нет. (§17 архива —
«current code gaps / target refactoring» — зафиксированы как целевые
расхождения в `okx-order-mapping.md`, это не нерешённые вопросы, а
известные правки кода под целевую политику.)
