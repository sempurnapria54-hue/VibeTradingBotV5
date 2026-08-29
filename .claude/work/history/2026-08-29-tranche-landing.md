# Агрегатная сделка и транши — приземление в корпусе

## На какой вопрос отвечает этот файл

Что мы сделали, приземляя дизайн-проход агрегатной сделки.

## Итог

Имя ратифицировано держателем — **`Tranche`** (`StrategyTranche` /
`DealTranche`); `DealDetail` и `DealFlow` отклонены. Дизайн-проход
приземлён в корпусе по §E прохода. Прогон исполнимых спецификаций
зелёный: **15 предметов, 178 примеров** (было 14 и 158).

Работы по корпусу не осталось; дальше — CODE-дельта
(`.claude/work/backlog.md` §«Агрегатная сделка и транши — CODE-дельта»).

## Что стало

- **`Deal` — агрегат:** слот инструмента, эпизоды позиции, окно линковки,
  результат, признаки отбора, четыре числа риска, координация траншей.
  Пять статусов: `ACTIVE`, `EXIT_PENDING`, `CLOSED`, `ERROR`,
  `EMERGENCY_CLOSED`.
- **`DealTranche` — самостоятельная сделка внутри сделки:** свой вход,
  своя защита, свои выходы, семь статусов (сегодняшние минус два
  ошибочных) плюс ребро переоткрытия `MANAGING → ENTRY_SUBMITTED`.
- **Ошибочное состояние — только агрегатное:** отказ транша
  останавливает набор риска по всей сделке.
- **Экспозиция транша — производная его заявок**; эпизод остаётся
  биржевым фактом агрегата; **сумма экспозиций сверяется с
  нетто-размером** — новый детектор неатрибутируемого риска.
- **Инвариант покрытия стал траншевым:** каждый транш закрыт своим
  стопом; агрегатная достаточность выводится, а не проверяется.
- **Стратегия объявляет транши:** `StrategyTranche` с `stepsByStatus` по
  статусам транша, шаблон сетки (`levelCount`, `levelStep`), признак
  переоткрытия. Агрегатная поверхность шагов узкая — только `EXIT` и
  `FAIL_SAFE`.

## Куда приземлено

| Группа | Файлы |
|---|---|
| модели | `Deal.md` (переписан агрегатом), **`DealTranche.md` (новый)**, `Strategy.md` (+`StrategyTranche`), `Order.md`, `AlgoOrder.md`, `DealActionState.md`, `Position.md` |
| lifecycles | `Deal.md` (переписан), **`DealTranche.md` (новый)**, `Position.md` |
| спецификации | `protection-coverage.json` (переписана траншевой), **`deal-tranche-lifecycle.json` (новая)**, `deal-lifecycle.json`, `strategy-walkthrough.json`, `strategy-reference.json`, `deal-context-load.json`, `risk-limits.json`, `deal-risk-numbers.json` |
| правила | `live-risk-protection.md`, `exit-teardown-order.md` (переписано), `strategy-validation.md`, `risk-policy.md`, `no-partial-close.md`, `risk-validator-scope.md`, `deal-without-operations.md`, `trading-constraints.md` |
| процессы | `deal-management.md`, `fsm-execution-layering.md`, `risk-evaluation.md` |
| компоненты | 6 handler-доков переименованы в `Tranche*Handler`; **новые** `DealActiveHandler`, `DealExitPendingHandler`, `DealTrancheStateMachine`; правлены `DealStateMachine`, `DealOrchestratorJob`, `DealOpeningService`, `DealContextService`, `RiskValidator`, `RiskBlockResolver`, `AnomalyJob`, `SystemActionExecutor`, `FinalizeDealEntryExecutor`, `MarkDealClosedExecutor`, `ErrorHandler`, `ExitActionExecutor`, `ClosePositionExecutor`, `KillSwitchExecutor`, `CreateOrderExecutor`, `SubmitOrderExecutor`, `EntryScannerJob`, `models/DealContext.md`, `models/CalculatedSize.md` |
| словарь | **`leg-term.md` (новый)** — «нога» это заявка, уровень сделки называется траншем |
| эталон | `strategy-examples/trend-following-ema.json` — шаги завёрнуты в один транш на деталь, выход поднят на уровень сделки, маркер `level` с действий снят |
| дерево | `.claude/knowledge-tree.md` — новые и переименованные файлы |

## Отклонения от §C4 прохода

**`entryReason` остался на сделке**, на транш уехал только
`entryStepType`. Проход планировал перенести оба; при приземлении
выяснилось, что у восстановленной сделки (живой риск, заведённый вне
приложения) объявленных траншей нет вовсе, а причина заведения сделки
нужна. Решение — Д121 дайджеста.

## Что осталось живым

- **CODE-дельта** — `.claude/work/backlog.md` §«Агрегатная сделка и
  транши — CODE-дельта»: схема, енумы, две машины состояний, сверка Σ,
  валидация create.
- **Две названные цены** держателю приняты и записаны в проходе (§G):
  потраншевый `R` не выводится из фактов нетто-режима; внешнее частичное
  сокращение позиции становится громким.

## Связи

- Дизайн-проход и его исполнимый аннекс —
  `2026-08-29-tranche-landing/aggregate-deal-design.md` и
  `aggregate-deal-design-spec.json` (17 примеров, зелёный).
- Автономные развилки приземления — `.claude/work/decision-digest.md`
  (итерация 2026-08-29, Д121-Д124).
