# Task 07F — Postman: коллекции и окружение для e2e тестов

Всегда руководствоваться `codex/Code style.md`.
Опирайся на stage: `codex/stage/07 - Ops API: E2E проверка свечей + торговли + реконсиляции.md

## Цель
Сгенерировать Postman артефакты, чтобы быстро прогонять e2e сценарий Stage 07:
- Admin: create/list exchanges & instruments
- CandleGroups: bootstrap/list/run-once
- Trading: create/cancel orders, algo-orders, close position
- Reconcile: run SAFE/FULL + list/get reports

---

## 1) Что создать в репозитории
Папка: `tools/postman/`
1) `TradingBot.local.postman_environment.json`
2) `TradingBot.postman_collection.json`

---

## 2) Environment variables (минимум)
- baseUrl (default http://localhost:8080)
- exchangeId
- instrumentId
- groupId
- internalOrderId
- reportId
- instId (например ETH-USDT-SWAP)
- instType (например SWAP)

---

## 3) Коллекция: структура папок
1) 00 - Admin
    - Create Exchange
    - List Exchanges
    - Create Instrument
    - List Instruments
    - Get Instrument By Id

2) 01 - CandleGroups
    - Bootstrap CandleGroups (по instrumentId)
    - List CandleGroups (по instrumentId)
    - Run CandleGroup Once (по groupId)

3) 02 - Trading
    - Create Order
    - Cancel Order
    - Create Algo Order
    - Cancel Algo Orders (batch)
    - Close Position

4) 03 - Reconcile
    - Run Reconcile SAFE
    - Run Reconcile FULL
    - List Reports
    - Get Report By Id

---

## 4) Автосохранение id (tests scripts)
Добавь tests scripts:
- После Create Exchange -> сохранить exchangeId
- После Create Instrument -> сохранить instrumentId
- После List CandleGroups -> если groupId пустой, взять первый и сохранить
- После Create Order -> сохранить internalOrderId
- После Run Reconcile -> сохранить reportId

---

## 5) Примеры тел запросов (минимум)
Create Instrument:
{ "exchangeId": "{{exchangeId}}", "instId": "{{instId}}", "instType": "{{instType}}" }

Bootstrap CandleGroups:
{ "timeframes": ["1m","5m"], "coverageStartTs": 1700000000000 }

Create Order (market):
{ "exchangeId": "{{exchangeId}}", "instrumentId": "{{instrumentId}}", "side": "buy", "ordType": "market", "sz": "1" }

---

## DoD
- Postman коллекция импортируется без ошибок.
- Можно пройти полный флоу Stage 07 без ручного копипаста id.
