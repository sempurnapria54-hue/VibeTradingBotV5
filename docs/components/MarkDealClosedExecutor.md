# MarkDealClosedExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_CLOSED_COMMAND` (компонент-executor): терминальное ребро,
что читает/пишет, идемпотентность, retry-anchor, контракт обязательного
`resultProfit`.

## Назначение

Получает `MARK_DEAL_CLOSED_COMMAND` — **терминальное ребро штатного закрытия**.
**Читает** подтверждённое отсутствие live risk и **уже записанное на `Deal`**
число `resultProfit`/`resultProfitCurrency` (его пишет `FinalizeDealExitExecutor`
на шаге 7 — N7). **Ассертит** непустоту числа (инвариант чистого `CLOSED`) и
**пишет** терминал `Deal.status = CLOSED` — в одной транзакции с
завершением исполнения `FINALIZE_DEAL_EXIT_ACTION`, чьим вторым звеном
является (транзакционная клауза —
`docs/decisions/command-action-boundary.md` §5). Само число
`MARK_DEAL_CLOSED_COMMAND` **не вычисляет и не пишет** — оно durable-живёт полем
`Deal` от звена финализации. `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Терминальное ребро

`EXIT_PENDING → CLOSED` (`docs/lifecycles/Deal.md`). `CLOSED` — terminal:
handler'а не имеет; обязательны `resultProfit`/`resultProfitCurrency`.
`MARK_DEAL_CLOSED_COMMAND` ставит терминал только после подтверждённого отсутствия
live risk (иначе — не терминализирует, остаётся в `EXIT_PENDING`/уходит в
`ERROR`).

## resultProfit на терминальном ребре и контракт неисчислимой прибыли (DEAL-Q2)

- **Расчёт и запись числа** `resultProfit` — **шаг 7**, владелец
  `FinalizeDealExitExecutor` (net из `Position.externalRealizedProfit` +
  разбивка `DealCashFlow` + сверка; пишет число **на `Deal`**,
  `docs/decisions/result-profit-source.md`,
  `docs/decisions/pnl-finalization-mechanics.md` реш.2). `MARK_DEAL_CLOSED_COMMAND`
  **число не считает и не пишет** — **читает `Deal.resultProfit`, ассертит
  непустоту** и ставит терминал `CLOSED` (N7).
- **Step-6 → step-7 переход (placeholder снят).** Механика шага 6 писала
  на терминале интерим-placeholder `resultProfit = BigDecimal.ZERO`, чтобы
  удовлетворить инвариант «на чистом `CLOSED` число обязательно» до расчёта
  шага 7. **Шаг 7 снимает placeholder**: число считает и пишет `FINALIZE_EXIT`
  (реальный net), а `MARK_DEAL_CLOSED_COMMAND` его лишь ассертит. Инвариант непустоты —
  теперь ассерт на `Deal.resultProfit`, а не запись ZERO.
- Если число временно нельзя получить — добыча и финализация **ретраятся**
  бюджетами своих системных действий (`REFRESH_DEAL_CONTEXT_ACTION` /
  `FINALIZE_DEAL_EXIT_ACTION`,
  `docs/components/SystemActionExecutor.md`).
- Если после исчерпания бюджета прибыль всё ещё неисчислима — исполнение
  `FINALIZE_DEAL_EXIT_ACTION` уходит в `FAILED`: чистый терминал `CLOSED`
  **не** ставится; сделка уходит ошибочной тропой
  (`MarkDealErrorExecutor`/`ErrorHandler`) и доходит до **ошибочного
  терминала** (`EMERGENCY_CLOSED`), не зависает живым риском. Инвариант
  «прибыль обязательна» — про чистое закрытие; на ошибочном терминале он не
  блокируется, а число там **best-effort**: доступен net — пишем фактический
  realized net (вкл. `liqPenalty`); **genuinely недоступен — `null` с
  семантикой «неисчислимо», и это не ноль** (реш.3,
  `docs/components/MarkDealEmergencyClosedExecutor.md`). Прежняя клауза
  «число всё равно проставляется — не ноль/null» осталась от редакции до N8 и
  противоречила разведённым провенансам — снята (H16, `GAPS_CLOSE_6`).
  Полный контракт — `docs/lifecycles/Deal.md` §«Терминальный контракт
  финализации».

## Идемпотентность и retry

- **Retry-anchor** — строка исполнения `FINALIZE_DEAL_EXIT_ACTION`
  (второе звено; вид SYSTEM, база `Retryable`;
  `docs/models/domain/other/DealActionState.md`).
- **Идемпотентность** — факт `Deal.status = CLOSED`: повтор на уже
  закрытой сделке — no-op.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; эмиссия звеньев —
`docs/components/SystemActionExecutor.md`.
