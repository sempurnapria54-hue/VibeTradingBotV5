# Snapshot v65

**Дата:** 2026-07-03.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — шаг 6 фазы 1 полностью закрыт
(`DONE`; пройдены все гейты, включая пост-хок концепт-гейт §6a через
`GAPS_CLOSE_4` / `DOCS_CHECK_5` / `GAPS_CLOSE_5`). Стартован шаг 7 «Сделки и
P&L» — под-шаг `DOCS_CHECK_1` (concept ×2 + trading).** Сменяет v64.

## Состояние

Фаза 1 — `IN_PROGRESS`; **шаги 1-6 `DONE`, шаг 7 `IN_PROGRESS`
(`DOCS_CHECK_1`)**, шаги 8-11 `HOLD`. Ветка `claude-audit`.

`HEAD = fc3d0a8 (ROADMAP 1-6-5)` — сюда закоммичены SYNC-доки v64. **Поверх
HEAD в staging, не закоммичено:** дельта `GAPS_CLOSE_4`/`GAPS_CLOSE_5` (§6a) —
3 новых дока (`SafetyHoldCoordinator.md`, `KillSwitchService.md`,
`models/HoldSignal.md`) + ~11 правок (`Deal.md`, `trading-constraints.md`,
`SubmitOrderExecutor.md`, `PrecheckHandler.md`, `action-orchestration-vs-command.md`,
`DealStateMachine.md`, `MarkDealClosedExecutor.md`, `EntryFinalizedHandler.md`,
`AnomalyReport.md`, `Instrument.md`/`mapping/Instrument.md`, `open-questions.md`)
+ снапшот + прогресс-файл `phase-1-step-6-docs-check-4.md`. CC не коммитит —
ревью/коммит в IDEA за пользователем.

## Как сюда пришли (после v64)

v64 зафиксировал `SYNC_DOCS_FROM_CODE` шага 6 (52 дока под as-built). Далее —
пост-хок концепт-гейт §6a:

- **`DOCS_CHECK_4`** (concept-review по пост-sync докам): 6 пробелов — 2 блокера
  (таксономия kill-switch «команда» vs side-executor; частичный unique-index
  `uk_deal_active_instrument` не задан) + 4 не-блокера (inline set-leverage не у
  owner-дока; `SafetyHoldCoordinator`/`HoldSignal` без спеки; placeholder-ZERO не
  заявлен; битая ссылка §8.C).
- **`GAPS_CLOSE_4`** (docs←code, документирование as-built): все 6 закрыты.
  Kill-switch «команда»→side-executor; §Персистентность в `Deal.md`
  (`uk_deal_active_instrument`, предикат `NOT IN (CLOSED, EMERGENCY_CLOSED)`,
  benign insert-race) + `trading-constraints.md` (app-gatekeeper + DB
  defense-in-depth); inline set-leverage в `SubmitOrderExecutor.md` (INSTR-Q2
  закрыт); новые `SafetyHoldCoordinator.md` / `HoldSignal.md` (+`HoldScope`) /
  `KillSwitchService.md`; placeholder-ZERO примирён (`Deal.md` §Итоговый PnL +
  `MarkDealClosedExecutor.md`); §8.C ссылка починена.
- **`DOCS_CHECK_5`** (перепрогон): 6 пробелов подтверждены закрытыми; 1 новый
  минорный остаток — `AnomalyReport.md` не нёс поле `scope` (docs↔code-лаг).
- **`GAPS_CLOSE_5`**: `scope: HoldScope` добавлено в `AnomalyReport.md`.
- **§6a ПРОЙДЕН.** Совокупно с CODE-фокусами (`conventions`/`performance`/
  `disaster`) и SYNC (`divergence`) — все гейты `DONE` шага 6 удовлетворены,
  включая жёсткие D-B3 (recovery-by-clientId) / D-M1 (concurrency-guard).
  Шаг 6 → `DONE`.

## Шаг 7 — старт (`DOCS_CHECK_1`)

**Scope (граница 6↔7).** `DealOrchestratorJob` агрегирует факты исполнения в
`Deal`; расчёт `resultProfit` / P&L. Петля, FSM, финализационная механика —
шаг 6 (DONE), вне скоупа. Шаг 7 сужен до: **(1) расчёт самого числа
`resultProfit` на терминале** (вкл. PnL для `EMERGENCY_CLOSED`; контракт DEAL-Q2
«когда обязателен» закрыт на шаге 6 — остаётся число); **(2) агрегация фактов в
`Deal`**. Сейчас код шага 6 пишет `resultProfit = ZERO` placeholder — шаг 7
заменяет реальным расчётом.

**Форвард-долг, приземляющийся на шаг 7:**
- Комиссии в риск-расчёте (со §6a шага 5): включить в risk-amount / risk-bounded
  сайзинг или обосновать вне (`per-trade-risk-policy.md` §«Учёт комиссий —
  отложен к шагу 7»; `RiskValidator`/`SizeCalculator` — «опущены, фаза 1»).
- В-3 `positions-history`: `realizedPnl = pnl + fee + fundingFee + liqPenalty`.
- В-6 `funding-rate(-history)`: funding-компонент PnL SWAP. **OKX-Q3** — две
  дороги к funding (bills subType 173/174 vs `funding-rate-history.realizedRate`);
  шаг 7 выбирает осознанно, не ведёт два трека.
- В-7 `trade-fee`: ставки комиссий.
- Backlog §6 (аудит/история + breakdown PnL: fills, avg prices, partial exits,
  `TradeFill`/`TradeFillsArchive`, `REFRESH_FILLS` 3d→3m, ~30 подвопросов) —
  граница «audit/история → шаг 8?» vs «PnL-число → шаг 7» на ревью уточняется.

**Прогон.** `TOOLING` пройден без новых артефактов (фокусы `concept`/`trading`
активны). `DOCS_CHECK_1` — запущены независимые ревьюер-субагенты (concept ×2:
механика финализации/агрегации + данные/модели PnL; trading: корректность
PnL/комиссий/funding). Gap-отчёт собирается — `phase-1-step-7-docs-check-1.md`.

## Loose ends

- Staged дельта §6a + снапшот — ревью/коммит в IDEA (CC не коммитит).
- `phase-1.md`: хронологические записи шага 6 `SYNC`→`DONE` в моменте не
  дописывались (таблица держала статус); консолидирующая запись добавлена этой
  сессией.
- `tradingbot.iml`, `vault.hcl` — untracked, не трогать.

## Что дальше

1. Отчёт `DOCS_CHECK_1` шага 7 → gap-разбор → `GAPS_CLOSE_1` (либо чисто →
   концепт-гейт `CODE`).
2. Открытые вопросы шага 7 (кандидаты стадии 0): **OKX-Q3** (funding-путь),
   политика включения комиссий, STRAT-Q4 (якорь allocation %).

## Среда

Тулчейн/инфра — без изменений (см. v62/v64): `mvn` 3.9.11 из wrapper-dist,
`JAVA_HOME=~/.jdks/corretto-25`; boot — `SPRING_PROFILES_ACTIVE=test`, джобы
`*_ENABLED=false`, `VAULT_TOKEN`/`VAULT_URI` из `.env.vault.test.local`; docker
postgres:16 (5441 test), vault (8200).

## После коммита

Обновить PK (v65 заменяет v64).
