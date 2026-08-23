# RefreshOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_ORDER_COMMAND` (компонент-executor): что делает, границы.

## Назначение

Получает `REFRESH_ORDER_COMMAND`. Загружает локальный order и проходит **order
evidence-cycle внутри одной команды** (эскалация, обрыв на первом успешном
эндпоинте; полный обход — только при не-найдено):

```text
GET /trade/order            (по ordId; нет externalId → по clOrdId)
  → orders-pending          (не найден среди pending ≠ финал)
  → orders-history (7d)
  → orders-history-archive   (если history не покрывает период)
```

Обновляет order и статусы исполнения через `OrderExternalStatusResolver`,
при необходимости обновляет `DealActionState` (см.
`docs/rules/external-status-resolution.md`, `docs/models/mapping/Order.md`).

Сам выносит терминал: не найден после **полного** цикла →
`ExternalNotFoundException` → `Order.ERROR` + `MISSING_AFTER_REFRESH`
(пустой ответ одного эндпоинта — не основание). Обновляет только `Order` —
включая order-fill-метрики (`accFillSz` → `accumulatedFillSize`, `avgPx` →
`averagePrice`, `fee`), которые приходят готовыми из самого этого refresh
(`OkxOrderResponse`), отдельной fill-команды нет. Сделку целиком не
сопровождает; cross-entity refresh (`REFRESH_POSITION_COMMAND`) — отдельная команда,
выбирает FSM / `DealOrchestratorJob`.
**Исключение из «обновляет только `Order`» — две суммы риска на сделке**
(H3 `DOCS_CHECK_16`, решение пользователя). `Deal.plannedRiskAmount`
(заявленный) и `Deal.incurredRiskAmount` (взятый) — **производные проекции
ног**, а не самостоятельные факты: взятый риск считается с весом
`accumulatedFillSize / plannedSizeContracts`, то есть меняется ровно тогда,
когда меняется исполненный объём ноги входа, — здесь. Поэтому executor,
обновив ногу входа, **той же транзакцией пересчитывает обе суммы целиком**
(инкремента нет — пересчёт идемпотентен и от порядка не зависит;
`docs/models/domain/aggregate/Deal.md` §«Взятый риск»). Для не-входных ног
(защита, reduce-only) суммы не трогаются — у них планового риска нет.
Cross-entity refresh это не вводит: читаются ноги той же сделки, чей
контекст уже в руках.

Pending/history-эндпоинты — звенья этого цикла, не отдельные исполнители
(`.claude/decisions/executor-payload-file-granularity.md`); их судьба как
самостоятельных `ServiceCommandType` — CMD-Q3. Владение циклом —
`docs/decisions/refresh-evidence-cycle-ownership.md`; эндпоинт-механика —
`docs/integrations/okx/contracts/order.md`. Общая семантика `REFRESH_*` —
`docs/components/ServiceCommandExecutor.md`.
