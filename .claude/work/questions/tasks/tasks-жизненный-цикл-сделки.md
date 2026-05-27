# Локальные вопросы: миграция «Жизненный цикл сделки»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «Жизненный цикл сделки» (локальные
вопросы и форвард-заметки прохода 1).

## Открытые вопросы

- **ЖЦ-Q1. Процесс vs lifecycle: как разделить.** Архивный док — общая
  карта процесса сопровождения сделки (4 больших процесса, зоны
  ответственности, итоговый flow). При этом статусная механика `Deal`
  уже мигрирована в `docs/lifecycles/Deal.md`. Развилка: завести
  `docs/processes/<deal-lifecycle>.md` для оркестрации (jobs → FSM →
  calculator → command → executor) и не дублировать статусную механику,
  ссылаясь на lifecycle; либо распределить иначе. Решить на проходе 2.
- **ЖЦ-Q2. Противоречие по `PositionContext`.** §5.3 (этот док) явно
  исключает `PositionContext` из `DealContext` (в рамках одной `Deal` —
  максимум одна `Position`). Но модель `CalculationContext` в
  «Калькуляторы действий стратегии» §4 содержит поле
  `private PositionContext positionContext;`. Материализация
  `PositionContext` под вопросом. См. зеркальную заметку в
  `tasks-калькуляторы-действий-стратегии.md`. Кандидат в общий открытый
  вопрос.
- **ЖЦ-Q3. `DealActionState` — core или other.** Персистентная
  операционная модель (id, `UNIQUE(deal_id, strategy_action_id)`,
  status-enum, наследует `Retryable`). Не торговая бизнес-сущность в
  смысле PnL, но тесно связана с сопровождением сделки. По
  `classify-type` лежит ближе к `docs/models/other/`, но возможен спор
  за core. Также кандидат на собственный lifecycle (status-enum
  `PLANNED → CREATED → SUBMITTED → COMPLETED / RETRY_PENDING / FAILED /
  SKIPPED`). Решить тип и наличие lifecycle на проходе 2.

## Форвард-заметки

- **ЖЦ-FW1.** §1.1 «Архитектурные инварианты lifecycle/FSM» — крупный
  свод инвариантов, многие из которых принадлежат другим владельцам
  (Position live risk → `Position`; RiskValidator scope → risk-rule;
  partial close → `no-partial-close.md`; ACK → `ack-not-runtime-truth.md`).
  При миграции разнести по владельцам через `rule-source-of-truth.md`,
  сам свод не воспроизводить целиком.
- **ЖЦ-FW2.** §5 содержит полную модель `DealContext` (RVO) с
  Java-полями (`deal`, `exchange`, `instrument`, `strategyDetail`,
  `balanceContainer`, `actionStates`) — primary source для
  `docs/components/models/DealContext.md`. §5.3 «Что специально не входит
  в DealContext» — отрицания; по `negative-statements-not-fixated.md`
  фиксировать позитив (что входит) + указатель, где живут исключённые
  данные (CalculationContext собирается в рантайме).
- **ЖЦ-FW3.** §6 — `CalculationContext` (краткое), §7 — `DealActionState`
  (Java-модель + enums), §8 — `ServiceCommand` (тезисно). Полные версии в
  «Калькуляторы» / «Сервисные команды»; здесь — вторичные упоминания.
- **ЖЦ-FW4.** §9 даёт per-status источники/проверки/команды (PRECHECK…
  ERROR). Более детальный per-status регламент — в «FSM этапы сделки»;
  владельцы — handler-компоненты + lifecycle. Здесь не primary.
- **ЖЦ-FW5.** §11 (AnomalyJob/kill-switch), §12 (торговые ограничения
  проекта) — кандидаты: `AnomalyJob`/`ReconciliationJob` компоненты;
  торговые ограничения → сквозное правило `docs/rules/`.
- **ЖЦ-FW6.** §14 (Q2–Q8 дополнение про risk/calculation/command-flow)
  дублирует решения из «Оценка рисков» / «Калькуляторы» / «Сервисные
  команды». При миграции — не дублировать, ссылаться на владельцев.
