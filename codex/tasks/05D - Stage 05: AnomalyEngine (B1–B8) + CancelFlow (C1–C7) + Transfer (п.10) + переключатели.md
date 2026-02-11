# Task 050D (v2) — Stage 05: AnomalyEngine (B1–B8) + CancelFlow (C1–C7) + Transfer (п.10) + переключатели

Опирайся на stage: `codex/stage/codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

Завершить Stage 05:

* AnomalyEngine: B1–B8
* AnomalyReport persistence
* CancelExchangeFlow: C1–C7 **под флагом**
* Transfer: п.10 (обновление атрибутов)
* связать pipeline `SynchronizeExecutionEnvironmentService.run()`

---

## Переключатели (обязательно)

* `reconcile.enabled`
* `reconcile.cancel-flow.enabled` (default false)

Поведение:

* если `reconcile.enabled=false` → pipeline не выполняется
* если `cancel-flow.enabled=false` → нет cancel/close запросов

---

## 1) AnomalyEngine

Создай `AnomalyEngine`:

* `Optional<AnomalyDecision> evaluate(ExchangeSnapshot snapshot, InstrumentBucket bucket, DbInstrumentState dbState)`

`DbInstrumentState` включает:

* counts в БД по инструменту: P/O/A
* выборки сущностей (минимально)

`AnomalyDecision`:

* `category` (B1..B8)
* `severity` (INFO|WARN|CRITICAL)
* `shouldHold`
* `shouldCancelFlow`
* `summary`
* `detailsJson`

Реализуй B1–B8 строго по сценарию.

Запись отчёта:

* сохраняй `AnomalyReportEntity` (exchangeId + instrumentId nullable)

---

## 2) CancelExchangeFlow

Создай `CancelExchangeFlow`:

* `CancelFlowResult execute(Long exchangeId, Long instrumentId, ExchangeSnapshot snapshot, InstrumentBucket bucket, AnomalyDecision decision)`

Требования:

* Запускается только если:

    * `cancelFlowEnabled` и `decision.shouldCancelFlow=true`
* Идемпотентность:

    * повторный запуск не должен плодить UNKNOWN
    * повторный запуск не должен бесконечно отменять уже отменённое

Алгоритм реализуй по C1–C7.
После отмен/закрытий сделай повторный snapshot и проверку `P/O/A == 0`.

---

## 3) Transfer (п.10)

Создай `ExchangeToDbTransferService`:

* обновляет поля:

    * `OrderEntity.exchangeOrderId`
    * `AlgoOrderEntity.exchangeAlgoOrderId`
    * `PositionEntity.side` (если есть)
* не создаёт новые сущности (кроме UNKNOWN по сценарию)

---

## 4) Связка pipeline

В `SynchronizeExecutionEnvironmentService.run()`:

1. lock (если есть)
2. snapshot
3. buckets
4. per bucket:

    * counts-only sync (050C)
    * anomaly evaluate
    * persist report
    * hold if needed
    * cancelflow if enabled + needed
    * transfer

---

## Ограничения

* Не добавлять торговую логику.
* Не добавлять новые REST endpoints.

---

## DoD

* При выключенном cancel-flow: только отчёты/HOLD, без cancel/close.
* При включенном cancel-flow: выполняется C1–C7.
* Transfer обновляет атрибуты идемпотентно.
