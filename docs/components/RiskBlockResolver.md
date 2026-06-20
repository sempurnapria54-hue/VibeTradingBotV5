# RiskBlockResolver

## На какой вопрос отвечает этот файл

Кто превращает результат risk-проверки в действие handler'а (компонент):
контракт, зачем каждый параметр.

## Назначение

`RiskBlockResolver` нужен, чтобы handler не содержал большой `switch` по
всем risk-кодам. Получает результат проверки риска и возвращает
`RiskBlockAction` (см. `docs/components/models/RiskBlockAction.md`):
`RiskValidationResult → RiskBlockAction`.

## Контракт

```java
RiskBlockAction resolve(
    DealContext dealContext,
    Deal.Status currentStatus,
    StrategyStep strategyStep,
    StrategyAction strategyAction,
    CalculatedStrategyAction calculatedAction,
    RiskValidationResult riskValidationResult
);
```

- `dealContext` — фактическое состояние сделки (позиция, live orders/algo,
  `DealActionState`, active risk);
- `currentStatus` — различить `PRECHECK` и этапы, где live risk уже есть
  или мог появиться;
- `strategyStep` — тип шага (`ENTRY` … `FAIL_SAFE`);
- `strategyAction` — исходное намерение стратегии (kind, actionType,
  risk-creating vs reduce-only exit, `positionReducingOnly`);
- `calculatedAction` — рассчитанные цена/размер (`calculatedPrice` /
  `calculatedSize`); reduce-only intent выводится из
  `CalculatedSize.sizeMode`/`closeFraction`, направление — из
  `DealContext.deal.direction` (для order-action также доступно на
  `sourceAction`), отдельных полей направления/reduce-only у
  `CalculatedStrategyAction` нет;
- `riskValidationResult` — итоговое решение и конкретные `RiskCheckCode`.

`boolean liveRiskExists` отдельным параметром не передаётся — это
производное состояние, выводится из `DealContext + currentStatus + runtime
graph`.

## Границы

Сам команды не исполняет; политику маппинга кодов риска в действие держит
здесь, исполнение `RiskBlockAction` — у FSM handler (см.
`docs/processes/risk-evaluation.md`).
