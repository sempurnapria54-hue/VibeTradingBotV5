# Локальные вопросы: миграция «FSM этапы сделки»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «FSM этапы сделки» (локальные
вопросы и форвард-заметки прохода 1).

## Открытые вопросы

- **FSM-Q1. Гранулярность handler-компонентов.** Док даёт детальный
  per-status регламент (PRECHECK, ENTRY_SUBMITTED, ENTRY_FINALIZED,
  PROTECTION_SWITCHED, MANAGING, EXIT_PENDING, CLOSED, ERROR). По
  `fsm-handler-as-component.md` каждый handler — компонент
  (`docs/components/<Handler>.md`). Развилка: 7 отдельных файлов-handler
  (`PrecheckHandler`…`ErrorHandler`) или один файл-свод. CLOSED/
  EMERGENCY_CLOSED handlers не имеют — проверить, как отражать.
- **FSM-Q2. Куда кладутся per-status списки источников/команд/проверок.**
  Для каждого статуса: «источники информации», «входные/рабочие/выходные
  проверки», «возможные ServiceCommand», «допустимые StrategyStep». Это
  поведение исполнителя → handler-компонент. Механика самого статуса
  (назначение, переходы, recovery) → раздел lifecycle. Граница «что в
  handler vs что в lifecycle» — уточнить на проходе 2 (см.
  `classify-type.md` §FSM-handler vs раздел lifecycle).

## Форвард-заметки

- **FSM-FW1.** §1 «Три типа проверок FSM handler» (входные / рабочая
  логика / выходные) — общая конструкция handler'а. По `classify-type.md`
  это раздел оркестратора `docs/components/DealStateMachine.md`, не
  lifecycle и не отдельный handler.
- **FSM-FW2.** §13.1 «Общий runtime-flow одного StrategyAction»
  (16-шаговая схema) — кандидат в процесс расчёта/исполнения action либо
  раздел `DealStateMachine`. Дублирует материал «Калькуляторы» /
  «Оценка рисков» / «Сервисные команды» — не воспроизводить, ссылаться.
- **FSM-FW3.** §5.4.1 «Missing attached protection» — policy по статусу
  parent `Order` (CREATED/PENDING/ACTIVE/COMPLETED/CANCELED/ERROR).
  Владелец — `docs/models/core/Order.md` (AttachedAlgoOrder) +
  `ErrorHandler`/`EntrySubmittedHandler`. Расширение уже мигрированного
  `Order.md`.
- **FSM-FW4.** §3.1 «Balance freshness как precondition» + defensive
  BLOCKED (`BALANCE_NOT_FRESH`/`BALANCE_INVALID`) — связано с risk-layer
  (`RiskValidator`) и `BalanceContainer`. Расширение `BalanceContainer.md`.
- **FSM-FW5.** §12 «Общие правила recovery» (forward recovery по фактам,
  не равен обычному переходу) — раздел lifecycle `Deal` (recovery) +
  `DealStateMachine`. Частично уже в `docs/lifecycles/Deal.md` (Restart/
  recovery) — дополнить, не дублировать.
- **FSM-FW6.** §13.5–13.9 (реакция FSM на BLOCKED, calculation errors,
  EXCHANGE_ERROR, unexpected exceptions) — дублирует «Оценка рисков» /
  «Сервисные команды» / «Калькуляторы». Владельцы там; здесь вторично.
