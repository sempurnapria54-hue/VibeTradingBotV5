# Task 07A — Admin API: Exchange + Instrument CRUD (минимум)

Всегда руководствоваться `codex/Code style.md`.
Опирайся на stage: `codex/stage/07 - Ops API: E2E проверка свечей + торговли + реконсиляции.md


## Цель
Сделать минимальные контроллеры и сервисы для создания и просмотра:
- Exchange
- Instrument

Важно:
- НЕ создавать candle_group автоматически при создании инструмента.
- candle_group создаются только через bootstrap REST (Task 07B).

---

## 1) Persistence (минимальные поля)

### ExchangeEntity
- id
- name
- status (String/enum)
- baseUrl
- audit

### InstrumentEntity
- id
- exchangeId (FK)
- instId (например `ETH-USDT-SWAP`)
- instType (например `SWAP`)
- status (NEW/CANDLES_LOADING/ACTIVE/SYNC/HOLD/ERROR)
- audit

UNIQUE:
- (exchange_id, inst_id)

---

## 2) DataService
- ExchangeDataService
    - create(...)
    - findAll()

- InstrumentDataService
    - create(...)
    - findAll()
    - findById(id)

При создании инструмента:
- status = NEW (или CANDLES_LOADING) — выбери один вариант и зафиксируй.

---

## 3) Domain services
- ExchangeAdminService
    - createExchange(...)
    - list()

- InstrumentAdminService
    - createInstrument(...)
    - list()
    - get(id)

---

## 4) REST
Package: com.example.tradingbot.rest.controller.admin

### ExchangeAdminController
- POST /api/admin/exchanges
- GET  /api/admin/exchanges

### InstrumentAdminController
- POST /api/admin/instruments
- GET  /api/admin/instruments
- GET  /api/admin/instruments/{id}

Response модели пока = domain.

---

## DoD
- Можно создать exchange и instrument.
- Можно получить список и конкретный instrument.
- Уникальность (exchangeId, instId) соблюдается.
