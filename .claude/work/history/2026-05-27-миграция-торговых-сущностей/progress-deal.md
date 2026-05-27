# Прогресс: миграция Deal

## На какой вопрос отвечает этот файл

На каком шаге миграция архивной сущности Deal и как классифицирован
каждый фрагмент.

## Статус

**Завершено.** Источник:
`.claude-archive/2026-05-21/docs/domain/models/Deal.md`.

`Deal` — lifecycle root со своей FSM. Не биржевая сущность → OKX
client-доков нет. §15 (открытые вопросы) перенесён в
`open-questions.md`. Кластер процессов Deal management (FSM,
DealContext, DealActionState, commands) — отдельная миграция
(форвард-заметки DEAL-FW1…FW9).

## Созданные / изменённые файлы

- `docs/models/core/Deal.md` — модель (создан).
- `docs/lifecycles/Deal.md` — lifecycle FSM (создан).
- `.claude/work/questions/open-questions.md` — добавлены DEAL-Q1,
  DEAL-Q2 (изменён).
- `.claude/work/questions/tasks/deal.md` — форвард-заметки (создан).

Без OKX-доков и без новых сквозных правил (resultProfit — правило,
которым владеет `Deal`, размещено в модели).

## Отчёт по фрагментам

Область у всех — **продукт**.

| # | Фрагмент | Тип | Размещение / диспозиция |
|---|---|---|---|
| Ф1 | Назначение `Deal` (lifecycle root + runtime graph) | модель | `Deal.md` §Назначение |
| Ф2 | Инвариант «не биржевая сущность, нет external id/status, OKX mapping не нужен» | модель | `Deal.md` §Назначение (позитив); отрицания свёрнуты |
| Ф3 | Атрибуты `Deal` | модель | `Deal.md` §Структура |
| Ф4 | Енум `Status` (9 значений) | модель + lifecycle | Перечень → `Deal.md`; механика → lifecycle |
| Ф5 | Енум `EntryReason` | модель | `Deal.md` §Енумы |
| Ф6 | Енум `EntryStepType` + комбинации с entryReason | модель | `Deal.md` §Енумы |
| Ф7 | Енум `ShutdownReason` + когда заполняется | модель + lifecycle | Перечень → `Deal.md`; когда заполняется → `Deal lifecycle` §graceful shutdown |
| Ф8 | Енум `CloseReason` + правила (не used значения) | модель | `Deal.md` §Енумы |
| Ф9 | `resultProfit` через REFRESH_FILLS, обязателен для terminal, =0 только расчёт | модель (правило сущности) | `Deal.md` §Итоговый PnL (первоисточник — `rule-source-of-truth.md`; закрывает направление BAL-Q5/POS-Q5) |
| Ф10 | Детальный PnL breakdown не в Deal | модель | `Deal.md` §Итоговый PnL (позитив: через TradeFill/audit); → DEAL-FW5/FW9 |
| Ф11 | Status semantics (не описывает Order/AlgoOrder/Position/ACK) | lifecycle | `Deal lifecycle` §Статусы |
| Ф12 | Active/terminal groups | lifecycle | `Deal lifecycle` §Группы статусов |
| Ф13 | Status invariants (ERROR→CLOSED запрещён, terminal без handlers, …) | lifecycle | `Deal lifecycle` §Инварианты переходов |
| Ф14 | Terminal semantics + Deal live risk (вычисляемо, не boolean) | lifecycle | `Deal lifecycle` §Terminal semantics |
| Ф15 | Runtime graph (orders/algoOrders/position, ≤1 Position) | модель | `Deal.md` §Runtime graph |
| Ф16 | Что не входит в runtime graph / не хранится в Deal | модель | `Deal.md` §Runtime graph (позитив); отрицания свёрнуты |
| Ф17 | `DealActionState` не поле Deal; связь dealId→strategyActionId→RuntimeTarget | модель (граница) + Deal-runtime | Граница → `Deal.md` §Границы; полная модель → DEAL-FW2 |
| Ф18 | `DealContext` не часть Deal; состав | модель (граница) + RVO | Граница → `Deal.md` §Границы; полная модель → DEAL-FW1 |
| Ф19 | pinned `StrategyDetail`; `Deal.direction` = StrategyTradeDirection | модель | `Deal.md` §Структура; Strategy-связи → DEAL-FW8 |
| Ф20 | FSM handlers / DealStateMachine | компоненты | Lifecycle ссылается; компоненты → DEAL-FW3 (`fsm-handler-as-component.md`) |
| Ф21 | Restart/recovery (не ищем pending command; FSM по graph/context/DealActionState/facts) | lifecycle | `Deal lifecycle` §Restart/recovery; commands → DEAL-FW4 |
| Ф22 | §15.1 retry-state финализации | общий открытый вопрос | `open-questions.md` DEAL-Q1 (по backlog) |
| Ф23 | §15.2 недосчитанный resultProfit после retry | общий открытый вопрос | `open-questions.md` DEAL-Q2 (по backlog) |
| Ф24 | entry context / MarketPhase result / audit | продуктовый процесс (аудит) | Отложено → DEAL-FW9 |

## Итог по Deal

- Размещено в `docs/`: 2 файла (модель, lifecycle). OKX-доков нет
  (Deal не биржевая). Новых сквозных правил нет.
- В `open-questions.md`: DEAL-Q1, DEAL-Q2 (продуктовые, по backlog).
- Свёрнуты к позитиву отрицательные перечни (Ф2, Ф10, Ф16) по
  `negative-statements-not-fixated.md`.
- В форвард-заметки: DEAL-FW1…FW9 — кластер Deal management
  (DealContext, DealActionState, FSM-handlers/DealStateMachine,
  command-подсистема, TradeFill/REFRESH_FILLS, RiskValidator,
  Anomaly/Reconciliation, Strategy-связи, аудит). Консолидируют
  ранее накопленные форвард-заметки Balance/Position/Order/AlgoOrder.
