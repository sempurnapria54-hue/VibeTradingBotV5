# Локальные вопросы: миграция Position

## На какой вопрос отвечает этот файл

Что неясно / отложено по миграции архивной сущности Position.

## Контекст

Источник: `.claude-archive/2026-05-21/docs/domain/models/Position.md`
и `.../mapping/okx/OKX_Position_mapping.md`. Стратегия: парковать
cross-cutting подсистемы, создавать только владение Position,
ссылаться по имени.

## Форвард-заметки (мигрируются с владельцем)

- **POS-Q1. Подсистема ServiceCommand / REFRESH_POSITION /
  CLOSE_POSITION.** `REFRESH_POSITION` — единственный штатный способ
  создать/обновить локальную `Position`; `CLOSE_POSITION` —
  атомарная команда полного закрытия. Компоненты
  `RefreshPositionExecutor`, `ClosePositionExecutor` — command-слой,
  отложены. Доменная статусная механика (resolver-логика,
  executor-правила применения status/closeReason, условия
  create/close) зафиксирована в `docs/lifecycles/Position.md`.
  Минимальные проверки перед `CLOSE_POSITION` (позиция существует
  локально и в текущей Deal; `status=ACTIVE`; команда закрывает всю
  позицию; есть `DealContext` с Exchange/Instrument; facts не
  доказывают, что позиции уже нет; `RiskValidator` для
  `CLOSE_POSITION` не вызывается) — для command-миграции.

- **POS-Q2. `PositionStatusResolver` + `PositionStatusResolveResult`.**
  Resolver — компонент; его result-object (`status` +
  `closeReason` candidate) — RVO (`docs/components/models/`). Логика
  resolver захвачена в `docs/lifecycles/Position.md`. Сами
  `docs/components/PositionStatusResolver.md` и RVO — отложены до
  command/adapter-миграции (стратегия «не плодить тонкие доки из
  частичного обзора»). `PositionMapper` (mapper) — аналогично; его
  существо — в `okx-position-mapping.md`.

- **POS-Q3. RiskValidator (shared).** Для `CLOSE_POSITION`
  `RiskValidator` не вызывается; handler/executor делает только
  minimal safety checks. Зафиксировать при миграции RiskValidator.

- **POS-Q4. Shared RVO: DealContext.** `Position` не хранит
  `instrumentId`/`exchangeId`, поэтому `DealContext` на каждой
  итерации FSM несёт `Deal`, `Exchange`, `Instrument`, текущую
  `Position` (если материализована). `relatedPositions` не нужны
  (≤1 Position на Deal); `exchangePositionFact` в `DealContext` не
  хранится. Отсутствие локальной `Position` допустимо в
  `ENTRY_SUBMITTED` до успешного `REFRESH_POSITION`. Создаётся с
  миграцией Deal.

- **POS-Q5. Deal.resultProfit / REFRESH_FILLS (Deal-owned).**
  `Position` не используется для итогового PnL; `Deal.resultProfit`
  — через `REFRESH_FILLS`. Зафиксировать при миграции Deal
  (`rule-source-of-truth.md`). Дубль форвард-заметки BAL-Q5.

- **POS-Q6. Recovery-контур (Deal-owned).** `DealOrchestratorJob`
  собирает `DealContext`, видит Deal в recovery-compatible статусе,
  запускает refresh-контур (`REFRESH_ORDER`/`REFRESH_ORDER_HISTORY`
  → `REFRESH_POSITION` → `REFRESH_ALGO_ORDER_HISTORY` →
  `REFRESH_FILLS`); `Deal` финализируется по фактам, переходит в
  `EXIT_PENDING`, `Deal.closeReason` по сработавшей защите. Полный
  flow — при миграции lifecycle Deal. Position-правило (локальную
  `CLOSED Position` можно не создавать, если её ещё не было) — уже в
  `docs/lifecycles/Position.md`.

- **POS-Q7. `AnomalyJob` (shared safety).** Active position на
  бирже без active `Deal`, объясняющего её, — зона
  `AnomalyJob`/safety-flow. Компонент — при миграции
  anomaly/safety-подсистемы.

## Открытые вопросы

Открытых вопросов, требующих решения, по Position нет. (Архивный §
«Open questions» в Position.md отсутствует; продуктовых открытых
вопросов не заявлено.)
