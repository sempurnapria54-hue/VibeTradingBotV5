# Stage 07 — Ops API: e2e проверка свечей + торговли + реконсиляции

## 0) Цель
Дать минимальный, но полный набор REST-эндпоинтов и сервисной обвязки, чтобы вручную (Postman/curl) проверить end-to-end флоу:
1) создать Exchange и Instrument
2) bootstrap candle_group(ы) для инструмента (явно через REST)
3) запустить загрузку свечей → меняются статусы candle_group → инструмент становится готов к торговле
4) создать ордера/алго-ордера/закрыть позицию (через command API)
5) запустить reconcile (Stage 05) и проверить:
    - отчёты и снапшоты
    - (под флагом) cancel/close
    - SYNC presence и transfer атрибутов

Ключевое бизнес-правило:
- Нельзя создавать/менять торговые объекты, пока инструмент не готов по данным свечей (gate по Instrument.status).

---

## 1) Границы этапа

### Включено
- Admin/Ops REST API
- CRUD минимум: Exchange и Instrument
- CandleGroups Ops API:
    - bootstrap candle_group(ов) только через REST (явная операция)
    - list
    - run-once (ручной прогон)
- Пересчёт Instrument.status на основании статусов candle_group
- Trading Command API (создать/отменить order, algo-order, close position) для ручного тестирования
- Gate: запрет торговых команд при неподготовленном инструменте
- Reconcile Ops API: запуск SAFE/FULL и просмотр report

### Исключено
- UI
- стратегия/кворум
- “идеальная” валидация параметров ордеров как в production (в этом этапе — минимум для теста)

---

## 2) Статусы и правило готовности

### 2.1 Instrument.status — единый gate
Минимальный набор:
- NEW
- CANDLES_LOADING
- ACTIVE
- SYNC (reconcile в процессе)
- HOLD (аварийная остановка)
- ERROR

### 2.2 Готовность свечей
Свечи “готовы”, если для всех candle_group инструмента:
- candle_group.status == SYNC

Тогда:
- если инструмент не в HOLD и не в SYNC → Instrument.status = ACTIVE
- иначе (есть хотя бы одна группа не SYNC) → Instrument.status = CANDLES_LOADING

Приоритеты:
- HOLD и SYNC не перетираются “готовностью данных”.
- Сервис готовности данных может ставить только ACTIVE / CANDLES_LOADING / ERROR.

### 2.3 Gate торговли
Команды торговли разрешены только если:
- Exchange.status == ACTIVE
- Instrument.status == ACTIVE

Иначе HTTP 409:
- INSTRUMENT_NOT_READY (если свечи не готовы)
- INSTRUMENT_SYNC / INSTRUMENT_HOLD
- EXCHANGE_NOT_ACTIVE

---

## 3) Пакеты

REST:
- com.example.tradingbot.rest.controller.admin.*
- com.example.tradingbot.rest.controller.ops.*
- com.example.tradingbot.rest.controller.trading.*

Domain services:
- com.example.tradingbot.domain.service.admin.*
- com.example.tradingbot.domain.service.ops.*
- com.example.tradingbot.domain.service.trading.*
- com.example.tradingbot.domain.service.candlegroup.*   (Stage 06)
- com.example.tradingbot.domain.service.reconcile.*     (Stage 05)

Persistence:
- com.example.tradingbot.persistence.*

---

## 4) Компоненты (минимум)

Admin:
- ExchangeAdminController / ExchangeAdminService
- InstrumentAdminController / InstrumentAdminService

CandleGroups:
- CandleGroupAdminController
- CandleGroupOpsService (bootstrap/list/run-once)

Instrument readiness:
- InstrumentDataReadinessService
    - recomputeInstrumentStatusFromCandleGroups(instrumentId)

Trading:
- TradingGuardService
    - assertTradingAllowed(exchangeId, instrumentId)
- OrderCommandService
- AlgoOrderCommandService (минимум)
- PositionCommandService (минимум)

Reconcile:
- ReconcileOpsController / ReconcileOpsService

---

## 5) REST endpoints (минимум)

Admin Exchange:
- POST /api/admin/exchanges
- GET  /api/admin/exchanges

Admin Instrument:
- POST /api/admin/instruments
- GET  /api/admin/instruments
- GET  /api/admin/instruments/{id}

CandleGroups:
- POST /api/admin/instruments/{instrumentId}/candle-groups/bootstrap
- GET  /api/admin/instruments/{instrumentId}/candle-groups
- POST /api/admin/candle-groups/{groupId}/run-once

Trading:
- POST /api/trading/orders
- POST /api/trading/orders/{internalId}/cancel
- POST /api/trading/algo-orders
- POST /api/trading/algo-orders/cancel
- POST /api/trading/positions/close

Reconcile:
- POST /api/ops/reconcile/run?mode=SAFE|FULL&exchangeId=...
- GET  /api/ops/reconcile/reports?exchangeId=...&limit=...
- GET  /api/ops/reconcile/reports/{id}

---

## 6) E2E playbook
1) создать Exchange
2) создать Instrument
3) bootstrap candle_groups для инструмента
4) запускать run-once для групп до SYNC
5) убедиться, что Instrument.status стал ACTIVE
6) создать order
7) запустить reconcile FULL и открыть report

---

## 7) DoD
- Можно пройти playbook руками.
- До готовности свечей торговые команды возвращают 409.
- После готовности свечей торговые команды работают.
- Reconcile запускается, создаёт report и его можно посмотреть по API.
