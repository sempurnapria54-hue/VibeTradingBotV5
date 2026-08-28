# EntryFinalizedHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `ENTRY_FINALIZED` (компонент): проверки,
логика, шаги, команды.

## Назначение

Подтверждает, что вход завершён и позиция открыта, и определяет следующий
безопасный путь. Стратегия может требовать создать/подтвердить standalone
main protection — но не всякая стратегия заменяет temporary attached
protection, поэтому `ENTRY_FINALIZED` не всегда ведёт в
`PROTECTION_SWITCHED`. Допустимо `→ PROTECTION_SWITCHED → MANAGING`, `→
MANAGING`, `→ ERROR`. Конструкция — `docs/components/DealStateMachine.md`;
статусная механика — `docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = ENTRY_FINALIZED`; pinned `StrategyDetail`; позиция активна
и соответствует сделке/инструменту/направлению; entry order финализирован
(факты исполнения — `accFillSz`/`avgPx` из `REFRESH_ORDER_COMMAND`); известна цена
входа; ≤1 живая позиция; нет критичного риска без возможности защиты;
если `Strategy.DELETED` → graceful shutdown, не обычные data-dependent
steps.

## Рабочая логика

## Выходные проверки

Позиция активна; entry финализирован; **защита позиции с live risk
покрывает её целиком** — покрытие attached-защит ног ≥
`Position.externalSize` (формула операнда — дом,
`docs/rules/live-risk-protection.md`), пока main
protection не подтверждена (голого окна без защиты для risk-creating
позиции нет, инвариант — тот же дом); нет дублирующей/конфликтующей защиты
и orphan algo-orders; нет риска под kill-switch.
→ `ENTRY_FINALIZED → PROTECTION_SWITCHED` (если switch реально нужен) или
`→ MANAGING`. Живой риск с покрытием ниже `externalSize` (ни main, ни
attached не добирают) → `ERROR` + ступень 2 (`Exchange.TRADE_BLOCKED`; в
коде пока L3-холд инструмента — перевешивание на `CODE`).

## Допустимые StrategyStep

Steps: `MAIN_PROTECTION`, `FAIL_SAFE`. Перечень команд handler-док не
держит: состав команд — собственность действий
(`docs/processes/fsm-execution-layering.md`;
реестры звеньев — `docs/rules/command-lifecycle.md`,
`docs/components/SystemActionExecutor.md`).
