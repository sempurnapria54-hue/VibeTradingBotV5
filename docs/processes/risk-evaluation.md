# Оценка рисков (risk-evaluation)

## На какой вопрос отвечает этот файл

Как устроен процесс оценки риска.

## Назначение

Risk-layer отвечает на вопрос: можно ли выполнить уже рассчитанное
действие при текущем состоянии сделки, позиции, баланса, инструмента и
биржи. Не выбирает стратегию, не проверяет сигнал, не исполняет команды.
Процесс — точка композиции, вызывается из
`docs/processes/deal-management.md` (см.
`.claude/decisions/process-materialization-criterion.md`).

## Поток (risk-creating / risk-increasing action)

```text
FSM handler
  -> выбрал StrategyStep, проверил условие, выбрал StrategyAction
  -> StrategyActionCalculator -> CalculatedPrice / CalculatedSize
  -> RiskValidator -> RiskValidationResult
  -> RiskBlockResolver -> RiskBlockAction (если BLOCKED)
  -> StrategyActionOrchestrator -> одна актуальная ServiceCommand, если разрешено
```

Компоненты: `docs/components/RiskValidator.md`,
`docs/components/RiskBlockResolver.md`. RVO: `RiskValidationResult`,
`RiskCheckResult`, `RiskBlockAction` (см. `docs/components/models/`).

## Когда вызывается

См. `docs/rules/risk-validator-scope.md`: только для risk-creating /
risk-increasing / risk-weakening actions; не для refresh/search/history,
cleanup/safety, finalization и reduce-only partial exit. Перед
risk-sensitive action handler обеспечивает fresh `BalanceContainer`; при
absent/stale — добыча звеном `REFRESH_BALANCE_COMMAND` через
`REFRESH_DEAL_CONTEXT_ACTION` (handler добывающие `REFRESH_*` напрямую не
эмитит, `docs/components/SystemActionExecutor.md`) и новый проход FSM (на
этой итерации `RiskValidator` не вызывается).

## Реакция на результат

```text
транш в PRECHECK + BLOCKED + живого риска у него ещё нет
  -> терминал транша CLOSED, closeReason = RISK_CONTROL   # не авария
     (сделка закрывается, когда так закрылись все её транши)

транш в ENTRY_SUBMITTED / ENTRY_FINALIZED / PROTECTION_SWITCHED / MANAGING + BLOCKED
  (для risk-creating / risk-weakening action)
  -> ребро в ERROR **сделки** -> ErrorHandler / safety-flow
     (ошибочного статуса у транша нет)

WARNING  -> action не блокируется; фиксируется в логах / истории
ALLOWED  -> StrategyActionOrchestrator создаёт команду
```

### Карв-аут исчерпанного бюджета сделки

**`BLOCKED` по `RISK_PER_DEAL_CUMULATIVE_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED` и `DEAL_NOTIONAL_EXCEEDED`
в `ERROR` не уводит**.

**Почему глобальный и сделочный коды тоже в карв-ауте.** Оба достижимы,
когда потолок изменила **система** после приёма стратегии, — то есть по поводу
**внутреннему**, а не по признаку рассогласования состояния сделки. Обе
развязки одинаково не дают превысить потолок (действие не исполняется),
но маршрутизация в `ERROR` дополнительно **создаёт исполнение по рынку**
там, где риск уже был под контролем: `ErrorHandler` снял бы живой риск
защищённой позиции с корректно стоящим стопом, а исход попал бы в
`closeOutcome = NORMAL_EXIT` и в `R`-выборку неотличимо от торгового
решения — то есть ужесточение конфига загрязняло бы распределение `R`.

Исчерпанный бюджет сделки — ожидаемый
исход легитимной стратегии, а не рассогласование состояния: действие
**не исполняется**, сделка остаётся в текущем статусе и ведётся до
выхода имеющимися траншами. Ребро в `ERROR` означало бы, что нормальная работа
стратегии флагается как авария — и стоило бы teardown живого риска там,
где риск под контролем.

**`PROTECTION_COVERAGE_REDUCED` — в том же карв-ауте**. Реджект защитного действия, оставляющего покрытие ниже
живой экспозиции, — не признак рассогласования, а **сработавший
контроль**: реджект приходится на **place-ногу** защитного `REPLACE`
(place-new → подтверждение → cancel-old), поэтому старая защита остаётся
живой и покрытие сохраняется; уводить сделку в `ERROR` значило бы рвать
живой риск там, где он под контролем. Дом предиката —
`docs/rules/live-risk-protection.md`.

Карв-аут не новый по конструкции: тот же ряд, что «транш в `PRECHECK` без
живого риска ⇒ его терминал `CLOSED`, не авария» (схема выше). Ось
разведения одна — **является ли реджект признаком рассогласования**.
Все прочие коды `BLOCKED` на risk-creating / risk-weakening действии
ведут в `ERROR` по-прежнему.

**Статус здесь никто не присваивает.** Схема называет **исход**, а не
писателя: терминал сделки `CLOSED` пишет звено `MARK_DEAL_CLOSED_COMMAND`
(`docs/components/MarkDealClosedExecutor.md`), ребро в `ERROR` — по
карв-ауту природы тропы: решение handler'а ⇒ звено
`MARK_DEAL_ERROR_COMMAND`, перехват ⇒ прямая запись петлёй
(`docs/processes/fsm-execution-layering.md`). Прежняя запись «`Deal.status = ERROR`»
присваиванием выражала писателя, которого на этой тропе нет.

`RISK_CONTROL` отличается от `ENTRY_CONDITION_EXPIRED`: первое — вход
запрещён risk-policy до live risk; второе — входное условие стало false.
Отдельный `ENTRY_RISK_BLOCKED` не используется. Реакция на BLOCKED как
статусная механика — `docs/lifecycles/Deal.md`.

## Несколько actions в одном StrategyStep

Actions выполняются последовательно; для каждого — свежий
`CalculationContext` → расчёт → (risk-validation либо minimal
safety/invariant checks) → одна актуальная команда. Если любое
risk-creating действие step заблокировано — остальные actions step не
выполняются (для транша в `PRECHECK` без живого риска → его закрытие без
ошибки; для этапов с живым риском → `ERROR` сделки и safety-flow). Если exit /
cleanup / safety действие не прошло minimal checks — step останавливается,
flow уходит в recovery / ERROR / safety по контексту.

## Границы с ошибками

`CalculationError` (не смогли безопасно рассчитать) ≠ `RiskValidationResult`
(рассчитали, проверили по policy) ≠ unexpected exception
(`RuntimeErrorCode`, см. `docs/rules/runtime-error-classification.md`).
`CalculatedRiskMetrics` из calculator-layer не передаётся — risk-layer сам
считает нужные метрики.
