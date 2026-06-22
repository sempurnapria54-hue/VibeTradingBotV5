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
(или есть достаточные fills facts); известна цена входа (или путь через
`REFRESH_FILLS`); ≤1 позиция; нет критичного риска без возможности защиты;
если `Strategy.DELETED` → graceful shutdown, не обычные data-dependent
steps.

## Рабочая логика

Определить, нужен ли реальный protection switch. Если есть
`MAIN_PROTECTION` step → freshness → `StrategyCondition` → protection
actions → `DealActionState` → `StrategyActionCalculator` →
`CREATE_ALGO_ORDER` → `SUBMIT_ALGO_ORDER` → refresh для подтверждения
active protection. Снять attached protection — только после подтверждения
main protection (`CANCEL_*`). Если switch не нужен — проверить безопасное
состояние позиции для перехода в `MANAGING`.

## Выходные проверки

Позиция активна; entry финализирован; **защита позиции с live risk
подтверждена** — attached SL держится, пока main protection не подтверждена
(голого окна без защиты для risk-creating позиции нет, инвариант
`docs/rules/risk-creating-entry-protection.md`); нет дублирующей/
конфликтующей защиты и orphan algo-orders; нет риска под kill-switch.
→ `ENTRY_FINALIZED → PROTECTION_SWITCHED` (если switch реально нужен) или
`→ MANAGING`.

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `MAIN_PROTECTION`, `FAIL_SAFE`. Команды: `REFRESH_BALANCE`,
`CREATE_ALGO_ORDER`, `SUBMIT_ALGO_ORDER`, `REFRESH_ALGO_ORDER`,
`CANCEL_ALGO_ORDER`, `CANCEL_ORDER`, `REFRESH_POSITION`,
`MARK_DEAL_ERROR`, `EXECUTE_KILL_SWITCH`.
