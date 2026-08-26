# Snapshot v88

**Дата:** 2026-08-26.

## На какой вопрос отвечает этот файл

Где мы сейчас. **Пересмотр пайплайна выполнен: пакет из шести решений
держателя (по итогам анализа потока вопросов шага 7) материализован —
четыре концептуальных правила, лестница биржевых safety-состояний,
режим автономии, «дом политики», диета рабочих файлов, дистилляция
`DOCS_CHECK`-формы, новые Custom Instructions чата.** Сменяет v87.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-6 `DONE`, шаг 7 — `GAPS_CLOSE_17`
(закрыт), шаги 8-11 `HOLD`. Ветка `claude-audit`, `HEAD = 53d9fee`;
вся дельта пересмотра — **staged, ждёт ручного ревью и коммита**.

**Режим — автономия до конца фазы 1**
(`.claude/processes/question-delegation.md`): проектные развилки CC
решает сам и фиксирует в дайджесте решений; пользователю — только
дефицит `подтверждение`. Гейт делегирования, леджер, ступени, слепые
проходы, пакеты валидации — выключены
(`.claude/work/history/2026-08-26-autonomy-transition.md`).

**Дайджест решений Д1-Д12 ждёт чтения пользователем** —
`.claude/work/decision-digest.md` (вето по пункту возвращается CC
задачей).

## Что произошло за сессию (после v87)

| Что | Носитель |
|---|---|
| Четыре правила держателя материализованы | `docs/rules/execution-hierarchy.md` (иерархия исполнения); `docs/rules/error-handling-policy.md` уровень 4 (неожиданное поведение биржи); `.claude/rules/design-simplicity.md` (минимальный след — норма); `docs/rules/risk-creating-entry-protection.md` (инвариант «живой риск без защиты») |
| **Лестница биржевых safety-состояний** — `Exchange.HOLD` (заморозка, без kill-switch) ↔ `Exchange.TRADE_BLOCKED` (полная, flatten); flatten снят с controlled-троп, бесстоповая позиция уехала с L3 на ступень 2 | дом `docs/rules/exchange-hold.md`; решение `docs/decisions/exchange-safety-ladder.md`; свип по ~20 докам (HoldService, HoldSignal `FREEZE`, координатор, handlers, lifecycles); CODE-дельта — `backlog.md` §Шаг 7 |
| Режим автономии вписан в процессы; агенты/скиллы приведены | `.claude/processes/question-delegation.md` (переписан), `roadmap-step-execution.md` §3 (переписан, 64 → 33 KB), 11 файлов агентов/скиллов |
| Принцип «дом политики» + правило диеты рабочих файлов | `.claude/rules/policy-home.md`; `.claude/rules/closed-work-transfer.md` §Диета |
| Распил гигантов: `pnl-finalization-mechanics.md` 157 → 24 KB (дом сверки — **`docs/rules/pnl-reconciliation.md`**, новый); `Deal.md` 136 → 89,5 KB (якоря сохранены) | архивы — `.claude/work/history/2026-08-26-policy-home-split/` |
| Диета рабочих файлов: `backlog.md` 130 → 92 KB, `open-questions.md` 62 → 27 KB (все 16 вопросов живы, сжаты) | итоги — `.claude/work/history/2026-08-26-workfiles-diet.md` |
| Дистилляция `gap-report.md` 102 → 24 KB: принципы вместо кейсов, потолок отчёта 40 KB, блокирующее/прочее, без попарных сверок и полных грепов | `.claude/templates/docs/gap-report.md`; архив кейсов — `history/2026-08-26-gap-report-archive.md` (не обязательный вход линз) |
| Custom Instructions чата переписаны (33 → 20 KB): автономия, дайджест, только `подтверждение` | `.claude/chat/chat-project-instructions.md` — **вставить в Settings** |

## Ратифицировано — не пере-решать

- Лестница: две ступени, выходы `TRADE_BLOCKED → HOLD → ACTIVE`
  вручную, эскалация `HOLD → TRADE_BLOCKED` (Д2/Д3 дайджеста).
- `HoldSignal.ReactionClass.FREEZE`; фабрика `exchange()` → ступень 1,
  `exchangeTradeBlock()` → ступень 2; отчёт ступени 1 —
  `NON_CRITICAL` (Д6).
- Градация остаётся 4-уровневой; уровень 4 двухступенчатый (Д11).
- Cleanup/safety-команды handler'ов — легитимное исключение иерархии
  исполнения (Д4).

## Что дальше (очередь)

1. **Пользователь:** закоммитить дельту; вставить новый
   `chat-project-instructions.md` в Settings → Custom Instructions;
   обновить Project Knowledge (этот снапшот, `structure-digest.md`
   при необходимости, новые дома правил, похудевшие рабочие файлы).
2. **Прочитать дайджест Д1-Д12** (`.claude/work/decision-digest.md`);
   вето — задачей CC.
3. **Первый прогон `DOCS_CHECK` новой формой** (отдельный промпт).
   17 гейтящих находок `DOCS_CHECK_17` этой сессией не разбирались —
   идут в него; многие закроются четырьмя правилами держателя.
4. CODE-дельта лестницы — `backlog.md` §Шаг 7 §«Лестница биржевых
   safety-состояний».
5. Остаточные кластеры policy-home — `backlog.md` §M3 (по мере
   касания).

## Среда

Без изменений — см.
`.claude/work/history/snapshots/snapshot-v87.md`.
