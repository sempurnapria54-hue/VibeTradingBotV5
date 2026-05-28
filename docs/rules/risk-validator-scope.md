# Scope вызова RiskValidator

## На какой вопрос отвечает этот файл

Какое у нас правило: когда `RiskValidator` вызывается, а когда нет.

## Правило

`RiskValidator` вызывается **после** расчёта цены и размера и **до**
создания торговой команды — но только для actions, которые **создают,
увеличивают или ослабляют контроль риска**.

### Вызывается

Для `CREATE_ORDER` / `AMEND_ORDER` / `CREATE_ALGO_ORDER` /
`AMEND_ALGO_ORDER`, только если конкретное рассчитанное действие
risk-creating / risk-increasing / risk-weakening:

- entry order, открывающий позицию;
- scaling / pyramiding order, увеличивающий позицию;
- amend, увеличивающий размер live order;
- amend, ухудшающий защиту (двигает SL дальше от входа);
- создание защитного algo-order, не обеспечивающего требуемый контроль
  риска.

### Не вызывается

- **refresh/search/history**: `REFRESH_BALANCE`, `REFRESH_POSITION`,
  `REFRESH_ORDER`, `REFRESH_PENDING_ORDERS`, `REFRESH_ORDER_HISTORY`,
  `REFRESH_ALGO_ORDER`, `REFRESH_ALGO_ORDERS`,
  `REFRESH_ALGO_ORDER_HISTORY`, `REFRESH_FILLS` — только обновляют факты;
- **cleanup / safety**: `CANCEL_ORDER`, `CANCEL_ALGO_ORDER`,
  `CLOSE_POSITION`, `EXECUTE_KILL_SWITCH` — снимают/локализуют уже
  существующий риск;
- **finalization**: `MARK_DEAL_ERROR`, `MARK_DEAL_CLOSED`,
  `FINALIZE_DEAL_EXIT`;
- **reduce-only partial exit** через `Order`/`AlgoOrder` — это exit-flow.

Для exit / cleanup / safety / reduce-only partial exit handler выполняет
minimal domain / exchange safety + invariant checks (reduce-only intent;
размер ≤ текущей позиции; направление уменьшает позицию; target относится
к текущей `Deal`; есть свежие exchange facts), а не risk-policy validation.

Главное: `RiskValidator` не должен блокировать команды, цель которых —
снять live risk.

## Первоисточник и смежное

Правило сквозное — повторяется в нескольких процессных доках, единого
владельца-сущности нет (`.claude/decisions/rule-source-of-truth.md`).
Поведение самого `RiskValidator` — `docs/components/RiskValidator.md`;
поток и реакция на BLOCKED — `docs/processes/risk-evaluation.md`;
freshness баланса — `docs/rules/market-data-freshness.md` и handler;
partial exit — `docs/rules/no-partial-close.md`.
