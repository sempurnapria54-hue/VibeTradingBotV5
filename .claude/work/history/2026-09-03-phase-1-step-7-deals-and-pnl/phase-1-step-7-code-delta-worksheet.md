# Рабочий лист CODE-дельты шага 7 фазы 1 (снят из бэклога 2026-09-11)

## На какой вопрос отвечает этот файл

Какие инструкции исполнителю `CODE` шага 7 фазы 1 стояли в бэклоге на момент их снятия — со снятыми редакциями и провенансом.

## Откуда

Дословная копия секций `.claude/work/backlog.md` «Агрегатная сделка и транши — CODE-дельта» (строки 72-332) и подсекций «Шаг 7 (сделки и P&L) — исполнительный хвост»: «CODE-дельта шага», «Грунт `integrator` для шага 7», «Рантайм-верификация и форвард» (строки 1920-3116) по состоянию на коммит `714a0217`. Всё, что здесь названо «что писать», построено шагом 7 фазы 2 (`services/trading-core`, `libs/domain-model`, `libs/strategy-engine`) — доказательства по блокам в `.claude/work/history/2026-09-11-backlog-diet.md`. Живые остатки вынесены в бэклог отдельными секциями; **две конструкции ниже сняты дизайном и переносу не подлежат**: `Instrument.externalModifiedAt` как измеритель свежести ключа группы (заменён `TradeFeeRate.externalModifiedAt`) и `Order.Type.REDUCE_ONLY` (заменён `positionReducingOnly` + `SizeMode.REDUCE_ONLY`). Файл описывает прошлое и не правится.

---

## Агрегатная сделка и транши — CODE-дельта

**Имя ратифицировано держателем — `Tranche`** (`StrategyTranche` /
`DealTranche`); `DealDetail` и `DealFlow` отклонены. **Корпус приземлён**
(2026-08-29): модели, lifecycles, спецификации, правила, процессы,
компоненты и эталон стратегии переведены на транши, прогон зелёный.
Конструкция и доводы —
`.claude/work/history/2026-08-29-tranche-landing/aggregate-deal-design.md`,
итог приземления —
`.claude/work/history/2026-08-29-tranche-landing.md`.

Здесь — то, что осталось **коду**; в корпусе работы нет.

- **Схема** (пре-лонч политика: таблицы пусты, бэкфилла нет,
  `ALTER` сразу в целевой форме —
  `.claude/rules/pre-launch-schema-changes.md`):
  - `+deal_tranches`: `deal_id` FK `NOT NULL`, `internal_id`
    (`varchar(64)`, уникален) `NOT NULL`, `status varchar(64)`
    `NOT NULL`, `strategy_tranche_id` FK, `level int`,
    `entry_step_type varchar(64)`, `close_reason varchar(64)`, аудит;
    индексы `ix_deal_tranche_deal_status`, частичный
    `uk_deal_tranche_declaration` (`deal_id`, `strategy_tranche_id`,
    `level`) среди нетерминальных;
  - `+orders.deal_tranche_id`, `+algo_orders.deal_tranche_id` — FK
    `NOT NULL`;
  - `+deal_strategy_action_states.deal_tranche_id` (FK, nullable у
    системных), `+deal_system_action_states.deal_tranche_id`; **транш
    входит в состав частичных ключей живых стратегийных исполнений** —
    иначе N траншей сетки конфликтуют по одному объявлению;
  - `−deals.entry_step_type` (уехал на транш); `entry_reason` остаётся на
    сделке и становится **`NOT NULL`**, а `deals.strategy_detail_id` —
    **nullable**: у восстановленной сделки объявленной детали нет вовсе,
    и биекция «деталь пуста ⟺ причина заведения — восстановление»
    держится `CHECK`-ограничением. Состав значений причины и довод
    разворота — `docs/models/domain/aggregate/Deal.md`;
  - `+strategy_tranches`: `strategy_detail_id` FK, `key`, `level_count
    int`, `level_step numeric(36,18)`, `position_reopen_allowed`;
    уникальность пары «деталь, ключ»;
  - `−strategy_details.position_reopen_allowed`;
    `−strategy_order_actions.level`, `−strategy_algo_order_actions.level`;
  - `strategy_steps` ссылается на транш; шаги узкой агрегатной
    поверхности (`EXIT`, `FAIL_SAFE`) — на деталь;
  - `+positions.external_fee`, `+positions.external_funding_cost`,
    `+positions.external_liquidation_penalty` — `numeric(36,18)`,
    nullable: правые операнды второй, третьей и четвёртой пар сверки
    (заведены `GAPS_CLOSE_26`; прежде маппинг их адресовал, а модель не
    несла — `docs/models/domain/core/Position.md`);
  - `+exchanges.risk_base` `numeric(36,18)`, `+exchanges.risk_base_currency`
    `varchar(64)`, `+exchanges.consecutive_loss_count` `int NOT NULL
    DEFAULT 0` — носители базы риска и серии убытков
    (`docs/rules/risk-policy.md`, `docs/rules/loss-streak-halt.md`);
  - `deals` сверх перечисленного выше не меняются: слот, окно линковки,
    результат, признаки отбора, четыре числа риска, эпизоды — агрегатные
    и остаются как есть.
- **Статусные енумы:** `Deal.Status` → `ACTIVE`, `EXIT_PENDING`,
  `CLOSED`, `ERROR`, `EMERGENCY_CLOSED`; новый `DealTranche.Status` —
  семь значений без ошибочных.
- **FSM:** `DealStateMachine` выбирает из трёх обработчиков сделки
  (`DealActiveHandler`, `DealExitPendingHandler`, `ErrorHandler`);
  `DealTrancheStateMachine` прогоняет шесть обработчиков транша.
  Ребро переоткрытия `MANAGING → ENTRY_SUBMITTED` — с тремя условиями
  (`docs/spec/deal-tranche-lifecycle.json`).
- **Экспозиция транша** — производная его заявок **и приписанного ему
  закрывающего исполнения уровня сделки** (правило сопоставления — дельта
  `GAPS_CLOSE_28` ниже); **сверка Σ экспозиций
  с `Position.externalSize`** в входных проверках активной сделки и в
  `AnomalyJob`, реакция — существующая лестница.
- **Преконтроль:** операнд покрытия траншевый, операнды потолков —
  по всей сделке.
- **Валидация create:** транши (≥1 у торгуемой детали, одно входное
  объявление, признак переоткрытия, согласованность `levelCount` /
  `levelStep`), узкая агрегатная поверхность
  (`STRATEGY_DEAL_LEVEL_STEP_OUT_OF_SCOPE`), `N_overlap` = Σ
  `levelCount`.
- **Эталон** `strategy-examples/trend-following-ema.json` уже в новой
  форме — api-модель обязана её принимать.

**Названные цены, принятые держателем** (перебор вариантов и цена —
`.claude/work/history/2026-08-29-step-7-docs-check-loop/`): потраншевый `R` из
фактов нетто-режима не выводится; внешнее частичное сокращение позиции
становится громким (останов вместо молчаливого поглощения).

### CODE-дельта `GAPS_CLOSE_29` (узлы посерийного прохода)

Дом каждой позиции назван; здесь — что писать.

- **Входной гейт полноты графа у преконтроля** — `RiskValidator` первым
  делом читает признак полноты графа с контекста прохода и при неполном
  отказывает **fail-fast** кодом `DEAL_GRAPH_INCOMPLETE`, до всех
  остальных ветвей. Дом — `docs/components/RiskValidator.md`, форма
  признака — `docs/spec/deal-context-load.json` (`graphComplete`).
  Прежде гейта не было в предписании **вовсе**, при том что на нём уже
  стоя́т два чужих клейма: нота `exposureReconciled`
  (`docs/spec/protection-coverage.json`) объявляет его держащим общий
  гейт, и конструкция симметричного отказа строилась как «по аналогии с
  существующим». Направление ошибки разрешающее: на неполном графе
  операнды потолков занижены, и преконтроль разрешал бы действие,
  которое потолок обязан отвергнуть.
- **`STRATEGY_ACTION_FRACTION_NOT_POSITIVE`** и
  **`STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE`** — реджекты
  `StrategyCreateRequestValidator`: обе доли действия лежат в `(0, 100]`
  (`docs/rules/strategy-validation.md`). Первого кода в предписании тоже
  не было.
- **Схема:** `+deals.entry_market_phase varchar(64)` (nullable — пусто у
  восстановленной сделки); `uk_position_deal_external` пересобрать как
  `(deal_id, external_id, external_created_at)`, частичный
  (`where external_id is not null`).
- **Дискриминатор эпизода — пара** `(externalId, externalCreatedAt)` на
  первой ноге добычи позиции, в сопоставлении записи истории со строкой
  эпизода и в ключе (`docs/models/domain/core/Position.md` §«Адресуемая единица эпизода — пара, а не идентификатор»). Запрос истории адресует записи инструментом и
  окном, **не** фильтром `posId`.
- **Энфорсеры запрета набора риска в окне сворачивания — ТРИ, и все
  три обязательны** (`docs/rules/exit-teardown-order.md` §«У запрета
  названы энфорсеры — по статусу транша, поимённо»):
  - `TranchePrecheckHandler` получает операнд статуса сделки и четвёртую
    ветвь исхода условия — расчёт, преконтроль и отправка входной заявки
    не запускаются; форма — `docs/spec/deal-tranche-lifecycle.json`
    (`riskCreatingUnderCollapse`);
  - `TrancheManagingHandler` не применяет ветвь переоткрытия по нулевой
    экспозиции: экспозиция обнулена приписанным закрытием уровня сделки,
    и транш уходит в свой выход;
  - `RiskValidator` отвергает **любое** risk-creating действие в окне
    сворачивания — `RISK_CREATING_UNDER_COLLAPSE`, новое значение
    `RiskCheckCode`; форма — `docs/spec/risk-limits.json`
    (`riskCreatingUnderCollapseRejected`). Первые два стоя́т на статусном
    ребре и действие, статус не двигающее (добор, замещение с
    увеличением), не гейтят вовсе. **Реакция на реджект — карв-аут:**
    действие не исполняется, ребра в `ERROR` нет; дом реакции —
    `docs/processes/risk-evaluation.md` §«Карв-аут исчерпанного бюджета
    сделки».
- **Ступень реакции резолвится покрытием, радиус — типом исключения.**
  Выделенный обработчик оркестратора, поймавший контролируемое исключение
  интеграции либо исчерпание бюджета попыток, собирает пару «радиус ×
  ступень» так: **радиус** — по типу исключения (как сейчас), **ступень**
  — по признаку покрытия, выбираемому **по уровню отказавшего
  исполнения** — таблица дома `docs/rules/instrument-hold.md` §«Операнд
  покрытия выбирается по УРОВНЮ отказавшего исполнения, и это не
  косметика» (потраншевое —
  траншевый признак; системное уровня сделки, у которого `dealTrancheId`
  пуст, — агрегатный; обе величины — `docs/spec/protection-coverage.json`;
  имя операнда здесь не воспроизводится — дом ветвит, копия ветвление
  теряла и уводила системное исполнение в биржевую ступень 2 на покрытом
  риске). Безусловной
  полной формы у триггера исчерпания больше нет; при нарушенном покрытии
  реакцию поднимает не он, а инвариант покрытия. Контракт сигнала —
  `docs/components/HoldService.md`.
- **Энфорсмент жёсткой ступени — оба радиуса**: шаг 2 прохода
  оркестратора читает и статус инструмента, и статус биржи
  (`docs/rules/error-handling-policy.md` §«Жёсткая ступень энфорсится
  непрерывно, а не одним ходом»).
- **Идемпотентность отчёта отдельна от анкера реакции**: строку
  `HoldService` при поглощении не пропускает; дедуп отчёта-состояния — по
  стоящему состоянию объекта, не по статусу отчёта.
- **Признаки отбора не пересчитываются**, если число финализировано
  (`resultProfitFinalized`), — у аварийного терминала; счётчик серии
  убытков считает по **непустоте** записанного числа, а не по
  вычислимости итога на проходе.
- **Фаза рынка в проходе сделки**: `DealContextService` кладёт фазу в
  `DealContext`; `DealOpeningService` пишет `Deal.entryMarketPhase`
  write-once; `StrategyConditionEvaluator` реализует `MARKET_PHASE_IS` и
  `TREND_CHANGED` по `docs/spec/market-phase-condition.json` (сегодня оба
  падают в `default -> false`).

  **Первый блокер закрыт: привязка к идентичностям вычисления есть.**
  В сервисной форме фазу считает `market-data` по клаузам потребителя, а
  соответствие «авторское имя операнда → идентичность вычисления» держит
  потребитель (`docs/architecture/market-data-collection.md` §«Фаза:
  клаузы приезжают операндом вызова»). В копии у `trading-core` такой
  привязки не было ни одной колонкой; её ввёл `CODE`-заход 2026-09-06
  (`.claude/work/code-gate-ledger.json`, компонент «объявление потребности
  стратегии у market-data: идентичности вычисления в копии»).

  **Что оживит остаток — читатель фазы.** Фазу потребляют условия шагов
  `MARKET_PHASE_IS` и `TREND_CHANGED`; они приезжают с обработчиками FSM и
  там же реализуются. До них сборка контекста прохода фазу не кладёт:
  поле без читателя (`.claude/work/code-gate-ledger.json`, компонент «FSM
  сделки и её обработчики»).
- **Судьба встроенной защиты по фактам родителя**: исход выводится из
  пары «терминален ли родитель» + «есть ли налив», а **не** из присутствия
  элемента в снапшоте — величины `attachedParentClass` и
  `attachedOutcomeByParent`; ненайденность после цикла добычи разбирает
  вторая ступень `searchExhaustedOutcome`. Матч живой записи — по
  **клиентскому** идентификатору. Действующий резолвер
  (`OkxAttachedAlgoOrderStateResolver`) исполняет снятую редакцию
  (`nonNull(snapshot) → ACTIVE`, поиск исхода только при `snapshot == null`)
  и переписывается целиком. Дом — `docs/lifecycles/Order.md`.
- **Однократность шага стратегии на эпизод транша — остался писатель
  `+1`.** Три колонки заведены (`deal_tranches.episode_seq` V11,
  `tranche_episode_seq` у обеих таблиц строк исполнения V22), признак
  применённости стои́т на исчерпании пакета, отбор строки идёт номером
  эпизода. Не построен **инкремент**: `1` пишет материализатор транша
  обеими тропами, а `+1` пишет тот, кто гейтит **ребро переоткрытия**, —
  и самой ветви переоткрытия по нулевой экспозиции в обработчике
  сопровождения нет (`docs/spec/deal-tranche-lifecycle.json`, три условия
  ребра). Пока ветви нет, эпизод у транша всегда первый, и различение
  эпизодов работает вырожденно — не ошибочно, но и не проверено ни одним
  переоткрытием.
- **Отказ расчёта по стороне уровня** — новый код контролируемой ошибки
  `STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING`, тип `PERMANENT`, но реакция —
  **отказ шага**: строка исполнения уходит в отказ, сделка в ошибочное
  состояние **не** уводится. Развязка — `docs/processes/risk-evaluation.md`;
  форма отказа сайзинга — `docs/spec/order-sizing.json`, величина
  `perContractRisk`.
- **Цикл добычи материализованной защиты — реализация.**
  `RefreshOrderExecutor` на исходе `SEARCH_MORE` запускает **второй**
  цикл (не пятую ногу первого); гейт запуска — **терминальный родитель**
  (`TERMINAL_FILLED` / `TERMINAL_FILL_UNKNOWN`), не всякий `SEARCH_MORE`.
  Состав: **нога живых** (`GET /api/v5/trade/orders-algo-pending`) →
  пуста ⇒ **вторая ступень** (`searchExhaustedOutcome`:
  `PROTECTION_LOST` — терминал сразу, разбор истории не запускается;
  `ANALYSE_HISTORY` — запуск разбора) → **разбор истории тремя
  вызовами** `GET /api/v5/trade/orders-algo-history` (`instType`,
  `instId`, `ordType=conditional`; `state=effective`, `state=canceled`,
  `state=order_failed` — три терминальных `state` контракта; `state`
  либо `algoId` обязателен, `algoId` материализованной записи неизвестен
  по построению). Матч по `algoClOrdId` в **ответе** — фильтра по
  клиентскому идентификатору у эндпоинтов нет; глубина — пагинацией
  `after`/`limit`, окна у эндпоинта нет. Исходы найденной записи и
  пустого разбора — таблица разбора дома (`docs/lifecycles/Order.md`
  §«Исход ненайденности — вторая ступень»; формула здесь не
  воспроизводится). `MISSING_AFTER_REFRESH` цикл не производит.
  Найденный `AlgoOrderOkxResponse` маппится в
  `AttachedAlgoOrderExternalSnapshot`
  (поля — `docs/models/mapping/Order.md`), в инвентарь источника
  добавляется `sz`; в снапшот добавляется поле `externalStatus` (сырой
  `state` записи — **диагностика**, исход кодирует нога). Вход
  `AttachedAlgoOrderStateResolver` становится двухместным; живость в
  обеих тропах предъявления выводит `attachedBecomesActive`
  (предъявленная запись — второй дизъюнкт,
  `docs/spec/order-lifecycle.json`), терминал по найденному факту —
  через активацию тем же наблюдением.
- **Уровень на всю позицию отказывает вычислением**, если транш с
  экспозицией не несёт своего уровня (`docs/spec/protection-coverage.json`,
  `dealStopUnresolved`).
- **Снять значения без производителя:** `Position.CloseReason.MANUAL_CLOSE`,
  `Deal.CloseReason.MANUAL_CLOSE` (решение держателя) и `MANUAL_CANCEL` у
  `Order`/`AlgoOrder`/`AttachedAlgoOrder`; `UNKNOWN_EXTERNAL_STATUS` в
  `Position.CloseReason` кода не было — сверить и не заводить.
  **Той же правкой — значения, снятые по перечням причин сделки и позиции**
  (`GAPS_CLOSE_31`, узел писателя причины): `Position.CloseReason.UNKNOWN`,
  `Deal.CloseReason.UNKNOWN`, а у `Deal.ShutdownReason` — `MANUAL_STOP` и
  `UNKNOWN`. Перечни в коде шире перечней корпуса; действующие дома —
  `docs/models/domain/aggregate/Deal.md`,
  `docs/models/domain/core/Position.md`. **`RISK_POLICY` и `EXCHANGE_HOLD`
  остаются**, и их писатель в коде уже есть — `DealOrchestratorJob.enforceHold()`
  пишет их на ребре в `ERROR`; правки он не требует.
  **Обратная правка того же перечня — ЗАВЕСТИ значение
  `EXTERNAL_CLOSE`** в `Deal.CloseReason` (енум общий с траншем): клетка
  ребра `EXIT_PENDING → CLOSED` без инициатора выхода закрывается им, а
  писатель — `TrancheExitPendingHandler` на тропе нулевой экспозиции без
  инициатора (`docs/lifecycles/DealTranche.md`). Значение **старшее** в
  порядке старшинства причин траншей, и резолв старшинства в коде обязан
  это отразить (`docs/lifecycles/Deal.md` §«Причина закрытия — значение по
  маршруту»; исполнимая форма — `docs/spec/deal-lifecycle.json`, величины
  `trancheCloseReasonRank` и `dealCloseReasonBySeniority`).
  **Вместе с константами снять их и из javadoc трёх payload'ов** —
  `ClosePositionCommandPayload`, `CancelOrderCommandPayload`,
  `CancelAlgoOrderCommandPayload` перечисляют снятые значения законными
  причинами. Javadoc переживает удаление константы молча и остаётся
  единственным носителем снятого значения.


---

### CODE-дельта шага

**Форма позиции: имя величины + указатель на дом; формула здесь не
воспроизводится** (F14 `DOCS_CHECK_28`, крен C). Позиция говорит
исполнителю **что сделать** и **где правило**; сама формула, предикат,
перечень значений и состав колонок живут в доме и читаются оттуда.
Пересказ политики в этой секции — дефект, а не удобство: он стареет
первым, а адресован исполнителю следующего под-шага
(`.claude/rules/closed-work-transfer.md` §«Диета рабочих файлов»,
`.claude/rules/policy-home.md`).

Класс, на котором форма записана: семь позиций несли снятые или
несуществующие редакции (база риска, читаемая на ходу; снятое деление баз
кумулятивного; отменённое слагаемое `incurredCanceled`; агрегатный
операнд покрытия вместо траншевого; ключ без транша, запрещавший законное
состояние; три несуществующих имени колонок; указатель в файл без
клаузы), причём **четыре из семи** стояли рядом со своей актуальной
формой в том же файле — то есть точечная правка уже применялась и класс
её пережил.

**Дельта кодового захода 2 шага 7** (закрытия B10 и F4 прогона `_34`;
дома политик названы, пересказ здесь не ведётся):

- **`stepRetryGated` — гейт повтора по СТОЯЩЕЙ ступени, а не «после
  `FAILED` новая строка законна».** Дельта воспроизводила снятую прозу
  предиката: шаг с `FAILED`-строкой снова становился eligible без гейта.
  Дом — `docs/rules/strategy-step-once-per-episode.md` §«Надобность после
  исчерпания бюджета — гейт по стоящей ступени», исполнимая форма —
  `docs/spec/strategy-walkthrough.json` §`stepRetryGated`. **Область —
  оба уровня шага** (потраншевый и агрегатный): дом §«Область признака —
  эпизод объекта шага».
- **`+RiskCheckCode.INSTRUMENT_SAFETY_HOLD` и его карв-аут.** В дельте
  отсутствовал вовсе, при том что пять братьев-кодов названы поимённо.
  Дом кода — `docs/components/models/RiskCheckResult.md`; дом реакции и
  карв-аута — `docs/rules/instrument-hold.md`.
- **Снять `@NotNull` с `distancePercents`** в api-модели настроек стопа:
  аннотация срабатывает раньше валидатора и делает **эталон репозитория**
  (`BREAKEVEN` без доли) непринимаемым. Обязательность переносится в
  кастомный валидатор по типу расчёта, `@Schema` дополняется. Дом правила
  — `docs/rules/strategy-validation.md`.
- **`+STRATEGY_ACTION_ALLOCATION_NOT_DECLARED`** и `@NotNull` на
  `allocationPercents` входного действия; снять `coalesce`-чтение пустоты
  в сумме объявленного нотинала. Дом —
  `docs/rules/strategy-validation.md`, счётчик —
  `docs/spec/strategy-reference.json` §`allocationNotDeclared`.
- **`+STRATEGY_STEP_ACTIONS_EMPTY`** и снятие `@NotEmpty` с `actions`
  api-модели шага: пустой пакет законен **у шага `EXIT`** (вторая форма
  полного выхода) и незаконен у прочих типов. Дом — там же.

**Дельта `GAPS_CLOSE_27`, узел Н5** (дома политик названы, пересказ здесь
не ведётся):

- **Гейт терминала — `riskProvenAbsent` из `docs/spec/deal-lifecycle.json`
  целиком**, не двумя конъюнктами: живая заявка любого транша (шире
  входного множества), живая условная заявка любого транша и сходимость
  суммы экспозиций с нетто-размером входят в предикат наравне с позицией
  и `dealRiskBearing`.
- **Пятый признак живого риска — «неизвестная живая сущность на бирже» —
  операндом терминального прохода не резолвится**: добыча ходит по
  известным сущностям сделки. Его носитель — инструмент-скоупный сбор
  чистоты инструмента (`docs/components/TranchePrecheckHandler.md`), и
  сегодня у сбора один названный потребитель — предвходовая проверка.
  **Названное ограничение с условием выхода:** распространить сбор на
  терминальный проход (или явно оставить остаток поиску нарушений
  инвариантов) — решается на шаге, вводящем поиск нарушений инвариантов;
  до тех пор гейт терминала на этот признак не смотрит, и это записано в
  `docs/lifecycles/Deal.md` §«Живой риск», а не подразумевается.
- **Ребро `PLANNED → COMPLETED` строки исполнения** для звеньев трёх
  действий финализации (`docs/lifecycles/DealActionState.md` §«Локальное
  звено завершается прямым ребром»). В коде сегодня иначе:
  `DealActionStateStatus` этого ребра не знает, а финализация обходит
  проблему **отдельным** енумом `DealFinalizationStateStatus`
  (`PENDING → COMPLETED`), которого в корпусе нет; его javadoc ссылается
  на несуществующий `docs/lifecycles/DealFinalizationState.md`. Второй
  носитель прогресса исполнения снимается, тропа переезжает на общее
  ребро.
- **`+RiskCheckCode.INSTRUMENT_SETTLE_CURRENCY_MISSING`** (fail-fast,
  `docs/components/RiskValidator.md` §Проверки; дом перечня —
  `docs/components/models/RiskCheckResult.md`) и **выборка входа
  отфильтровывает инструмент с пустой `externalSettlementCurrency`** —
  дом правила `docs/models/domain/core/Instrument.md` §«Инструмент без
  расчётной валюты не торгуется».
- **Аварийный терминал пишет все четыре признака отбора**, каждый по
  своему предикату (`docs/components/MarkDealEmergencyClosedExecutor.md`
  §«Признаки отбора»). **Веток числа три, не две**, и первая из них —
  «число уже стои́т на сделке → **не писать ничего**»: пересчёт и
  затирание запрещены. Ключ остальных двух — `resultAvailable`, а не
  добытость положений закрытия. Дом всех трёх веток —
  `docs/components/MarkDealEmergencyClosedExecutor.md` §«Число —
  best-effort по доступности, не по составу»; порядок веток и их условия
  читаются оттуда, здесь не пересказываются.
- **Операнд `episodes[].exitAt` в `docs/spec/deal-context-load.json` назван
  по несуществующему полю — снять.** Носитель: `exitAt` / `exit_at` не
  встречается ни в `docs/models/**`, ни в `donor/src/**` (греп — ноль вхождений);
  единственное место в корпусе — сама спека (`:11` глосса, `:175`, `:181`
  `notNull(exitAt)`, значения-времена в примерах). Дефект двойной: имя без
  поля **и** тип вразрез с собственной глоссой — глосса объявляет предикат
  «положение закрытия эпизода добыто», значения задают время. **Что
  сделать:** имя привести к `closeRecordFetched` (дом имени —
  `docs/spec/deal-result.json`, носитель предиката —
  `docs/models/domain/core/Position.md` §Персистентность), тип — к булеву
  признаку (`notNull(exitAt)` → `closeRecordFetched`), примеры пересобрать.
  Если спеке нужно именно **время** (окно линковки), это отдельный операнд
  с другим именем, и заводится он в модели, а не в спеке. **Адресаты:**
  владелец `deal-context-load.json` (узел Н1) и владелец
  `docs/spec/pnl-reconciliation.json` (узел Н4, операнд
  `duty.closeRecordsFetchedForAllEpisodes` — то же имя третьей редакцией).
  Разбор — `.claude/work/history/2026-09-03-phase-1-step-7-deals-and-pnl/phase-1-step-7-gaps-close-27/node-H5.md`
  §«Находка: операнд, названный по несуществующему полю».
- **Причина отмены не бывает пустой:** `closeReasonCandidate` замыкает
  ветку `CANCELED` значением `UNKNOWN`, когда наше намерение не
  резолвится (`docs/spec/external-status-resolution.json`); `refusalReason`
  сужен конъюнктом `entity == 'ALGO_ORDER'` — обычная заявка получает
  только `UNKNOWN_EXTERNAL_STATUS`.

**Дельта `GAPS_CLOSE_26`** (заведена закрытием прогона `_26`; дома
политики названы, пересказ здесь не ведётся):

- **Итог сделки — по `docs/spec/deal-result.json`** (состав и предикаты
  здесь не пересказываются; дом —
  `docs/models/domain/aggregate/Deal.md` §«Итоговый результат сделки»).
  Что обязано попасть в код помимо формулы: доступность итога требует
  **трёх** конъюнктов (записи закрытия, добытая и целиком предъявленная
  разбивка, отсутствие строки без курса); область **блокировки** по курсу
  шире области слагаемого на корзину `OTHER`; недоступность различает два
  исхода по бюджету попыток добычи (`waitContinues` /
  `errorPathRequired`). Три новые колонки `positions` (см. схему выше) —
  правые операнды пар сверки.
- **Композиция четырёх пар сверки — по
  `docs/spec/pnl-reconciliation.json`:** левая сторона — суммы за вычетом
  комиссионной компоненты строки (не суммы `amount`), комиссионная пара
  собирается по всей области сверки; правая суммами по эпизодам,
  конвенция знака сырая (финансирование де-нормализуется). Множество типов
  ноги входа берётся через `includes` у дома `protection-coverage`
  §`isEntryOrder` (подключён напрямую: `includes` нетранзитивен).
- **Курс чужой валюты пишет `RefreshBillsExecutor`** той же транзакцией,
  что и строку: лестница огрубления разрешения и догон курса —
  `docs/components/RefreshBillsExecutor.md`.
- **Лестница защиты считается от экспозиции транша**
  (`docs/spec/order-sizing.json`, операнд `ladder.trancheExposure`), не
  от нетто-размера позиции.
- **Резолв действующего уровня защиты** — `trancheStopCurrent` и
  `stopCurrentLive` в `docs/spec/protection-coverage.json`; потребители —
  четыре числа риска и два потолка.
- **Транш входит в частичный ключ системных исполнений** для
  `FINALIZE_DEAL_ENTRY_ACTION` (`deal_id`, `deal_tranche_id`,
  `system_action_type`); три остальных типа агрегатные.
- **Писатель `Order.positionId` — `RefreshPositionExecutor`** в
  транзакции материализации эпизода (не рефреш заявки при первом филле).
- **База риска** — `ExchangeAccount.riskBase` / `riskBaseCurrency`: **первое
  значение пишет рефреш баланса** той же транзакцией, что приземляет
  снимок средств, и только на строго положительном остатке; далее её
  двигают **оба терминальных исполнителя** (`MarkDealClosedExecutor`,
  `MarkDealEmergencyClosedExecutor`) после коммита терминала, **в обе
  стороны** присваиванием остатка — финализация выхода писателем базы
  **не** является; все четыре потолка считаются от снимка на сделке.
  Пустая база даёт отказ, а не ноль, и ненаблюдение отличимо в данных —
  формы и коды в домах (`docs/rules/risk-policy.md`,
  `docs/spec/risk-limits.json`,
  `docs/components/RefreshBalanceExecutor.md`). Колонки `risk_base` /
  `risk_base_currency` вводятся этой же schema-дельтой (см. выше) —
  строк в таблицах нет, бэкфилл не требуется
  (`.claude/rules/pre-launch-schema-changes.md`).
- **Энфорсер П1** — счётчик `ExchangeAccount.consecutiveLossCount`, конфиг
  `globalConsecutiveLossLimit`, реакция мягким холдом счёта. **Операнд
  счётчика — ценовой результат сделки, а не её итог:** накопленное
  финансирование в определение риска не входит, и carry-доход серию не
  обнуляет. Пишут счётчик **оба терминальных исполнителя** своей
  транзакцией, они же собирают операнд из уже добытых чисел — итога
  сделки и накопленного финансирования её эпизодов; отдельного поля под
  ценовой результат не заводится. Форма —
  `docs/spec/loss-streak-halt.json`, политика —
  `docs/rules/loss-streak-halt.md`.
- **Валидация создания стратегии:** защитная условная заявка только с
  базой срабатывания `MARK` — реджект
  `STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK`
  (`docs/rules/strategy-validation.md`).
- **Входная проверка транша** не проверяет «нет активной позиции и
  сделки» — она проверяет отсутствие чужого живого риска
  (`docs/components/TranchePrecheckHandler.md`).

**Гигиена, заведённая прогоном `_26`** (не гейтит `CODE`, чинится по
ходу):

- **27 битых входящих ссылок из `src/`** на снятый каталог
  `docs/decisions/` (15 различных доков, 44 файла Java с упоминаниями) —
  javadoc указывает в несуществующее место. Правится при касании файла в
  `CODE`; отдельным заходом переписывать не требуется.
- **101 вхождение процессной арматуры в 41 доке** `docs/` (номера
  находок, прогонов, шагов роадмапа) — свип курации.

**Многоэпизодная сделка и следствия `GAPS_CLOSE_18` / `_19`** (N4
`DOCS_CHECK_19` — прежде эта половина дельты не имела рабочего носителя,
хотя решение указывало сюда):

- **`Deal.position` → `Deal.positions`** (`List<Position>`) + доменный
  предикат `Deal.livePosition()`; читатели переписываются на него;
- **строка `Position` = эпизод**: смена `posId` на живой ноге закрывает
  строку и заводит новую; нога 2 добывает положение закрытия **для
  каждой** `CLOSED`-строки без него
  (`docs/lifecycles/Position.md` §«Смена эпизода»);
- **`StrategyTranche.positionReopenAllowed`** + гейт в `MANAGING` при
  `false` — наблюдатель и применитель `TrancheManagingHandler`, входная
  классификация `ACTIVE && externalSize == 0` ветвится по параметру
  (`docs/components/TrancheManagingHandler.md` §«Входные проверки»);
- **`Deal.billsFetchedThrough`** — писатель `RefreshBillsExecutor`,
  монотонный `UPDATE`; предикаты завершения bills-звена и обязанности
  сверки переписываются на него;
- **линковка bills при сохранении** (окно открыто сверху до времени
  источника прохода при нетерминальном `Deal`; форма —
  `docs/spec/cash-flow-linkage.json` §`inWindow`);
- **пересчёт четырёх чисел риска — единым методом**, зовомым из точек
  закрытого перечня писателей в доме
  (`docs/models/domain/aggregate/Deal.md`) — перечень здесь не
  дублируется; гейт пересчёта по полноте графа — форма в
  `docs/spec/deal-context-load.json`; знаковая дистанция стопа по
  `Deal.direction`; операнд трейлинга —
  `AlgoOrder.condition.trailing.externalPrice` (B2 `DOCS_CHECK_20`; не
  плоское `externalPrice`, оно несёт `actualPx`);
- **актор предиката неполноты числа** — выходная проверка
  `TrancheExitPendingHandler`; **`SystemActionExecutor` пишет `SKIPPED`** при
  выводе стадии;
- **реакция на `MISMATCHED`** — `HoldSignal.exchange(...)` из
  исполнителей терминального ребра при боевом режиме допуска, с машинным
  кодом **`PNL_RECONCILIATION_MISMATCH`**; **флаг разведочного режима**
  per-exchange и сервисная операция его снятия. Дом кода и состав
  реакции — `docs/rules/pnl-reconciliation.md`.

**Дельта `GAPS_CLOSE_28`** (дома политик названы, пересказ здесь не
ведётся):

- **Резолв торгового исхода закрытия.** Тропа: читает
  `Position.externalCloseType` со строк эпизодов, пишет
  `Deal.closeOutcome`; отображение сырого типа, ветка **непригодного
  операнда** (незнакомое значение и пустое поле ведут в неё одинаково) и
  старшинство исходов на уровне сделки — дом
  `docs/models/mapping/PositionCloseResult.md`, форма —
  `docs/spec/position-close-outcome.json`. Носители — финализация выхода
  и аварийный терминал.
- **Три новых значения `AnomalyReport`-кодов** (дом каждого — там же, где
  живёт его политика; здесь только состав дельты):
  `PNL_RECONCILIATION_MISMATCH` (расхождение сверки),
  `UNRECOGNIZED_CLOSE_TYPE` (операнд торгового исхода **непригоден** —
  и незнакомое значение, и пустое поле на добытой записи ведут в одну
  ветку), `RISK_BENCHMARK_MISSING` (недоступен знаменатель
  R-мультипликатора). Четвёртый, `RESULT_CURRENCY_MISMATCH`, уже
  зарегистрирован ниже; пятый — `RESULT_CURRENCY_UNVERIFIABLE`: контроль
  валюты результата **не проведён**, потому что операнд пуст (дом —
  `docs/rules/pnl-reconciliation.md` §«Проверка валюты чисел записей
  закрытия»). Он и `RESULT_CURRENCY_MISMATCH` — **разные** исходы:
  «сравнили, разошлось» и «сравнивать не с чем», и без второго молчание
  контроля неотличимо от его прохождения.
- **Правило сопоставления закрывающего исполнения уровня сделки с
  траншами** (FIFO по возрасту транша) — дом
  `docs/models/domain/aggregate/DealTranche.md`, форма и инварианты —
  `docs/spec/protection-coverage.json`. Порядок **несущий**: ключ —
  `DealTranche.id`, сортировка обязана быть явной и одинаковой на всех
  проходах (порядок выборки строк не годится); довод — в доме.
- **Секция настроек контура на биржу** в конфигурации
  (`application*.yaml` + `@ConfigurationProperties`) — по составу
  таблицы настроек в `docs/models/domain/core/Exchange.md`; состав здесь
  не дублируется.
- **Секция чисел допуска сверки** в той же конфигурации — числа
  **общие**, не per-exchange (на биржу задаётся только режим их
  применения), колонок в схеме не заводится. Состав, писатель, читатель и
  момент — в доме `docs/rules/pnl-reconciliation.md` §Допуск; здесь не
  переписываются.

**Дельта `GAPS_CLOSE_20`** (закрытие `DOCS_CHECK_20`; дома политик —
в ссылках, здесь только «что сделать»):

- **`Deal.coverageProvenThrough` — монотонное вперёд**, не write-once: guard
  `where coverage_proven_through is null or coverage_proven_through < :uTime`
  (`docs/models/domain/aggregate/Deal.md`); предикат завершения `REFRESH_POSITION_COMMAND` —
  **по всем строкам эпизодов**;
- **`GET /public/time` — метод клиента + якорь верхней границы окна
  bills**: `billsFetchedThrough` и `end` запроса берутся из него, не из
  системных часов (`docs/rules/time-utc.md`);
- **дискриминатор броска радиусной реакции — дизъюнкция** «`Deal.status
  = ERROR` или живого риска нет» с явной непустотой коллекции
  `positions` (`docs/components/ServiceCommandExecutor.md`); **`POST_MORTEM_HARVEST_EXHAUSTED` не
  вводится**;
- **ребро `* → ERROR` — карв-аут по природе тропы**: решение ⇒ звено
  `MARK_DEAL_ERROR_COMMAND`, перехват (enforcement холда, оба `catch`
  оркестратора) ⇒ прямая запись петлёй
  (`docs/processes/fsm-execution-layering.md`);
- **`+deals.planned_risk_equity_base`** (write-once, пишет
  `CreateOrderExecutor` из восьмого поля `CreateOrderCommandPayload`);
- **`+orders.position_id`** (FK, write-once, пишет
  **`RefreshPositionExecutor`** той же транзакцией, в которой эпизод
  материализован или наблюдён, — **не** `RefreshOrderExecutor` при
  первом филле ноги. Дом писателя —
  `docs/models/domain/core/Order.md` §«`positionId` — ось эпизода»: на
  открывающем входе момент связи — материализация строки позиции, а не
  филл ноги, и ретроспективно связь невосстановима. Прежняя редакция
  этой строки называла писателя, **запрещённого домом**, и противоречила
  двум другим строкам этого же файла — инструкция была исполнимой и
  неверной, а ошибка неисправимой на месте);
- **три лимита риска внутри уровня «риск на сделку»** (C6
  `DOCS_CHECK_20` + `RISK-Q3-A`; дом —
  `docs/rules/risk-policy.md`):
  - `strategy_details.risk_per_trade_percent` → `risk_per_action_percent`
    (`ALTER RENAME`), `+cumulative_risk_per_deal_multiplier`; правка
    `StrategyDetail` / api-модели / entity и двух
    `strategy-examples/*.json`;
  - **глобальный конфиг** `globalSimultaneousRiskPerDealPercent`
    (`@ConfigurationProperties`, колонки нет, **умолчания нет** — значение
    задаёт держатель при запуске, `docs/rules/risk-policy.md`)
    **+ параметр стратегии**
    `strategySimultaneousRiskPerDealPercent` (колонка есть — см.
    CODE-дельту `GAPS_CLOSE_21` ниже; C9 `DOCS_CHECK_21`);
  - `RiskCheckCode.RISK_PER_TRADE_EXCEEDED` → `RISK_PER_ACTION_EXCEEDED`,
    `+RISK_PER_DEAL_CUMULATIVE_EXCEEDED`,
    `+RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED`,
    `+RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED`; **три** новые
    проверки в `RiskValidator` — операнды считаются по runtime graph,
    носителя остатка не заводить (формулы — дом политики,
    `docs/rules/risk-policy.md`);
  - **статическая проверка на create стратегии**
    (`StrategyCreateRequestValidator`, 400): два правила — вложенность
    потолков и согласованность одновременного риска с максимумом
    стратегии; неравенства и их операнды — дом
    `docs/rules/strategy-validation.md`, форма —
    `docs/spec/strategy-reference.json`;
  - `BLOCKED` по сделочным кодам, включая `DEAL_NOTIONAL_EXCEEDED`,
    **в `ERROR` не уводит**
    (`docs/processes/risk-evaluation.md` §«Карв-аут исчерпанного бюджета
    сделки»);
- **биржевой якорь нижней границы окна bills** (П7-B, вариант (г)):
  `EntryScannerJob` читает `GET /public/time` перед вызовом
  `DealOpeningService`, тот пишет `Deal.externalCreatedAt`; отказ
  эндпоинта ⇒ сделка не создаётся. Суррогат `Deal.createdAt` снят;
- **`+trade_fee_rates.external_fee_level`**, **`+ix_anomaly_report_unfinished_state`**
  (сборка — `docs/rules/pnl-reconciliation.md`);
- **приземление `condition.trailing.externalPrice`** при рефреше —
  `updateFromSnapshot` с `IGNORE`-стратегией null'ов
  (`docs/models/mapping/AlgoOrder.md`);
- **`+attached_algo_orders.trigger_price_type`** и поле
  `AttachedAlgoOrder.triggerPriceType` + `AttachedProtectionPayload` →
  `attachAlgoOrds[*].slTriggerPxType` (C1 `DOCS_CHECK_20`); `RiskValidator`
  учитывает базу стопа в проверке запаса до ликвидации;
- **`stopCurrent` резолвится поногово** (C2 `DOCS_CHECK_20`), комиссионный
  член — по `stopCurrent_i` со ставкой, обращённой из шестёрки (B9);
- **пола `minSz` у reduce-only размера нет** — четыре исхода округления
  (`PARTIAL`, `FULL_BY_FRACTION`, `FULL`, `SKIPPED`) и два журнальных кода
  (`PARTIAL_EXIT_ROUNDED_TO_FULL` у `FULL`, `PARTIAL_EXIT_BELOW_MIN_SIZE` у
  `SKIPPED`; у `PARTIAL` и `FULL_BY_FRACTION` отчёта нет). Носитель исхода —
  `CalculatedSize.exitOutcome`, читатель — per-type `StrategyActionExecutor`
  действия выхода (C5 `DOCS_CHECK_20`, E4 `DOCS_CHECK_27`;
  `docs/components/SizeCalculator.md`,
  `docs/components/models/CalculatedSize.md`, форма —
  `docs/spec/order-sizing.json`);
- **CODE-дельта `GAPS_CLOSE_21`** (дом каждой позиции назван, здесь —
  что писать):
  - **база риска** — величина, её движение и её единственность для всех
    потолков читаются в доме (`docs/rules/risk-policy.md`); здесь —
    только потребители: `SizeCalculator` (аллокация + бюджет),
    `RiskValidator` (все неравенства + `BALANCE_INVALID`, когда величина
    не резолвится), `CreateOrderExecutor` (снимок в
    `Deal.plannedRiskEquityBase`) — C6 `DOCS_CHECK_21`. База —
    **хранимая** величина счёта с названными писателями, а не строка
    `Balance`, читаемая на ходу в момент проверки;
  - **операнды сделочных лимитов считаются в `RiskValidator` по
    runtime graph**, а не читаются из четвёрки. Формы операндов
    (`liveRiskNow`, `dealRiskTaken` и их слагаемые) — в исполнимом доме
    `docs/spec/risk-limits.json`, политика — `docs/rules/risk-policy.md`;
    здесь они не воспроизводятся; слагаемых у `dealRiskTaken` столько,
    сколько объявляет дом — C1-C3 `DOCS_CHECK_21`, F14 `DOCS_CHECK_28`;
  - **четыре неравенства вместо трёх** + новый код
    `RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED` в `RiskCheckCode`.
    База у всех четырёх потолков **одна и та же** — деление баз
    («кумулятивный — от `min(снимок, текущая)`») домом снято
    (`docs/rules/risk-policy.md`); прежняя редакция позиции держала
    снятую формулу (C7; F14 `DOCS_CHECK_28`);
  - **`+strategy_details.strategy_simultaneous_risk_per_deal_percent`**
    (`numeric(36,18)`, nullable) + поле `StrategyDetail` +
    api-модель; **глобальный** максимум — `@ConfigurationProperties`
    (`globalSimultaneousRiskPerDealPercent`; **носитель переехал на строку
    риск-аппетита тенанта** — `GAPS_CLOSE_1` шага 7 фазы 2,
    `docs/rules/risk-policy.md`; в доноре он остаётся
    `RiskAppetiteProperties`, и значение здесь не называется), прежнее
    безмаркерное имя снято (C9);
  - **create-валидация стратегии** — три новых правила
    `StrategyCreateRequestValidator`:
    `STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL`,
    `STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE` (`N_overlap` — по
    структуре пересечений, не по одному шагу; C9) и
    `STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT` — статическая ступень
    запаса нотинала под катастрофическим потолком (решение держателя
    2026-08-30; дом правила — `docs/rules/risk-policy.md` §«Нотинал
    укладывается в потолок с запасом, а не в границу», форма —
    `docs/spec/strategy-reference.json`, `notionalHeadroomSatisfied`);
    число запаса `notionalHeadroomShare = 0.01` — константа правила, не
    поле конфигурации;
  - **вторая точка входа `SystemActionExecutor.reviseLiveExecutions(
    DealContext)`** — ревизия живых SYSTEM-исполнений на проходе
    оркестратора и на терминальном ребре; переход
    `RETRY_PENDING → SKIPPED` в матрице SYSTEM (A3, A14);
  - **выходная проверка `TrancheManagingHandler`** получает конъюнкт
    `positionReopenAllowed` + дизъюнкт «живых входных ног нет» (A4);
  - **`HoldService.hold(...)` на терминальном ребре — после коммита**
    транзакции терминала, обе тропы (A7);
  - **суррогат нижней границы окна** подставляется и в предикат
    линковки, и в операнд признака полноты, не только в запрос (A6);
  - **схема:** `−deal_action_states.target` (jsonb),
    `status varchar(32) → varchar(64)`,
    `RENAME CONSTRAINT fk_deal_action_state_strategy_action →
    fk_deal_strategy_action_state_strategy_action`, три частичных ключа
    с именами, `uk_position_deal_external` — частичный
    (`where external_id is not null`) — B1-B5, B11, B13.
    **Состав ключа переопределён `GAPS_CLOSE_29`:**
    `(deal_id, external_id, external_created_at)`, а не пара — `posId`
    переиспользуется, и пара отвергала бы легитимный второй эпизод
    (дом — `docs/models/domain/core/Position.md` §«Адресуемая единица эпизода — пара, а не идентификатор»). Третья колонка уже существует (аудит), новой
    схемной дельты сверх переопределения ключа не требуется;
  - **омиссионный член epsilon** взвешивается филлами по предикату
    взятого (C11); область суммирования и список исключений — в доме
    (`docs/rules/pnl-reconciliation.md`, форма —
    `docs/spec/pnl-reconciliation.json`), здесь не воспроизводятся (A5).
- **CODE-дельта `GAPS_CLOSE_22`** (дом каждой позиции назван, здесь —
  что писать):
  - **create-валидация стратегии — третье правило**
    `StrategyCreateRequestValidator`: `triggerPriceType ∈ {LAST, INDEX}`
    ⇒ 400 `STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK` (C5, решение держателя;
    дом ограничения — `docs/models/domain/core/AlgoOrder.md` §Енумы,
    правило создания — `docs/rules/strategy-validation.md`). Енум
    `TriggerPriceType` **не сужается**;
  - **предикат пары `protectionRelievedRiskAmount`** получает четвёртый
    конъюнкт `orders.position_id = livePosition().id` — обе половины
    разности по ногам живого эпизода; `incurredRiskAmount` остаётся
    пожизненным (B2);
  - **отображение `type`/`subType` → `CashFlowCategory`** — тот же
    per-exchange `@ConfigurationProperties`, что список исключений, с
    непустым стартовым набором в конфиге по умолчанию; значение вне
    отображения ⇒ `OTHER` + `UNCLASSIFIED_CASH_FLOW` (B1); ключ, порядок
    разрешения и условие выхода из разведочного режима —
    `docs/models/mapping/DealCashFlow.md` §«Как резолвится категория»;
  - **комиссионная компонента строки** берётся по
    `docs/spec/pnl-reconciliation.json` §`flowFeeComponent` — с различителем
    гранулярности (`separateFeeGranularity`), иначе эхо комиссии на
    торговой записи задваивает комиссию; довод и три формы источника —
    `docs/models/mapping/DealCashFlow.md` §«Арифметика левых сторон:
    `amount − externalFee = pnl`»;
    пустота — `docs/rules/absent-value-semantics.md` (B3);
  - **`reviseLiveExecutions(dealContext)` зовётся из трёх точек:**
    `DealOrchestratorJob` (шаг 4 цикла, до handler'а),
    `MarkDealClosedExecutor` и `MarkDealEmergencyClosedExecutor` (своей
    транзакцией терминала) — вызов был назван только в доме (A1);
  - **гигиена комментариев `src/`:** `OkxSigningInterceptor` (2 места) и
    `ICredEmptyCredentialsLiveTest` адресуют `backlog` §I3 — секции нет,
    пункт закрыт и вынесен в `history/` (N6, свип `GAPS_CLOSE_22`).
    Правится на `CODE`; доки этим не затронуты.
- **CODE-дельта `GAPS_CLOSE_23` + `GAPS_CLOSE_24`** (записана одним блоком
  — N1 `DOCS_CHECK_24`: дельта `_23` рабочего носителя не имела вовсе, и
  пакет C1/C3 не был назван ни в одной секции; дома политик — в ссылках,
  здесь только «что сделать»):
  - **инвариант защиты — покрытие, а не наличие.** Политика — дом
    `docs/rules/live-risk-protection.md`; форма предиката, состав
    покрытия по видам защиты и предикат «живой защиты» — исполнимый дом
    `docs/spec/protection-coverage.json`. Здесь не воспроизводятся:
    прежняя редакция позиции держала **агрегатный** операнд
    (`≥ Position.externalSize`) там, где действующий предикат траншевый,
    и **безусловный** перечень `conditionType` там, где действующий
    предикат живой защиты условен по трейлингу (F14 `DOCS_CHECK_28`;
    траншевая редакция записана выше в этом же файле);
  - **`+AlgoOrder.isActiveLike()`** = `{PENDING, ACTIVE,
    PARTIALLY_COMPLETED}` (у `AttachedAlgoOrder` метод уже есть и набор
    у него **другой** — два статуса);
  - **четыре точки проверки покрытия**: `StrategyCreateRequestValidator`
    (статическая проверка объявленных долей на создании; правило —
    `docs/rules/strategy-validation.md`),
    `RiskValidator` ветка weakening (покрытие **после завершения
    ремодела** — предикат и его операнд в исполнимом доме
    `docs/spec/protection-coverage.json`, операнд траншевый; замещаемая
    защита — по `replacesInternalId`),
    выходная проверка `TrancheManagingHandler` (**новая**, четвёртая),
    `TrancheEntryFinalizedHandler` / `TrancheProtectionSwitchedHandler`;
  - **два кода реджекта:** `STRATEGY_PROTECTION_COVERAGE_INCOMPLETE`
    (create, 400) и `PROTECTION_COVERAGE_REDUCED` (`RiskCheckCode`,
    рантайм; в `ERROR` не уводит — карв-аут
    `docs/processes/risk-evaluation.md`);
  - **у create-кода конъюнкта два, не один:** полнота набора защиты **и**
    «шаг, забирающий часть, возвращает не меньше забранного». Второй
    конъюнкт отсекает конфигурацию, которая не покроет риск ни при каком
    рынке (снял ступень на бо́льшую долю, поставил взамен меньшую), и
    отдаёт тот же код. Обе формы и их guard — в доме
    `docs/rules/live-risk-protection.md`, исполнимо —
    `docs/spec/strategy-reference.json` (create-проверка; рантайм-предикат
    снятия живёт отдельно, в `docs/spec/protection-coverage.json`); здесь
    не переписываются;
  - **call-site преконтроля — `CreateAlgoOrderActionExecutor`**: он
    прогоняет `RiskValidator` по ветке weakening (прежний док утверждал
    обратное);
  - **`+CalculationError` `PROTECTION_LADDER_STEP_BELOW_MIN_SIZE`**
    (`PERMANENT`, не retryable) + **остаток последней ступени лестницы**:
    форма — `docs/spec/order-sizing.json` (`lastLadderStepSize`,
    `ladderStepRejected`); операнд **траншевый** — экспозиция транша, а не
    нетто-размер позиции (`docs/components/SizeCalculator.md` §«Защитная
    ступень — другой класс»);
  - **порядок ног entry-`REPLACE` по филлу НЕ ветвится**: входной класс
    идёт cancel-old → подтверждение терминала → place-new при любом
    наливе (`docs/rules/replace-not-amend.md` §«Порядок ног — по
    риск-классу действия»). Прежняя редакция позиции стоя́ла на посылке,
    снятой наблюдением: защита родителя с непустым наливом уходит в
    самостоятельную живую заявку, а не в терминал;
  - **сужение набора неравенств по классу действия** (C3
    `DOCS_CHECK_23`): состав применяемых неравенств, значение «риска
    акта» и пост-действенность `liveRiskNow` для класса `risk-weakening`
    читаются в доме (`docs/rules/risk-validator-scope.md`); счёт здесь
    не воспроизводится — он пересобирается из перечня дома, а прежняя
    редакция позиции называла число («три неравенства»), разошедшееся с
    перечнем (Г2 / П15 `DOCS_CHECK_28`). Что сделать: знать об этом
    сужении обязаны `RiskValidator` и его call-site'ы;
  - **писатель `orders.position_id` — `RefreshPositionExecutor`**, не
    `RefreshOrderExecutor` (A9 `DOCS_CHECK_24`): проставляет ногам с
    непустым `accumulated_fill_size` и пустым `position_id` в момент
    материализации/наблюдения эпизода, write-once;
  - **схема:** `+orders.book_depth_at_placement` (`numeric(36,18)`,
    nullable, write-once — измеритель ёмкости, пишет `CreateOrderExecutor`);
    состав `ALTER` по `orders` пересобирается из перечня схемной дельты
    выше, а не называется числом: с `orders.deal_tranche_id`
    траншевой дельты прежний счёт «девятиколоночный» отстал
    (F16 `DOCS_CHECK_28`);
  - **`+MarketPriceData.externalAskSize` / `externalBidSize`** (маппинг
    `askSz`/`bidSz` тикера) — операнды измерителя выше;
  - **`1 %` — провизорная величина** с семантикой «верхняя граница
    вложенности потолков» (C4 `DOCS_CHECK_23`); поведение кода не
    меняется;
  - **гигиена комментариев `src/`:**
    `CreateAlgoOrderActionExecutor.java:40` несёт снятую клаузу
    «Risk-валидацию не проходит» — привести к действующей редакции
    (валидируется по ветке risk-weakening). Найдено свипом
    `GAPS_CLOSE_24`; линзам `src/`-комментарии в предмет не входили.
- **CODE стадий 1-2 — остаток.** Закодированы (2026-09-02/03): носители
  `PositionsHistoryOkxResponse` / `PositionCloseResultExternalSnapshot`,
  `DealCashFlow` со схемой и маппингом, `REFRESH_BILLS` вместе с
  эмиттером выходной тропы; **`REFRESH_FILLS` снят целиком** (N12 —
  команда, исполнитель, поверхности `IntegrationService`, методы клиента,
  маппер, снапшот и DTO). **Закрыто целиком** заходом по строкам
  исполнения (2026-09-03, позиция хвоста Т13): `MARK_DEAL_EMERGENCY_CLOSED`,
  расчёт и запись `resultProfit` на `Deal` (N7), сверка bills↔net →
  `AnomalyReport` (N10; правило — `docs/rules/pnl-reconciliation.md`).
  Итог — `.claude/work/history/2026-09-03-step-7-code-tail-closures.md`.
- **CODE узла добычи положения закрытия:**
  - `RefreshPositionExecutor` — **вторая нога evidence-cycle**: при
    not-found live-позиции запрос `/account/positions-history`
    **инструментом и временным окном, НЕ фильтром `posId`** — наблюдение
    контура опровергло посылку «один эпизод ↔ один `posId`»: запрос по
    `posId` вернул все записи инструмента, а не одну
    (`.claude/tests/source-api/okx/plan.md` §AG1.9). Адресуемая единица —
    пара `(externalId, externalCreatedAt)`
    (`docs/models/domain/core/Position.md`), и по ней запись
    сопоставляется уже после выборки. Дальше — маппинг в
    `PositionCloseResultExternalSnapshot` и запись полей на `Position`.
    Терминала цикл не выносит; запись не найдена — поля `null`, статус
    `CLOSED` (`docs/components/RefreshPositionExecutor.md`);
  - **ветвь «живой позиции нет и локальной строки нет»** — завести строку
    **закрытого** эпизода по записи истории: без неё у восстановленной
    сделки, чью позицию сняли до первого прохода, эпизодов не появится
    никогда, признак полноты графа ложен навсегда, и сделка не доходит ни
    до одного терминала (`docs/components/RefreshPositionExecutor.md`);
  - `Position`/`PositionEntity` + миграция: **колонки положения
    закрытия** — состав и обязательность читаются в доме
    (`docs/models/domain/core/Position.md` §Персистентность), здесь не
    перечисляются: прежняя редакция позиции называла три имени из
    восьми, и неполный перечень читался как полный
    (F14 `DOCS_CHECK_28`);
  - `REFRESH_POSITIONS_HISTORY` в `ServiceCommandType` **не заводить**
    (целевой состав — 17, `docs/components/models/ServiceCommand.md`);
    handler'ы её не эмитят;
  - финализаторы (`FinalizeDealExitExecutor`,
    `MarkDealEmergencyClosedExecutor`) читают число **со строки
    `Position`**, вложенных команд не исполняют;
  - `RefreshBillsExecutor` — окно `[Deal.billsWindowBegin, t_source]`,
    где `t_source` — `GET /public/time` прохода; линковка **при
    сохранении**, ограничена нетерминальностью `Deal`, а не отметкой
    (**guard «сделка удерживает слот»** — статус вне
    `CLOSED`/`EMERGENCY_CLOSED`); инструмент из `DealContext`
    (`docs/components/RefreshBillsExecutor.md`);
  - `Deal`/`DealEntity` + миграция: колонки `bills_window_begin`,
    `coverage_proven_through`, `bills_fetched_through`, `planned_risk_equity_base`
    — состав и guard'ы держит `docs/models/domain/aggregate/Deal.md`
    §Персистентность, здесь не дублируются; **писатели разные**:
    нижняя граница окна — `SubmitOrderExecutor`; порог доказанного
    покрытия — `RefreshPositionExecutor`, нога 2.
- **CODE R-слота и формулы риска** (дом политики —
  `docs/models/domain/aggregate/Deal.md` §«Четыре числа риска»,
  `docs/rules/risk-policy.md`):
  - **шесть колонок `orders`** (дом планового риска — нога, не
    сделка): `planned_risk_amount`, `planned_risk_currency`,
    `planned_entry_price`, `planned_size_contracts`,
    `planned_contract_value`, `planned_stop_price`; все
    `numeric(36,18)`, валюта — `varchar(64)`; nullable, write-once
    (`updatable = false`), `ALTER` миграцией шага. Инвариант — «шесть
    или ни одной»: производит один преконтроль, пишет одна транзакция.
    Пишет `CreateOrderExecutor` для **входного** действия (предикат —
    прохождение риск-преконтроля, `docs/models/domain/core/Order.md`) одной транзакцией с созданием сущности;
    поле `plannedStopPrice` — также в `CreateOrderCommandPayload`,
    резолв уровня стопа у финализатора переезжает с
    `attachedAlgoOrders` на persisted-число;
  - **на `Deal` — четыре производных числа** + общая
    `plannedRiskCurrency`: `plannedRiskAmount` (риск, принятый сделкой на
    входах: знаменатель R-мультипликатора **и** операнд кумулятивного
    потолка), `incurredRiskAmount` (фактический на входе),
    `currentRiskAmount` (неотработанная доля),
    `protectionRelievedRiskAmount` (снятый защитой). Не write-once,
    пересчитываются **целиком**; формулы и предикат отбора слагаемых —
    дом `docs/spec/deal-risk-numbers.json`, здесь не переписываются;
  - **пересчитывают исполнители закрытого перечня писателей**, каждый
    своей транзакцией и **все четыре числа целиком**; перечень с
    триггерами, исключённые команды и запрет пересчёта на неполном
    графе — место истины `docs/models/domain/aggregate/Deal.md`, здесь
    не дублируется. Отмена — обычной заявки и условной — в перечень не
    входит: она записывает намерение, а не наблюдённый факт;
  - **`+risk_benchmark_availability`** на `deals` (`varchar(64)`, енум
    `AVAILABLE`/`NOT_APPLICABLE`/`MISSING`): значение выбирается **по
    факту входа, а не по тропе** — форма `benchmarkAvailabilityOnTerminal`
    в `docs/spec/deal-lifecycle.json`, писатели по тропам —
    `docs/lifecycles/Deal.md` §«Признаки отбора на рёбрах в терминал»
    (здесь не переписываются). Пишущая транзакция — та же, что ставит
    терминал либо считает число;
  - **`+liquidation_distance_ratio`** на `orders` (`numeric(36,18)`,
    nullable, write-once) — запас до ликвидации на момент постановки
    ноги; измеритель, не контроль;
  - **`Order.Type` получает третье значение `REDUCE_ONLY`** (енум,
    javadoc всех констант, Strategy API `@Schema`, примеры стратегий;
    хранение строкой — миграция значений не нужна,
    `.claude/rules/pre-launch-schema-changes.md`) и **валидацию
    инварианта пары** «бизнес-тип заявки и признак сокращения позиции
    согласованы» в `StrategyCreateRequestValidator` (сейчас не
    проверяется вовсе; форма пары — `docs/models/domain/core/Order.md`
    §Енумы);
  - **предикат «нога входа» переезжает на доменную модель** —
    `Order.isEntryLeg()` (бизнес-тип, `Type ∈ {ENTRY,
    ENTRY_ATTACHED_STOP_LOSS}`); оба call-site
    (`CalculationContextFactory.isEntryType`,
    `DealFsmSupport.entryOrder`) зовут его, инлайновых дизъюнкций по
    `Type` в `src/` не остаётся; исполнимая форма того же предиката —
    `docs/spec/protection-coverage.json` §`isEntryOrder` (там он и
    живёт домом; имя Java-метода — отдельная конвенция слоя);
    в суммах риска — страховочный конъюнкт непустоты `plannedRiskAmount`;
  - **детектирующий контур пары `Type` ↔ `positionReducingOnly`** (Р1):
    сверка пары на ногах сделки в трёх исполнителях пересчёта сумм;
    при расхождении — отказ операции (`VALIDATION_ERROR`), без нового
    кода аномалии (`Deal.md`);
  - **javadoc `RiskValidator.checkRiskCreatingEntryProtection`**
    (`RiskValidator.java:142-148`) привести к принятому: определимый
    стоп — **встроенная attached-защита**, иной механизм формой защиты
    не является (`docs/rules/live-risk-protection.md`
    §Правило);
  - **ремодел основной защиты под увеличенную позицию** (Р3):
    исполнитель `StrategyActionType.REPLACE` под
    `PROTECTION_ADJUSTMENT` (сегодня исполнителя нет) + снятие
    attached SL доборной ноги по подтверждении новой основной
    (`closeReason = SWITCHED_BY_STRATEGY`); триггер — шаг стратегии
    `PROTECTION_ADJUSTMENT` с условием «позиция увеличилась»;
  - **прочая дельта валидаций `GAPS_CLOSE_16`/`_17`:**
    `POST_MORTEM_HARVEST_EXHAUSTED` **не вводится** (снят A3
    `DOCS_CHECK_20` — производителя нет; дом —
    `docs/rules/instrument-hold.md`);
    `RECONCILIATION_OPERAND_MISSING` — код и ветка удаляются; предикат
    неполноты числа уводит терминал на ошибочную тропу
    (`INCOMPLETE_BY_WINDOW` либо cross-ccy-строка без `rateStatus =
    APPLIED`), нового поля нет; конвенция «пусто = 0» несобытийных
    полей записи positions-history — в native-слое, **до** проверки
    обязательности контракта границы; реджект `contractType ≠ LINEAR`
    на тропе заведения инструмента; состав цикла добычи един для всех
    троп (гейт bills — у звена); пустой `billsWindowBegin` ⇒ суррогат
    **`Deal.externalCreatedAt`** внутри исполнителя (П7-B, биржевой
    якорь); операнд `breakdownIncomplete`
    составной — форма и оба его операнда живут в доме
    (`docs/models/domain/aggregate/Deal.md`), здесь не воспроизводятся;
    оба операнда биржевые. Триггер `NOT_ASSESSED` **один** — пустой
    `billsFetchedThrough` (A1 `DOCS_CHECK_23`); состав и довод —
    `docs/models/domain/aggregate/Deal.md`;
  - **`attachedProtection` не доезжает до payload — гейт `CODE`.**
    `CreateOrderExecutor` читает `payload.getAttachedProtection()`, а
    единственный строитель payload'а
    `CreateOrderActionExecutor.createOrderCommand(...)` его **не
    заполняет** ⇒ `Order.attachedAlgoOrders` пуст всегда ⇒ сделка без
    шага `MAIN_PROTECTION` уходит в `ERROR` на **каждом** входе.
    Заполнить payload из `StrategyOrderAction.attachedProtection`.
    ⚠️ **Торговое следствие:** между филлом входа и постановкой
    основной защиты позиция стоит на бирже **без стопа** — при том что
    риск-преконтроль стоп потребовал и по нему сайзил. **Это риск
    денег.** Инвариант `docs/rules/live-risk-protection.md` формально
    соблюдается — он локальный (FSM), а не биржевой; текст правила
    этого не различает — позиция в пакете валидации;
  - **канал доставки плановых чисел — поля
    `CreateOrderCommandPayload`**: ни один существующий RVO метрику не
    несёт; `plannedEntryPrice` **нельзя брать с `Order.price`** — при
    market-входе executor его не заполняет
    (`CreateOrderExecutor.java:65-67`);
  - **тропа выхода по условию/действию стратегии — не построена.**
    Целевая форма — решение держателя: способов объявить выход два, и
    оба законны (условие-переход **и** явное действие шага `EXIT`,
    `docs/rules/no-partial-close.md`); команда закрытия одна —
    `CLOSE_POSITION_COMMAND`, эмитентов два. Дельта:
    - шаг `EXIT` может быть **условие-только** — пустой список
      действий допустим, `TrancheManagingHandler` по истинному условию делает
      переход, валидация перестаёт требовать действия у этого типа
      шага;
    - третье значение `actionKind` — `POSITION`; третий подтип
      `StrategyAction` — `StrategyPositionAction` (`key`, `actionType`,
      `level`); четвёртое значение `StrategyActionType` —
      **`EXIT_ACTION`**; валидация состава действий расширяется на
      новый подтип;
    - **`ExitActionExecutor`** (компонент-док заведён —
      `docs/components/ExitActionExecutor.md`) эмитит
      последовательность команд сам: отмена живых входных
      (не reduce-only) ног → `CLOSE_POSITION_COMMAND`; порядок —
      инвариант `docs/rules/exit-teardown-order.md`; **порядок
      дочистки `TrancheExitPendingHandler`** приводится к тому же инварианту
      (прежняя редакция закрывала позицию первой);
    - в поставляемом примере `trend-following-ema.json` перевод
      `actionType` на `"EXIT_ACTION"` **уже исполнен** (`CLOSE_FULL` в
      файле не встречается; действующая форма — на строках 384 и 627),
      что согласуется с записью выше в этом же файле «эталон уже в новой
      форме»; прежняя редакция позиции предписывала сделанное и называла
      строки, которых нет (F16 `DOCS_CHECK_28`). `actionKind:
      "POSITION"` верен, остаётся; javadoc/комментарий
      `StrategyActionType` («полного закрытия позиции как действия
      нет») снимается;
  - **`StrategyActionType.REPLACE` и `CANCEL` — исполнителей нет**
    (inspection 2026-08-23). `StrategyActionExecutor`-ов два, оба на
    `CREATE`; оркестратор не находит исполнителя ⇒ `ActionPlan.empty()`
    ⇒ действие молча не исполняется. Задет **весь ремодел защиты**
    (`PROTECTION_ADJUSTMENT`) и снятие grid-ног (`GRID_MANAGEMENT`);
    REPLACE — единственная операция ремоделирования
    (`docs/rules/replace-not-amend.md`). Терминал не гейтит —
    гейтит **управление** сделкой;
  - **`TrancheManagingHandler` не наблюдает состояние — сделка не выходит из
    `MANAGING`** (inspection 2026-08-23, **несущий разрыв**). Handler
    не эмитит ни одной `REFRESH_*`-команды, контекст собирается только
    из persistence ⇒ срабатывание SL/TP на бирже локально не
    наблюдается ⇒ перехода в `EXIT_PENDING` нет ⇒ сделка удерживает
    слот бессрочно. Целевая форма — `REFRESH_DEAL_CONTEXT_ACTION`
    (`docs/components/SystemActionExecutor.md`); минимальная —
    `TrancheManagingHandler` эмитит `REFRESH_POSITION` / `REFRESH_ALGO_ORDER`,
    когда продвигать нечего;
  - **`FAIL_SAFE` — значение `StrategyStepType` без потребителя**
    (inspection 2026-08-23): `managingSteps()` перечисляет четыре
    типа, javadoc и `docs/components/TrancheManagingHandler.md` — пять.
    Подключить или снять; клейм полноты в доке ложен в любом случае —
    в пакет валидации;
  - **мёртвые поля `CreateOrderCommandPayload`** — `positionSide`,
    `marginMode`, `executionType` не заполняются и не читаются;
    `strategyDirection`, `instrumentExternalId` заполняются и не
    читаются. Снять или обосновать (`.claude/rules/codestyle.md`
    §«Неиспользуемый код»);
  - **координатные колонки ссылки на свечу курса** —
    `applied_rate_candle_instrument` / `_timeframe` / `_open_time` в
    `deal_cash_flows`;
  - `SizeCalculator` — **закрытая форма** сайзинга; сама форма живёт в
    исполнимом доме (`docs/spec/order-sizing.json`, величина
    `perContractRisk`) и здесь не воспроизводится. Что сделать:
    итеративного подбора и «вычитания комиссии из бюджета» отдельным
    шагом не заводить; `RiskValidator` — та же база нотинала каждой ноги
    (вход по цене входа, выход по цене стопа), чтобы шорты не сайзились
    крупнее.
- **CODE журнальных аномалий** — **закрыт шагом 8** (2026-09-04). Две
  точки входа писателя разведены (`journalState` для состояния,
  `journal` для происшествия), дедуп по ключу состояния построен и несёт
  индекс `V25`. Колонка `kind` **снята как редакция**: природа факта
  известна писателю на call-site и ни одному читателю не нужна (дом —
  `docs/models/domain/other/AnomalyReport.md` §«Природа факта — свойство
  тропы, а не колонка», реестр снятого — `tools/retired-check.py`).
  Расширение `anomaly_reports.scope` до `varchar(64)` отпало вместе с
  групповым радиусом: значений длиннее `INSTRUMENT` у `HoldScope` нет.
  Осталось живым: javadoc `HoldScope` в коде несёт снятые ярлыки уровня —
  переформулировать (scope есть **радиус**, уровень живёт в
  error-политике).
- **CODE cross-ccy** (политика —
  `docs/components/RefreshBillsExecutor.md` §«Догон курса»,
  `docs/rules/pnl-reconciliation.md` §«Ожидание курса чужой валюты»): сравнение
  `ccy` движения с **расчётной валютой инструмента**
  (`Instrument.externalSettlementCurrency`,
  `docs/models/domain/core/Instrument.md`); при несовпадении —
  персист + линковка + курс **из свечи на момент операции** + запись
  `DealCashFlow.appliedRate` и `rateStatus` + `AnomalyReport`;
  слагаемое по применённому курсу считает финализатор (форма — дом
  `docs/spec/deal-result.json`); недоступный курс — пустой
  `appliedRate` со своим статусом, слагаемое не вносится, курс не
  подставляется. **Догон курса — нога цикла добычи, не бессрочный
  долг**: политика ожидания, исчерпания бюджета попыток и реакции живёт
  в доме (`docs/rules/pnl-reconciliation.md` §«Ожидание курса чужой
  валюты») и здесь не воспроизводится. Что сделать: собственного срока
  ожидания, счётчика и детектора не заводить — читать дом.
  ⚠ **Хэнд-офф `integrator` — носитель курса.** Собрать: доступно ли
  секундное разрешение на парах `<CCY>-USDT` и глубина хранения;
  правило деградации (следующий интервал); какая цена берётся из
  свечи; стоимость по квоте и группировка запросов (по-строчный запрос
  упирается в 5 req/s); доступность пар при SWAP-only контуре. Завести
  строку операции в манифесте покрытия.
- **Список исключений сверки по бирже — дом решён, содержание
  открыто.** Носитель — `@ConfigurationProperties` per-exchange
  (непустой стартовый набор в конфиге по умолчанию); содержание — **два
  шага**: рантайм-прогон `AG6.2` даёт персистентный перечень
  **кандидатов** (пары вне окна, `observations/AG6_2.md`), а **состав**
  строит семантическая разметка справочника по кандидатам — шаг
  «кандидаты → состав», писатель — хэнд-офф `integrator`, тем же ходом,
  что отображение категорий п. 16 (`«вне окна» ≠ «вне экономики»`:
  фандинг `8/173`, `8/174` и штраф `5/116` — кандидаты, входящие в
  экономику; контракт слота — `.claude/tests/source-api/okx/plan.md`
  §AG6.2). ⚠ **Непустой список —
  предусловие `CODE`**: при пустом списке контроль целостности числа
  мёртв с первого дня, а на нём стоит вся R-выборка
  (`docs/models/mapping/DealCashFlow.md` §«Область сверки задаётся
  списком исключений по бирже»).
- **CODE fee-wiring (N9):** новая модель **`TradeFeeRate`** + таблица
  `trade_fee_rates` (одна строка на группу; ключ группы — **сырая**
  пара (`external_instrument_type`, `external_fee_group_id`); история:
  значение группы изменилось → новая строка, совпало — инкремент
  `refresh_count` + обновление `external_ts`) + native
  `TradeFeeOkxResponse` + `mapping/TradeFeeRate` (**знак ставки
  снимается здесь, `× −1`** — ниже маппинга ставка есть издержка);
  `externalFeeGroupId` на навесе `InstrumentExternalRules` (**не сама
  ставка**); гидрация ставки — в `InstrumentExternalRulesDataService`
  (обе тропы чтения навеса); синк владельца счёта — второй
  источник `trade-fee`, **один вызов на тик** до цикла, матч
  per-instrument **по паре**, не по голому `groupId`; реджект
  `FEE_RATE_UNAVAILABLE` в `RiskValidator` (только на `null`); холд
  инструментов группы по несвежести — по
  `docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии»
  (`ENTRY_BLOCKED`, без kill-switch, снятие вручную); порог в конфиг,
  стартово 24 ч.
  - **Развилка, которую эта позиция обязана решить:** запрашивает синк
    ступень **через ребро подъёма** (наследуя анкер, эскалацию и факт
    `HoldRaised`) или ставит её сам, мимо ребра. Сегодня применителей
    подъёма ровно один — ребро, — и писателя вне него корпус допускает, но
    ни одного не построил и ни одного не объявил
    (`docs/components/HoldService.md` §«Писатель статуса вне этого перечня
    законен, но в дереве его нет»). Ход мимо ребра берёт на себя
    идемпотентность, эскалацию **и отсутствие факта подъёма** — то есть
    делает подъём невидимым журналу и счётчику подъёмов
    (`docs/rules/statistics-aggregates.md` §«Полнота чисел, складывающих
    обе тропы, условна, и условия у них разные» — там же названа цена:
    подъём выпадет из `raisedHolds`, а заведённый той же тропой отчёт
    останется в `anomalyReports`); ход через ребро этой цены не платит.
    Развилка решается **здесь**, а не в доке холдов: там названо только,
    что она открыта.
- **CODE узла холда** (дом — `docs/rules/instrument-hold.md`,
  `docs/components/HoldService.md`):
  - новый статус **`Instrument.Status.ENTRY_BLOCKED`** (мягкий класс)
    + ручное снятие `ENTRY_BLOCKED → ACTIVE` (сервис/контроллер по
    образцу `InstrumentService.unblockTrade`); `TRADE_BLOCKED`
    остаётся за kill-switch-классом;
  - **гейт пропуска реакции `SafetyHoldCoordinator`** ключуется на
    «scope уже в `TRADE_BLOCKED`», а не «scope не в `ACTIVE`» — иначе
    мягкий холд маскирует последующий kill-switch-триггер;
  - javadoc `Instrument.Status.TRADE_BLOCKED` / `isTradeBlocked()` —
    описывает **kill-switch-класс**; мягкий класс — отдельный предикат
    под `ENTRY_BLOCKED`, `isTradeBlocked()` на него **не расширять**
    (иначе оркестратор уводил бы живые сделки в `ERROR` по
    несвежести);
  - **множества входа safety-статусов:**
    `TRADE_BLOCKED`/`CLOSED`/`ERROR` — из **любого** статуса;
    `ENTRY_BLOCKED` — только из `ACTIVE`
    (`docs/rules/instrument-hold.md`); привести
    охраняемое обновление `InstrumentDataService.blockTrade` (сейчас
    требует `status = 'ACTIVE'` и маскирует реакцию из
    `ENTRY_BLOCKED`);
  - **канал подъёма реакции — строится, и строится первым:** новый
    тип `RetryBudgetExhaustedException`; бросок в
    `ServiceCommandExecutor` **после** перевода строки исполнения в
    `FAILED`; `classify()` перестаёт схлопывать
    `ControlledExchangeException` в `VALIDATION_ERROR`; выделенный
    `catch` в `DealOrchestratorJob` вокруг шага диспетчеризации,
    поимённо по двум типам, **до** общего `catch (RuntimeException)`.
    Снятие прежнего транспорта (`DealTransition.holdSignal`,
    `DealOrchestratorJob.reactToHoldSignal`, `DealFsmSupport`) —
    **только после**: прежний канал в коде жив и работает
    (`docs/components/ServiceCommandExecutor.md` §«Контракт броска»,
    `docs/components/DealOrchestratorJob.md` §«Перехват реакции: выделенный обработчик до общего»);
  - **измеритель свежести ключа группы:** собственных
    `refreshCount`/`confirmedAt` у навеса не заводить; синк на каждом
    успешном чтении `/public/instruments` явно проставляет
    `Instrument.externalModifiedAt` (колонка есть с `V1`, сегодня
    никем не заполняется); возраст метки = возраст ключа группы.
    **Писатель ровно один — синк** (онбординговый `SYNC` метку не
    пишет). `NULL` = «ключ не подтверждён» ⇒ инструмент не попадает в
    entry-скан (предусловие, не холд; снимается первым успешным
    тиком). Бэкфилла нет и `instruments` в schema-дельте нет.
- **CODE-дельта `GAPS_CLOSE_10`** (остальное, сверх пунктов выше):
  - **контурный гейт входа:** `EntryScannerJob`/`DealOpeningService` к
    проверке «нет активной сделки по этому инструменту» добавляют «нет
    активной сделки **ни по одному**» — энфорсмент «в фазе 1 торгуется
    один инструмент». DB-инварианта нет, гонку закрывает
    `JobExecutionGuard`; снимается в фазе 3;
  - **предусловие entry-скана «ключ группы подтверждён»:** инструмент
    с пустым `external_modified_at` в скан не попадает;
  - **валюта результата:** `Deal.resultProfitCurrency` пишется из
    расчётной валюты инструмента; `Position.externalResultCurrency`
    **сверяется** → `RESULT_CURRENCY_MISMATCH` при расхождении (расчёт
    не блокируется); ветка **пустого операнда** — свой, третий исход:
    реджект `INSTRUMENT_SETTLE_CURRENCY_MISSING` в `RiskValidator` (новый
    `RiskCheckCode`; имя `SETTLE_CURRENCY_UNAVAILABLE` занято значением
    `DealCashFlow.RateStatus` и под этим смыслом запрещено —
    `docs/components/models/RiskCheckResult.md`) на входе и `AnomalyReport`
    `RESULT_CURRENCY_UNVERIFIABLE` на записи движения и на финализации;
  - **аварийный терминал считает то же слагаемое:**
    `MarkDealEmergencyClosedExecutor` применяет cross-ccy-слагаемое,
    на биржу не ходит (курс уже на строке);
  - **корзина `OTHER` наблюдаема:** непустой `OTHER` у сделки →
    `AnomalyReport` `UNCLASSIFIED_CASH_FLOW`; область суммирования
    сверки — дом `docs/rules/pnl-reconciliation.md`;
  - **epsilon двухчастный** — форма закрыта, величины провизорны
    (`docs/rules/pnl-reconciliation.md` §«Допуск»);
  - **`Position.externalCloseAveragePrice`** + колонка
    `external_close_average_price`; маппится из `closeAvgPx`
    positions-history; потребитель — калибровка запаса на проскок
    (расчётного потребителя в фазе 1 нет);
  - **колонки ставок `trade_fee_rates` — `varchar(64)`**, не
    `numeric`: доменный тип `String` по решению о **сыром хранении**
    ставки; исключение записано
    (`docs/rules/persistence-representation.md`;
    прежний довод «аксессор сознательно допускает непарсящееся
    значение» снят B3 `DOCS_CHECK_23` — у этой ветки нет производителя,
    граница реджектит непарсящуюся ставку и строку не пишет).
    **Сравнение при записи — численное** для ставок, строковое для
    `level` (B1 `DOCS_CHECK_23`,
    `docs/models/domain/other/TradeFeeRate.md`);
    **все строковые колонки шага — `varchar(64)`** (там же,
    §Персистентность);
  - **состав цикла добычи выводится из `DealContext`**, а не
    передаётся handler'ом; на `Deal.status = ERROR` отказ канала
    добычи расходует бюджет штатно, `FAILED` строки
    `REFRESH_DEAL_CONTEXT_ACTION` — durable-исход «недоступно», он же
    разрешает эмиссию терминала; радиусная реакция не поднимается.
    Контролируемое исключение под это не подпадает: бросается и на
    аварийной тропе, реакция — `Exchange.TRADE_BLOCKED` (ступень 2 +
    flatten) параллельно с ошибочным терминалом
    (`docs/rules/exchange-hold.md`);
  - **`billsWindowBegin` — единственный писатель, безусловно:**
    `SubmitOrderExecutor` пишет `Order.externalCreatedAt` первой
    отправленной ноги всегда при постановке, условным `UPDATE`; ни
    live-нога, ни нога 2 поля не касаются.

- **CODE-дельта катастрофического потолка сделки** (схема ратифицирована
  держателем 2026-08-29; дом — `docs/rules/risk-policy.md`, здесь только
  «что писать»):
  - **`+strategy_details.strategy_catastrophic_risk_per_deal_multiplier`**
    (`numeric(36,18)`, nullable) + поле `StrategyDetail` + api-модель;
    у торгуемой детали обязательно, умолчания нет;
  - **второй параметр риск-аппетита** —
    `globalCatastrophicRiskPerDealMultiplier`, **без умолчания**, рядом с
    `globalSimultaneousRiskPerDealPercent` (в доноре —
    `RiskAppetiteProperties`; **целевой носитель — строка риск-аппетита
    тенанта**, `docs/models/domain/core/Tenant.md`); незаданный отвергает
    **create стратегии**, а не действие;
  - **неравенство катастрофического потолка в `RiskValidator`** (четвёртый
    потолок, пятое неравенство) — форма по дому:
    `docs/spec/risk-limits.json`, величина `withinDealNotional`. Операнд
    слева несёт **три** слагаемых, включая нотинал проверяемого акта
    (`actNotional`; резолв — `CalculatedSize` × `CalculatedPrice` ×
    стоимость контракта, 0 у risk-weakening): без него первый вход
    сравнивал бы с потолком ноль. Код `DEAL_NOTIONAL_EXCEEDED` (в
    `RiskCheckCode` уже есть); отдельной константы кэпа номинала **не
    заводить** — потолок выводится;
  - **create-валидация стратегии — четвёртое правило**
    `StrategyCreateRequestValidator`:
    `STRATEGY_CATASTROPHIC_MULTIPLIER_ABOVE_GLOBAL`
    (`docs/rules/strategy-validation.md`);
  - **эталон уже несёт поле** (`strategy-examples/trend-following-ema.json`),
    api-модель обязана его принимать — вместе со снятием легаси
    `riskPerTradePercent`.

- **CODE-дельта риск-контура, узел Н6 `GAPS_CLOSE_27`** (дома —
  `docs/rules/risk-policy.md`, `docs/spec/stop-distance.json`,
  `docs/spec/deal-risk-numbers.json`; здесь только «что писать»):
  - **`actNotional` в преконтроле**: нотинал проверяемого акта —
    `CalculatedSize` × `CalculatedPrice` × стоимость контракта, 0 у
    risk-weakening; слагаемое левой части `withinDealNotional`
    (см. дельту катастрофического потолка выше);
  - **нотинал живой ноги — по НЕИСПОЛНЕННОЙ доле**
    (`(plannedSizeContracts − accumulatedFillSize) × …`): налитая доля
    уже несётся живым эпизодом, полный плановый размер задваивал бы её
    (`docs/spec/risk-limits.json` §`legNotional`; довод и непересечение
    трёх слагаемых — дом `docs/rules/risk-policy.md` §«Катастрофический
    потолок сделки»);
  - **слагаемые живого эпизода — отдельной ветвью по `hasLiveEpisode`**,
    а не произведением с нулевым/пустым размером: на первом входе эпизода
    нет, и произведение отказывало бы вычислением на каждом первом
    risk-creating действии;
  - **пол дистанции стопа в `RiskValidator`**: накапливаемая проверка
    `STOP_DISTANCE_BELOW_FLOOR` (новое значение `RiskCheckCode`) на любой
    постановке и переносе уровня; форма — `docs/spec/stop-distance.json`
    (`stopDistanceAboveFloor`), пол — `docs/spec/risk-at-stop.json`
    (`stopDistanceFloor`);
  - **предусловие закрытия нетто-экспозиции у обработчика
    координированного выхода**: `CLOSE_POSITION_COMMAND` не эмитится, пока
    `netCloseAllowed` ложно (живые входные ноги траншей сняты И граф
    предъявлен целиком); форма — `docs/spec/deal-lifecycle.json`, дом
    порядка — `docs/rules/exit-teardown-order.md`;
  - **третий триггер перехода транша в выход** у
    `TrancheManagingHandler`: сделка ушла в координированный выход ⇒ транш
    идёт своим выходом независимо от экспозиции. Без него круг: закрытие
    ждёт снятия входных ног, снятие ждёт статуса выхода, статус выхода
    ждёт закрытия (`docs/components/TrancheManagingHandler.md`);
  - **симметричный триггер у `TrancheEntrySubmittedHandler`**: сделка в
    координированном выходе ⇒ транш уходит `ENTRY_SUBMITTED →
    EXIT_PENDING`, живую входную ногу снимает дочистка обработчика
    выхода; нога, уже терминальная без операций, — прямой `→ CLOSED` с
    причиной сделки (дом маршрута —
    `docs/rules/exit-teardown-order.md` §«Окно сворачивания: нового
    риска не берёт ни один транш»; A1 `DOCS_CHECK_33`);
  - **сторона уровня в `RiskValidator` — ОГРАНИЧИТЬ РОЛЬЮ, а не завести
    второй код.** Наличная проверка `STOP_LOSS_INVALID_SIDE`
    (`RiskValidator`, ветвь стороны стопа) отвергает уровень на
    «неверной» стороне **безусловно** и тем блокирует перенос в
    безубыток и трейлинг за безубыток — их прямое назначение. Правка:
    проверка применяется только при `placementRole == 'PRIMARY'`; форма —
    `docs/spec/stop-distance.json` (`primaryStopOnLossSide`). Нового
    значения `RiskCheckCode` **не заводить**: предикат один, и код у него
    один;
  - **охрана знаменателя сайзинга в `SizeCalculator`**: неположительный
    убыток на контракте — не «размер по аллокации», а **отказ расчёта**;
    форма — `docs/spec/order-sizing.json` (`perContractRisk` даёт
    пустоту). Наличный код гасит ветвь возвратом размера по аллокации
    (`stopDistance.signum() <= 0 → вернуть размер по аллокации`) — это
    благоприятное умолчание, и оно снимается: вход без worst-case выхода
    не открывается (`docs/concept.md`, П1 следствие 1);
  - **`LOSS_LIMIT_NOT_CONFIGURED`** — новое значение `RiskCheckCode`,
    **fail-fast** ветвь преконтроля (`docs/components/RiskValidator.md`);
  - **проксирование марк- и индексной цены последней — снять**
    (решение держателя 2026-08-30). В `PriceCalculator.marketPriceBySource`
    ветви `MARK_PRICE` / `INDEX_PRICE` возвращают последнюю цену с
    комментарием «на первом этапе проксируем last» — это подмена ценового
    домена молча. Вместо неё: create-валидация отвергает такие объявления
    (`STRATEGY_PRICE_SOURCE_UNAVAILABLE`, новое значение кода отказа
    создания — `docs/rules/strategy-validation.md`) на **обоих** носителях
    источника цены: у размещения (`StrategyPricePlacement.priceSource`) и
    у **ценового операнда условия** (`StrategyConditionOperand.priceSource`,
    `StrategyCreateRequestValidator`). Сам калькулятор на недостижимом
    источнике **отказывает** контролируемой ошибкой расчёта
    `PRICE_SOURCE_UNAVAILABLE` (`docs/components/models/CalculationError.md`),
    а не подставляет. Предикаты — `docs/spec/strategy-reference.json`,
    величины `priceSourceUnavailable` и `conditionPriceSourceUnavailable`;
    дом правила — `docs/models/domain/aggregate/Strategy.md`
    §«StrategyPricePlacement — правило расчёта цены размещения». Условие
    возврата значений — §«Марк- и индексная цена как источник размещения —
    возврат по поддержке источником» этого файла;
  - **`BREAKEVEN`** — значение `StopLossCalculationType` **уже заведено в
    коде**; остаются: ветвь `PriceCalculator` (уровень по **точной**
    форме из исполнимого дома — `docs/spec/stop-distance.json`, величина
    `breakevenLevel`; формула здесь не воспроизводится, `distancePercents`
    в ней не участвует) и **два** правила create-валидации: «доля
    согласована со способом» и «`BREAKEVEN` только у защитного
    `REPLACE_ACTION` с `targetActionKey`» ⇒
    `STRATEGY_BREAKEVEN_NOT_A_TRANSFER` (`docs/rules/strategy-validation.md`);
  - **якорь уровня — ветвь, а не одна цена**: пока живого эпизода нет —
    `Order.plannedEntryPrice` своей ноги; эпизод есть —
    `Position.externalAverageEntryPrice`; эпизод есть, а средняя не
    наблюдена — якоря нет, уровень и пол отказывают вычислением
    (`docs/spec/stop-distance.json` §`entryAnchor`). Потребители — и
    `PriceCalculator` (расчёт `BREAKEVEN`), и `RiskValidator` (пол);
  - **ставка комиссии требуется всякому действию, ставящему или
    переносящему уровень**, не только risk-creating: `FEE_RATE_UNAVAILABLE`
    выдаётся и на защитном переносе (`docs/components/RiskValidator.md`);
  - **`levelCount` становится обязательным** у объявления транша: схема
    (`not null`), api-модель, `StrategyCreateRequestValidator`; умолчания
    нет (`docs/models/domain/aggregate/Strategy.md`). Эталон поле уже
    несёт;
  - **формула `Deal.plannedRiskAmount` изменилась**: выбывшая входная нога
    входит налитой долей, а не выпадает целиком; пересчёт — те же
    исполнители закрытого перечня дома, формула — дом
    `docs/spec/deal-risk-numbers.json`
    (`dealPlannedRisk`). Кумулятивный потолок берёт это же число, второго
    слагаемого `incurredCanceled` в `RiskValidator` **не заводить**.

- **Числа риска отчётности мерятся от плановой цены входа, а не от
  фактической** — открытый вопрос `RISK-Q1`
  (`.claude/work/questions/open-questions.md`); счёт, направление
  смещения и разбор `feeRate` — там же. Здесь задачи нет, пока вопрос не
  решён: дом величин — `docs/models/domain/aggregate/Deal.md`, форма —
  `docs/spec/deal-risk-numbers.json`.

**Форвард вне шага 7** (`принято-в-работу`, B6 `DOCS_CHECK_20`):
top-level эхо attached-защиты (`attachedAlgoInternalId`,
`stopLossTriggerPrice` снапшота `Order`) в домен не приземляется —
решить, нужен ли ему носитель, или строки снапшота выводятся из состава
(`docs/models/mapping/Order.md` §«`OrderExternalSnapshot` → `Order`»).
Смежно: эхо `slTriggerPxType` не читается вовсе
(`docs/rules/live-risk-protection.md`).

### Грунт `integrator` для шага 7

Собирается, **не дожидаясь чистого прогона** (правило §4
`.claude/processes/roadmap-step-execution.md`). Гейтящие позиции списка
закрыты прогонами 2026-08-30 — 2026-09-03 (оси адресации, семантика
`fundingFee`, инвентарь bill-типов, инвариант агрегации, знаки
операндов, судьба защиты у исполненного родителя, носитель курса
cross-ccy) — итоги в
`.claude/work/history/2026-09-02-env-revival-ground-batch.md` и в
реестре `.claude/tests/source-api/okx/code-preconditions.md`. Живое
(нумерация исходная, пропуски — закрытое):

3. **Семантика `actualPx` алго-ордера**: цена **исполнения** или цена
   **выставления** после триггера — от ответа зависит исполнимость
   операнда калибровки (`docs/models/domain/core/Position.md`).
   Гейта нет (`M15.7` не размечен ни планом, ни реестром).
10. **Наличие `instId`/`instType` в `data[]` positions-history** —
   **гейта нет**: посылка из контракт-дока проекта, не из офдока; при
   отрицательном ответе структурная валидация вырождается в записанное
   ограничение «корректность держит фильтр запроса».

### Рантайм-верификация и форвард

- **N11 — рантайм-верификация инварианта агрегации positions-history**
  (гейтит корректность числа, **до CODE**): партиал-выходы одного
  `posId` → одна финализированная запись, `realizedPnl` кумулятивен.
  Test-план — `.claude/tests/source-api/okx/plan.md` §AG1.5
  (⏳ PENDING; интегратор/тестер: фикстура-цепочка на demo). Если OKX
  не агрегирует — путь корректируется.
- **Рантайм-хвост на той же фикстуре §AG1.5** — **негейтящий**, идёт в
  общем порядке: **H2** гранулярность bills (§AG3.5), **RQ-3** ставка
  группы ↔ фактическая комиссия (§AG12.5). Без фикстуры: **RQ-1**
  покрытие `feeGroup[]` (§AG12.4), **RQ-2** `groupId` непуст (§M1.7).
  **`RQ-4` (`ccy` fee-bills = USDT, §AG3.4) из этого перечня выведен:**
  кейс гейтящий (слот п. 9 реестра) и собирается **сразу**, не дожидаясь
  чистого `DOCS_CHECK` — порядок сбора грунта живёт в
  `.claude/processes/roadmap-step-execution.md` §4, здесь не
  пересказывается (F7 `DOCS_CHECK_27`: прежняя редакция ставила сбор
  грунта строго после прогона и потому загоняла гейтящий кейс в петлю
  «факт ждёт прогона, прогон без факта не проходит»).
- **N13 — funding как holding-cost (форвард, фаза 2 / шаг
  ожидаемости):** в число funding учтён; разделяющий довод «комиссию в
  R, funding в post-cost expectancy» зафиксирован
  (`docs/rules/risk-policy.md`); завести форвард-дом
  на шаге ожидаемости/бэктеста. Scope (фаза 2 vs step-7-adjacent) —
  хвост пользователя.
- **Калибровка epsilon сверки bills↔net (N10):** величины (`0.01`,
  `0.5%`, `k`) провизорны — подтверждение/калибровка:
  пользователь/бэктест. Форма закрыта
  (`docs/rules/pnl-reconciliation.md` §«Допуск»); снятие разведочного
  режима — §«Прод-рубеж — снятие разведочного режима допуска
  сверки». **Гейтом `CODE` `k` не является** (решение держателя
  `GAPS_CLOSE_19`; реестр `.claude/tests/source-api/okx/code-preconditions.md` §«Что в реестр НЕ входит», п. 8): прежняя
  приписка «предусловие `CODE` п. 8 и второе основание `PNL-Q1` п. 3»
  снята — номер 8 в реестре не живёт, а третий пункт `PNL-Q1` закрыт
  `GAPS_CLOSE_19`, в вопросе осталось два (F8 `DOCS_CHECK_27`).
- **H6 — добор недостающего числа на `EMERGENCY_CLOSED` (форвард,
  фаза 2 / шаг ожидаемости):** null = «неисчислимо» — отложенный долг,
  не финальный вердикт; направление принято (добор до истечения окна
  positions-history, ~3 мес), материализация (кто дочитывает, на каком
  такте, что с просроченным окном) — за шагом ожидаемости
  (`docs/rules/pnl-reconciliation.md`). Пропуск
  outcome-коррелирован, drop завышает ожидаемость. Смежное **H17**:
  направление стоит на непроверенном допущении «недоступность обычно
  временна» — если записи не существует в принципе (краевые
  ADL/ликвидационные исходы), добор — no-op; при материализации
  записать: H6 направлением **уменьшается, а не закрывается**
  [Kaufman гл.1, PDF с.110-112].
- **Искажение измеряемой ожидаемости: две оси × две стороны (торговый
  форвард-фокус; владелец — фаза ожидаемости).** Матрица «оси
  (исходы/возможности) × стороны (оптимистично/пессимистично)»,
  механизмы по клеткам; крены на разных осях не компенсируются —
  сравнение бэктест ↔ live двусторонне несопоставимо, мерить одно, не
  зная другого, нельзя:
  - **исходы × оптимистично:** H6 null-drop
    (`docs/rules/pnl-reconciliation.md`), N11 недосчёт агрегации,
    опущенный гэп-проскок (`docs/rules/risk-policy.md`);
  - **исходы × пессимистично:** flatten чужих здоровых сделок при
    `Exchange.TRADE_BLOCKED` (ступень 2) — рыночное закрытие в момент,
    некоррелированный с рынком: правый хвост R усекается, измеряемая
    ожидаемость занижается. Радиус механизма **не сужен**: ревизия
    держателя (`GAPS_CLOSE_18`) вернула controlled-исключения и
    safety-каскад на ступень 2 с flatten — цена названа и принята
    (`docs/rules/exchange-hold.md`);
  - **возможности × пессимистично:** taker-консерватизм при
    maker-входах = систематический недосайзинг
    (`docs/rules/pnl-reconciliation.md`); цена пропуска входа под
    реджектом/холдом оценена в ~0 — корпус против [Tharp гл.6, гл.11;
    Kaufman гл.1]; промо нулевой комиссии не видно в `trade-fee` ⇒
    прогноз завышает издержку ⇒ недосайзинг
    (`docs/integrations/okx/contracts/trade-fee.md` §«Прочие
    ремарки»);
  - **возможности × оптимистично:** окно несвежести ставки
    двусторонне — при понижении тира внутри окна (0-24 ч) прогноз
    комиссии занижен ⇒ позиция больше положенной [Vince гл.1]; лечится
    сокращением порога свежести — калибровка вместе с величиной
    порога.
- **Форвард: авто-снятие мягкого холда по предикату свежести** —
  отложено, не отвергнуто: в фазе 1 снятие ручное (пайплайн в отладке,
  человек разбирает причину). Горизонт пересмотра — установившийся
  режим; тогда же взвесить гистерезис / K подряд успешных чтений.
  Носитель довода — `docs/rules/instrument-hold.md` §Снятие.
- **Вход в market-maker-программу → пересмотр оси запроса
  `trade-fee`** (инвариант organic-base-rates,
  `docs/rules/pnl-reconciliation.md`): запрос без
  `instId`/`instFamily` даёт organic base rates — не тот ответ, если
  аккаунт станет участником программы.
- **`elpMaker` → `rpiMaker`** (прод OKX **2026-07-28**, параллельные
  имена до 2026-10-31): поле **unused**, механики нет по
  `docs/rules/raw-exchange-dto-boundary.md`; переоценка —
  только если поле станет used до конца окна.

