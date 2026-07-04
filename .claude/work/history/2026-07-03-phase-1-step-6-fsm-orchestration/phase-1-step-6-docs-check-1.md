# DOCS_CHECK_1 — шаг 6 фазы 1 (FSM + живая оркестрация)

## На какой вопрос отвечает этот файл

На каком под-шаге мы в исполнении шага 6 фазы 1 и какие пробелы концепции
нашёл первый прогон сквозной проверки (`concept-review` + `trading-review`).

## Контекст прогона

- **Шаг:** 6 фазы 1 — «FSM + живая оркестрация» (после уточнения границы
  6 ↔ 7 от 2026-06-21): помимо статусной механики (состояния и переходы
  сущностей) и конструкции handler'ов — **живая оркестрационная петля**
  (`DealOrchestratorJob` driving), **REPLACE-оркестрация**, **per-deal
  concurrency-guard** (D-M1) и **механика финализации** (финализационные
  executor'ы, терминальные рёбра, retry-state финализации).
- **Под-шаг:** `DOCS_CHECK_1` (первая итерация). `TOOLING` пройден **без
  новых артефактов** — фокусы `concept-review` / `trading-review` активны
  (реестр `reviewer`); новых агентов/скиллов под оркестрацию не
  потребовалось.
- **Что шаг должен делать функционально:** включить **живой торговый
  цикл сопровождения сделки**. `DealOrchestratorJob` гоняет активные
  `Deal` через `DealStateMachine` + per-status handler'ы; handler'ы по
  фактам выбирают `StrategyAction`, считают (calc-слой шага 5), валидируют
  (risk-слой шага 5), порождают `ServiceCommand` (фабрика шага 4) и
  исполняют executor'ами (шаг 4); REPLACE-ремодел и kill-switch
  оркеструются по фактам; сделка **финализируется** (терминальные рёбра
  `CLOSED`/`EMERGENCY_CLOSED`). Шаг **композиционный**: нижние слои
  (market-data, calc, risk, command-layer) уже материализованы шагами
  1-5; шаг 6 их **сшивает в работающую петлю** и достраивает то, что без
  петли было мёртвым кодом (REPLACE-оркестрация, финализация,
  concurrency-guard).
- **Особенность:** статусная механика и конструкция handler'ов **в
  основном уже материализованы** миграцией из архива — процесс
  `deal-management`, `DealStateMachine`, 7 handler'ов, lifecycle `Deal` /
  `DealActionState`, command-layer, правила (`command-lifecycle`,
  `runtime-error-classification`, `controlled-exchange-exceptions`,
  `ack-not-runtime-truth`). Первый прогон подтверждает зрелый **статусный
  костяк** и вычленяет то, что **гейтит `CODE`**: пробелы сосредоточены на
  **петле, финализации и операционной оболочке оркестратора** — на том,
  что петля «включает».

## Охват

### Проверены (в охвате шага 6)

- **Процессы:** `docs/processes/deal-management.md`.
- **Lifecycles:** `Deal.md`, `DealActionState.md`, `Order.md`,
  `AlgoOrder.md`, `Position.md`, `Strategy.md` (enforcement).
- **Компоненты (оркестрация + FSM):** `DealOrchestratorJob`,
  `DealStateMachine`, `DealContextService`, `DealOpeningService`,
  `EntryScannerJob`, `StrategyConditionEvaluator`; 7 handler'ов
  (`PrecheckHandler`, `EntrySubmittedHandler`, `EntryFinalizedHandler`,
  `ManagingHandler`, `ProtectionSwitchedHandler`, `ExitPendingHandler`,
  `ErrorHandler`).
- **Командный слой:** `ServiceCommandExecutor`, `ServiceCommandFactory`,
  `RetryPolicyService`; 13 executor'ов (Create/Submit/Cancel/Close/Refresh
  + `KillSwitchExecutor`); 4 резолвера; component-models `ServiceCommand`,
  `ServiceCommandPayload`, `PositionStatusResolveResult`, `DealContext`.
- **Доменные модели:** `DealActionState` (+ `RuntimeTarget` / `Retryable`),
  `Deal`, `Order`, `AlgoOrder`, `Position`.
- **Правила:** `command-lifecycle`, `runtime-error-classification`,
  `controlled-exchange-exceptions`, `ack-not-runtime-truth`,
  `audit-not-runtime-source`, `external-status-resolution`,
  `idempotency-via-unique`, `exchange-hold`, `trading-constraints`,
  `no-partial-close`, `risk-validator-scope`.
- **Решения:** `replace-not-amend`, `deal-action-state-materialization`,
  `service-command-payload-base-type`, `refresh-evidence-cycle-ownership`.
- **Конвенции:** `.claude/rules/codestyle.md` §«Обработка ошибок — TBD
  (владелец — шаг 6)», §«Джобы».
- **Open-questions:** проход по `open-questions.md` (DEAL-Q1, DEAL-Q2,
  CMD-Q4, CMD-Q5, CMD-Q6, INSTR-Q2-остаток; смежно OKX-Q1/Q3, ORCH-Q1).

### Вне охвата (помечены, не проверялись по существу)

- **Сделки и P&L (шаг 7):** расчёт `Deal.resultProfit` / breakdown PnL,
  агрегация `Deal`, DEAL-Q2, `TradeFill`/bills (OKX-Q1/Q2/Q3). **Граница
  6 ↔ 7:** *механика* финализации (executor'ы, терминальные рёбра,
  retry-state — DEAL-Q1) — шаг 6; *расчёт прибыли* — шаг 7.
- **AnomalyJob / ReconciliationJob (шаг 8):** полный orphan/чужой-live-risk
  скан, after-terminal live risk; шаг 6 трогает только границу (кто владеет
  live risk после terminal) и Precheck-cleanliness (CMD-Q4 вход).
- **Безопасность (шаг 9):** auth-инфраструктура, фокус `security-review`
  деактивирован.
- **Нижние слои (шаги 1/3/5):** market-data, calc-слой, risk-слой —
  точки композиции, по существу не реревьюятся (закрыты своими шагами).

## Стадия остановки

Формальный стоп — **стадия 0** (гейтящие технические/скоуп-вопросы **не
чисты**). Чтобы дать `GAPS_CLOSE_1` полную картину, обход всё же
прогнан до **стадии 2** оппортунистически (статусный костяк
order/algo/position независим от дыр финализации); находки стадий 1-2
зафиксированы, но стадия-2 финализации (N3/N4) **контингентна** закрытию
стадии-0 (N2/DEAL-Q1).

- **Стадия 0 (гейтящие механика/скоуп) — НЕ чисто.** Всплыли пять
  гейтов самой механики петли: **N1** (error-политика — codestyle-объявленный
  гейт `CODE` шага 6), **N2** (DEAL-Q1 — у retry-state финализации нет
  дома), **N5** (CMD-Q5 — у REPLACE-оркестрации нет компонента-владельца),
  **N6** (CMD-Q6 — не сформулирован принцип «действие-оркестрация vs
  команда-с-шагами», классификация `KILL_SWITCH`), **N10** (INSTR-Q2-остаток —
  тайминг/владелец set-leverage). Без них неясно, *что* за петлю/финализацию
  описываем.
- **Стадия 1 (процессы/lifecycles) — чисто по содержанию**, две
  гигиенические битые ссылки (N13). `deal-management`, lifecycle `Deal` /
  `DealActionState`, матрица переходов, recovery-по-фактам — целостны и
  согласованы.
- **Стадия 2 (компоненты + модели) — найдены пробелы**, сосредоточены на
  петле/финализации/ретрае: N3, N4, N7, N8, N9, N11, N12 (+ гигиена
  N14/N15).

## Пробелы по типам

### Неотвеченные вопросы / нерешённая политика (стадия 0, гейтят механику)

- **N1 — error-политика не зафиксирована; codestyle-объявленный гейт
  `CODE` шага 6. Гейтит `CODE` (блокер).** `.claude/rules/codestyle.md`
  §«Обработка ошибок — TBD (владелец — шаг 6)» прямо держит единую
  error-политику (коды; `@ControllerAdvice` vs per-endpoint;
  документирование ошибок на контроллере `@ApiResponses`) **за шагом 6** и
  пишет, что она «проектируется docs-first на шаге 6 и **гейтит его
  `CODE`**». FSM-**внутренний** runtime-путь специфицирован
  (`runtime-error-classification`, `controlled-exchange-exceptions`,
  `ErrorHandler`, execution boundary `DealOrchestratorJob.md:21-22`), но
  **API/контроллерная поверхность** (как ошибки оркестрации и ручного
  триггера джобы ложатся на HTTP-коды/ответы) не задана. Неблокирующие
  майоры шагов 2/4 (500 вместо 422/409, невыровненные коды реджектов)
  ретро-висят на ней. → Э2.

- **N2 — у retry-state финализации нет persisted-дома (DEAL-Q1). Гейтит
  `CODE` (блокер) + латентная doc↔OQ-несогласованность.** Финализация —
  теперь скоуп шага 6; финализационные команды (`FINALIZE_DEAL_*`,
  `MARK_DEAL_*`) могут падать и обязаны ретраиться. Единственный носитель
  persisted-retry — база `Retryable`, наследуемая **только**
  `DealActionState`, а `DealActionState` жёстко привязан к `StrategyAction`
  (`strategyActionId` обязателен; инвариант `UNIQUE(deal_id,
  strategy_action_id)`). Финализация — **lifecycle/system action без
  `StrategyAction`** → строку `DealActionState` под неё создать нечем.
  DEAL-Q1 фиксирует ровно это и открыт (`open-questions.md:98-106`).
  Усугубление: `Deal.md` §Итоговый PnL утверждает «временно нельзя
  посчитать — финализация retry-ится **по общей retry-policy**. (Поведение
  при исчерпании retry — открытый вопрос)» (`Deal.md:95-98`) — то есть
  предполагает тот самый `DealActionState`-дом, которого для финализации
  нет. → Э1.

- **N5 — у REPLACE-оркестрации нет компонента-владельца (CMD-Q5). Гейтит
  `CODE` (блокер).** Правило порядка ног по риск-классу (protective:
  place-new → подтверждение фактом → cancel-old; entry: cancel-old →
  подтверждение терминала → place-new) зафиксировано в `replace-not-amend`,
  но **где живёт его оркестрация** (фабрика vs петля) явно открыто и
  запарковано «на 6-7» (`open-questions.md:206-226`). Петля — теперь шаг 6.
  Верифицировано: `ServiceCommandFactory` `CANCEL`/`REPLACE` **не
  порождает** (`ServiceCommandFactory.md:34-36`), таблица перехода покрывает
  только `CREATE_*→SUBMIT_*→REFRESH_*`/`CLOSE` (`:25-30`); `DealStateMachine`
  / `ManagingHandler` говорят «REPLACE-оркестрацией из этого же набора», но
  компонент, **вычисляющий следующую ногу по фактам**, не назван. Это
  центральное, что петля должна сшить, и у него нет владельца. → Э3.

- **N6 — принцип «действие-оркестрация vs команда-с-внутренними-шагами»
  не сформулирован; классификация `KILL_SWITCH` (CMD-Q6). Гейтит `CODE`
  (майор).** REPLACE смоделирован как действие стратегии (оркестрация
  атомарных команд по фактам), `EXECUTE_KILL_SWITCH` — как **одна команда**
  с внутренним teardown'ом (close → cancel orders → cancel algos →
  безусловный финальный close), хотя сам компаунд над атомарными
  операциями. Принцип границы запаркован «на 6-7» (`open-questions.md:228-246`).
  Петля строит оба; без принципа неясно, как петля классифицирует компаунды
  (тот же вопрос повторится для будущих). Майор, не блокер: текущая модель
  `KillSwitchExecutor` сама по себе достаточно специфицирована, чтобы
  кодиться. → Э3.

- **N10 — тайминг/владелец set-leverage не задан (INSTR-Q2-остаток).
  Гейтит `CODE` пути выставления плеча (майор, узкий скоуп).** Остаток
  INSTR-Q2 (кто и когда пишет рабочее плечо на биржу: онбординг / перед
  сделкой / на каждую сделку) явно форвард к **шагу 6** (оркестрация,
  `open-questions.md:119-137`; `backlog.md:296-300` В-9). Ни один док
  не назначает владельца write-действия: `PrecheckHandler` set-leverage не
  упоминает, executor'а/команды под него нет. При **динамическом** рабочем
  плече (`per-trade-risk-policy`) без записи плеча до постановки ордер уйдёт
  на бирже со стейл-плечом. → Э6.

### Name-level без структуры (нужна структура; стадия 2)

- **N3 — финализационные executor'ы только name-level. Гейтит `CODE`
  (блокер; контингентно N2).** `FINALIZE_DEAL_ENTRY`, `FINALIZE_DEAL_EXIT`,
  `MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR` — живые члены `ServiceCommandType`
  (`ServiceCommand.md:37-41`) и эмитятся handler'ами, но **executor-дока,
  payload'а и поведенческой семантики нет ни у одного**:
  `ServiceCommandExecutor` определяет семантику только групп
  CREATE/SUBMIT/CANCEL/REFRESH (финализационной группы нет); файлов
  `Finalize*Executor.md` / `MarkDeal*Executor.md` в `docs/components/` нет
  (executor-док есть у каждого CREATE/SUBMIT/CANCEL/REFRESH/CLOSE/BALANCE и
  у `EXECUTE_KILL_SWITCH`). Не заданы: что executor читает/пишет, какое
  ребро `Deal`/`DealActionState` делает, идемпотентность, как
  удовлетворяется обязательность `resultProfit` на терминале в скоупе шага 6
  (vs расчёт прибыли — шаг 7). Шаг 6 обязан материализовать механику
  финализации; доки её не задают. → Э1.

- **N4 — кто/когда эмитит финализационные команды не задан (дыра фабрики).
  Гейтит `CODE` (майор; следствие N2/N3).** `ServiceCommandFactory`
  (док-владелец «кто создаёт какую `ServiceCommand`») маппит команды строго
  по статусу `DealActionState` (`ServiceCommandFactory.md:25-30`) и
  привязывает каждую к `dealActionStateId` (`ServiceCommand.md:27`).
  Финализационные команды по N2 `DealActionState` не имеют → их источник/
  диспетчеризация не описаны: handler'ы (`PrecheckHandler.md:47`,
  `EntryFinalizedHandler.md:50`, `ExitPendingHandler`) перечисляют их в
  «возможных командах», но путь эмиссии команды **без** `dealActionStateId`
  не специфицирован. → Э1.

- **N7 — per-deal concurrency-guard (D-M1) не специфицирован ни в одном
  доке. Гейтит `DONE` шага 6 (жёсткий гейт) + нужна спека для `CODE`.**
  Сериализация исполнения per-deal (перекрытие тика и ручного триггера →
  двойной SUBMIT) — скоуп шага 6 и **жёсткий гейт `DONE`** (роадмап
  `phase-1.md` §гейты: петлю нельзя включать, пока D-B3/D-M1 не закрыты).
  В доках **отсутствует**: `DealOrchestratorJob.md:10-19` описывает цикл без
  упоминания эксклюзивности/`JobExecutionGuard`/row-lock/`@Version`;
  `ServiceCommandExecutor`/`command-lifecycle`/`RetryPolicyService` молчат.
  Единственная ссылка на `JobExecutionGuard.runExclusively` в доках —
  у несвязанного джоба (`InstrumentExternalRulesSyncJob.md`), и она
  per-job in-memory, не per-deal — автоматически не покрывает. Механизм
  открыт (`backlog.md:429-433`). → Э4.

- **N8 — операционная оболочка `DealOrchestratorJob` недоспецифицирована
  (cadence / enabled / CRON / facade / выборка). Гейтит `CODE` (майор).**
  Codestyle §«Джобы» требует у каждого `@Scheduled`-джоба CRON из конфига,
  булев `enabled` (`@ConfigurationProperties`), async-фасад ручного
  триггера и `JobExecutionGuard`. `DealOrchestratorJob.md:10-19` не задаёт
  **ничего из этого**: ни cadence/CRON, ни `enabled`, ни фасада/ручного
  триггера; «находит активные `Deal`» неквалифицировано — нет критериев
  выборки/планирования (фильтр due-for-retry по `nextRetryAt`, порядок,
  батч, как подхватываются `RETRY_PENDING`). Ниже функционального порога,
  нужного, чтобы написать джоб. → Э4.

- **N9 — модель не выражает обязательную защиту risk-creating входа
  (бесстоповый вход). Гейтит `CODE` (блокер) + торгово-блокирующее (TR1).**
  `Strategy` допускает голый `ENTRY` (без attached-SL, без шага
  `MAIN_PROTECTION`). Трассировка FSM: `PrecheckHandler` строит
  `CREATE_ORDER`/`SUBMIT_ORDER` без проверки «стоп резолвится»
  (`PrecheckHandler.md:17-35` — такой проверки нет), `RiskValidator` на
  бесстоповом входе молча считает размер по `allocationPercents` и
  пропускает `RISK_PER_TRADE` (fail-open в сторону большего риска, известно
  из `backlog.md` §Шаг 6), `EntryFinalizedHandler` пускает в `MANAGING`, а
  его выходная проверка **явно разрешает** отсутствие защиты
  («активная защита подтверждена **либо её отсутствие явно разрешено**»,
  `EntryFinalizedHandler.md:40`). Итог: risk-creating позиция доходит до
  биржи и ведётся в `MANAGING` **без стопа**. Правила «защита обязательна
  для risk-creating входа» нет ни в `Deal.md`, ни в handler'ах, ни в
  `docs/rules/` (форвард-долг со `CODE` шага 5, `backlog.md:320-341`, шаг 1:
  «если нет — завести явно»). → Э5 (торговый разбор — §Торговый фокус, TR1).

- **N12 — перечисление неизвестных live orders/algo для Precheck-чистоты
  не покрыто (CMD-Q4). Гейтит входную проверку `PrecheckHandler` (майор);
  спан шаг 8.** `PrecheckHandler` должен проверять «нет чужих live
  orders/algo» на инструменте перед входом, но сам док отдаёт это в CMD-Q4
  («bulk-командой больше не покрыто», `PrecheckHandler.md:47-49`). Снятие
  bulk-команд (CMD-Q3) оставило дыру: per-entity `REFRESH_*` покрывает
  только **известные** сущности. Владелец — `solution-designer`; remedy —
  инструмент-скоупный read **вне** command-layer (`open-questions.md:165-204`).
  Precheck-аспект — шаг 6; orphan-скан — `AnomalyJob` шаг 8. → Э7.

### Несогласованности между доками (стадия 2)

- **N11 — `maxAttempts` с двумя источниками истины (поле `Retryable` vs
  policy). Гейтит `CODE` (майор).** `maxAttempts` определён дважды без
  указания авторитета: persisted-поле на `Retryable` (значит, на
  `DealActionState`) и поле `ServiceCommandRetryPolicy` (из конфига,
  per-command). Retry-петля сверяет `attemptCount >= maxAttempts → FAILED`
  не уточняя **какой**; контракт `canRetry(retryable, commandType)`
  получает оба. Не задано: копируется ли policy-значение на сущность при
  создании, читается ли live каждый тик (тогда поле сущности избыточно),
  могут ли расходиться. Помечено не-гейтящей CODE-заметкой ещё на
  `DOCS_CHECK_2` шага 4, но концептно не разрешено; финализация (N3) и
  ноги REPLACE идут через тот же retry-путь. → Э4.

- **N13 — стале-кросс-ссылки (handler'ы «мигрируются отдельно» + битый
  указатель на `tasks/deal.md`; форвард-указатель `ack-not-runtime-truth`).
  Не гейтит (гигиена).** `Deal.md` (lifecycle):11-18 пишет, что handler'ы и
  `DealStateMachine` «мигрируются отдельно (форвард-заметки — в
  `.claude/work/questions/tasks/deal.md`)» — но все 7 handler'ов и
  `DealStateMachine` **уже материализованы**, а файл `tasks/deal.md` **не
  существует** (в `tasks/` только `.gitkeep`). Формулировка читается как
  «ещё не сделано», указатель битый. Аналогично `ack-not-runtime-truth.md`
  и `Deal.md:103-104` ссылаются на несуществующие форвард-заметки в
  `tasks/`. → Э8.

- **N14 — `risk-validator-scope` finalization-список опускает
  `FINALIZE_DEAL_ENTRY`. Не гейтит (гигиена).** `risk-validator-scope.md:42-43`
  перечисляет no-validate группу финализации как `MARK_DEAL_ERROR` /
  `MARK_DEAL_CLOSED` / `FINALIZE_DEAL_EXIT` — без `FINALIZE_DEAL_ENTRY`,
  который существует в enum'е и в командах `EntrySubmittedHandler`. Намеренно
  исключён или забыт — из дока неясно. Снимается вместе с N3. → Э8.

- **N15 — `account-bills.md` относит расчёт `resultProfit` к
  `FINALIZE_DEAL_EXIT` — конфликт с границей 6 ↔ 7. Не гейтит (граница
  скоупа).** `account-bills.md:85-86` пишет «`FINALIZE_DEAL_EXIT` считает:
  `Deal.resultProfit = sum(DealCashFlow.amount)`» — кладёт расчёт PnL
  **внутрь** executor'а. Граница 2026-06-21 относит расчёт `resultProfit` к
  **шагу 7**, оставляя в шаге 6 только *механику* финализации. Док делает
  `FINALIZE_DEAL_EXIT` «полностью определённым» через шаг-7-работу, тогда
  как его шаг-6-механика (retry-state, терминальное ребро, триггер) не
  описана (N3). Снимается вместе с N3 + явным отнесением расчёта к шагу 7.
  → Э8.

## Блокирующие открытые вопросы

Из `open-questions.md` (со ссылками):

- **DEAL-Q1** — где хранить persisted retry-state финализации (= N2).
  Горизонт-владелец — шаг 6 (финализация в скоупе). **Блокирует `CODE`.**
- **CMD-Q5** — место оркестрации порядка ног REPLACE (= N5). Запаркован
  «на 6-7»; петля — шаг 6. **Блокирует `CODE`.**
- **CMD-Q6** — принцип «действие vs команда» + классификация `KILL_SWITCH`
  (= N6). Запаркован «на 6-7». **Блокирует `CODE`** (майор).
- **INSTR-Q2 (остаток)** — тайминг/владелец set-leverage (= N10). Форвард к
  шагу 6. **Блокирует `CODE`** пути выставления плеча.
- **CMD-Q4** — перечисление неизвестных live orders/algo (= N12). Владелец
  — `solution-designer`; Precheck-аспект — шаг 6. **Блокирует** входную
  проверку чистоты Precheck.

**Не открытый вопрос, но codestyle-объявленный гейт:** error-политика
(= N1, `codestyle.md` §«Обработка ошибок — TBD»). **Блокирует `CODE`**
шага 6 по самому codestyle.

**Смежные, НЕ гейтящие шаг 6 (форвард к своим шагам):** DEAL-Q2
(`resultProfit` при исчерпании retry — расчёт PnL, шаг 7; *механизм*
финализации — шаг 6), OKX-Q1/Q2/Q3 (`TradeFill`/bills — шаг 7), OKX-Q4
(WS), ORCH-Q1 (онбординг инструмента — другой горизонт), STRUCT-Q1 /
PHASE-Q1/Q2 / STRAT-Q4 / IND-Q1 (другие владельцы/шаги).

> **Граничный кейс DEAL-Q2.** `MARK_DEAL_CLOSED` шага 6 обязан
> удовлетворить инвариант обязательного `resultProfit` на терминале; край
> «что если посчитать нельзя после retry» **касается** шага 6, но по
> границе 2026-06-21 относится к шагу 7 (поведение расчёта). Зафиксировать
> на `GAPS_CLOSE_1`, чтобы механика финализации шага 6 знала, какой
> терминальный контракт ей оставляет шаг 7.

## Эскалации

Маршрут first-cut (через владельцев, `concept-review.md` §Эскалация). CC в
прогоне `DOCS_CHECK` **предлагает** (варианты/крен), не финализирует —
закрытие на `GAPS_CLOSE_1`. Центральная масса гейтов имеет
горизонт-владельца **шаг 6** (петля «включает» отложенное) — `DOCS_CHECK_1`
поднимает их закономерно.

### Э1 (N2 + N3 + N4 / DEAL-Q1). Финализационная под-спина: дом retry-state + executor'ы + эмиссия

- **Вопрос:** где живёт persisted retry-state финализации (`DealActionState`
  привязан к `StrategyAction`, финализация — lifecycle/system action без
  него); как специфицированы финализационные executor'ы
  (`FINALIZE_DEAL_*`/`MARK_DEAL_*`: чтение/запись, терминальное ребро,
  идемпотентность, retry-anchor) и кто/как их эмитит (фабрика маппит только
  по `DealActionState`).
- **Ожидаемый владелец:** `solution-designer` (форма retry-state-дома +
  семантика executor'ов/эмиссии) + `knowledge-curator` (размещение).
- **Кто ответил + трассировка:** reviewer (`concept-review`, два кластера
  сошлись независимо) surface-ил из скоупа «механика финализации» шага 6;
  сверка с owner-доками — `Deal.md:95-98` («общей retry-policy» +
  «открытый вопрос»), `DealActionState.md` (`strategyActionId` обязателен,
  `UNIQUE(deal_id, strategy_action_id)`), `RetryPolicyService.md`
  (`Retryable` только на `DealActionState`), `ServiceCommand.md:37-41`
  (имена, executor'ов нет), `ServiceCommandFactory.md:25-30` (таблица без
  финализации), `ServiceCommandExecutor.md` (группы без FINALIZE/MARK),
  `deal-action-state-materialization.md` («смежный DEAL-Q1 открыт»).
- **Ответ (предложение):** на `GAPS_CLOSE_1` решить DEAL-Q1 — ввести дом
  retry-state финализации (кандидаты: отдельный `DealFinalizationState`
  RVO/сущность с `Retryable`; либо обобщить `DealActionState` на
  lifecycle-target с опциональным `strategyActionId` + `TargetEntityType.DEAL`,
  сняв обязательность ключа на финализации); затем материализовать
  финализационные executor'ы и терминальные рёбра против выбранного дома;
  привести `Deal.md` к выбранному механизму.
- **Варианты + крен:** (а) **отдельный `DealFinalizationState`** (чистое
  разделение «action-retry» vs «lifecycle-retry», не ломает инвариант
  `DealActionState`); (б) обобщить `DealActionState` (меньше типов, но
  размывает «привязан к `StrategyAction`» и инвариант уникальности). **Крен —
  (а)** (инвариант `DealActionState` остаётся жёстким; финализация —
  отдельная природа). Концепт-выбор владельца.
- **Целевой док:** новый `docs/models/domain/other/DealFinalizationState.md`
  (+ lifecycle) либо правка `DealActionState`; новые executor-доки
  `FINALIZE_*`/`MARK_*`; правка `Deal.md`/`DealActionState.md`/
  `ServiceCommandFactory.md`/`ServiceCommandExecutor.md`; закрытие DEAL-Q1.
- **Ярлык исхода:** `варианты-с-креном` (дом retry-state) + `принято-в-работу`
  (executor'ы — проектирование).
- **Ярлык дефицита:** `работа` (проектное решение дома + семантики).
- **Флаг действия CC:** `предложил`.

### Э2 (N1). Error-политика (codestyle-объявленный гейт `CODE` шага 6)

- **Вопрос:** единая политика исключений шага 6 — коды; `@ControllerAdvice`
  vs per-endpoint; документирование ошибок на контроллере; трансляция
  FSM/оркестрационных и manual-trigger ошибок в HTTP. Закрывает TBD
  `codestyle.md` §«Обработка ошибок»; ретро-закрывает майоры шагов 2/4
  (500 вместо 422/409, невыровненные коды реджектов).
- **Ожидаемый владелец:** `solution-designer` (конструкция политики) +
  **пользователь** (policy-хвост: набор кодов, 409-vs-идемпотентность,
  глубина документирования).
- **Кто ответил + трассировка:** reviewer surface-ил из
  `codestyle.md` §«Обработка ошибок — TBD (владелец — шаг 6)» (гейт `CODE`)
  + execution boundary `DealOrchestratorJob.md:21-22`; FSM-внутренний путь
  состоятелен (`runtime-error-classification`, `controlled-exchange-exceptions`,
  `ErrorHandler`) — пробел на API-поверхности.
- **Ответ (предложение):** на `GAPS_CLOSE_1` спроектировать error-политику
  docs-first: глобальный `@ControllerAdvice` + таблица кодов/HTTP-маппинга;
  отделить FSM-runtime-классификацию (есть) от API-error-contract (нет);
  привязать ретро-майоры шагов 2/4.
- **Варианты + крен:** (а) глобальный `@ControllerAdvice` + единый
  error-DTO; (б) per-endpoint обработка. **Крен — (а)** (единая поверхность,
  меньше дублирования) — но набор кодов и 409-vs-идемпотентность — хвост
  пользователя.
- **Целевой док:** `codestyle.md` §«Обработка ошибок» (снять TBD) + новое
  правило/решение error-политики (`docs/rules/` или `docs/decisions/`);
  пометки на затронутых контроллерах.
- **Ярлык исхода:** `варианты-с-креном` (форма) + остаток — `неразрешимо-владельцем`
  (коды/409-политика — пользователь).
- **Ярлык дефицита:** `работа` (конструкция) + `подтверждение` (набор кодов
  — policy-выбор пользователя).
- **Флаг действия CC:** `предложил`.

### Э3 (N5 + N6 / CMD-Q5 + CMD-Q6). Владелец REPLACE-оркестрации + граница «действие vs команда»

- **Вопрос:** где живёт оркестрация порядка ног REPLACE (фабрика vs петля)
  и кто вычисляет следующую ногу по фактам (CMD-Q5); сформулировать принцип
  «действие-оркестрация vs команда-с-внутренними-шагами» и
  классифицировать `KILL_SWITCH` по нему (CMD-Q6).
- **Ожидаемый владелец:** `solution-designer` (оба — конструкция оркестрации).
- **Кто ответил + трассировка:** reviewer (command-кластер) surface-ил из
  «петля — теперь шаг 6»; сверка — `open-questions.md:206-226` (CMD-Q5),
  `:228-246` (CMD-Q6), `ServiceCommandFactory.md:34-36` (CANCEL/REPLACE не
  порождаются), `replace-not-amend.md` (правило ног зафиксировано, владелец
  не назначен), `DealActionState.md` («представление секвенса ног — деталь
  `CODE`»), `KillSwitchExecutor.md` (синхронный fire-all teardown).
- **Ответ (предложение):** на `GAPS_CLOSE_1` назначить владельцем
  REPLACE-секвенса **оркестрационную петлю/`DealStateMachine`** (по фактам),
  не фабрику (фабрика остаётся «одна атомарная команда за проход»);
  сформулировать принцип: *действие-оркестрация* = многоногая последовательность
  **по подтверждённым фактам** (REPLACE), *команда-с-шагами* = синхронный
  **fire-all без ожидания фактов** для скорости снятия риска (`KILL_SWITCH`).
- **Варианты + крен:** CMD-Q5 — (1) правило ног в `ServiceCommandFactory`;
  (2) правило+петля **вместе** (оркестратор владеет секвенсом по фактам).
  **Крен — (2)** (без петли правило мёртвое; факт-driven секвенс — природа
  оркестратора). CMD-Q6 — крен: `KILL_SWITCH` остаётся командой (оправдание —
  аварийный синхронный fire-all), REPLACE — действие.
- **Целевой док:** `DealStateMachine.md` / `ServiceCommandFactory.md`
  (явная запись владельца секвенса) + новое правило/решение «действие vs
  команда»; закрытие CMD-Q5/CMD-Q6.
- **Ярлык исхода:** `варианты-с-креном`.
- **Ярлык дефицита:** `работа` (конструкция оркестрации).
- **Флаг действия CC:** `предложил`.

### Э4 (N7 + N8 + N11 / D-M1). Операционная оболочка оркестратора: concurrency-guard + cadence/enabled/facade/выборка + источник `maxAttempts`

- **Вопрос:** специфицировать per-deal concurrency-guard (D-M1; механизм:
  `JobExecutionGuard` per-deal / row-lock на `Deal`/`DealActionState` /
  `@Version`); операционную оболочку `DealOrchestratorJob` (CRON+`enabled`+
  фасад+guard по codestyle §«Джобы», критерии выборки активных `Deal` и
  подхвата `RETRY_PENDING` по `nextRetryAt`); авторитет `maxAttempts`
  (поле `Retryable` vs policy).
- **Ожидаемый владелец:** `solution-designer` (механизм guard + критерии
  петли) + `code-writer` (применение джоб-конвенций — детали `CODE`).
- **Кто ответил + трассировка:** reviewer surface-ил из скоупа петли +
  жёсткого гейта D-M1; сверка — `DealOrchestratorJob.md:10-19` (нет
  guard/enabled/CRON/facade/выборки), `phase-1.md` §гейты (D-M1 — жёсткий
  гейт `DONE`), `backlog.md:429-433` (механизм открыт), `codestyle.md`
  §«Джобы» (`JobExecutionGuard` per-job, не per-deal), `RetryPolicyService.md`
  (`maxAttempts` дважды), `phase-1.md` (заметка `DOCS_CHECK_2` шага 4).
- **Ответ (предложение):** на `GAPS_CLOSE_1` дописать `DealOrchestratorJob`
  до джоб-конвенции (CRON+`enabled`+async-фасад) + критерии выборки;
  выбрать механизм per-deal guard; зафиксировать авторитет `maxAttempts`.
- **Варианты + крен:** guard — (1) `@Version` оптимистик-лок на `Deal`;
  (2) row-lock; (3) per-deal `JobExecutionGuard`. **Крен — (1)** (in-process
  guard на инстанс — хрупок при мультиинстансе; `@Version` устойчив, готовит
  к распределённому контуру). `maxAttempts` — крен: **policy — авторитет**
  (snapshot на сущность при создании для аудита; live-сверка по policy),
  поле `Retryable` несёт зафиксированный лимит попытки.
- **Целевой док:** `DealOrchestratorJob.md` (оболочка + выборка + guard);
  `RetryPolicyService.md`/`DealActionState.md` (авторитет `maxAttempts`);
  снятие D-M1 как дока-гейта (реализация — `CODE`/`DONE`).
- **Ярлык исхода:** `варианты-с-креном` (механизм guard, авторитет
  `maxAttempts`) + `выводимо-Предложение` (джоб-оболочка — из codestyle).
- **Ярлык дефицита:** `работа` (механизм guard).
- **Флаг действия CC:** `предложил`.

### Э5 (N9 / TR1). Обязательная защита risk-creating входа (бесстоповый вход)

- **Вопрос:** ввести инвариант «risk-creating вход без резолвимого стопа не
  доходит до постановки»; enforcement в PRECHECK/handler (шаг 6) +
  аномалия (шаг 8); снять fail-open `RiskValidator` на бесстоповом входе.
- **Ожидаемый владелец:** `solution-designer` (форма инварианта/enforcement)
  + `trading-specialist` (торговое обоснование) + `trading-review` (валидация).
- **Кто ответил + трассировка:** reviewer (`concept-review` N9 +
  `trading-review` TR1); сверка — `PrecheckHandler.md:17-35` (нет проверки
  резолвимости стопа), `EntryFinalizedHandler.md:40` (отсутствие защиты
  «явно разрешено»), `Strategy.md` (голый `ENTRY`, `attachedProtection`
  null), `RiskValidator.md` (risk-amount требует стопа),
  `backlog.md:320-341` (форвард-долг шага 5: «если нет — завести явно»);
  торговый грунт — `risk-and-sizing.md` §9/§7/§12 (стоп — конститутив:
  worst-case на входе [Tharp гл.9 с.234-236, 144-146]; неограниченная
  ответственность → risk-of-ruin [Vince введ. с.6, гл.5 с.63]); ⚠ корпус
  расколот (Carver — continuous sizing без стопов), но бот сам
  стоп-driven (`per-trade-risk-policy` обуславливает сайзинг «входом со
  стопом»; инвариант «ликвидация за стопом»).
- **Ответ (предложение):** на `GAPS_CLOSE_1` завести правило (вероятно
  `docs/rules/` + lifecycle `Deal`/`PrecheckHandler`): risk-creating вход
  без резолвимой защиты (attached-SL или иной) **не выпускается** PRECHECK;
  бесстоповый risk-creating allocation-сайзинг = аномалия (прекек шаг 6 /
  AnomalyJob шаг 8).
- **Варианты + крен:** (1) **жёсткий блок в PRECHECK** (вход без резолвимого
  стопа → `CLOSED`+причина, не доходит до постановки); (2) разрешить, но
  пометить аномалией постфактум. **Крен — (1)** (защита обязательна *до*
  live risk; постфактум-аномалия оставляет окно без стопа). Форма enforcement
  — владелец; численного хвоста нет (структурное требование).
- **Целевой док:** новое правило `docs/rules/` (защита обязательна для
  risk-creating входа) + `PrecheckHandler.md` (проверка резолвимости стопа)
  + `Deal.md`/`RiskValidator.md` (снять fail-open); закрытие форвард-долга.
- **Ярлык исхода:** `варианты-с-креном` (форма enforcement) + `принято-в-работу`
  (правило + проверка handler'а).
- **Ярлык дефицита:** `работа` (форма инварианта).
- **Флаг действия CC:** `предложил`.

### Э6 (N10 / INSTR-Q2-остаток). Владелец/тайминг set-leverage

- **Вопрос:** кто и когда пишет рабочее плечо на биржу (OKX set-leverage:
  онбординг / перед сделкой / на каждую сделку) и нужна ли роль статического
  `Instrument.leverage` при динамическом рабочем плече.
- **Ожидаемый владелец:** `solution-designer` (тайминг/владелец write) +
  `integrator` (контракт/команда set-leverage, если вводится).
- **Кто ответил + трассировка:** reviewer surface-ил из «динамическое плечо
  + постановка ордера»; сверка — `open-questions.md:119-137` (остаток
  INSTR-Q2, горизонт шаг 6), `backlog.md:296-300` (В-9), `PrecheckHandler.md`
  (set-leverage не упомянут), `per-trade-risk-policy.md` / `instrument-external-rules-materialization.md`.
- **Ответ (предложение):** на `GAPS_CLOSE_1` назначить запись плеча в
  entry-flow **перед постановкой** (PRECHECK-этап): команда/звено set-leverage
  до `SUBMIT_ORDER`, если рабочее плечо отличается от выставленного на бирже;
  `Instrument.leverage` — дефолт/потолок, не источник рабочего плеча.
- **Варианты + крен:** (а) set-leverage **перед каждой сделкой** в PRECHECK
  (idempotent, гарантирует совпадение с расчётом); (б) при онбординге
  (риск стейл-плеча при динамике). **Крен — (а)**.
- **Целевой док:** `PrecheckHandler.md` / entry-flow (звено set-leverage);
  возможная команда/контракт; продвижение INSTR-Q2-остатка.
- **Ярлык исхода:** `варианты-с-креном`.
- **Ярлык дефицита:** `работа` (тайминг — концепт-выбор).
- **Флаг действия CC:** `предложил`.

### Э7 (N12 / CMD-Q4). Перечисление неизвестных live orders/algo для Precheck-чистоты

- **Вопрос:** откуда `PrecheckHandler` берёт «нет чужих live orders/algo»
  на инструменте, раз bulk-команды сняты (per-entity `REFRESH_*` покрывает
  только известное).
- **Ожидаемый владелец:** `solution-designer` (паттерн read вне command-layer).
- **Кто ответил + трассировка:** reviewer surface-ил из входной проверки
  PRECHECK; сверка — `PrecheckHandler.md:47-49` (отдано в CMD-Q4),
  `open-questions.md:165-204` (варианты: read вне command-layer vs scoped
  bulk-scan), `ServiceCommand.md:59-60`.
- **Ответ (предложение):** на `GAPS_CLOSE_1` зафиксировать инструмент-скоупный
  exchange-read **вне command-layer** (в `IntegrationService`, дёргается
  Precheck/job'ами для сверки), не `ServiceCommand`; orphan-скан — `AnomalyJob`
  шаг 8 (учесть легитимное окно двойной reduce-only защиты REPLACE).
- **Варианты + крен:** (1) read вне command-layer; (2) узкая scoped
  bulk-scan операция. **Крен — (1)** (не возвращать снятый bulk в command-layer).
- **Целевой док:** `IntegrationService.md` / `PrecheckHandler.md` (read-источник);
  продвижение CMD-Q4 (orphan-часть — шаг 8).
- **Ярлык исхода:** `варианты-с-креном`.
- **Ярлык дефицита:** `работа` (паттерн read).
- **Флаг действия CC:** `предложил`.

### Э8 (N13 + N14 + N15). Гигиена: стале-ссылки + finalization-список + scope-нота account-bills

- **Вопрос:** снять стале-формулировку «handler'ы мигрируются отдельно» +
  битый указатель `tasks/deal.md` (`Deal.md`/`ack-not-runtime-truth`);
  добавить `FINALIZE_DEAL_ENTRY` в finalization-список `risk-validator-scope`
  (или явно исключить); привести scope-ноту `account-bills` (расчёт
  `resultProfit` → шаг 7).
- **Ожидаемый владелец:** `knowledge-curator` (реконсиляция формулировок/ссылок).
- **Кто ответил + трассировка:** reviewer; сверка — `Deal.md:11-18`
  (стале-нота, `tasks/` только `.gitkeep`), `ack-not-runtime-truth.md`/
  `Deal.md:103-104`, `risk-validator-scope.md:42-43`, `account-bills.md:85-86`.
- **Ответ (предложение):** переформулировать lifecycle-шапку `Deal.md`
  (handler'ы материализованы; ссылка — на `fsm-handler-as-component`);
  снять/перенаправить указатели на `tasks/deal.md`; выровнять
  finalization-список; пометить расчёт `resultProfit` шагом 7 в `account-bills`.
- **Варианты + крен:** без вариантов (правки-cleanup); N14/N15 снимаются
  вместе с N3.
- **Целевой док:** `Deal.md`, `ack-not-runtime-truth.md`,
  `risk-validator-scope.md`, `account-bills.md`.
- **Ярлык исхода:** `выводимо-Предложение`.
- **Ярлык дефицита:** —.
- **Флаг действия CC:** `предложил`.

## Торговый фокус (`trading-review`)

Адверсариальный проход по торговой корректности оркестрации/FSM (грунт —
`.claude/library/trading/distilled/` §system-design / risk-and-sizing /
microstructure / strategy-patterns).

- **TR1 (= N9) — НОВАЯ БЛОКИРУЮЩАЯ торговая находка.** Голый `ENTRY` может
  открыть risk-creating позицию **без резолвимого стопа**: PRECHECK не
  проверяет резолвимость стопа, `RiskValidator` на бесстоповом входе
  fail-open'ит (сайзинг по `allocationPercents`, пропуск `RISK_PER_TRADE`),
  `EntryFinalizedHandler` явно разрешает отсутствие защиты → позиция
  ведётся в `MANAGING` без стопа. **Жёсткий гейт «модель не выражает нужное
  торговое правило»** — правила «защита обязательна для risk-creating входа»
  нет в доках. Корпус: стоп — конститутив системы (worst-case на входе
  [Tharp гл.9 с.234-236]); неограниченная ответственность → risk-of-ruin→1
  [Vince введ. с.6, гл.5 с.63]. ⚠ Корпус расколот (Carver — sizing без
  стопов), но **этот бот стоп-driven** (`per-trade-risk-policy` обуславливает
  сайзинг «входом со стопом»). Разбор — Э5.

- **TR2 — нет верхнего кэпа плеча/нотинала при узком стопе (не гейтит,
  форвард).** Плечо связано только лимитом риска на сделку; узкий стоп
  пускает риск в лимит при большом нотинале/плече. Корпус: позиционные
  лимиты обязательны для автоматики (минимум из границ; кэп плеча
  напр. 2×; ≤1% OI) [Carver AFTS т.4 с.651-655]; ни одна позиция не
  обнуляет счёт на макс-движении [Carver ST гл.9 с.180-181]. **Форвард:**
  сознательно отложено и ратифицировано (`per-trade-risk-policy.md:127-134`,
  `backlog.md:307-316`); revisit — бэктест/живые прогоны (фаза 2+). Scope
  бота (один инструмент / одна позиция / isolated) делает кэпы экспозиции
  преждевременными в фазе 1. **Cross-cutting (forward).**

- **TR3 — нет буфера на проскок/гэп за стопом (не гейтит, форвард).**
  Risk-amount считается так, будто стоп исполняется по своей цене; на
  крипто-перпе гэп проскакивает. Корпус: «цена не рациональна, гэпы через
  уровни — методология исполнения по цене стопа готовит к катастрофе»
  [Vince введ. с.6]; «stop ≠ guaranteed price» [Harris гл.4 с.78].
  **Форвард:** сознательно отложено (`per-trade-risk-policy.md:75-81`,
  «без поправки на проскок (фаза 1)»); числа — бэктест (шаг 7/калибровка).
  **Cross-cutting (forward).**

- **TR4 — наивность размещения к манипуляциям / market-close на
  неликвиде (не гейтит, форвард).** Оркестрация шага 6 (постановка SL/TP,
  market reduce-only close, kill-switch close) placement-blind: нет понятия
  «где стоп относительно очевидных уровней», ликвидности на момент закрытия,
  кластеризации. Корпус: стопы у очевидных S/R выбивают стампидами [Tharp
  гл.9 с.246]; охота за стопами — прятать стопы, торговать ликвид [Harris
  гл.11 с.255-257]; market-impact крупных market-ордеров [Harris гл.4
  с.71-73]. **Форвард:** зависит от калиброванных чисел/ликвидности, которых
  бот ещё не ест; страт-слой имеет хуки (`MARKET_STRUCTURE_BUFFER_PERCENT`,
  OBV «не единственное основание ENTRY»). **Cross-cutting (forward,
  фаза 2+).**

- **Вывод торгового фокуса:** **одна новая блокирующая находка — TR1**
  (модель не выражает обязательную защиту risk-creating входа; жёсткий гейт
  `CODE`). TR2/TR3/TR4 — cross-cutting forward (сознательно отложенные
  калибровочные/экспозиционные оси), не гейтят шаг 6.

### Торгово-состоятельно (проверено, не пробел)

- **Непрерывность защиты при REPLACE** — `replace-not-amend` состоятелен:
  protective-ремодел = place-new → подтверждение фактом → cancel-old
  (окно двойной защиты, **никогда не без защиты**); overlap двух reduce-only
  безопасен (не наращивает позицию). Окна без защиты в protective-пути нет.
- **Порядок teardown EXIT_PENDING / `KillSwitchExecutor`** — торгово-здравы:
  close (доминирующий market risk) → cancel orders → cancel reduce-only
  protection **последней** (явно, чтобы не оставить окно без защиты) →
  безусловный финальный close. `ExitPendingHandler`/`ErrorHandler` не снимают
  защиту до подтверждения flat.
- **ACK ≠ truth** — состоятельно: статус подтверждается `REFRESH_*`, не по
  ACK; защита `ACTIVE` только после refresh-факта. Латентное окно прикрыто
  protective-порядком place-before-cancel.
- **Recovery после рестарта** — FSM пересобирает по фактам (runtime graph +
  `DealContext` + `DealActionState` + exchange facts), не реплеит очередь;
  легитимные окна downtime покрыты. (Бесстоповая позиция после рестарта
  осталась бы неуправляемой по той же причине, что TR1 — не отдельная
  находка.)
- **reduce-only / no-partial-close** — adapter ловит mismatch →
  `EXCHANGE_INVARIANT_VIOLATION` → safety; `CLOSE_POSITION` full-only,
  partial через reduce-only. Защита от разворота/наращивания на выходе
  закрыта.

## Сводка

- **Пробелов:** 15 (N1-N15). Эскалаций: 8 (Э1-Э8). Торговых находок: 4
  (TR1-TR4), блокирующая — 1 (TR1 = N9 = Э5).
- **Агрегация по ярлыкам исхода:** `варианты-с-креном` — 6 (Э1, Э2, Э3, Э4,
  Э5, Э6, Э7 — частью); `выводимо-Предложение` — 2 (Э4-часть, Э8);
  `принято-в-работу` — 3 (Э1 executor'ы, Э5 правило/проверка); `неразрешимо-владельцем`
  — 1 (Э2-остаток: коды/409 — пользователь).
- **Агрегация по ярлыкам дефицита:** `работа` — 7 (Э1 дом, Э2 форма, Э3
  оркестрация, Э4 guard, Э5 форма, Э6 тайминг, Э7 паттерн); `подтверждение`
  — 1 (Э2 коды — policy-выбор пользователя); без дефицита — 1 (Э8).
- **Флаги действия CC:** `предложил` — 8/8. Финализаций нет (DOCS_CHECK
  только предлагает).
- **Гейт `CODE`:** **не чисто.** Гейтят `CODE`: **N1** (error-политика,
  codestyle-гейт), **N2/N3/N4 / DEAL-Q1** (финализационная под-спина без
  дома retry-state и executor'ов), **N5 / CMD-Q5** (REPLACE без владельца
  оркестрации), **N6 / CMD-Q6** (граница действие/команда), **N9 / TR1**
  (бесстоповый вход — жёсткий торговый гейт), **N8** (оболочка джоба),
  **N10 / INSTR-Q2** (set-leverage), **N11** (`maxAttempts`), **N12 / CMD-Q4**
  (Precheck-чистота). **N7 / D-M1** — гейтит `DONE` (жёсткий), нужна
  спека механизма. N13/N14/N15 — гигиена.
- **Торговый гейт:** **блокер есть — TR1** (модель не выражает обязательную
  защиту risk-creating входа).

## Рекомендация

Нужен **`GAPS_CLOSE_1`** (порядок — сначала стадия-0 гейты):

1. **Э2 / N1** — error-политика (codestyle-объявленный гейт `CODE`):
   `@ControllerAdvice` + коды/HTTP-маппинг; снять TBD; привязать ретро-майоры
   шагов 2/4. *(блокер `CODE`)*
2. **Э1 / DEAL-Q1 / N2+N3+N4** — финализационная под-спина: дом retry-state
   (крен — отдельный `DealFinalizationState`) → executor'ы `FINALIZE_*`/
   `MARK_*` + терминальные рёбра + эмиссия. *(блокер `CODE`)*
3. **Э5 / N9 / TR1** — правило обязательной защиты risk-creating входа +
   проверка резолвимости стопа в PRECHECK; снять fail-open. *(жёсткий гейт
   `CODE` + торговый)*
4. **Э3 / CMD-Q5+CMD-Q6 / N5+N6** — назначить владельца REPLACE-секвенса
   (крен — петля/`DealStateMachine`); принцип «действие vs команда»
   (`KILL_SWITCH` — команда). *(блокер `CODE`)*
5. **Э4 / N7+N8+N11 / D-M1** — оболочка `DealOrchestratorJob` (CRON+enabled+
   фасад+выборка); механизм per-deal guard (крен — `@Version`); авторитет
   `maxAttempts` (крен — policy). *(гейт `CODE` + жёсткий гейт `DONE`)*
6. **Э6 / N10 / INSTR-Q2** — тайминг/владелец set-leverage (крен — перед
   постановкой в PRECHECK). *(гейт `CODE`)*
7. **Э7 / N12 / CMD-Q4** — инструмент-скоупный read вне command-layer для
   Precheck-чистоты (orphan-часть — шаг 8). *(гейт Precheck)*
8. **Э8 / N13+N14+N15** — гигиена: стале-ссылки, finalization-список,
   scope-нота `account-bills`.
9. Зафиксировать граничный контракт DEAL-Q2 (что шаг 7 оставляет механике
   финализации шага 6 на терминале).

После `GAPS_CLOSE_1` — `DOCS_CHECK_2` (подтверждающий прогон). Чистый
`DOCS_CHECK` — обязательное условие гейта `CODE`
(`roadmap-step-execution.md` §«Гейт `CODE` — чистый `DOCS_CHECK`»).

## Закрытие (GAPS_CLOSE_1, 2026-06-22)

Пробелы `DOCS_CHECK_1` закрыты в доках по согласованным с пользователем
решениям. Сводка по находкам:

### Стадия-0 гейты

- **N1 (Э2) — error-политика.** Заведено правило
  `docs/rules/error-handling-policy.md` (внешняя поверхность — единый
  глобальный `@ControllerAdvice` + единый error-DTO; async-фасад ручного
  триггера: `202` запуск / `409` отказ запуска; внутренняя градация 4
  уровня — лог / ретрай / **холд инструмента** / **холд биржи**) + новое
  правило `docs/rules/instrument-hold.md` (уровень 3, инструмент-scope холд,
  снятие вручную). Реконсиляция: уровни 1-2 → `runtime-error-classification`
  + retry-policy; уровень 4 → `EXCHANGE_INVARIANT_VIOLATION`/`exchange-hold`.
  **TBD снят** в `.claude/rules/codestyle.md` §«Обработка ошибок». Набор
  HTTP-кодов / 409-vs-идемпотентность — провизорный хвост пользователя.
- **N2+N3+N4 / DEAL-Q1 (Э1) — финализационная под-спина.** Дом retry-state
  — отдельная сущность `DealFinalizationState`
  (`docs/models/domain/other/DealFinalizationState.md` +
  `docs/lifecycles/DealFinalizationState.md` +
  `docs/decisions/deal-finalization-state-materialization.md`; **крен (а)**
  принят — не обобщение `DealActionState`). Материализованы 4 executor-дока
  (`FinalizeDealEntryExecutor`, `FinalizeDealExitExecutor`,
  `MarkDealClosedExecutor`, `MarkDealErrorExecutor`: чтение/запись,
  терминальные рёбра, идемпотентность, retry-anchor). Путь эмиссии:
  `ServiceCommand` +`dealFinalizationStateId`; `ServiceCommandFactory`
  эмитит по статусу `DealFinalizationState`; `DealContext.finalizationStates`.
  Граница 6 ↔ 7: расчёт `resultProfit` — шаг 7. **DEAL-Q1 закрыт.**
- **N5+N6 / CMD-Q5+CMD-Q6 (Э3) — REPLACE-владелец + «действие vs команда».**
  Решение `docs/decisions/action-orchestration-vs-command.md`: владелец
  оркестрации REPLACE — петля/`DealStateMachine` (по фактам), фабрика —
  «одна команда за проход»; `KILL_SWITCH` — отдельная команда (доводит
  teardown сама, не зависит от петли; защиту снимать последней). Записано в
  `DealStateMachine.md`, `ServiceCommandFactory.md`, `KillSwitchExecutor.md`.
  **CMD-Q5/CMD-Q6 закрыты.**
- **N10 / INSTR-Q2 (Э6) — set-leverage.** Рабочее плечо пишется на биржу в
  `PRECHECK` **перед каждым ордером** (idempotent); `Instrument.leverage` —
  потолок/умолчание. `PrecheckHandler.md`; **INSTR-Q2 продвинут** (остаток —
  CODE-представление write).

### Стадия-2 (петля / финализация / ретрай)

- **N9 / TR1 (Э5) — обязательная защита risk-creating входа.** Инвариант
  `docs/rules/risk-creating-entry-protection.md`: risk-creating вход без
  резолвимого стопа не доходит до постановки (`PRECHECK` блок → `CLOSED` +
  `RISK_CONTROL`); двусторонний enforcement (блок на входе + нарушение
  постфактум → реакция уровня 4). Снят fail-open `RiskValidator` (новый код
  `RISK_CREATING_ENTRY_WITHOUT_STOP` — `RiskCheckResult.md`/`RiskValidator.md`);
  `EntryFinalizedHandler` больше не допускает live-risk-позицию без защиты.
  Reduce-only не трогается; численного хвоста нет. **TR1 снят.**
- **N7+N8+N11 / D-M1 (Э4) — оболочка оркестратора + concurrency + `maxAttempts`.**
  `DealOrchestratorJob.md`: CRON+`enabled`+async-фасад+критерии выборки
  (active + due-for-retry по `nextRetryAt`); **D-M1** — блокировка на уровне
  БД на **весь проход** (и таймерный, и ручной заход), сериализует проходы →
  per-deal-защита не нужна; in-memory guard отвергнут (мультиинстанс).
  **`maxAttempts`** — авторитет **policy** (читается живьём), поле на
  сущности — снимок для истории (`RetryPolicyService.md`,
  `DealActionState.md`). Реализация D-M1 — гейт `DONE` на `CODE`.
- **N12 / CMD-Q4 (Э7) — Precheck-чистота.** Инструмент-скоупный exchange-read
  **вне command-layer** (`IntegrationService.md` §«Инструмент-скоупный read»,
  `PrecheckHandler.md`): видит незнакомые сущности; bulk-команду не
  возвращаем. **Precheck-часть CMD-Q4 закрыта**, orphan-скан — шаг 8.

### Гигиена (N13+N14+N15) + DEAL-Q2

- **N13** — снята стале-формулировка «handler'ы мигрируются отдельно» и
  битые указатели на `tasks/deal.md` (`Deal.md` модель+lifecycle,
  `ack-not-runtime-truth.md`).
- **N14** — `risk-validator-scope.md`: добавлен `FINALIZE_DEAL_ENTRY` в
  finalization-список.
- **N15** — `account-bills.md`: расчёт `resultProfit` отнесён к шагу 7, не
  внутрь `FINALIZE_DEAL_EXIT`.
- **DEAL-Q2** — граничный контракт терминала: финализация всегда доводит до
  терминала (чистый `CLOSED` с числом / ошибочный терминал, не зависает живым
  риском); инвариант «прибыль обязательна» — про чистое закрытие; число на
  ошибочном терминале — шаг 7 (`Deal.md` §«Терминальный контракт финализации»).
  **DEAL-Q2 закрыт.**

### Open-questions

Закрыты: **DEAL-Q1**, **DEAL-Q2**, **CMD-Q5**, **CMD-Q6** (удалены из
`open-questions.md`, история — в decisions). Продвинуты: **INSTR-Q2-остаток**
(тайминг/владелец решены), **CMD-Q4** (Precheck-часть). Снят TBD error-политики
в `codestyle`. Не тронуты cross-cutting форварды TR2/TR3/TR4 (backlog).

Далее — `DOCS_CHECK_2` (подтверждающий прогон).
