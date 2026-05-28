# Локальные вопросы: миграция «Оценка рисков»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «Оценка рисков» (локальные вопросы
и форвард-заметки прохода 1).

## Открытые вопросы

- **ОР-Q1. Противоречие closeReason: `RISK_CONTROL` vs `ENTRY_RISK_BLOCKED`.**
  Этот док (§8.1) явно фиксирует: при BLOCKED в PRECHECK без live risk —
  `Deal.closeReason = RISK_CONTROL`, и «Отдельный `ENTRY_RISK_BLOCKED` не
  используем». Но «Аудит и история исполнения» §7.1 пишет: «closeReason
  может быть `ENTRY_RISK_BLOCKED` или другое согласованное значение».
  `docs/lifecycles/Deal.md` уже использует `RISK_CONTROL`. Решённый
  вариант — `RISK_CONTROL` (risk-doc + lifecycle); аудит-док старше и
  даёт черновую формулировку. На проходе 2 не тащить `ENTRY_RISK_BLOCKED`.
  Зеркальная заметка в `tasks-аудит-и-история-исполнения.md`.
- **ОР-Q2. Где владелец правила «scope RiskValidator».** Правило «когда
  вызывается / не вызывается RiskValidator» (risk-creating/increasing/
  weakening да; exit/cleanup/safety/refresh нет) повторяется в 5 доках.
  Сквозное правило → `docs/rules/` (кандидат `risk-validator-scope.md`)
  vs раздел компонента `RiskValidator`. По `rule-source-of-truth.md` —
  скорее сквозной слой. Решить на проходе 2.
- **ОР-Q3. Размещение `RiskCheckCode` (§5).** Большой enum кодов
  (RISK_PER_TRADE_EXCEEDED … CALCULATED_ACTION_INVALID), включая коды,
  которые сам док помечает как safety/invariant, а не risk-policy
  (`PARTIAL_EXIT_NOT_REDUCE_ONLY`, `PARTIAL_EXIT_INCREASES_POSITION`,
  `DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN`). Куда: внутрь RVO
  `RiskCheckResult`/`RiskValidationResult` или отдельный справочник.

## Форвард-заметки

- **ОР-FW1.** §4 — RVO `RiskValidationResult` (+ `RiskDecision`
  ALLOWED/WARNING/BLOCKED), `RiskCheckResult` (+ `RiskCheckStatus`).
  §5 — `RiskCheckCode`. Primary source.
- **ОР-FW2.** §2.3 — компонент `RiskValidator` (сам считает risk-метрики:
  risk amount, risk percent, leverage, margin, notional, SL distance,
  liquidation guard, exposure; не меняет статус, не создаёт команды, не
  вызывает REFRESH_BALANCE/ClientService/adapter). Primary.
- **ОР-FW3.** §6/§7 — компонент `RiskBlockResolver` (+ интерфейс с 6
  параметрами) и RVO `RiskBlockAction` (+ Type: CONTINUE/
  CONTINUE_WITH_WARNING/CLOSE_CANDIDATE_DEAL/MOVE_DEAL_TO_ERROR/
  REQUEST_REFRESH/SKIP_ACTION). Primary.
- **ОР-FW4.** §2.5 «BalanceContainer freshness boundary» + defensive
  BLOCKED (`BALANCE_NOT_FRESH`/`BALANCE_INVALID`) — расширение
  `BalanceContainer.md` + связь FSM/handler.
- **ОР-FW5.** §8 «Политика реакции на BLOCKED» (PRECHECK→CLOSED/
  RISK_CONTROL; пост-live-risk статусы→ERROR; WARNING не блокирует) —
  расширение `docs/lifecycles/Deal.md` (closeReason RISK_CONTROL уже
  частично есть). Дублируется в FSM/Жизненный цикл/Калькуляторы.
- **ОР-FW6.** §10/§11 — границы `CalculationError` vs `RiskValidationResult`
  vs unexpected exception (`INTERNAL_ERROR`/`EXCHANGE_ERROR`/
  `VALIDATION_ERROR`). Пересекается со «Сервисные команды» §11.4.1,
  «Калькуляторы» §20.1. Не дублировать; владелец классификации ошибок —
  единое сквозное правило (см. карту RULE13).
