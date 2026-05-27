# Локальные вопросы: миграция Balance

## На какой вопрос отвечает этот файл

Что неясно / отложено по миграции архивной сущности Balance
(`BalanceContainer` / `Balance`).

## Контекст

Источник: `.claude-archive/2026-05-21/docs/domain/models/Balance.md`
и `.../mapping/okx/OKX_Balance_mapping.md`. Стратегия cross-cutting:
парковать подсистемы, которыми Balance не владеет; создавать только
то, чем владеет сама сущность; ссылаться по имени. Ниже — форвард-
заметки (что мигрировать при владельце) и собственно открытые
вопросы.

## Форвард-заметки (мигрируются с владельцем)

- **BAL-Q1. Подсистема ServiceCommand / REFRESH_BALANCE.**
  `REFRESH_BALANCE` — единственный runtime-flow обновления
  `BalanceContainer`; read-only для биржи, но меняет локальный
  persisted snapshot. Flow: FSM/handler → `REFRESH_BALANCE` →
  `RefreshBalanceExecutor` → `ClientService` (raw → validation →
  `BalanceContainerMapper` → `BalanceContainerExternalSnapshot`) →
  upsert `BalanceContainer` → replace balances. Подсистема
  cross-cutting (свой архивный кластер: `Сервисные команды.md`,
  `Команда REFRESH_BALANCE.md`, `Границы выполнения команд...`),
  охватывает все сущности. **Отложено** до отдельной миграции
  command-подсистемы. Replace semantics и null contract как свойства
  модели — уже зафиксированы в `BalanceContainer.md`; orchestration
  и error-reaction — здесь, для command-миграции.

- **BAL-Q2. RiskValidator (shared).** Balance-relevant поведение:
  использует fresh `BalanceContainer` как input (account-level
  equity + поля settle currency), не обновляет его, не вызывает
  `ClientService`/`REFRESH_BALANCE`, не создаёт `ServiceCommand`;
  при absent/stale/invalid → `RiskValidationResult.BLOCKED` с code
  `BALANCE_NOT_FRESH` / `BALANCE_INVALID`. Зафиксировать при миграции
  RiskValidator (вероятно при Position/Deal). В `BalanceContainer.md`
  — упоминание со ссылкой.

- **BAL-Q3. Shared RVO: DealContext, CalculationContext.** Создаются
  с владельцами (Deal / расчётный слой) как
  `docs/components/models/`. Balance-участие: `DealContext` держит
  последний persisted `BalanceContainer` (не гарантия свежести);
  `CalculationContext` может использовать как input для sizing, не
  обновляя. Состав `DealContext` (по архиву): `deal`, `exchange`,
  `instrument`, `strategyDetail`, `balanceContainer`, `position`,
  `orders`, `algoOrders`, `actionStates` — для миграции Deal.

- **BAL-Q4. FSM / handler freshness (Deal-owned).** FSM/handler
  обязан обеспечить fresh `BalanceContainer`: при старте обработки /
  `PRECHECK`; перед risk-creating / risk-increasing / risk-weakening
  action; при финализации выхода; при emergency/safety finalization.
  При absent/stale — создаёт `REFRESH_BALANCE` и не вызывает
  `RiskValidator` на этой итерации; после refresh FSM пересобирает
  `DealContext`. Зафиксировать при миграции lifecycle Deal.

- **BAL-Q5. Deal.resultProfit (Deal-owned правило).** `resultProfit`
  считается через `REFRESH_FILLS` и факты исполнений, **не** по
  balance diff (`rule-source-of-truth.md` → владелец `Deal`).
  `REFRESH_BALANCE` после выхода нужен для актуального account
  snapshot, не для PnL. Зафиксировать при миграции Deal; в
  `BalanceContainer.md` — упоминание со ссылкой.

- **BAL-Q6. Adapter/checker компоненты Balance.**
  `BalanceFreshnessChecker` (checker) и `BalanceContainerMapper`
  (mapper) — компоненты adapter/command-слоя. Существо их работы
  захвачено: freshness-формула — в `BalanceContainer.md`; mapping и
  валидация — в `docs/client/okx/rules/okx-balance-mapping.md`. Сами
  `docs/components/<...>.md` — отложены до command/adapter-миграции
  (чтобы не плодить тонкие доки из частичного обзора). Развилка:
  считать ли entity-specific executor/checker/mapper «владением
  Balance» (создавать сразу) или частью command-подсистемы
  (отложить). Выбрано отложить — согласно стратегии «парковать
  cross-cutting».

- **BAL-Q7. Аудит / история баланса.** Намеренно вне модели Balance;
  отдельный процесс (`.claude-archive/.../processes/Audit/Аудит и
  история исполнения.md`). Мигрируется в составе аудита.

## Открытые вопросы (требуют ответа)

- **BAL-Q8. OKX balance endpoint.** В архивной mapping-доке путь —
  `GET /api/v5/account/balanceExternalSnapshot`, что выглядит как
  ошибка (нормализованный snapshot — доменное имя, не путь OKX). В
  `okx-balance-mapping.md` записан вероятный реальный путь
  `/api/v5/account/balance`. Сверить с авторитетной API-докой
  (`.claude-archive/.../docs/api/okx/Получить баланс REST.md`) при
  миграции API-кластера OKX и при необходимости поправить.
