# Backlog

## На какой вопрос отвечает этот файл

Что мы планируем сделать.

## Связь с роадмапом

Продуктовое движение по фазам и шагам ведётся отдельно —
`.claude/work/roadmap/roadmap.md` (главный) и
`.claude/work/roadmap/phase-N.md` (детальные). Когда шаг роадмапа
берётся в работу, через процесс
`.claude/processes/roadmap-step-execution.md` он порождает
конкретные задачи в этот backlog. Backlog шире роадмапа: помимо
шагов фаз сюда попадают cross-cutting миграции из архива,
методологические ревизии и пайплайн-задачи. Обоснование
связи — `.claude/decisions/product-roadmap-type.md`.

Файл держит только живое (правило —
`.claude/rules/closed-work-transfer.md`); итоги закрытого — в
`history/` (последние чистки:
`2026-07-13-backlog-phase-1-closed-cleanup.md`,
`2026-07-14-claude-docs-curation.md`). Нумерация секций — с
пропусками (номера закрытых не переиспользуются). Полные
форвард-заметки миграций — в подпапках `history/`
(`tasks-<сущность>.md` / `tasks-<док>.md`); архивные доки в
`.claude-archive/` не удалены — источник для оставшихся миграций.

## Cross-cutting миграции

### 2. Resolver / mapper / checker компоненты — частично

**Осталось:** `*Mapper` (`OrderMapper`, `PositionMapper`,
`AlgoOrderMapper`, `BalanceContainerMapper`), `BalanceFreshnessChecker`,
`OkxAlgoOrderTypeResolver`, `AttachedAlgoOrderStateResolver`.
**Форвард-заметки:** `2026-05-27-.../tasks-order.md` (ORD-Q2),
`tasks-position.md` (POS-Q2), `tasks-algo-order.md` (ALGO-Q2),
`tasks-balance.md` (BAL-Q6); `2026-05-28-.../tasks-статусы-торговых-сущностей.md`
(Mappers — Решения прохода 2).

### 6. Аудит и история исполнения — частично

**Источник:** `.claude-archive/.../processes/Audit/Аудит и история
исполнения.md`;
`.claude-archive/2026-05-21/docs/deprecated/models/domain/old/TradeFill.md`,
`TradeFillsArchive.md`. Архивный док — рабочий каркас, **выведен из
миграции процессов** (`.claude/decisions/process-materialization-criterion.md`):
модели истории/timeline не спроектированы, ~30 подвопросов.
**Осталось:** модели `ServiceCommandExecutionHistory`, entity history,
timeline, snapshot-формат; ~30 подвопросов. Связано с DEAL-Q1/DEAL-Q2.
Финализация PnL закрыта отдельно
(`docs/decisions/result-profit-source.md`); пофилловый аудит
(`TradeFill`/`TradeFillsArchive`) — вне фазы 1.
**Форвард-заметки:** `2026-05-28-.../tasks-аудит-и-история-исполнения.md`
(§5/§8 подвопросы + Решения прохода 2); `2026-05-27-.../tasks-deal.md`
(DEAL-FW5, FW9), `tasks-balance.md` (BAL-Q7), `tasks-order.md` (ORD-Q7),
`tasks-position.md` (POS-Q5).

### 7. Anomaly / safety / kill-switch — частично

**Осталось:** `ReconciliationJob` (в архиве только название —
live risk после terminal / позиция без active Deal), полный kill-switch
flow (`KillSwitchService`, kill-switch report, after-snapshot,
`Position.CloseReason = KILL_SWITCH`), `TradeRuleValidator`.
**Форвард-заметки:** `2026-05-28-.../tasks-жизненный-цикл-сделки.md`
(ReconciliationJob), `2026-05-27-.../tasks-position.md` (POS-Q7),
`tasks-deal.md` (DEAL-FW7),
`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` (ANOM-Q1…Q3).

### 8. Strategy: enforcement, валидатор, примеры — частично

**Осталось:**
- **runtime-прогон Strategy API** (хвост scope шага 2, PostgreSQL не
  был поднят): миграция `V2` → `POST` `trend-following-ema.json` →
  `GET` → `PUT`-переходы статуса. Выполнить при поднятом PostgreSQL.
- `Strategy API examples.md` (JSON-примеры, тип reference —
  воспроизводить ли как файл знания) — **остаётся открытым**.

Построенное (Strategy API, create-валидатор, «одна реализация») и
scope-решения — `history/2026-06-05-phase-1-step-2-strategy.md`,
`docs/decisions/strategy-materialization-and-validation.md`.
**Форвард-заметки:** `2026-05-27-.../tasks-strategy.md` (STR-FW8, FW9,
FW10), `tasks-deal.md` (DEAL-FW8).

### 9. Exchange модель/lifecycle

**Суть:** полная модель/lifecycle `Exchange` (`HOLD`/`DISABLED` среди
прочих; правило — `docs/rules/exchange-hold.md`), `Instrument`,
`Account`. Сюда же — координация статусов инструмента (онбординговый
`HOLD` × safety-статусы `TRADE_BLOCKED`/`ENTRY_BLOCKED`) и standalone
модель `Instrument` для market-data.

> **`AnomalyReport.Severity` из этого пункта выведен** (H6,
> `GAPS_CLOSE_6`). Прежняя формулировка («CRITICAL → торговля запрещена;
> NON_CRITICAL → после kill-switch может быть разрешена») несла снятую
> политику: severity отвечает **только** на «гоняется ли kill-switch», а
> блокировку торговли задаёт **состав реакции** error-политики и несёт
> статус scope (`docs/models/domain/other/AnomalyReport.md` §Енумы,
> `docs/rules/error-handling-policy.md` §«Перечень scope и реакций»).

> **Множество входа `Exchange.TRADE_BLOCKED` — не пересмотрено** (свип
> `GAPS_CLOSE_7`, H13). Для `Instrument` ратифицировано: `TRADE_BLOCKED` /
> `CLOSED` / `ERROR` достижимы **из любого статуса** (авария застаёт
> сущность в любом состоянии; ограничение входа делает реакцию
> пропускаемой — тот же отказ маскировки, ради которого менялся анкер
> координатора). `docs/models/domain/core/Exchange.md` по-прежнему говорит
> «вход — только из `ACTIVE`»: тот же вопрос для биржи **не обсуждался**,
> решение за владельцем. Взвесить вместе с полным lifecycle `Exchange` и
> разнобоем имён `HOLD`/`TRADE_BLOCKED`.

**Осталось:** полный lifecycle `Exchange`, периферийные статусы
`Instrument` (`HOLD`, `ERROR`-recovery, повторный онбординг,
`CLOSED`), `Account`. Минимальные модели `Instrument`/`Exchange` и
онбординг-путь lifecycle уже материализованы
(`docs/models/domain/core/`, `docs/lifecycles/Instrument.md`);
INSTR-Q1 закрыт
(`docs/decisions/instrument-external-rules-materialization.md`).
Связанный открытый вопрос: ORCH-Q1 (владелец оркестрации онбординга
инструмента и загрузки свечей; ось владения `Instrument.Status`) —
`open-questions.md`.

**Разнобой имён safety-статуса биржи — сводится здесь** (зафиксирован
`GAPS_CLOSE_4` шага 7; не находка шага 7, а унаследованный долг). Три
расходящихся источника:

- **`Exchange.HOLD`** — `docs/rules/exchange-hold.md` (сам носитель
  правила), `docs/rules/external-status-resolution.md`,
  `docs/rules/runtime-error-classification.md`,
  `docs/rules/risk-creating-entry-protection.md`,
  `docs/lifecycles/Order.md`, `docs/lifecycles/AlgoOrder.md`,
  `docs/models/mapping/AlgoOrder.md`, `docs/models/mapping/Balance.md`,
  `docs/components/EntryScannerJob.md`,
  `docs/integrations/okx/rules/reduce-only-invariant.md`.
- **`Exchange.TRADE_BLOCKED`** (аппарат шага 6) —
  `docs/decisions/controlled-violation-exchange-wide-hold.md`,
  `docs/rules/controlled-exchange-exceptions.md`,
  `docs/components/SafetyHoldCoordinator.md`,
  `docs/components/models/HoldSignal.md` (перебакечен `GAPS_CLOSE_5`, H20:
  имени `Exchange.HOLD` файл не содержит — несёт `TRADE_BLOCKED` и
  `HoldScope.EXCHANGE`).
- **Доковый инвентарь был стейл** — `docs/models/domain/core/Exchange.md`
  перечислял `CREATED`, `PENDING`, `ACTIVE`, `CLOSED`, `ERROR` и утверждал,
  что в енуме нет ни `HOLD`, ни `TRADE_BLOCKED`. **В коде `TRADE_BLOCKED`
  есть** (`Exchange.java`, плюс `isTradeBlocked()`); инвентарь
  синхронизирован на `GAPS_CLOSE_6` (H3). То же было по `Instrument`
  (`Instrument.java` несёт `TRADE_BLOCKED` наряду с онбординговым `HOLD`).

Имя в обороте — **`TRADE_BLOCKED`**; **статус материализован в коде**, долг
свёлся к **переименованию `Exchange.HOLD` → `TRADE_BLOCKED` в доках первой
группы**. Делается **одним ходом** с полным lifecycle (весь набор состояний
разом), не точечным переименованием.
**Шаг 7 писателя `Exchange.HOLD` НЕ вводит** — холд по несвежести
ставки комиссии уехал на инструмент (`GAPS_CLOSE_4`;
`docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии»).

**Форвард-заметки:** `2026-05-27-.../tasks-order.md` (ORD-Q5),
`2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md` (ANOM-Q4).

### Отложенные продуктовые вопросы (future)

- **Политика очистки накопленных строк исполнений** (`deal_strategy_action_states`
  **и** `deal_system_action_states` — чистка per-таблица, горизонты видов
  могут различаться; имена приведены к принятой топологии H15
  `DOCS_CHECK_14`) — фаза 3. Строки копятся by design (`COMPLETED` жёстко
  терминален, слот-переиспользования нет — В2.1 развилки «команда ↔
  действие»); ретеншен — цена, явно отложенная за границу фазы
  (`docs/decisions/command-action-boundary.md` §Отложено). **Операнд
  ретеншена есть** — `created_at` появляется вместе с audit-колонками
  обеих таблиц (H15 `DOCS_CHECK_15`), бэкфилл на том горизонте не
  потребуется.
- `linkedOrderExternalIds` — использование для fills/recovery/audit
  (`2026-05-27-.../tasks-algo-order.md` ALGO-Q6).
- Стандарт описания персистентности доменных моделей: формат и
  версионирование jsonb-снимков (`AnomalyReport.internalBefore/After` и
  др.). Шире одной модели.
  (Провенанс — `2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`,
  **архивный** вопрос `ANOM-Q5` той миграции; не путать с одноимённым
  закрытым вопросом шага 7 — H25 `DOCS_CHECK_14`.)

## Закрытие фазы 1 — обязательный пересмотр допущения «таблицы пусты»

**Встречный якорь к `.claude/rules/pre-launch-schema-changes.md`.** Правило
действует «до конца фазы 1», но условие снятия записано **внутри самого
правила** — то есть сработать оно должно в момент, когда правило никто не
открывает. Этот пункт — носитель со стороны **события**; механический
триггер — `.claude/skills/update-roadmap-progress.md` §«Гейт закрытия фазы»
(проверяется при ролляпе фазы в `DONE`).

**Что сделать при закрытии фазы 1 — до перевода фазы в `DONE`:**

1. **Снять правило.** `.claude/rules/pre-launch-schema-changes.md` перестаёт
   действовать: перенести в `history/` либо переписать под новое условие,
   если запуск сдвинулся. Оставленное «на всякий случай» правило опаснее
   отсутствующего — оно продолжает разрешать `ALTER … NOT NULL` без ответа
   на «чем заполняются существующие строки».
2. **Пересмотреть миграции, написанные под допущение.** Свип по миграциям
   фазы 1: каждая `NOT NULL`-колонка, введённая `ALTER`'ом **без**
   `DEFAULT` и без промежуточного nullable-шага, писалась под пустую
   таблицу. Проверить по каждой: (а) таблица действительно осталась пустой
   к моменту применения; (б) если контур запускался раньше срока —
   миграция **падает**, и это надо обнаружить до фазы 2, а не в проде.
   Известные носители на момент записи (`GAPS_CLOSE_12`):
   `anomaly_reports.kind` (H17), валютные колонки `instruments` (H10),
   признаки отбора для отчёта на `deals` (узел F).
3. **Проверить не-схемные допущения того же класса.** Правило снимает не
   только вопрос о колонках: «заведённых инструментов на момент ввода нет»
   (писатель валют, H10) и «сделок, живых на момент ввода, не существует»
   (`Deal.md` §«Ветка "операнд пуст"») — те же рассуждения о популяции,
   которой нет. После запуска они перестают быть верными, а ветки, которые
   на них сослались, — достижимыми.
4. **Записать исход** в history-файл закрытия фазы: что снято, что
   пересмотрено, что осталось верным.

**Почему якорь заведён отдельным пунктом, а не примечанием в правиле.**
Класс дефекта, который прогон шага 7 ловил пять итераций подряд, — «решение
записано, но не доведено до носителя, который читают в нужный момент».
Условие снятия внутри правила — ровно этот класс: его читает тот, кто уже
открыл правило, то есть тот, кому оно и так не нужно.

## Шаг «Безопасность» (Фаза 1, шаг 9) — форвард-материал

Материал, отложенный до шага «Безопасность» роадмапа
(`.claude/work/roadmap/phase-1.md`, шаг 9). Содержание шага
прорабатывается docs-first на самом шаге; здесь — что туда заведомо
идёт.

### S1. Конфигурация секретов через Vault — остаточный хардненинг

Vault-привязка секретов per-profile введена
(`.claude/rules/tech-radar.md`, строка spring-cloud-vault).

**Остаётся на шаг 9 (остаточный хардненинг):** политики/approle вместо
root/dev-token, ротация секретов, unseal/инициализация Vault не в dev-режиме,
вынос Vault-токена из run-config. Auth-инфраструктура (Spring Security) — S2.

### S2. Auth-инфраструктура

Spring Security, `@PreAuthorize`, `SecurityFilterChain`. На этом
шаге **реактивируется** фокус `security-review`
(`.claude/skills/security-review.md`), деактивированный на текущих
шагах.

## Форвард-материал шагов 7 / 8 и фазы 3 (скан интегратора + ревью)

Заметки владельцам шагов, **не действия сейчас**; решения — на самих
шагах. Поле-уровневые контракты кандидатов скана готовы (см.
`docs/integrations/okx/coverage-manifest.md`, прогон 3). Кандидаты,
рассмотренные и не взятые (В-4 batch-write, В-5 STP), — итог в
`history/2026-07-14-claude-docs-curation.md`.

### Риск-преконтроль — остаточные кандидаты (вернуться по наблюдениям)

- **В-2 `order-precheck`** — серверная пре-валидация ордера
  (`contracts/order-precheck.md`). ⚠ Ограничение офдока: только
  режимы счёта MCM/PM (`acctLv` 3/4) — для Spot/Futures mode
  неприменим; не замена собственному преконтролю.
- **В-8 `mark-price` / `price-limit`** — дистанция ликвидации от
  mark price; границы допустимой цены ордера до постановки
  (`contracts/mark-price.md`, `contracts/price-limit.md`).
- **В-9 `account/config` + `set-leverage`/`set-position-mode`** —
  bootstrap-валидация посылок адаптера (`isolated`/`net`, плечо):
  старт-проверка `posMode`/`acctLv`/`perm` вместо принятия как
  данности (`contracts/account-config.md`); смежно INSTR-Q2 (кто и
  когда выставляет плечо).
- Рядом (без номера): `max-size`/`max-avail-size` — серверные
  потолки размера (`contracts/max-size.md`); `position-tiers` —
  `maxLever`/`maxSz` по тирам — форвард к **риску на биржу/портфель**
  (фаза 3; экспозиционные лимиты,
  `docs/decisions/per-trade-risk-policy.md`), не в валидаторе фазы 1
  (`contracts/position-tiers.md`).
- **Простой жёсткий предел плеча на сделку — отложен (ратифицировано
  2026-06-20,** `docs/decisions/per-trade-risk-policy.md`**).**
  Остаточный зазор: **узкий стоп → высокое плечо** при малом денежном
  убытке по стопу (риск на сделку умещается в лимит, но нотинал/плечо
  большие). **Вернуться после наблюдений** (бэктест / живые прогоны),
  когда станет видно, материализуется ли зазор на практике.

### Шаг 8 (safety / AnomalyJob)

- **Остаток холдов L3/L4** (реактивный enforcement построен на шаге 6 —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-holds-design.md`):
  (1) **проактивная детекция** аномалий (`AnomalyJob`/`TradeRuleValidator`
  + численный порог «серия неудач» STRUCT-Q1) — **шаг 8**;
  (2) **точный локальный after через REFRESH_*** + **биржа-широкая
  L4-реконсиляция** (внешний слепок читает только instId триггера) —
  **шаг 8**; (3) **аудит ручного un-hold** (кем/когда) — **шаг 9 / п.9**
  (сама операция un-hold построена).
- **В-1 `cancel-all-after`** — dead-man's switch: серверная
  страховка на потерю связи **поверх** явного kill-switch, не вместо
  него (`contracts/cancel-all-after.md`; heartbeat раз в секунду,
  timeOut 0|[10,120] с). Покрытие algo-ордеров CAA офдоком не
  специфицировано — уточнить на шаге.
- **Остаток kill-switch (ANOM-Q2):** per-инструмент контур построен,
  декларативный kill-switch откачён (семантика —
  `docs/components/KillSwitchExecutor.md`). **Остаётся форвардом:**
  (1) **AnomalyJob-путь** (проактивная детекция → зов executor'а) +
  общебиржевая **orphan-сверка** (сущности вне модели сделки) +
  перевод залипших L4-отчётов + порог «серия неудач» STRUCT-Q1 —
  **шаг 8**; (2) **PnL-финализация `EMERGENCY_CLOSED`** (остаток
  DEAL-Q2 закрыт G5: число считается по **той же формуле, что на чистой
  тропе** — net вкл. `liqPenalty` **плюс cross-ccy-слагаемое**;
  best-effort — про доступность числа, не про состав, H18
  `DOCS_CHECK_11`; расчёт — шаг 7). Связано с **ANOM-Q2**
  (`history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`).
- **Остаток Stage 3 FSM/action слоистости** (решение —
  `docs/decisions/fsm-execution-layering.md`; Stage 1-2 построены на
  шаге 6): transition-conditions в модели стратегии +
  exit-as-transition (`MANAGING→EXIT_PENDING` без `DEAL_EXIT`) + снять
  вырожденный `CLOSE_FULL` — сверить остаток с as-built шага 6.
- **Идемпотентность `AnomalyReport`** (хвост H17
  `GAPS_CLOSE_13`; анкер-ключ **снят**, идемпотентность держится
  незавершённым статусом — `docs/models/domain/other/AnomalyReport.md`
  §Персистентность). **`ANOM-Q5` закрыт** (2026-08-20): акторов три,
  межакторная гонка в фазе 1 недостижима; составной ключ поиска по
  радиусу + проход радиуса у синка. На шаге 8 остаётся: как радиус
  влияет на поиск незавершённого при эскалации scope. **`ANOM-Q6` —
  зависимый, горизонт фаза 3** (оживает с частичным уникальным индексом
  мультиинстанса). `ANOM-Q3`, `ANOM-Q4` сняты вместе с ключом.
- **Переоценка инварианта «ликвидация за стопом» — проектирование**
  (H18 `DOCS_CHECK_10`, решение пользователя; требование записано —
  `docs/components/AnomalyJob.md` §«Переоценка инварианта»,
  `docs/decisions/per-trade-risk-policy.md` §«Роль плеча»). Спроектировать
  на шаге: **такт** проверки; **гистерезис** против ложных срабатываний у
  широких стопов; выбор реакции — **перестановка защиты** vs
  **контролируемый выход**; код аномалии в реестре. Довод, почему не
  `RiskValidator`: переоценка ведомой позиции риска не создаёт
  (`docs/rules/risk-validator-scope.md` §«Переоценка вне создания
  риска»).
- **`TradeGuardJob` — новая джоба, счётчик серии неудач по инструменту**
  (H17 `DOCS_CHECK_10`). Компонент-дока и кода нет; шаг 7 работает с
  порогом серии = 1 (исчерпание бюджета попыток исполнения). Спроектировать:
  носитель счётчика (**один класс** — ось «вход-сайд / управление-сайд»
  снята H6 `DOCS_CHECK_14`, форвард приведён к принятому H9
  `DOCS_CHECK_15`), точка инкремента и сброса, окно/порог, `code`
  `HoldSignal`;
  **учёт отказов cleanup-команд** (сегодня их не считает никто — анкера у
  них нет). Граница с `AnomalyJob`: тот сравнивает **текущее** состояние с
  инвариантом, `TradeGuardJob` считает **историю исходов** по инструменту.
- **Инвентарь периодических джоб — держать сверенным** (`GAPS_CLOSE_10`).
  Состояние на 2026-08-03: **в коде и докax** — `CandleJob`,
  `IndicatorJob`, `MarketStructureJob`, `InstrumentExternalRulesSyncJob`,
  `EntryScannerJob`, `DealOrchestratorJob`; **док есть, кода нет** —
  `AnomalyJob` (материализация — этот шаг); **только название** —
  `ReconciliationJob` (п.7), `TradeGuardJob` (введена H17); **снят** —
  `MarketPhaseJob` (`docs/decisions/market-phase-stateless.md`);
  **отвергнута** — `TradeFeeSyncJob`
  (`docs/decisions/pnl-finalization-mechanics.md` реш.4, вариант B).
  Класс дефекта, ради которого инвентарь собран: механизм **существует,
  но не записан** (или записан, но не существует), и проверяющая линза
  находит «механизма нет» там, где он есть, — или принимает на веру то,
  чего нет. Пометки состояния носителей ставятся **в самих
  компонент-доках** (образец — `docs/components/AnomalyJob.md`
  §«Состояние носителей»), отдельного инвентарь-дока не заводим: типа под
  кросс-компонентный инвентарь в `.claude/rules/structure.md` нет, а
  заводить его ради одной таблицы преждевременно.

### Ось упущенных возможностей и разрешимость выборки — фаза 3

**Отправлено форвардом решением пользователя** (`GAPS_CLOSE_12`, узел G):
ось упущенных возможностей относится к **фазе 3**, не к фазе 2; в шаге 7 и
фазе 2 не разбирается.

- **Счёт упущенных возможностей на уровне скана** (H22 `DOCS_CHECK_12`).
  Пропуски входа **до** создания `Deal` не счётны ни одним механизмом:
  инструмент под `ENTRY_BLOCKED`/`TRADE_BLOCKED` выпадает из выборки скана,
  инструмент с неподтверждённым ключом группы статуса не меняет и отчёта не
  заводит, контурный гейт «нет активной сделки ни по одному инструменту»
  отсекает кандидатов молча. Реакции проекта на отказы — преимущественно
  «запрет новых входов + ручное снятие», то есть цена отказа конвертируется
  ровно в эту ось. Развилка фазы 3: считать **сделку** или **время торговой
  недоступности** (uptime). Носитель признанной цены —
  `docs/decisions/pnl-finalization-mechanics.md` §«Форвард-фокус: ось
  упущенных возможностей» (раздел заведён `GAPS_CLOSE_12`; прежде ссылка на
  него была битой). Смежное — `PNL-Q1` п.2.
- **Разрешимость R-выборки при пропускной способности контура** (H24
  `DOCS_CHECK_12`). Контур фазы 1 — один инструмент, одна активная сделка;
  сколько сделок нужно, чтобы отличить систему от шума, не оценено
  [SR ∝ √(независимых ставок/год), Carver ST гл.2 с.59-60]. До оценки числа
  ожидаемости фазы 1 — **операционные, не статистические**, и ни одно
  решение не должно опираться на них как на оценку.

### Перф-форвард (порог актуальности — фаза 3)

- **[MAJOR, perf] L4 `fireExchange` — небанженный O(сделок) burst под
  guard прохода (ревью холд-дельты, 2026-06-24).**
  `KillSwitchService.fireExchange` итерирует небанженный
  `DealDataService.findActiveByExchangeId` и на **каждую** сделку строит
  `DealContext` (~9 запросов) + kill-switch REST — внутри guard-прохода.
  Распухает по **памяти / стоимости запроса** линейно по числу
  одновременных сделок биржи, без потолка. В фазе 1 объём мал — не
  нагружено. **Починка (когда возьмём): перебор пачками (bounded) —
  полный свип сохранён**, режется не скорость, а ограниченный аппетит
  (память/стоимость). **`LIMIT` небезопасен** (отрезал бы несвёрнутый
  live risk); альтернатива — off-lock dispatch L4-teardown. Источник —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-code.md`
  §Доработка холд-дельты.
- **[MINOR, perf] Дублирующий тикер-REST в entry-скане (повторное ревью
  фикс-дельты M4, 2026-07-02).** `MarketPhaseService.buildContext` тянет
  тикер (`MarketPriceDataService.getMarketPriceData`) для классификации
  фазы по каждому ACTIVE-инструменту без активной сделки за проход;
  квалифицированный инструмент затем тянет тот же тикер повторно в
  `MarketConditionContextFactory.build`. Итог: +1 тикер-REST на каждый
  скан-инструмент, 2 идентичных вызова на квалифицированный — линейно к
  числу инструментов, давление на rate-limit OKX. Функционально
  корректно, согласуется с stage-1 no-cache. **Починка:** тянуть
  `MarketPriceData` один раз в `EntryScannerJob.scanInstrument` и
  прокинуть в оба контекста (фазовый + condition), либо короткоживущий
  per-tick кэш в `MarketPriceDataService`. Кросс-коллаборатор:
  `MarketConditionContextFactory.build` шарится с FSM
  (`DealFsmSupport.conditionContext`).
- **Унификация инфраструктуры джоб — горизонт фаза 3 (код-ревью заход 2,
  2026-07-01).** Доработка механизма замыкания под
  мультиинстанс/микросервисы. Состав: абстрактный `ScheduledJob`-родитель
  (шаблон `enabled → lock → run`) + единый `JobLock`-интерфейс с двумя
  реализациями (`InProcessJobLock` поверх `JobExecutionGuard`,
  `AdvisoryJobLock` — БД advisory-замок; raw-JDBC advisory —
  ратифицированное исключение: замок держит **одно соединение** весь
  проход). В фазе 1 все джобы — на in-process `JobExecutionGuard`;
  БД-замок вернётся с мультиинстансом (см.
  `.claude/rules/tech-radar.md` строка Raw-JDBC → `hold`,
  `docs/components/DealOrchestratorJob.md` §Concurrency-guard).

## Шаг 7 (сделки и P&L) — исполнительный хвост

**Концепция закрыта:** источник числа —
`docs/decisions/result-profit-source.md`; механика/носители стадий 1-2 —
`docs/decisions/pnl-finalization-mechanics.md`. Ниже — **исполнительный хвост
(CODE) + рантайм-верификация + форвард**, не выбор пути. **Гейт `CODE`
упирается в** (состояние на `GAPS_CLOSE_14`, 2026-08-20): калибратор
допуска (предусловие `CODE` п. 8), грунт `integrator` (5 позиций —
`AG1.6`, `AG1.7`, `AG6.2`, `M15.7`, `MG7.5`; статус `AG3.4` внесён в
реестр гейтящим H19 `DOCS_CHECK_14` — прогон той же фикстуры `AG1`) и
чистый `DOCS_CHECK_15`. **`RISK-Q4` и `ANOM-Q5` как гейты сняты**
(закрыты 2026-08-20; итоги — реестр предусловий `CODE` пп. 3-4).

- **CODE стадий 1-2 (доспецифицировано, писать код):** носители
  `OkxPositionsHistoryResponse` / `PositionCloseResultExternalSnapshot`
  (`mapping/PositionCloseResult.md`) + `DealCashFlow` (модель+mapping+таблица
  `deal_cash_flows`, включая компоненту `externalFee` и `applied_rate`);
  команды/executor'ы `REFRESH_BILLS` / `MARK_DEAL_EMERGENCY_CLOSED`;
  расчёт+запись `resultProfit` на `Deal` в `FinalizeDealExitExecutor` (N7);
  сверка bills↔net → `AnomalyReport` (N10); снятие `REFRESH_FILLS` (N12, доки
  закрыты — код-удаление на CODE).
- **CODE узла добычи положения закрытия (`GAPS_CLOSE_7`, H1/H3):**
  - `RefreshPositionExecutor` — **вторая нога evidence-cycle**: при not-found
    live-позиции запрос `/account/positions-history` по `posId`, маппинг в
    `PositionCloseResultExternalSnapshot`, запись полей на `Position`.
    Терминала цикл не выносит; запись не найдена — поля `null`, статус
    `CLOSED` (`docs/components/RefreshPositionExecutor.md`);
  - `Position`/`PositionEntity` + миграция: колонки
    `external_realized_profit`, `external_result_currency`,
    `external_close_type`;
  - **`REFRESH_POSITIONS_HISTORY` в `ServiceCommandType` не заводить** (её нет
    в целевом составе — 17, `docs/components/models/ServiceCommand.md`);
    handler'ы её не эмитят;
  - финализаторы (`FinalizeDealExitExecutor`,
    `MarkDealEmergencyClosedExecutor`) читают число **со строки `Position`**,
    вложенных команд не исполняют;
  - `RefreshBillsExecutor` — окно `[Deal.billsWindowBegin,
    Deal.billsWindowEnd]` (собственные поля сделки, узел 1 `DOCS_CHECK_8`;
    `billsWindowEnd` пуст → привязка ждёт), инструмент из `DealContext`,
    **guard «сделка удерживает слот»** (статус вне
    `CLOSED`/`EMERGENCY_CLOSED`) перед линковкой;
  - `Deal`/`DealEntity` + миграция: колонки `bills_window_begin`,
    `bills_window_end`; **писатели окна — разные** (H9 `DOCS_CHECK_16`):
    `begin` — `SubmitOrderExecutor` (`Order.externalCreatedAt` первой
    отправленной ноги, всегда при постановке); `end` —
    `RefreshPositionExecutor`, нога 2 (`uTime` записи закрытия, одной
    транзакцией с полями положения закрытия). Обе — условным `UPDATE`
    (`where ... is null`), не `updatable = false`.
- **CODE R-слота и формулы риска (`GAPS_CLOSE_7`, H9/H10; расширено
  H5/H6 `DOCS_CHECK_10`):**
  - **дом — нога, не сделка** (H6/H11 `GAPS_CLOSE_15`, решение
    пользователя; инструкция приведена к принятому H20 `DOCS_CHECK_16` —
    прежняя редакция предписывала **отменённую** топологию: все четыре
    числа как поля `Deal`, write-once):
    - **пять колонок `orders`** — `planned_risk_amount`,
      `planned_risk_currency`, `planned_entry_price`,
      `planned_size_contracts`, **`planned_contract_value`** (H5
      `DOCS_CHECK_16` — `ctVal` момента постановки; все `numeric(36,18)`,
      кроме валюты — `varchar(64)`; все nullable, write-once на уровне
      entity `updatable = false`), + `ALTER` миграцией шага. **Инвариант —
      «пять или ни одной»**: производит один преконтроль, пишет одна
      транзакция;
    - **на `Deal` — два числа, не одно** (H3 `DOCS_CHECK_16`, решение
      пользователя): `plannedRiskAmount` — **заявленный** (Σ по ногам входа
      за вычетом замещённых, предикат — H2 `DOCS_CHECK_16`; знаменатель
      R-мультипликатора) и **`incurred_risk_amount`** — **взятый**
      (Σ `plannedRiskAmount_i × accFillSz_i / plannedSizeContracts_i`);
      плюс общая `plannedRiskCurrency`. Обе — **не write-once**,
      производные проекции ног, пересчитываются **целиком**;
    - **пересчитывают три исполнителя** (каждый своей транзакцией, по
      своему триггеру): `CreateOrderExecutor` (создана нога входа),
      `RefreshOrderExecutor` (наблюдены исполнение/терминальный статус),
      `CancelOrderExecutor` (нога отменена/замещена). Разбор —
      `docs/models/domain/aggregate/Deal.md` §«Взятый риск»;
    - **`+risk_benchmark_availability`** на `deals` (`varchar(64)`, енум
      `RiskBenchmarkAvailability`: `AVAILABLE`/`NOT_APPLICABLE`/`MISSING`,
      H13 `DOCS_CHECK_16`) — пишут оба финализатора;
    - `CreateOrderExecutor` пишет пять чисел **входного** действия в одной
      транзакции с созданием сущности. **Предикат «входное действие» —
      прохождение риск-преконтроля, не `Order.Type`** (H1 `DOCS_CHECK_16`:
      енум двузначен, обе константы носят и не-входные ордера;
      `docs/models/domain/core/Order.md` §«Предикат "нога входа"»);
    - **javadoc `Order.Type`** в `src/` называет обе константы «входной
      ордер» — привести к редакции §Енумы того же дока (H1
      `DOCS_CHECK_16`);
    - **тавтологичные предикаты «это входная нога» в `src/`** — найдены
      свипом H1 `DOCS_CHECK_16`:
      `CalculationContextFactory.isEntryType(...)` и фильтр в
      `DealFsmSupport` (~стр. 190) проверяют `type == ENTRY || type ==
      ENTRY_ATTACHED_STOP_LOSS`, то есть **истинны для всякого** ordinary
      order — енум двузначен и третьего значения нет. Предикат подменить
      на действующий (риск-преконтроль на стороне писателя, непустой
      `plannedRiskAmount` на стороне читателя;
      `docs/models/domain/core/Order.md` §«Предикат "нога входа"»). Это не
      правка стиля: сейчас код молча считает входной **любую** ногу,
      включая reduce-only.
  - **Канал доставки — поля `CreateOrderCommandPayload`** (H5): ни один
    существующий RVO метрику не несёт, а `RiskCheckResult.actualValue` на
    happy-path не существует (в фазе 1 валидатор строит только
    `BLOCKED`-результаты). `plannedEntryPrice` **нельзя брать с
    `Order.price`** — при market-входе executor его не заполняет
    (верифицировано `CreateOrderExecutor.java:65-67`);
  - **координатные колонки ссылки на свечу курса** —
    `applied_rate_candle_instrument` / `_timeframe` / `_open_time` в
    `deal_cash_flows` (H11 `DOCS_CHECK_14`); прежняя редакция перечисляла
    только `appliedRate` и `rateStatus` — неполнота инструкции, снята H20
    `DOCS_CHECK_16`;
  - `SizeCalculator` — **закрытая форма**
    `contracts ≤ budget / (ctVal × (|entry−stop| + rate × (entryPx + stopPx)))`;
    итеративного подбора и «вычитания комиссии из бюджета» отдельным шагом
    нет;
  - `RiskValidator` — та же база нотинала каждой ноги (вход по цене входа,
    выход по цене стопа), чтобы шорты не сайзились крупнее.
- **CODE границы «команда ↔ действие»
  (`docs/decisions/command-action-boundary.md`; замещает прежний пункт
  «retry-анкера добывающих команд»):**
  - **носитель — две таблицы, не общая** (H15 `DOCS_CHECK_14`, решение
    пользователя; инструкция приведена к принятому H8 `DOCS_CHECK_15` —
    прежняя редакция предписывала **отменённую** топологию V2: общую
    таблицу с nullable `strategy_action_id`, `+action_kind` и частичными
    ключами по виду):
    - `deal_action_states` **переименовывается** в
      `deal_strategy_action_states`; `strategy_action_id` остаётся
      `NOT NULL`; `+target_entity_type` (`varchar(64)`, nullable),
      `+target_entity_id` (`bigint`, nullable), **+ шесть audit-колонок**
      (H15 `DOCS_CHECK_15`); снятие `uk_deal_action_state_deal_action`,
      два частичных уникальных индекса живых исполнений; `DROP COLUMN
      target` без бэкфилла;
    - создаётся **`deal_system_action_states`**: `id`, `deal_id`
      (`NOT NULL`, FK), `system_action_type` (`varchar(64)` `NOT NULL`),
      `status` (`varchar(64)` `NOT NULL`), retry-поля, шесть
      audit-колонок; один частичный уникальный индекс живых по
      (`deal_id`, `system_action_type`); **target-колонок нет** — цель
      всегда сделка;
    - **колонки `action_kind` нет ни в одной** — вид кодируется таблицей;
    - вторая entity + ветвление `DataService` по виду;
      `DealContext.actionStates` собирается **из двух чтений**;
    - `deal_finalization_states`: строки **не переносятся** — `DELETE` +
      `DROP TABLE`; удаление `DealFinalizationState`-стека
      (модель/entity/mapper/repository/dataservice). Место истины —
      `docs/models/domain/other/DealActionState.md` §Персистентность;
  - **`SystemActionExecutor`** вместо `DealFinalizationCommandFactory`;
    handler'ы перестают эмитить добывающие `REFRESH_*` напрямую через
    `DealFsmSupport.systemCommand(...)` — только звеньями
    `REFRESH_DEAL_CONTEXT_ACTION` (иначе `applyFailureAccounting` —
    no-op); cleanup (`CANCEL_*`/`CLOSE_POSITION`) — напрямую, без анкера;
  - **`ServiceCommandExecutor`** — одна ветка учёта (анкер один);
    `DealActionStateDataService` — резолв живого исполнения по частичному
    ключу вместо `findByDealIdAndStrategyActionId` (ключ дефектен для
    грида: второму исполнению узла некуда лечь);
  - **транзакционные связки:** `ENTRY_FINALIZED` — одной транзакцией с
    завершением `FINALIZE_DEAL_ENTRY_ACTION` (В4.1); терминалы — с
    продвижением своих звеньев (обобщение N7);
  - **переименование enum'ов** — `ServiceCommandType` → `*_COMMAND`
    (в БД не хранится), `StrategyActionType` → `*_ACTION` + **миграция
    значений `strategy_actions.action_type`** + правка примера
    `strategy-examples/trend-following-ema.json`; свип по
    switch/equals/конфиг-ключам;
  - **retry-конфиг:** завести секцию `service-command-retry`
    (`default-policy` + per-command `policies`) — сегодня её **нет ни в
    одном конфиге**, `getPolicy` возвращает `null` и `canRetry` падает
    NPE в catch-ветке учёта отказа, подменяя исходную ошибку; защитить
    `getPolicy` от пустого default'а;
  - нового finalization-типа `FETCH_*` **не заводить** (сущность
    финализации упразднена). На аварийном терминале — жёсткий отказ
    чтения приравнять к «недоступно» (пустой результат + терминал), чтобы
    `MARK_DEAL_EMERGENCY_CLOSED` не уходил в `FAILED`.
- **CODE журнальных аномалий (`GAPS_CLOSE_7`, H19/H23; уточнено H16/H22
  `DOCS_CHECK_10`):** вторая точка входа `AnomalyReportService` — **без
  `DealContext`**, с явными `scope`/`severity`/`kind`/`code`
  (`NON_CRITICAL`); коды шага 7 — по реестру
  `docs/models/domain/other/AnomalyReport.md` §«Производящая поверхность и
  коды шага 7».
  - **Новая колонка `kind`** (`STATE`/`EVENT`) + **поисковый** (не
    уникальный) индекс `where kind = 'STATE' and status in (незакрытые)`:
    идемпотентность `STATE`-отчёта держится **незавершённым статусом** —
    производитель ищет незакрытый отчёт своего `code` и радиуса и
    продолжает его (H17 `GAPS_CLOSE_13`; анкер-ключ снят). Журнальные
    события (`PNL_RECONCILIATION_MISMATCH`,
    `RESULT_CURRENCY_MISMATCH`, `UNCLASSIFIED_CASH_FLOW`,
    `SETTLE_CURRENCY_VIOLATION`, `SETTLE_CURRENCY_UNAVAILABLE`,
    `CROSS_CCY_RATE_UNAVAILABLE`, `RESULT_PROFIT_UNAVAILABLE`,
    `CLOSE_OUTCOME_UNDETERMINED`, `BREAKDOWN_COMPLETENESS_NOT_ASSESSED`)
    поиска незавершённого не делают — они обязаны быть счётными (на этом
    стоит рамка R-выборки).
  - **`STATE`-отчёт не завершается сразу:** `CREATED → IN_PROGRESS` при
    постановке холда → `COMPLETED` при **ручном снятии**
    (`docs/lifecycles/AnomalyReport.md`). Иначе искать незавершённый
    нечего, и синк заводит копию каждый тик.
  - **`anomaly_reports.scope` → `varchar(64)`** тем же `ALTER` (H22
    `DOCS_CHECK_10` — расширение; длина по единой норме строковых колонок,
    H18 `DOCS_CHECK_15`).
  - ~~`HoldScope.INSTRUMENT_GROUP` — целевое значение, вводится этим же
    CODE~~ — **снято** (H14 `DOCS_CHECK_15`): групповой радиус отчёта
    упразднён, единственного производителя у значения не осталось, енум
    остаётся двузначным (`INSTRUMENT` / `EXCHANGE`) — как в коде и в
    комментарии `V10`. **Колонка `anomaly_reports.fee_group_key` тоже не
    заводится**; отчёт о несвежести ставок — **на инструмент**, по одному
    на каждый затронутый, идентичность несёт `instrument_id`; поисковый
    индекс незавершённых — по (`exchange_id`, `code`, `scope`,
    `instrument_id`).
  - **javadoc `HoldScope` в коде несёт снятые `GAPS_CLOSE_6`
    ярлыки уровня** («инструмент = уровень 3, биржа = уровень 4»,
    «Уровни error-градации») — переформулировать: scope есть **радиус**,
    уровень живёт в error-политике.
- **CODE cross-ccy (`GAPS_CLOSE_7`, H4; CCY-Q1 закрыт):** сравнение `ccy`
  движения с **расчётной валютой инструмента** (не с
  `Deal.resultProfitCurrency` — на записи оно `null`); при несовпадении —
  персист + линковка + курс **из свечи на момент операции** (H25
  `DOCS_CHECK_11`; редакция «отдельным вызовом биржи на момент обработки»
  снята) + запись `DealCashFlow.appliedRate` и `rateStatus` +
  `AnomalyReport`; слагаемое числа Σ(`amount` × `appliedRate`) **по строкам
  `rateStatus = APPLIED`** (H9 `DOCS_CHECK_11`; предикат «по строкам чужой
  `ccy`» снят) считает финализатор. Носитель расчётной
  валюты **определён** — поле `Instrument.externalSettlementCurrency`
  (`docs/decisions/instrument-currencies-home.md`); `CCY-Q2` **закрыт**
  (H6 `DOCS_CHECK_11`).
  ⚠ **Хэнд-офф `integrator` — носитель курса** (H11 `DOCS_CHECK_10`;
  источник котировки задан H25 `DOCS_CHECK_11` — **свеча на момент
  операции**, а не тикер). Собрать: доступно ли **секундное разрешение**
  на нужных парах `<CCY>-USDT` и на какую **глубину хранения**; отсюда —
  **правило деградации** (какой следующий интервал берётся); **какая
  цена берётся из свечи** (close интервала, содержащего момент; иное) —
  от этого зависит воспроизводимость; **стоимость по квоте и группировка
  запросов** (движений в окне может быть много, по-строчный запрос курса
  упирается в 5 req/s); доступность пар при SWAP-only контуре. Завести
  строку операции в манифесте покрытия. Политика при недоступном курсе
  **уже задана** (`docs/components/RefreshBillsExecutor.md` §«Носитель
  курса», §«Политика отказа котировки и догон»): `appliedRate = null`,
  `rateStatus = RATE_UNAVAILABLE`, слагаемое не вносится, `AnomalyReport`
  `CROSS_CCY_RATE_UNAVAILABLE`, строка образует **долг догона**; курс не
  подставляется.
- **Список исключений сверки по бирже — дом решён, содержание открыто**
  (H14 `DOCS_CHECK_10`; дом — H16 `DOCS_CHECK_16`, решение пользователя).
  Область Σ-сверки задаётся перечнем `type`/`subType`, **исключаемых** из
  экономики сделки. **Носитель — `@ConfigurationProperties` per-exchange**
  (меняется без релиза; непустой стартовый набор — в конфиге по умолчанию;
  ключ по бирже). Открытым остаётся **содержание** перечня —
  хэнд-офф `integrator`.
  ⚠ **Непустой список — предусловие `CODE`** (H10 `DOCS_CHECK_11`,
  решение пользователя). Прежняя редакция отгружала контроль с заведомо
  пустым списком («проявятся через `OTHER` и расхождение сверки») — это
  процедура разведки, а не режим работы: при пустом списке контроль
  целостности числа мёртв с первого дня, а на нём стоит вся R-выборка.
  Перечень наполняется тем же рантайм-прогоном
  (`.claude/tests/source-api/okx/plan.md`), и до его непустоты шаг на
  `CODE` не переводится
  (`docs/models/mapping/DealCashFlow.md` §«Область сверки задаётся списком
  исключений по бирже»).

- **Грунт `integrator` для шага 7 — собирается, не дожидаясь чистого
  прогона** (правило §4 `.claude/processes/roadmap-step-execution.md`;
  заведено `GAPS_CLOSE_11`). Сводный перечень того, что упирается в факт
  источника, а не в проектирование:
  1. **Оси адресации истории позиций без `posId`** (H5 `DOCS_CHECK_11`):
     какие оси запроса принимает `positions-history` и как ведёт себя,
     если в окне по инструменту **несколько** записей (несколько циклов
     открытия-закрытия; частичные закрытия отдельными записями). Без
     ответа create-тропа не специфицирована — **гейт `CODE`**.
  2. **Носитель курса cross-ccy** (H25) — см. хэнд-офф выше.
  3. **Семантика `actualPx` алго-ордера** (H21, новый хвост): означает
     ли поле цену **исполнения** сработавшего ордера или цену его
     **выставления** после триггера. От ответа зависит, измеряет ли
     разность «уровень стопа ↔ `externalPrice`» проскок или ноль, то есть
     исполним ли выбранный операнд калибровки
     (`docs/models/domain/core/Position.md` §«Цена фактического выхода»).
  4. **Семантика `fundingFee` записи positions-history** (H20): накоплен
     за жизнь `posId` или только за последнее закрытие — от этого зависит,
     сверяется ли Σ`FUNDING` окна с ним напрямую.
  5. **Инвентарь bill-типов, не принадлежащих экономике сделки** (H10) —
     см. хэнд-офф выше, **гейт `CODE`**.
  6. **Инвариант агрегации positions-history** (N11, §AG1) — прежний
     гейт `CODE`, не новый.
  7. **Знаки трёх операндов записи и bill'а штрафа** (H15
     `DOCS_CHECK_16`) — **гейт `CODE`**, кейс `AG1.7`: фактический знак
     `fee` и `liqPenalty` в positions-history и знак `balChg` у bill'а
     ликвидационного штрафа. Четвёртая пара сверки сравнивает
     `externalLiquidationPenalty` **без отрицания**; при положительной
     величине штрафа Δ₄ = 2·|штраф| на **каждой** ликвидации, то есть
     контроль погашен на левом хвосте. Предусловие `CODE` п. 7.
  8. ~~**Дом конфига списка исключений** (H16 `DOCS_CHECK_16`)~~ —
     **структурная половина закрыта** (решение пользователя:
     `@ConfigurationProperties` per-exchange, предусловие `CODE` п. 12).
     Хэнд-офф выше существовал врезкой в mapping-доке и в сквозной реестр
     гейтов не вносился — внесён и закрыт. Остаётся **содержание**
     перечня — это п. 5 выше, не отдельная позиция.
  9. ~~**Ветка `AG1.5`** (штампуется ли entry-fee раньше `cTime`
     позиции)~~ — **снята** (H9 `DOCS_CHECK_16`, решение пользователя):
     писатель нижней границы один, `Order.externalCreatedAt` не позже
     комиссии исполнения той же ноги ⇒ окно накрывает entry-fee по
     построению. Позиция закрыта, не отложена.
  10. **Наличие `instId`/`instType` в `data[]` positions-history** (H17
     `DOCS_CHECK_16` — посылка H18 `DOCS_CHECK_14`) — **гейта нет**:
     посылка взята из контракт-дока проекта, не из офдока. При
     отрицательном ответе структурная валидация вырождается в записанное
     ограничение «корректность держит фильтр запроса».

  **Позиции 7-10 добываются одним заходом** (H15/H16/H9/H17
  `DOCS_CHECK_16`): все четыре — недобытый факт источника, у 7 фикстура
  названа (`AG1.7`), у 9 прогон уже запланирован, 8 и 10 сверяются с
  офдоком без отдельного прогона. Разносить их на четыре задачи нечем —
  владелец, контур и фикстура общие.
- **CODE fee-wiring (N9, доспецифицирован на `GAPS_CLOSE_3`, доведён на
  `GAPS_CLOSE_4`):** новая модель **`TradeFeeRate`** + таблица
  `trade_fee_rates` (одна строка на группу; ключ группы — **сырая** пара
  (`external_instrument_type`, `external_fee_group_id`), H7; история: значение
  группы — taker/maker/`level` — изменилось → новая строка, совпало →
  **инкремент `refresh_count`** + обновление `external_ts`, H5/H11) + native
  `OkxTradeFeeResponse` + `mapping/TradeFeeRate` (**знак ставки снимается здесь,
  `× −1`** — ниже маппинга ставка есть издержка, `abs` в формулах нет, H2);
  `externalFeeGroupId` на навесе `InstrumentExternalRules` (**не сама ставка**);
  **гидрация ставки — в `InstrumentExternalRulesDataService`** (хранилищный
  слой; обе тропы чтения навеса гидрированы, H1); `InstrumentExternalRulesSyncJob`
  — второй источник `trade-fee`, **один вызов на тик** до цикла (A′), матч
  per-instrument **по паре**, не по голому `groupId` (H8); реджект
  `FEE_RATE_UNAVAILABLE` в `RiskValidator` (только на `null`); **мягкий холд
  инструментов группы по несвежести** — запрет новых входов +
  `AnomalyReport`, **без kill-switch**, живые сделки доживают под своим
  стопом (H2 `GAPS_CLOSE_5`; реконсилировано H1 `GAPS_CLOSE_6`), **снятие —
  вручную** (H2 `GAPS_CLOSE_6`); порог в конфиг, стартово 24 ч; H3/H4.
- **CODE узла холда (`GAPS_CLOSE_6`):**
  - новый статус **`Instrument.Status.ENTRY_BLOCKED`** (мягкий класс) +
    ручное снятие `ENTRY_BLOCKED → ACTIVE` (сервис/контроллер по образцу
    `InstrumentService.unblockTrade`); `TRADE_BLOCKED` остаётся за
    kill-switch-классом (H3);
  - **гейт пропуска реакции `SafetyHoldCoordinator`** ключуется на «scope
    уже в `TRADE_BLOCKED`», а не «scope не в `ACTIVE`» — иначе мягкий холд
    маскирует последующий kill-switch-триггер (H3, эскалация
    `ENTRY_BLOCKED → TRADE_BLOCKED`);
  - javadoc `Instrument.Status.TRADE_BLOCKED` / `Instrument.isTradeBlocked()`
    сужает класс до «уровень 3» — **переформулировать без расширения на
    мягкий класс** (H14, `GAPS_CLOSE_7`). Прежняя формулировка пункта
    («расширить — тропа несвежести приезжает с уровня 4 по радиусу»)
    противоречила первому буллету этой же секции: несвежесть уводит в
    `ENTRY_BLOCKED`, а не в `TRADE_BLOCKED`. Исполнение как было написано
    расширило бы `isTradeBlocked()` на мягкий класс — и оркестратор снова
    начал бы уводить живые сделки в `ERROR` по несвежести, воскресив снятую
    политику. Задача: javadoc описывает **kill-switch-класс** (перехват
    активных сделок), мягкий класс — отдельный предикат под `ENTRY_BLOCKED`;
  - **множества входа safety-статусов** (H13, `GAPS_CLOSE_7`): охраняемое
    обновление `InstrumentDataService.blockTrade` требует `status = 'ACTIVE'`
    — из `ENTRY_BLOCKED` вернёт «не применено» и **замаскирует**
    kill-switch-реакцию. Привести к решению: `TRADE_BLOCKED`/`CLOSED`/`ERROR`
    — из **любого** статуса; `ENTRY_BLOCKED` — только из `ACTIVE`
    (`docs/rules/instrument-hold.md` §«Множества входа»);
  - ~~раздельные счётчики серии неудач вход-сайд / управление-сайд (H7)~~
    — **снято** (H9 `DOCS_CHECK_15`): ось стороны ноги отменена вовсе
    (H6 `DOCS_CHECK_14`, решение пользователя). Резолв класса реакции идёт
    **по типу перехваченного исключения**, форма исчерпания бюджета одна —
    мягкая, код холда один — `RETRY_BUDGET_EXHAUSTED`
    (`docs/rules/instrument-hold.md` §«Серия неудач: реакция на исчерпание
    бюджета», `docs/components/HoldService.md` §«Момент вызова»);
  - **канал подъёма реакции — строится, и строится первым** (H1
    `DOCS_CHECK_15`): новый тип `RetryBudgetExhaustedException`; бросок в
    `ServiceCommandExecutor` **после** перевода строки исполнения в
    `FAILED` (вместо нынешнего `catch (RuntimeException) → return
    failure(...)`); `classify()` перестаёт схлопывать
    `ControlledExchangeException` в `VALIDATION_ERROR`; выделенный `catch`
    в `DealOrchestratorJob` вокруг шага диспетчеризации команд, поимённо
    по двум типам, **до** общего `catch (RuntimeException)`. Снятие
    прежнего транспорта (`DealTransition.holdSignal`,
    `DealOrchestratorJob.reactToHoldSignal`, `DealFsmSupport`) — **только
    после** этого: прежний канал в коде жив и работает
    (`docs/components/ServiceCommandExecutor.md` §«Контракт броска»,
    `docs/components/DealOrchestratorJob.md` §«Перехват реакции»);
  - **измеритель свежести ключа группы** (H11, `GAPS_CLOSE_7` — ревизует H9
    `GAPS_CLOSE_6`; начальное состояние — H21 `DOCS_CHECK_10`):
    собственных `refreshCount`/`confirmedAt` у навеса **не заводить**;
    синк на каждом успешном чтении `/public/instruments` **явно
    проставляет `Instrument.externalModifiedAt`** (колонка
    `instruments.external_modified_at` уже есть — `V1`, сегодня никем не
    заполняется). Возраст этой метки и есть возраст ключа группы.
    **Писатель ровно один — синк**: онбординговый `SYNC` метку не пишет и
    не может (граничный снапшот шага 1 поля времени не несёт).
    **`NULL` = «ключ не подтверждён»** ⇒ инструмент **не попадает в
    entry-скан** (предусловие, не холд; снимается само первым успешным
    тиком). **Бэкфилла нет и `instruments` в schema-дельте нет** —
    бэкфилл проставил бы метке значение, которого измерение не
    производило;
  - ~~`AnomalyReport.scope` — значение `INSTRUMENT_GROUP` в `HoldScope`
    (H4)~~ — **снято** (H14 `DOCS_CHECK_15`): групповой радиус отчёта
    упразднён, отчёт о несвежести — **на инструмент** (`scope =
    INSTRUMENT`, `instrument_id` заполнен, по одному на каждый
    затронутый); значения `INSTRUMENT_GROUP` в енуме не появляется.
- **CODE-дельта `GAPS_CLOSE_10`** (остальное, сверх пунктов выше):
  - **контурный гейт входа** (H8): `EntryScannerJob`/`DealOpeningService`
    к проверке «нет активной сделки по этому инструменту» добавляют «нет
    активной сделки **ни по одному**» — энфорсмент «в фазе 1 торгуется
    один инструмент». DB-инварианта нет (у `deals` нет колонки биржи),
    гонку закрывает `JobExecutionGuard`; снимается в фазе 3;
  - **предусловие entry-скана «ключ группы подтверждён»** (H21):
    инструмент с пустым `external_modified_at` в скан не попадает;
  - **валюта результата** (H10): `Deal.resultProfitCurrency` пишется из
    **расчётной валюты инструмента**, `Position.externalResultCurrency`
    **сверяется** → `RESULT_CURRENCY_MISMATCH` при расхождении (расчёт не
    блокируется); ветка пустого операнда — реджект
    `SETTLE_CURRENCY_UNAVAILABLE` в `RiskValidator` (новый
    `RiskCheckCode`) на входе, `AnomalyReport` того же кода на записи
    движения и на финализации;
  - **аварийный терминал считает то же слагаемое** (H12):
    `MarkDealEmergencyClosedExecutor` применяет cross-ccy-слагаемое
    Σ(`amount` × `appliedRate`), на биржу не ходит (курс уже на строке);
  - **корзина `OTHER` наблюдаема** (H14): непустой `OTHER` у сделки →
    `AnomalyReport` `UNCLASSIFIED_CASH_FLOW`; Σ-сверка идёт за вычетом
    типов из конфига исключений биржи (дом конфига — хэнд-офф
    `integrator`, выше);
  - **epsilon двухчастный** (H15): `min( max(0.01, 0.5%·Σ|amount|),
    k × ожидаемая комиссия сделки )` — срабатывает меньший; величины
    провизорны, структура нет;
  - **`Position.externalCloseAveragePrice`** (H26) + колонка
    `external_close_average_price`; маппится из `closeAvgPx`
    positions-history; расчётного потребителя в фазе 1 нет — поле
    накапливает наблюдения для калибровки запаса на проскок;
  - **правило переноса `deal_finalization_states`** (H19): строки
    финализации **не переносятся** — `DELETE` + `DROP TABLE`; **`target`
    (jsonb) расплющивается** в target-колонки + `DROP COLUMN target`,
    **бэкфилла нет** — таблицы пусты по правилу фазы (H25
    `GAPS_CLOSE_13`, `.claude/rules/pre-launch-schema-changes.md`);
  - **колонки ставок `trade_fee_rates` — `varchar(64)`** (H23; длина по
    единой норме, H18 `DOCS_CHECK_15`), не `numeric`: доменный тип
    `String`, аксессор сознательно допускает непарсящееся значение;
    исключение записано
    (`docs/rules/persistence-representation.md` §«Численные колонки»);
  - **все строковые колонки шага — `varchar(64)`** (H18 `DOCS_CHECK_15`,
    решение пользователя): категоризация по типу значения (валюта 16 /
    сырой код 32 / сырой идентификатор 64 / enum 32) схлопнута
    (`docs/rules/persistence-representation.md` §«Строковые колонки:
    длины»); места истины — §Персистентность моделей, сборка —
    `docs/decisions/pnl-finalization-mechanics.md` §Следствия;
  - ~~SYSTEM-строки `deal_action_states` несут цель (H24)~~ — **снято**
    (H8 `DOCS_CHECK_15`): системные исполнения живут в собственной
    таблице `deal_system_action_states`, **target-колонок у неё нет**
    (цель системного действия всегда сама сделка, операнды цели в ключе
    были бы производными; H15 `DOCS_CHECK_14`). Условие возврата —
    появление системного действия с целью ≠ `DEAL`
    (`docs/models/domain/other/DealActionState.md` §Инварианты);
  - **состав цикла добычи выводится из `DealContext`** (H3), а не
    передаётся handler'ом; на `Deal.status = ERROR` **отказ канала
    добычи** расходует бюджет штатно (H3 `DOCS_CHECK_15` — прежнее «ноль
    попыток» отменено), `FAILED` строки `REFRESH_DEAL_CONTEXT_ACTION` —
    durable-исход «недоступно», он же **разрешает эмиссию терминала**;
    радиусная реакция не поднимается. **Контролируемое исключение под это
    не подпадает** (H4 `DOCS_CHECK_15`): бросается и на аварийной тропе,
    реакция — полный биржевой холд параллельно с ошибочным терминалом;
  - **`billsWindowBegin` — единственный писатель, безусловно** (H9
    `DOCS_CHECK_16`, решение пользователя; условная ветка H27 снята):
    `SubmitOrderExecutor` пишет `Order.externalCreatedAt` первой
    отправленной ноги **всегда** при постановке, условным `UPDATE` (`where
    bills_window_begin is null`). Ни live-нога, ни нога 2
    `REFRESH_POSITION_COMMAND` поля не касаются. Ждать рантайм-ответа
    §AG1.5 не нужно — он снят конструкцией.
- **Форвард (не сейчас): авто-снятие мягкого холда по предикату свежести.**
  Отложено, не отвергнуто (H2 `GAPS_CLOSE_6`): в фазе 1 снятие ручное —
  пайплайн в отладке, человек идёт разбирать причину сбоя. Горизонт
  пересмотра — **установившийся режим** (сбои интеграции стали редкими и
  понятными); тогда же взвесить гистерезис / K подряд успешных чтений как
  порог восстановления доверия. Носитель довода —
  `docs/rules/instrument-hold.md` §Снятие.
- **N11 — рантайм-верификация инварианта агрегации positions-history** (гейтит
  корректность числа, **до CODE**): партиал-выходы одного `posId` → одна
  финализированная запись, `realizedPnl` кумулятивен. Test-план —
  `.claude/tests/source-api/okx/plan.md` §AG1.5 (⏳ PENDING; интегратор/тестер:
  фикстура-цепочка на demo). Если OKX не агрегирует — путь корректируется.
- **Рантайм-хвост на той же фикстуре §AG1.5** (один прогон, **после чистого
  `DOCS_CHECK_4`** — порядок последовательный): **H2** гранулярность bills
  (§AG3.5), **RQ-3** ставка группы ↔ фактическая комиссия (§AG12.5), **RQ-4**
  `ccy` fee-bills = USDT (§AG3.4). Без фикстуры: **RQ-1** покрытие `feeGroup[]`
  (§AG12.4), **RQ-2** `groupId` непуст (§M1.7).
- **N13 — funding как holding-cost (форвард, фаза 2 / шаг ожидаемости):** в
  число funding учтён; на форварде издержка удержания без дома — разделяющий
  довод «комиссию в R, funding в post-cost expectancy» зафиксирован
  (`per-trade-risk-policy.md` §«Учёт комиссий»); завести форвард-дом на шаге
  ожидаемости/бэктеста. Scope (фаза 2 vs step-7-adjacent) — хвост пользователя.
- **Epsilon сверки bills↔net (N10)** — провизорная **величина**
  (max(0.01 settle-ccy, 0.5%·Σ`|amount|`)); подтверждение/калибровка —
  пользователь/бэктест. **Якорь** (Σ`|amount|`, не `|net|`) провизорным больше
  не является — закрыт на `GAPS_CLOSE_3` (H7).
- **H6 — добор недостающего числа на `EMERGENCY_CLOSED` (форвард, фаза 2 / шаг
  ожидаемости):** null = «неисчислимо» — не финальный вердикт, а **отложенный
  долг**; направление принято (добор до истечения окна positions-history, ~3 мес),
  **материализация** (кто дочитывает, на каком такте, что с просроченным окном) —
  за шагом ожидаемости (`pnl-finalization-mechanics.md` реш.3). Пометки
  недостаточно: пропуск outcome-коррелирован, drop завышает ожидаемость.
- **Искажение измеряемой ожидаемости: две оси × две стороны (торговый
  форвард-фокус):** форма уточнена на `GAPS_CLOSE_5` (H22) — **знак есть
  свойство механизма, а не оси**; правильная форма — матрица «две оси × две
  стороны», механизмы по клеткам. Сама двухосевая декомпозиция
  (`GAPS_CLOSE_4`) держится, третьей оси не нашлось.
  - **Исходы × оптимистично** (число лучше правды): H6 null-drop
    (`pnl-finalization-mechanics.md` реш.3) + N11 недосчёт агрегации +
    опущенный гэп-проскок (TR2, `per-trade-risk-policy.md` §«Без поправки на
    проскок»). **H16 из клетки выведен** (`GAPS_CLOSE_6`, H5): cross-ccy
    движение больше не помечается-и-забывается — оно персистится, линкуется
    и **входит в число эквивалентом по курсу из свечи на момент операции**
    (курс записан полем `appliedRate`; `pnl-finalization-mechanics.md`
    реш.5, H4 `GAPS_CLOSE_7`, источник котировки — H25 `DOCS_CHECK_11`;
    редакция «по курсу на момент обработки» снята). Урок H6 («пометка
    фиксирует факт, но не
    устраняет смещение») к нему **применён**; остаток «точность курса»
    закрыт записью применённого курса — величина воспроизводима.
  - **Исходы × пессимистично:** клетка **непуста** (H8, `DOCS_CHECK_6` →
    `GAPS_CLOSE_6`). Механизм — **L4-flatten чужих здоровых сделок**:
    controlled-violation на **одной** сделке безусловно поднимает биржевой
    холд и каскадный `KillSwitchService.fireExchange` по **всем** активным
    сделкам биржи (`controlled-violation-exchange-wide-hold.md`,
    `KillSwitchService.md` §«Биржа-scope»). Здоровые сделки закрываются по
    рынку в момент, некоррелированный с рынком: правый хвост R усекается,
    а закрытые с реальным числом входят в R-выборку — измеряемая
    ожидаемость **занижается**. Прежний механизм-кандидат клетки
    (kill-switch по несвежести) снят `GAPS_CLOSE_5` (H2), но клетка от
    этого пустой не стала — просто занята другим механизмом.
    Соразмерность L4 сознательно принята риск-политикой
    (`controlled-violation-exchange-wide-hold.md` §Принцип: незрелая
    интеграция ⇒ консервативный широкий тормоз) — здесь фиксируется её
    **цена по оси измерения**, не пересмотр решения.
  - **Возможности × пессимистично** (сделок меньше/мельче правды): **H13**
    taker-консерватизм при maker-входах = систематический недосайзинг
    (`pnl-finalization-mechanics.md` реш.4, оговорка); **H15** цена пропуска
    входа под реджектом/холдом оценена в ~0 — корпус против: «весь годовой
    профит часто делает одна сделка — её нельзя пропустить» [Tharp гл.6
    с.158-159; гл.11 с.279], «издержки: равный вред от занижения и завышения»
    [Kaufman гл.1, PDF с.114-119]; **H21** промо нулевой комиссии не видно в
    `trade-fee` ⇒ прогноз завышает издержку ⇒ недосайзинг
    (`docs/integrations/okx/contracts/trade-fee.md` §«Прочие ремарки»).
  - **Возможности × оптимистично** (сделок больше/крупнее правды): клетка
    **непуста** (H8, `DOCS_CHECK_6` → `GAPS_CLOSE_6`). Механизм — **окно
    несвежести двусторонне**: до срабатывания порога (0-24 ч) сайзинг идёт
    по последней известной ставке, и если внутри окна тир **понизился**
    (ставка выросла), прогноз комиссии занижен ⇒ бюджет риска «свободнее»
    ⇒ позиция **больше положенной** [Vince гл.1 с.9,18 — перебор хуже
    недобора]. Прежде окно рассматривалось только со стороны «мы можем
    недосайзить»; сторона перебора зафиксирована здесь. Радиус ограничен
    величиной шага тира и длиной окна; лечится сокращением порога
    свежести — калибровка вместе с величиной порога.
  - **Почему это один фокус, а не два пункта.** Крены на разных осях в
    разные стороны не компенсируются, а делают сравнение **бэктест ↔ live
    двусторонне несопоставимым**: искажение исходов сдвигает распределение
    R, искажение возможностей — его объём и состав. Мерить одно, не зная
    другого, нельзя. Владелец — фаза ожидаемости
    (`progress/phase-1-step-7-docs-check-4.md` §Сводка, Lens C; форма —
    `phase-1-step-7-docs-check-5.md` H22).
- **H17 — «недоступность обычно временна» на непроверенном допущении**
  (форвард, фаза 2 / шаг ожидаемости): направление H6 (добор числа до истечения
  окна positions-history) стоит на допущении о **той самой популяции**, которая
  по доводу H6 **outcome-коррелирована**. Если null возникает оттого, что записи
  не существует в принципе (краевые ADL/ликвидационные исходы), добор — no-op.
  Следствие, которое надо записать при материализации добора: H6 направлением
  **уменьшается, а не закрывается**, коэффициент неизвестен [Kaufman гл.1, PDF
  с.110-112: «подозревать хорошие результаты»].
- **Форма epsilon — закрыта, живёт только калибровка.** Итог закрытия —
  `.claude/work/history/2026-08-20-curation-sweep-snapshot-v82.md` §2;
  содержание — `docs/decisions/pnl-finalization-mechanics.md` реш.5 §epsilon.
  Живой форвард: **калибровка величин** (`0.01`, `0.5%`, `k`) провизорна —
  числится отдельным пунктом выше; `k` вдобавок стоит предусловием `CODE`
  п. 8 и вторым основанием `PNL-Q1` п. 3.
- **Вход в market-maker-программу → пересмотр оси запроса `trade-fee`**
  (инвариант organic-base-rates, `pnl-finalization-mechanics.md` реш.4): запрос
  без `instId`/`instFamily` даёт organic base rates — валидный ответ, но не тот,
  если аккаунт станет участником программы.
- **`elpMaker` → `rpiMaker`** (прод OKX **2026-07-28**, параллельные имена до
  2026-10-31): поле **unused**, механики нет по
  `docs/decisions/source-model-change-absorption.md`; переоценка — только если
  поле станет used до конца окна.

## Хвост шага 4 (CODE-отложения, 2026-06-11)

Refinements, сознательно отложенные при `CODE` шага 4 (код — первый
проход, доки описывают целевой дизайн). Источник —
`.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-sync-docs-from-code.md`
(§DEFER). Берутся при доведении командного слоя / на смежных шагах.
Гейтовые D-B3 (SUBMIT recovery-by-clientId) и D-M1 (concurrency-guard)
закрыты на шаге 6 — итог в
`history/2026-07-14-claude-docs-curation.md`.

- **ClosePosition settle ccy** — `ClosePositionExecutor`/
  `IntegrationService.closePosition`: передавать settle currency в
  close-request (сейчас `null`).
- **`ServiceCommandFactory`: REPLACE-оркестрация + CANCEL-резолюция
  цели.** Порядок ног REPLACE по риск-классу (place→факт→cancel для
  protective; cancel→факт→place для entry) и резолюция цели CANCEL по
  цепочке `replacesInternalId` — не реализованы (фабрика покрывает
  CREATE/SUBMIT/REFRESH/CLOSE). Владелец оркестрации: секвенс ног ведёт
  петля/`DealStateMachine` по фактам, фабрика остаётся «одна команда за
  проход» (`docs/decisions/action-orchestration-vs-command.md`);
  концепция — `replace-not-amend`, `DealActionState` §REPLACE.
  **Re-deferred за `CODE` шага 6 (deferral D1, 2026-06-22):** фабрика
  REPLACE-ног возвращает `empty`, `ManagingHandler` стоит в `MANAGING`;
  самостоятельный объёмный refinement, не нужен базовой петле фазы 1.
- **Refresh algo: external-поля дерева `condition`.** `updateFromSnapshot`
  игнорит `condition`; обновляются только top-level факты срабатывания.
  Обновление trigger/trailing external-цен из снапшота — добрать.
- **Evidence-cycle пагинация.** Order/algo pending/history — сейчас одна
  страница на звено; добрать пагинацию назад до пустого `data`
  (владение циклом — `refresh-evidence-cycle-ownership`). Плюс
  order-цикл не доходит до `orders-history-archive` (последнее звено по
  докам) — добрать. Актуально и для звеньев шага 7: **вторая нога
  `REFRESH_POSITION`** (positions-history, пагинация по `uTime`) и
  `REFRESH_BILLS` (7d→3m); `REFRESH_FILLS` снят, отдельной команды
  `REFRESH_POSITIONS_HISTORY` нет (H1/H3 `GAPS_CLOSE_7`).
- **Рантайм-прогон через `OkxProxyController`** — отдельно, при
  поднятом PostgreSQL + demo-кредах (вкл. И-2: подтверждение
  `cancel-advance-algos` для trailing в demo trading).

### Из адверсариального ревью (2026-06-11) — неблокирующий остаток

Источник —
`.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-adversarial-review.md`.
Гейтовые D-B3/D-M1 закрыты на шаге 6 (итог —
`history/2026-07-14-claude-docs-curation.md`).

- **[MAJOR] D-M5/R5 — пагинация evidence-цикла + orders-history-archive.**
  Одна страница на звено цикла (недобор фактов → искажение P&L-разбивки);
  пагинация назад по `billId` / добрать archive-звено (см. «Evidence-cycle
  пагинация» выше; fills-часть снята вместе с `REFRESH_FILLS`).
- **[MAJOR] perf P-M3 — `getRequiredById` грузит attached** даже для
  submit/cancel. Разделить: лёгкий load без attached vs граф-load для
  refresh.
- **[MAJOR, design] D-M4 — корроборация RefreshPosition.** Пустой
  positions-ответ → CLOSED от одного чтения (соответствует докам, но
  транзиентные пустые ответы → ложный CLOSED). Рассмотреть корроборацию
  (повторное чтение / cross-check fills) до объявления close. Форвард-
  вопрос дизайна позиции.
- **[MINOR] perf — батчи/churn.** `saveAll` для attached/balances вместо
  per-row; upsert баланса вместо delete+insert; собрать изменённые
  ордера в один `saveAll`.
- **[MINOR] D-m1/D-m2 — подпись/эхо.** Clock-skew tolerance подписи OKX;
  лишние циклы refresh при пустом clOrdId-эхе.
- **[MINOR] conventions m2 — `getRequiredByInternalId`** (Order/AlgoOrder)
  сейчас не вызывается; если step-6/7 lookup так и не появится — удалить.

## Агентское ревью всей кодовой базы (2026-07-02) — 2 блокера, НЕ закрыты

Источник — `.claude/notes/2026-07-02-code-review-full-codebase.md` (420
Java-файлов, 20 подсистем, адверсариальная верификация; выжило 32 находки:
2 blocker / 4 major / 26 minor). **Правки по ревью не вносились** — это было
чистое ревью.

**Регистрация задним числом (свип курации при снапшоте v79):** ревью
существовало только заметкой, входящих ссылок из живых файлов не имело, и
**оба блокера в `backlog.md` не значились** — то есть исполнителю `CODE`
они были невидимы. Ровно тот класс, из-за которого рабочие файлы считаются
опаснее доков: они адресованы исполнителю.

- **🔴 B1. `CreateAlgoOrderActionExecutor.java:68` — стоп-лосс уходит на
  биржу без триггерной цены.** `createAlgoCommand` строит `Condition` с
  одним `type` и **никогда** не заполняет `trigger`/`trailing`; результат
  калькулятора (`getCalculatedPrice()`) игнорируется, читается только
  `getCalculatedSize()`. `validateConditionProjection` проверяет лишь
  `conditionType == condition.type` и пропускает. Итог: algo-ордер
  сабмитится без триггера — стоп, который никогда не сработает.
- **🔴 B2. `EntryFinalizedHandler.java:128` — бесстоповая позиция проходит
  в `MANAGING`.** Гейт `toManagingIfProtected` проверяет **наличие**
  attached-algo, не его **активность**; терминальный (CANCELED/ERROR)
  attached считается защитой. `ManagingHandler.checkEntry` перепроверяет
  только `positionLiveRisk`, защиту — никогда.
- **Связка B1 → B2:** B1 создаёт стоп без триггера ⇒ биржа его отвергает
  ⇒ B2 не ловит отсутствие активной защиты и пускает live-risk позицию в
  `MANAGING` бесстоповой. Это прямо противоречит
  `docs/rules/risk-creating-entry-protection.md` (требует увода в `ERROR`
  через `markErrorStopless`).

**Статус:** оба — в **ядре шага 6**, на котором шаг 7 стоит. Гейтом `CODE`
шага 7 формально не объявлены (шаг 7 их не вводил), но чинить их следует
**до** прогонов с реальным риском: это незащищённая позиция, которую петля
считает нормальной. Major/minor-хвост (4 + 26) — в самой заметке
поимённо, отдельного переноса не требует.

## Ретро-ревью шагов 1-3 (2026-06-11) — неблокирующий форвард-долг

Независимый адверсариальный code-review, ретроспективно достроенный по
шагам 1-3 (источник —
`.claude/work/history/2026-06-11-phase-1-steps-1-3-retro-adversarial-review.md`).
Блокеров нет, статусы `DONE` валидны.

- **Шаг 1.** `[MAJOR]` SYNC-overlap не реализован (`syncOverlapBars`
  объявлен, не зовётся; overlap от `pageSize`) — реализовать или удалить
  свойство. `[MINOR]` `repairAttempts` in-memory → поле на `CandleGroup`
  (гарантия «N попыток → ERROR» через рестарт); отброшенный return
  `saveCandles`; двойной `findByStatusIn` за тик.
- **Шаг 2.** `[MAJOR][PERF]` декартов join-fetch дерева
  (`StrategyRepository.findByInternalIdWithTree`) — разнести на 2
  fetch / `@EntityGraph`+`@BatchSize` (should-fix, бьёт с ростом дерева).
  `[MAJOR, error-convention]` 500 вместо 422 при гонке «одна ACTIVE» и
  500 вместо 409/идемпотентности при повторном POST — ловить нарушения
  `uk_strategy_active_per_instrument` / `uk_strategy_internal_id`
  (развилка 409-vs-идемпотентность — продуктовая). `[MINOR]`
  неиндексированные FK `strategy_actions.strategy_step_id` /
  `target_action_id`.
- **Шаг 3.** `[MINOR]` провизорные пороги-дефолты резолвера применяются
  молча (сигнал «дефолт применён»); двойная owner-простановка на
  UNKNOWN-ветке `MarketStructureJob`; `lookbackBars` без нижней границы
  перед `PageRequest.of`. `[NIT]` N+1 по таймфреймам (повторная загрузка
  окна для настроек одного инструмента).
- **Сквозное.** Ретро-майоры шагов 2 и 4 (коды ошибок) закрываются в
  одном месте по error-политике (`docs/rules/error-handling-policy.md`;
  кратко — `codestyle.md` §«Обработка ошибок»); конкретный набор
  HTTP-кодов и 409-vs-идемпотентность — провизорны (хвост пользователя).

## Методологические задачи (по итогам миграции)

Не cross-cutting миграции, а ревизии методологии по итогам прогонов.

### M1. Ревизия разделов «Чего не хранит» в мигрированных моделях

**Суть.** Decision `.claude/decisions/negative-statements-not-fixated.md`
отвергает раздел «Чего не хранит» как альтернативу C (отрицания
отбрасываются, позитив фиксируется там, где живёт). На практике при
миграции CC последовательно применяет более мягкое прочтение
(«отрицание + указатель на позитив = оставить») — разделы «Чего не
хранит» / «Что не хранит» появились в моделях. **Перед запуском
задачи решить:** либо уточнить decision и зафиксировать практику
(раздел разрешён в формате «отрицание + позитив»), либо почистить
модели по букве decision.

**Тип:** методологическая ревизия по итогам миграции.

**Сфера — накопительная.** Текущие затронутые модели:
- `docs/models/domain/core/Position.md` (§Что Position не хранит);
- `docs/models/domain/core/Order.md` (§Что Order не хранит);
- `docs/models/domain/aggregate/Deal.md` (§Runtime graph — «не входят / не
  хранятся»);
- `docs/models/domain/aggregate/Strategy.md` (§Что Strategy не хранит — через
  архитектурные инварианты);
- `docs/models/domain/other/AnomalyReport.md` (§Чего не хранит).

### M2. `BalanceContainer.externalUpdatedAt` → конвенционное имя

**Суть.** `GAPS_CLOSE_7` (H25) свёл имя «время события источника» к
конвенции `Auditable` (`externalCreatedAt`/`externalModifiedAt`) и запретил
заводить собственные имена под этот факт: `DealCashFlow.externalTs` →
`externalCreatedAt`, `TradeFeeRate.externalTs` → `externalModifiedAt`,
снапшот положения закрытия → `externalModifiedAt`. **`BalanceContainer`
намеренно не тронут**: его `externalUpdatedAt` введён на шаге 4, живёт в
домене, персистенции и api-ответе, на нём стоит freshness-check баланса —
переименование к шагу 7 отношения не имеет и тянет свою миграцию.

**Задача:** привести `BalanceContainer.externalUpdatedAt` (account-level и
currency-level) и его снапшоты к `externalModifiedAt`, либо зафиксировать
исключение решением. Носители: `docs/models/domain/core/BalanceContainer.md`,
`docs/models/mapping/Balance.md`, entity/api/миграция.

**Тип:** чистка именования, неблокирующая. Провенанс — H25
`DOCS_CHECK_7`.

## Инфра-долг (Boot 4 миграция / рантайм-робастность)

Вскрыто на первом реальном рантайм-старте (dev/test-сплит БД + Vault,
2026-06-12). Не cross-cutting миграция из архива — инфра/рантайм-долг
переезда стека.

### I1. Boot 3→4 split-autoconfig: durable-проверка

Переезд Boot 3→4 / Spring 7 / Hibernate 7 / JDK 25 раньше не гонялся в
рантайме — компиляция пробелы не ловит. Вскрыто 3 пробела
split-autoconfig: `RestClient.Builder` (→ `spring-boot-starter-restclient`),
Jackson 2 `ObjectMapper` (→ `spring-boot-jackson2`), Flyway
(→ `spring-boot-starter-flyway`) — все по шаблону «библиотека на classpath
есть, её `spring-boot-*` автоконфиг-модуль не подтянут → бин/фича молча не
активируется». **Durable-проверка на будущее** (при добавлении/обновлении
зависимости): «библиотека на classpath → её `spring-boot-*`
автоконфиг-модуль подтянут?». Особо коварны «тихие» стартовые автоконфиги
без инжекта бина (Flyway): без модуля не падают, просто ничего не делают.

### I2. Миграция кода на Jackson 3

Код на Jackson 2 (`com.fasterxml.jackson`); Boot 4 / Spring 7 дефолтят
Jackson 3 (`tools.jackson`). Бин `ObjectMapper` сейчас даём
совместимостным `spring-boot-jackson2` (интерим-adopt, `tech-radar`).
**Чистый end-state:** миграция кода (`RuntimeJsonConverter` /
`StrategyJsonConverter`, DTO-аннотации; `ObjectMapper.copy()` /
`setDefaultPropertyInclusion`, `JsonProcessingException`) на Jackson 3 и
снятие `jackson2`. Радар — Jackson 3 = `assess`.

### I4. Jackson 3 × Lombok beanspec мангли́нг в OKX-DTO — защита от рецидива

Системный класс, смежный I2. **Корень:** Jackson 3 (`tools.jackson`,
дефолт RestClient в SB4/Spring 7) выводит имя свойства из Lombok
beanspec-аксессора поля «строчная-первая/заглавная-вторая»
(`sCode`→`getsCode()`, `cTime`→`getcTime()`) иначе, чем JSON-ключ → поле
биндится в **null**. Точечная защита текущих OKX-DTO поставлена
(`@JsonProperty` на 7 полях + round-trip тесты `OkxAckDeserializationTest` /
`OkxReadDtoDeserializationTest`; итог —
`history/2026-07-14-claude-docs-curation.md`, run-log
`history/2026-06-20-source-api-contour/source-api-pilot-run-log.md` F3a/F4).

**Открыто (integrator, routing — НЕ в этом заходе):**
- **Защита от рецидива:** глобальный конфиг Jackson 3 (вернуть
  legacy-мангли́нг — широкий blast radius на всю десериализацию) **vs**
  конвенция «OKX-DTO аннотируют поля `@JsonProperty` + round-trip тест».
- **Репо-wide sweep:** эвристика lower-upper по текущим OKX-DTO
  исчерпана, но (а) не гарантирует все Jackson-3 edge-cases (аббревиатуры
  / all-caps), (б) будущие и иные источники DTO — на конвенцию/sweep.

**Влияние:** гейтит корректность любого read-снапшота с таймстампами
(`cTime`/`uTime`) → относится к **Фазе 3** prod read-only. Связано с I2
(миграция кода на Jackson 3 — целевой end-state).

## Средовой дефицит автономного RUN тестов (контур source-api)

Вскрыто эскалацией RUN пилота `source-api-testing` (2026-06-12):
`tester` не может прогнать demo-фазу автономно из shell CC — нет
headless-бута приложения и нет доступа к Vault-токену. Снять дефицит,
чтобы demo-прогоны не зависели от ручного бута. Состав:

- **`mvnw`/wrapper в репо** — headless-бут из shell (сейчас нет
  `mvn`/`mvnw`/собранного jar — приложение поднимает только пользователь
  через IDEA run-config).
- **Проброс Vault-токена в окружение прогона** — токен живёт только в
  IDEA run-config env; shell CC его не видит → `spring.config.import:
  vault://` из CC не проходит. Дать токен окружению автономного прогона
  (test-профиль).
- **Правило безопасности (инвариант роли `tester`):** автономно
  бутается **только `test`-профиль** (demo-креды, `x-simulated-trading=1`
  — prod-write технически невозможен). **`prod`-профиль — никогда
  автономно**, только под пользователем.

Снимает зависимость demo-прогонов от ручного бута. Источник — пауза RUN
пилота (run-log
`history/2026-06-20-source-api-contour/source-api-pilot-run-log.md`).

> Примечание: ре-база контура на сырьё
> (`.claude/decisions/source-api-target-rebase.md`) делает контур
> demo/non-prod и убирает prod из контура; prod read-only — ад-хок
> ручная проверка пользователя вне контура. Demo-бут по-прежнему нужен
> для автономного RUN код-тестов.
