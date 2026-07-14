# Snapshot v61

**Дата:** 2026-07-02.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — шаг 6 `CODE`: поверх реактивных
холдов L3/L4 переработана слоистость исполнения сделки (handler = 3 метода,
kill-switch вынесен в аварийный executor сбоку, преждевременный advisory-lock
снят). Впервые в этой среде дельта не только компилируется (JDK 25), но и
поднимается вживую (bootstrap test-профиля зелёный).** Сменяет v60.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-5 `DONE`, **шаг 6 `CODE`** (дельта дописана,
**компилируется и бутается**; финальный аппрув за пользователем — **ещё не
дан**), шаги 7-11 `HOLD`. Ветка `claude-audit`.

Вся дельта шага 6 — **staged, не закоммичено** (CC не коммитит): **34 файла,
+1049 / −472** поверх `HEAD = 0eb1a50 (ROADMAP 1-6-4 CODE_3)`.

## Как сюда пришли (эта сессия)

**Вход:** 7 замечаний по ревью staged-дельты — (1) `DealContext` должен
собираться и для сделок неактивной стратегии; (2) стрим-логику — в rich-модели;
(3) nullSafe/safe-хелпер — в util; (4) общий механизм джоб — в родителя;
(5) единый лок по всем джобам; (6) SQL только в репозиториях; (7)
`KillSwitchExecutor` обязан гарантировать полное снятие риска (не отдавать `ok`
при неудачной отмене ордеров).

**Дизайн-диалог (главная развилка сессии):** развели понятия
`CommandExecutor` / `StrategyActionExecutor` / kill-switch; **ввели, затем
РАЗВЕРНУЛИ** декларативный kill-switch (Scope A/B — kill-switch как объявленное
`StrategyAction`). Итог: kill-switch — **не** `StrategyAction`, а аварийный
executor **сбоку**; выход сделки — **условием-перехода** стратегии, а не
действием; преждевременный `OrchestratorPassLock` (advisory-lock на проход) —
**снят** (многопоточка отложена на фазу 3, в фазе 1 хватает in-process-гарда).

**Оформление → построение:** зафиксировали `docs/decisions/fsm-execution-layering.md`,
затем построили по нему **Stage 1** (контракт handler'а из 3 методов).

## Дельта шага 6 — два слоя

### А. Реактивные холды L3/L4 (дизайн v60, дописаны + провалидированы ранее — staged)

- `domain.safety`: `HoldScope`, `HoldSignal{scope,code}`, `SafetyHoldCoordinator`,
  `KillSwitchService`, `AnomalyReport` (+`Service`, entity, 4 jsonb-слепка,
  миграция `V10`).
- Статус `TRADE_BLOCKED` у **`Instrument` и `Exchange`** (заморозка только из
  `ACTIVE`; ручной un-hold `TRADE_BLOCKED→ACTIVE` через REST
  `POST /api/{…}/{internalId}/trade-unblock`; L4 — одно снятие биржи отпускает
  каскад).
- Триггеры в handler'ах (`DealFsmSupport`): `markError` → **L4** при
  controlled-violation в retry-anchor'ах; `markErrorStopless` → **L3**
  (бесстоповая позиция постфактум) на двух точках `EntryFinalizedHandler`/
  `ProtectionSwitchedHandler`; координация реакции в проходе
  (`SafetyHoldCoordinator`: TRADE_BLOCKED первым → `AnomalyReport` before →
  kill-switch → after → COMPLETED); enforcement (`EntryScannerJob` фильтрует
  held-инструменты + читает статус биржи; `DealOrchestratorJob.enforceHold`
  уводит активную held-сделку в `ERROR`).
- **HOLD-Q1 закрыт** `docs/decisions/controlled-violation-exchange-wide-hold.md`:
  controlled-violation = **безусловный L4** (доминирует L3), квалификатор офдока
  «по severity/safetyImpact» снят; зафиксирован **переиспользуемый принцип** —
  под неизвестный радиус незрелой интеграции тормозим консервативно широко.
- Ревью холд-дельты (`conventions`/`performance`/`disaster`) прогнаны, блокеры
  закрыты; №2 (`DealContext` для INACTIVE-стратегии → `StrategyDetailRepository.findByIdWithTree`
  + `getRequiredDetailByIdWithTree`, review-пункт 1 и 6); №3 (`AnomalyReport`
  терминал гейтится **фактом** закрытия, не ACK).

### Б. Слоистость исполнения FSM (эта сессия)

- **`FsmHandler` = 3 метода:** `checkEntry` (субъект + среда: сделка, в которой
  нечего вести, — это входное условие) / `checkTransition` (этап завершён →
  `nextStatus`) / `handle` (прогресс действия). Default-методы (`Optional.empty()`)
  — для инкрементальной миграции. `DealStateMachine.advance` =
  `checkEntry.or(checkTransition).orElseGet(handle)`.
- **Все 7 handler'ов разложены:** `Precheck`, `EntrySubmitted`, `EntryFinalized`,
  `Managing`, `ProtectionSwitched`, `Error` — с `checkEntry`/`checkTransition`;
  `ExitPending` — handle-only cleanup (входного условия нет, корректно).
  `EntrySubmitted` держит факт-каскад в `handle` (общие факт-проверки не дублируем
  в `checkTransition`).
- **decision `docs/decisions/fsm-execution-layering.md`:** слои петля → handler →
  оркестратор действия → `StrategyActionExecutor` (per-pass эмиттер на тип
  действия) → `CommandExecutor` (1 команда); kill-switch **сбоку**; exit —
  условие-перехода; уточняет `action-orchestration-vs-command.md` (CMD-Q6); тип
  `ServiceCommandType.EXECUTE_KILL_SWITCH` **убран** (kill-switch — не команда).
- **Kill-switch переработан:** `domain.command.executor.KillSwitchExecutor`
  → `domain.command.action.KillSwitchExecutor` (git mv + переписан). Теперь
  обычный `@Component` **вне реестра команд** (не `CommandExecutor`, не
  `StrategyActionExecutor`); зовётся напрямую —
  `KillSwitchService … killSwitchExecutor.execute(dealContext).getSuccess()`.
  Ограниченный teardown-loop (`KillSwitchProperties.maxTeardownAttempts`,
  дефолт 3), refresh-confirm по фактам (close позицию → cancel orders → cancel
  algo → безусловный close → сверка `isFlat`); инвариант «защиту снимать только
  после подтверждённого закрытия позиции». Конфиг `kill-switch.max-teardown-attempts`
  в `application.yaml`.
- **`OrchestratorPassLock` удалён** → `DealOrchestratorJob` на
  `JobExecutionGuard.runExclusively("dealOrchestratorJob", …)` — единый механизм
  с прочими джобами (review-пункты 4/5). `tech-radar` Raw-JDBC-строка → `hold`;
  `docs/components/DealOrchestratorJob.md` §concurrency-guard → in-process guard.
- **Rich-модели (review-пункты 2/3):** `Deal.liveOrders()/liveAlgoOrders()/
  hasLivePositionRisk()`; `DealFsmSupport` делегирует в них; извлечён
  `DealContextService.reloadRuntimeGraph(deal)`.

## Верификация (впервые прогнана в самой среде)

- **Компиляция:** `mvn -o -q -DskipTests compile` (Maven 3.9.11 wrapper-dist +
  Corretto 25) → **EXIT=0**; все 7 handler'ов на контракте, осиротевшего кода нет.
- **Bootstrap:** приложение поднято на **test-профиле** — `Started
  TradingBotApplication in ~7s`, Flyway *«Schema up to date. No migration
  necessary»* (удаление `V11` чисто — миграция никогда не применялась), весь
  Spring-контекст связан (рефактор handler'ов, `KillSwitchExecutor`+
  `KillSwitchProperties`, `JobExecutionGuard` на оркестраторе, `RetryPolicyService`)
  — **без DI-цикла/недостающих бинов**. `exit=124` = таймаут-kill долгоживущего
  процесса (ожидаемо).
- Это **снимает** оговорку v60 «компиляция as-built — за пользователем в IDEA»:
  CC теперь и компилирует, и бутает в среде.

## Loose ends (перед аппрувом/следующим заходом)

- **`MarketStructureJob.java` — unstaged stray-reformat** (переупорядочивание
  импортов + переносы method-chain, вероятно авто-формат IDEA). **Не** часть
  логической дельты — откатить/дискардить (`git checkout --`).
- **Progress `phase-1-step-6-code.md` частично устарел:** описывает
  `D-M1 = OrchestratorPassLock (built)` и kill-switch через
  `EXECUTE_KILL_SWITCH`-команду — переработку этой сессии (guard-swap,
  kill-switch вынесен сбоку) ещё не отражает. Выровнять при обновлении / на `SYNC`.
- `tradingbot.iml`, `vault.hcl` — untracked, не трогать.

## Что дальше (хвост шага 6)

1. **Финальный аппрув `CODE`** (scope-complete) — за пользователем.
2. **Форварды шага 6** (в `backlog.md`): **Stage 2** — per-pass
   `StrategyActionExecutor` на тип действия (обобщение `DealActionPlanner` +
   `ServiceCommandFactory`, сохраняет CMD-Q6); **Stage 3** — transition-conditions
   в модели стратегии + exit-as-transition (`MANAGING→EXIT_PENDING` без
   `DEAL_EXIT`) + снять вырожденный `CLOSE_FULL`. Можно считать шаг закрытым на
   уровне Stage 1 — выбор за пользователем.
3. **Большой `SYNC_DOCS_FROM_CODE`** (`divergence` docs←code): компонент-доки
   handler'ов + `DealStateMachine.md`, `KillSwitchExecutor.md`, квалификатор
   `controlled-exchange-exceptions.md`, `exchange-hold.md`/`instrument-hold.md`,
   §6a-концепт-инкременты → пост-хок концепт-гейт → `DONE`.

**Жёсткие гейты `DONE`:** **D-B3** (SUBMIT recovery-by-clientId) — built;
**D-M1** (per-pass concurrency-guard) — удовлетворён **in-process
`JobExecutionGuard`** для фазы 1 (одиночный инстанс; распределённый лок —
фаза 3, `tech-radar` Raw-JDBC → `hold`).

## Концепт-инкременты на `CODE` (для §6a / финальной проверки)

placeholder-profit `MarkDealClosed` (ZERO; реальный PnL — шаг 7), retry re-arm
RETRY_PENDING→производная стадия, частичный unique-index
`uk_deal_active_instrument`, set-leverage inline-представление, D-B3
representation, дизайн холдов + слоистость FSM как въехавшие инкременты.

## Открытые вопросы

HOLD-Q1 — **закрыт** (решение controlled-violation). Прочие без изменений
(INSTR-Q2 остаток, ORCH-Q1, CMD-Q4→шаг 8, OKX-Q1/Q2/Q3→шаг 7, OKX-Q4 WS,
STRAT-Q4, IND-Q1, STRUCT-Q1, PHASE-Q1/Q2). Ни один не гейтит текущий заход.

## Принципы (без изменений)

Docs-first; шаг 6 композиционный; механика финализации — шаг 6, расчёт PnL —
шаг 7; числа провизорны/не выдумываются; инфраструктура от потребности.
Эскалации/дыры со штатным владельцем чинятся на уровне владельца (концепт →
`solution-designer`, риск → `trading-specialist`); A/B пользователю — только
остаточный хвост. Kill-switch как аварийный тормоз не зависит от исправности
петли (синхронный self-contained — природа CMD-Q6-исключения сохранена).

## Среда

**Тулчейн (найден и рабочий):** `mvn` — Maven 3.9.11 из wrapper-dist
(`~/.m2/wrapper/dists/apache-maven-3.9.11-bin/…/bin/mvn`); `JAVA_HOME` =
`~/.jdks/corretto-25`. Компиляция: `mvn -o -q -DskipTests compile`. Boot:
`SPRING_PROFILES_ACTIVE=test`, все джобы `*_ENABLED=false`, `VAULT_TOKEN` из
`.env.vault.test.local`; инфра в docker — postgres:16 (5440 prod / **5441
test**), vault:1.15 (8200). Прод автономно **не** поднимаем; test — demo,
`x-simulated-trading=1`.

## После коммита

Обновить PK (v61 заменяет v60).
