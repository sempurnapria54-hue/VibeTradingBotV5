# Pipeline Evolution Log

История изменений пайплайна работы с Claude на этом проекте: агенты, скиллы, процессы, концепция.

**Правила лога:**
- Append-only. Старые записи не редактируются.
- Каждая запись — заголовок с датой и кратким описанием + 2-5 строк сути.
- Сюда пишутся **сделанные** изменения, не запланированные.
- Записи добавляются в хронологическом порядке (новые — наверх).

---

## 2026-05-14 — Initial setup

Запуск работы с Claude на проекте.

- Установлен Claude Code v2.1.141.
- Создана структура `.claude/agents/`, `.claude/skills/`.
- Подняты стартовые агенты: architect, domain-expert, risk-engineer, trading-risk-officer, knowledge-curator.
- Создан `CLAUDE.md`, `docs/working-with-claude.md`, `docs/playbook.md`.
- Принята схема поэтапной миграции документации в `docs/spec/`.
- Старые `codex/stage/`, `codex/tasks/`, `codex/Main.md` удалены. `codex/CodeStyle.md` и `codex/TechRadar.md` перенесены в `docs/conventions/`.

Контекст и обоснования — в первой стратегической сессии в чате (этот лог стартует здесь).
