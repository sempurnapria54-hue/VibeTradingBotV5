# Локальные вопросы: миграция AlgoOrder

## На какой вопрос отвечает этот файл

Что неясно / отложено по миграции архивной сущности AlgoOrder
(standalone algo-order + condition-дерево).

## Контекст

Источник: `.claude-archive/2026-05-21/docs/domain/models/AlgoOrder.md`
+ `.../mapping/okx/OKX_AlgoOrder_mapping.md`. Стратегия: парковать
cross-cutting, создавать владение AlgoOrder.

## Форвард-заметки (мигрируются с владельцем)

- **ALGO-Q1. Подсистема ServiceCommand (algo-flow).** Команды
  `CREATE_ALGO_ORDER → SUBMIT_ALGO_ORDER`, `AMEND_ALGO_ORDER`,
  `CANCEL_ALGO_ORDER`, `REFRESH_ALGO_ORDER`, `REFRESH_ALGO_ORDERS`,
  `REFRESH_ALGO_ORDER_HISTORY` и executors
  (`RefreshAlgoOrderExecutor`, `AmendAlgoOrderExecutor`,
  `CancelAlgoOrderExecutor`) — command-подсистема, отложено.
  Доменная статусная механика, cancel/amend-по-фактам, граница
  refresh-executor — в `docs/lifecycles/AlgoOrder.md`.

- **ALGO-Q2. Resolver / mapper компоненты.**
  `AlgoOrderExternalStatusResolver` (per-exchange,
  `OkxAlgoOrderExternalStatusResolver`), `AlgoOrderMapper`,
  `OkxAlgoOrderTypeResolver` (`conditionType → ordType`). Логика
  захвачена: статус-таблица — в `docs/lifecycles/AlgoOrder.md`;
  mapping/ordType — в `okx-algo-order-mapping.md`. Сами компоненты —
  отложены до command/adapter-миграции.

- **ALGO-Q3. Связь через `DealActionState` + `RuntimeTarget`.**
  `AlgoOrder` не хранит `strategyActionId`; связь:
  `DealActionState(strategyActionId, target =
  RuntimeTarget(ALGO_ORDER, algoOrder.id))`. `CREATE_ALGO_ORDER`
  создаёт `AlgoOrder` + `DealActionState.target` в одной
  транзакции; `AMEND`/`CANCEL` находят target через
  `targetActionKey`. Runtime через `strategyActionId`, не
  `strategyActionKey`. `DealActionState`/`RuntimeTarget` — с Deal/
  Strategy.

- **ALGO-Q4. `SizeCalculator` + `closeFraction` (Strategy/Calc).**
  `closeFraction` живёт в strategy/action sizing intent
  (`StrategyAlgoOrderAction.closeFractionPercents`), не в
  `Condition`. `SizeCalculator`: `closeFractionPercents + Position +
  InstrumentExternalRules → AlgoOrder.size`. Компонент — с расчётным
  слоем (Strategy/Calculation).

- **ALGO-Q5. RiskValidator (shared).** reduce-only checks для partial
  exit. Зафиксировать при миграции RiskValidator.

- **ALGO-Q6. `linkedOrderExternalIds` — будущее использование.**
  OKX `ordId`/`ordIdList` сохраняются как внешний факт; на первом
  этапе не создают `Order`/`DealActionState`, не FSM-target.
  Отдельное решение о применении для fills/recovery/audit —
  принимается позже (продуктовый вопрос на будущее, не блокирует
  миграцию). Зафиксировать при проработке fills/recovery/audit.

## Открытые вопросы

Открытых вопросов, требующих немедленного решения, по AlgoOrder нет.
§18 архива (сводка полей) и §18-impact (checklist изменений кода)
зафиксированы в модели и `okx-algo-order-mapping.md` как целевая
политика и checklist — не нерешённые вопросы. ALGO-Q6 — отложенный
продуктовый вопрос на будущее, помечен в форвард-заметках.
