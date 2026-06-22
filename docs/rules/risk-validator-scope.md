# Scope вызова RiskValidator

## На какой вопрос отвечает этот файл

Какое у нас правило: когда `RiskValidator` вызывается, а когда нет.

## Правило

`RiskValidator` вызывается **после** расчёта цены и размера и **до**
создания торговой команды — но только для actions, которые **создают,
увеличивают или ослабляют контроль риска**.

### Вызывается

Для `CREATE_ORDER` / `CREATE_ALGO_ORDER` — включая **place-ногу
REPLACE-действий** (амендных команд нет, ремоделирование — REPLACE,
`docs/decisions/replace-not-amend.md`) — только если конкретное
рассчитанное действие risk-creating / risk-increasing /
risk-weakening:

- entry order, открывающий позицию;
- scaling / pyramiding order, увеличивающий позицию;
- replace, увеличивающий размер замещаемого live order;
- replace, ухудшающий защиту (новая сущность двигает SL дальше от
  входа);
- создание защитного algo-order, не обеспечивающего требуемый контроль
  риска.

Cancel-нога REPLACE отдельной валидации не получает (см. «Не
вызывается»: cancel снимает риск); риск-контроль ремодела целиком —
на place-ноге новой сущности.

### Не вызывается

- **refresh/search/history**: `REFRESH_BALANCE`, `REFRESH_POSITION`,
  `REFRESH_ORDER`, `REFRESH_ALGO_ORDER`, `REFRESH_FILLS` — только обновляют
  факты (evidence-cycle — внутри исполнителя, см.
  `docs/decisions/refresh-evidence-cycle-ownership.md`);
- **cleanup / safety**: `CANCEL_ORDER`, `CANCEL_ALGO_ORDER`,
  `CLOSE_POSITION`, `EXECUTE_KILL_SWITCH` — снимают/локализуют уже
  существующий риск;
- **finalization**: `FINALIZE_DEAL_ENTRY`, `FINALIZE_DEAL_EXIT`,
  `MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR` (lifecycle/system actions без
  `StrategyAction`; retry-state — `DealFinalizationState`,
  `docs/decisions/deal-finalization-state-materialization.md`);
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
