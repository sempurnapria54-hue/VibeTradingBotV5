# Открытые вопросы

## На какой вопрос отвечает этот файл

Что мы ещё не решили (общие вопросы — пайплайн и продукт).

## Статус

Открыты продуктовые вопросы по финализации `Deal` (перенесены из
архивного `Deal.md` §15 при миграции, 2026-05-27), один вопрос,
обнаруженный при составлении карты артефактов миграции процессов
(проход 1, 2026-05-27), и четыре вопроса от миграции API-кластера OKX
(2026-05-28; OKX-Q1..Q4 — TradeFill, TradeFillsArchive, AccountBill /
DealCashFlow, WS-каналы как отдельный заход). Три вопроса — из шага 1
Фазы 1 (2026-05-30): INSTR-Q1 (`GAPS_CLOSE_2` — разграничение
`Instrument` / снапшот / `InstrumentExternalRules`), INSTR-Q2
(`GAPS_CLOSE_3` — изначально валидация рабочего плеча; освежён
2026-06-04 на `GAPS_CLOSE_7` шага 2 под решение «плечо динамическое»
— теперь роль статического плеча `Instrument` при динамическом
рабочем) и ORCH-Q1 (вынос процесса `candle-loading` — владелец
оркестрации онбординга и загрузки свечей). Один вопрос — из шага 2 Фазы 1:
STRAT-Q4 (`GAPS_CLOSE_2`, 2026-06-02 — percent-anchor, якорь
процент-смещения).
STRAT-Q1/Q2/Q3 закрыты 2026-06-02 решениями
`docs/decisions/strategy-condition-authoring-contract.md` (STRAT-Q1),
`strategy-signal-is-entry-condition.md` (STRAT-Q2),
`strategy-materialization-and-validation.md` (STRAT-Q3). STRAT-Q5
(представление условия в БД) закрыт 2026-06-03 на `GAPS_CLOSE_5`:
условие — JSONB на строке `strategy_step` по дефолту правила
персистентности (`docs/decisions/strategy-tree-persistence.md`
§Условие). Два вопроса — из `DOCS_CHECK_8` шага 2 (2026-06-05,
первый прогон торгового фокуса `trading-review`): RISK-Q2
(worst-case guard поверх вычисленного плеча; владелец — шаг 5) и
IND-Q1 (надёжность биржевого объёма / wash trading; владелец —
шаг 3). По итогам валидации делегирования 2026-06-06: PROC-Q1
закрыт (рудимент `PositionContext` снят из `CalculationContext.md`
применением инварианта «одна `Deal` — максимум одна `Position`»;
decision не заводился); CMD-Q1 закрыт решением
`.claude/decisions/executor-payload-file-granularity.md`
(file-per-executor; payload — раздел у своего executor'а), его
отложенный подвопрос вынесен в CMD-Q2; ENUM-Q1 снят без решения
как архивный артефакт (конфликт двух архивных доков; канон уже
стоит на `RISK_CONTROL`). Один вопрос — из закрывающего батча шага 3
(2026-06-09, торговая валидация): STRUCT-Q1 (калибровка числовых порогов
структуры — ER-порог тренда и k-толеранс — на бэктест-гейте фазы 2;
владелец — фаза 2). STRUCT-Q2 (идентичность `config_id` структуры vs разные
ER/ATR-входы) **закрыт** 2026-06-10 реверсом ключевания
(`docs/decisions/market-data-result-identity-keying.md`: результаты
ключуются настройкой-владельцем, разделяемого ряда нет). Открыты PHASE-Q1
(трек D, 2026-06-10): «липкость» / гистерезис фазы при stateless-резолве —
владелец `trading-review`; и PHASE-Q2 (трек D): размещение `MarketPhase`
после перехода в вычисляемое значение (RVO vs доменный computed value;
конфликт критериев из-за доменного enum `Type`) — владелец
`knowledge-curator`/`solution-designer`. Оба non-gating. DEAL-Q3
(размещение/структура `DealActionState`) закрыт 2026-06-10 на
`GAPS_CLOSE_1` шага 4 решением
`docs/decisions/deal-action-state-materialization.md` (материализован:
`domain/other` + own lifecycle, `RuntimeTarget` объектом, retry через
`Retryable`). CMD-Q2 (базовый тип payload'ов) закрыт 2026-06-10 на
`GAPS_CLOSE_1` шага 4 решением
`docs/decisions/service-command-payload-base-type.md` (маркер-база
`ServiceCommandPayload`, дискриминатор — `ServiceCommandType` на команде,
файл — дом базового типа). F1 (владение evidence-cycle refresh-команд)
закрыта 2026-06-10 на `GAPS_CLOSE_2` шага 4 решением
`docs/decisions/refresh-evidence-cycle-ownership.md` (обход внутри
исполнителя, вариант (a)). CMD-Q3 (судьба standalone pending/history
refresh-команд) закрыт 2026-06-10 (steer): refresh-набор — ровно по одной
команде на сущность, bulk-команды сняты из enum'а; открыт **CMD-Q4**
(перечисление неизвестных live orders/algo по инструменту — дыра от снятия
bulk). Из разбора ревью шага 4 (2026-06-12) открыты **CMD-Q5** (место
правила порядка ног REPLACE) и **CMD-Q6** (граница «действие стратегии vs
`ServiceCommand`» + классификация `KILL_SWITCH`) — оба парк на шаги 6-7.
На `GAPS_CLOSE_1` шага 5 (2026-06-20) закрыты **RISK-Q1** (нет RVO
`RiskSettings`; риск-настройки — поля `StrategyDetail`) и **RISK-Q2**
(worst-case guard экспозиции — уровень риска на биржу/портфель, отложен к
фазе 3; в фазе 1 — только риск на сделку) решением
`docs/decisions/per-trade-risk-policy.md`; **INSTR-Q1** (материализация
`InstrumentExternalRules` на шаге 5, JSONB-навес на `Instrument`, без
ренейма) и большая часть **INSTR-Q2** — решением
`docs/decisions/instrument-external-rules-materialization.md` (остаток
INSTR-Q2 — тайминг set-leverage, форвард к шагу 6).
На `GAPS_CLOSE_1` шага 6 (2026-06-22) закрыты **DEAL-Q1** (дом retry-state
финализации — отдельная сущность `DealFinalizationState`, решение
`docs/decisions/deal-finalization-state-materialization.md`), **DEAL-Q2**
(терминальный контракт при неисчислимой прибыли —
`docs/lifecycles/Deal.md` §«Терминальный контракт финализации»), **CMD-Q5**
и **CMD-Q6** (владелец оркестрации REPLACE + принцип «действие vs команда»,
решение `docs/decisions/action-orchestration-vs-command.md`). Сняты с TBD
error-политика (`docs/rules/error-handling-policy.md`) и форвард-долг
бесстопового risk-creating входа
(`docs/rules/risk-creating-entry-protection.md`). **Закрыт INSTR-Q2**
(остаток — представление write плеча: решено — inline-write в
`SubmitOrderExecutor` перед постановкой открывающего ордера, только для
открывающих, reduce-only пропускается, idempotent; as-built шага 6,
`docs/components/SubmitOrderExecutor.md`). **Продвинут CMD-Q4** (Precheck-часть
закрыта инструмент-скоупным read вне command-layer; orphan-часть — шаг 8).
**HOLD-Q1**
(L4-доминирование controlled-violation: любой `ControlledExchangeException`
на одной сделке гасит всю биржу), открытый на доработке холд-дельты шага 6
(2026-06-24), **закрыт** на заходе 1 разбора находок (2026-06-30) решением
`docs/decisions/controlled-violation-exchange-wide-hold.md` (вариант (1):
безусловный L4, доминирует L3; + переиспользуемый принцип консервативного
торможения под неизвестный радиус незрелой интеграции).

История закрытых вопросов пайплайна:

- Q1, Q2, Q3 закрыты решением
  `.claude/decisions/rule-source-of-truth.md` (2026-05-26).
- Q4 закрыт решением
  `.claude/decisions/chat-vs-cc-knowledge-split.md`.
- NQ-F закрыт решениями `.claude/decisions/runtime-value-object.md`
  и `.claude/decisions/models-core-vs-other.md` (2026-05-26).
- NQ-H закрыт решением
  `.claude/decisions/fsm-handler-as-component.md` (2026-05-27).
- NQ-G закрыт решением
  `.claude/decisions/master-index-not-fixated.md` (2026-05-27).

## Открытые продуктовые вопросы

### ORCH-Q1. Владелец оркестрации онбординга инструмента и загрузки свечей

Кто драйвит переходы `Instrument.Status`
(`CREATED → SYNC → CANDLES_LOADING → ACTIVE`) и `CandleGroup.Status`
(`BACKFILL`/`SYNC`/`CHECK`/`REPAIR`/`ACTIVE`/`ERROR`) и координирует
их между собой. Не решено при выносе процесса `candle-loading`
(2026-05-30). Поглощает вопрос, поднятый при закрытии `GAPS_CLOSE_2`
(владелец записи `Instrument.Status` был оставлен деталью `CODE`).

Варианты: (1) отдельный orchestrator-компонент + per-status
handler'ы по образцу FSM сделки (`docs/components/DealStateMachine.md`
+ handler'ы); (2) оркестрация внутри `CandleJob` + лёгкий координатор
инструмента; (3) событийная модель; (4) иное. До решения владелец
**не материализуется**: семантика переходов и координации зафиксирована
в lifecycle-доках, оркестрация описана процессом `candle-loading`
без привязки к компоненту-владельцу.

Горизонт (взвешивание срока, 2026-06-06): отложен — узкий
онбординг-кусок несёт провизорный seam шага 1 (`CandleJob`); широкое
мультиисточниковое владение `Instrument.Status` (FSM, AnomalyJob и
пр.) — отдельная ось (backlog п.9). Решать к концу фазы / когда
осядет ось владения статусом.
Связано: `docs/processes/candle-loading.md`,
`docs/lifecycles/Instrument.md`, `docs/lifecycles/CandleGroup.md`,
`docs/components/CandleJob.md`.

### CMD-Q4. Перечисление неизвестных live orders/algo по инструменту

CMD-Q3 закрыт (steer, 2026-06-10): refresh-набор — ровно по одной команде
на сущность (`REFRESH_ORDER`, `REFRESH_ALGO_ORDER`, `REFRESH_POSITION`,
`REFRESH_BALANCE`, `REFRESH_FILLS`); bulk-команды `REFRESH_PENDING_ORDERS` /
`REFRESH_ORDER_HISTORY` / `REFRESH_ALGO_ORDERS` / `REFRESH_ALGO_ORDER_HISTORY`
сняты, их эндпоинты живут звеньями внутреннего evidence-cycle
(`docs/decisions/refresh-evidence-cycle-ownership.md`).

Снятие bulk-команд оставляет **дыру** (подтверждена при чистке, не
достраивается): per-entity `REFRESH_*` покрывает только **известные**
сущности сделки. Перечисления **неизвестных** live orders/algo по
инструменту command-layer больше не предоставляет:

- `PrecheckHandler` — входная проверка «нет чужих live orders/algo» на
  инструменте перед входом;
- `AnomalyJob` (шаг 8) — orphan orders/algo (на бирже, нет в БД), хвосты
  после cleanup, чужой live risk;
- `ErrorHandler` / `ExitPendingHandler` — неизвестные live-хвосты.

(`REFRESH_POSITION` уже инструмент-скоупный — позиции-orphan покрыты; дыра
только по orders/algo.)

Варианты: (1) инструмент-скоупный exchange-read **вне command-layer** (read
в `IntegrationService`, дёргается job'ами/handler'ами для сверки/orphan-скана,
не `ServiceCommand`); (2) вернуть узкую scoped bulk-scan операцию (не
per-deal-команду); (3) иное по проработке anomaly / precheck-cleanliness.

**Продвинут на `GAPS_CLOSE_1` шага 6 (2026-06-22): принят вариант (1),
Precheck-часть закрыта.** Чистота инструмента перед входом берётся из
стартового инструмент-скоупного exchange-read **вне command-layer**
(`docs/components/IntegrationService.md` §«Инструмент-скоупный read»,
`docs/components/PrecheckHandler.md`); «оптовую команду» в command-layer не
возвращаем (вариант (2) отвергнут — отменил бы снятие bulk CMD-Q3). **Остаток
— orphan-скан шага 8** (`AnomalyJob`: чужие сущности при уже открытой сделке
и по неведомым инструментам). Владелец orphan-части — `solution-designer` /
шаг 8.

**Смежный вход (REPLACE-only, 2026-06-11):** при проработке
Precheck/AnomalyJob учесть легитимное **окно двойной reduce-only
защиты** во время REPLACE-ремодела (protective-порядок: новая
поставлена, старая ещё не отменена) — намерение видно из
`DealActionState` / цепочки `replacesInternalId`; не флагать
аномалией (`docs/decisions/replace-not-amend.md` §Следствия).
Связано: `docs/decisions/refresh-evidence-cycle-ownership.md`,
`docs/components/PrecheckHandler.md`, `docs/components/AnomalyJob.md`,
`docs/components/ServiceCommandExecutor.md`,
`docs/components/IntegrationService.md`.

### OKX-Q1. Persisted `TradeFill` модель и executor финализации

OKX endpoint'ы `GET /trade/fills` и `GET /trade/fills-history`
обеспечивают факты исполнения. На первом этапе `Fill` как persisted
entity не введён: `RefreshFillsExecutor` агрегирует filled-метрики в
существующие `Order`/`AlgoOrder`/`Position`. Не решено, нужна ли
отдельная persisted-сущность `TradeFill` (для аудита / detailed PnL /
recovery) и как именно она ложится на `Deal.resultProfit`.

Цитаты источника (архив, `Получить сделки за последние 3 дня REST.md`):
- «**fills ≠ ордера**: Order = заявка ... Fill = конкретная сделка
  (каждое исполнение ордера порождает 1 или несколько fills).»
- «`billId` — внутренний ID записи (используется как **якорь для
  пагинации** через `after/before`).»

Также в `docs/components/RefreshFillsExecutor.md` зафиксировано:
«`Fill` как отдельную persisted entity на первом этапе не вводим
(один общий `RefreshFillsExecutor`; материализация `TradeFill` —
backlog).»

Варианты: (1) ввести `docs/models/domain/other/TradeFill.md` + lifecycle (по
аналогии с `Order`/`AlgoOrder`) — даёт source-of-truth для PnL и
аудита; (2) оставить агрегацию в `Order`/`AlgoOrder`/`Position`, без
TradeFill — проще, но fills не персистятся отдельно. До решения
поля DTO зафиксированы в `docs/models/integrations/okx/OkxFillResponse.md`;
маппинг → snapshot не описан (откладывается).
Связано: `docs/models/integrations/okx/OkxFillResponse.md`,
`docs/models/mapping/TradeFill.md`,
`docs/components/RefreshFillsExecutor.md`,
`docs/models/domain/aggregate/Deal.md` §Итоговый PnL.

### OKX-Q2. Persisted `TradeFillsArchive` и async-флоу выгрузки

OKX endpoint'ы `POST/GET /trade/fills-archive` дают доступ к fills
старше 3 месяцев и до ~2 лет через двухшаговый async-флоу (генерация
файла → polling state → скачивание `fileHref`). На первом этапе ни
executor, ни persisted-сущность `TradeFillsArchive` не введены: текущий
runtime использует только `RefreshFillsExecutor` за последние 3
месяца. Не решено: нужен ли async-executor под архив (с long-running
polling state) и persisted-модель `TradeFillsArchive`.

Цитаты источника (архив, `Запрос генерации файла из архива сделок REST.md`):
- «**последние 3 месяца** — берёшь обычным `GET /api/v5/trade/fills-history`;
  **старше 3 месяцев и до ~2 лет** — через архив: сначала **POST
  (запросить генерацию)**, потом **GET (взять ссылку на файл)**.»
- «`result=false`, OKX пишет, что файл может генерироваться **долго
  (порядка десятков часов)**.»

Варианты: (1) materialize `TradeFillsArchive` с lifecycle
(`REQUESTED → ONGOING → FINISHED|FAILED`) + executor; (2) держать
архив вне runtime (off-band tool для аудита); (3) отложить до явной
потребности. До решения контракт endpoint'ов и поля responses
зафиксированы в `docs/models/integrations/okx/OkxFillsArchiveResponse.md` и
`docs/integrations/okx/contracts/fills-archive.md`.
Связано: `docs/models/integrations/okx/OkxFillsArchiveResponse.md`,
`docs/integrations/okx/contracts/fills-archive.md`, OKX-Q1.

### OKX-Q3. Bills (`account/bills`) как источник `DealCashFlow`

OKX endpoint'ы `GET /account/bills` (7d) и `/account/bills-archive`
(3m) дают записи движения денег по аккаунту: realized PnL,
комиссии/rebate, funding, прочие cashflow-события. Для итогового
`Deal.resultProfit` bills могут быть **полнее** fills, потому что
включают funding и rebate, не привязанные к конкретному ордеру.
Доменно `AccountBill` / `DealCashFlow` не введены; вопрос — нужны ли
сейчас.

Цитаты источника (архив, `Получить bill-записи аккаунта за последние 7 дней REST.md`):
- «В отличие от fills, bills показывают именно **изменение денег на
  аккаунте**, а не только факт исполнения ордера.»
- «Для финального `Deal.resultProfit` bills могут быть точнее, потому
  что туда попадают не только trade executions, но и funding.»
- Рекомендуемая логика (архив): «Запросить bills ... Сохранить как
  DealCashFlow ... `Deal.resultProfit = sum(DealCashFlow.amount)`.»

Варианты: (1) ввести `docs/models/domain/other/DealCashFlow.md` + executor
`RefreshDealCashFlowExecutor` (или общий `RefreshDealFinalizationExecutor`
с fills + bills) — даёт самый точный PnL; (2) считать PnL только через
fills (без funding/rebate) — проще, но менее точно; (3) отложить до
явной потребности. До решения контракт endpoint'ов и поля responses —
`docs/models/integrations/okx/OkxAccountBillResponse.md` и
`docs/integrations/okx/contracts/account-bills.md`.

**Смежный вход (В-6, скан интегратора 2026-06-11):** funding в P&L
достижим **двумя путями** — bills `subType` 173/174 (фактические
списания/начисления по аккаунту) и публичный
`funding-rate-history.realizedRate` (ставки расчётных периодов, без
привязки к позиции) — `docs/integrations/okx/contracts/funding-rate.md`.
Шаг 7 выбирает один путь осознанно (bills точнее для фактического
P&L; ставки — для прогноза/сверки), не ведёт два параллельных трека.
Deep-архив bills с 2021 теперь существует
(`account-bills.md` §Deep-архив) — снимает прежнее «глубже 3 месяцев
пути нет».

Связано: `docs/models/integrations/okx/OkxAccountBillResponse.md`,
`docs/integrations/okx/contracts/account-bills.md`,
`docs/integrations/okx/contracts/funding-rate.md`,
`docs/models/domain/aggregate/Deal.md` §Итоговый PnL. (Финализационная
механика и терминальный контракт — DEAL-Q1/DEAL-Q2 **закрыты** на шаге 6:
`docs/decisions/deal-finalization-state-materialization.md`,
`docs/lifecycles/Deal.md` §«Терминальный контракт финализации»; здесь —
*расчёт* PnL, шаг 7.)

### OKX-Q4. WS-каналы OKX — отдельный заход

В архивном источнике WS-каналы покрыты только обзорной таблицей: имена
(`account`, `positions`, `orders`, `balance_and_position`, `tickers`,
`candle<bar>`, `instruments`, `algo-orders`, `algo-advance`) и краткое
назначение. Детального описания протокола подписок, push-сообщений,
матчинга `orders` events с runtime-фактами — нет. Текущая миграция
покрывает REST. WS — отдельный заход.

Цитаты источника (архив, `okx_api_for_trading_bot_v5.md`):
- «WS канал `orders` **не даёт начальный snapshot**. Он начинает слать
  события **только при изменениях**.»
- WS-каналы фигурируют в обзорной таблице операций без отдельных
  файлов; в каждом REST-файле есть короткая WS-заметка («WS — основной
  realtime-канал», «REST — fallback»).

Варианты: (1) выделить кластер `docs/integrations/okx/ws/` или ввести
`docs/integrations/okx/rules/okx-ws-channels.md` (один файл = один канал
либо один файл = все каналы) — после сбора реальных push-примеров;
(2) держать WS-альтернативу пометками внутри существующих
`okx-*-mapping.md`, без отдельных файлов. До решения WS-каналы описаны
только короткой пометкой в mapping-файлах; в `okx-service-urls.md`
зафиксированы base URL public/private/business.
Связано: `docs/integrations/okx/contracts/service-urls.md`,
`docs/integrations/okx/rules/ws-limits.md`,
`docs/models/mapping/Order.md` (примеры WS-альтернатив).

Статус (GAPS_CLOSE_1, 2026-05-29): шаг 1 — REST-first (WS отложен),
поэтому OKX-Q4 **шаг 1 не блокирует**. Якорь пересмотра — рефакторинг
на микросервисы (архитектурный рубеж роадмапа). Контрактные доки
рыночных данных приведены к REST-first
(`docs/integrations/okx/contracts/market-price-data.md`,
`docs/models/mapping/MarketPriceData.md`).

### STRAT-Q4. percent-anchor: «−N% относительно чего»

При авторинге условий/действий процент-смещение (`percents` / offset)
задаёт сдвиг «−N% относительно чего»: вход в позицию, предыдущая свеча,
хай/лоу диапазона и т. п. Чистая бизнес-семантика якоря процента —
какие якоря допустимы и как они выражаются в контракте — не
зафиксирована. Вынесено из STRAT-Q1 (контракт авторинга условия,
закрыт 2026-06-02) как самостоятельная бизнес-развилка.

До решения конкретные якоря не перечисляются; поля `percents` /
`offsetSide` в `Strategy.md` остаются как есть, интерпретация якоря —
деталь реализации соответствующего `ruleType` / placement.
Связано: `docs/models/domain/aggregate/Strategy.md` (§Условия,
§StrategyPricePlacement — `percents`/`offsetSide`),
`docs/decisions/strategy-condition-authoring-contract.md`.

### IND-Q1. Надёжность биржевого объёма для volume-условий (wash trading) (владелец — шаг 3)

**Частично закрыт (2026-06-09) — книжная часть.** `VOLUME_FILTER_PASSED`
и OBV опираются на биржевой объём. Книжная часть закрыта решением
`docs/decisions/volume-condition-semantics.md` (грунт Kaufman гл. 12 /
Harris гл. 12, провалидировано): объём манипулируем (wash trades / «paint
the tape» [Harris, гл. 12, с. 259-260, 273-274]) → объёмное условие —
**подтверждающий фильтр, не единственное основание `ENTRY`**; OBV-операнд
ограничен **относительными формами** (абсолютный `CONSTANT`-compare
исключён — OBV кумулятивен, ТР2).

**Остаётся открытой крипто-часть.** Крипто-специфика надёжности
**спот-объёма CEX** (накрутка / фейковый объём на нерегулируемых биржах,
надёжность по площадкам/ликвидности) — в корпусе **∅**, подтверждено
прямой вычиткой сырых книг
(`.claude/library/trading/distilled/microstructure.md` §9): крипту
застали только Kaufman (2019) / Carver AFTS (2023), обе трактуют её как
торгуемый инструмент (Carver — через регулируемые фьючерсы), достоверности
спот-объёма не касаются. **Эскалация:** корпусу не хватает
крипто-микроструктурного источника по интегритету объёма — подбор
источника в **контуре дообучения (в чате)**; до него крипто-часть не
закрывается. Внешний (не-книжный) добор зафиксирован в snapshot v37 §ТР1,
в корпусные тезисы не вносился.

**Якорь — фаза 4.** Крипто-надёжность спот-объёма CEX решается в **фазе
4** (анализ рынка + заведение новых инструментов), где встаёт продуктовый
подбор инструмента/площадки и заводится будущий аналитик торгового
совета. До фазы 4 крипто-микроструктурный источник **не добирается**:
(1) в фазе 1 инструмент и стратегия задаются пользователем **вручную** —
острый случай накрутки объёма под ручным контролем пользователя;
(2) объёмное условие уже ограничено ролью **подтверждающего фильтра** (не
единственное основание `ENTRY`, `docs/decisions/volume-condition-semantics.md`)
— даунсайд прикрыт. Книжный контур дообучения корпуса (подбор
крипто-микроструктурного источника) запускается **к фазе 4**.

Находка ТР1 `DOCS_CHECK_5` (= ТР4 `DOCS_CHECK_8`, торговый фокус
`trading-review`). Затрагивает авторинг стратегии (чек-лист СТ-1,
применён на `CODE` шага 2; архив —
`.claude/work/history/2026-06-05-phase-1-step-2-strategy/tasks-phase-1-step-2-strategy.md`).
Связано: `docs/decisions/volume-condition-semantics.md`,
`docs/models/domain/aggregate/Strategy.md` (§Условия),
`docs/models/domain/other/IndicatorValue.md` (OBV),
`docs/components/IndicatorJob.md`.

### STRUCT-Q1. Калибровка числовых порогов структуры рынка (ER-порог тренда, k-толеранс) (владелец — фаза 2)

Закрывающий батч шага 3 (D2/D3, 2026-06-09) вынес два числовых порога
резолвера структуры из захардкоженных констант в `MarketStructureParams`
(per-конфиг): `trendEfficiencyThreshold` (ER ≥ порога → тренд vs диапазон,
D2) и `levelToleranceAtrMultiplier` (k в толерансе кластеризации = k·ATR,
D3). **Подход** грунтован корпусом (ER-дискриминация тренд/шум [Kaufman
гл. 1 «Measuring Noise», гл. 17 KAMA]; свинг-фильтр/толеранс относительно
волатильности [Kaufman гл. 5, гл. 8]), но **числовые значения — нет**:
корпус не даёт ни бинарной ER-отсечки (ER используется континуально в
KAMA), ни конкретного k. Значения — **пользовательский хвост**, калибруются
на **бэктест-гейте фазы 2** (анализ робастности: широкая зона успеха, не
пик — [Kaufman гл. 21]).

До калибровки резолвер применяет **провизорные консервативные дефолты**
(ER-порог 0.30; k 0.5), если поля не заданы в `MarketStructureParams`;
числом в канон не зашиваются (в доках помечать «value: бэктест»). Находки
ТВ1/ТВ3 торговой валидации шага 3 (`trading-review`).

Связано: `docs/models/domain/aggregate/Strategy.md` (§MarketStructureParams),
`docs/models/domain/other/MarketStructure.md` (§Семантика классификации),
`docs/components/MarketStructureResolver.md`,
`.claude/library/trading/distilled/strategy-patterns.md` §4,
`.claude/library/trading/distilled/system-design.md` §5.

### PHASE-Q1. «Липкость» / гистерезис фазы при stateless-резолве (владелец — `trading-review`)

Трек D (2026-06-10) сделал `MarketPhase` **stateless — вычисляется на лету,
не персистится** (`docs/decisions/market-phase-stateless.md`). Резолв на
лету по текущим индикаторам/структурам не даёт **гистерезиса/
подтверждаемости** фазы: у границы режимов фаза может перескакивать тик за
тиком. Сейчас анти-whipsaw — только операнд-уровневый (сглаживающие периоды
индикаторов, структурный `breakoutConfirmationBars`,
`docs/decisions/market-phase-conditional-classification.md` §Анти-whipsaw).

Не решено: нужна ли фазе **отдельная подтверждаемость / гистерезис** поверх
операнд-уровневого сглаживания (hold-N-баров на смену фазы и т. п.), и если
да — как выразить без хранимого состояния истории фаз (источник истории —
готовая структура, не фаза; см. «дверь на будущее» в
`docs/models/domain/aggregate/Strategy.md` §StrategyMarketPhaseRule).
Приемлемость остаточного перескока как численный риск-аппетит автора уже
принята пользователем, но stateless-переход вопрос обостряет.

**Торговый грунт (trading-review пост-D, ТВ-1/ТВ-2).** Корпус: режим
флип-флопит при слишком отзывчивом дискриминаторе (одиночная быстрая MA
«дёргается» — Carver AFTS стр. 5), а анти-whipsaw встроен в **конструкцию
дискриминатора** (медленный кроссовер; KAMA — адаптивная скорость EMA из
efficiency ratio, Kaufman гл. 17), не в отдельный stateful-дебаунс ярлыка
режима. ⇒ операнд-уровневый механизм модели корпусно-грунтован; жёсткий гейт
«не выражает торговое правило» не срабатывает. Если гистерезис понадобится —
опоры ввода: **KAMA-адаптивная скорость** входов / документированный
инкремент **`confirmationBars`** на сравнивающее правило
(`docs/decisions/market-phase-conditional-classification.md` §Анти-whipsaw),
не stateful debounce. Смягчающий фактор (ТВ-2): перескок фазы влияет только
на выбор детали при **входе** — открытые сделки идут по pinned detail, не
«треплются» (`docs/lifecycles/Strategy.md`). Полный разбор —
`.claude/work/history/2026-06-10-phase-1-step-3-derived-market-data/phase-1-docs-check-post-revision-d.md` §Торговый фокус.

Владелец — торговый ревью (`trading-review`) со специалистом; горизонт — по
ходу торговой проработки фазы. До решения дополнительный гистерезис не
вводится (S0), анти-whipsaw остаётся операнд-уровневым.
Связано: `docs/decisions/market-phase-stateless.md`,
`docs/decisions/market-phase-conditional-classification.md`,
`docs/models/domain/aggregate/Strategy.md` (§StrategyMarketPhaseRule),
`docs/components/MarketPhaseResolver.md`.

### PHASE-Q2. Размещение `MarketPhase` после перехода в вычисляемое значение (классификация)

Трек D сделал `MarketPhase` вычисляемым на лету (не персистится). По
структурному критерию RVO (`.claude/decisions/runtime-value-object.md`: не
persisted / без identity / без lifecycle — носитель данных) он подходит под
**Runtime value object** (`docs/components/models/`). Но тот же критерий
требует «не доменная сущность», а `MarketPhase.Type` — **доменный enum**
рыночного режима, вшитый в strategy-layer (`StrategyDetail.marketPhaseType`,
`phaseRules`, `MARKET_PHASE_IS`); по codestyle enum'ы живут только в домене.
Критерии конфликтуют — развилка чисто в доках не снимается.

Варианты: (а) перенести `MarketPhase` в `docs/components/models/` как RVO —
тогда `MarketPhase.Type` выносится отдельным доменным enum (сопутствующая
**код-правка**: извлечь enum, иначе домен зависит от компонентного слоя);
(б) признать `MarketPhase` **доменным вычисляемым value-объектом** и оставить
в `docs/models/domain/other/` (ось «вычисляемое vs хранимое» — новый тонкий
признак, по которому `other` пока не дробится,
`.claude/decisions/models-core-vs-other.md`); (в) новый тип «доменный computed
value». Не гейтит (модель работает как значение).

До решения файл остаётся `docs/models/domain/other/MarketPhase.md` с
форвард-заметкой о развилке; владелец — `knowledge-curator` /
`solution-designer` (классификация + возможная код-правка извлечения enum).
Горизонт — когда осядет ось «computed domain value» (вероятно, с RVO-кластером
Deal management, шаг 4+).
Связано: `docs/models/domain/other/MarketPhase.md`,
`.claude/decisions/runtime-value-object.md`,
`.claude/decisions/models-core-vs-other.md`,
`docs/decisions/market-phase-stateless.md`.

## Конвенция

Новые открытые вопросы добавляются сюда по мере появления. Закрытый
вопрос удаляется отсюда; история закрытия живёт в соответствующем
decision (конвенция из
`.claude/decisions/chat-vs-cc-knowledge-split.md`).
