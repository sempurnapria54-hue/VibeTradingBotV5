# Инвентарь упоминаний ADR в репозитории

**Дата:** 2026-05-19.
**Цель.** Собрать исчерпывающий инвентарь всех мест в репозитории, где
упоминаются текущие 9 ADR (`0001`…`0009` в `.claude/adr/`), чтобы при
Этапе 1 рефакторинга нумерации (пересборка в `ADR-PIPELINE-0001..0006` +
`ADR-PRODUCT-0001`, удаление 0003/0004/0005) ни одна ссылка не потерялась.

**Тип работы:** разведка. Файлы не двигаются, ссылки не правятся, тип
упоминания фиксируется без додумывания.

**Область разведки.** Исключены: `docs/domain/`, `docs/api/`,
`docs/planning/` (legacy), `docs/context/`, `docs/deprecated/`,
`docs/old/`, `docs/review/`. Прочитаны: всё в `.claude/`,
`docs/README.md`, `docs/conventions/`, `docs/spec/`, корневой
`CLAUDE.md`.

---

## Сводка

- **Файлов с упоминаниями ADR:** 45 (грубо, по матчам `ADR-\d{4}`).
- **Всего совпадений `ADR-\d{4}`:** 757.
- **Активные файлы (правки нужны при рефакторинге):** ~30 (см. §3.1).
- **Исторические append-only файлы (правки не нужны, но контекст
  устаревает):** ~15 (strategy-summary v1–v6, pipeline-evolution-log,
  `notes/adr-drafts/`, `notes/migration-*`, `notes/migration-inventory`).

Список фактов, выявленных по ходу инвентаря, но не сводимых к одному
упоминанию ADR, — в §5 (Открытые вопросы).

---

## 1. Таблица упоминаний по ADR

«Активные» — файлы, которые редактируются по ходу проекта и где ссылки
надо обновить при рефакторинге. «Исторические» — append-only снимки и
журналы (strategy-summary v1–v6, `pipeline-evolution-log.md`,
`notes/adr-drafts/`, `notes/migration-*`, `notes/migration-inventory`) —
их трогать нельзя, но контекст становится непригодным.

| ADR | Всего | Активные файлы (без adr/) | Внутри `.claude/adr/` (между ADR) | Исторические |
|---|---|---|---|---|
| 0001 | 24 | ~7 | ~10 | ~7 |
| 0002 | 100+ | ~20 | ~24 | ~50+ |
| 0003 | 50+ | ~10 | ~16 | ~25 |
| 0004 | 40+ | ~7 | ~17 | ~20 |
| 0005 | 18 | ~3 | ~10 | ~5 |
| 0006 | 150+ | ~15 | ~26 | ~80+ |
| 0007 | 80+ | ~12 | ~22 | ~40+ |
| 0008 | 60+ | ~14 | ~16 | ~25 |
| 0009 | 80+ | ~10 | ~14 | ~50+ |

Цифры в активных/внутри-adr колонках точные снизу по выборке грепов;
«исторические» — оценка по counts на файл (snapshot v1–v6 +
pipeline-evolution-log + notes/adr-drafts + notes/migration-*).

---

## 2. Топ-10 файлов по концентрации упоминаний

Из `ADR-\d{4}` counts:

| Ранг | Файл | Кол-во | Статус |
|---|---|---|---|
| 1 | `.claude/notes/migration-dryrun-C4-C2-2026-05-17.md` | 89 | historic (dry-run) |
| 2 | `.claude/pipeline-evolution-log.md` | 44 | append-only journal |
| 3 | `.claude/notes/migration-inventory-2026-05-15.md` | 42 | historic (snapshot разведки) |
| 4 | `.claude/adr/0009-domain-migration-strategy.md` | 39 | active ADR (Proposed) |
| 5 | `.claude/planning/backlog.md` | 38 | active |
| 6 | `.claude/strategy-summary/strategy-summary-v5.md` | 36 | historic snapshot |
| 7 | `.claude/notes/adr-drafts/0009-domain-migration-strategy.md` | 35 | historic draft |
| 8 | `.claude/adr/0007-spec-structure.md` | 32 | active ADR (Proposed) |
| 9 | `.claude/notes/migration-dryrun-analysis-2026-05-17.md` | 29 | historic (dry-run analysis) |
| 10 | `.claude/strategy-summary/strategy-summary-v6.md` | 27 | historic snapshot |

Дополнительно к топ-10: `strategy-summary-v3.md` (24),
`notes/adr-drafts/0007-spec-structure.md` (23),
`notes/spec-document-migration-decomposition-2026-05-18.md` (22),
`adr/0004-spec-templates-relocation.md` (22),
`notes/pipeline-spec-fitting-2026-05-18.md` (20),
`adr/0006-spec-principles.md` (20),
`strategy-summary-v2.md` (20),
`adr/0008-open-questions-journal.md` (18),
`adr/0005-adr-template-relocation.md` (18),
`adr/0004-spec-templates-relocation.md` (как выше),
`notes/adr-drafts/0008-open-questions-journal.md` (17),
`strategy-summary-v7.md` (16),
`strategy-summary-v4.md` (16),
`notes/adr-drafts/0006-spec-principles.md` (15),
`notes/artifact-question-mapping-2026-05-18.md` (13),
`adr/0003-meta-docs-relocation.md` (13),
`notes/adr-drafts/0009-review-progress.md` (12),
`adr/0002-spec-document-standard.md` (10),
`adr/0001-reconcile-via-anomaly-report.md` (3).

**Главные точки внимания при рефакторинге** (активные, не historic):

1. `.claude/planning/backlog.md` — 38 упоминаний, активный документ,
   ссылки в обе стороны (на старые номера ADR-0001..0009).
2. `.claude/adr/0009-domain-migration-strategy.md` — 39 упоминаний;
   сам мигрирует в ADR-PIPELINE-0005 и содержит ссылки на все
   ADR-0002..0008.
3. `.claude/adr/0007-spec-structure.md` — 32 упоминания; мигрирует в
   ADR-PIPELINE-0003.
4. `.claude/adr/0006-spec-principles.md` — 20 упоминаний; мигрирует
   в ADR-PIPELINE-0002.
5. `.claude/adr/0008-open-questions-journal.md` — 18; мигрирует
   в ADR-PIPELINE-0004.
6. `.claude/adr/0002-spec-document-standard.md` — 10; мигрирует
   в ADR-PIPELINE-0001.
7. `.claude/adr/0004-spec-templates-relocation.md` (22),
   `0005-adr-template-relocation.md` (18),
   `0003-meta-docs-relocation.md` (13) — **удаляются**; все ссылки
   на них требуют переадресации или удаления (см. §3.4).
8. `.claude/strategy-summary/strategy-summary-v7.md` — 16
   упоминаний; текущий снимок, остаётся активным до создания v8.

---

## 3. Сводный анализ

### 3.1. Активные файлы (правки потребуются)

Это файлы, на которые рефакторинг должен распространиться. Группировка
по природе. Точные строки и контекст — в §4.

| Группа | Файлы |
|---|---|
| Корень | `CLAUDE.md` |
| `docs/` (читаемая зона) | `docs/README.md`, `docs/conventions/terminology.md`, `docs/spec/MODELS.md`, `docs/spec/models/core/AnomalyReport.md`, `docs/spec/lifecycle/AnomalyReport.md` |
| `.claude/` ядро | `.claude/working-with-claude.md`, `.claude/project-instructions.md`, `.claude/flow/playbook.md`, `.claude/agents/README.md` (см. ниже — упоминаний нет), `.claude/pipeline-evolution-log.md` (append-only, но активно пишется) |
| `.claude/planning/` | `.claude/planning/backlog.md`, `.claude/planning/migration-tracker.md` |
| `.claude/questions/` | `.claude/questions/open-questions.md` |
| `.claude/templates/` | `templates/README.md`, `templates/documents/adr.md`, `templates/documents/model.md`, `templates/documents/lifecycle.md`, `templates/documents/process.md`, `templates/documents/integration-mapping.md`, `templates/documents/reference.md`, `templates/documents/invariant.md`, `templates/documents/open-questions-journal.md` |
| `.claude/skills/` | `spec-document-workflow/SKILL.md`, `spec-document-migration/SKILL.md`, `spec-models-registry/SKILL.md`, `open-questions-workflow/SKILL.md` (внутри `spec-cluster-migration/SKILL.md` упоминаний `ADR-\d{4}` нет; см. §3.7) |
| `.claude/agents/` | `architect.md`, `domain-expert.md`, `risk-engineer.md`, `trading-risk-officer.md`, `knowledge-curator.md` — все по теме «класс ADR», без номеров; `README.md` без упоминаний |
| `.claude/adr/` | `README.md`, `0001-reconcile-via-anomaly-report.md`, `0002-spec-document-standard.md`, `0006-spec-principles.md`, `0007-spec-structure.md`, `0008-open-questions-journal.md`, `0009-domain-migration-strategy.md` — переходят в новую нумерацию; `0003`, `0004`, `0005` удаляются |
| `.claude/strategy-summary/` | `strategy-summary-v7.md` (текущий — активный до создания v8). Версии v1–v6 — historic |
| `.claude/notes/` свежие (2026-05-18) | `pipeline-spec-fitting-2026-05-18.md` (создан только что), `artifact-question-mapping-2026-05-18.md`, `spec-document-migration-decomposition-2026-05-18.md` |

### 3.2. Исторические файлы (append-only, ссылки замораживаются)

Эти файлы по принципу проекта **не редактируются** при рефакторинге.
Они остаются с старыми номерами как часть исторического контекста.
Аналог — Технические примечания в самих старых ADR (ADR-0001, 0002 уже
содержат такие записи о переезде по ADR-0003/0004).

| Файл | Counts | Природа |
|---|---|---|
| `.claude/strategy-summary/strategy-summary-v1.md` | 1 | historic snapshot |
| `.claude/strategy-summary/strategy-summary-v2.md` | 20 | historic snapshot (после ADR-0001/0002/0003) |
| `.claude/strategy-summary/strategy-summary-v3.md` | 24 | historic snapshot |
| `.claude/strategy-summary/strategy-summary-v4.md` | 16 | historic snapshot |
| `.claude/strategy-summary/strategy-summary-v5.md` | 36 | historic snapshot |
| `.claude/strategy-summary/strategy-summary-v6.md` | 27 | historic snapshot |
| `.claude/pipeline-evolution-log.md` | 44 (по `ADR-\d{4}`) + 72 (по класс-упоминанию `\bADR\b`) | append-only журнал пайплайна |
| `.claude/notes/adr-drafts/0006-spec-principles.md` | 15 | черновик перед принятием (snapshot) |
| `.claude/notes/adr-drafts/0007-spec-structure.md` | 23 | то же |
| `.claude/notes/adr-drafts/0008-open-questions-journal.md` | 17 | то же |
| `.claude/notes/adr-drafts/0009-domain-migration-strategy.md` | 35 | то же |
| `.claude/notes/adr-drafts/0009-review-progress.md` | 12 | журнал ревью |
| `.claude/notes/migration-dryrun-C4-C2-2026-05-17.md` | 89 | dry-run V1 |
| `.claude/notes/migration-dryrun-analysis-2026-05-17.md` | 29 | dry-run V1 анализ |
| `.claude/notes/migration-inventory-2026-05-15.md` | 42 | snapshot разведки до пакета |

**Рекомендация по историческим:** оставить как есть. Опционально — после
рефакторинга добавить отдельное техническое примечание (например, в шапке
`pipeline-evolution-log.md` или в новый README в `strategy-summary/`),
объясняющее, что упоминания `ADR-0001..0009` в исторических файлах
относятся к **старой нумерации до Этапа 1 рефакторинга** (с датой), и
дать соответствие к новой нумерации. Это решение оформляется отдельно;
здесь только фиксирую необходимость.

### 3.3. Упоминания ADR-0001 (→ ADR-PRODUCT-0001)

24 упоминания, требующие правки в активных файлах + ADR. Подробно — §4.
Ключевые места:

- Frontmatter `related_adrs: [ADR-0001]`:
  - `docs/spec/models/core/AnomalyReport.md:4`
  - `docs/spec/lifecycle/AnomalyReport.md:4`
  - `.claude/adr/0002-spec-document-standard.md:86` (внутри Decision в frontmatter-примере)
- Внутренние ссылки в ADR:
  - `.claude/adr/0002-spec-document-standard.md:5,10,297,308` (Связан с / Контекст / Технические примечания)
  - `.claude/adr/0003-meta-docs-relocation.md:43,195,224,315,339`
  - `.claude/adr/0001-reconcile-via-anomaly-report.md:1` (заголовок) + 2 технических примечания
- Backlog: `backlog.md:34,40,123,132,138,146,150,154,211,235,243,244` (раздел «Содержательные ADR (TBD из ADR-0001)», источники задач, история).
- Pipeline log: `pipeline-evolution-log.md:439,583,590,592`
- Snapshot v7: `strategy-summary-v7.md:226` (только в формулировке «ADR-0001..0005 — Accepted»).

### 3.4. Упоминания ADR-0003, 0004, 0005 (УДАЛЯЮТСЯ)

Эти три ADR умирают со старой нумерацией. Никакие из них не
переадресовываются на ADR-PIPELINE/PRODUCT напрямую. Все ссылки на них
требуют либо удаления, либо переадресации.

#### 3.4.1. ADR-0003 (разделение `docs/` ↔ `.claude/`)

Содержательное наследие ADR-0003 поглощается CLAUDE.md +
working-with-claude.md (раздел Repository layout уже описывает
разделение). Возможные переадресации: на CLAUDE.md, на
`working-with-claude.md`, на (новый) ADR-PIPELINE-0006 (двойная ось как
дальнейшее развитие разделения).

**Активные упоминания ADR-0003 (фиксирую каждое + предложение):**

| Файл:строка | Контекст | Предложение |
|---|---|---|
| `CLAUDE.md:47` | «ADR-0003 records the rationale for this split.» | переадресовать на CLAUDE.md (как уже встроенное в файл правило) или удалить ссылку — это требует уточнения в чате |
| `docs/README.md:13` | «Обоснование расположения — ADR-0003.» | требует уточнения: переадресация на CLAUDE.md / новый ADR-PIPELINE-0006 |
| `docs/README.md:26` | «Обоснование разделения `docs/` vs `.claude/` — ADR-0003.» | то же |
| `docs/README.md:31` | «v2 — после ADR-0003» (исторический контекст версионирования) | требует уточнения: оставить или переписать формулировку |
| `.claude/adr/0008-open-questions-journal.md:10,52,211,260,267` | многократные ссылки «по ADR-0003» как обоснование выбора `.claude/questions/` | внутри ADR — будут переписаны при пересборке ADR-PIPELINE-0004 |
| `.claude/adr/0009-domain-migration-strategy.md:6,250,382` | «Связанные ADR: …, ADR-0003 …» + «по ADR-0003 — в `.claude/`» | то же при пересборке ADR-PIPELINE-0005 |
| `.claude/adr/0004-spec-templates-relocation.md:11,13,28,88,96,198` | мотивировка ADR-0004 ссылается на ADR-0003 | весь ADR-0004 удаляется |
| `.claude/adr/0007-spec-structure.md` — упоминаний ADR-0003 нет | — | — |
| `.claude/planning/backlog.md:138,213` | «Переномерован с ADR-0003 (?) после принятия ADR-0003» / «✅ ADR-0003: Разделение…» | в раздел «Закрытые» — историческая запись; может потребоваться примечание о новой нумерации |
| `.claude/pipeline-evolution-log.md:420,422,445,459` | «принят ADR-0003 (`.claude/adr/0003-meta-docs-relocation.md`)» | append-only, не правится |
| `.claude/strategy-summary/strategy-summary-v2.md..v6.md` | многочисленные упоминания | historic, не правится |
| `.claude/adr/0001-reconcile-via-anomaly-report.md:223,229` + `.claude/adr/0002-spec-document-standard.md:297,301` | Технические примечания «Принят ADR-0003…» | внутри старых ADR; будут переписаны при пересборке в ADR-PRODUCT-0001 и ADR-PIPELINE-0001 |
| `.claude/notes/artifact-question-mapping-2026-05-18.md:125` | «По ADR-0003 ADR живут в `.claude/adr/`» | активная нота, требует обновления |

#### 3.4.2. ADR-0004 (расположение шаблонов spec-документов)

Содержательное наследие ADR-0004: шаблоны лежат в
`.claude/templates/documents/`. Это уже зафиксировано в:
- `working-with-claude.md` (есть ссылка на ADR-0004),
- `templates/README.md` (структура),
- `CLAUDE.md` (раздел Repository layout — «templates/...; per ADR-0004,
  ADR-0005, ADR-0008»).

Естественные переадресации: на `templates/README.md` (где живёт сама
карта), на CLAUDE.md, или на (новый) ADR-PIPELINE-0006 (если он закрепит
расположение шаблонов).

**Активные упоминания ADR-0004:**

| Файл:строка | Контекст | Предложение |
|---|---|---|
| `CLAUDE.md:85` | «per ADR-0004, ADR-0005, ADR-0008» | переадресовать на templates/README.md или удалить ссылку, оставить факт |
| `.claude/working-with-claude.md:115` | «(см. [ADR-0004](adr/0004-spec-templates-relocation.md))» — markdown-ссылка на путь | удалить ссылку (факт остаётся), или ссылку на templates/README.md |
| `.claude/planning/backlog.md:134,216,246,247,248` | «ADR-0004 (?): Каталог аномалий», «принят ADR-0004 (../adr/0004-...md)», «закрыты пункты ADR-0004/0005», «сессия ADR-0004/0005» | раздел «Закрытые» — historic; примечание о новой нумерации |
| `.claude/adr/0007-spec-structure.md:6,100,168,182` | «Связанные ADR: …, ADR-0004 …», «по ADR-0004», «**ADR-0004** (расположение шаблонов)» | внутри ADR-0007 (→ ADR-PIPELINE-0003) — переписать |
| `.claude/adr/0008-open-questions-journal.md:6,268` | то же | внутри ADR-0008 (→ ADR-PIPELINE-0004) — переписать |
| `.claude/adr/0002-spec-document-standard.md:301,303` | Технические примечания «Принят ADR-0004…» | внутри старого ADR-0002 (→ ADR-PIPELINE-0001) — переписать |
| `.claude/adr/0005-adr-template-relocation.md:*` | весь ADR — про связь с ADR-0004; удаляется вместе с 0004 | — |
| `.claude/adr/0004-spec-templates-relocation.md:*` | сам ADR — удаляется | — |
| `.claude/skills/spec-document-workflow/SKILL.md:231` | «(переехали по ADR-0004)» | переадресовать на templates/README.md или удалить, оставить факт |
| `.claude/pipeline-evolution-log.md:*` | многочисленные упоминания | append-only, не правится |

#### 3.4.3. ADR-0005 (расположение шаблона ADR)

Содержательное наследие — шаблон ADR лежит в
`.claude/templates/documents/adr.md`. Уже зафиксировано в:
- `templates/README.md` (структура),
- `.claude/adr/README.md` (ссылается),
- `CLAUDE.md` (та же строка «per ADR-0004, ADR-0005, ADR-0008»).

**Активные упоминания ADR-0005:**

| Файл:строка | Контекст | Предложение |
|---|---|---|
| `CLAUDE.md:85` | «per ADR-0004, ADR-0005, ADR-0008» | переадресовать на templates/README.md или удалить ссылку |
| `.claude/adr/README.md:91` | «Шаблон — `.claude/templates/documents/adr.md` (переехал по ADR-0005).» | удалить хвост «(переехал по ADR-0005)»; либо переадресовать на templates/README.md |
| `.claude/planning/backlog.md:117,218,246,247,248` | «принят ADR-0005 (../adr/0005-…md)», «закрыты пункты ADR-0004 и ADR-0005» | раздел «Закрытые» — historic |
| `.claude/adr/0004-spec-templates-relocation.md:*` и `.claude/adr/0005-adr-template-relocation.md:*` | оба ADR удаляются | — |
| `.claude/pipeline-evolution-log.md:*` | append-only, не правится | — |

#### 3.4.4. Сводно по 0003/0004/0005

Все три ADR — про **расположение артефактов**. После рефакторинга:

- Факт «`docs/` и `.claude/` разделены» — уже описан в CLAUDE.md
  («Documentation organization») и `docs/README.md`; ссылка на
  ADR-0003 как обоснование может быть удалена без потери смысла, либо
  переадресована на CLAUDE.md / новый ADR-PIPELINE-0006.
- Факт «шаблоны лежат в `.claude/templates/documents/`» — уже описан в
  `templates/README.md` и CLAUDE.md (Repository layout). Ссылки на
  ADR-0004 и ADR-0005 как обоснование могут быть удалены / переадресованы
  на `templates/README.md`.

Решение про каждое из 3-х (удалять / переадресовать на что) — за чатом.
Здесь только инвентарь.

### 3.5. Прозаические упоминания, требующие интерпретации

Места, где упоминание ADR по теме без номера, и не однозначно, какой
ADR имеется в виду:

1. **`.claude/working-with-claude.md:31`** — «**ADR** — для архитектурных
   решений с альтернативами.» Это упоминание класса (см. §3.6); как
   ссылка на конкретный ADR не интерпретируется.

2. **`.claude/working-with-claude.md:44`** — «Конфликты внутри одного
   уровня — выносятся на обсуждение и фиксируются как ADR.» Аналогично —
   класс.

3. **`.claude/working-with-claude.md:84`** — «**Принцип 7. Скиллы и
   агенты — самостоятельные источники применения, ADR — обоснование**».
   Класс.

4. **`.claude/working-with-claude.md:157,159,161,163,164`** — «Режим
   as-if-Accepted для пакета Proposed-ADR…». Прозаически указывает на
   пакет ADR-0006..0009, но без перечисления номеров. **Требует
   уточнения:** надо ли упоминать конкретные ADR (новые
   ADR-PIPELINE-0001..0005) или формулировка «пакет Proposed-ADR»
   остаётся generic. Текущий контекст — режим для итераций; после
   рефакторинга может потерять адресата.

5. **`.claude/flow/playbook.md:428,430,432,434,449,465,468,472,488,491,499,503,504,508`** —
   Сценарий 10 «Итеративная доводка инфраструктуры через dry-run».
   Многократное упоминание «пакета Proposed-ADR» без номеров.
   Прозаически адресовано пакету ADR-0006..0009. **Требует уточнения:**
   после рефакторинга и принятия новых ADR (PIPELINE-0001..0006 и
   PRODUCT-0001) сценарий 10 либо переадресуется на новые
   ADR-PIPELINE-* в Proposed-статусе, либо генерализуется без
   номеров.

6. **`.claude/adr/0002-spec-document-standard.md:278`** — «Открытые
   вопросы (вне scope ADR-0002)» — заголовок раздела. Это про конкретный
   ADR-0002, но формулировка-маркер.

7. **`.claude/notes/migration-dryrun-analysis-2026-05-17.md:121,122,127–149`** —
   множественные «ADR + скилл требуют новой структуры», «ADR должен» без
   номера в одном абзаце, хотя соседние абзацы упоминают ADR-0006/0007
   явно. По контексту — про пакет 0006..0009. Historic, не правится,
   но при чтении после рефакторинга надо помнить про старую нумерацию.

8. **`.claude/notes/artifact-question-mapping-2026-05-18.md:55`** — про
   ADR-0002: «Граница "ADR vs convention" — наследственная (см.
   развилку).» Это про метамодель (L-ADR vs L-CONV), не про конкретный
   ADR. После рефакторинга смысл сохраняется.

### 3.6. «ADR» как класс артефакта (без номера)

Это упоминания, которые **не ссылаются на конкретный ADR**, но затронуты
рефакторингом: после введения двух семейств (ADR-PIPELINE-*, ADR-PRODUCT-*)
формулировки «ADR» могут потребовать уточнения (какое именно семейство).

Активные файлы с такими упоминаниями (на основе `\bADR\b` без
номера-цифр):

| Файл | Кол-во | Характер |
|---|---|---|
| `CLAUDE.md` | 7 | Process layers, Repository layout, конфликты резолвятся через ADR |
| `.claude/working-with-claude.md` | ~25 | Принцип 7 (ADR — обоснование), правила, журнал as-if-Accepted, ADR в indexed CLAUDE.md |
| `.claude/flow/playbook.md` | ~25 | Сценарий 1, Сценарий 10, чек-листы, упоминания «новый ADR» |
| `.claude/adr/README.md` | ~24 | Все правила работы с классом ADR: статусы, шаблон, индекс |
| `.claude/templates/README.md` | 4 | «adr.md — шаблон для нового ADR» |
| `.claude/templates/documents/adr.md` | ~3 | Сам шаблон (placeholder `ADR-XXXX`, `Superseded by ADR-NNNN`, `Supersedes ADR-NNNN`) |
| `.claude/agents/architect.md:77`, `domain-expert.md:79`, `risk-engineer.md:89`, `trading-risk-officer.md:103`, `knowledge-curator.md:30,56,83` | 1–3 на агента | «Propose ADR…», «if alternatives → ADR», KPI Curator |
| `.claude/project-instructions.md:57,66,86` | 3 | «substantive changes (new ADR…)», «ADR / spec doc / skill / …», «(ADR creation, agent доработка…)» |
| `.claude/planning/backlog.md` | ~35 | «ADR-кандидат», «кандидат на ADR», «Содержательные ADR (TBD из ADR-0001)» (заголовок) |
| `.claude/skills/spec-document-workflow/SKILL.md` | ~10 | «изменение → ADR», `related_adrs`, «дубль ADR» |
| `.claude/skills/spec-document-migration/SKILL.md` | ~5 | «дубль ADR», `related_adrs` |
| `.claude/skills/spec-models-registry/SKILL.md` | ~5 | «ссылки на ADR живут в model», «упразднение зафиксировано ADR» |
| `.claude/skills/open-questions-workflow/SKILL.md` | ~10 | «уже решён принятым ADR», «обсуждение ADR» (без номера), `obsolete-by-decision` про ADR |

Подмножество специфическое — **placeholder-формы**:
`ADR-NNNN`, `ADR-MMMM`, `ADR-XXXX`, `Supersedes ADR-NNNN`,
`Superseded by ADR-NNNN` — встречаются в:

- `.claude/templates/documents/adr.md:1,4,46,47` — это сам шаблон.
- `.claude/adr/README.md:31,61,70` — описание правил (статус
  Superseded by, технические/содержательные примечания).
- `.claude/adr/0003-meta-docs-relocation.md:160,174` — внутри ADR-0003
  (как пример формата примечания).
- `.claude/adr/0008-open-questions-journal.md:70` — поле `resolution`
  ссылается на «ADR-NNNN / spec-документ + раздел / коммит».
- `.claude/working-with-claude.md:88,153` — «по ADR-NNNN §X», «Supersedes
  ADR-NNNN»; первое — антипаттерн (Принцип 7), второе — правило.
- `.claude/flow/playbook.md:338` — «Принят ADR-NNNN → проверка
  индекса».
- `.claude/notes/pipeline-spec-fitting-2026-05-18.md:494,504,527,528,566,812` —
  в анализе метамодели ADR (это нота, не нормативный документ).

**Требует уточнения после рефакторинга:** правила работы с ADR (в
`adr/README.md`, `working-with-claude.md`, `flow/playbook.md`, шаблон
`adr.md`) — нужно ли в формате `ADR-NNNN` сохранять generic
плейсхолдер, или переходить на формулу `ADR-{PIPELINE|PRODUCT}-NNNN`
с обоими вариантами. Сейчас плейсхолдер generic — после двойного
семейства это может перестать быть понятным.

### 3.7. Артефакты, где упоминаний ADR НЕТ

- `.claude/agents/README.md` — нет.
- `.claude/skills/spec-cluster-migration/SKILL.md` — нет упоминаний
  `\bADR\b` вообще. **Это потенциальный сигнал** к ревью: скилл
  оркестрирует то, что описано ADR-0009; ожидаемая ссылка на
  ADR-0009 (или эквивалент после рефакторинга) либо отсутствует
  намеренно (по Принципу 7 в working-with-claude.md — скилл
  самостоятелен), либо забыта. Требует уточнения, но **не блокирует
  рефакторинг**.
- `docs/conventions/terminology.md` упоминает ADR (3 раза, см. §4.5),
  но не входит в сводки по `ADR-\d{4}` строго — там ссылки только на
  0006, 0007 + «фиксируется ADR» (класс).
- `docs/spec/` — упоминания только в трёх документах (MODELS.md +
  AnomalyReport.md × 2).

---

## 4. Детальный инвентарь по активным файлам

Колонки: «строка», «тип» (`direct-id` / `path` / `frontmatter` /
`prose` / `index`), «ADR», «контекст» (краткая выдержка).

### 4.1. `CLAUDE.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 21 | prose | (класс) | «**ADR** — *why was this chosen over alternatives?* → `.claude/adr/`» |
| 45 | prose | (класс) | «working artifacts (ADR, backlog), process documents…» |
| 47 | direct-id | 0003 | «ADR-0003 records the rationale for this split.» |
| 59 | prose | (класс) | «Conflicts between levels 1 and 2 … are resolved by recording an ADR.» |
| 85 | direct-id | 0004, 0005, 0008 | «templates/… per ADR-0004, ADR-0005, ADR-0008» |
| 93 | prose | (класс) | «will be subject of an ADR.» |
| 102 | prose | (класс) | «Conflicts of substance should be resolved by creating an ADR…» |

### 4.2. `docs/README.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 13 | direct-id | 0003 | «Обоснование расположения — ADR-0003.» |
| 26 | direct-id | 0003 | «Обоснование разделения `docs/` vs `.claude/` — ADR-0003.» |
| 31 | direct-id | 0003 | «v2 — после ADR-0003.» |
| 43 | prose | (класс) | «При конфликте уровней … фиксируется в ADR.» |

### 4.3. `docs/conventions/terminology.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 10 | prose | (класс) | «Пересмотр фиксируется ADR (если влияет на принятые конвенции в нескольких местах)…» |
| 38 | direct-id | 0006 | «Полный критерий — ADR-0006 §1.» |
| 40 | direct-id | 0007 | «(ADR-0007 §1)» |

### 4.4. `docs/spec/MODELS.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 9 | direct-id + path | 0006 | «по [ADR-0006 §1](../../.claude/adr/0006-spec-principles.md)» — markdown-ссылка с путём |

### 4.5. `docs/spec/models/core/AnomalyReport.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 4 | frontmatter | 0001 | `related_adrs: [ADR-0001]` |

### 4.6. `docs/spec/lifecycle/AnomalyReport.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 4 | frontmatter | 0001 | `related_adrs: [ADR-0001]` |

### 4.7. `.claude/working-with-claude.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 31 | prose | (класс) | «**ADR** — для архитектурных решений с альтернативами.» |
| 44 | prose | (класс) | «Конфликты внутри одного уровня — фиксируются как ADR.» |
| 51 | prose | (класс) | «Принят ADR → проверяем, какие спецификации требуют обновления…» |
| 55 | prose | (класс) | «Принятие ADR / обновление спецификации, закрывающее пункт…» |
| 56 | direct-id | 0008 | «по правилам ADR-0008» |
| 66 | direct-id | 0007, 0009 | «ADR-0007 §3 (MODELS.md) и ADR-0009 §9 (общая раскладка)» |
| 84 | prose | (класс) | «Принцип 7. Скиллы и агенты — самостоятельные источники применения, ADR — обоснование» |
| 86–94 | prose | (класс) | многократно «ADR» как класс; в строке 92 — «пакета ADR-0006..0009» — diapason-форма |
| 92 | direct-id | 0006..0009 | «пакета ADR-0006..0009» (диапазон) |
| 112 | direct-id + path | 0002 | «[ADR-0002](adr/0002-spec-document-standard.md). Шесть жанровых…» |
| 115 | direct-id + path | 0004 | «(см. [ADR-0004](adr/0004-spec-templates-relocation.md))» |
| 117 | direct-id | 0002 | «в ADR-0002. При работе…» |
| 140 | prose | (класс) | «- ADR (обоснование решений).» |
| 146 | prose | (класс) | «## ADR — архитектурные решения» (заголовок раздела) |
| 148 | prose | (класс) | «ADR хранятся в `.claude/adr/`. Индекс — `.claude/adr/README.md`. Шаблон — `.claude/templates/documents/adr.md`.» |
| 150 | prose | (класс) | «**Правила ADR:**» |
| 152 | prose | (класс) | «- ADR **append-only**…» |
| 153 | placeholder | (класс) | «новый ADR со статусом `Supersedes ADR-NNNN`. Старый — `Superseded by ADR-MMMM`.» |
| 154 | prose | (класс) | «ADR должен содержать: контекст, решение, альтернативы…» |
| 155 | prose | (класс) | «ADR создаётся для значимых решений…» |
| 157 | prose | (класс) | «**Режим as-if-Accepted для пакета Proposed-ADR…**» |
| 159 | prose | (класс/диапазон) | «Когда пакет ADR находится в Proposed…» |
| 161 | prose | (класс) | «Правки тела Proposed-ADR накапливаются…» |
| 163 | prose | (класс) | «Правки **инфраструктуры**… не часть тестируемого пакета ADR» |
| 164 | prose | (класс) | «Правка Proposed-ADR по ходу итерации нарушает стабильность…» |
| 229 | prose | (класс) | «новый ADR, обновлённый концепт, новый snapshot…» |
| 239 | prose | (класс) | «ADR → проверка индекса, цитаты ключевых положений…» |
| 245 | prose | (класс) | «- Принят новый ADR.» |
| 263 | prose | (класс) | «- Решение с альтернативами → ADR.» |

### 4.8. `.claude/flow/playbook.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 45 | prose | (класс) | «## Сценарий 1. Архитектурное решение → ADR» |
| 55 | prose | (класс) | «- Черновик ADR (полный текст).» |
| 61 | prose | (класс) | «- Сохранить ADR в `.claude/adr/` со следующим свободным номером.» |
| 71 | prose | (класс) | «ADR создан, спецификации забыты → главный риск.» |
| 72 | prose | (класс) | «Спецификация расходится с ADR → разруливается новым ADR (Supersedes)…» |
| 170 | prose | (класс) | «Дублирование со спецификацией / ADR…» |
| 187 | prose | (класс) | «…Раздел в CLAUDE.md? ADR? Спецификация?» |
| 202 | prose | (класс) | «Скилл вместо ADR → ADR это "что и почему решили"…» |
| 219 | prose | (класс) | «- Новые ADR за период.» |
| 261 | direct-id | 0002 | «Согласовать с ADR-0002.» |
| 275 | prose | (класс) | «Проверить связные обновления (другие spec-документы, ADR, backlog).» |
| 284 | prose (frontmatter-ref) | (класс) | «`related_adrs`» |
| 294 | prose | (класс) | «связные документы обновлены (другие spec-документы, ADR, backlog).» |
| 311 | prose | (класс) | «Приняла новый ADR.» |
| 338 | placeholder | (класс) | «- Принят ADR-NNNN → проверка индекса…» |
| 339 | prose | (класс) | «проверка наличия ADR в `.claude/adr/`…» |
| 342 | prose | (класс) | «упомянутых ADR в шапке.» |
| 380 | prose | (класс) | «сценариев 1, 3, 5, 7 (создание ADR…)» |
| 400 | direct-id + path | 0009 | «[ADR-0009](../adr/0009-domain-migration-strategy.md) — стратегия» |
| 428, 430, 432, 434, 449, 465, 468, 472, 488, 491, 499, 503, 504, 508 | prose | (класс / неявный пакет 0006..0009) | Сценарий 10 «Итеративная доводка…»; формулировки «пакет Proposed-ADR», «правки тела Proposed-ADR — в журнал, не в тела» и т.д. |
| 533 | prose | (класс) | «ADR оформлен — если применимо.» |

### 4.9. `.claude/planning/backlog.md`

Файл — главный реципиент при рефакторинге (38 упоминаний `ADR-\d{4}`).

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 3 | prose | (класс) | «открытых задач, ADR-кандидатов…» |
| 6 | path | (класс) | «`../adr/` — принятые архитектурные решения (append-only).» |
| 15 | prose | (класс) | «Если задача стала ADR-кандидатом — оставить ссылку на номер ADR…» |
| 34 | direct-id | 0001 | «*Источник: чат ADR-0001, 2026-05-14.*» |
| 40 | direct-id | 0001 | «в рамках ADR-0001. Полный стандарт — отдельная задача…» |
| 41 | prose | (класс) | «Кандидат на ADR.» |
| 48 | direct-id | 0002 | «*Источник: ADR-0002 §1 + миграция AnomalyReport…*» |
| 55 | direct-id | 0007, 0009 | «по ADR-0007 §3 и ADR-0009 §9.» |
| 62 | direct-id | 0007 | «по ADR-0007 §3.» |
| 68 | direct-id | 0009 | «по ADR-0009 §9.» |
| 106 | prose | (класс) | «отдельный ADR про…» |
| 109 | direct-id | 0002 | «*Источник: ADR-0002, чат принятия.*» |
| 115 | direct-id | 0002 | то же |
| 117 | direct-id | 0005 | (omitted long matching line) — ссылка про ADR-0005 |
| 119 | direct-id | 0004 | (omitted) |
| 123 | direct-id | 0001 | «## Содержательные ADR (TBD из ADR-0001)» |
| 127 | direct-id | 0002 | «**ADR-0002 (?): AnomalyJob — полная спецификация.**» |
| 132 | direct-id | 0001 | «*Источник: ADR-0001, TBD-1.*» |
| 134 | direct-id | 0004 | «**ADR-0004 (?): Каталог аномалий.**» |
| 138 | direct-id | 0001, 0003 | «*Источник: ADR-0001, TBD-2. Переномерован с ADR-0003 (?) после принятия ADR-0003 (разделение docs/ vs .claude/).*» |
| 142 | prose | (класс) | «**ADR: KillSwitchExecutor…**» |
| 146 | direct-id | 0001 | «*Источник: ADR-0001, TBD-3.*» |
| 148 | prose | (класс) | «**ADR: политика блокировки торговли…**» |
| 150 | direct-id | 0001 | «*Источник: ADR-0001, TBD-4.*» |
| 152 | prose | (класс) | «**ADR: конкурентность DealOrchestratorJob vs AnomalyJob.**» |
| 154 | direct-id | 0001 | «*Источник: ADR-0001, TBD-5.*» |
| 163 | prose | (класс) | «Кандидат на ADR.» |
| 167 | prose | (класс) | «Кандидат на ADR. Текущий код использует RestTemplate…» |
| 189 | prose | (класс) | «Тестовая стратегия — кандидат на ADR.» |
| 205 | direct-id | 0002 | «*Источник: ADR-0002.*» |
| 211 | direct-id | 0001 | «- ✅ **ADR-0001: модель reconcile через AnomalyReport.** Закрыто 2026-05-14.» |
| 212 | path | 0001 | «Ссылка: `../adr/0001-reconcile-via-anomaly-report.md`.» |
| 213 | direct-id | 0003 | «- ✅ **ADR-0003: Разделение продуктовой документации и работы с Claude.**» |
| 214 | path | 0003 | «Закрыто 2026-05-15. Ссылка: `../adr/0003-meta-docs-relocation.md`.» |
| 216 | direct-id + path | 0004 | «принят ADR-0004 (`../adr/0004-spec-templates-relocation.md`).» |
| 217 | prose | (класс) | «Перенос шаблона ADR в `.claude/templates/documents/adr.md`» |
| 218 | direct-id + path | 0005 | «принят ADR-0005 (`../adr/0005-adr-template-relocation.md`). Семантика…» |
| 221 | direct-id | 0002 | «соответствие со стандартом ADR-0002…» |
| 230 | direct-id | 0006 | «части задачи из ADR-0006 §Consequences…» |
| 231 | direct-id | 0007 | «по ADR-0007 §1).» |
| 235 | direct-id | 0001 | «model-документ). Технические примечания добавлены в ADR-0001,» |
| 236 | direct-id | 0002, 0006, 0009 | «ADR-0002, ADR-0006, ADR-0009.» |
| 243 | direct-id | 0001 | «- 2026-05-14 — создан по итогам чата ADR-0001.» |
| 244 | direct-id | 0001 | «добавлены TBD из ADR-0001…» |
| 246 | direct-id | 0004 | «закрыты пункты ADR-0004…» |
| 247 | direct-id | 0005 | «ADR-0005 (перенос шаблона ADR)…» |
| 248 | direct-id | 0004, 0005 | «наблюдения из сессии ADR-0004/0005…» |
| 250 | direct-id | 0002 | «- 2026-05-15 — закрыт пункт «…AnomalyReport.md к стандарту ADR-0002»…» |
| 260 | direct-id | 0007 | «(правки в Proposed-ADR-0007 §3» |
| 261 | direct-id | 0009 | «и ADR-0009 §9).» |
| 264 | direct-id | 0006 | «(структурная часть задачи из ADR-0006 §Consequences…)» |
| 265 | direct-id | 0007 | «ADR-0007 §1).» |

### 4.10. `.claude/planning/migration-tracker.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 5 | direct-id + path | 0009 | «Стратегия миграции — [ADR-0009](../adr/0009-domain-migration-strategy.md).» |
| 18 | direct-id + path | 0009 | «Порядок и обоснование — в [ADR-0009 §2](../adr/0009-domain-migration-strategy.md).» |

### 4.11. `.claude/questions/open-questions.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 7 | direct-id + path | 0008 | «Полные правила работы с журналом — ADR-0008 (`.claude/adr/0008-open-questions-journal.md`).» |
| 31 | direct-id | 0008 | «обоснование устаревания по одному из трёх условий (см. ADR-0008 §5).» |

### 4.12. `.claude/templates/`

#### 4.12.1. `templates/README.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 11 | prose | (класс) | «`adr.md` — Документ — Architecture Decision Record…» |
| 18 | direct-id | 0008 | «См. ADR-0008» |
| 20 | direct-id + path | 0002 | «в [ADR-0002, §1 «Жанры документов»](../adr/0002-spec-document-standard.md).» |
| 21 | direct-id + path | 0008 | «в [ADR-0008](../adr/0008-open-questions-journal.md).» |
| 22 | path | (класс) | «Правила работы с ADR — в [`.claude/adr/README.md`](../adr/README.md).» |

#### 4.12.2. `templates/documents/adr.md` (сам шаблон)

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 1 | placeholder | (класс) | «# ADR-XXXX: <Короткое название решения>» |
| 4 | placeholder | (класс) | «**Статус:** Proposed \| Accepted \| Superseded by ADR-NNNN \| Deprecated» |
| 46 | placeholder | (класс) | «**Supersedes:** ADR-NNNN…» |
| 47 | placeholder | (класс) | «**Superseded by:** ADR-MMMM…» |

#### 4.12.3. `templates/documents/{model,lifecycle,process,integration-mapping,reference,invariant}.md`

Все шаблоны содержат frontmatter `related_adrs: []` (с пустым списком).
Это структурное поле, не упоминание конкретного ADR. Файлы:

- `model.md:4` — `related_adrs: []`
- `lifecycle.md:4` — `related_adrs: []`
- `process.md:4` — `related_adrs: []`
- `integration-mapping.md:4` — `related_adrs: []`
- `reference.md:4` — `related_adrs: []`
- `invariant.md:4` — `related_adrs: []`

#### 4.12.4. `templates/documents/open-questions-journal.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 7 | direct-id + path | 0008 | «Полные правила работы с журналом — ADR-0008 (`.claude/adr/0008-open-questions-journal.md`).» |
| 31 | direct-id | 0008 | «обоснование устаревания по одному из трёх условий (см. ADR-0008 §5).» |

### 4.13. `.claude/agents/*` (5 файлов)

Все упоминания — про класс ADR (без номера). Не требуют изменения по
номерам, но могут потребовать уточнения «какое именно семейство» после
введения PIPELINE/PRODUCT.

| Файл:строка | Контекст |
|---|---|
| `architect.md:77` | «Propose creating or updating an ADR in `.claude/adr/`.» |
| `domain-expert.md:79` | «If decision has alternatives — propose ADR.» |
| `risk-engineer.md:89` | «Propose ADR if it requires a design decision…» |
| `trading-risk-officer.md:103` | «Propose ADR if it has alternatives…» |
| `knowledge-curator.md:30` | «moved to "Закрытые" section with date and link to ADR/commit» |
| `knowledge-curator.md:56` | «Where it should live (ADR / spec file / skill / …)» |
| `knowledge-curator.md:83` | «Decisions with alternatives → ADR.» |
| `agents/README.md` | упоминаний ADR нет |

### 4.14. `.claude/project-instructions.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 57 | prose | (класс) | «substantive changes (new ADR, updated concept, new snapshot…)» |
| 66 | prose | (класс) | «(ADR / spec doc / skill / terminology.md / MODELS.md / …)» |
| 86 | prose | (класс) | «(ADR creation, agent доработка, skill creation, retrospective)» |

### 4.15. `.claude/skills/`

#### 4.15.1. `spec-document-workflow/SKILL.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 3 | direct-id | 0002 | «full ADR-0002 compliance checklist.» (description) |
| 10 | direct-id | 0002 | «стандарта, зафиксированного в ADR-0002.» |
| 26 | prose | (класс) | «требующая согласования через ADR» |
| 28 | direct-id | 0002 | «## Базовые принципы (выжимка из ADR-0002)» |
| 40 | direct-id + path | 0002 | «Полный стандарт — в `.claude/adr/0002-spec-document-standard.md`.» |
| 41 | prose | (класс) | «При неоднозначности всегда смотри ADR.» |
| 88 | prose | (класс) | «`related_adrs`: список ADR, касающихся документа.» |
| 108 | prose | (класс) | «история — в ADR, не для документа спецификации.» |
| 114 | prose | (класс) | «`related_adrs`: добавить новые ADR…» |
| 132–136 | prose | (класс) | «ADR. Если изменение мотивировано новым решением…», «ссылкой на коммит/ADR» |
| 151 | prose | (класс) | «Frontmatter заполнен (`status`, `last_review`, `related_adrs`).» |
| 210 | placeholder | (класс) | «раздел «Дополнение после ADR-0NNN» в конце документа.» |
| 211 | prose | (класс) | «история — в ADR.» |
| 230 | direct-id + path | 0002 | «`.claude/adr/0002-spec-document-standard.md` — полный стандарт.» |
| 231 | direct-id | 0004 | «(переехали по ADR-0004).» |

#### 4.15.2. `spec-document-migration/SKILL.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 101 | prose | (класс) | «ADR в `.claude/adr/`, на которые ссылается исходник…» |
| 178 | prose | (класс) | «(дубль ADR / реализационный класс / значение).» |
| 392 | prose | (класс) | «`related_adrs` включает ADR, к которым относится исходник.» |
| 431 | prose | (класс) | «(дубль ADR / реализационный класс / значение).» |
| 455 | prose | (класс) | «Связь — только через `related_adrs` в frontmatter…» |
| 462–464 | prose | (класс) | «Удаление содержания "потому что в ADR уже сказано"…» |
| 488 | direct-id + path | 0002 | «`.claude/adr/0002-spec-document-standard.md` — стандарт» |

#### 4.15.3. `spec-models-registry/SKILL.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 3 | prose | (класс) | «what NOT to include (…ADR references).» (description) |
| 58 | direct-id | 0006 | «по делению ADR-0006 §1» |
| 100 | prose | (класс) | «**Ссылки на ADR.** Реестр — это «карта моделей», не «карта решений».» |
| 101 | prose | (класс) | «Ссылки на ADR живут в model-документах…» |
| 116 | direct-id | 0006 | «(по тесту ADR-0006 §1 — …)» |
| 143 | prose | (класс) | «упразднение зафиксировано ADR.» |
| 151 | prose | (класс) | «Изменение имени модели должно быть зафиксировано ADR…» |
| 170 | direct-id | 0006 | «(по ADR-0006 §1: модели, существующие…)» |
| 187 | direct-id + path | 0007 | «`.claude/adr/0007-spec-structure.md` §3 — обоснование» |
| 189 | direct-id + path | 0009 | «`.claude/adr/0009-domain-migration-strategy.md` §6, §9 —» |
| 204 | direct-id | 0002 | «ADR-0002) и шаблоны model.» |
| 206 | direct-id | 0006 | «ADR-0006 §1, расширенное объяснение в» |

#### 4.15.4. `open-questions-workflow/SKILL.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 12 | direct-id + path | 0008 | «[ADR-0008](../../adr/0008-open-questions-journal.md).» |
| 27 | prose | (класс) | «развилки, для которых пока нет ADR.» |
| 36 | direct-id + path | 0008 | «11 полей по [ADR-0008 §2](…).» |
| 43 | prose | (класс) | «в чате, ревью ADR» |
| 59 | direct-id + path | 0008 | «[ADR-0008 §4](…) с» |
| 84 | prose | (класс) | «принятие ADR.» |
| 86 | prose | (класс) | «legacy расходится с ADR» |
| 97 | prose | (класс) | «`obsolete-by-decision` — вопрос уже решён принятым ADR…» |
| 133 | prose | (класс) | «ссылка на ADR / раздел spec-документа / коммит» |
| 169 | prose | (класс) | «отвечён ADR/spec на момент заведения» |
| 173 | prose | (класс) | «В Resolution указать ADR/spec, отвечающий на вопрос.» |
| 179 | direct-id + path | 0008 | «по [ADR-0008 §5](…).» |
| 185 | prose | (класс) | «разрешён вне журнала (через ADR, spec…).» |
| 190 | direct-id + path | 0008 | «(см. [ADR-0008 §5](…))» |
| 252 | direct-id + path | 0008 | «`.claude/adr/0008-open-questions-journal.md` — полная» |
| 264 | prose | (класс) | «Принятое решение с альтернативами → ADR» |

#### 4.15.5. `spec-cluster-migration/SKILL.md`

Упоминаний `\bADR\b` нет. См. §3.7.

### 4.16. `.claude/adr/` (внутренние ссылки между ADR)

Кросс-ссылки между ADR — это место, где новая нумерация требует
переписывания **в каждом** ADR, который переходит в новую структуру.

#### 4.16.1. `adr/README.md`

Содержит весь регламент работы с ADR (24 упоминания `\bADR\b` без
номера + 1 упоминание ADR-0003 + 1 ADR-0005).

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 31 | placeholder | (класс) | «**Superseded by ADR-NNNN**…» |
| 61, 70 | placeholder | (класс) | «(см. ADR-NNNN). Актуальные пути — в CLAUDE.md.» / «Решение пересмотрено в ADR-NNNN.» |
| 87 | direct-id | 0003 | «Полный пример применения этого алгоритма — ADR-0003 (раздел Decision…).» |
| 91 | direct-id | 0005 | «Шаблон — `.claude/templates/documents/adr.md` (переехал по ADR-0005).» |
| 95–101 | index | 0001–0005 | таблица индекса ADR: «0001 \| … \| Accepted \| 2026-05-14», «0002», «0003», «0004», «0005» (5 строк) |
| 103 | prose | (класс) | «При создании ADR добавляй строку в эту таблицу.» |
| 108 | prose | (класс) | «Сценарий 1 — операционная процедура создания ADR.» |
| 109 | prose | (класс) | «отдельный лог для изменений пайплайна работы (не для ADR).» |

#### 4.16.2. `adr/0001-reconcile-via-anomaly-report.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 1 | заголовок | 0001 | «# ADR-0001: Модель reconcile через AnomalyReport» |
| 223 | direct-id | 0003 | «- [2026-05-15] Принят ADR-0003. Файлы, на которые ссылается этот ADR…» |
| 229 | direct-id | 0007 | «ADR-0007 §1 (раскладка `models/{core,runtime}/`).» |

#### 4.16.3. `adr/0002-spec-document-standard.md`

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 1 | заголовок | 0002 | «# ADR-0002: Стандарт документа `docs/spec/`» |
| 5 | direct-id | 0001 | «**Связан с:** ADR-0001…» |
| 10 | direct-id | 0001 | «На момент принятия ADR-0001 туда был…» |
| 20 | direct-id | 0002 | «в чате принятия ADR-0002.» |
| 86 | frontmatter (пример) | 0001 | «related_adrs: [ADR-0001]» (пример в теле документа) |
| 93 | prose | (класс) | «`related_adrs` обновляется при принятии нового ADR…» |
| 278 | direct-id | 0002 | «## Открытые вопросы (вне scope ADR-0002)» |
| 297 | direct-id | 0003 | «- [2026-05-15] Принят ADR-0003.» |
| 301 | direct-id | 0004 | «- [2026-05-15] Принят ADR-0004.» |
| 303 | direct-id | 0004 | «(см. таблицу в ADR-0004).» |
| 308 | direct-id | 0007 | «по дисциплине ADR-0007 §1.» |

#### 4.16.4. `adr/0003-meta-docs-relocation.md` (УДАЛЯЕТСЯ)

13 упоминаний `ADR-\d{4}` (включая `ADR-NNNN` placeholders).

| Строка | Тип | ADR | Контекст |
|---|---|---|---|
| 1 | заголовок | 0003 | «# ADR-0003: Разделение продуктовой документации и работы с Claude» |
| 43 | direct-id | 0001 | «ADR-0001 (про reconcile в торговом боте) лежит в `.claude/adr/`…» |
| 83 | path table | 0000 (шаблон) | таблица переездов `docs/adr/0000-template.md → .claude/adr/0000-template.md` |
| 84 | path table | 0001 | таблица переезда ADR-0001 |
| 85 | path table | 0002 | таблица переезда ADR-0002 |
| 160, 174 | placeholder | (класс) | «(см. ADR-NNNN). Актуальные пути — в CLAUDE.md.» |
| 195 | direct-id | 0001, 0002 | «### Применение алгоритма к ADR-0001 и ADR-0002 в рамках этого ADR» |
| 204 | direct-id | 0003 | «- [2026-05-15] Принят ADR-0003.» |
| 224 | direct-id | 0001 | «ADR-0001 (про reconcile продукта)…» |
| 225 | direct-id | 0002 | «остаётся в `docs/adr/`, ADR-0002 (про стандарт документа) переезжает в…» |
| 230 | direct-id | 0002 | «ADR на стыке (типа ADR-0002) — отдельная…» |
| 288, 289 | path | 0001, 0002 | «`.claude/adr/0001-reconcile-via-anomaly-report.md`» / «`.claude/adr/0002-spec-document-standard.md`» |
| 298, 300 | prose | 0003 | «ADR-0003 (?): разделение» / «ADR-0003 (?): Каталог…» |
| 301 | direct-id | 0004 | «**ADR-0004**.» |
| 315 | direct-id | 0001, 0002, 0003 | «ADR-0001, ADR-0002, ADR-0003.» |
| 339 | direct-id | 0001, 0002 | «**Исключение — принятые ADR-0001 и ADR-0002**…» |

#### 4.16.5. `adr/0004-spec-templates-relocation.md` (УДАЛЯЕТСЯ)

22 упоминаний `ADR-\d{4}`. Включает:
- ссылки на ADR-0002 (мотивация, §7, related): строки 9, 25, 29, 74, 77, 79, 82, 84, 85, 94.
- ссылки на ADR-0003 (расположение): строки 11, 13, 28, 88, 96, 198.
- сам ADR-0004 заголовок (1), индексирование (171, 183, 195, 199).
- внутренние ссылки на `.claude/adr/0002-spec-document-standard.md`
  (168, 223).

#### 4.16.6. `adr/0005-adr-template-relocation.md` (УДАЛЯЕТСЯ)

18 упоминаний `ADR-\d{4}`. Включает:
- сам ADR-0005 (1, 25, 52, 82, 150, 153).
- многократные ссылки на ADR-0004 как родительский (9, 11, 20, 25, 26, 51, 78, 80, 82, 83, 98, 160, 161, 172).
- путевые ссылки `.claude/adr/0000-template.md`, `.claude/templates/adr/0000-template.md`,
  `.claude/templates/documents/adr.md` (86, 101, 129).

#### 4.16.7. `adr/0006-spec-principles.md`

20 упоминаний `ADR-\d{4}`. Главные:
- frontmatter «Связанные ADR» (6): ADR-0002, 0007, 0008, 0009.
- внутренние сходные ссылки на ADR-0002, 0007, 0009 (см. §2 топ-файлы).
- технические примечания: ADR-0007 §1 (201).

#### 4.16.8. `adr/0007-spec-structure.md`

32 упоминания. Главные:
- frontmatter «Связанные ADR» (6): ADR-0002, 0004, 0006, 0008, 0009.
- много ссылок на ADR-0002, ADR-0006 в Decision и Alternatives.
- ссылка на ADR-0009 (138, 185).

#### 4.16.9. `adr/0008-open-questions-journal.md`

18 упоминаний. Главные:
- frontmatter «Связанные ADR» (6): ADR-0002, 0003, 0004, 0006, 0007, 0009.
- внутри Decision и Consequences — ссылки на все 6.

#### 4.16.10. `adr/0009-domain-migration-strategy.md`

39 упоминаний. Главные:
- frontmatter «Связанные ADR» (6): ADR-0002, 0003, 0006, 0007, 0008.
- многократные ссылки на ADR-0002, 0006, 0007, 0008 в Decision /
  Alternatives / Consequences.
- блок Consequences «Связь с другими ADR» (§Consequences).
- ссылка на путь `docs/spec/models/core/AnomalyReport.md` по дисциплине
  ADR-0007 §1 (393).

### 4.17. `.claude/strategy-summary/strategy-summary-v7.md` (текущий)

16 упоминаний. Главные:
- 15: «AnomalyReport — единственный продукт миграции до старта фазы 1
  (создан до пакета ADR-0006..0009 как пилотный кейс).»
- 19: «Пакет ADR-0006..0009 — Proposed, в режиме as-if-Accepted…»
- 129: «## Состав пакета ADR-0006..0009»
- 133, 142, 146, 154 — заголовки разделов «ADR-0006: …», «ADR-0007: …»,
  «ADR-0008: …», «ADR-0009: …»
- 202: «по составу из ADR-0009 §2.»
- 226: «ADR-0001..0005 — Accepted. ADR-0006..0009 — Proposed…»
- 263: «`adr-drafts/` — ADR-0009 review-progress…»
- 344: «12. Перевод ADR-0006..0009 в Accepted.»
- 363: «Не править ADR-0006 §1…»
- 392: «История архитектурных решений — `../adr/`.»

Это **самый свежий** strategy snapshot. После рефакторинга версия v8
будет писаться уже в новой нумерации; v7 остаётся как historic.

### 4.18. `.claude/notes/` свежие (2026-05-18)

Эти три файла созданы за последние два дня, активные:

- `pipeline-spec-fitting-2026-05-18.md` — 20 упоминаний (включая
  созданный мной только что; ссылки на ADR-0002, 0006, 0007, 0008).
- `artifact-question-mapping-2026-05-18.md` — 13 упоминаний (включая
  ADR-0001..0009 в таблице артефактов как L-ADR).
- `spec-document-migration-decomposition-2026-05-18.md` — 22 упоминания
  (ADR-0006, ADR-0007, ADR-0008 в анализе декомпозиции).

Все три — рабочие ноты для текущего сюжета. Решение, правятся они или
нет, — за чатом. Возможно, после Этапа 1 они либо переписываются в
новую нумерацию, либо помечаются «historic snapshot до Этапа 1».

---

## 5. Журналы правок старых ADR

В `.claude/notes/adr-drafts/` лежит 5 файлов:

| Файл | Назначение |
|---|---|
| `0006-spec-principles.md` | финальный черновик ADR-0006 до помещения в `.claude/adr/`. По содержанию идентичен принятому ADR-0006 (Proposed) |
| `0007-spec-structure.md` | финальный черновик ADR-0007 до помещения в `.claude/adr/` |
| `0008-open-questions-journal.md` | финальный черновик ADR-0008 |
| `0009-domain-migration-strategy.md` | финальный черновик ADR-0009 |
| `0009-review-progress.md` | журнал ревью ADR-0009: что замечалось, как переписывалось до закрытия ревью. Включает упоминания «Правка A», «Правка B», «Правка C», «Правка D» с привязкой к замечаниям. Содержит ссылки на ADR-0006..0009, на artifact paths и на «правки в `.claude/notes/migration-dryrun-analysis-2026-05-17.md`» |

**Дополнительно:**

- В `.claude/strategy-summary/strategy-summary-v6.md:207` упоминается,
  что «Журнал правок итерации 1 (`adr-fixes-iteration-1.md`) удалён в
  этой сессии» — то есть отдельный журнал правок (раздельный от
  pipeline-evolution-log.md) уже был, и был удалён до v7.
- Технические примечания внутри принятых ADR (ADR-0001, ADR-0002) уже
  фиксируют исторические переезды (ADR-0003, ADR-0004, ADR-0007) — это
  тоже фактически встроенные журналы.

**Что эти журналы фиксируют:**

- `0009-review-progress.md` фиксирует, какие пункты ревью были закрыты
  (Правки A–D), куда уехало содержание (Принцип 6 в
  `working-with-claude.md`, классификация в ADR-0008 §4, и т.д.).
- Черновики 0006–0009 в `adr-drafts/` — это рабочий снапшот тела ADR
  до его помещения в `.claude/adr/`. После принятия пакета они
  становятся избыточными (но не удалены).

**При рефакторинге.** Файлы в `adr-drafts/` исчезают вместе со старыми
номерами по смыслу:
- если старые ADR-0006..0009 пересобираются в ADR-PIPELINE-0002..0005,
  черновики старой нумерации становятся не нужны — либо удаляются,
  либо помечаются как historic snapshot.
- `0009-review-progress.md` — historic, важна как контекст принятия
  ADR-0009; после рефакторинга — historic snapshot.

Решение про судьбу `adr-drafts/` — за чатом. Здесь только фиксирую
факт.

---

## 6. Класс упоминаний, не учтённый в первоначальной схеме

Добавляю как отдельную секцию по запросу «если по ходу обнаружится
класс упоминаний, который не учтён».

### 6.1. Diapason-формы

Многократные ссылки на **диапазон** ADR — «ADR-0001..0005»,
«ADR-0006..0009», «пакет ADR-0006..0009». Это особый тип:
переадресуется не на один ADR, а на множество.

Места:
- `CLAUDE.md:` нет (вместо этого — перечисление).
- `.claude/working-with-claude.md:92`: «как было до Сессии 1 итерации
  1 доводки пакета ADR-0006..0009».
- `.claude/strategy-summary/strategy-summary-v7.md:15,19,129,226,344`
  + аналогично в v3–v6.
- `.claude/adr/0001-reconcile-via-anomaly-report.md` — нет.
- В исторических нотах (migration-dryrun, migration-inventory) —
  многократно.

После рефакторинга диапазон-формы потребуют переписывания (например,
«пакет ADR-PIPELINE-0001..0005» или просто «пакет ADR-PIPELINE»).
В активных файлах (working-with-claude, strategy-summary v7) — требуют
переадресации.

### 6.2. Технические/содержательные примечания в самих ADR

Внутри принятых ADR-0001 и ADR-0002 уже есть **технические примечания
о переездах** (про ADR-0003, ADR-0004, ADR-0007). Это форма
встроенного журнала, по правилу из `adr/README.md` (раздел «Два типа
примечаний»).

Места:
- `adr/0001-reconcile-via-anomaly-report.md:223,229` — про ADR-0003,
  ADR-0007.
- `adr/0002-spec-document-standard.md:297,301,303,308` — про ADR-0003,
  ADR-0004, ADR-0007.
- `adr/0006-spec-principles.md:201` — про ADR-0007 §1.

При рефакторинге эти примечания исчезают вместе со старыми ADR (тела
старых не сохраняются; ADR-PRODUCT-0001 = новая нумерация ADR-0001 с
новым телом без таких примечаний). **Утрачиваемый контекст:** запись
о том, что AnomalyReport исторически жил в `docs/spec/models/` без
подкаталога и переехал в `models/core/` по дисциплине ADR-0007 §1.
Этот факт нужно либо вынести в `pipeline-evolution-log.md`, либо
явно зафиксировать как historical context.

### 6.3. Frontmatter `related_adrs: []` в шаблонах

Шесть шаблонов жанров (`model.md`, `lifecycle.md`, `process.md`,
`integration-mapping.md`, `reference.md`, `invariant.md`) содержат пустой
`related_adrs: []`. После рефакторинга это поле сохраняется как поле
frontmatter, но семантика «какие ADR здесь могут быть» меняется (теперь
PIPELINE-* или PRODUCT-*). Шаблон может потребовать дополнительной
заметки про две нумерации.

---

## 7. Открытые вопросы / развилки

Эти вопросы выявились по ходу инвентаря и требуют решения **в чате
до Этапа 1**, чтобы рефакторинг был детерминированным:

1. **OQ-INV-1. Судьба ссылок на ADR-0003/0004/0005.** Для каждой из
   трёх категорий (см. §3.4) — что делать с каждым активным
   упоминанием:
   (a) удалить ссылку, оставив только факт?
   (b) переадресовать на CLAUDE.md / working-with-claude.md /
       templates/README.md?
   (c) переадресовать на новый ADR-PIPELINE-0006 (если он закроет
       историческое наследие)?
   Например, `CLAUDE.md:85` («per ADR-0004, ADR-0005, ADR-0008») — что
   с ним?

2. **OQ-INV-2. Историческое наследие старых ADR в самих новых ADR.**
   Принятые ADR-0001 (→ ADR-PRODUCT-0001) и ADR-0002 (→ ADR-PIPELINE-0001)
   содержат технические примечания о переездах и о принятии ADR-0003,
   ADR-0004, ADR-0007 (см. §6.2). При пересборке этих ADR в новой
   нумерации тела пишутся заново. Что делать с этими историческими
   фактами? Варианты:
   (a) опустить (история есть в git);
   (b) перенести в `pipeline-evolution-log.md` отдельной записью;
   (c) оставить как «Технические примечания» в новых ADR с
       обновлёнными формулировками.

3. **OQ-INV-3. Diapason-формы (§6.1).** В CLAUDE.md, working-with-claude
   и strategy-summary-v7 есть «пакет ADR-0006..0009». После рефакторинга
   это «пакет ADR-PIPELINE-0002..0005»? Или формулировка обобщается до
   «пакет ADR-PIPELINE»? Особенно в `working-with-claude.md:157–164`,
   `flow/playbook.md:428–508` (раздел про режим as-if-Accepted) —
   эти разделы привязаны к конкретной истории пакета ADR-0006..0009.
   После рефакторинга они либо переадресуются на новый пакет (но он
   находится в статусе Accepted сразу после Этапа 1, не Proposed?),
   либо превращаются в generic правила работы с пакетами Proposed-ADR
   без привязки к конкретному.

4. **OQ-INV-4. Placeholder `ADR-NNNN` / `ADR-MMMM` / `ADR-XXXX`.** В
   шаблоне `adr.md`, `adr/README.md`, `working-with-claude.md`,
   `flow/playbook.md` используются плейсхолдеры `ADR-NNNN`. После
   введения двух семейств — что писать? Варианты:
   (a) сохранить generic `ADR-NNNN` (читатель понимает контекст);
   (b) перейти на `ADR-{PIPELINE|PRODUCT}-NNNN`;
   (c) ввести два набора шаблонов (`adr-pipeline.md`, `adr-product.md`).

5. **OQ-INV-5. Активные ноты от 2026-05-18.**
   `pipeline-spec-fitting`, `artifact-question-mapping`,
   `spec-document-migration-decomposition` — это рабочие ноты текущего
   сюжета, написанные в старой нумерации (5×L-* слоёв, ADR-0001..0009).
   Что с ними после Этапа 1: переписывать в новой нумерации, помечать
   как historic, или оставить как есть?

6. **OQ-INV-6. Strategy-summary-v7 — текущий снимок.** Он содержит 16
   упоминаний ADR в старой нумерации. После Этапа 1 — либо создаётся
   v8 с новой нумерацией, либо v7 правится (нарушая обычное правило
   «snapshot не редактируется задним числом»). Текущее правило
   snapshot-документов — append-only по версиям; рефакторинг ADR
   создаёт прецедент.

7. **OQ-INV-7. Skill `spec-cluster-migration` без упоминаний ADR
   (§3.7).** Может быть ожидаемой ситуацией (по Принципу 7) или
   пробелом. До Этапа 1 не блокирует; после — возможно, потребует
   ревью при общей чистке скиллов.

8. **OQ-INV-8. Backlog.** В `backlog.md:117` (omitted long line) есть
   строка про ADR-0005 без полного контекста (выходит за лимит грепа).
   Перед рефакторингом backlog должен быть прочитан полностью, чтобы
   не упустить omitted-line упоминания. То же — для
   `backlog.md:119` (ADR-0004 omitted).

---

## Подытоживая

- Инвентарь покрывает все 9 ADR; ссылки разбиты на frontmatter,
  direct-id, path, prose, index, placeholder.
- Активных файлов под правку — ~30; исторических — ~15.
- Топ-3 файлов под правку: `backlog.md`, `0009-domain-migration-strategy.md`
  (как сам ADR + его переписывание), `0007-spec-structure.md`.
- Самая чувствительная зона — упоминания удаляемых ADR-0003/0004/0005
  (§3.4): они требуют явного решения о переадресации каждой
  активной ссылки.
- Диапазон-формы и placeholder-формы (§3.6, §6.1) — отдельные классы,
  не сводимые к замене номера 1-в-1.
- 8 открытых вопросов (§7) — рекомендуется проработать в чате до старта
  Этапа 1, чтобы избежать переделок.
