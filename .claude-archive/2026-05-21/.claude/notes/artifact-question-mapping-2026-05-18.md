# Карта артефактов проекта по характеристическим вопросам

**Дата:** 2026-05-18
**Цель:** входная разведка для решения о структуре приёма «вопросы-маркеры» (strategy-summary v7). Не перераскладка, а карта: обнаружить артефакты, которые не лезут в пять слоёв/пять трекеров чисто, и понять масштаб проблемы.

**Кандидаты-вопросы:**

Процессные слои:
- **L-SPEC**: «что есть в системе?» (Specification)
- **L-ADR**: «почему выбрано это?» (ADR)
- **L-CONV**: «как принято в проекте?» (Convention)
- **L-SKILL**: «как выполняется операция?» (Skill)
- **L-AGENT**: «какая роль смотрит?» (Agent)

Трекеры:
- **T-SNAP**: «где мы сейчас?» (Strategy snapshot)
- **T-Q**: «что не решено?» (Open questions)
- **T-BL**: «что в работе?» (Backlog)
- **T-MIG**: «где мы в миграции?» (Migration tracker)
- **T-EVO**: «как менялся пайплайн?» (Evolution log)

Дополнительные кандидаты (фиксирую, когда чистого слоя/трекера нет):
- **M-PIPE**: «как взаимодействовать с Claude (и Claude Code)?» — операционная методология пайплайна, видимая Claude
- **M-USER**: «как мне (человеку) работать на проекте?» — операционная методология пайплайна, видимая человеку
- **M-NAV**: «куда смотреть для X?» — навигационный хаб
- **M-META**: «как устроены артефакты процесса?» — метамодель Q-N, MIGRATED-маркеров, заглушек
- **M-FORM**: «как оформить документ X?» — форматный скелет (templates)
- **M-PROCDESIGN**: «как принимать решения о пайплайне?» — методология эволюции пайплайна (отличается от M-PIPE тем, что это не «как использовать», а «как менять»)
- **L-SCRATCH**: рабочие/разведочные ноты (не категория знания, а «временное хранилище»)
- **LEGACY**: вне текущей карты

---

## Сводная таблица

| Файл / папка | Основной вопрос | Сопутствующие | Комментарий |
|---|---|---|---|
| `CLAUDE.md` | **M-NAV** (навигация по вопросам) | M-PIPE, L-CONV (key conventions, source hierarchy, auto-staging) | Хаб-файл. Содержит карты Process layers и Trackers, иерархию источников, repo layout, конвенции работы Claude Code. Сам **является** реализацией приёма «вопросы-маркеры» на уровне корня. |
| `.claude/working-with-claude.md` | **M-PIPE** (concept of working) | M-PROCDESIGN, L-CONV (Principle 4 — new term routing), L-AGENT (команда агентов — описание), L-ADR meta (правила работы с ADR) | СМЕШАННЫЙ. См. подраздел «Смешанные файлы» ниже. |
| `.claude/project-instructions.md` | **M-PIPE** для чата (claude.ai), не для Claude Code | L-CONV (terminology rule 12), операционные процедуры (rules 0, 3, 8), разделение ролей (rule 5, 11), стиль (rule 13, 14) | СМЕШАННЫЙ. Резервная копия Custom Instructions для claude.ai. Аналог CLAUDE.md, но для чата. Подмешаны и правила (convention), и процедуры (skill-like), и meta-роли. |
| `.claude/flow/playbook.md` | **M-USER** (операционная инструкция для человека) | M-PROCDESIGN (сценарии 2-6 — как менять пайплайн); L-SKILL частично пересекается (сценарии указывают на скиллы) | Прямо во введении: «Это не справочник для Claude. Это твоя памятка». Не вписывается в 5 слоёв чисто — это операционка для человека, а не для Claude Code. |
| `.claude/pipeline-evolution-log.md` | **T-EVO** | — | Чистый трекер. По канону. |
| `.claude/strategy-summary/strategy-summary-v1.md` | **T-SNAP** (исторический) | T-EVO частично (каждый snapshot — точка в истории пайплайна) | v1-v6 — застывшие снимки. По канону отвечают T-SNAP в момент создания; сейчас де-факто работают как T-EVO (срезы истории). |
| `.claude/strategy-summary/strategy-summary-v2.md` | T-SNAP (исторический) | T-EVO | то же |
| `.claude/strategy-summary/strategy-summary-v3.md` | T-SNAP (исторический) | T-EVO | то же |
| `.claude/strategy-summary/strategy-summary-v4.md` | T-SNAP (исторический) | T-EVO | то же |
| `.claude/strategy-summary/strategy-summary-v5.md` | T-SNAP (исторический) | T-EVO | то же |
| `.claude/strategy-summary/strategy-summary-v6.md` | T-SNAP (исторический) | T-EVO | то же |
| `.claude/strategy-summary/strategy-summary-v7.md` | **T-SNAP** (актуальный) | T-BL, T-EVO, M-PROCDESIGN (раздел про редизайн пайплайна — методология) | По канону T-SNAP. Текущий v7 содержит ещё и план следующих шагов (близко к T-BL) и анализ редизайна (близко к M-PROCDESIGN). |
| `.claude/planning/backlog.md` | **T-BL** | — | Чистый трекер. |
| `.claude/planning/migration-tracker.md` | **T-MIG** | — | Чистый трекер. |
| `.claude/questions/open-questions.md` | **T-Q** | L-SKILL (раздел «Как пользоваться» — операционные правила) | В основном трекер; шапка содержит операционную инструкцию — частично пересекается с скиллом `open-questions-workflow`. |
| `.claude/adr/README.md` | **M-NAV** (индекс ADR) | L-CONV (правила работы с ADR — append-only, типы примечаний), L-SKILL частично («Алгоритм работы с ADR» — это процедура) | Хаб-документ. Совмещает индекс + правила + процедуру. |
| `.claude/adr/0001-reconcile-via-anomaly-report.md` | **L-ADR** | — | Чисто. |
| `.claude/adr/0002-spec-document-standard.md` | **L-ADR** | L-CONV (стандарт документа как convention) | Чисто как ADR, но содержание — стандарт оформления = convention о формате. Граница «ADR vs convention» — наследственная (см. развилку). |
| `.claude/adr/0003-meta-docs-relocation.md` | **L-ADR** | — | Чисто. |
| `.claude/adr/0004-spec-templates-relocation.md` | **L-ADR** | — | Чисто. |
| `.claude/adr/0005-adr-template-relocation.md` | **L-ADR** | — | Чисто. |
| `.claude/adr/0006-spec-principles.md` | **L-ADR** | L-CONV (принципы декомпозиции — convention для авторов spec) | Так же, как ADR-0002: ADR содержит правила, которые сами по себе живут как convention. |
| `.claude/adr/0007-spec-structure.md` | **L-ADR** | L-CONV (структура каталогов — convention) | то же |
| `.claude/adr/0008-open-questions-journal.md` | **L-ADR** | M-META (метамодель журнала: 11 полей entry, 10 classification, статусы) | ADR-обоснование + метамодель артефакта журнала. |
| `.claude/adr/0009-domain-migration-strategy.md` | **L-ADR** | M-PROCDESIGN (стратегия миграции — методология процесса) | ADR-обоснование стратегии + операционный план. |
| `.claude/skills/spec-document-workflow/SKILL.md` | **L-SKILL** | L-ADR (выжимка из ADR-0002), L-CONV (стандарт формата) | См. разведку 2026-05-18 — аналогичная по структуре проблема, что и у `spec-document-migration`. |
| `.claude/skills/spec-document-migration/SKILL.md` | **L-SKILL** | L-ADR, L-CONV, M-META | См. отдельный отчёт `spec-document-migration-decomposition-2026-05-18.md` — детальный разбор. |
| `.claude/skills/spec-cluster-migration/SKILL.md` | **L-SKILL** | M-PROCDESIGN (методология кластерной миграции), L-CONV (правила hard gates) | Оркестратор миграции, часть операционная (вызовы), часть методологическая (4 этапа, gates). |
| `.claude/skills/spec-models-registry/SKILL.md` | **L-SKILL** | L-CONV (формат записи в реестре), L-SPEC меta (что такое MODELS.md) | Операционка + правила оформления реестра. |
| `.claude/skills/open-questions-workflow/SKILL.md` | **L-SKILL** | L-ADR (выжимка из ADR-0008), M-META (10 classification — категории артефакта Q-N) | Операционка + метамодель журнала, ссылается на ADR. |
| `.claude/agents/README.md` | **M-NAV** (индекс агентов по вопросам) | L-AGENT (краткое описание каждой роли) | Индекс. По букве — точка входа в L-AGENT, но сама не агент, а навигация. Идеальный пример «приём вопросов-маркеров на подчинённом уровне». |
| `.claude/agents/architect.md` | **L-AGENT** | — | Чисто. |
| `.claude/agents/domain-expert.md` | **L-AGENT** | — | Чисто. |
| `.claude/agents/risk-engineer.md` | **L-AGENT** | — | Чисто. |
| `.claude/agents/trading-risk-officer.md` | **L-AGENT** | — | Чисто. |
| `.claude/agents/knowledge-curator.md` | **L-AGENT** | M-PROCDESIGN (knowledge capture rules — методология) | Содержит операционные проверки (как curator работает) — но это часть промпта агента, а не отдельный артефакт. |
| `.claude/templates/README.md` | **M-NAV** + **M-FORM** | L-CONV (соответствие шаблон↔жанр) | Индекс + правила соответствия. |
| `.claude/templates/documents/adr.md` | **M-FORM** | — | Форматный скелет, не вписывается в 5 слоёв чисто. См. развилку. |
| `.claude/templates/documents/model.md` | **M-FORM** | — | то же |
| `.claude/templates/documents/lifecycle.md` | **M-FORM** | — | то же |
| `.claude/templates/documents/process.md` | **M-FORM** | — | то же |
| `.claude/templates/documents/integration-mapping.md` | **M-FORM** | — | то же |
| `.claude/templates/documents/reference.md` | **M-FORM** | — | то же |
| `.claude/templates/documents/invariant.md` | **M-FORM** | — | то же |
| `.claude/templates/documents/open-questions-journal.md` | **M-FORM** | T-Q meta | Шаблон журнала. На границе M-FORM и метамодели артефакта. |
| `.claude/notes/migration-dryrun-C4-C2-2026-05-17.md` | **L-SCRATCH** | T-SNAP-like (срез прогона) | Промежуточный отчёт V1 dry-run. |
| `.claude/notes/migration-dryrun-analysis-2026-05-17.md` | **L-SCRATCH** | — | Анализ dry-run. |
| `.claude/notes/migration-inventory-2026-05-15.md` | **L-SCRATCH** | T-MIG-like (срез) | Разведка legacy. |
| `.claude/notes/model-catalog-2026-05-15.md` | **L-SCRATCH** | T-MIG-like | Разведка моделей. |
| `.claude/notes/spec-document-migration-decomposition-2026-05-18.md` | **L-SCRATCH** | — | Вчерашняя разведка по приёму. |
| `.claude/notes/adr-drafts/` | **L-SCRATCH** | T-EVO-like (журналы правок) | Черновики ADR + журналы правок по сценарию 10. |
| `.claude/settings.local.json` | — | — | Конфигурация инструмента Claude Code (permissions), не часть карты знаний. |
| `docs/README.md` | **M-NAV** | L-CONV (легенда `[MIGRATED]`) | Продуктовый навигационный хаб (для `docs/`). |
| `docs/conventions/terminology.md` | **L-CONV** | — | Чисто. |
| `docs/spec/MODELS.md` | **L-SPEC** | M-NAV (точка входа в `docs/spec/`) | Реестр + навигация. |
| `docs/spec/models/core/AnomalyReport.md` | **L-SPEC** | — | Чисто. |
| `docs/spec/lifecycle/AnomalyReport.md` | **L-SPEC** | — | Чисто. |
| `docs/domain/Открытые вопросы по движку.md` | **LEGACY** | (целевой слой при миграции — T-Q + L-SPEC) | Phase 3 migration. |
| `docs/domain/Справочник по доменным моделям.md` | **LEGACY** | L-SPEC при миграции | Phase 1, кластеры C-NEW, C8. |
| `docs/domain/models/AlgoOrder.md` | **LEGACY** | L-SPEC | Phase 1, C2. |
| `docs/domain/models/Balance.md` | **LEGACY** | L-SPEC | Phase 1, C4. |
| `docs/domain/models/Deal.md` | **LEGACY** | L-SPEC | Phase 1, C1. |
| `docs/domain/models/Order.md` | **LEGACY** | L-SPEC | Phase 1, C2. |
| `docs/domain/models/Position.md` | **LEGACY** | L-SPEC | Phase 1, C2. |
| `docs/domain/models/Strategy.md` | **LEGACY** | L-SPEC | Phase 1, C5. |
| `docs/domain/models/Strategy API examples.md` | **LEGACY** | L-SPEC / docs/api | Phase 1, C5. |
| `docs/domain/models/Справочник по доменным моделям.md` | **LEGACY** | L-SPEC | то же что выше (один файл, продублированная ссылка) |
| `docs/domain/models/mapping/okx/*` | **LEGACY** | L-SPEC integration | Phase 2 migration. |
| `docs/domain/processes/Audit/Аудит и история исполнения.md` | **LEGACY** | L-SPEC | Phase 1, C8. |
| `docs/domain/processes/Calculation/Калькуляторы действий стратегии.md` | **LEGACY** | L-SPEC | Phase 1, C7. |
| `docs/domain/processes/Calculation/Оценка рисков.md` | **LEGACY** | L-SPEC | Phase 1, C7. |
| `docs/domain/processes/Calculation/Расчёт индикаторов и рыночных данных.md` | **LEGACY** | L-SPEC | Phase 1, C6 + C-NEW (фрагменты по Instrument). |
| `docs/domain/processes/Deal management/FSM этапы сделки.md` | **LEGACY** | L-SPEC | Phase 1, C1. |
| `docs/domain/processes/Deal management/Жизненный цикл сделки.md` | **LEGACY** | L-SPEC | Phase 1, C1. |
| `docs/domain/processes/Deal management/Сервисные команды.md` | **LEGACY** | L-SPEC | Phase 1, C7 + C2 (§12). |
| `docs/domain/processes/Deal management/Статусы торговых сущностей.md` | **LEGACY** | L-SPEC | Shared-файл, фрагменты по C-NEW, C2, C4, C8. |
| `docs/domain/generated/` | LEGACY (пустая) | — | — |
| `docs/api/API стратегии.md` | **LEGACY** | L-SPEC API | Phase 4. |
| `docs/api/Справочник по API сервиса.md` | **LEGACY** | L-SPEC API | Phase 4. |
| `docs/api/okx/*` (25 файлов) | **LEGACY** | L-SPEC API + L-SPEC integration | Phase 4. |
| `docs/planning/00..08_veha_*.md` | **LEGACY** | T-BL-like (long-term milestones) | Унаследованный roadmap, отдельный от текущего backlog. |
| `docs/planning/README.md` | **LEGACY** | M-NAV | то же |
| `docs/planning/execution/` | **LEGACY** | — | — |
| `docs/ops/vault-local.md`, `vault_local.md` | **L-SPEC** (operations) | — | Операционные заметки про vault — продуктовая операционка, не пайплайн. |
| `docs/context/*` (6 файлов + `comands/`) | **LEGACY** | — | Архив. По CLAUDE.md — приоритет 5. |
| `docs/deprecated/models/`, `scenario/`, `tasks/` | **LEGACY** | — | Архив (приоритет 5). |
| `docs/old/`, `docs/review/` | **LEGACY** | — | Архив. |
| `docs/adr/` | — | — | Не существует. По ADR-0003 ADR живут в `.claude/adr/`. |

---

## Группировка по вопросам

### L-SPEC «что есть в системе?» (4 + ops + legacy)
- `docs/spec/MODELS.md`
- `docs/spec/models/core/AnomalyReport.md`
- `docs/spec/lifecycle/AnomalyReport.md`
- `docs/ops/vault-local.md`, `vault_local.md` (продуктовая операционка — пограничный случай, см. развилку)
- (косвенно, при миграции — все `docs/domain/`, `docs/api/`)

### L-ADR «почему выбрано это?» (9)
- `.claude/adr/0001-reconcile-via-anomaly-report.md` (Accepted)
- `.claude/adr/0002-spec-document-standard.md` (Accepted, содержательно ≈ convention)
- `.claude/adr/0003-meta-docs-relocation.md` (Accepted)
- `.claude/adr/0004-spec-templates-relocation.md` (Accepted)
- `.claude/adr/0005-adr-template-relocation.md` (Accepted)
- `.claude/adr/0006-spec-principles.md` (Proposed, содержательно ≈ convention)
- `.claude/adr/0007-spec-structure.md` (Proposed, содержательно ≈ convention)
- `.claude/adr/0008-open-questions-journal.md` (Proposed, содержательно ≈ метамодель)
- `.claude/adr/0009-domain-migration-strategy.md` (Proposed, содержательно ≈ методология миграции)

### L-CONV «как принято в проекте?» (1)
- `docs/conventions/terminology.md`

### L-SKILL «как выполняется операция?» (5)
- `.claude/skills/spec-document-workflow/SKILL.md`
- `.claude/skills/spec-document-migration/SKILL.md`
- `.claude/skills/spec-cluster-migration/SKILL.md`
- `.claude/skills/spec-models-registry/SKILL.md`
- `.claude/skills/open-questions-workflow/SKILL.md`

### L-AGENT «какая роль смотрит?» (5)
- `.claude/agents/architect.md`
- `.claude/agents/domain-expert.md`
- `.claude/agents/risk-engineer.md`
- `.claude/agents/trading-risk-officer.md`
- `.claude/agents/knowledge-curator.md`

### Трекеры (5)
- T-SNAP: `.claude/strategy-summary/strategy-summary-v7.md` (актуальный); v1-v6 — исторические снимки
- T-Q: `.claude/questions/open-questions.md`
- T-BL: `.claude/planning/backlog.md`
- T-MIG: `.claude/planning/migration-tracker.md`
- T-EVO: `.claude/pipeline-evolution-log.md`

### Расширенные кандидаты-вопросы

**M-NAV «куда смотреть для X?»** (4 хаба + 1 продуктовый)
- `CLAUDE.md`
- `.claude/adr/README.md`
- `.claude/agents/README.md`
- `.claude/templates/README.md`
- `docs/README.md`

**M-PIPE «как взаимодействовать с Claude (Claude Code)?»** (1 с дополнением)
- `.claude/working-with-claude.md` (основной)
- `CLAUDE.md` (Working with Claude Code in this project)
- `.claude/project-instructions.md` — то же, но **для чата claude.ai**, не для Claude Code

**M-USER «как мне (человеку) работать на проекте?»** (1)
- `.claude/flow/playbook.md`

**M-FORM «как оформить документ X?»** (8 шаблонов + 1 README)
- `.claude/templates/documents/{adr,model,lifecycle,process,integration-mapping,reference,invariant,open-questions-journal}.md`
- `.claude/templates/README.md` (частично M-NAV, частично M-FORM)

**M-META «как устроены артефакты процесса?»** (распределено внутри других)
- Фрагменты в ADR-0008 (метамодель Q-N — 11 полей entry, 10 classification, статусы)
- Фрагменты в `spec-document-migration` (метамодель MIGRATED-маркера, заглушки spec-документа) — см. отчёт вчерашний
- Фрагменты в `open-questions-workflow` (структура entry — продублирована из ADR-0008)
- Отдельного места нет.

**M-PROCDESIGN «как принимать решения о пайплайне?»** (распределено)
- `working-with-claude.md` (Принцип 7 — meta-правила про скиллы/агенты как источники применения; режим as-if-Accepted)
- `playbook.md` (сценарии 2-6, 10 — методология эволюции пайплайна)
- ADR-0009 (методология миграционной стратегии)
- v7 snapshot, раздел «Редизайн пайплайна» (текущий мета-разбор)
- Отдельного места нет.

**L-SCRATCH «временное рабочее»** (5 + папка)
- `.claude/notes/migration-dryrun-C4-C2-2026-05-17.md`
- `.claude/notes/migration-dryrun-analysis-2026-05-17.md`
- `.claude/notes/migration-inventory-2026-05-15.md`
- `.claude/notes/model-catalog-2026-05-15.md`
- `.claude/notes/spec-document-migration-decomposition-2026-05-18.md`
- `.claude/notes/adr-drafts/`

**LEGACY** (вне текущей карты, ~ 50 файлов)
- `docs/domain/`, `docs/api/`, `docs/planning/00-08*`, `docs/context/`, `docs/deprecated/`, `docs/old/`, `docs/review/`

---

## «Неклассифицируемые» (не лёгшие ни в один вопрос даже после расширения)

Строгий «неклассифицируемый» — таких нет. Расширенный список кандидатов (M-NAV, M-PIPE, M-USER, M-FORM, M-META, M-PROCDESIGN, L-SCRATCH) покрыл всё.

**Но** — следующие категории появились **только за счёт расширения** и сигнализируют о пробеле в пяти слоях:

1. **M-NAV** — хабы (5 шт.). Канон «5 слоёв» не предусматривает навигационных файлов; они нужны как точки входа на каждом уровне.
2. **M-USER** — playbook для человека (1 шт.). Пять слоёв ориентированы на Claude/Claude Code; операционка человека не вписывается.
3. **M-PIPE** — два варианта (CC + chat) (3 шт.). Разделение «как работает Claude Code» vs «как ведёт себя чат claude.ai» — внутри одного слоя «как взаимодействовать с Claude» сидят два разных адресата.
4. **M-FORM** — шаблоны (9 шт.). Скелет документа — не «как делать» (это в скилле) и не «как принято» (там — правила) и не «что есть» (там — содержание).
5. **M-META** — метамодели артефактов процесса (Q-N, MIGRATED-маркер, заглушка spec) — **не имеют своего места**, распределены по ADR + скиллам + шаблонам. Это то, что вчерашняя разведка по `spec-document-migration` уже зафиксировала как Р-9.
6. **M-PROCDESIGN** — методология эволюции пайплайна (в отличие от методологии использования) — тоже **не имеет своего места**. Распределена.
7. **L-SCRATCH** — `.claude/notes/`. Временные артефакты процесса; не категория знания. Канон молчит.

---

## «Смешанные» (несколько вопросов внутри одного файла)

### 1. `CLAUDE.md`
- **«How to work on this project»** — M-PIPE (отсылка)
- **«Process layers»** — M-NAV (определение карты)
- **«Trackers»** — M-NAV
- **«Documentation organization»** — L-CONV (разделение `docs/` vs `.claude/`)
- **«Source of truth hierarchy»** — L-CONV (правило разрешения конфликтов)
- **«Repository layout»** — M-NAV
- **«Key project conventions»** — L-CONV (HTTP-клиент, миграции, exchange, domain dependency)
- **«Conflicts and ambiguities»** — L-CONV
- **«Working with Claude Code in this project»** — M-PIPE (auto-staging, не коммитить, knowledge-curator)

Файл функционально мета-индекс + свод проектных конвенций. Это **намеренный** хаб; разделять на отдельные файлы не стоит, но факт смешения отметить.

### 2. `.claude/working-with-claude.md`
- **«Базовые принципы 1-7»** — M-PIPE + M-PROCDESIGN
  - Принцип 1 (чат vs CC) — M-PIPE
  - Принцип 2 (знания в файлах) — M-PIPE / L-CONV
  - Принцип 3 (иерархия) — L-CONV (дубль CLAUDE.md)
  - Принцип 4 (связное обновление + new term routing) — L-CONV
  - Принцип 5 (CC не коммитит) — M-PIPE
  - Принцип 6 (док — источник истины) — L-CONV
  - Принцип 7 (skills/agents как источник применения) — M-PROCDESIGN
- **«Поэтапная миграция документации»** — M-PROCDESIGN (методология) + L-CONV (правила маркеров)
- **«Команда агентов»** — L-AGENT (описание ролей)
- **«Скиллы»** — описание категории (≈ M-NAV для скиллов)
- **«ADR — архитектурные решения»** — L-CONV (правила работы) + M-PROCDESIGN (режим as-if-Accepted)
- **«Project Knowledge в claude.ai»** — L-CONV (правила PK)
- **«Проверка консистентности PK»** — L-SKILL (процедура) + M-PIPE (правило)
- **«Knowledge capture rules»** — L-AGENT meta (зашитая инструкция в агентов)

### 3. `.claude/project-instructions.md`
- **Rule 0** (PK consistency check) — L-SKILL (процедура)
- **Rule 1** (source hierarchy) — L-CONV (дубль)
- **Rule 2** (capture knowledge) — M-PIPE
- **Rule 3** (deliverable format) — L-CONV (формат ответа)
- **Rule 4** (explicit paths) — L-CONV
- **Rule 5** (chat vs CC split) — M-PIPE
- **Rule 6** (language) — L-CONV
- **Rule 7** (scenarios from playbook) — M-PIPE (ссылка)
- **Rule 8** (connected updates) — L-CONV (дубль)
- **Rule 9** (no commit) — L-CONV (дубль)
- **Rule 10** (PK updates) — L-SKILL
- **Rule 11** (clarifying via CC) — M-PIPE
- **Rule 12** (terminology) — L-CONV (дубль terminology.md)
- **Rule 13** (short style) — L-CONV (формат ответа чата)
- **Rule 14** (discussion format) — L-CONV (формат ответа)

Внутри файла — много дублей правил из других мест. Это намеренная компиляция «для чата». Но архитектурно — единая роль чата живёт распылённо.

### 4. `.claude/flow/playbook.md`
- **«Базовые роли»**, **«Чат vs CC»**, **«Гигиена работы»** — M-PIPE (описание ролей и базового цикла)
- **Сценарии 1, 7** (ADR, spec-документ) — M-USER (операционка человека)
- **Сценарии 2-5** (агенты, скиллы) — M-PROCDESIGN (как менять пайплайн)
- **Сценарий 6** (ретроспектива) — M-PROCDESIGN
- **Сценарий 8** (PK consistency) — L-SKILL (процедура)
- **Сценарий 9** (миграция кластера) — отсылка к L-SKILL
- **Сценарий 10** (dry-run итерация) — M-PROCDESIGN
- **Чек-листы** — M-USER

### 5. `.claude/adr/README.md`
- **«Что такое ADR»** — M-PROCDESIGN
- **«Когда создавать ADR»** — L-CONV
- **«Статусы ADR»** — M-META (метамодель ADR как артефакта)
- **«Алгоритм работы с ADR»** — L-SKILL (процедура)
- **«Два типа примечаний»** — L-CONV / M-META
- **«Шаблон»** — M-NAV
- **«Индекс»** — M-NAV
- **«Связанные документы»** — M-NAV

### 6. ADR-0002, ADR-0006, ADR-0007
Внутри: содержательное решение (L-ADR) + сами правила/принципы, которые **являются** convention (L-CONV) о формате/декомпозиции/структуре spec-документов. По принципу 7 working-with-claude (skills/agents как источник применения) — правила должны жить в скиллах, ADR — обоснование. Сейчас правила сидят и в ADR, и в скиллах (частично продублированы). Это известный системный паттерн, не уникальный смешанный случай.

### 7. ADR-0008
Внутри: решение о создании журнала (L-ADR) + полная **метамодель** артефакта (11 полей entry, 10 classification, статусы, условия устаревания). Метамодель — M-META, для неё нет отдельного слоя; она оседает в ADR как «обоснование структуры».

### 8. ADR-0009
Внутри: решение о стратегии (L-ADR) + **методология процесса** (6 фаз, 8 кластеров, hard gates, единица миграции). Методология — M-PROCDESIGN.

### 9. `.claude/questions/open-questions.md`
- **«Как пользоваться»** — L-SKILL (операционка, дубль шапки скилла `open-questions-workflow`)
- **Open/Closed разделы** — T-Q (трекер)
- **Q-EXAMPLE** — M-FORM (форматный пример)

### 10. `.claude/skills/*` (все 5 скиллов)
Внутри каждого: операционка (L-SKILL основное) + правила формата (L-CONV) + критерии/принципы (L-ADR) + метамодели артефактов (M-META). Эта смесь — то, что подсветил вчерашний детальный разбор `spec-document-migration`. Аналогичные пятна — в остальных скиллах.

### 11. `.claude/templates/documents/open-questions-journal.md`
- Шаблон документа (M-FORM)
- Но этот «шаблон» содержит и шапку (как пользоваться) — её копию мы видим как раздел в самом журнале. Это **скелет + операционка**.

### 12. `.claude/strategy-summary/strategy-summary-v7.md`
- **«Контекст на текущий момент»**, **«Состав пакета ADR»** — T-SNAP
- **«Редизайн пайплайна»** — M-PROCDESIGN (методология) + T-SNAP (история изменения)
- **«Принятые концептуальные решения»** — T-SNAP, но по существу — список зафиксированных решений (близко к индексу ADR-like)
- **«План работы»** — T-BL (план шагов)
- **«Что НЕ делать»** — L-CONV частично (зафиксированные запреты)

Snapshot — намеренно содержит всё в одном месте. Это «срез на дату», и смесь в нём ожидаема.

---

## Сводка

**Количественно (по основному вопросу):**

| Слой / трекер / расширенный | Файлов |
|---|---|
| L-SPEC | 3 (актуальные) + 2 ops + ~50 legacy |
| L-ADR | 9 |
| L-CONV | 1 |
| L-SKILL | 5 |
| L-AGENT | 5 |
| T-SNAP (актуальный) | 1 |
| T-Q | 1 |
| T-BL | 1 |
| T-MIG | 1 |
| T-EVO | 1 |
| **M-NAV** (новый) | **5** |
| **M-PIPE** (новый) | **3** (working-with-claude.md, project-instructions.md, CLAUDE.md как basis) |
| **M-USER** (новый) | **1** (playbook) |
| **M-FORM** (новый) | **8 шаблонов + 1 README** |
| **M-META** (новый) | **0 (распределено)** |
| **M-PROCDESIGN** (новый) | **0 (распределено)** |
| **L-SCRATCH** | **5 файлов + 1 папка** |
| T-SNAP исторические (v1-v6) | 6 |
| LEGACY (вне карты) | ~50 |
| settings.local.json (конфиг) | 1 |

**«Неклассифицируемых» в строгом смысле:** 0 — все ложатся, но за счёт расширения списка вопросов на 6 категорий (M-NAV, M-PIPE, M-USER, M-FORM, M-META, M-PROCDESIGN) + L-SCRATCH.

**«Смешанных»** (внутри файла несколько вопросов): 12 крупных кейсов. Это:
- 4 хаба (CLAUDE.md, working-with-claude.md, project-instructions.md, adr/README.md) — намеренно смешаны, это их функция;
- playbook.md — смешан по сценариям;
- ADR-0002/0006/0007/0008/0009 — содержат L-ADR + правила/метамодели/методологию, которые сами тянут на отдельные слои;
- все 5 скиллов — типичная смесь L-SKILL + L-CONV + L-ADR + M-META (паттерн, не уникальные кейсы);
- open-questions.md — трекер с встроенной шапкой-скиллом;
- snapshot v7 — намеренно «всё в одном месте на дату».

**Масштаб проблемы пяти слоёв:**

1. **Пять слоёв покрывают «слой знания», но не покрывают «навигацию»**, «методологию пайплайна», «формат», «метамодель артефактов процесса». M-NAV + M-USER + M-FORM + M-META + M-PROCDESIGN — это либо отдельные категории, либо признание, что эти артефакты должны жить **внутри** существующих слоёв как поджанры. Сейчас они распылены без явного приёма.

2. **Пять процессных слоёв предполагают «один файл — один вопрос»**, но 12 крупных файлов смешивают вопросы намеренно (хабы, snapshot, скиллы). Это не ошибка — это рабочая практика. Приём «вопросы-маркеры» должен либо разрешить намеренные смеси (через подразделы-маркеры внутри файла), либо нарезать хабы на части.

3. **ADR содержат convention/метамодель/методологию как побочный продукт**. Это явное напряжение с Принципом 7 working-with-claude.md («skills и agents — источник применения, ADR — обоснование»): сами правила, прикладные критерии, метамодели сейчас живут одновременно в ADR и в скиллах. После пакета ADR-0006..0009 этот рассинхрон — в полный рост.

4. **«Метамодель артефактов процесса» (M-META) — настоящий пробел.** Q-N как сущность (11 полей, 10 classification), MIGRATED-маркер (формат, правила), spec-документ-заглушка (валидное состояние) — все они не имеют своего места. Распылены по ADR-0008, шаблонам, скиллам. Это то, что вчерашняя разведка по `spec-document-migration` зафиксировала как Р-9; теперь видно, что пробел системный, не локальный.

5. **`docs/ops/` — пограничный случай.** Vault-заметки — это продуктовая операционка (как настроить инфру), не пайплайн. Сейчас лежат в `docs/`, по букве — L-SPEC operations. Если приём «вопросы-маркеры» строит на нём правило, нужно решить, что делать с операционкой продукта (не системы).

6. **`docs/planning/` (00-08_veha_*)** — унаследованный roadmap, отдельный от `.claude/planning/backlog.md`. Двойная очередь планирования: продуктовая long-term (vehas) и рабочая short/mid-term (backlog). Канон трекеров (T-BL) видит только второе. Это известный пробел (см. backlog: «Создание docs/ROADMAP.md» — Later), не уникален для разведки, но фиксирую.

**Сигнал на приём «вопросы-маркеры»:**

Из ~80 файлов (без legacy) ~50 классифицируются чисто. Остальные ~30 либо распределены по новым кандидатам-вопросам (M-NAV, M-FORM, L-SCRATCH — ~18), либо смешаны намеренно (~12 хабов/скиллов/ADR). Чистый канон «5 + 5» покрывает ~60% артефактов; ещё ~25% требуют расширения списка вопросов; ~15% — намеренно смешаны и не разрешаются нарезкой.

Если масштабировать приём «вопросы-маркеры»:
- Либо **расширить набор вопросов** до 7-8 категорий (добавить M-NAV, M-FORM, и сделать что-то с M-META и M-PROCDESIGN).
- Либо **признать намеренные смеси легитимными**, и тогда приём — это разметка **внутри** файла, не правило размещения.
- Либо **гибрид**: 5 слоёв как структура размещения, дополнительные вопросы — как способ разметить разделы внутри хабов и снимков.
