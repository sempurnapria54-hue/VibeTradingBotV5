# Собственная проверка CC: ссылочная целостность живых носителей

Метод: механический сбор всех ссылок вида `` `path.md` `` и `` `path.md` §«Имя» ``
из **живых** носителей (`.claude/rules|skills|agents|processes|decisions|templates|
snapshots|tests|work/{roadmap,progress,questions}`, `backlog.md`,
`decision-digest.md`, `CLAUDE.md`, `knowledge-tree.md`); `history/` и `notes/`
исключены (архив не чинится). §-адрес считается разрешённым, если ему
соответствует заголовок **или** лид-жирный пассаж (`.claude/rules/structure.md`
§Принципы). Всего §-адресов: 169.

Каждый заявленный дефект перепроверен **чтением целевого файла**
(перечень заголовков), а не только грепом.

## Несуществующие цели (3)

| Носитель | Цель | Исход |
|---|---|---|
| `.claude/skills/integration-okx.md:121` | `docs/endpointFunctionList.md` | файла нет — дефект |
| `.claude/work/backlog.md:65` | `.claude/work/progress/aggregate-deal-design.md` | переехал в `history/2026-08-29-tranche-landing/` — дефект |
| `.claude/work/decision-digest.md:1697` | `.claude/work/progress/aggregate-deal-design.md` | то же — дефект |

## Неразрешимые §-адреса (8 из 14 кандидатов; 6 отсеяны как ложные)

| Носитель | Адрес | Что есть в цели фактически |
|---|---|---|
| `.claude/skills/concept-review.md:183` | `question-delegation.md` §«Калибровка» | секции нет вовсе |
| `.claude/tests/source-api/okx/plan.md:1400` | `PositionCloseResult.md` §Validation | «Структурная валидация — до маппинга» |
| `.claude/work/questions/open-questions.md:38` | `replace-not-amend.md` §Следствия | секции нет вовсе |
| `.claude/work/questions/open-questions.md:273` | `Exchange.md` §Енумы | «Енум `Status`» |
| `.claude/work/backlog.md:211` | `command-lifecycle.md` §Отложено | секции нет вовсе |
| `.claude/work/backlog.md:587` | `DealOrchestratorJob.md` §Concurrency-guard | «Операционная оболочка» |
| `.claude/work/backlog.md:880` | `replace-not-amend.md` §Решение п. 3 | «Порядок ног — по риск-классу действия» |
| `.claude/work/backlog.md:1105` | `DealActionState.md` §Персистентность | «Структура» / «Инварианты» |

## Отсеяно как ложные срабатывания (6)

| Кандидат | Почему не дефект |
|---|---|
| `tech-radar.md:45` → backlog §Унификация инфраструктуры джоб | лид-жирный пассаж есть (`backlog.md:578`), перенос строки внутри `**…**` |
| `knowledge-curator.md:45` → gap-report §«Находка-дубль…» | то же — многострочный лид-жирный |
| `design-fork.md:81` → gap-report §одноимённый | не адрес, а проза («секция одноимённая») |
| `update-roadmap-progress.md:180` → backlog §«Закрытие фазы N …» | шаблонный адрес с плейсхолдером `N` |
| `backlog.md:397` → `AlgoOrder.md` §Персистентность | не ссылка, а **утверждение** «секции не существует»; проверено — утверждение истинно |
| `pre-launch-schema-changes.md:40`, `phase-1.md:61` | секция в backlog есть, кавычки внутри имени |

## Клеймы снапшота v92 — проверены

| Клейм | Исход |
|---|---|
| «228 файлов знания в `docs/` (213 доков и 15 спецификаций)» | сошлось: 213 `.md` + 15 `.json` |
| «§-адресов внутри `docs/` — ноль» | сошлось: `grep -rn '§' docs --include=*.md` → 0 |
| Прогон `tools/spec-run.sh` зелёный | сошлось: 15 спецификаций, 178 примеров, «ВСЕ ПРИМЕРЫ СОШЛИСЬ» |
