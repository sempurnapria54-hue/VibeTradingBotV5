# RefreshOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_ORDER` (компонент-executor): что делает, границы.

## Назначение

Получает `REFRESH_ORDER`. Загружает локальный order и проходит **order
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
(пустой ответ одного эндпоинта — не основание). Обновляет только `Order`,
сделку целиком не сопровождает; cross-entity refresh (`REFRESH_FILLS`,
`REFRESH_POSITION`) — отдельные команды, выбирает FSM / `DealOrchestratorJob`.
Pending/history-эндпоинты — звенья этого цикла, не отдельные исполнители
(`.claude/decisions/executor-payload-file-granularity.md`); их судьба как
самостоятельных `ServiceCommandType` — CMD-Q3. Владение циклом —
`docs/decisions/refresh-evidence-cycle-ownership.md`; эндпоинт-механика —
`docs/integrations/okx/contracts/order.md`. Общая семантика `REFRESH_*` —
`docs/components/ServiceCommandExecutor.md`.
