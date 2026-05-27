# Открытые вопросы

## На какой вопрос отвечает этот файл

Что мы ещё не решили (общие вопросы — пайплайн и продукт).

## Статус

Открыты два продуктовых вопроса по финализации `Deal` (перенесены из
архивного `Deal.md` §15 при миграции, 2026-05-27).

История закрытых вопросов пайплайна:

- Q1, Q2, Q3 закрыты решением
  `.claude/decisions/rule-source-of-truth.md` (2026-05-26).
- Q4 закрыт решением
  `.claude/decisions/chat-vs-cc-knowledge-split.md`.
- NQ-F закрыт решениями `.claude/decisions/runtime-value-object.md`
  и `.claude/decisions/models-core-vs-other.md` (2026-05-26).
- NQ-H закрыт решением
  `.claude/decisions/fsm-handler-as-component.md` (2026-05-27).
- NQ-G закрыт решением
  `.claude/decisions/master-index-not-fixated.md` (2026-05-27).

## Открытые продуктовые вопросы

### DEAL-Q1. Где хранить persisted retry-state финализации сделки

Lifecycle/finalization commands (`REFRESH_FILLS`, `FINALIZE_DEAL_EXIT`,
`MARK_DEAL_CLOSED`, emergency finalization) нуждаются в persisted
retry-state, но `DealActionState` относится к `StrategyAction`, а
финализация сделки — это lifecycle/system action. Audit/history не
должен быть runtime-source, поэтому retry-state финализации нельзя
хранить только в истории. Где его хранить — не решено.
Связано: `docs/models/core/Deal.md`, `docs/lifecycles/Deal.md`.

### DEAL-Q2. Что делать, если resultProfit нельзя посчитать после исчерпания retry

Зафиксировано: `resultProfit`/`resultProfitCurrency` обязательны для
`CLOSED`/`EMERGENCY_CLOSED`; `resultProfit = 0` допустим только как
результат расчёта, не fallback. Не решено, что делать, если после
всех retry итоговый PnL всё ещё нельзя безопасно посчитать. Варианты
на будущее: отдельный finalization state; перевод в `ERROR`;
отдельный `DealFinalizationState`; ручной разбор; специальный
operational flag без нарушения terminal semantics.
Связано: `docs/models/core/Deal.md` §Итоговый PnL.

## Конвенция

Новые открытые вопросы добавляются сюда по мере появления. Закрытый
вопрос удаляется отсюда; история закрытия живёт в соответствующем
decision (конвенция из
`.claude/decisions/chat-vs-cc-knowledge-split.md`).
