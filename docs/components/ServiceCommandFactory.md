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

В шаге 5 `CANCEL` / `REPLACE` команды не порождают (refinement —
граница со step-5 калькулятором), а algo-`Condition` собирается только с
`type` (полные SL/TP-цены — refinement).

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
