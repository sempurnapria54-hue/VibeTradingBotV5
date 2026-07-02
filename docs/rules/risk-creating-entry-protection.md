# Обязательная защита risk-creating входа

## На какой вопрос отвечает этот файл

Какой инвариант системы требует, чтобы risk-creating вход без определимого
стопа не доходил до постановки на бирже.

## Правило (инвариант системы)

**Risk-creating вход** (действие, открывающее или наращивающее позицию)
**без определимого стопа не доходит до постановки** ордера на бирже. `PRECHECK`
блокирует такой вход (закрытие кандидата-сделки с причиной), **до** live risk.

«Определимый стоп» — резолвимая защита на момент постановки: attached SL на
entry-ордере либо иной механизм защиты, дающий цену стопа. Нет резолвимого
стопа у risk-creating входа → вход не выпускается.

Альтернатива (пропустить вход и пометить аномалией постфактум) **отвергнута**
— оставляет живую бесстоповую позицию на бирже; защита обязана быть **до**
live risk, а не после.

## Двусторонний enforcement

Как инвариант, защита двусторонняя:

1. **Блок на входе** (`PRECHECK`, шаг 6). Risk-creating вход без резолвимого
   стопа `RiskValidator` помечает `BLOCKED`
   (`RISK_CREATING_ENTRY_WITHOUT_STOP`) — **без** fail-open allocation-сайзинга
   в обход `RISK_PER_TRADE`. В `PRECHECK` без live risk это не авария:
   `Deal.status = CLOSED`, `closeReason = RISK_CONTROL`
   (`docs/components/PrecheckHandler.md`, `docs/processes/risk-evaluation.md`).
   Выходная проверка `PRECHECK` не выпускает entry без резолвимой защиты;
   `ENTRY_FINALIZED` не уводит в `MANAGING` позицию с live risk без
   подтверждённой **active-like** защиты (attached SL входа в active-like
   состоянии, `Order.hasActiveAttachedProtection()`); иначе — L3-холд
   (`docs/components/EntryFinalizedHandler.md`).
2. **Реакция на нарушение постфактум.** Если бесстоповая risk-creating позиция
   обнаружится **иным путём** (восстановление после перезапуска, фоновый скан)
   — это нарушение инварианта → **реакция уровня 3** error-градации (холд
   инструмента + kill-switch + `AnomalyReport`,
   `docs/rules/error-handling-policy.md`, `docs/rules/instrument-hold.md`).
   Сопутствующий controlled-violation доминирует и поднимает L4
   (`docs/decisions/controlled-violation-exchange-wide-hold.md`). На входе
   нарушение не должно случаться (п.1); п.2 — страховка инварианта.

## Что правило не трогает

- **Reduce-only / закрывающие сделки** — правило не касается: они снимают
  риск, а не создают. Risk-validator-scope их и так не валидирует
  (`docs/rules/risk-validator-scope.md`).
- **Численного хвоста нет** — требование **структурное** (есть резолвимый
  стоп или нет), не калибровочное. Конкретные расстояния стопа — отдельная
  ось (`docs/decisions/per-trade-risk-policy.md`).

## Торговое обоснование

Стоп — конститутив стоп-driven системы: сайзинг обусловлен «входом со
стопом» (`docs/decisions/per-trade-risk-policy.md`), инвариант «ликвидация
за стопом» (`docs/rules/trading-constraints.md`). Risk на сделку нечем
связать без стопа → бесстоповый вход обходит `RISK_PER_TRADE` и оставляет
неограниченную ответственность (risk-of-ruin). Закрывает форвард-долг
шага 5 (`.claude/work/backlog.md` §Шаг 6) и торговую находку TR1
`DOCS_CHECK_1` шага 6.

## Первоисточник и смежное

Правило сквозное (risk-преконтроль + FSM + safety; единого
владельца-сущности нет, `.claude/decisions/rule-source-of-truth.md`).

- Enforcement входа — `docs/components/PrecheckHandler.md`,
  `docs/components/RiskValidator.md`, `docs/rules/risk-validator-scope.md`;
  код — `docs/components/models/RiskCheckResult.md`
  (`RISK_CREATING_ENTRY_WITHOUT_STOP`).
- Защита в `MANAGING`/switch — `docs/components/EntryFinalizedHandler.md`.
- Реакция уровня 3 — `docs/rules/error-handling-policy.md`,
  `docs/rules/instrument-hold.md` (сопутствующий controlled-violation
  доминирует L4 — `docs/rules/exchange-hold.md`,
  `docs/decisions/controlled-violation-exchange-wide-hold.md`).
- Торговый грунт — `docs/decisions/per-trade-risk-policy.md`,
  `docs/rules/trading-constraints.md`.
