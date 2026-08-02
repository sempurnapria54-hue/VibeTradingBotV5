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
инициированного выхода/закрытия; ≤1 позиция; можно безопасно проверить
live risk; локальные orders/algo доступны для очистки.

## Рабочая логика

**Добыча — через системное действие `REFRESH_DEAL_CONTEXT_ACTION`**
(handler добывающие `REFRESH_*` напрямую не эмитит — они шли бы без
анкера попыток; узел 3 `DOCS_CHECK_8`,
`docs/components/SystemActionExecutor.md`): `REFRESH_POSITION_COMMAND`
(подтвердить отсутствие live-risk позиции; **та же команда второй ногой
цикла добывает положение закрытия** — число приземляется на `Position`,
окно — `Deal.billsWindowEnd`; `docs/components/RefreshPositionExecutor.md`);
`REFRESH_ORDER_COMMAND` / `REFRESH_ALGO_ORDER_COMMAND` по известным сущностям сделки
(каждый внутри себя проходит pending/history); `REFRESH_BALANCE_COMMAND` после
снятия live risk; `REFRESH_BILLS_COMMAND` (`DealCashFlow` — категорийная
разбивка; её факт durable).

**Cleanup — напрямую, без анкера:** live ordinary orders →
`CANCEL_ORDER_COMMAND`; live algo → `CANCEL_ALGO_ORDER_COMMAND` (серия неудач считается
на инструмент-scope, `docs/components/models/ServiceCommand.md`).

**Завершение — через `FINALIZE_DEAL_EXIT_ACTION`:** определить/
подтвердить `Deal.CloseReason`; `FINALIZE_DEAL_EXIT_COMMAND` эмитится по
терминальному исходу добычи (считает `resultProfit` из положения закрытия
на `Position` + bills и **пишет его на `Deal`**,
`docs/decisions/pnl-finalization-mechanics.md`); `MARK_DEAL_CLOSED_COMMAND` когда
всё очищено (ассертит число, ставит терминал). `REFRESH_FILLS`
**снимается** на `CODE` шага 7 (в коде пока жив — H15, `GAPS_CLOSE_6`).
Cleanup/safety команды — без `RiskValidator`.

**Отдельной команды `REFRESH_POSITIONS_HISTORY` handler не эмитит** —
её нет в реестре (`docs/components/models/ServiceCommand.md`).

## Выходные проверки

`REFRESH_POSITION_COMMAND` подтвердил отсутствие позиции (или локальной не было и
entry/exit facts доказывают отсутствие live risk); active position в
домене → `CLOSED`; нет live orders/algo и активного рыночного риска;
balance обновлён; причина закрытия определена. → `EXIT_PENDING → CLOSED`
(терминал ставит `MARK_DEAL_CLOSED_COMMAND`). Риск не снят / противоречие →
`EXIT_PENDING → ERROR`.

**Число гейтит терминал через финализатор, не через handler** (узел 4
`DOCS_CHECK_8`, вариант (а)): `FINALIZE_DEAL_EXIT_COMMAND` не завершается без
числа, добыча ретраится бюджетом `REFRESH_DEAL_CONTEXT_ACTION`;
исчерпание уводит сделку ошибочной тропой + холд инструмента. Положение
закрытия — поля на `Position`, проверяемые durable-факты. **Полнота
разбивки bills** — best-effort: недобранные движения дают `AnomalyReport`
на сверке, а не удержание сделки; асимметрия штатной и аварийной троп —
`docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия троп отказа
добычи».

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `FAIL_SAFE` (торговые steps обычно не применяются). Команды:
`REFRESH_POSITION_COMMAND`, `REFRESH_ORDER_COMMAND`, `REFRESH_ALGO_ORDER_COMMAND`,
`CANCEL_ORDER_COMMAND`, `CANCEL_ALGO_ORDER_COMMAND`, `REFRESH_BALANCE_COMMAND`,
`REFRESH_BILLS_COMMAND`, `FINALIZE_DEAL_EXIT_COMMAND`, `MARK_DEAL_CLOSED_COMMAND`, `MARK_DEAL_ERROR_COMMAND`
(добывающие — звеньями `REFRESH_DEAL_CONTEXT_ACTION`, финализационные —
звеньями `FINALIZE_DEAL_EXIT_ACTION`/`FINALIZE_DEAL_ERROR_ACTION`).
