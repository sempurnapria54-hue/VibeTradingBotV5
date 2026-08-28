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

Определить, нужен ли реальный protection switch. Если есть
`MAIN_PROTECTION` step → freshness → `StrategyCondition` → protection
actions → `DealActionState` → `StrategyActionCalculator` →
`CREATE_ALGO_ORDER_COMMAND` → `SUBMIT_ALGO_ORDER_COMMAND` → refresh для подтверждения
active protection. Снять attached protection — только после подтверждения
main protection (`CANCEL_*`). Если switch не нужен (нет `MAIN_PROTECTION`
step или его условие не сработало) — переход в `MANAGING` **только если
приложенные защиты ног покрывают позицию целиком**: `Σ accumulated_fill_size`
ног с active-like attached-защитой ≥ `Position.externalSize` (C1
`DOCS_CHECK_23`, решение держателя). Прежний предикат —
`Order.hasActiveAttachedProtection()`, то есть active-like **наличие**, —
пропускал недопокрытую позицию как защищённую; предикат и его три точки
проверки — `docs/rules/risk-creating-entry-protection.md` §«Предикат
покрытия и точки его проверки». Иначе
позиция с live risk без резолвимой защиты = бесстоповая постфактум →
`ERROR` + **ступень 2 биржевой лестницы: `Exchange.TRADE_BLOCKED`**
(живой риск без защиты — нарушение инварианта,
`docs/rules/risk-creating-entry-protection.md`,
`docs/rules/exchange-hold.md`; в коде `markErrorStopless` пока ставит
L3-холд инструмента — перевешивание на `CODE`).

## Выходные проверки

Позиция активна; entry финализирован; **защита позиции с live risk
подтверждена активной** — attached SL держится в active-like состоянии
(`Order.hasActiveAttachedProtection()`), пока main protection не подтверждена
(голого окна без защиты для risk-creating позиции нет, инвариант
`docs/rules/risk-creating-entry-protection.md`); нет дублирующей/
конфликтующей защиты и orphan algo-orders; нет риска под kill-switch.
→ `ENTRY_FINALIZED → PROTECTION_SWITCHED` (если switch реально нужен) или
`→ MANAGING`. Живой риск без активной резолвимой защиты (ни main, ни
active-attached) → `ERROR` + ступень 2 (`Exchange.TRADE_BLOCKED`; в коде
пока L3-холд инструмента — перевешивание на `CODE`).

## Допустимые StrategyStep

Steps: `MAIN_PROTECTION`, `FAIL_SAFE`. Перечень команд handler-док не
держит: состав команд — собственность действий
(`docs/decisions/fsm-execution-layering.md` §«Handler исполняет действия»;
реестры звеньев — `docs/decisions/command-action-boundary.md` §2,
`docs/components/SystemActionExecutor.md`).
