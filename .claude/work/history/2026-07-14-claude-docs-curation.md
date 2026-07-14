# Курация базы знания `.claude` (2026-07-14)

## На какой вопрос отвечает этот файл

Что изменила курация базы знания 2026-07-14 (что перенесено,
вычищено, введено — и где живут итоги).

## Контекст

Ревью доков `.claude` по запросу пользователя; закреплено как
регулярная обязанность куратора — новое правило
`.claude/rules/curation.md`, дерево-индекс
`.claude/knowledge-tree.md`.

## Перенесено в `history/`

- **Снапшоты v1–v65** → `history/snapshots/` (в
  `.claude/snapshots/` остаётся только актуальный; правило —
  `structure.md` строка snapshots + `curation.md` п.3).
- **`work/run-logs/`** (папка упразднена):
  `REPORT-2026-06-19-source-api-stabilization.md`, `okx_demo.py`,
  `run.sh` → `history/2026-06-20-source-api-contour/` (артефакты
  той задачи). Ссылки на несохранившиеся `*.log` в
  `.claude/tests/source-api/okx/plan.md` заменены указателем на
  REPORT.
- **Заметки с закрытой темой без живых входящих ссылок** →
  `history/`: `2026-05-21-archive-concept-extraction.md`,
  `2026-05-23-обкатка-классификации-deal-lifecycle.md`,
  `2026-05-26-обкатка-классификации-процессы.md`,
  `2026-06-19-source-api-code-tests.md`. Заметки с живыми ссылками
  (провенанс decisions/agents/processes) остались в `notes/`.

## Вычищено из `backlog.md` (итоги закрытого)

- **§2 «Мигрировано»:** `OrderExternalStatusResolver`,
  `AlgoOrderExternalStatusResolver`, `PositionStatusResolver`
  (+ RVO), refresh/close executor'ы — доки в `docs/components/`,
  хроника — `history/2026-05-27-миграция-торговых-сущностей.md`.
- **§6 «Мигрировано»:** правило
  `docs/rules/audit-not-runtime-source.md`; PnL-финализация закрыта
  на шаге 7 (`docs/decisions/result-profit-source.md`, OKX-Q1
  закрыт, `REFRESH_FILLS` снят в `GAPS_CLOSE_2`).
- **§7 «Мигрировано»:** `AnomalyReport` модель+lifecycle
  (2026-05-27); `AnomalyJob`, `KillSwitchExecutor` (2026-05-28).
- **§8 «Мигрировано/Построено»:** enforcement
  `Strategy.INACTIVE`/`DELETED` (`docs/lifecycles/Strategy.md`);
  scope валидатора (STRAT-Q3,
  `docs/decisions/strategy-materialization-and-validation.md`);
  Strategy API + create-валидатор + «одна реализация» — `CODE`
  шага 2 (`history/2026-06-05-phase-1-step-2-strategy.md`).
- **§9 статус-блоки GAPS_CLOSE_1/2:** минимальные модели
  `Instrument`/`Exchange` (`docs/models/domain/core/`),
  онбординг-путь lifecycle (`docs/lifecycles/Instrument.md`),
  INSTR-Q1 закрыт
  (`docs/decisions/instrument-external-rules-materialization.md`).
- **Шаг 6, холды L3/L4:** реактивный enforcement построен
  (CODE-делта холдов, 2026-06-23/24; D2-реактивный снят) — детали в
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/`
  (`phase-1-step-6-holds-design.md`, `phase-1-step-6-code.md`).
  Остатки — форвард шага 8 (backlog §Шаг 8).
- **Шаг 8, kill-switch:** per-инструмент контур построен,
  декларативный kill-switch (Scope A/B) откачён (код-ревью заход 2,
  2026-07-01) — `docs/components/KillSwitchExecutor.md`, chronicle
  шага 6.
- **Шаг 8, FSM/action слоистость:** decision
  `docs/decisions/fsm-execution-layering.md` + Stage 1 построены
  (2026-07-01); Stage 2/3-рефактор — на `SYNC_DOCS_FROM_CODE` шага 6
  (`history/2026-07-03-phase-1-step-6-fsm-orchestration.md`).
- **«Рассмотрено, не берём» (прогоны 2-3):** В-4 batch-write —
  конфликт с гранулярностью «одна команда — одна сущность» (CMD-Q3);
  контракт задокументирован (`contracts/batch-operations.md`),
  `mass-cancel` вне периметра. В-5 STP — сознательно не используется;
  действует биржевой default `acctStpMode=cancel_maker`
  (`contracts/account-config.md`). Пересмотр — только при новой
  фактуре.
- **Хвост шага 4, гейтовые D-B3/D-M1 — закрыты на шаге 6:**
  D-B3 SUBMIT recovery-by-clientId реализован; D-M1
  concurrency-guard — проход `DealOrchestratorJob` под
  `JobExecutionGuard` (спека пересмотрена: in-process в фазе 1,
  БД advisory — фаза 3;
  `docs/components/DealOrchestratorJob.md` §Concurrency-guard).
  Прохождение гейтов — chronicle шага 6.
- **I4 «Сделано (interim)»:** F3a/F4 — `@JsonProperty` на 7 полях
  OKX-DTO (`sCode`/`sMsg` ack; `cTime`/`uTime` read) + тесты
  `OkxAckDeserializationTest` / `OkxReadDtoDeserializationTest`;
  grep паттерна lower-upper по OKX-DTO исчерпан. Run-log —
  `history/2026-06-20-source-api-contour/source-api-pilot-run-log.md`.
- **Унификация джоб (нарратив):** прежний `OrchestratorPassLock`
  (БД advisory, raw-JDBC) удалён как преждевременный (2026-07-01),
  код в git-истории; оркестратор выровнен на `JobExecutionGuard`;
  п.6 «SQL только в репозиториях» снят из фазы 1.
- **Мета-раздел «Статус»** backlog свёрнут в шапку «Связь с
  роадмапом».

## Вычищено из `phase-1.md`

- **DONE-заметки шагов 3–6** удалены; итоги и хроника — в
  chronicle-файлах шагов:
  `history/2026-06-10-phase-1-step-3-derived-market-data/phase-1-step-3-chronicle.md`,
  `history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-chronicle.md`,
  `history/2026-06-20-phase-1-step-5-risk-precontrol/phase-1-step-5-chronicle.md`,
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-chronicle.md`.
- **Хроника текущего шага 7** перенесена в
  `work/progress/phase-1-step-7-chronicle.md` (новая конвенция:
  хроника текущего шага живёт в `progress/`, не в `phase-N.md` —
  `closed-work-transfer.md` §Roadmap обновлён).

## Правки «один файл — один вопрос»

Составные вопросы переформулированы: `roadmap.md`, `phase-1.md`,
`structure.md` (строки roadmap / lifecycles / mapping / snapshots),
`decisions/product-roadmap-type.md`,
`library/trading/distilled/corpus-map.md`,
`processes/api-docs-completion.md`, `snapshots/snapshot-v66.md`.
Принцип «вопрос — ровно один» закреплён в `structure.md` §Принципы.

## Введено

- **`.claude/knowledge-tree.md`** — дерево-индекс каталогов и
  файлов с вопросом каждого узла (строка в таблице `structure.md`).
- **`.claude/rules/curation.md`** — правило регулярной курации
  (триггеры, чек-лист свипа); обязанность внесена в
  `agents/knowledge-curator.md`.
- **`structure.md`:** зарегистрирован `work/delegation-ledger.md`
  (ранее вне таблицы); судьба старых снапшотов —
  `work/history/snapshots/`.
- Указатели на дерево и курацию — в `CLAUDE.md` и
  `chat/structure-digest.md` (копию дайджеста в PK — обновить).

## Починенные ссылки

- `skills/concept-review.md` — пример gap-отчёта обобщён до
  плейсхолдера (указывал на файл, уехавший в history).
- `tests/source-api/okx/plan.md` — три ссылки на несохранившиеся
  `run-logs/*.log` заменены/сняты (итог — REPORT в history).
