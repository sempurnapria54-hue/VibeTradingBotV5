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

Создан, но не отправлен → `SUBMIT_ORDER`; отправлен, не подтверждён →
`REFRESH_ORDER` (внутр. цикл order → pending → history); мог исполниться →
`REFRESH_POSITION`; нужны факты исполнения → `REFRESH_FILLS`. Перед повторным submit — поиск по client id. Если
`REFRESH_POSITION` нашёл позицию, а локальной нет — `RefreshPositionExecutor`
создаёт `Position` и привязывает к `Deal`. Missing attached protection —
policy по статусу parent `Order` (см. `docs/lifecycles/Order.md`).
Противоречивые факты → recovery / `ERROR`.

## Выходные проверки

Entry order финализирован; позиция открыта (через `REFRESH_POSITION`) и
соответствует сделке/инструменту/направлению; attached protection не
потеряна, если была нужна; нет конфликтующих active orders; нет критичных
аномалий. → `ENTRY_SUBMITTED → ENTRY_FINALIZED`. Если позиция уже
закрылась на бирже (SL/TP/trailing) и факты это объясняют — recovery в
`EXIT_PENDING` (не anomaly при active Deal и known entry order).

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `FAIL_SAFE` (новые торговые actions обычно не выбираются). Команды:
`SUBMIT_ORDER`, `REFRESH_ORDER`, `REFRESH_POSITION`, `REFRESH_FILLS`,
`REFRESH_BALANCE`, `FINALIZE_DEAL_ENTRY`, `MARK_DEAL_ERROR`,
`EXECUTE_KILL_SWITCH`.
