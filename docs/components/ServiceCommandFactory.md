# ServiceCommandFactory

## На какой вопрос отвечает этот файл

Кто создаёт `ServiceCommand` из рассчитанного действия (компонент): что
делает, принцип «одна актуальная команда за проход», связь с risk-layer.

## Назначение

`ServiceCommandFactory` создаёт команды из рассчитанных действий
стратегии: выбирает `ServiceCommandType` по типу action, создаёт payload,
привязывает к `dealId` и (если команда относится к action) к
`dealActionStateId`. Не проверяет условия, не считает цену/размер/риск, не
исполняет команды. Если action нельзя выполнить без target entity —
команду не создаёт.

## Одна актуальная команда за проход

Не создаёт всю цепочку `CREATE_* → SUBMIT_* → REFRESH_*` заранее — это
lifecycle, а не пакет (см. `docs/rules/command-lifecycle.md`). За один
проход — одна актуальная команда для текущего action/state, по свежим
`DealContext` / `DealActionState` / runtime-сущностям / exchange facts:

```text
DealActionState отсутствует / PLANNED -> CREATE_ORDER / CREATE_ALGO_ORDER
                                         (CLOSE_FULL -> CLOSE_POSITION)
status = CREATED                       -> SUBMIT_ORDER / SUBMIT_ALGO_ORDER
status = SUBMITTED                     -> REFRESH_ORDER / REFRESH_ALGO_ORDER
                                         / REFRESH_POSITION
```

(`DealActionState`-статусы — `docs/lifecycles/DealActionState.md`.)

В шаге 5 `CANCEL` / `REPLACE` команды не порождались (refinement —
граница со step-5 калькулятором); их оркестрация — скоуп шага 6 (см.
§«REPLACE и финализация» ниже). Algo-`Condition` собирается только с `type`
(полные SL/TP-цены — refinement).

## REPLACE и финализация (эмиссия за пределами action-маппинга)

Фабрика остаётся **«одна атомарная команда за проход»** и секвенс в себя не
берёт. Две оси выходят за прямой `DealActionState`-маппинг выше:

- **REPLACE-ноги.** Порядок ног REPLACE по риск-классу
  (`docs/decisions/replace-not-amend.md`) оркеструет **петля /
  `DealStateMachine`** по подтверждённым фактам, а не фабрика: фабрика на
  каждом проходе порождает одну атомарную ногу (`CREATE_*`/`SUBMIT_*`/
  `CANCEL_*`) для текущего звена цепочки замещений по фактам
  (`docs/components/DealStateMachine.md`, владелец секвенса;
  `docs/decisions/action-orchestration-vs-command.md`).
- **Финализационные команды** (`FINALIZE_DEAL_*`/`MARK_DEAL_*`) не имеют
  `dealActionStateId` (нет `StrategyAction`). Когда handler решил
  финализировать, command-layer материализует строку
  `DealFinalizationState(deal, type)` (upsert по `UNIQUE(deal_id,
  finalization_type)`); фабрика читает её статус, привязывает команду к
  `dealFinalizationStateId` и эмитит **одну** финализационную команду за
  проход (статус сам не пишет — паритет с `DealActionState`):

```text
DealFinalizationState отсутствует / PENDING -> FINALIZE_DEAL_* / MARK_DEAL_*
                                               (по type)
status = COMPLETED                          -> финализация type закрыта
```

(Статусы/тип — `docs/lifecycles/DealFinalizationState.md`; дом retry-state —
`docs/decisions/deal-finalization-state-materialization.md`.)

## Связь с risk-layer и freshness

Получает только разрешённое к исполнению action: risk-creating /
risk-increasing / risk-weakening action проходит `RiskValidator` →
(`RiskBlockResolver` при BLOCKED) до создания команды; при блокирующем
риске команда не создаётся (см. `docs/processes/risk-evaluation.md`,
`docs/rules/risk-validator-scope.md`). Reduce-only partial exit,
cleanup/safety — без `RiskValidator`, через minimal safety/invariant
checks. Свежесть рыночных данных сам не проверяет: если данные step
устарели и `marketDataExpiredSetting` запрещает actions, торговый action
до фабрики не доходит; safety/cleanup команды остаются допустимыми (см.
`docs/rules/market-data-freshness.md`).
