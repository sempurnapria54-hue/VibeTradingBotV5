# Чистка закрытого из backlog.md и phase-1.md

## На какой вопрос отвечает этот файл

Какие закрытые пункты вычищены из `backlog.md` / `phase-1.md`
при проходе 2026-07-13 и где живут их итоги.

## Контекст

Проход по базе знания классами дефектов (2026-07-13): закрытые
пункты по конвенции самих файлов перенесены из рабочих файлов
сюда. Нумерация оставшихся секций backlog сохранена с пропусками.
Правило переноса — `.claude/rules/closed-work-transfer.md`.

## Вычищено из `backlog.md`

### Cross-cutting миграции (статус-блок)

Миграции 2026-05-27/28 (торговые сущности; архивные процессы;
API-кластер OKX) завершены и закрыты —
`history/2026-05-27-миграция-торговых-сущностей.md`,
`2026-05-28-миграция-процессов.md`, `2026-05-28-миграция-api-okx.md`.

### 1. Deal management: lifecycle, FSM, команды — закрыто 2026-05-28

FSM, command-layer, процесс `deal-management` и правила
мигрированы — `history/2026-05-28-миграция-процессов.md`. Хвосты
закрыты: DEAL-Q3
(`docs/decisions/deal-action-state-materialization.md`), DEAL-Q1 +
финализационные executor'ы
(`docs/decisions/deal-finalization-state-materialization.md`;
код — на `CODE` шага 6).

### 3. Калькуляторы действий стратегии + RVO — закрыто 2026-05-28

Калькуляторы, RVO и процесс `strategy-action-calculation`
мигрированы — `history/2026-05-28-миграция-процессов.md`. Хвосты
закрыты: RISK-Q1 (`docs/decisions/per-trade-risk-policy.md`),
PROC-Q1 — рудимент `PositionContext` не материализуется
(`history/2026-06-06-delegation-validation.md`).

### 4. Risk-слой — закрыто 2026-05-28

Валидатор, resolver, RVO, правило, процесс `risk-evaluation`
мигрированы — `history/2026-05-28-миграция-процессов.md`.

### 5. Расчёт индикаторов и рыночных данных — закрыто 2026-05-28

Jobs, сервисы, market-data модели, правило и процесс
`market-data-calculation` мигрированы —
`history/2026-05-28-миграция-процессов.md`. Хвосты закрыты:
standalone `Candle`/`CandleGroup`/`Instrument` материализованы на
`GAPS_CLOSE_1` шага 1 (`docs/models/domain/other/Candle.md` и
соседние), `TimeFrame` размещён в `CandleGroup.md` (TIME-Q1).
Архивный легаси-исходник —
`.claude-archive/2026-05-21/docs/deprecated/models/domain/old/Candle.md`.

### 10. API-кластер OKX — закрыто 2026-05-28

26 REST endpoint-доков мигрированы в `docs/integrations/okx/` —
`history/2026-05-28-миграция-api-okx.md`. Открытыми остались
OKX-Q2/OKX-Q4 (`open-questions.md`); OKX-Q1/OKX-Q3 закрыты
(`docs/decisions/result-profit-source.md`). Playbooks v1 — вне
скоупа.

### P1. Код-шаблоны для `code-writer` — закрыто 2026-05-31

Решённая модель: код-шаблоны — тир `.claude/templates/code/`
(вход для письма), `find-code-examples` — пост-код-скилл;
отдельного слоя «референс-доков» нет. Обоснование и закрытие
REF-Q1 — `.claude/decisions/code-templates-vs-examples.md`.

### S1. Vault — базовая привязка закрыта 2026-06-12

Сделано на инфра-шаге (раньше планового шага 9): Vault-привязка
секретов per-profile (`spring.config.import: vault://` —
datasource и OKX-креды) — детали в снапшоте v47 и
`.claude/rules/tech-radar.md` (строка spring-cloud-vault).
Остаток (хардненинг) — живой пункт `backlog.md` §S1.

### Шаг 6: бесстоповый risk-creating вход — закрыто 2026-06-22

Инвариант — `docs/rules/risk-creating-entry-protection.md`
(`PRECHECK` блокирует вход без резолвимого стопа; закрыл TR1
`DOCS_CHECK_1` шага 6). Код-снятие fail-open
(`RiskValidator`/`SizeCalculator`, `PrecheckHandler` +
set-leverage) выполнено на `CODE` шага 6.

### Шаг 6: `EXECUTE_KILL_SWITCH` — эмиссия подключена 2026-06-23

CODE-делта холдов; позже на §6a kill-switch перестал быть
командой — тип `EXECUTE_KILL_SWITCH` убран, side-executor
зовётся из `KillSwitchService`
(`docs/components/KillSwitchExecutor.md`).

### I3. `OkxSigningInterceptor` — внятная ошибка на пустых кредах — закрыто 2026-06-20

Fail-fast `requireCredentials()` в
`OkxSigningInterceptor.intercept` + тест
`ICredEmptyCredentialsLiveTest` переведён на ожидание внятной
ошибки — `history/2026-06-20-source-api-contour.md`.

### Ре-база source-api: снятие mapped-поверхности — закрыто 2026-06-18

`OkxProxyController` переписан на A2 raw-passthrough,
mapped-цепочка `getMarketPriceData` снята (forward-дизайн шага 5
сохранён в `docs/models/mapping/MarketPriceData.md`) — детали —
`.claude/decisions/source-api-target-rebase.md` §Следствия.

## Вычищено из `phase-1.md`

- **Граница шага 6 ↔ 7 (2026-06-21)** — перенесена записью в
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-chronicle.md`.
- **Жёсткие гейты `DONE` шага 6 (D-B3/D-M1)** — гейты пройдены;
  определения — `backlog.md` §Хвост шага 4, прохождение — chronicle
  шага 6 (записи `CODE` / сверка scope / §6a).
- **Error-политика (зафиксирована на `GAPS_CLOSE_1` шага 6,
  2026-06-22)** — норматив: `docs/rules/error-handling-policy.md`
  (+ `codestyle.md` §Обработка ошибок); хроника закрытия — chronicle
  шага 6 (запись `GAPS_CLOSE_1`, N1).
- **DONE-заметки шагов 3–6** ужаты до формата 3-5 строк; срезанные
  перечисления — в chronicle-файлах соответствующих шагов.
