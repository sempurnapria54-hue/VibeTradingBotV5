# Шаблоны

Эта папка содержит шаблоны, используемые в работе над проектом.

## documents/

Шаблоны markdown-документов проекта.

| Шаблон | Когда применяется |
|---|---|
| `adr.md` | Документ — Architecture Decision Record. Применяется для нового ADR в `.claude/adr/` |
| `model.md` | Документ описывает структуру одной доменной модели — для `docs/spec/models/<Name>.md` |
| `lifecycle.md` | Документ описывает динамику доменной модели (жизненный цикл, переходы) — для `docs/spec/lifecycle/<Name>.md` |
| `process.md` | Документ описывает оркестрацию (Job, executor-flow) — для `docs/spec/processes/<Name>.md` |
| `integration-mapping.md` | Документ маппит внешнюю систему на доменную структуру — для `docs/spec/integrations/<exchange>/<name>.md` |
| `reference.md` | Документ — стабильный справочник или архитектурный паттерн — для `docs/spec/references/<name>.md` |
| `invariant.md` | Документ описывает правило, пересекающее несколько моделей или процессов — для `docs/spec/invariants/<name>.md` |
| `open-questions-journal.md` | Документ — журнал концептуальных и рабочих open-questions проекта. Применяется один раз при создании `.claude/questions/open-questions.md`. См. ADR-0008 |

Спецификация категорий документов спецификации — в [ADR-0002, §1 «Жанры документов»](../adr/0002-spec-document-standard.md).
Спецификация журнала open-questions — в [ADR-0008](../adr/0008-open-questions-journal.md).
Правила работы с ADR — в [`.claude/adr/README.md`](../adr/README.md).

Процедура создания и обновления документа спецификации — скилл
[`spec-document-workflow`](../skills/spec-document-workflow/SKILL.md)
и [Сценарий 7](../flow/playbook.md) в playbook.
