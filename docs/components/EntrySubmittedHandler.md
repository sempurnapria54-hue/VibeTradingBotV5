# EntrySubmittedHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `ENTRY_SUBMITTED` (компонент): проверки,
логика, шаги, команды.

## Назначение

Подтверждает, что entry order отправлен, и определяет, появилась ли
позиция. Конструкция handler'а — `docs/components/DealStateMachine.md`;
статусная механика/переходы/recovery — `docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = ENTRY_SUBMITTED`; есть pinned `StrategyDetail`,
`DealActionState` и локальный entry `Order`, относящийся к сделке и не в
невозможном статусе; ≤1 позиция/инструмент; нет чужого риска; если
attached protection ожидалась — она в entry `Order` или есть recovery-путь.

## Рабочая логика

Создан, но не отправлен → `SUBMIT_ORDER_COMMAND` (strategy-нога, анкер —
STRATEGY-исполнение). **Добыча — через `REFRESH_DEAL_CONTEXT_ACTION`**
(handler добывающие `REFRESH_*` напрямую не эмитит;
`docs/components/SystemActionExecutor.md`): отправлен, не подтверждён →
`REFRESH_ORDER_COMMAND` (внутр. цикл order → pending → history); мог исполниться →
`REFRESH_POSITION_COMMAND` (первое наблюдение позиции пишет
`Deal.billsWindowBegin`). Факты исполнения ордера (`accFillSz`/`avgPx`) —
из `REFRESH_ORDER_COMMAND`, отдельной fill-команды нет. Перед повторным submit —
поиск по client id. Если `REFRESH_POSITION_COMMAND` нашёл позицию, а локальной
нет — `RefreshPositionExecutor` создаёт `Position` и привязывает к `Deal`.
Missing attached protection — policy по статусу parent `Order` (см.
`docs/lifecycles/Order.md`). Противоречивые факты → recovery / `ERROR`.

## Выходные проверки

Entry order финализирован; позиция открыта (через `REFRESH_POSITION_COMMAND`) и
соответствует сделке/инструменту/направлению; attached protection не
потеряна, если была нужна; нет конфликтующих active orders; нет критичных
аномалий. → handler **эмитит `FINALIZE_DEAL_ENTRY_COMMAND`**; само ребро
`ENTRY_SUBMITTED → ENTRY_FINALIZED` пишет звено в одной транзакции со
своим завершением (В4.1,
`docs/components/FinalizeDealEntryExecutor.md` §«Статусное ребро») —
handler гейтит эмиссию, не двигает статус. Если позиция уже закрылась на
бирже (SL/TP/trailing) и факты это объясняют — recovery в `EXIT_PENDING`
(не anomaly при active Deal и known entry order).

## Допустимые StrategyStep

Steps: `FAIL_SAFE` (новые торговые actions обычно не выбираются).
Перечень команд handler-док не держит: состав команд — собственность
действий (`docs/decisions/fsm-execution-layering.md` §«Handler исполняет
действия»; реестры звеньев — `docs/decisions/command-action-boundary.md`
§2, `docs/components/SystemActionExecutor.md`).
