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
стоит на `RISK_CONTROL`).

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

### RISK-Q2. Worst-case guard поверх вычисленного плеча/позиции (владелец — шаг 5)

После решения «плечо динамическое» (2026-06-04,
`docs/rules/trading-constraints.md`) наших потолков плеча/позиции
нет; единственный жёсткий предел — биржевой максимум
(`InstrumentExternalRules.externalMaxLeverage`). На крипто-перпах
биржевой максимум порядка 50-100× и ролью guard rail не обладает.
Находка ТР1 `DOCS_CHECK_8` (торговый фокус `trading-review`, первый
прогон): **не пересмотр** решения о динамическом плече — отдельная
страховка **поверх** вычисленного значения. Формула даёт корректное
плечо при корректных входах; guard ловит некорректные (ошибка
данных/расчёта, гэп, jump-риск).

Корпусное обоснование: позиционные лимиты обязательны для автоматики
— минимум из «позиция при максимальном форкасте ×1.5», лимита плеча
на инструмент, доли открытого интереса [Carver AFTS, тактика 4, PDF
с. 651-655]; ни одна позиция не должна обнулять счёт при максимальном
мыслимом движении [Carver ST, гл. 9, с. 180-181]; кэп экспозиции —
«единственный форвардный риск-контроль» [Kaufman, гл. 24, PDF
с. 2188, 2192-2193]; будущий крупнейший убыток больше исторического
[Vince, гл. 2, с. 30-31].

Варианты на будущее: (1) кэп плеча на инструмент; (2) позиционный
лимит (нотинал / доля капитала / доля открытого интереса);
(3) комбинация — минимум из нескольких границ; (4) иное по итогам
проработки риск-движка. Здесь не решается — владелец **шаг 5**
(риск-преконтроль); до решения guard не материализуется.
Связано: `docs/rules/trading-constraints.md`,
`docs/components/models/RiskCheckResult.md` (`RiskCheckCode`),
RISK-Q1 (`RiskSettings`), INSTR-Q2 (set-leverage / роль статического
плеча).

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

Под общее правило персистентности
(`docs/rules/persistence-representation.md`, 2026-06-03) дефолт
представления материализованных rules — JSONB на строке владельца
(`Instrument`), пока на них нет FK-ссылок из других мест;
самостоятельная таблица — только как осознанное исключение. Это вход
будущего решения, не предрешение.

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

### INSTR-Q2. Роль статического плеча `Instrument` при динамическом рабочем плече

Плечо динамическое (решение чата 2026-06-04,
`docs/rules/trading-constraints.md`): рабочее значение выводится на
сделку из торговых правил (риск на сделку, инвариант «ликвидация за
стопом») и рыночных условий (волатильность); наших потолков нет,
единственный жёсткий предел — биржевой максимум
(`InstrumentExternalRules.externalMaxLeverage`). При этом на
`Instrument` остаются статические поля плеча: рабочее `leverage`
(`Integer`, задаётся при создании) и сырой биржевой
`externalLeverage` (OKX `lever`). Шаг 1 (поток рыночных данных,
онбординг-путь `CREATED → SYNC → CANDLES_LOADING → ACTIVE`) вопросом
**не блокируется**. Здесь не решается — проработка уходит в
риск-движок (шаг 5) / торговый совет.

Открытые аспекты:
- Нужна ли вообще статическому полю `Instrument.leverage` роль
  рабочего плеча, если рабочее плечо выводится динамически на
  каждую сделку (переосмысление / удаление / иная роль — не
  предрешено). Кто и когда выставляет плечо на бирже
  (set-leverage: при онбординге, перед сделкой, на каждую сделку).
- Роль биржевого `externalLeverage` (OKX `lever`, сырое значение на
  `Instrument`) как биржевого потолка плеча: выступает ли он
  верхней границей для рабочего плеча и как соотносится с
  rules-полем `externalMaxLeverage`, в т.ч. возможный
  дубль/удаление (`docs/rules/trading-constraints.md` — предел
  плеча сослан на `InstrumentExternalRules.externalMaxLeverage`;
  `docs/models/domain/other/InstrumentExternalRules.md`).
- Состояние / действие `HOLD` в lifecycle инструмента: в текущем
  материализованном онбординг-пути шага 1 `HOLD` нет
  (`docs/lifecycles/Instrument.md`; периферийные статусы отложены —
  backlog п.9). Нужен ли переход в `HOLD` при нарушении правила
  плеча (после снятия наших потолков — только превышение биржевого
  максимума) и кто его инициирует — не решено.

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

Горизонт (взвешивание срока, 2026-06-06): отложен — узкий
онбординг-кусок несёт провизорный seam шага 1 (`CandleJob`); широкое
мультиисточниковое владение `Instrument.Status` (FSM, AnomalyJob и
пр.) — отдельная ось (backlog п.9). Решать к концу фазы / когда
осядет ось владения статусом.
Связано: `docs/processes/candle-loading.md`,
`docs/lifecycles/Instrument.md`, `docs/lifecycles/CandleGroup.md`,
`docs/components/CandleJob.md`.

### CMD-Q2. Базовый тип/дискриминатор payload'ов и судьба `ServiceCommandPayload.md`

Вынесен из CMD-Q1 при его закрытии (2026-06-06,
`.claude/decisions/executor-payload-file-granularity.md`: гранулярность
command-layer — file-per-executor; payload документируется разделом в
доке своего executor'а, отдельного агрегирующего файла нет). Не решено:
существует ли у payload'ов общий базовый тип/дискриминатор
(`ServiceCommandPayload` как база + подтипы) и, если да, где живёт
описание дискриминатора и какова судьба существующего
`docs/components/models/ServiceCommandPayload.md`. Завязано на
проработку command-layer. Содержимое payload'ов в любом случае едет к
своему executor'у; до переноса разделы остаются в существующем файле.

Горизонт (взвешивание срока): шаг 4 — материализация payload-детали.
Связано: `docs/components/models/ServiceCommandPayload.md`,
`docs/components/models/ServiceCommand.md`,
`.claude/decisions/executor-payload-file-granularity.md`.

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
  представления — часть DEAL-Q3. Под общее правило персистентности
  (`docs/rules/persistence-representation.md`, 2026-06-03) вложенные
  объекты (`RuntimeTarget`, `lastError`/`RetryError`) в БД — JSONB на
  строке `DealActionState` при любом доменном выборе; открытым
  остаётся доменное представление (объект vs инлайн-поля), не
  представление в БД.

Варианты: (1) `docs/models/domain/other/DealActionState.md` + отдельный
`docs/lifecycles/DealActionState.md` (status-enum как FSM); (2)
`docs/models/domain/aggregate/DealActionState.md` (тесная связь с
сопровождением сделки); (3) без отдельного lifecycle (статусы — раздел
модели).
До решения файл модели **не материализуется**; в местах использования
(`ServiceCommand`, executors, FSM handlers) упоминается с пометкой
«структура и размещение — DEAL-Q3».

Горизонт (взвешивание срока, 2026-06-06): шаг 4 — размещение решается
штатно при материализации модели (чистая классификация,
Автономия-рутина `knowledge-curator`), не пре-решается.
Связано: `docs/components/models/ServiceCommand.md`,
`docs/components/ServiceCommandFactory.md`, executor-компоненты,
`docs/components/RetryPolicyService.md` (база `Retryable`).

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

## Конвенция

Новые открытые вопросы добавляются сюда по мере появления. Закрытый
вопрос удаляется отсюда; история закрытия живёт в соответствующем
decision (конвенция из
`.claude/decisions/chat-vs-cc-knowledge-split.md`).
