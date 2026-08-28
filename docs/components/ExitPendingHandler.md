# ExitPendingHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `EXIT_PENDING` (компонент): проверки,
логика, шаги, команды.

## Назначение

Дочищает сделку после инициированного выхода: подтверждает закрытие
позиции, отменяет live-сущности, финализирует факты. Конструкция handler'а
— `docs/components/DealStateMachine.md`; статусная механика —
`docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = EXIT_PENDING`; pinned `StrategyDetail`; есть факты
инициированного выхода/закрытия; ≤1 живая позиция; можно безопасно проверить
live risk; локальные orders/algo доступны для очистки.

## Рабочая логика

**Добыча — через системное действие `REFRESH_DEAL_CONTEXT_ACTION`**: `REFRESH_POSITION_COMMAND`
(подтвердить отсутствие live-risk позиции; **та же команда второй ногой
цикла добывает положение закрытия** — число приземляется на `Position`,
окно — `Deal.billsWindowEnd`; `docs/components/RefreshPositionExecutor.md`);
`REFRESH_ORDER_COMMAND` / `REFRESH_ALGO_ORDER_COMMAND` по известным сущностям сделки
(каждый внутри себя проходит pending/history); `REFRESH_BALANCE_COMMAND` после
снятия live risk; `REFRESH_BILLS_COMMAND` (`DealCashFlow` — категорийная
разбивка; её факт durable) — **звено выходной тропы**: гейт эмиссии стоит
на тропе, а не на окне,
поэтому здесь оно в цикле есть всегда, а на аварийной тропе — не эмитится
вовсе (`docs/components/SystemActionExecutor.md`).

**Cleanup — напрямую, без анкера**, в порядке инварианта
`docs/rules/exit-teardown-order.md` (единственное место записи; собственной
копии последовательности handler-док не держит): живые **входные**
(не reduce-only) ноги → `CANCEL_ORDER_COMMAND` / `CANCEL_ALGO_ORDER_COMMAND`
→ живая позиция → `CLOSE_POSITION_COMMAND` → остальные live-сущности
(reduce-only ноги, защита) → `CANCEL_*`. Учёта серии неудач **нет** — анкера
у cleanup нет, потому что нет исполнения-действия; форвард на
`TradeGuardJob`,,
`docs/components/models/ServiceCommand.md`.

**Завершение — через `FINALIZE_DEAL_EXIT_ACTION`:** определить/
подтвердить `Deal.CloseReason`; `FINALIZE_DEAL_EXIT_COMMAND` эмитится по
терминальному исходу добычи (считает `resultProfit` как Σ положений
закрытия эпизодов + bills и **пишет его на `Deal`**,
`docs/rules/pnl-reconciliation.md`); `MARK_DEAL_CLOSED_COMMAND` когда
всё очищено (ассертит число, ставит терминал). `REFRESH_FILLS`
**снимается** на `CODE` шага 7.
Cleanup/safety команды — без `RiskValidator`.

**Отдельной команды `REFRESH_POSITIONS_HISTORY` handler не эмитит** —
её нет в реестре (`docs/components/models/ServiceCommand.md`).

## Выходные проверки

`REFRESH_POSITION_COMMAND` подтвердил отсутствие позиции (или локальной не было и
entry/exit facts доказывают отсутствие live risk); живой эпизод в домене
→ `CLOSED`; нет live orders/algo и активного рыночного риска;
balance обновлён; причина закрытия определена. → `EXIT_PENDING → CLOSED`
(терминал ставит `MARK_DEAL_CLOSED_COMMAND`). Риск не снят / противоречие →
`EXIT_PENDING → ERROR`.

**Неполное число — тот же выход в `ERROR`, третьей веткой**. После того как звено 1
`FINALIZE_DEAL_EXIT_ACTION` записало число и признаки отбора, handler
проверяет предикат неполноты
(`docs/components/FinalizeDealExitExecutor.md`); истина ⇒ `EXIT_PENDING → ERROR` вместо разрешения
звена 2:

- **почему здесь.** Развилка «`CLOSED` vs `ERROR`» на этой тропе уже
  принадлежит handler'у, и предикат читается из durable-полей `Deal` —
  ни нового механизма, ни нового значения статуса. Ребро в `ERROR`
  по-прежнему пишет звено (`MARK_DEAL_ERROR_COMMAND`
  `FINALIZE_DEAL_ERROR_ACTION`), handler лишь доводит сделку до
  состояния, в котором действие заводится
  (`docs/processes/fsm-execution-layering.md`);
- **живая строка `FINALIZE_DEAL_EXIT_ACTION` уходит в `SKIPPED`**, не в
  `FAILED`: исполнение стало неактуальным (сделка ушла с тропы), а не
  провалилось — значение существующее, с ровно этим смыслом
  (`docs/lifecycles/DealActionState.md`).
  Это несущая деталь: `FAILED` притянул бы холд инструмента, который
  решение П11 исключает — радиус локализован сделкой;
- **названная цена — задержка в один проход:** число и признаки пишутся
  на проходе N, предикат читается на N+1. Живого риска в этот момент уже
  нет, поэтому задержка ничем не рискует;
- **холд не поднимается ни на одной ветке** — ни инструментный, ни
  биржевой: недостача наша и её радиус известен.

**Наличие числа гейтит терминал через финализатор, не через handler**: `FINALIZE_DEAL_EXIT_COMMAND` не
завершается без
числа, добыча ретраится бюджетом `REFRESH_DEAL_CONTEXT_ACTION`;
исчерпание уводит сделку ошибочной тропой + холд инструмента. Положения
закрытия эпизодов — поля на строках `Position`, проверяемые durable-факты.

**Это не спорит с веткой выше: гейт и маршрутизация — разные вопросы.**
«Числа нет» гейтит завершение звена (финализатор, ретрай, холд при
исчерпании); «число есть, но неполно» — уже посчитанный факт, и он не
гейтит, а **уводит на другую тропу** (handler, без ретраев и без холда).
Первое про добычу, второе про доверие к добытому. **Полнота
разбивки bills** — best-effort: недобранные движения дают `AnomalyReport`
на сверке, а не удержание сделки; асимметрия штатной и аварийной троп —
`docs/rules/pnl-reconciliation.md`.

## Допустимые StrategyStep

Steps: `FAIL_SAFE` (торговые steps обычно не применяются). Перечень
команд handler-док не держит: состав команд — собственность действий
(`docs/processes/fsm-execution-layering.md`;
реестры звеньев — `docs/rules/command-lifecycle.md`,
`docs/components/SystemActionExecutor.md`).
