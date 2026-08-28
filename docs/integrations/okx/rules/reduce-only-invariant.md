# OKX reduce-only invariant

## На какой вопрос отвечает этот файл

Какой invariant OKX adapter проверяет по `reduceOnly` факту.

## Правило

`Order.positionReducingOnly` (доменное намерение) → OKX `reduceOnly`
в create request. `OrderResponse.reduceOnly` **не** маппится в
`OrderExternalSnapshot` и **не** обновляет `positionReducingOnly`.
Adapter проверяет соответствие как invariant:

```text
expected = Order.positionReducingOnly
actual   = OrderResponse.reduceOnly
mismatch -> EXCHANGE_INVARIANT_VIOLATION
         -> Order.ERROR, closeReason = EXCHANGE_INVARIANT_VIOLATION,
            Deal.ERROR, Exchange.TRADE_BLOCKED (ступень 2 + flatten)
```

Если биржа не поддерживает reduce-only/close-only — adapter может
проигнорировать `positionReducingOnly`; unsupported exchange на
первом этапе не блокируем.

## Где применяется

- `OkxIntegrationService` validation после create order;
- `OrderExternalSnapshot` материализация (поле сознательно
  отсутствует, чтобы не дать silent override).

## Связанные

- `docs/models/mapping/Order.md` → request mapping.
- `docs/rules/external-status-resolution.md` (safety-каскад).
- `docs/rules/raw-exchange-dto-boundary.md`.
