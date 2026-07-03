# MarkDealClosedExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_CLOSED` (компонент-executor): терминальное ребро,
что читает/пишет, идемпотентность, retry-anchor, контракт обязательного
`resultProfit`.

## Назначение

Получает `MARK_DEAL_CLOSED` — **терминальное ребро штатного закрытия**.
**Читает** подтверждённое отсутствие live risk и готовый результат
финализации выхода (`FinalizeDealExitExecutor`). **Пишет** терминал
`Deal.status = CLOSED` с обязательными `resultProfit` /
`resultProfitCurrency` и `DealFinalizationState(MARK_CLOSED).status =
COMPLETED`. `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Терминальное ребро

`EXIT_PENDING → CLOSED` (`docs/lifecycles/Deal.md`). `CLOSED` — terminal:
handler'а не имеет; обязательны `resultProfit`/`resultProfitCurrency`.
`MARK_DEAL_CLOSED` ставит терминал только после подтверждённого отсутствия
live risk (иначе — не терминализирует, остаётся в `EXIT_PENDING`/уходит в
`ERROR`).

## Обязательный resultProfit и контракт неисчислимой прибыли (DEAL-Q2)

- *Сам расчёт* `resultProfit` — **шаг 7** (граница 6 ↔ 7); `MARK_DEAL_CLOSED`
  механики шага 6 обязан **удовлетворить инвариант** наличия числа на
  чистом терминале, не вычислять его внутри себя.
- **Как шаг 6 удовлетворяет инвариант — интерим-placeholder ZERO.** Executor
  пишет **явный механический placeholder** `resultProfit = BigDecimal.ZERO` +
  `resultProfitCurrency = settleCurrency` (settle-валюта резолвится из
  `BalanceContainer`), чтобы на чистом терминале было число до расчёта шага 7.
  Placeholder помечен как интерим; **шаг 7** (`REFRESH_FILLS` / `TradeFill`)
  **заменит** его расчётным PnL. Это задокументированный интерим, **не**
  молчаливый ZERO-fallback: если settle-валюта **не резолвится** — executor
  **кидает failure** (`VALIDATION_ERROR`), а не садит тихий ZERO (уход на
  retry/ошибочную тропу — ниже). Разграничение placeholder vs error-fallback —
  `docs/models/domain/aggregate/Deal.md` §«Итоговый PnL».
- Если число временно нельзя получить — финализация **ретраится** по общему
  механизму (`DealFinalizationState`).
- Если после исчерпания retry прибыль всё ещё неисчислима —
  `DealFinalizationState(MARK_CLOSED) = FAILED`: чистый терминал `CLOSED`
  **не** ставится; сделка уходит ошибочной тропой
  (`MarkDealErrorExecutor`/`ErrorHandler`) и доходит до **ошибочного
  терминала**, не зависает живым риском. Инвариант «прибыль обязательна» —
  про чистое закрытие; ошибочный терминал на нём не блокируется. Что именно
  с числом прибыли на ошибочном терминале — деталь **шага 7**. Полный
  контракт — `docs/lifecycles/Deal.md` §«Терминальный контракт финализации».

## Идемпотентность и retry

- **Retry-anchor** — `DealFinalizationState(deal, MARK_CLOSED)` (база
  `Retryable`, см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- **Идемпотентность** — через `UNIQUE(deal_id, finalization_type)`: повтор
  на уже `CLOSED`-сделке — no-op → `COMPLETED`.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; модель retry-state —
`docs/models/domain/other/DealFinalizationState.md`.
