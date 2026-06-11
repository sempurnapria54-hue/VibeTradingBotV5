# Шаг 4 — SYNC_DOCS_FROM_CODE

## На какой вопрос отвечает этот файл

Какие расхождения код↔доки выявлены после аппрува `CODE` шага 4 и как
они реконсилированы (направление docs←code).

## Контекст

Под-шаг 6 процесса `roadmap-step-execution`. Код шага 4 (командный слой:
ядро `ServiceCommand`, модели, persistence, OKX-интеграция, резолверы,
13 исполнителей + диспетчер + retry, `ServiceCommandFactory`) написан
инкрементами 1-8, скомпилирован чисто (JDK 25, `mvn clean compile`),
проревьюен и **аппрувнут пользователем**. Рантайм не прогонялся
(PostgreSQL/креды — отдельно, за пользователем). Сверка полная:
add / change / remove.

## Расхождения

### ADD (код сверх доков — добавления)

- **Balance persistence** — `BalanceContainerEntity`/`BalanceEntity`,
  таблицы `balance_containers`/`balances` (Flyway `V7`),
  `BalanceContainerDataService` (replace-семантика балансов),
  `BalanceContainerMapper` domain↔persistence. Фактура: `REFRESH_BALANCE`
  нужен upsert контейнера; persistence — scaffold-слой, заполняется по
  потребности (как Order/AlgoOrder/Position, отдельных per-entity
  persistence-доков нет).
- **`CalculatedStrategyAction` / `CalculatedPrice` / `CalculatedSize`** —
  созданы **минимальными command-facing заглушками** в
  `domain.command.calc` (вход `ServiceCommandFactory`). Полный
  калькуляторный выход (purposes, набор цен, разложение sizing) —
  **шаг 5**; см. пометку в `docs/components/models/CalculatedStrategyAction.md`.
- **Evidence-cycle эндпоинты `IntegrationService`** — `getPendingOrders`/
  `getOrderHistory`/`getPendingAlgoOrders`/`getAlgoOrderHistory`/
  `getFillsHistory` + методы `OkxRestClient` + пути в `Constants`.
  Реализуют **уже задокументированный** цикл
  (`refresh-evidence-cycle-ownership`): звенья внутреннего обхода, не
  новый внешний контракт.
- **`OkxProxyController`** — тестовая поверхность (9 эндпоинтов:
  get/place/cancel order·algo, position·close, balance, fills,
  instrument) для рантайм-прогона OKX demo через прокси.
- **Инфра-утилиты** — `ClientIdGenerator` (stable client id);
  `ServiceCommandRetryPolicy`/`ServiceCommandRetryProperties` (конфиг
  политики retry поверх `Retryable`/`RetryPolicyService`).

### CHANGE (код отличается от доков)

- **`KillSwitchExecutor` — порядок риск-минимизации.** Док не
  специфицировал порядок; код задаёт: close позиции (доминирующий live
  market risk, autoCxl) → cancel ordinary orders (анти-re-entry) →
  cancel algo-защит (reduce-only, последними) → **безусловный** финальный
  best-effort close (ловит entry-ордер, исполнившийся во время отмен).
  Реконсилировано в `docs/components/KillSwitchExecutor.md` (§Порядок
  исполнения).
- **`StatusResolveResult`** — `@Value` + явная generic-фабрика `of`
  вместо `@Value(staticConstructor="of")` (обход трения IDEA-Lombok с
  generic staticConstructor при аргументе `null`). Impl-деталь,
  контракт неизменен.
- **Конвенция маппинга** — перенос `*ExternalSnapshot` → сущность делает
  маппер (`updateFromSnapshot(@MappingTarget)`), не исполнитель; резолв
  статуса остаётся в исполнителе. Зафиксировано в
  `.claude/rules/codestyle.md` §Маппинг.

### DEFER (код < доков — отложенные refinements)

Доки описывают целевой дизайн; код — первый проход. Не правки доков, а
форвард-долг (`.claude/work/backlog.md` §Хвост шага 4):

- **SUBMIT recovery-by-clientId** (поиск на бирже до place при пустом
  externalId) — пока place-if-blank.
- **ClosePosition settle ccy** — пока `null` в close-request.
- **`ServiceCommandFactory`: REPLACE-оркестрация ног** (порядок по
  риск-классу) и **CANCEL-резолюция цели по цепочке** — не реализованы.
- **Refresh algo: обновление external-полей дерева `condition`** —
  обновляются только top-level факты (`updateFromSnapshot` игнорит
  `condition`).
- **Evidence-cycle пагинация назад по `billId`** (orders/fills) — пока
  одна страница на звено.
- **`FINALIZE_DEAL_*` / `MARK_DEAL_*` исполнителей нет** (4 из 17 типов
  команд без исполнителя) — **по концепции** (DEAL-Q1, шаг 7), не
  отложение шага 4.

### REMOVE

Удалений (доки описывают то, чего в коде сознательно нет) — нет.

## Пост-хок концепт-гейт (§6a) — не триггерится

На `CODE` **не въехал** продуктовый концепт/контракт-инкремент,
закрывающий концепт-пробел (признак-триггер §6a — решение с
обоснованием или закрытый концепт-пробел). Реализована утверждённая
концепция (REPLACE-only принят в `GAPS_CLOSE_3` до CODE) + отложения
(код < доков) + одна codestyle-конвенция (mapper) + поведенческие
refinements (порядок KillSwitch, evidence-cycle обход) в рамках
задокументированной минимальной семантики. → `SYNC_DOCS_FROM_CODE` →
`DONE` напрямую (§6a, ветка «только поля/механика без новой контрактной
семантики»).

## Открытый хвост (non-gating, вне шага 4)

- **И-2** — runtime-подтверждение `cancel-advance-algos` (trailing) в
  demo trading; ждёт кредов demo (за пользователем).
- **DEAL-Q1** (финализационные исполнители, шаг 7), **RISK-Q1/Q2**
  (шаг 5), **OKX-Q1/Q2/Q3** (fills/архив/bills), **CMD-Q4** (unknown
  live tails — Precheck/AnomalyJob, шаги 5/8).
- Рантайм-прогон командного слоя через `OkxProxyController` — отдельно,
  после шага 4.
