# DOCS_CHECK_1 — шаг 4 фазы 1 (Команды и их жизненный цикл, `ServiceCommand`)

## На какой вопрос отвечает этот файл

На каком под-шаге мы в исполнении шага 4 фазы 1 и какие пробелы концепции
нашёл первый прогон сквозной проверки (`concept-review` + `trading-review`).

## Контекст прогона

- **Шаг:** 4 фазы 1 — «Команды и их жизненный цикл (`ServiceCommand`:
  submit/amend/cancel/close/REFRESH; исполнители; lifecycle; факт и
  реконсиляция через REFRESH, не ACK; ведение Position/Order)».
- **Под-шаг:** `DOCS_CHECK_1` (первая итерация проверки концепции).
- **Что шаг должен делать функционально:** материализовать command-layer —
  атомарные команды над runtime-сущностями (`Order` / `AlgoOrder` /
  `Position`), их исполнители, жизненный цикл `CREATE → SUBMIT → REFRESH`,
  реконсиляцию фактического состояния через `REFRESH_*` (а не ACK), ведение
  статусов order/algo/position по биржевым фактам.
- **Особенность:** концепт command-layer **в основном уже материализован**
  миграцией из архива (~15 executor-доков, 3 resolver-компонента, 3
  lifecycle-дока, ~10 сквозных правил, модели `Order`/`AlgoOrder`/`Position`,
  OKX-контракты). Первый прогон — не на пустом месте: подтверждает зрелую
  часть и вычленяет то, что **гейтит `CODE`**.

## Охват

### Проверены (в охвате шага 4)

- **Command-модели:** `docs/components/models/ServiceCommand.md`,
  `ServiceCommandPayload.md`, `DealContext.md`.
- **Command-каркас:** `ServiceCommandFactory.md`, `ServiceCommandExecutor.md`,
  `RetryPolicyService.md`.
- **Executors:** `Create/Submit/Amend/CancelOrderExecutor`,
  `Create/Submit/Amend/CancelAlgoOrderExecutor`, `ClosePositionExecutor`,
  `Refresh{Order,AlgoOrder,Position,Fills,Balance}Executor`,
  `KillSwitchExecutor` (выборочная вычитка + сверка с общей семантикой групп).
- **Resolver'ы:** `OrderExternalStatusResolver`,
  `AlgoOrderExternalStatusResolver`, `PositionStatusResolver`,
  `PositionStatusResolveResult`.
- **Lifecycles:** `docs/lifecycles/Order.md`, `AlgoOrder.md`, `Position.md`.
- **Доменные модели:** `docs/models/domain/core/Order.md`, `AlgoOrder.md`,
  `Position.md`.
- **Правила (сквозные, command-релевантные):** `command-lifecycle`,
  `ack-not-runtime-truth`, `external-status-resolution`,
  `controlled-exchange-exceptions`, `runtime-error-classification`,
  `exchange-hold`, `no-partial-close`, `idempotency-via-unique`,
  `audit-not-runtime-source`, `reduce-only-invariant` (OKX).
- **Решения:** `executor-payload-file-granularity` (CMD-Q1).
- **Open-questions:** проход по `open-questions.md` (DEAL-Q1/Q3, CMD-Q2,
  OKX-Q1/Q2/Q3, RISK-Q1/Q2).

### Вне охвата (помечены, не проверялись по существу)

- **FSM (шаг 6):** `DealStateMachine`, FSM-handler'ы (`Precheck`,
  `EntrySubmitted`, `EntryFinalized`, `ProtectionSwitched`, `Managing`,
  `ExitPending`, `Error`). Command-layer спроектирован FSM-агностично
  (executor принимает `payload` + `DealContext`); кто и когда зовёт команду —
  шаг 6.
- **Deal / P&L (шаг 7):** `Deal` модель/lifecycle, `deal-management` процесс
  (оркестрация), `DealOrchestratorJob`, `DealOpeningService`,
  `DealContextService`, `EntryScannerJob`, команды `FINALIZE_DEAL_*` /
  `MARK_DEAL_*`, финализация PnL (`RefreshFillsExecutor` в части итогового
  `resultProfit`).
- **Risk-преконтроль (шаг 5):** `RiskValidator`, `RiskBlockResolver`,
  `risk-evaluation`, `RiskSettings`.
- **Anomaly / kill-switch flow (шаг 8):** `AnomalyJob`, полный kill-switch
  (только исполнительная семантика `KillSwitchExecutor` — в охвате).

## Стадия остановки

Обход дошёл до **стадии 2** (компоненты + модели) — прошёл все стадии.

- **Стадия 0 (гейтящие технические вопросы / скоуп):** чисто. Механика
  command-layer полностью специфицирована (`CREATE → SUBMIT → REFRESH`,
  ACK-not-truth, resolver→safety-каскад, evidence-cycle). Гейтящего
  технического вопроса уровня «WS vs REST» нет; границы скоупа заданы
  формулировкой шага (finalize/FSM — поздние шаги).
- **Стадия 1 (процессы):** чисто-для-обхода. Жизненный цикл команды —
  правило (`command-lifecycle`), не процесс. Recovery/evidence-порядок
  (какой `REFRESH_*` за каким) описан per-entity в lifecycle'ах; сводный
  recovery-flow — форвард к Deal-оркестрации (шаг 6/7), обход не гейтит.
- **Стадия 2 (компоненты + модели):** найдены пробелы (ниже).

## Пробелы по типам

### Name-level без структуры (нужна структура)

- **N1 — `DealActionState` не материализован (= DEAL-Q3). Гейтит `CODE`.**
  Центральная для command-layer модель: на неё опираются
  `ServiceCommand.dealActionStateId`, `ServiceCommandFactory` (выбор команды
  по статусу `PLANNED/CREATED/SUBMITTED`), `ServiceCommandExecutor` (переводы
  `RETRY_PENDING` / `FAILED`), `RetryPolicyService` (база `Retryable`),
  `DealContext.actionStates`, `RuntimeTarget` (связь `StrategyAction ↔
  Order/AlgoOrder/Position`). По функциональному порогу сущность
  **конструируется, читается по статусу, персистится** (инвариант
  `UNIQUE(deal_id, strategy_action_id)`) → доки обязаны задать
  типы/nullability/схему хранения. Файл модели сейчас **отсутствует**
  (`docs/models/domain/.../DealActionState.md` нет); структура известна из
  архива (DEAL-Q3), но открыты: **размещение** (`aggregate` vs `other`),
  **представление** (`RuntimeTarget` объектом + база `Retryable` vs инлайн
  `targetEntityType`/`targetEntityId` + retry-поля), **отдельный lifecycle vs
  статусы разделом**. Горизонт DEAL-Q3 — ровно шаг 4. Эскалация ниже.

- **N2 — `AttachedAlgoOrderStateResolver` без компонент-дока.**
  `docs/lifecycles/Order.md` резолвит attached protection «по фактам» через
  `AttachedAlgoOrderStateResolver` (отдельно от `OrderExternalStatusResolver`,
  т.к. у OKX `attachAlgoOrds` нет полноценного `state`). Алгоритм матчинга
  частично описан в lifecycle (§«Attached protection resolving», §«Missing
  attached protection policy»), но компонент-дока, фиксирующего контракт и
  границы как у трёх других resolver'ов (`docs/components/...Resolver.md`),
  **нет** (подтверждено grep'ом: имя есть только в lifecycle, backlog и
  архиве). Шаг 4 материализует резолвинг attached-защиты → компонент нужно
  специфицировать. Эскалация ниже.

### Несогласованности между доками

- **N3 — битые форвард-ссылки на несуществующие task-файлы.** Lifecycle'ы и
  доменные модели `Order`/`AlgoOrder`/`Position` ссылаются на форвард-заметки
  в `.claude/work/questions/tasks/order.md`, `algo-order.md`, `position.md` и
  формулируют command-подсистему как «мигрируется отдельно». Эти файлы
  **не существуют** (активный `tasks/` — только `.gitkeep`; глобальный glob по
  именам не находит; историчны под `2026-05-27-миграция-торговых-сущностей`).
  Ссылки дважды-стале: мёртвый указатель + устаревшая рамка — шаг 4 **и есть**
  та «отдельная миграция». Цель правки — снять/перенаправить заметки
  (gap-материал для `GAPS_CLOSE`/`SYNC`). Низкий приоритет, не гейтит.

- **N5 — не закреплён исполнитель 4 recovery-refresh команд.**
  `REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` / `REFRESH_ALGO_ORDERS` /
  `REFRESH_ALGO_ORDER_HISTORY`: решение `executor-payload-file-granularity.md`
  **сознательно** не заводит им отдельных executor-файлов («покрыты общей
  семантикой `REFRESH_*`»). Но `RefreshOrderExecutor.md` заявляет, что
  исполняет «только `REFRESH_ORDER`», а pending/history перечисляет как
  «выбирает FSM» — то есть **какой компонент их фактически исполняет** при
  материализации в коде не зафиксировано. Сознательно отложено решением;
  всплывёт деталью на `CODE`. Низкий приоритет.

### Неотвеченные вопросы (гейтят чистоту, не обход)

- **N4 — CMD-Q2: базовый тип/дискриминатор payload'ов + судьба
  `ServiceCommandPayload.md`.** Горизонт CMD-Q2 — шаг 4 (материализация
  payload-детали). Нужно: есть ли у payload'ов общий базовый
  тип/дискриминатор; перенос payload-разделов из агрегирующего
  `ServiceCommandPayload.md` к своим executor'ам (по решению
  `executor-payload-file-granularity`). Не гейтит обход, нужно для чистого
  кода. Эскалация ниже.

## Блокирующие открытые вопросы

Из `open-questions.md` (со ссылками) — гейтят **`CODE`** шага 4, не обход:

- **DEAL-Q3** — размещение/структура `DealActionState` (= N1). Горизонт —
  шаг 4. **Блокирует `CODE`.**
- **CMD-Q2** — базовый тип/дискриминатор payload'ов (= N4). Горизонт — шаг 4.
  Блокирует чистоту.

**Смежные, но НЕ гейтящие шаг 4 (форвард к своим шагам):**

- **DEAL-Q1** — persisted retry-state финализации (`REFRESH_FILLS`,
  `FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`). Финализация — шаг 7; вопрос
  примыкает к `DealActionState`, но его разрешение — шаг 7. Cross-cutting.
- **OKX-Q1** — persisted `TradeFill` + executor финализации. PnL/финализация
  — шаг 7; `RefreshFillsExecutor` для обновления execution-фактов
  (`accumulatedFillSize`/`averagePrice`/`fee`) шагу 4 достаточно.
- **OKX-Q2 / OKX-Q3** — fills-archive / bills (`DealCashFlow`). Шаг 7 (PnL).
- **RISK-Q1 / RISK-Q2** — `RiskSettings` / worst-case guard. Шаг 5.
  `ServiceCommandFactory` ссылается на risk-гейт, но `RiskValidator` — шаг 5.

## Эскалации

Маршрут first-cut (через владельцев, `concept-review.md` §Эскалация). CC в
прогоне `DOCS_CHECK` **предлагает** (варианты/крен), не финализирует —
закрытие на `GAPS_CLOSE_1`.

### Э1 (N1 / DEAL-Q3). Структура и размещение `DealActionState`

- **Вопрос:** как материализовать `DealActionState` — размещение,
  доменное представление, наличие отдельного lifecycle.
- **Ожидаемый владелец:** `solution-designer` (представление — концепт-выбор)
  + `knowledge-curator` (размещение — рутинная классификация).
- **Кто ответил + трассировка:** reviewer (`concept-review`) surface-ил из
  сквозной зависимости command-layer; сверка с owner-источниками — DEAL-Q3
  (`open-questions.md`, цитаты архива СК §6 / ЖЦ §7),
  `RetryPolicyService.md` (база `Retryable`: `attemptCount`, `maxAttempts`,
  `nextRetryAt`, `lastError:RetryError`), `ServiceCommand.md`
  (`dealActionStateId`), `persistence-representation` (вложенные объекты →
  JSONB). Поля известны: `id`, `dealId`, `strategyActionId`, target
  (`entityType:TargetEntityType ∈ {ORDER,ALGO_ORDER,POSITION,DEAL,BALANCE,
  NONE}`, `entityId`), `status:DealActionStateStatus ∈ {PLANNED,CREATED,
  SUBMITTED,COMPLETED,RETRY_PENDING,FAILED,SKIPPED}`, retry-поля; инвариант
  `UNIQUE(deal_id, strategy_action_id)`. Расхождение представления СК §6 vs
  ЖЦ §7 не реконструируется — взято из DEAL-Q3.
- **Ответ (предложение):** материализовать модель + (вероятно) отдельный
  lifecycle. В БД вложенные `RuntimeTarget`/`RetryError` — JSONB (по
  `persistence-representation`, не вопрос). Открыт доменный выбор.
- **Варианты + крен:**
  - *Представление:* (а) `RuntimeTarget` объектом + база `Retryable` (СК §6);
    (б) инлайн `targetEntityType`/`targetEntityId` + retry-поля (ЖЦ §7).
    **Крен — (а):** согласуется с rich-доменом и уже заявленной в
    `RetryPolicyService.md` базой `Retryable`; едина с retry-механикой.
  - *Размещение:* `domain/other` (операционная runtime-модель) vs
    `domain/aggregate` (тесная связь с сопровождением сделки). **Лёгкий крен —
    `other`** (операционное runtime-состояние, не торговый PnL-агрегат); но
    это рутинный вызов `knowledge-curator` при материализации.
  - *Lifecycle:* отдельный `docs/lifecycles/DealActionState.md` (есть
    status-enum с переходами, параллель `Order`/`AlgoOrder`) vs статусы
    разделом. **Крен — отдельный lifecycle.**
- **Целевой док:** новый `docs/models/domain/{other|aggregate}/DealActionState.md`
  (+ возможно `docs/lifecycles/DealActionState.md`); закрытие DEAL-Q3.
- **Ярлык исхода:** `варианты-с-креном` (представление/lifecycle) +
  `принято-в-работу` (размещение — рутинная классификация).
- **Ярлык дефицита:** `работа` (нужно проектное решение по представлению).
- **Флаг действия CC:** `предложил`.

### Э2 (N2). Компонент `AttachedAlgoOrderStateResolver`

- **Вопрос:** специфицировать ли отдельный компонент-резолвер attached
  protection и каков его контракт/границы.
- **Ожидаемый владелец:** `solution-designer` (контракт компонента) /
  `knowledge-curator` (размещение компонент-дока).
- **Кто ответил + трассировка:** reviewer surface-ил из `Order.md` lifecycle
  (резолвинг attached «по фактам» + missing-policy матрица по статусу
  parent); сверка — `docs/components/` (три аналогичных resolver-дока есть,
  этот отсутствует), grep по имени (только lifecycle/backlog/архив). Контракт
  в основном **выводим** из lifecycle: вход — набор фактов
  (`AttachedAlgoOrderExternalSnapshot` в `OrderExternalSnapshot` +
  статус parent), выход — `AttachedAlgoOrder.Status`/`closeReason` candidate,
  границы как у прочих resolver'ов (не сохраняет, не решает за FSM).
- **Ответ (предложение):** завести `docs/components/AttachedAlgoOrderStateResolver.md`
  по образцу трёх resolver-доков; матрицу «по фактам» оставить владельцем в
  `Order.md` lifecycle, в компоненте — контракт/границы/реализация под биржу.
- **Варианты + крен:** (а) отдельный компонент-док (крен — паритет с тремя
  resolver'ами, имя уже используется); (б) оставить только в lifecycle (резолв
  как часть `RefreshOrderExecutor`). **Крен — (а).**
- **Целевой док:** новый `docs/components/AttachedAlgoOrderStateResolver.md`.
- **Ярлык исхода:** `выводимо-Предложение` (контракт выводим из lifecycle).
- **Ярлык дефицита:** —.
- **Флаг действия CC:** `предложил`.

### Э3 (N4 / CMD-Q2). Базовый тип payload'ов + перенос разделов

- **Вопрос:** есть ли общий базовый тип/дискриминатор `ServiceCommandPayload`
  и куда уезжают payload-разделы.
- **Ожидаемый владелец:** `solution-designer` (базовый тип — концепт) +
  `knowledge-curator` (перенос разделов к executor'ам).
- **Кто ответил + трассировка:** reviewer surface-ил из CMD-Q2 +
  `executor-payload-file-granularity.md` (payload — раздел у своего
  executor'а; судьба `ServiceCommandPayload.md` отложена в CMD-Q2). Содержимое
  payload'ов в любом случае едет к executor'ам (решение принято); открыт лишь
  базовый тип/дискриминатор.
- **Ответ (предложение):** на `GAPS_CLOSE_1` — решить по базовому типу
  (дискриминатор `ServiceCommandType` уже есть на команде; отдельная база
  payload'ов может быть не нужна), перенести разделы к executor'ам, закрыть
  CMD-Q2 и упразднить/переразместить `ServiceCommandPayload.md`.
- **Варианты + крен:** (а) без общего базового типа — payload'ы независимы,
  дискриминация по `ServiceCommandType` команды; (б) общий маркер-база
  `ServiceCommandPayload`. **Лёгкий крен — (а)** (база без поведения ценности
  не несёт; тип команды уже дискриминирует) — но это концепт-выбор владельца.
- **Целевой док:** разделы → доки executor'ов; решение/закрытие CMD-Q2;
  судьба `docs/components/models/ServiceCommandPayload.md`.
- **Ярлык исхода:** `варианты-с-креном`.
- **Ярлык дефицита:** `работа` (концепт-выбор базового типа).
- **Флаг действия CC:** `предложил`.

### Э4 (N3, N5). Гигиена: стале-ссылки и исполнитель recovery-refresh

- **Вопрос:** снять битые форвард-ссылки на `tasks/{order,algo-order,position}.md`
  (N3) и закрепить исполнителя 4 recovery-refresh команд (N5).
- **Ожидаемый владелец:** `knowledge-curator` (реконсиляция ссылок) /
  `code-writer`-деталь (исполнитель refresh — на `CODE`).
- **Кто ответил + трассировка:** reviewer; N3 — grep подтвердил отсутствие
  файлов; N5 — `executor-payload-file-granularity.md` (сознательно без
  файлов) vs `RefreshOrderExecutor.md` («только `REFRESH_ORDER`»).
- **Ответ (предложение):** N3 — на `GAPS_CLOSE_1`/`SYNC` переписать заметки
  (command-подсистема = шаг 4, мёртвые указатели убрать). N5 — зафиксировать
  одной строкой, что recovery-refresh команды исполняет соответствующий
  entity-refresh-executor (или общий refresh-механизм), без отдельных файлов
  (как решено); деталь уровня `CODE`.
- **Варианты + крен:** N3 — без вариантов (правка-cleanup). N5 — деталь
  материализации, крен «не плодить файлы» (по существующему решению).
- **Целевой док:** `Order.md`/`AlgoOrder.md`/`Position.md` (lifecycle +
  модели) — снять заметки; пометка про исполнителя — в
  `ServiceCommandExecutor.md` или `command-lifecycle`.
- **Ярлык исхода:** `выводимо-Предложение`.
- **Ярлык дефицита:** —.
- **Флаг действия CC:** `предложил`.

## Торговый фокус (`trading-review`)

Адверсариальный проход по торговой/операционной корректности command-layer
(грунт — дистиллят корпуса, операционно-исполнительный риск).

- **Реконсиляция-по-фактам, не по ACK** (`ack-not-runtime-truth`,
  evidence-cycle до `MISSING_AFTER_REFRESH`, `CLOSE_POSITION` подтверждается
  `REFRESH_POSITION`) — корпусно-состоятельна: исполнение/операционный риск
  требует подтверждать факт состоянием на бирже, не приёмом команды. Защита
  от «бот считает позицию закрытой, а она жива» (голый риск) и «двойной
  ордер» — закрыта.
- **Reduce-only / full-close-only** (`no-partial-close`,
  `reduce-only-invariant`: mismatch → `EXCHANGE_INVARIANT_VIOLATION` →
  safety-каскад) — защита от случайного увеличения/разворота позиции на
  выходах. Состоятельно.
- **Protection-lost** (parent `COMPLETED` + позиция active + standalone
  protection отсутствует → attached `ERROR`, `PROTECTION_LOST`, `Deal ERROR`,
  `Exchange HOLD`): attached-SL рождается **вместе** с entry-order (OKX
  `attachAlgoOrds`) → нет окна «позиция без стопа» от момента исполнения;
  switch attached→standalone — только после подтверждения standalone. Голого
  окна нет.
- **Вывод:** **новой блокирующей торговой находки по command-layer нет** —
  модель операционной безопасности зрелая и корпусно-согласованная.
- **Cross-cutting форвард (не гейтит шаг 4):** worst-case guard поверх
  вычисленного плеча/позиции — **RISK-Q2**, владелец шаг 5. Уже запаркован.

## Сводка

- **Пробелов:** 5 (N1-N5). Эскалаций: 4 (Э1-Э4).
- **Агрегация по ярлыкам исхода:** `варианты-с-креном` — 2 (Э1, Э3);
  `выводимо-Предложение` — 2 (Э2, Э4); `принято-в-работу` — 1 (Э1, размещение).
- **Агрегация по ярлыкам дефицита:** `работа` — 2 (Э1 представление, Э3
  базовый тип); без дефицита — 2 (Э2, Э4).
- **Флаги действия CC:** `предложил` — 4/4. Финализаций нет.
- **Гейт `CODE`:** **не чисто.** Блокирует `CODE` — **N1 (DEAL-Q3)**
  структурно (центральная модель command-layer без материализации). N4
  (CMD-Q2) гейтит чистоту payload-детали. N2 — name-level компонент-пробел.
  N3/N5 — гигиена.
- **Торговый гейт:** блокеров нет (модель корпусно-состоятельна).

## Рекомендация

Нужен **`GAPS_CLOSE_1`**:

1. **N1 / DEAL-Q3** — материализовать `DealActionState` (размещение +
   представление + lifecycle), закрыть DEAL-Q3. *(блокер `CODE`)*
2. **N4 / CMD-Q2** — решить базовый тип payload'ов, перенести разделы к
   executor'ам, закрыть CMD-Q2, разрешить судьбу `ServiceCommandPayload.md`.
3. **N2** — завести `AttachedAlgoOrderStateResolver.md`.
4. **N3 / N5** — снять стале-ссылки на несуществующие task-файлы; закрепить
   исполнителя recovery-refresh команд.

После `GAPS_CLOSE_1` — `DOCS_CHECK_2` (подтверждающий прогон). Чистый
`DOCS_CHECK` — обязательное условие гейта `CODE`
(`roadmap-step-execution.md` §«Гейт `CODE` — чистый `DOCS_CHECK`»).
