# Task 05H — Stage 05 (v3): AnomalyEngine (B1–B8) + CancelFlow (C1–C7) + запись в Report

Опирайся на stage: `codex/stage/05 — Synchronize Execution Environment v3.md`.

## Цель

* Реализовать AnomalyEngine (B1–B8)
* Реализовать CancelExchangeFlow (C1–C7) под флагом
* Вместо `AnomalyReport` писать аномалии в `SynchronizeExecutionEnvironmentReportAnomaly`
* После cancel/close делать refresh exchange facts по инструменту для продолжения SYNC/Transfer

---

## 1) AnomalyEngine

Создай `AnomalyEngine`:

* `Optional<AnomalyDecision> evaluate(InstrumentBucket bucket)`

`AnomalyDecision`:

* `type` (B1..B8 или именованный тип)
* `severity` (NON_CRITICAL|CRITICAL)
* `shouldHold` (bool)
* `shouldCancelFlow` (bool)
* `summary`
* `detailsJson`

Реализуй правила B1–B8 строго по сценарию.

---

## 2) CancelExchangeFlow

Создай `CancelExchangeFlow`:

* `CancelFlowResult execute(InstrumentBucket bucket, AnomalyDecision decision)`

Требования:

* выполняется только если `reconcile.cancel-flow.enabled=true` и `decision.shouldCancelFlow=true`
* идемпотентность: повторный запуск не создаёт дублей UNKNOWN и не падает на повторных cancel/close

Алгоритм (C1–C7):

* C1: `Instrument.mode=HOLD`
* C2: cancel/close по всем сущностям из exchange фактов
* C2.2: если объекта нет в БД → создать UNKNOWN + status=ANOMALY
* C4: обновить Report (appendAnomaly)
* C5: если NON_CRITICAL → `Instrument.mode=OPEN` и продолжаем
* C6/C7: обновить `Instrument.positionMode` по фактам и сохранить инструмент, если изменился

---

## 3) Refresh exchange facts после cancel/close

Если cancel/close выполнялся, то для продолжения pipeline по этому bucket нужно обновить exchange facts.

Сделай одно из двух:

* либо `OkxExchangeSnapshotProvider.refreshInstrumentSnapshot(instId)`
* либо повторно получить orders/positions/algoOrders только по инструменту

Внутренний refresh используется только для:

* SYNC (counts-only)
* Transfer (Task 05E)

В отчёте `exchange_before` остаётся исходным (не меняем).

---

## 4) Интеграция в service.run()

В `SynchronizeExecutionEnvironmentService.run()` на шаге A2:

* `decision = anomalyEngine.evaluate(bucket)`
* если есть decision:

    * `reportService.appendAnomaly(...)`
    * если CRITICAL:

        * `Instrument.mode=HOLD`, `Instrument.status=HOLD`
        * (опционально cancel-flow)
        * завершить bucket
    * если NON_CRITICAL:

        * при необходимости cancel-flow
        * `Instrument.mode=OPEN`
        * продолжить

Результат A2 должен вернуть:

* актуальный `ExchangeInstrumentSnapshot currentExchangeState` для A3/A4

---

## Ограничения

* Не переносить расширенные атрибуты (это Task 05E).

---

## DoD

* Аномалии пишутся в новый report.
* CancelFlow выполняется только под флагом.
* После cancel/close pipeline продолжает работу по актуальным exchange фактам.
