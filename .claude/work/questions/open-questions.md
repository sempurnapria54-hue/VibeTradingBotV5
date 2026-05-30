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
(`GAPS_CLOSE_3` — валидация рабочего плеча и роль `externalLeverage`)
и ORCH-Q1 (вынос процесса `candle-loading` — владелец оркестрации
онбординга и загрузки свечей).

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

### DEAL-Q1. Где хранить persisted retry-state финализации сделки

Lifecycle/finalization commands (`REFRESH_FILLS`, `FINALIZE_DEAL_EXIT`,
`MARK_DEAL_CLOSED`, emergency finalization) нуждаются в persisted
retry-state, но `DealActionState` относится к `StrategyAction`, а
финализация сделки — это lifecycle/system action. Audit/history не
должен быть runtime-source, поэтому retry-state финализации нельзя
хранить только в истории. Где его хранить — не решено.
Связано: `docs/models/domain/aggregate/Deal.md`, `docs/lifecycles/Deal.md`.

### DEAL-Q2. Что делать, если resultProfit нельзя посчитать после исчерпания retry

Зафиксировано: `resultProfit`/`resultProfitCurrency` обязательны для
`CLOSED`/`EMERGENCY_CLOSED`; `resultProfit = 0` допустим только как
результат расчёта, не fallback. Не решено, что делать, если после
всех retry итоговый PnL всё ещё нельзя безопасно посчитать. Варианты
на будущее: отдельный finalization state; перевод в `ERROR`;
отдельный `DealFinalizationState`; ручной разбор; специальный
operational flag без нарушения terminal semantics.
Связано: `docs/models/domain/aggregate/Deal.md` §Итоговый PnL.

### PROC-Q1. Существует ли `PositionContext` как самостоятельный RVO

Противоречие между двумя архивными процессными доками. Модель
`CalculationContext` («Калькуляторы действий стратегии» §4) содержит поле
`private PositionContext positionContext;` (отдельно от `activePosition`).
Но «Жизненный цикл сделки» §5.3 явно исключает `PositionContext` из
`DealContext`: в рамках одной `Deal` допускается максимум одна `Position`,
а отдельный контейнер не нужен. Не решено, существует ли `PositionContext`
как доменный runtime value object или это рудимент. Влияет на состав RVO
при миграции процессов (проход 2).

Цитаты источника:
- «Калькуляторы действий стратегии» §4 (модель `CalculationContext`):
  `private Position activePosition;` и отдельно
  `private PositionContext positionContext;` (комментарий: «Состояние
  позиций по инструменту»).
- «Жизненный цикл сделки» §5.3 «Что специально не входит в DealContext»:
  отдельный `PositionContext` не нужен — в рамках одной `Deal`
  допускается максимум одна `Position`.

Варианты: (1) `PositionContext` — рудимент, не материализовать, в
`CalculationContext` оставить только `activePosition`; (2) существует как
самостоятельный RVO (мультипозиционный контекст по инструменту) →
`docs/components/models/PositionContext.md`.
Связано: `docs/components/models/CalculationContext.md` (поле помечено),
`tasks-калькуляторы-действий-стратегии.md` (КЛ-Q1),
`tasks-жизненный-цикл-сделки.md` (ЖЦ-Q2),
`.claude/work/progress/progress-карта-артефактов.md`.

### RISK-Q1. Структура и материализация `RiskSettings`

`RiskSettings` упомянут как поле `CalculationContext` и как вход
`RiskValidator`, но нигде в архиве не описан детально — структура полей
неизвестна. Возможно, это часть `StrategyDetail`
(`riskPerTradePercent` / `maxLeverage`) или отдельный RVO. Только
name-level. Материализация под вопросом.

Цитаты источника:
- «Калькуляторы действий стратегии» §4 (`CalculationContext`):
  `private RiskSettings riskSettings;` (комментарий: «Настройки риска из
  StrategyDetail или глобальной risk policy»).
- «Оценка рисков» §2.1 (Strategy-layer) — risk-настройки живут в
  стратегии: `StrategyDetail.riskPerTradePercent`,
  `StrategyDetail.maxLeverage`, `StrategyOrderAction.allocationPercents`
  и др. §2.3 (RiskValidator) — `RiskSettings` указан среди входов
  `RiskValidator` (структура не приведена).

Варианты: (1) не отдельный RVO — risk-настройки берутся из
`StrategyDetail` (`riskPerTradePercent`/`maxLeverage`) + глобальная risk
policy; (2) самостоятельный RVO `docs/components/models/RiskSettings.md`,
когда станет известна структура.
До решения отдельный файл не создаётся; упоминается с пометкой «структура
— RISK-Q1».
Связано: `docs/components/models/CalculationContext.md`,
`docs/components/RiskValidator.md`.

### TIME-Q1. Где разместить доменный enum `TimeFrame`

`TimeFrame` — чистый доменный enum для таймфреймов свечей/индикаторов,
OKX-строк не хранит. Размещение неясно: сейчас описан разделом в
`docs/models/domain/aggregate/Strategy.md` (используется многими настройками
strategy-tree), но как самостоятельный enum может жить иначе.

Цитата источника (архив, «Расчёт индикаторов и рыночных данных» §8):
«`TimeFrame` — чистый доменный enum. OKX-строки в нём не храним.»
Значения: `ONE_MINUTE`, `THREE_MINUTES`, `FIVE_MINUTES`,
`FIFTEEN_MINUTES`, `ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `ONE_DAY`.
Маппинг OKX-строк живёт отдельно (`TimeFrameMapper` /
`docs/models/mapping/TimeFrame.md`).

Статус (GAPS_CLOSE_1, 2026-05-29): `CandleGroup.timeframe` (целевой
тип `TimeFrame`) сделал enum явной кодовой зависимостью свечной
подсистемы. По критерию первоисточника каноническое описание enum
размещено разделом в `docs/models/domain/other/CandleGroup.md`
(§«Енум `TimeFrame`»); `docs/models/mapping/TimeFrame.md` указывает
туда. Для кода шага 1 вопрос закрыт (enum определён и размещён, шаг 1
не блокирует).

Остаточный хвост: раздел `TimeFrame` в
`docs/models/domain/aggregate/Strategy.md` (шаг 2) свести до ссылки
на канон в `CandleGroup.md`, чтобы не дублировать определение.
Делается при проработке шага 2 (Стратегия).
Связано: `docs/models/domain/other/CandleGroup.md` (§Енум),
`docs/models/domain/aggregate/Strategy.md` (§TimeFrame),
`docs/models/mapping/TimeFrame.md`,
`docs/models/domain/other/IndicatorValue.md` / `MarketStructure.md` /
`MarketPhase.md` (через settings).

### INSTR-Q1. Как снапшот-концепция ляжет на `InstrumentExternalRules` (и нужен ли ренейм)

Шаг 1 (поток рыночных данных) развёл модель инструмента так: домен
`Instrument` держит идентичность (+ онбординг-статус,
`plannedCandleStartDate`, биржевые `externalStatus`/`externalLeverage`
из снапшота, рабочее `leverage` из создания), а справочные
sizing/rounding-поля спецификации (base/quote/settle, sizes)
приходят транзиентно в `InstrumentExternalSnapshot` и в шаге 1
персистентно **не** хранятся. Биржевые `state`/`lever` (OKX) с
шага 1 персистятся на `Instrument` (`externalStatus`/
`externalLeverage`), а не на rules (`GAPS_CLOSE_3`). Модель
`InstrumentExternalRules` (persisted sizing/rounding-правила) для
шага 1 отложена (округление/sizing/риск — поздние шаги; backlog
п.9) и на base/quote/settle больше не претендует — этим снят дубль
Н1 (`DOCS_CHECK_2`).

Не решено (всплывёт при материализации rules на поздних шагах): как
именно снапшот-концепция (`InstrumentExternalSnapshot` —
транзиентная граница) соотнесётся с persisted
`InstrumentExternalRules` — отдельные ли это сущности, одна ли
материализуется из другой, где окончательно живёт персистентный дом
справочных полей; и не потребуется ли в результате **ренейм**
`InstrumentExternalRules` (например, к снапшот-неймингу).

Варианты на будущее: (1) `InstrumentExternalRules` остаётся
самостоятельной persisted-моделью, материализуется из снапшота;
(2) переосмыслить как persisted-проекцию снапшота с ренеймом;
(3) иное по итогам проработки шагов округления/sizing/риска. До
решения rules не материализуется; в шаге 1 справочные поля — только
транзиентный снапшот.
Связано: `docs/models/domain/core/Instrument.md`,
`docs/models/mapping/Instrument.md`,
`docs/models/domain/other/InstrumentExternalRules.md`,
`docs/models/mapping/InstrumentExternalRules.md`, backlog п.9.

### INSTR-Q2. Валидация рабочего плеча и роль `externalLeverage`

Рабочее `leverage` инструмента (`Instrument.leverage`, `Integer`,
задаётся при создании) не должно превышать конфиговый максимум
плеча. При создании / обновлении / прочих операциях с инструментом
превышение трактуется как нарушение торгового правила: инструмент
**не выпускается на биржу** — переводится в `HOLD`. Шаг 1 (поток
рыночных данных, онбординг-путь `CREATED → SYNC → CANDLES_LOADING →
ACTIVE`) этого не требует и **не блокируется** — вопрос
прорабатывается позже.

Открытые аспекты:
- Роль биржевого `externalLeverage` (OKX `lever`, сырое значение на
  `Instrument`) как биржевого потолка плеча: выступает ли он
  верхней границей для рабочего `leverage` и как соотносится с
  конфиговым максимумом и с rules-полем `externalMaxLeverage`
  (`docs/rules/trading-constraints.md` — лимит плеча сейчас сослан
  на `InstrumentExternalRules.externalMaxLeverage`;
  `docs/models/domain/other/InstrumentExternalRules.md`).
- Состояние / действие `HOLD` в lifecycle инструмента: в текущем
  материализованном онбординг-пути шага 1 `HOLD` нет
  (`docs/lifecycles/Instrument.md`; периферийные статусы отложены —
  backlog п.9). Нужен ли переход в `HOLD` при нарушении правила
  плеча и кто его инициирует — не решено.

Связано: `docs/models/domain/core/Instrument.md` (`leverage`,
`externalLeverage`), `docs/lifecycles/Instrument.md`,
`docs/rules/trading-constraints.md`,
`docs/models/domain/other/InstrumentExternalRules.md`,
`docs/models/mapping/InstrumentExternalRules.md`, INSTR-Q1.

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
Связано: `docs/processes/candle-loading.md`,
`docs/lifecycles/Instrument.md`, `docs/lifecycles/CandleGroup.md`,
`docs/components/CandleJob.md`.

### ENUM-Q1. closeReason `RISK_CONTROL` vs `ENTRY_RISK_BLOCKED`

Конфликт значения `Deal.closeReason` при risk-block в `PRECHECK` (до live
risk) между двумя архивными процессными доками.

Цитаты источника:
- «Оценка рисков» §8.1: при `BLOCKED` в `PRECHECK` без live risk —
  `Deal.status = CLOSED`, `Deal.closeReason = RISK_CONTROL`; «Отдельный
  `ENTRY_RISK_BLOCKED` не используем».
- «Аудит и история исполнения» §7.1 (старше, черновое): `closeReason`
  может быть `ENTRY_RISK_BLOCKED` «или другое согласованное значение».

`docs/lifecycles/Deal.md` и `docs/models/domain/aggregate/Deal.md` уже используют
`RISK_CONTROL` (в списке `CloseReason` `ENTRY_RISK_BLOCKED` помечен как не
используемый). Решённый по букве risk-доки и lifecycle вариант —
`RISK_CONTROL`; аудит-док даёт устаревшую формулировку.

Вариант: подтвердить `RISK_CONTROL`, `ENTRY_RISK_BLOCKED` окончательно
отвергнуть (закрыть вопрос ссылкой на decision). До закрытия список
значений `closeReason` в `Deal.md` **не меняется**.
Связано: `docs/models/domain/aggregate/Deal.md` (§Енумы, `CloseReason`),
`docs/lifecycles/Deal.md`,
`.claude/work/questions/tasks/tasks-оценка-рисков.md` (ОР-Q1),
`.claude/work/questions/tasks/tasks-аудит-и-история-исполнения.md` (АУ-Q2).

### CMD-Q1. Гранулярность файлов executor'ов и payload'ов

Не решено, как гранулировать документацию command-layer: файл на каждый
executor / payload (file-per-X) или группировка по семантике
(CREATE_*/SUBMIT_*/AMEND_*/CANCEL_*/REFRESH_*; один файл payload'ов с
разделами).

Цитаты источника:
- «Сервисные команды» §13 описывает детально ~14 executor'ов
  (`CreateOrderExecutor`, `SubmitOrderExecutor`, `RefreshOrderExecutor`,
  `AmendOrderExecutor`, `CancelOrderExecutor` и их algo-аналоги,
  `RefreshPositionExecutor`, `ClosePositionExecutor`, `RefreshFillsExecutor`,
  `RefreshBalanceExecutor`); refresh-executor'ы под `REFRESH_PENDING_ORDERS`
  / `REFRESH_ALGO_ORDERS` / `REFRESH_ORDER_HISTORY` /
  `REFRESH_ALGO_ORDER_HISTORY` упомянуты без отдельных секций.
- «Сервисные команды» §10 даёт 9+ payload-классов (Create/Submit/Amend/
  Cancel Order/AlgoOrder, ClosePosition, AttachedProtection).

Текущее решение прохода 2 (до закрытия вопроса): executor'ы —
file-per-executor (`docs/components/<X>Executor.md`); payload'ы — один файл
`docs/components/models/ServiceCommandPayload.md` с разделами; четыре
refresh-executor'а без отдельных секций отдельными файлами не заводятся
(покрыты общей семантикой `REFRESH_*` и `ServiceCommandType`).
Варианты на будущее: подтвердить file-per-executor либо сгруппировать;
для payload'ов — оставить разделами либо вынести в отдельные файлы.
Связано: `docs/components/*Executor.md`,
`docs/components/ServiceCommandExecutor.md`,
`docs/components/models/ServiceCommandPayload.md`.

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
Связано: `docs/models/integrations/okx/OkxAccountBillResponse.md`,
`docs/integrations/okx/contracts/account-bills.md`,
`docs/models/domain/aggregate/Deal.md` §Итоговый PnL, DEAL-Q1, DEAL-Q2.

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

### DEAL-Q3. Размещение `DealActionState` (domain layer + own lifecycle)

`DealActionState` — persisted операционная модель runtime-состояния
выполнения `StrategyAction`. Не торговая бизнес-сущность в смысле PnL, но
тесно связана с сопровождением сделки. Не решено: `docs/models/domain/aggregate/`
(тесная связь с сопровождением сделки) или `docs/models/domain/other/`
(прочая хранимая); нужен ли отдельный lifecycle (есть status-enum).

Цитаты источника:
- «Сервисные команды» §6: `public class DealActionState extends Retryable`
  с полями `id`, `dealId`, `strategyActionId`, `target` (`RuntimeTarget`),
  `status` (`DealActionStateStatus`); инвариант `UNIQUE(deal_id,
  strategy_action_id)`; `strategyActionId` не хранится в
  `Order`/`AlgoOrder`/`Position`. `DealActionStateStatus`: `PLANNED`,
  `CREATED`, `SUBMITTED`, `COMPLETED`, `RETRY_PENDING`, `FAILED`,
  `SKIPPED`. `RuntimeTarget`: `entityType` (`TargetEntityType`: ORDER /
  ALGO_ORDER / POSITION / DEAL / BALANCE / NONE), `entityId`.
- «Жизненный цикл сделки» §7 — описание `DealActionState` с полями `id`,
  `dealId`, `strategyActionId`, `targetEntityType` (`TargetEntityType`),
  `targetEntityId`, `status` (`DealActionStateStatus`), `attemptCount`,
  `lastError` (`RetryError`). Отличие от СК §6: ЖЦ инлайнит
  `targetEntityType`/`targetEntityId` и retry-поля прямо в класс, СК §6
  выносит `RuntimeTarget` объектом и наследует от `Retryable`. Выбор
  представления — часть DEAL-Q3.

Варианты: (1) `docs/models/domain/other/DealActionState.md` + отдельный
`docs/lifecycles/DealActionState.md` (status-enum как FSM); (2)
`docs/models/domain/aggregate/DealActionState.md` (тесная связь с
сопровождением сделки); (3) без отдельного lifecycle (статусы — раздел
модели).
До решения файл модели **не материализуется**; в местах использования
(`ServiceCommand`, executors, FSM handlers) упоминается с пометкой
«структура и размещение — DEAL-Q3».
Связано: `docs/components/models/ServiceCommand.md`,
`docs/components/ServiceCommandFactory.md`, executor-компоненты,
`docs/components/RetryPolicyService.md` (база `Retryable`).

## Конвенция

Новые открытые вопросы добавляются сюда по мере появления. Закрытый
вопрос удаляется отсюда; история закрытия живёт в соответствующем
decision (конвенция из
`.claude/decisions/chat-vs-cc-knowledge-split.md`).
