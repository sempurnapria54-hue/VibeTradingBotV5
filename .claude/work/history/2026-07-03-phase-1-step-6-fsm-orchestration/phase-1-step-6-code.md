# CODE — шаг 6 фазы 1 (FSM + живая оркестрация)

## На какой вопрос отвечает этот файл

На каком под-шаге мы в исполнении шага 6 фазы 1, что написано на `CODE` и
с каким зафиксированным исходом прошли адверсариальные ревьюер-фокусы.

## Контекст

- **Шаг:** 6 фазы 1 — «FSM + живая оркестрация».
- **Под-шаг:** `CODE` (написание `code-writer` + независимые ревью-фокусы
  `conventions`/`performance`/`disaster` + закрытие находок). Концепт-гейт
  `CODE` пройден чисто на `DOCS_CHECK_3`.
- **Сборка:** `mvn clean compile` на JDK 25 — **чисто**, без deprecation/
  warnings в нашем коде (`-Dmaven.compiler.showDeprecation/showWarnings`).
- **Финальный аппрув `CODE` и переход к `SYNC_DOCS_FROM_CODE` — за
  пользователем.**

## Что написано

Нижние слои (market-data/calc/risk/command-layer) уже были; шаг 6 сшил их
в живую петлю и достроил петлю/финализацию/оболочку.

### Финализационная под-спина (DEAL-Q1)
- `DealFinalizationState` (+ `DealFinalizationType`, `DealFinalizationStateStatus`)
  — persisted retry-state финализации (наследует `Retryable`); entity +
  repository + DataService + mapper; миграция `V9` (`deal_finalization_states`,
  `UNIQUE(deal_id, type)`).
- 4 финализационных executor'а: `FinalizeDealEntryExecutor`,
  `FinalizeDealExitExecutor`, `MarkDealClosedExecutor` (терминал `CLOSED`),
  `MarkDealErrorExecutor` (ребро `ERROR`).
- `ServiceCommand.dealFinalizationStateId`; `DealContext.finalizationStates`;
  `ServiceCommandFactory.finalizationCommand` (эмиссия по статусу +
  upsert PENDING); `ServiceCommandExecutor` — retry-anchor обобщён на
  `DealActionState` **и** `DealFinalizationState`.

### FSM + петля
- `DealStateMachine` + `FsmHandler` + `DealTransition`; 7 handler'ов
  (`PrecheckHandler`, `EntrySubmittedHandler`, `EntryFinalizedHandler`,
  `ManagingHandler`, `ProtectionSwitchedHandler`, `ExitPendingHandler`,
  `ErrorHandler`).
- Общая опора: `DealFsmSupport` (шаги по статусу, оценка condition,
  find-or-create action-state, системные/cleanup/finalization-команды,
  `reactToPlan`), `DealActionPlanner` (calc → risk → factory),
  `MarketConditionContextFactory` (сборка `ConditionEvaluationContext`).
- `DealContextService` (сборка `DealContext` с runtime graph + pinned
  `StrategyDetail` из дерева активной стратегии), `DealOpeningService`.
- `DealOrchestratorJob` (+ фасад) — оболочка джоба (CRON+`enabled`+async-
  фасад), execution boundary (unexpected → `ERROR`), concurrency-guard
  `OrchestratorPassLock` (Postgres advisory lock на весь проход — **D-M1**);
  `EntryScannerJob` (+ фасад). `JobController` — 2 новых триггера.
  Конфиг — `deal-orchestrator` / `entry-scanner` в обоих профилях.

### Жёсткие гейты `DONE`
- **D-M1 (concurrency-guard)** — реализован: `OrchestratorPassLock`
  (advisory lock на проход, lock/unlock на одном соединении; перекрытие
  таймер+ручной пропускается).
- **D-B3 (SUBMIT recovery-by-clientId)** — реализован в `SubmitOrderExecutor`
  / `SubmitAlgoOrderExecutor`: перед **повторным** submit (attemptCount>0)
  поиск по client id, найден → восстановить externalId, не плодить дубль.

### Прочее (закрытие пробелов `GAPS_CLOSE_1`)
- **N9/TR1** — `RiskValidator.checkRiskCreatingEntryProtection`
  (`RISK_CREATING_ENTRY_WITHOUT_STOP`): risk-creating вход без резолвимого
  стопа `BLOCKED` (fail-open снят); enforcement выхода в `EntryFinalizedHandler`
  (live-risk позиция в `MANAGING` только при подтверждённой защите).
- **set-leverage (INSTR-Q2)** — `IntegrationService.setLeverage` (+ OKX
  request/response DTO, `OkxRestClient`, `Constants.Okx.ACCOUNT_SET_LEVERAGE_PATH`);
  вызывается inline в `SubmitOrderExecutor` перед постановкой открывающего
  ордера (idempotent; reduce-only не трогается).
- **Error-политика (N1)** — внешняя поверхность (`GlobalExceptionHandler`
  `@RestControllerAdvice` + `ErrorApiResponse`) **и** внутренняя градация
  **уровни 1-2** (`RuntimeErrorCode` classify в `ServiceCommandExecutor` →
  retry/FAILED + execution boundary `DealOrchestratorJob` → `ERROR`). Уровни
  3-4 (реактивные холды) — обоснованный deferral D2 (см. сверку scope).

## Сверка scope `CODE` (built / deferred) — 2026-06-22

Поставленный `CODE` сверён построчно со scope шага 6 (роадмап-строка + граница
6↔7 + закрытия `GAPS_CLOSE_1` N1-N15 + жёсткие гейты). По каждому пункту —
built / deferred. Недостающее достроено либо вынесено обоснованным deferral'ом
(D1/D2 ниже), не молча.

| Пункт scope | Источник | Статус |
|---|---|---|
| FSM статусная механика (`DealStateMachine`/`FsmHandler`/`DealTransition` + 7 handler'ов) | роадмап | **built** |
| Живая петля `DealOrchestratorJob` (driving) + `EntryScannerJob` + фасады + конфиг + триггеры | роадмап | **built** |
| per-deal concurrency-guard **D-M1** (`OrchestratorPassLock`) | жёсткий гейт | **built** |
| Механика финализации: 4 executor'а + терминальные рёбра (`CLOSED`/`EMERGENCY_CLOSED`/`ERROR`) + retry-state `DealFinalizationState` (V9) | роадмап / N2-N4/DEAL-Q1 | **built** |
| **D-B3** SUBMIT recovery-by-clientId | жёсткий гейт | **built** |
| Защита бесстопового risk-creating входа (преконтроль `RiskValidator` + enforcement `EntryFinalizedHandler`) | N9/TR1 | **built** |
| set-leverage | N10 | **built** (сужение → §6a, ниже) |
| Авторитет `maxAttempts` = policy (`RetryPolicyService`) | N11 | **built** |
| Инструмент-скоупный read вне command-layer (Precheck-чистота `foreignLiveRisk`) | N12/CMD-Q4 | **built** |
| Терминальный контракт (`EMERGENCY_CLOSED`, `MarkDealClosed`/`MarkDealError`) | DEAL-Q2 | **built** |
| Error-политика — внешняя поверхность (`GlobalExceptionHandler` + `ErrorApiResponse`) | N1 | **built** |
| Error-политика — внутренняя градация **уровни 1-2** (`RuntimeErrorCode` + retry + execution boundary) | N1 | **built** |
| `KILL_SWITCH` executor (fire-all, защиту снимает последней) | action-orchestration-vs-command | **built** (эмиссия — deferred D2) |
| **REPLACE-leg-оркестрация** | роадмап / Хвост шага 4 | **deferred (D1)** |
| Error-политика — внутренняя градация **уровни 3-4** (instrument-hold / exchange-hold реактивный enforcement + `AnomalyReport`-реакция) | N1 | **deferred (D2)** |

### Достроено на сверке

- **Орфан `DealFsmSupport.killSwitchCommand()` удалён** (codestyle
  §Неиспользуемый код). Метод эмиссии `EXECUTE_KILL_SWITCH` не имел ни одного
  вызова — единственные штатные эмитенты (реакции уровней 3-4, D2) отложены,
  поэтому хелпер был преждевременной проводкой. Сам `KillSwitchExecutor`
  **остаётся** (материализация решения `action-orchestration-vs-command`;
  диспатчится через реестр по типу команды; его эмиссию подключит отложенная
  hold-подсистема). Конвенц-фокус эту орфан-находку пропустил — снято здесь.
  Удаление — zero-caller delete (грепом подтверждено отсутствие вызовов), прежнее
  clean-compile-состояние сохранено; в этой среде `mvn`/`java` недоступны, прогон
  сборки не выполнялся.

### Обоснованные deferral'ы (зафиксированы)

**D1 — REPLACE-leg-оркестрация (PROTECTION_ADJUSTMENT).** Фабрика REPLACE-ног
возвращает `Optional.empty()` (`ServiceCommandFactory` `case CANCEL, REPLACE`),
`ManagingHandler` остаётся в `MANAGING`. **Почему форвард:** полный факт-driven
секвенс ног (place-new → подтверждённый факт → cancel-old для protective;
cancel-old → факт → place-new для entry; резолюция цели по цепочке
`replacesInternalId`) — самостоятельный refinement оркестрации. Концепция
владельца закрыта на `GAPS_CLOSE_1` (`action-orchestration-vs-command.md`,
CMD-Q5/Q6: секвенс ведёт петля/`DealStateMachine`, фабрика — «одна команда за
проход»); реализация объёмна и для базовой петли фазы 1 не требуется. **Не
гейтит `DONE` шага 6** — петля включается без REPLACE-секвенса. **Трек:**
`backlog.md` §Хвост шага 4 (запись REPLACE, помечена re-deferred за `CODE`
шага 6); владелец — `CODE`-доводка command-слоя.

**D2 — Error-градация уровни 3-4 (реактивные холды + `AnomalyReport`-реакция).**
Внешняя поверхность и уровни 1-2 построены. Уровни 3-4 — **реактивный
enforcement холдов** (серия неудач / нарушение риск-политики → `Instrument.HOLD`;
нарушение контракта/инварианта → `Exchange.HOLD`; реакция = холд + kill-switch +
`AnomalyReport`) — **не построены**. **Почему обоснованный форвард, а не дострой
сейчас:**

- **`AnomalyReport`-реакция** (журнал инцидента + severity-политика, на которой
  висит ручное снятие холда) операционализируется `AnomalyJob` — **шаг 8**
  (`HOLD`); Java-модели `AnomalyReport` в коде ещё нет (только доковый каркас).
- **Точка enforcement холдов** = lifecycle/координация статусов `Instrument.HOLD`
  / `Exchange.HOLD` — **backlog п.9** (`exchange-hold.md`/`instrument-hold.md`
  сами это фиксируют: «Полная модель координации статусов инструмента — backlog
  п.9», «Модель/lifecycle самого `Exchange` … не создаётся»).
- **Порог «серия неудач подряд»** — провизорное число (паттерн STRUCT-Q1), «не
  выдумывается»; до калибровки бэктестом триггер уровня 3 численно не определён.

Построить уровни 3-4 в шаге 6 = втянуть scope шага 8 (`AnomalyReport` ops) и
backlog п.9 (status-lifecycle) преждевременно, половинной реализацией (холд без
журнала, с выдуманным порогом), отдельной от их владельцев. **Что из реакции
уже есть:** `KILL_SWITCH` executor (компонент реакции); преконтроль бесстопового
входа (N9/TR1 — превентивная грань триггера уровня 3); `ControlledExchangeException`
классифицируется терминалом (не ретраится) и уводит сделку в `ERROR` →
safety-recovery (`ErrorHandler` риск-минимизирующим порядком) — частичная грань
уровня 4 на per-deal контуре. Недостаёт именно **широкого** холда (стоп торговли
по инструменту/бирже) + журнала + автотриггера. **Владельцы:** шаг 8 (AnomalyJob /
`AnomalyReport` ops; полный kill-switch flow — `backlog.md` §7), backlog п.9
(status-lifecycle). **Трек:** `backlog.md` §Шаг 6 (новая запись). **Не гейтит
`DONE` шага 6** (жёсткие гейты шага — D-B3/D-M1, оба built).

### set-leverage — намеренное сужение, §6a-инкремент (подтверждено)

Spec (`GAPS_CLOSE_1` N10): set-leverage перед ордером в `PRECHECK`. Поставлено:
inline в `SubmitOrderExecutor.ensureLeverage` перед постановкой, **только для
открывающих (не reduce-only) ордеров**, idempotent. Сужение «каждый ордер →
открывающий» — **намеренно и корректно**: плечо релевантно только
позиция-открывающим ордерам, для reduce-only бессмысленно. Место (submit-executor,
не handler) co-locates set-leverage с place-вызовом — атомарно, непропускаемо,
покрывает и наращивание в `MANAGING`. Доки тайминг set-leverage **сами отнесли к
шагу 6 как форвард-решение** (`Instrument.md:140`, `mapping/Instrument.md:52` —
«остаток INSTR-Q2 — только тайминг set-leverage, форвард к шагу 6»), поэтому
as-built = и есть это решение → фиксируется **§6a-инкрементом** (уже в списке §6a
ниже: «set-leverage представление»). Выравнивание к буквальному «каждый ордер» не
требуется.

## Реактивные холды L3/L4 — CODE-делта (D2-реактивный снят) — 2026-06-23

Достроен **реактивный** контур CRITICAL-холда по
`.claude/work/progress/phase-1-step-6-holds-design.md` (аппрув дизайна + развилки
B/C/§8.C даны пользователем). Снимает прежний deferral **D2 в части реактивного
enforcement** и инертность kill-switch (орфан-эмитент подключён). Проактивная
детекция (`AnomalyJob`/`TradeRuleValidator`, численные пороги) остаётся **шаг 8**;
ops ручного un-hold — **backlog п.9 / шаг 9**.

### Решения по развилкам (входы)
- **B (статус-модель):** новый `Status.TRADE_BLOCKED` у `Instrument` **и**
  `Exchange`. Заморозка по аварии — **только из `ACTIVE`** (гардированный
  statusный `updateStatus`, `int rows`); онбординг-`Instrument.Status.HOLD` на
  safety не переиспользуется. Обратный переход (ручная разморозка
  `TRADE_BLOCKED → ACTIVE`) — с un-hold-операцией (не вводим превентивно: метод
  без вызова = codestyle-нарушение).
- **C (статус разбора):** не вводим (c1). `AnomalyReport` — авто-журнал без
  `RESOLVED`/`resolvedBy/At`.
- **§8.C:** бесстоповая позиция постфактум = **L3** (инструмент); controlled-violation
  (биржевой контракт/инвариант) = **L4** (биржа).

### Что построено
- `domain.safety`: `HoldScope` (INSTRUMENT/EXCHANGE, общий для сигнала и журнала),
  `HoldSignal{scope, code}` (VO), `SafetyHoldCoordinator`, `KillSwitchService`,
  `AnomalyReport` (+ `Status`/`Severity`), `AnomalyReportService`.
- `DealTransition.holdSignal` (опц.); handler уводит свою сделку в `ERROR` (команда
  `MARK_DEAL_ERROR`) **и** несёт сигнал.
- **Триггеры в handler'ах** (`DealFsmSupport`): `markError` авто-поднимает **L4** при
  controlled-violation в retry-anchor'ах сделки (`VALIDATION_ERROR` ⟺
  `ControlledExchangeException` ⟺ «Exchange→HOLD», `controlled-exchange-exceptions.md`);
  новый `markErrorStopless` поднимает **L3** (бесстоповая позиция постфактум,
  `RISK_CREATING_ENTRY_WITHOUT_STOP`) на двух точках «live risk без резолвимой защиты»
  (`EntryFinalizedHandler.toManagingIfProtected`, `ProtectionSwitchedHandler`);
  controlled-violation доминирует над L3 (→ L4).
- **Координация в проходе** (`DealOrchestratorJob.processDeal`, под D-M1): по
  `transition.holdSignal` → `SafetyHoldCoordinator.react` после `applyTransition`.
  Последовательность (L3/L4 одной формы): `TRADE_BLOCKED` scope **первым** (gate +
  анкер идемпотентности; ставится только из `ACTIVE` → повтор/не-ACTIVE
  пропускается) → `AnomalyReport` CREATED (before-слепок) → IN_PROGRESS → kill-switch
  (scope) → KILL_SWITCH_EXECUTED → after-слепок → COMPLETED; сбой обработки →
  `AnomalyReport` ERROR, проход живёт.
- **Kill-switch скоуп** (`KillSwitchService`, эмитент `EXECUTE_KILL_SWITCH`): L3 — по
  графу триггерной сделки (доминирующая позиция инструмента покрыта финальным
  безусловным close в `KillSwitchExecutor`); L4 — каскадный sweep по всем активным
  сделкам биржи (`DealDataService.findActiveByExchangeId`), команда на сделку,
  best-effort per-deal.
- **Enforcement.** Новые сделки: `EntryScannerJob` фильтрует TRADE_BLOCKED-инструменты
  (исключены выборкой `ACTIVE`) **и** инструменты TRADE_BLOCKED-биржи (каскад L4 —
  сканер теперь читает статус биржи, `ExchangeDataService.findIdsByStatus`). Активные
  сделки: `DealOrchestratorJob.enforceHold` уводит held-scope сделку (не-ERROR) в
  `ERROR` со `shutdownReason` (EXCHANGE_HOLD / RISK_POLICY) — normal-flow не
  запускается, teardown доводит `ErrorHandler`; L4 ручной un-hold одним действием
  отпускает каскад (enforcement читает статус биржи живьём; per-instrument холды для
  L4 не пишутся).
- **AnomalyReport стек:** domain + entity (4 jsonb-слепка) + repository + DataService +
  MapStruct mapper + миграция `V10` (`anomaly_reports`, uk_internal_id). Явный `scope`
  (INSTRUMENT/EXCHANGE) вместо вывода из `instrument_id=null`.
- TRADE_BLOCKED-enum миграции **не требуют** (статус-колонки `varchar(32)` без
  CHECK-constraint).

### Доработка холд-дельты (2026-06-24): два сужения сняты + ревью

Два сужения дельты, сужавшие утверждённый дизайн, **сняты** (решение в чате):

- **A. Внешние (биржевые) слепки `external_before/after` — теперь собираются.**
  `AnomalyReportService.externalSnapshot` читает реальное биржевое состояние по
  instId триггера (`getPosition` + `getPendingOrders` через `IntegrationService`)
  в обе точки: `externalBefore` при CREATED (`open`), `externalAfter` перед
  COMPLETED (`complete`, читается **после** kill-switch → остаточный риск виден).
  Чтение биржи best-effort (`readExchange` ловит `RuntimeException` → маркер
  `readError`, не валит реакцию). Схема **открытая/аддитивная** (не финальная):
  PnL — шаг 7; биржа-широкая реконсиляция и orphan/algo по всей бирже (L4) —
  проактивный `AnomalyJob` шаг 8.
- **B. Ручной un-hold `TRADE_BLOCKED → ACTIVE` — построен через REST.**
  `unblockTrade` (гардированный обратный `updateStatus`, только из TRADE_BLOCKED) в
  `Instrument/ExchangeDataService` + `Instrument/ExchangeService` +
  `POST /api/{instruments|exchanges}/{internalId}/trade-unblock`. Не в TRADE_BLOCKED →
  `IllegalStateException` → 409. **L4: одно снятие биржи отпускает весь каскад**
  (enforcement читает статус биржи живьём); per-instrument un-hold для L4 не вводится
  (собственные L3-холды инструментов снимаются отдельно). Аудит «кем/когда» —
  по-прежнему форвард (шаг 9 / backlog п.9), не вводится превентивно.

**Хардненинг по ревью (MAJOR, correctness-фокус):** `SafetyHoldCoordinator.runReaction`
сделан **exception-total** и расцеплён с журналом — `open/advance/complete/fail`
обёрнуты best-effort (`openSafely`/…); сбой записи журнала (включая `open`, который
раньше был **вне** try) больше не подавляет kill-switch и не выходит наружу. Снятие
риска приоритетнее журнала.

**Открытая находка на валидацию — `open-questions.md` HOLD-Q1** (не закрыта): код
поднимает L4-холд+flatten всей биржи на **любой** `ControlledExchangeException`
(`VALIDATION_ERROR`) одной сделки, доминируя над L3, и теряет квалификатор офдока
«по severity / safetyImpact» для `ExternalStatusException`. Поведение в коде **не
менялось** — на решение владельца риск-семантики.

### Остаточные форварды (не дострой)
- **After-слепок (локальный, БД)** по-прежнему строится из in-memory `DealContext`
  (без REFRESH_*-re-read после kill-switch) → приближённый. Точный after через
  REFRESH_*-подтверждение — шаг 8. (Внешний after уже читается живьём с биржи — A.)
- **Внешний слепок для L4** читает только instId триггера (как и локальный) —
  биржа-широкая реконсиляция всех инструментов биржи — проактивный шаг 8.
- **Проактивный L4-путь** (паттерн «много бесстоповых / обойдён гард» = control-plane
  failure) — шаг 8; реактивный контур поднимает L3 на одну обнаруженную позицию.
- **Перф-форвард (ревью):** L4 `KillSwitchService.fireExchange` — небанженный
  `findActiveByExchangeId` × per-deal `DealContext` rebuild + kill-switch REST под
  advisory-локом D-M1 (O(сделок) burst). Усечение LIMIT'ом **небезопасно** (несвёрнутый
  live risk); фикс — пагинация-петля (бандженные запросы, полное покрытие) либо
  off-lock dispatch — на решение владельца. Трек: `backlog.md`.

### Сборка
`mvn`/`java` в среде недоступны — прогон сборки не выполнялся; проведена ручная
кросс-сверка символов (getters/enum/сигнатуры) по кодовой базе. Прогон
`mvn clean compile` + ревью-фокусы (`conventions`/`performance`/`disaster`) — за
пользователем.

## Ревью-фокусы (зафиксированный исход)

Три независимых субагента (не автор) + независимая верификация фиксов.

### `conventions`
0 blocker / 2 major / 9 minor — **все закрыты**. Major: `!= null` →
`nonNull` (`ErrorHandler`); `Objects.isNull` class-prefix → статический
импорт (`DealFsmSupport`). Minor: `.toList()` → `Collectors.toList()`
(×4), `isFalse(isEmpty/isNotEmpty/isTrue(...))` → прямые предикаты
(`isNotEmpty`/`isEmpty`/`isNotTrue`, ×8), unused imports (×2), pattern-
matching `instanceof` (×2).

### `performance`
0 blocker / 2 major / 3 minor. **Закрыто:** M1 — индексы `deals`
(`ix_deal_status`, `ix_deal_instrument_status`) в `V9` (единственная
находка, деградирующая с ростом). **Форвард (осознанно, фаза 1):** M2
(повторная загрузка настроек стратегии — плоская стоимость; чистый фикс
размывает семантику `DealContext`), M3 (N+1 индикаторов/структур —
индексирован, малая кардинальность), M4 (синхронный per-deal REST ticker
под advisory lock — приемлемо для фазы 1), M5 (`instruments.findByStatus`
без окна — курируемый малый набор). → `.claude/work/backlog.md`.

### `disaster`
2 blocker / 4 major / 3 minor. **Blocker — закрыты:**
- **B1** RETRY_PENDING action-команды зависали (фабрика возвращала empty) —
  `DealActionPlanner` re-arm: RETRY_PENDING → производная стадия
  (target null → PLANNED/CREATE; target есть → CREATED/SUBMIT, повтор
  идемпотентен через D-B3).
- **B2** `nextRetryAt` не соблюдался (ретрай каждый тик) — gate по
  `nextRetryAt` в `DealActionPlanner.retryDue` и
  `ServiceCommandFactory.retryDue` (финализация).

**Major — закрыты:**
- **M3** финализация FAILED крутилась вечно — `finalizationCommand` для
  FAILED отдаёт empty; `ExitPendingHandler` ловит FAILED FINALIZE_EXIT/
  MARK_CLOSED → `markError` (DEAL-Q2: сделка всегда доходит до терминала).
- **M4** терминальный гейт `MarkDealClosed` проверял только позицию —
  добавлен `hasLiveEntities` (live orders/algo).
- **M5** «одна сделка на инструмент» без БД-инварианта — частичный
  unique-index `uk_deal_active_instrument` (`V9`); гонка вставки —
  benign skip (ловится в try/catch `EntryScannerJob`).
- **M6 (форвард)** биржевой REST внутри `@Transactional` submit-executor'а
  (расширяет принятый на шаге 4 паттерн; проходы сериализованы advisory
  lock'ом — давление на пул ограничено в фазе 1; чистый фикс — расщепление
  tx по executor'ам). → `.claude/work/backlog.md`.

**Minor (форвард/принято):** advisory-lock держит 1 соединение на проход;
orchestrator не перезагружает `DealContext` после команд (benign: handler
возвращает команду XOR переход); `findActive` окно id-ASC (старвейшн при
> batchSize — фаза 1 не триггерит). Независимая верификация 5 фиксов —
все **CONFIRMED**.

## Концепт-инкременты на `CODE` (для пост-хок гейта §6a / SYNC)

Требуют `concept-review` по пост-sync докам перед `DONE`:
- **MarkDealClosed placeholder-прибыль** (ZERO + settle currency) —
  механически удовлетворяет инвариант чистого терминала; реальный PnL —
  шаг 7 (заменит).
- **Retry re-arm** RETRY_PENDING → производная стадия (механика повтора
  action-команд поверх `RetryPolicyService`).
- **Частичный unique-index** `uk_deal_active_instrument` (инвариант «одна
  незакрытая сделка на инструмент» на уровне БД).
- **set-leverage представление** — inline-write в submit-executor'е
  (а не отдельная команда), для открывающих ордеров.
- **D-B3 представление** — recovery-by-clientId перед повторным submit.

## Форвард / на разбор

- **tech-radar:** `OrchestratorPassLock` raw-JDBC (`DataSource`/`Connection`)
  ради same-connection advisory lock — **зафиксирован** на заходе 1 разбора
  находок (2026-06-30): запись в `tech-radar` (`adopt`, ограничено назначением,
  несущий с фазы 3; raw-JDBC только ради advisory-замка на одном соединении).
- **REPLACE-оркестрация ног** (PROTECTION_ADJUSTMENT) — **обоснованный deferral
  D1** (см. §Сверка scope): фабрика REPLACE-ног возвращает empty, handler
  остаётся в `MANAGING`. Полный секвенс — форвард-refinement, `backlog.md`
  §Хвост шага 4.
- **Error-градация уровни 3-4 (реактивные холды + `AnomalyReport`)** —
  deferral D2 был **ошибочным в части реактивного контура**: реактивный
  CRITICAL-холд L3/L4 построяем на шаге 6 на уже описанном. Дизайн проработан
  отдельным отчётом `.claude/work/progress/phase-1-step-6-holds-design.md`
  (локус-координатор, последовательность L3/L4 с встроенным kill-switch,
  enforcement+каскад, сверка `AnomalyReport`, развилки на валидацию). В шаг 8
  остаётся только **проактивный** детектор (`AnomalyJob`/`TradeRuleValidator`)
  и численные пороги. Аппрув дизайна и `CODE`-инкремент холдов — за
  пользователем.
- Перф-форварды M2-M5, disaster-форвард M6, noisy ERROR-лог на benign
  гонке вставки сделки — `.claude/work/backlog.md`.
- Численные провизорности (частоты джоб, batch-size, порог серии неудач
  уровня 3) — бэктест/наблюдения.

## Заход 1 разбора находок ревью (2026-06-30)

Первый из двух заходов разбора находок ревью холд-дельты. Здесь — правки кода
(№2, №3) и фиксации решений (№1, №4, HOLD-Q1) поверх холд-дельты. **Заход 2**
(финальный аппрув `CODE` + `SYNC_DOCS_FROM_CODE` + выравнивание **продуктовых**
доков под изменившийся код) — за пользователем.

### №2 — сборка `DealContext` открытой сделки не зависит от живой стратегии (код)
**Дефект подтверждён.** `DealContextService.resolvePinnedDetail` перерезолвил
pinned `StrategyDetail` через `findActiveByInstrumentIdWithTree` (живое дерево
**активной** стратегии) → бросал `IllegalStateException` при
`Strategy.INACTIVE`/`DELETED`. Падало бы **и сопровождение** INACTIVE-сделок, и
аварийное закрытие (kill-switch строит `DealContext`) — проблема общая, не про
kill-switch. **Выправлена сборка** (не точечный обход): pinned detail тянется по
`deal.strategyDetailId` со своим поддеревом, **без привязки к статусу**
родительской стратегии — новый `StrategyDetailRepository.findByIdWithTree` +
`StrategyDataService.getRequiredDetailByIdWithTree`. `DealContext.strategyDetail`
эквивалентен (старый путь и так брал только одну pinned-деталь, strategy-scope
настройки отбрасывал), но работает при INACTIVE/DELETED и тянет меньше данных.
`findActiveByInstrumentIdWithTree` остаётся входом entry-скана.

### №3 — терминал `AnomalyReport` гейтится фактом закрытия (код)
Раньше результат kill-switch выбрасывался → отчёт уходил в COMPLETED независимо
от того, закрылось ли. Теперь **узкий гейт на доступных данных** (отчёт самого
kill-switch): `KillSwitchExecutor` возвращает `success` = подтверждение закрытия
доминирующего риска (close live-risk позиции дал успешный ACK; нет live-risk
позиции → закрывать нечего → подтверждено); `KillSwitchService.fireInstrument/
fireExchange` пробрасывают подтверждение (L4 — `true` только если подтверждено по
**каждой** сделке каскада); `SafetyHoldCoordinator.completeIfClosed` доводит до
COMPLETED **только при подтверждении**, иначе отчёт остаётся открытым
(KILL_SWITCH_EXECUTED). Отмена ordinary/algo-ордеров и финальный безусловный
close — best-effort, в подтверждение не входят. **Форвард (шаг 8, ANOM-Q2):**
ретрай-до-закрытия + сверка реального состояния биржи (поймать sneak-позицию,
которую финальный безусловный close скрыл) — `backlog.md` §Шаг 8.

### №1 — перф-форвард L4 `fireExchange` (фаза 3) — фиксация
Существующая backlog-запись обогащена: порог актуальности — **фаза 3** (реальный
объём одновременных сделок); починка = перебор пачками (bounded, полный свип
сохранён) — режется аппетит (память/стоимость запроса), не скорость; `LIMIT`
небезопасен. Поведение **не менялось**.

### №4 — tech-radar: advisory-замок через raw-JDBC (фаза 3) — фиксация
Заведена запись в `tech-radar`: raw-JDBC (`DataSource`/`Connection`) допустим
**только** ради advisory-замка на одном соединении (`OrchestratorPassLock`), не
для обычного доступа к данным; несущий с фазы 3 (в фазе 1 хватило бы
in-process-замка, advisory уже готов и оставлен).

### HOLD-Q1 — закрыт решением (1) + принцип
Закрыт решением `docs/decisions/controlled-violation-exchange-wide-hold.md`:
controlled-violation = безусловный L4 (доминирует L3); квалификатор офдока «по
severity/safetyImpact» снят. Поведение кода **не менялось** (уже (1)).
Зафиксирован **переиспользуемый принцип** (консервативное широкое торможение под
неизвестный радиус незрелой интеграции направляет будущие развилки того же
класса). Выравнивание текста `controlled-exchange-exceptions.md` под (1) — на
`SYNC`. Удалён из `open-questions.md`.

### Сборка
`mvn`/`java` в среде недоступны — прогон сборки не выполнялся; проведена ручная
кросс-сверка символов/сигнатур (новый репозиторий/метод, overload
`persistenceToDomain(StrategyDetailEntity)`, `ExchangeAck.getSuccess`,
`Supplier<Boolean>`) по кодовой базе. `mvn clean compile` + ревью-фокусы — за
пользователем (заход 2).

## Следующий шаг

Scope `CODE` сверён на полноту (§Сверка scope): весь scope — built, кроме
двух обоснованных deferral'ов (D1 REPLACE-leg, D2 уровни 3-4 холдов), оба
зафиксированы с владельцами и треком; одна орфан-находка (kill-switch эмиссия)
снята. Финальный аппрув `CODE` (за пользователем) → `SYNC_DOCS_FROM_CODE`
(`divergence` docs←code) → пост-хок концепт-гейт §6a по концепт-инкрементам
выше → `DONE`. Жёсткие гейты `DONE` (D-B3 / D-M1) — оба built.
