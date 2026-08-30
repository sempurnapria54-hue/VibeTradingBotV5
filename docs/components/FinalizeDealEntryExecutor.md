# FinalizeDealEntryExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `FINALIZE_DEAL_ENTRY_COMMAND`.

## Назначение

Получает `FINALIZE_DEAL_ENTRY_COMMAND` — консолидацию результата входа
после того, как entry order финализирован и позиция подтверждена;
единственное звено системного действия **`FINALIZE_DEAL_ENTRY_ACTION`**
(`docs/components/SystemActionExecutor.md`). **Читает** подтверждённые
факты входа (entry `Order` финализирован; `Position` активна и
соответствует сделке/инструменту/направлению; при необходимости цена
входа / order-fill-метрики `accFillSz`/`avgPx` из уже выполненного
`REFRESH_ORDER_COMMAND`). **Пишет** консолидированный результат входа на runtime
graph сделки **и статусное ребро транша `DealTranche.status =
ENTRY_FINALIZED`** — в
**одной транзакции** с durable-продвижением своего исполнения
(валидация 4 развилки «команда ↔ действие»; второй экземпляр паттерна
N7 — транзакционная клауза `docs/rules/command-lifecycle.md`). На биржу сам не ходит; новых торговых решений не принимает
(`RiskValidator` не вызывается, `docs/rules/risk-validator-scope.md`).

## Статусное ребро

`ENTRY_SUBMITTED → ENTRY_FINALIZED` **транша** пишет **само звено**, не
handler:
`TrancheEntrySubmittedHandler` своими выходными проверками **гейтит эмиссию**
команды (`docs/components/TrancheEntrySubmittedHandler.md`), а ребро едет в
транзакции завершения — окно, в котором между `COMPLETED` исполнения и
переводом статуса могло завестись второе исполнение консолидации,
закрыто. Это действующий паттерн статусных рёбер (терминалы пишут
`MARK_*`-команды), не исключение
(`docs/processes/fsm-execution-layering.md`).

## Энфорсеров счёта не пишет

Ни `Exchange.consecutiveLossCount`, ни ход `Exchange.riskBase` вниз этому
звену не принадлежат: оно консолидирует **вход**, терминалом сделки не
является и её результата не знает. Ход вниз у обеих величин пишут оба
терминальных исполнителя; у базы риска есть и третий писатель — первое
наблюдение при приземлении снимка средств
(`docs/rules/loss-streak-halt.md`, `docs/rules/risk-policy.md`).

## Идемпотентность и retry

- **Retry-anchor** — строка исполнения `FINALIZE_DEAL_ENTRY_ACTION`
  (вид SYSTEM, база `Retryable`;
  `docs/models/domain/other/DealActionState.md`).
- **Идемпотентность** — факт `DealTranche.status = ENTRY_FINALIZED`: транш
  уже в нём → действие не заводится, повтор звена — no-op; плюс частичный
  ключ живого исполнения (`deal_id`, `deal_tranche_id`,
  `system_action_type`).
- Падение → `RETRY_PENDING`/`FAILED` по
  `docs/components/RetryPolicyService.md` и
  `docs/rules/runtime-error-classification.md`.

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; эмиссия звеньев —
`docs/components/SystemActionExecutor.md`.
