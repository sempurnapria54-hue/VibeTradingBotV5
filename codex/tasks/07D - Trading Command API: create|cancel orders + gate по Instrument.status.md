# Task 07D — Trading Command API: create/cancel orders + gate по Instrument.status

Всегда руководствоваться `codex/Code style.md`.
Опирайся на stage: `codex/stage/07 - Ops API: E2E проверка свечей + торговли + реконсиляции.md

## Цель
Сделать «командные» эндпоинты торговли, чтобы руками создавать торговые объекты и затем проверять reconcile.

Обязательное правило:
- Нельзя создавать/менять торговые объекты, если Instrument.status != ACTIVE или Exchange.status != ACTIVE.

---

## 1) TradingGuardService
- TradingGuardService.assertTradingAllowed(exchangeId, instrumentId)

Проверки:
- exchange.status == ACTIVE
- instrument.status == ACTIVE

Иначе:
- HTTP 409 с кодом:
    - INSTRUMENT_NOT_READY / INSTRUMENT_SYNC / INSTRUMENT_HOLD
    - EXCHANGE_NOT_ACTIVE

---

## 2) Команды и сервисы

### 2.1 OrderCommandService
- createOrder(CreateOrderCommand cmd)
- cancelOrder(CancelOrderCommand cmd)

CreateOrderCommand (минимум):
- exchangeId
- instrumentId
- side (buy/sell)
- ordType (market/limit)
- sz (в контрактах)
- px (опционально для limit)

Алгоритм create:
1) guard
2) сгенерировать internalId (будущий clOrdId)
3) создать OrderEntity в БД (status=PENDING/CREATED)
4) вызвать OKX createOrder (clOrdId = internalId)
5) обновить в БД exchangeOrderId + базовые поля ответа
6) вернуть результат (internalId + ordId + state)

Алгоритм cancel:
1) guard
2) найти OrderEntity по internalId
3) вызвать OKX cancelOrder
4) обновить state/статус в БД
5) вернуть результат

### 2.2 AlgoOrderCommandService (минимум)
- createAlgoOrder(...)
- cancelAlgoOrders(batch)

### 2.3 PositionCommandService (минимум)
- closePosition(...)

---

## 3) Persistence
Используем order/algo_order/position сущности (из контуров reconcile).
Важно:
- internalId = наш идентификатор
- на биржу передаём internalId как client id (clOrdId/algoClOrdId)

---

## 4) REST
Package: com.example.tradingbot.rest.controller.trading

- POST /api/trading/orders
- POST /api/trading/orders/{internalId}/cancel

- POST /api/trading/algo-orders
- POST /api/trading/algo-orders/cancel

- POST /api/trading/positions/close

Response модели пока = domain.

---

## DoD
- До готовности свечей (Instrument.status=CANDLES_LOADING) createOrder возвращает 409.
- После ACTIVE createOrder работает.
- В БД появляется OrderEntity с internalId=clOrdId.
