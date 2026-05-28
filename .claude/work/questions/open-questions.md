# Открытые вопросы

## На какой вопрос отвечает этот файл

Что мы ещё не решили (общие вопросы — пайплайн и продукт).

## Статус

Открыты продуктовые вопросы по финализации `Deal` (перенесены из
архивного `Deal.md` §15 при миграции, 2026-05-27) и один вопрос,
обнаруженный при составлении карты артефактов миграции процессов
(проход 1, 2026-05-27).

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
Связано: `docs/models/core/Deal.md`, `docs/lifecycles/Deal.md`.

### DEAL-Q2. Что делать, если resultProfit нельзя посчитать после исчерпания retry

Зафиксировано: `resultProfit`/`resultProfitCurrency` обязательны для
`CLOSED`/`EMERGENCY_CLOSED`; `resultProfit = 0` допустим только как
результат расчёта, не fallback. Не решено, что делать, если после
всех retry итоговый PnL всё ещё нельзя безопасно посчитать. Варианты
на будущее: отдельный finalization state; перевод в `ERROR`;
отдельный `DealFinalizationState`; ручной разбор; специальный
operational flag без нарушения terminal semantics.
Связано: `docs/models/core/Deal.md` §Итоговый PnL.

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
`docs/models/core/Strategy.md` (используется многими настройками
strategy-tree), но как самостоятельный enum может жить иначе.

Цитата источника (архив, «Расчёт индикаторов и рыночных данных» §8):
«`TimeFrame` — чистый доменный enum. OKX-строки в нём не храним.»
Значения: `ONE_MINUTE`, `THREE_MINUTES`, `FIVE_MINUTES`,
`FIFTEEN_MINUTES`, `ONE_HOUR`, `TWO_HOURS`, `FOUR_HOURS`, `ONE_DAY`.
Маппинг OKX-строк живёт отдельно (`TimeFrameMapper` /
`docs/client/okx/rules/okx-timeframe-mapping.md`).

Варианты:
- `docs/models/other/TimeFrame.md` — самостоятельная модель-enum;
- `docs/dictionary/time-frame.md` — словарная статья;
- оставить разделом внутри market-data / `Strategy.md`.

До решения отдельный файл не создаётся; `TimeFrame` упоминается как
термин в местах использования.
Связано: `docs/models/core/Strategy.md` (§TimeFrame),
`docs/client/okx/rules/okx-timeframe-mapping.md`,
`docs/models/other/IndicatorValue.md` / `MarketStructure.md` /
`MarketPhase.md` (через settings).

### ENUM-Q1. closeReason `RISK_CONTROL` vs `ENTRY_RISK_BLOCKED`

Конфликт значения `Deal.closeReason` при risk-block в `PRECHECK` (до live
risk) между двумя архивными процессными доками.

Цитаты источника:
- «Оценка рисков» §8.1: при `BLOCKED` в `PRECHECK` без live risk —
  `Deal.status = CLOSED`, `Deal.closeReason = RISK_CONTROL`; «Отдельный
  `ENTRY_RISK_BLOCKED` не используем».
- «Аудит и история исполнения» §7.1 (старше, черновое): `closeReason`
  может быть `ENTRY_RISK_BLOCKED` «или другое согласованное значение».

`docs/lifecycles/Deal.md` и `docs/models/core/Deal.md` уже используют
`RISK_CONTROL` (в списке `CloseReason` `ENTRY_RISK_BLOCKED` помечен как не
используемый). Решённый по букве risk-доки и lifecycle вариант —
`RISK_CONTROL`; аудит-док даёт устаревшую формулировку.

Вариант: подтвердить `RISK_CONTROL`, `ENTRY_RISK_BLOCKED` окончательно
отвергнуть (закрыть вопрос ссылкой на decision). До закрытия список
значений `closeReason` в `Deal.md` **не меняется**.
Связано: `docs/models/core/Deal.md` (§Енумы, `CloseReason`),
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

### DEAL-Q3. Размещение `DealActionState` (core/other, own lifecycle)

`DealActionState` — persisted операционная модель runtime-состояния
выполнения `StrategyAction`. Не торговая бизнес-сущность в смысле PnL, но
тесно связана с сопровождением сделки. Не решено: `docs/models/core/` или
`docs/models/other/`; нужен ли отдельный lifecycle (есть status-enum).

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

Варианты: (1) `docs/models/other/DealActionState.md` + отдельный
`docs/lifecycles/DealActionState.md` (status-enum как FSM); (2)
`docs/models/core/` (тесная связь с сопровождением сделки); (3) без
отдельного lifecycle (статусы — раздел модели).
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
