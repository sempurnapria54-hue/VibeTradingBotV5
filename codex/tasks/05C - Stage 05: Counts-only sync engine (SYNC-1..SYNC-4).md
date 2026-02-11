# Task 050C (v2) — Stage 05: Counts-only sync engine (SYNC-1..SYNC-4)

Опирайся на stage: `codex/stage/codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

Реализовать counts-only синхронизацию между snapshot OKX и БД:

* синхронизируем только наличие сущностей (presence)
* не делаем cancel/close
* не переносим атрибуты (это Task 050D)

---

## Что нужно сделать

### 1) Компоненты

Создай в `com.example.tradingbot.domain.service.reconcile`:

* `CountsOnlySyncEngine`

    * `void syncInstrumentBucket(Long exchangeId, Long instrumentId, ExchangeSnapshot snapshot, InstrumentBucket bucket)`

* `ReconcilePlanBuilder`

    * `ReconcilePlan buildPlan(...)`

Модели (package `...reconcile.model`):

* `ReconcilePlan`

    * целевые counts по bucket
    * списки действий: createMissing, markUnknown, markClosed

### 2) Логика SYNC-1..SYNC-4

Реализуй по сценарию:

* SYNC-1: DB=0, EX>0 → создать записи со статусом `UNKNOWN` или `SYNC` (как в сценарии)
* SYNC-2: DB>0, EX=0 → пометить DB записи как `CLOSED`/`SYNC` (как в сценарии)
* SYNC-3: DB>EX → пометить лишние как `UNKNOWN`/`ANOMALY` (как в сценарии)
* SYNC-4: DB<EX → создать недостающие как `UNKNOWN`

> Точные статусы и правила бери из `docs/scenario/...`.

### 3) Идемпотентность

* Использовать UNIQUE ключи в БД.
* При создании UNKNOWN:

    * не создавать дубликаты
    * использовать deterministic client ids (если в snapshot их нет — создать placeholder по внешнему id)

### 4) Persistence взаимодействие

Используй только `persistence.service.*`.
Не дергай репозитории напрямую.

---

## Ограничения

* Не выполнять CancelFlow.
* Не переносить атрибуты.

---

## DoD

* После sync состояние presence в БД соответствует snapshot.
* Повторный запуск не создаёт дублей.
