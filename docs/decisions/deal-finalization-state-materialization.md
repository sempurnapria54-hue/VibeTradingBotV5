# Дом persisted retry-state финализации: отдельная сущность DealFinalizationState

## На какой вопрос отвечает этот файл

Почему persisted retry-state финализации сделки жил в отдельной
сущности `DealFinalizationState`, а не в обобщённом `DealActionState`.

> **Ревизовано (шаг 7, развилка «команда ↔ действие»):**
> `DealFinalizationState` **упразднена** — финализация ведётся строками
> исполнений **системных действий** (носитель ревизован H15
> `DOCS_CHECK_14`: своя таблица `deal_system_action_states`, не общая —
> `docs/decisions/command-action-boundary.md` §3). Несущий довод этого
> решения — «не размывать инвариант `UNIQUE(deal_id,
> strategy_action_id)`» — рухнул вместе с самим инвариантом: он
> предполагал однократное исполнение действия стратегии в сделке, что
> опровергнуто (грид — многократные, параллельные исполнения). Второй
> довод (по-командный ретрай) сохранён формой «строка = исполнение».
> Текст ниже — исторический контекст DEAL-Q1.

## Контекст

Финализация сделки — теперь скоуп шага 6 (граница 6 ↔ 7, 2026-06-21:
*механика* финализации — шаг 6, *расчёт прибыли* — шаг 7). Финализационные
команды (`FINALIZE_DEAL_ENTRY_COMMAND`, `FINALIZE_DEAL_EXIT_COMMAND`, `MARK_DEAL_CLOSED_COMMAND`,
`MARK_DEAL_ERROR_COMMAND` — `docs/components/models/ServiceCommand.md`) могут падать
и обязаны ретраиться. Единственный носитель persisted-retry —
база `Retryable`, наследуемая `DealActionState`. Но `DealActionState`
жёстко привязан к `StrategyAction` (`strategyActionId` обязателен,
инвариант `UNIQUE(deal_id, strategy_action_id)`), а финализация — это
**lifecycle/system action без `StrategyAction`**. Под неё строку
`DealActionState` создать нечем. Это и есть открытый вопрос **DEAL-Q1**.

## Решение

> **Носителей этого решения больше нет.** Модель `DealFinalizationState`,
> её lifecycle и `DealFinalizationCommandFactory` **упразднены**, доки
> удалены на `GAPS_CLOSE_8`. Имена ниже — **исторический контекст
> DEAL-Q1**, не ссылки: адресата у них не существует. Действующие
> носители — `docs/models/domain/other/DealActionState.md` (строки
> исполнений, вид SYSTEM), `docs/components/SystemActionExecutor.md`
> (per-pass исполнитель), `docs/decisions/command-action-boundary.md` §3
> (ревизующее решение).

Дом persisted retry-state финализации — **отдельная сущность
`DealFinalizationState`** с базой `Retryable` (модель + собственный
lifecycle). По одной строке на финализационную команду сделки (ключ
`UNIQUE(deal_id, finalization_type)`), ретрай — по-командно через
`Retryable`.

Финализационные `ServiceCommand` несут `dealFinalizationStateId` (не
`dealActionStateId`); путь эмиссии — `DealFinalizationCommandFactory` по
статусу `DealFinalizationState`, аналогично action-командам, которые
эмитят per-type `StrategyActionExecutor`'ы под `StrategyActionOrchestrator`
по статусу `DealActionState` (слоение —
`docs/decisions/fsm-execution-layering.md`).

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
контракт терминала при неисчислимой прибыли (поведение `MARK_DEAL_CLOSED_COMMAND`
после исчерпания retry) — **DEAL-Q2**: финализация всегда доводит сделку до
терминала (чистый `CLOSED` с числом либо ошибочный терминал), число прибыли
на ошибочном терминале — деталь шага 7 (`docs/lifecycles/Deal.md`
§«Терминальный контракт финализации»).

## Связи

- Ревизующее решение — `docs/decisions/command-action-boundary.md` §3
  (сущность упразднена; действующий носитель —
  `docs/models/domain/other/DealActionState.md`, per-pass исполнитель —
  `docs/components/SystemActionExecutor.md`).
- Модель `DealFinalizationState`, её lifecycle и
  `DealFinalizationCommandFactory` — **упразднены** (файлы удалены на
  `GAPS_CLOSE_8`); упоминания в тексте выше — исторический контекст
  DEAL-Q1, не живые ссылки. **Пометка продублирована в §Решение** — там,
  где имена стоят: прежде она жила только здесь, а сами упоминания были
  записаны **путём к удалённому файлу** и от живой ссылки не отличались.
  Путь снят, имена оставлены — вопрос дока исторический, и переписывать
  его под действующие носители значило бы стереть предмет решения.
- Финализационные executor'ы — `docs/components/FinalizeDealEntryExecutor.md`,
  `docs/components/FinalizeDealExitExecutor.md`,
  `docs/components/MarkDealClosedExecutor.md`,
  `docs/components/MarkDealErrorExecutor.md`,
  `docs/components/MarkDealEmergencyClosedExecutor.md` (введён на шаге 7, N8 —
  `docs/decisions/pnl-finalization-mechanics.md` реш.3).
- Прецедент материализации операционной модели —
  `docs/decisions/deal-action-state-materialization.md`.
- Retry-база — `docs/components/RetryPolicyService.md`.
