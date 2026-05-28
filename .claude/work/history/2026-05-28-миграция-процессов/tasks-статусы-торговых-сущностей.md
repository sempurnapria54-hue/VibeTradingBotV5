# Локальные вопросы: миграция «Статусы торговых сущностей»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «Статусы торговых сущностей»
(локальные вопросы и форвард-заметки прохода 1).

## Открытые вопросы

- **СТ-Q1. Master-index → разбор по владельцам.** Док сам заявлен как
  «основной источник истины по статусам торговых сущностей» —
  файл-агрегатор. По `master-index-not-fixated.md` и
  `recognize-knowledge.md` сам файл не воспроизводим; каждый статусный
  блок размещаем по владельцу (Order.Status → `Order.md`; AlgoOrder.Status
  → `AlgoOrder.md`; Position.Status/CloseReason → `Position.md`;
  Deal.Status → уже в `Deal.md`/lifecycle; Exchange/Instrument статусы →
  модель `Exchange`/`Instrument`). Проверить, что ничего не потеряно при
  разборе.
- **СТ-Q2. Модели `Exchange` / `ExchangeAccount` / `Instrument`.** §7.1–
  7.2 описывают статусы ACTIVE/HOLD/DISABLED. Полные модели/lifecycles
  `Exchange`/`Instrument`/`Account` в порядок 6 торговых сущностей не
  входили (backlog п.9). Развилка: завести модели сейчас (как владельцев
  статусов) или вынести в отдельную миграцию п.9, оставив здесь только
  статусную семантику в `exchange-hold.md`. Решить на проходе 2.
- **СТ-Q3. `Component impact matrix` (§11).** Таблица «компонент → как
  влияет документ» — кросс-ссылочная. Не воспроизводить как файл; влияние
  распределить по соответствующим компонентам или сквозным правилам.

## Решения прохода 2

- **СТ-Q1 (master-index) — разобран по владельцам.** Статусная механика
  (`Order.Status`/`AlgoOrder.Status`/`Position.Status`/`CloseReason`,
  attached protection, live-risk формулы, resolvers, exchange-hold,
  controlled exceptions) **уже была мигрирована** прошлым model-кластером
  в `docs/models/core/*`, `docs/lifecycles/*`, `docs/rules/*`,
  `docs/client/okx/rules/*`. На проходе 2 дополнительно созданы
  компоненты-resolver'ы (`OrderExternalStatusResolver`,
  `AlgoOrderExternalStatusResolver`, `PositionStatusResolver` + RVO
  `PositionStatusResolveResult`), `AnomalyJob`, `KillSwitchExecutor`,
  правило `controlled-exchange-exceptions`; дополнены
  `external-status-resolution` (result-object/write-once) и
  `exchange-hold` (DISABLED). Статусные расширения моделей/lifecycles
  **не дублировались** — уже присутствуют.
- **СТ-Q2 (`Exchange`/`ExchangeAccount`/`Instrument` модели)** — за
  рамками миграции процессов, backlog п.9 (статусная семантика —
  `docs/rules/exchange-hold.md`).
- **СТ-Q3 (Component impact matrix §11)** — не воспроизведён как файл;
  влияние распределено по компонентам/правилам.
- **Mappers** (`OrderMapper`, `PositionMapper`, `AlgoOrderMapper`,
  `BalanceContainerMapper`) — упомянуты в архиве вскользь (например, СК
  §REFRESH_BALANCE: «raw OKX DTO → validation → `BalanceContainerMapper`
  → `BalanceContainerExternalSnapshot`»); доменное существо в
  `docs/client/okx/rules/*`. **Не материализованы** как
  `docs/components/<X>.md` — backlog п.2.

## Форвард-заметки

- **СТ-FW1.** §5 — resolver'ы внешних статусов (`OrderExternalStatusResolver`,
  `AlgoOrderExternalStatusResolver`, `PositionStatusResolver` +
  OKX-реализации). Компоненты (backlog п.2). `EntityStatusResolveResult`/
  `PositionStatusResolveResult` — RVO. Доменное существо resolver'ов уже
  частично в `external-status-resolution.md` — расширение.
- **СТ-FW2.** §6 controlled exchange exceptions + Exchange-level реакция
  (HOLD) + §6.6/§6.7 (что HOLD блокирует/разрешает) — расширение
  `exchange-hold.md`; таксономия exception'ов пересекается со «Сервисные
  команды» §2.5.
- **СТ-FW3.** §8.3 Order (Status: CREATED/PENDING/ACTIVE/
  PARTIALLY_COMPLETED/COMPLETED/CANCELED/ERROR; live risk; reduceOnly;
  attached protection) — расширение `Order.md`/`lifecycles/Order.md`.
  OKX external→domain mapping (live/partially_filled/filled/canceled/
  mmp_canceled) → расширение `okx-order-mapping.md`.
- **СТ-FW4.** §8.4 AlgoOrder (Status + семантика; live/pause→ACTIVE,
  partially_effective→PARTIALLY_COMPLETED, effective→COMPLETED,
  order_failed/partially_failed→exception) — расширение `AlgoOrder.md` +
  `okx-algo-order-mapping.md`.
- **СТ-FW5.** §8.5 Position (Status ACTIVE/CLOSED/ERROR; live risk =
  ACTIVE && externalSize>0; externalSize==0 семантика; CloseReason
  значения; `PositionStatusResolver`; REFRESH_POSITION policy;
  не используются CREATED/PENDING/OPENING/CLOSING/PARTIALLY_CLOSED) —
  расширение `Position.md`/`lifecycles/Position.md` +
  `okx-position-mapping.md`. Детальнее, чем в «Сервисных командах».
- **СТ-FW6.** §8.6 BalanceContainer (нет Status/lifecycle; freshness;
  settleCurrency; REFRESH_BALANCE) — расширение `BalanceContainer.md`.
- **СТ-FW7.** §9 cleanup rules (`ExitPendingHandler`, `ErrorHandler`,
  `KillSwitch`) + §10 anomaly rules (`AnomalyJob`) — компоненты; backlog
  п.7. `relatedInactive` (§4.8) — концепт для recovery/anomaly.
- **СТ-FW8.** §12 «минимальные правила для кода» (semantic helpers
  `isActiveEntity()`/`isClosedEntity()`/`hasLiveRisk()`; внешние статусы
  только в resolver; неизвестный статус safety-critical; ACK не truth) —
  сквозные правила; ACK → уже `ack-not-runtime-truth.md`; внешние статусы
  → `external-status-resolution.md`. Расширения.
