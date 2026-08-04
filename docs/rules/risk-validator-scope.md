# Scope вызова RiskValidator

## На какой вопрос отвечает этот файл

Какое у нас правило: когда `RiskValidator` вызывается, а когда нет.

## Правило

`RiskValidator` вызывается **после** расчёта цены и размера и **до**
создания торговой команды — но только для actions, которые **создают,
увеличивают или ослабляют контроль риска**.

### Вызывается

Для `CREATE_ORDER_COMMAND` / `CREATE_ALGO_ORDER_COMMAND` — включая **place-ногу
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

- **refresh/search/history**: `REFRESH_BALANCE_COMMAND`, `REFRESH_POSITION_COMMAND`
  (включая ногу positions-history), `REFRESH_ORDER_COMMAND`, `REFRESH_ALGO_ORDER_COMMAND`,
  `REFRESH_BILLS_COMMAND` — только обновляют факты (evidence-cycle — внутри
  исполнителя, см. `docs/decisions/refresh-evidence-cycle-ownership.md`);
- **cleanup / safety**: `CANCEL_ORDER_COMMAND`, `CANCEL_ALGO_ORDER_COMMAND`,
  `CLOSE_POSITION_COMMAND` — снимают/локализуют уже существующий риск (kill-switch
  снимает риск отдельно — реактивный side-executor вне реестра команд, тоже
  не проходит RiskValidator);
- **finalization**: `FINALIZE_DEAL_ENTRY_COMMAND`, `FINALIZE_DEAL_EXIT_COMMAND`,
  `MARK_DEAL_CLOSED_COMMAND`, `MARK_DEAL_ERROR_COMMAND`, `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`
  (звенья системных действий без `StrategyAction`; retry-anchor — строка
  исполнения SYSTEM-вида,
  `docs/decisions/command-action-boundary.md`);
- **reduce-only partial exit** через `Order`/`AlgoOrder` — это exit-flow.

Для exit / cleanup / safety / reduce-only partial exit handler выполняет
minimal domain / exchange safety + invariant checks (reduce-only intent;
размер ≤ текущей позиции; направление уменьшает позицию; target относится
к текущей `Deal`; есть свежие exchange facts), а не risk-policy validation.

Главное: `RiskValidator` не должен блокировать команды, цель которых —
снять live risk.

## Переоценка вне создания риска — не в этом scope

Scope валидатора отвечает на «можно ли **создавать** риск», и это
правильно. Но из него следует, что **инварианты ведомой позиции никем не
переоцениваются**, если их проверяет только `RiskValidator`. Конкретный
случай — «ликвидация за стопом»
(`RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION`): у позиции, которую
просто ведут, guard не срабатывает **никогда**, хотя
`Position.externalLiquidationPrice` рефрешится, а на изолированной марже
цена ликвидации едет к марку по мере удержания (H18 `DOCS_CHECK_10`).

**Правило:** переоценка инвариантов уже существующей позиции — задача
**`docs/components/AnomalyJob.md`** (сравнение живых биржевых фактов с
доменными инвариантами), не `RiskValidator`. Расширять scope валидатора на
сопровождение **не** нужно: это вернуло бы risk-policy validation на
горячий путь ведения и размыло бы границу «создание нового vs
сопровождение существующего», на которой стоит и блок-сет холда (§ниже).
Реакция на нарушение — по правилу владельца инварианта
(`docs/decisions/per-trade-risk-policy.md` §«Роль плеча»): перестановка
защиты или контролируемый выход, не только пометка.

## Граница — общая с блок-сетом холдов

Различение «создание нового риска vs сопровождение существующего», которым
задан scope вызова `RiskValidator`, — то же, которым режет блок-сет холда
(H3, `GAPS_CLOSE_5`; `docs/rules/instrument-hold.md` §«Что блокирует»):
блокируется то, что валидируется (risk-creating / risk-increasing /
risk-weakening); то, что валидации не получает (сопровождение: защита и её
ремодел, reduce-only partial exit, cancel/close, read/refresh), холд не
режет. Одна граница — одно определение; блок-сет своего не вводит.

## Первоисточник и смежное

Правило сквозное — повторяется в нескольких процессных доках, единого
владельца-сущности нет (`.claude/decisions/rule-source-of-truth.md`).
Поведение самого `RiskValidator` — `docs/components/RiskValidator.md`;
поток и реакция на BLOCKED — `docs/processes/risk-evaluation.md`;
freshness баланса — `docs/rules/market-data-freshness.md` и handler;
partial exit — `docs/rules/no-partial-close.md`.
