# Task 07B — CandleGroup Ops API: bootstrap + run-once + list

Всегда руководствоваться `codex/Code style.md`.
Опирайся на stage: `codex/stage/Опирайся на stage: `codex/stage/07 - Ops API: E2E проверка свечей + торговли + реконсиляции.md

## Цель
Дать REST для:
- bootstrap candle_group(ов) для инструмента (явная операция через REST)
- просмотр candle_groups инструмента
- ручной запуск обработки группы (run-once)

Bootstrap:
- создаёт строки candle_group для (instrumentId × timeframe), если их нет
- НЕ загружает свечи
- НЕ запускает job автоматически

---

## 1) Domain сервис
- CandleGroupOpsService
    - bootstrap(instrumentId, request)
    - listByInstrument(instrumentId)
    - runOnce(groupId)

BootstrapRequest (минимум):
- timeframes: List<String> (строго значения OkxTimeframes.*)
- coverageStartTs: long (UTC millis)

Правило bootstrap:
- для каждой timeframe:
    - если candle_group отсутствует → создать со status=NEW и coverage_start_ts=coverageStartTs
    - если есть → ничего не менять (идемпотентность)

---

## 2) Интеграция с обработчиком Stage 06
runOnce(groupId) вызывает:
- CandleGroupWorker.processGroup(groupId)

Важно:
- lease должен соблюдаться. Либо processGroup сам берёт lease, либо runOnce берёт lease и передаёт управление.

---

## 3) REST
Package: com.example.tradingbot.rest.controller.admin
- POST /api/admin/instruments/{instrumentId}/candle-groups/bootstrap
- GET  /api/admin/instruments/{instrumentId}/candle-groups

Package: com.example.tradingbot.rest.controller.ops (или admin — главное консистентно)
- POST /api/admin/candle-groups/{groupId}/run-once

---

## 4) Валидации
- timeframe не нормализуем (без lower-case), сравнение строго как есть.
- если инструмент не найден → 404.

---

## DoD
- Можно bootstrap candle_groups.
- Можно получить список candle_groups.
- Можно руками запускать run-once и видеть смену статусов.
