# Backlog

## На какой вопрос отвечает этот файл

Что мы планируем сделать.

## Миграция архивных торговых сущностей в `docs/`

**Источник:** `.claude-archive/2026-05-21/docs/domain/models/`.

**Порядок:** `Balance.md` → `Position.md` → `Order.md` →
`AlgoOrder.md` → `Deal.md` → `Strategy.md`.

**Метод.** Мигрировать каждую сущность в `docs/`, применяя
зафиксированные критерии:
- гранулярность модели — `.claude/decisions/model-granularity.md`;
- первоисточник правил — `.claude/decisions/rule-source-of-truth.md`;
- exchange-specific — `.claude/decisions/client-layer-docs.md`.

Активно использовать `docs/components/` (тип введён первой обкаткой,
но в обороте не задействован) — для resolver / executor / checker /
factory / job.

**Продуктовые вопросы.** Открытые вопросы из Deal §15 (retry-state
финализации сделки; недосчитанный `resultProfit` после исчерпания
retry) при миграции Deal переносятся в
`.claude/work/questions/open-questions.md` как есть.

**Запуск:** в новом чате.
