# Прогресс: миграция Balance

## На какой вопрос отвечает этот файл

На каком шаге миграция архивной сущности Balance и как
классифицирован каждый фрагмент.

## Статус

**Завершено.** Источник:
`.claude-archive/2026-05-21/docs/domain/models/Balance.md` +
`.../mapping/okx/OKX_Balance_mapping.md`.

Имя модели: backlog называет сущность «Balance.md», но агрегат —
`BalanceContainer` (`Balance` — раздел внутри него по
`model-granularity.md`). Файл модели — `BalanceContainer.md` в
`docs/models/core/` (core по `models-core-vs-other.md`).

## Созданные / изменённые файлы

- `docs/models/core/BalanceContainer.md` — модель (создан).
- `docs/rules/raw-exchange-dto-boundary.md` — сквозное правило
  (создан; переиспользуется следующими сущностями).
- `docs/client/okx/models/OkxBalanceResponse.md` — поля OKX response
  (создан).
- `docs/client/okx/rules/okx-balance-mapping.md` — OKX mapping/
  валидация/error policy (создан).
- `.claude/work/questions/tasks/balance.md` — форвард-заметки +
  BAL-Q8 (создан).

## Отчёт по фрагментам

Каркас: область → тип → тема → размещение. Область у всех —
**продукт**.

| # | Фрагмент | Тип | Размещение / диспозиция |
|---|---|---|---|
| Ф1 | `BalanceContainer` = persisted account-state snapshot aggregate; назначение | модель | `BalanceContainer.md` §Назначение |
| Ф2 | `Balance` = currency-level snapshot внутри контейнера | модель (раздел) | `BalanceContainer.md` §Структура/Balance (раздел по `model-granularity.md`) |
| Ф3 | Атрибуты `BalanceContainer` (id, exchangeId, externalUpdatedAt, externalTotalEquity, externalAdjustedEquity, externalAvailableEquity, balances) | модель | `BalanceContainer.md` §Структура |
| Ф4 | Атрибуты `Balance` (id, balanceContainerId, externalCurrency, …) | модель | `BalanceContainer.md` §Структура/Balance |
| Ф5 | `replaceBalances` / replace semantics | модель (инвариант/метод) | `BalanceContainer.md` §Структура + §Normalized snapshots |
| Ф6 | Инварианты «не trading entity, нет lifecycle/Status/active-closed» | модель | Позитивная часть («это snapshot aggregate; runtime-значимость через account/freshness/корректность/settle ccy») → `BalanceContainer.md` §Инварианты. Чистые отрицания свёрнуты по `negative-statements-not-fixated.md`. |
| Ф7 | Обязательность settle currency; multi-currency, но проект требует USDT | модель | `BalanceContainer.md` §Назначение + §Инварианты |
| Ф8 | Freshness вычисляемо (updatedAt + expiration), не Status, не CalculationError, precondition | модель | `BalanceContainer.md` §Свежесть |
| Ф9 | `BalanceFreshnessChecker` (интерфейс/компонент) | компонент | Отложен → BAL-Q6 (adapter/checker слой); формула — в модели |
| Ф10 | Normalized external snapshots (`BalanceContainerExternalSnapshot`, `BalanceExternalSnapshot`), «только runtime-useful поля» | модель (раздел, снапшоты) | `BalanceContainer.md` §Normalized snapshots (по `model-granularity.md`: снапшоты — внутри модели) |
| Ф11 | Raw exchange DTO не выходит за adapter-layer | сквозное правило | `docs/rules/raw-exchange-dto-boundary.md` (первоисточник — сквозной слой, `rule-source-of-truth.md`) |
| Ф12 | Null contract баланса (нет normal null; absent/missing ccy = controlled error; контраст с Position) | модель (правило сущности) | `BalanceContainer.md` §Null contract |
| Ф13 | `REFRESH_BALANCE` flow / `RefreshBalanceExecutor` ответственность / retry / error reaction | компонент + подсистема команд | Отложено → BAL-Q1 (cross-cutting command-подсистема). Доменные свойства (replace, null contract) — в модели |
| Ф14 | Граница `ClientService` (валидация, маппинг только useful) | компонент (ClientService) + сквозное правило | Сквозная часть → `raw-exchange-dto-boundary.md`; OKX-специфика → `okx-balance-mapping.md`; компонент ClientService — отложен (shared) |
| Ф15 | Участие в DealContext (последняя persisted версия, не гарантия свежести) | RVO (shared) | Ссылка в `BalanceContainer.md` §Участие; состав DealContext → BAL-Q3 (с Deal) |
| Ф16 | Участие в CalculationContext (input для sizing, не обновляет) | RVO (shared) | Ссылка в `BalanceContainer.md` §Участие; → BAL-Q3 |
| Ф17 | Участие в RiskValidator (использует поля, BLOCKED при absent/stale/invalid) | компонент (shared) | Ссылка в `BalanceContainer.md` §Участие; → BAL-Q2 (с RiskValidator) |
| Ф18 | Участие в FSM/handler (freshness перед risk-sensitive flow) | lifecycle/handler (Deal-owned) | Ссылка в `BalanceContainer.md` §Свежесть; → BAL-Q4 (с Deal) |
| Ф19 | `Deal.resultProfit` через `REFRESH_FILLS`, не по balance diff | продуктовое правило (Deal-owned) | Упоминание со ссылкой в `BalanceContainer.md` §Расчёт PnL; полная фиксация → BAL-Q5 (с Deal), `rule-source-of-truth.md` |
| Ф20 | «Что не храним в домене» (raw response, borrow/collateral/greeks/история …) | модель | Позитив («только runtime-useful поля») → `BalanceContainer.md` §Чего не хранит домен; отрицания свёрнуты; история → BAL-Q7 |
| Ф21 | OKX endpoint / query / auth / limits | правило биржи | `okx-balance-mapping.md` §Endpoint (endpoint под вопросом → BAL-Q8) |
| Ф22 | OKX raw response структура + поля | модель API биржи | `docs/client/okx/models/OkxBalanceResponse.md` |
| Ф23 | OKX валидация (structural/account/currency/numeric/project policy) | правило биржи | `okx-balance-mapping.md` §Валидация |
| Ф24 | OKX mapping-таблицы (account + currency level) | правило биржи | `okx-balance-mapping.md` §Mapping |
| Ф25 | OKX «что не маппим» (isoEq, imr, mmr, … rewardBal) | модель API биржи | `OkxBalanceResponse.md` §Поля, которые НЕ маппятся |
| Ф26 | OKX error policy (temporary / invalid / null contract) | правило биржи | `okx-balance-mapping.md` §Error policy |
| Ф27 | `BalanceContainerMapper` (mapper-компонент) | компонент | Отложен → BAL-Q6; mapping-существо → `okx-balance-mapping.md` |
| Ф28 | Аудит / история баланса (вне модели) | продуктовый процесс (аудит) | Отложено → BAL-Q7 (с аудитом) |

## Итог по Balance

- Размещено в `docs/`: 5 файлов (1 модель, 1 сквозное правило,
  2 client/okx, + task-вопросы как форвард).
- Отброшены как отрицания (свёрнуты к позитиву): части Ф6, Ф20.
- В локальные вопросы/форвард-заметки: BAL-Q1…Q7 (cross-cutting и
  Deal-owned), BAL-Q8 (открытый вопрос — endpoint).
- Архивные продуктовые открытые вопросы по Balance: §15 — «нет».
