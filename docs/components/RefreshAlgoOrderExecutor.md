# RefreshAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_ALGO_ORDER` (компонент-executor): что делает,
границы.

## Назначение

Получает `REFRESH_ALGO_ORDER`. Загружает локальный algo-order и проходит
**algo evidence-cycle внутри одной команды** (эскалация, обрыв на первом
успешном; полный обход — только при не-найдено):

```text
GET /trade/order-algo        (по algoId; нет externalId → по algoClOrdId)
  → orders-algo-pending
  → orders-algo-history (3m)   (ordType обязателен из conditionType)
```

Обновляет сущность, прогоняет внешний статус через
`AlgoOrderExternalStatusResolver`, при необходимости обновляет
`DealActionState` (см. `docs/rules/external-status-resolution.md`,
`docs/models/mapping/AlgoOrder.md`).

Сам выносит терминал: не найден после **полного** цикла →
`ExternalNotFoundException` → `AlgoOrder.ERROR` + `MISSING_AFTER_REFRESH`
(архива глубже 3m у algo нет). Обновляет только `AlgoOrder`; cross-entity
refresh (`REFRESH_ORDER` / `REFRESH_FILLS` / `REFRESH_POSITION`) — отдельные
команды, выбирает FSM. Pending/history-эндпоинты — звенья цикла; их судьба
как самостоятельных `ServiceCommandType` — CMD-Q3. Владение циклом —
`docs/decisions/refresh-evidence-cycle-ownership.md`. Общая семантика
`REFRESH_*` — `docs/components/ServiceCommandExecutor.md`.
