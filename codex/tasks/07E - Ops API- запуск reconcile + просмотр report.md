# Task 07E — Ops API: запуск reconcile + просмотр report

Всегда руководствоваться `codex/Code style.md`.
Опирайся на stage: `codex/stage/07 - Ops API: E2E проверка свечей + торговли + реконсиляции.md

## Цель
Дать контроллеры и сервисы, чтобы руками:
- запускать reconcile SAFE/FULL
- смотреть список отчётов
- читать конкретный отчёт

---

## 1) Ops service
- ReconcileOpsService
    - run(mode, exchangeId) -> reportId
    - listReports(exchangeId, limit)
    - getReport(id)

mode:
- SAFE -> runSafe()
- FULL -> run()

---

## 2) REST
Package: com.example.tradingbot.rest.controller.ops

- POST /api/ops/reconcile/run?mode=SAFE|FULL&exchangeId=...
    - response: { "reportId": 123 }

- GET /api/ops/reconcile/reports?exchangeId=...&limit=...
- GET /api/ops/reconcile/reports/{id}

---

## 3) Валидации
- exchangeId обязателен
- mode обязателен

---

## DoD
- Можно запустить reconcile и получить reportId.
- Можно открыть report и увидеть снапшоты.
