# Task 07C — Instrument.status gate: обновление статуса по candle_group

Всегда руководствоваться `codex/Code style.md`.
Опирайся на stage: `codex/stage/07 - Ops API: E2E проверка свечей + торговли + реконсиляции.md

## Цель
Сделать механизм, который держит Instrument.status корректным для gate торговли:
- пока свечи не готовы → CANDLES_LOADING
- когда готовы → ACTIVE

При этом НЕ перетирать:
- SYNC (reconcile в процессе)
- HOLD (аварийная остановка)

---

## 1) Domain сервис
- InstrumentDataReadinessService
    - recomputeInstrumentStatusFromCandleGroups(instrumentId)

Алгоритм:
1) загрузить instrument
2) если instrument.status in (SYNC, HOLD) → return
3) загрузить candle_groups по instrument
4) если групп нет → status=CANDLES_LOADING
5) если любая группа status != SYNC → status=CANDLES_LOADING
6) если все группы status == SYNC → status=ACTIVE
7) сохранить instrument только если статус реально изменился

---

## 2) Интеграция
Предпочтительно:
- в конце CandleGroupWorker.processGroup() вызвать recomputeInstrumentStatusFromCandleGroups(instrumentId)

---

## 3) REST (опционально, удобно для тестов)
- POST /api/ops/instruments/{id}/recompute-status

---

## DoD
- До готовности свечей инструмент остаётся CANDLES_LOADING.
- После того как все candle_groups стали SYNC — инструмент становится ACTIVE.
- Во время reconcile (SYNC) сервис готовности данных не перетирает статус.
