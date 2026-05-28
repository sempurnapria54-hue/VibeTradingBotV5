# Локальные вопросы: миграция «Сервисные команды»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «Сервисные команды» (локальные
вопросы и форвард-заметки прохода 1).

## Открытые вопросы

- **СК-Q1. `RetryErrorType` (legacy) vs `RuntimeErrorCode` (актуальный).**
  §11.4 даёт enum `RetryErrorType` (NETWORK, EXCHANGE_TIMEOUT,
  EXCHANGE_REJECTED, VALIDATION, DATABASE, UNKNOWN_RESULT, UNKNOWN), но
  §11.4.1 явно помечает его «исторический черновик» и вводит
  `RuntimeErrorCode` (INTERNAL_ERROR / EXCHANGE_ERROR / VALIDATION_ERROR)
  + общий `EXCHANGE_ERROR` вместо `UNKNOWN_RESULT`/`EXCHANGE_TIMEOUT`.
  На проходе 2 мигрировать **только** актуальную модель; legacy-enum не
  воспроизводить (или зафиксировать как вытесненный). Связано с
  правилом классификации ошибок (см. карту, RULE13).
- **СК-Q2. Гранулярность executor-компонентов.** §13 описывает ~14
  executor'ов детально (`CreateOrderExecutor`…`RefreshFillsExecutor`,
  `ClosePositionExecutor`, `RefreshBalanceExecutor`). Плюс упомянуты, но
  без отдельных секций, refresh-executor'ы под `REFRESH_PENDING_ORDERS`,
  `REFRESH_ALGO_ORDERS`, `REFRESH_ORDER_HISTORY`,
  `REFRESH_ALGO_ORDER_HISTORY`. Развилка: файл на каждый executor vs
  группировка по семантике групп (CREATE_* / SUBMIT_* / AMEND_* /
  CANCEL_* / REFRESH_*). Решить на проходе 2.
- **СК-Q3. Гранулярность payload'ов.** §10 даёт 9+ payload-классов
  (RVO). Отдельный файл `ServiceCommandPayload` со всеми subclass'ами vs
  разделы внутри `ServiceCommand`. Решить на проходе 2.
- **СК-Q4. Финализация: `FINALIZE_*` / `MARK_*` executors и retry-state.**
  §«REFRESH_FILLS и FINALIZE_*» + §15. Связано с открытым **DEAL-Q1**
  (`open-questions.md`): где хранить persisted retry-state финализации
  (`DealActionState` относится к `StrategyAction`, а финализация — это
  lifecycle/system action). Материализация финализационных executor'ов
  частично «под вопросом» до закрытия DEAL-Q1.

## Решения прохода 2

- **СК-Q1 — закрыт.** Мигрирован только `RuntimeErrorCode`
  (`docs/rules/runtime-error-classification.md`); legacy-enum
  `RetryErrorType` зафиксирован как вытесненный (там же и в
  `docs/components/RetryPolicyService.md`).
- **СК-Q2 / СК-Q3 — перенесены в CMD-Q1** (`open-questions.md`):
  гранулярность executor'ов и payload'ов. Текущее решение: executor'ы —
  file-per-executor; payload'ы — один `ServiceCommandPayload.md` с
  разделами.
- **СК-Q4 — финализационные executor'ы не материализованы.**
  `FINALIZE_DEAL_ENTRY`, `FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`,
  `MARK_DEAL_ERROR` executor'ы **не созданы** как компоненты: их
  retry-state финализации не имеет места хранения (`DealActionState`
  относится к `StrategyAction`, финализация — lifecycle/system action) —
  открытый **DEAL-Q1** (`open-questions.md`). Команды присутствуют в
  `ServiceCommandType` (`docs/components/models/ServiceCommand.md`). При
  закрытии DEAL-Q1 — материализовать executor'ы.
- **`DealActionState`** не материализован как модель — открытый
  **DEAL-Q3** (`open-questions.md`).

## Форвард-заметки

- **СК-FW1.** §3 `ServiceCommandType` (полный enum 24 значения) +
  §главная идея — primary source для перечня команд. Используется
  множеством handler'ов и executor'ов; сам по себе — атрибут модели
  `ServiceCommand` (RVO) / справочник команд.
- **СК-FW2.** §6 — полная модель `DealActionState extends Retryable`
  (+ `RuntimeTarget`, `TargetEntityType`, `DealActionStateStatus`).
  Дублирует «Жизненный цикл» §7; выбрать primary (этот док полнее: есть
  `RuntimeTarget`, `Retryable`).
- **СК-FW3.** §11 (retry policy, `ServiceCommandRetryPolicy`,
  `Retryable`, `RetryError`, `RetryPolicyService`, application.yml,
  «опасные команды → refresh/search перед retry») — кандидаты в
  компонент `RetryPolicyService` + RVO/модели retry. Размещение
  `Retryable`/`RetryError` (база персистентной `DealActionState`) —
  уточнить (внутри `DealActionState` vs отдельно).
- **СК-FW4.** §2.5–2.6 controlled exchange exceptions
  (`ExternalStatusException`, `ExternalInvariantViolationException`,
  `ExternalNotFoundException`) + runtime-реакция — сквозное правило;
  пересекается с уже мигрированным `external-status-resolution.md`.
  Дублируется в «Статусы торговых сущностей» §6 (там полнее).
- **СК-FW5.** §«Особенность REFRESH_BALANCE» + §13(RefreshBalance) —
  `RefreshBalanceExecutor` получает validated `BalanceContainerExternalSnapshot`,
  raw OKX DTO не выходит за `ClientService`. Пересекается с
  `raw-exchange-dto-boundary.md` и `okx-balance-mapping.md`. Расширение
  `BalanceContainer.md`.
- **СК-FW6.** §10.1/§10.5 — `tdMode=isolated`/`posSide=net` инварианты
  применяет OKX adapter; `positionReducingOnly → reduceOnly` mapping;
  `autoCxl` (§18.5) — exchange-specific. Кандидаты в `docs/client/okx/`
  (расширение `okx-order-mapping.md`/`okx-position-mapping.md`).
- **СК-FW7.** §7 (`StrategyAction.key` / `targetActionKey`, правила
  валидации 1–8, резолв при сохранении стратегии, `UNIQUE(strategy_detail_id,
  key)`) — расширение `docs/models/core/Strategy.md` + кандидат в
  компонент-валидатор стратегии (см. backlog п.8).
- **СК-FW8.** §главная идея + contracts: `ServiceCommandExecutor`,
  `ClientService` (nullable contract), `ExternalStatusResolver<S,R>` —
  компоненты/интерфейсы. `PositionStatusResolveResult` (RVO) — см.
  «Статусы» §8.5.
