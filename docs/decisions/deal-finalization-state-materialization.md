# Дом persisted retry-state финализации: отдельная сущность DealFinalizationState

## На какой вопрос отвечает этот файл

Почему persisted retry-state финализации сделки живёт в отдельной
сущности `DealFinalizationState`, а не в обобщённом `DealActionState`.

## Контекст

Финализация сделки — теперь скоуп шага 6 (граница 6 ↔ 7, 2026-06-21:
*механика* финализации — шаг 6, *расчёт прибыли* — шаг 7). Финализационные
команды (`FINALIZE_DEAL_ENTRY`, `FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`,
`MARK_DEAL_ERROR` — `docs/components/models/ServiceCommand.md`) могут падать
и обязаны ретраиться. Единственный носитель persisted-retry —
база `Retryable`, наследуемая `DealActionState`. Но `DealActionState`
жёстко привязан к `StrategyAction` (`strategyActionId` обязателен,
инвариант `UNIQUE(deal_id, strategy_action_id)`), а финализация — это
**lifecycle/system action без `StrategyAction`**. Под неё строку
`DealActionState` создать нечем. Это и есть открытый вопрос **DEAL-Q1**.

## Решение

Дом persisted retry-state финализации — **отдельная сущность
`DealFinalizationState`** с базой `Retryable`
(`docs/models/domain/other/DealFinalizationState.md` + lifecycle
`docs/lifecycles/DealFinalizationState.md`). По одной строке на
финализационную команду сделки (ключ `UNIQUE(deal_id, finalization_type)`),
ретрай — по-командно через `Retryable`.

Финализационные `ServiceCommand` несут `dealFinalizationStateId` (не
`dealActionStateId`); путь эмиссии — `ServiceCommandFactory` по статусу
`DealFinalizationState`, аналогично action-командам по `DealActionState`
(`docs/components/ServiceCommandFactory.md`).

## Обоснование

- **Не размывает инвариант `DealActionState`.** Обобщение `DealActionState`
  до lifecycle-target с опциональным `strategyActionId` посадило бы иную
  природу (финализация сделки) в ту же таблицу с nullable-ключом и ослабило
  жёсткий `UNIQUE(deal_id, strategy_action_id)` — несущую идемпотентность
  «одно действие стратегии = одна строка».
- **Финализация многокомандна** (`FINALIZE_ENTRY`/`FINALIZE_EXIT`/`MARK_*`).
  Ретрай нужен **по-командно**, а не одним счётчиком на сделку — это тоже за
  отдельную сущность с дискриминатором `finalization_type`, а не за единый
  `DealActionState`.
- Чистое разделение «action-retry» (`DealActionState`) vs
  «lifecycle-retry» (`DealFinalizationState`); каждая сущность держит свой
  инвариант уникальности.

## Альтернативы (отвергнуты)

- **Обобщить `DealActionState` до lifecycle-target** (опциональный
  `strategyActionId` + `TargetEntityType.DEAL`, снятие обязательности ключа
  на финализации). Отвергнуто: размывает инвариант `DealActionState`
  (жёсткий `UNIQUE`, «привязан к `StrategyAction`») и не даёт по-командного
  ретрая без второго ключа-дискриминатора — то есть всё равно вводит ту же
  ось, что отдельная сущность, но ценой ослабления существующего инварианта.

## Закрытие вопроса

DEAL-Q1 закрыт на `GAPS_CLOSE_1` шага 6 фазы 1 (2026-06-22). Граничный
контракт терминала при неисчислимой прибыли (поведение `MARK_DEAL_CLOSED`
после исчерпания retry) — **DEAL-Q2**: финализация всегда доводит сделку до
терминала (чистый `CLOSED` с числом либо ошибочный терминал), число прибыли
на ошибочном терминале — деталь шага 7 (`docs/lifecycles/Deal.md`
§«Терминальный контракт финализации»).

## Связи

- Модель — `docs/models/domain/other/DealFinalizationState.md`.
- Lifecycle — `docs/lifecycles/DealFinalizationState.md`.
- Финализационные executor'ы — `docs/components/FinalizeDealEntryExecutor.md`,
  `docs/components/FinalizeDealExitExecutor.md`,
  `docs/components/MarkDealClosedExecutor.md`,
  `docs/components/MarkDealErrorExecutor.md`.
- Эмиссия команд — `docs/components/ServiceCommandFactory.md`,
  `docs/components/models/ServiceCommand.md`.
- Прецедент материализации операционной модели —
  `docs/decisions/deal-action-state-materialization.md`.
- Retry-база — `docs/components/RetryPolicyService.md`.
