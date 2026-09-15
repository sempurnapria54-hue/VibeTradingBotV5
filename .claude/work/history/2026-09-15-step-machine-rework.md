# Переработка машины шага — итог (2026-09-15)

## Что сделано

Решением держателя 2026-09-15 (`.claude/decisions/step-machine-rework.md`)
машина шага docs-first (`TOOLING` → `DOCS_CHECK_N` / `GAPS_CLOSE_N` → `CODE` →
`SYNC_DOCS_FROM_CODE` → `DONE`) заменена на
`CONCEPT → CONCEPT_REVIEW → CODE → DOCS → REVIEW → DONE`. Знание старой
машины переписано или снято; снятое лежит в подпапке
`2026-09-15-step-machine-rework/`.

## Переписано (живые носители)

| Носитель | Что стало |
|---|---|
| `.claude/processes/roadmap-step-execution.md` | машина статусов, гейты, три под-шага тестового шага; 130 KB → 14 KB |
| `.claude/skills/update-roadmap-progress.md` | гейт-предусловия по статусам, гейт инструментов без снятых энфорсеров, реактор, гейт грунта, гейт закрытия фазы |
| `.claude/skills/concept-review.md` | ревью достаточности: две проверки, отчёт с вердиктом |
| `.claude/rules/session-work-unit.md` | единица — один заход одного статуса; таблица единиц |
| `.claude/rules/edit-kind-obligations.md` | дом свипов по роду правки (пассажи перенесены из процесса); классификационная таблица и её энфорсер сняты |
| `.claude/rules/measurement-commands.md` | **новое правило**: нормы проверочных команд (оси, код 2, полнота форм, блок кода, мутационная проба, ловушки среды) |
| `.claude/templates/docs/review-report.md` | **новый шаблон** отчёта фокуса ревью — наследник gap-отчёта без машинерии доковой петли |
| `.claude/agents/reviewer.md`, `.claude/agents/tester.md` | реестр фокусов по статусам; tester — владелец тестового шага и контура источника |
| `.claude/skills/test-design.md`, `test-review.md`, `test-code.md`, `.claude/templates/docs/test-plan.md` | три под-шага тестового шага; общая форма кейса с четырьмя полями |
| `.claude/processes/source-api-testing.md` | специфика контура источника (кейсы, код-тесты) перенесена сюда из скиллов |
| `tools/session-prompt.md`, `.claude/skills/session-chain.md` | ходы держателя → `holder_decision`; единица по статусу |
| `.claude/rules/structure.md`, `handoff-worklist.md`, `self-description-form.md`, `parking-address.md`, `backlog-section-form.md`, `knowledge-ownership-by-service.md`, `carrier-levels.md`, `policy-home.md` | указатели и формулировки под новую машину |
| роли, скиллы фокусов, `design-fork`, `find-code-examples`, `reconcile-knowledge`, `pipeline-shakedown`, `question-delegation`, решения `trading-council`, `product-roadmap-type`, `backlog-machine-form`, `env-wait-deadline` и др. | указатели под новые статусы и дома |

Прежние редакции полностью переписанных файлов — `replaced/`.

## Снято (в подпапке)

- **Решения (13):** `closure-completeness-by-population`,
  `measurement-repair-not-extension`, `closure-mechanism-amendments`,
  `code-contact-as-gate`, `loop-limited-to-corpus`, `edit-self-description-pass`,
  `gating-node-closure-depth`, `mini-loop-verification-ceiling`,
  `per-node-closure-frame`, `population-origin-and-code-gate`,
  `proof-method-change`, `stagnation-ratio-minimum-denominator`,
  `step-11-holder-direct-order` — `decisions/`.
- **Правила (2):** `docs-loop-limits`, `stopped-node-disposition` — `rules/`.
- **Скиллы (4):** `closure-population`, `stagnation-detection`,
  `classify-gap-level`, `classify-code-blocking` — `skills/`.
- **Шаблон:** `gap-report` — `templates/`.
- **Инструменты (3):** `code-gate-check.py`, `edit-kind-check.py`,
  `insertion-neighborhood-check.py` — `tools/`; снятые записи реестра
  снятых редакций — `tools/retired-check-removed-entries.py.txt`.
- **Реестр контакта с кодом** `code-gate-ledger.json` — `work/`; четыре
  припаркованные позиции (E1, G3, G4, G6) живут секциями бэклога.
- **Заметка** `2026-05-29-ростер-тулинга-роадмап` — `notes/`.
- **Открытый вопрос PROC-Q3** (независимость линз от провенанса) — закрыт
  как беспредметный, текст — `open-question-PROC-Q3.md`.
- **Секция бэклога «Номера прогонов в шаблоне gap-отчёта»** — закрыта
  снятием шаблона.

## Бэклог и роадмап

- Маркеров с условиями по снятым статусам не было; переписаны тела секций,
  опиравшихся на заморозку усиления измерения и реестр гейтов
  («Позиции класса `ИЗМЕРЕНИЕ`», «Припаркованные позиции ревью шага 7»,
  адрес пассажа в javadoc, решётка в ограждённом блоке).
- Шаг 12 фазы 2: `DOCS_CHECK_1` → `CONCEPT_REVIEW`; концепция тестирования
  записана в хронику. Примечания `phase-2.md` очищены от docs-first.

## Реестр снятых редакций

Снято тринадцать записей, чей предмет — снятая машинерия; у пяти
популяции перенацелены на новые дома; заведены две записи о снятии
самой машины (единица сессии; docs-first → `CONCEPT_REVIEW`). Долг прозы
сокращён на три строки, долг §-адресов — на пять.

## Проверки

Гейт инструментов корпуса прогнан целиком по итогу; ненулевых кодов нет.
Реактор не гонялся: дерево кода тронуто только тремя javadoc-указателями.

## Связи

- Решение — `.claude/decisions/step-machine-rework.md`.
- Машина — `.claude/processes/roadmap-step-execution.md`.
- Хроника шага 12 — `.claude/work/progress/phase-2-step-12-chronicle.md`.
- Дайджест — `.claude/work/decision-digest.md` (Д1544-Д1552).
