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
    RiskValidationResult riskValidationResult
);
```

- `dealContext` — фактическое состояние сделки (позиция, live orders/algo,
  `DealActionState`, active risk);
- `currentStatus` — различить `PRECHECK` и этапы, где live risk уже есть
  или мог появиться;
- `riskValidationResult` — итоговое решение и конкретные `RiskCheckCode`.

Параметры `strategyStep` / `strategyAction` / `calculatedAction` **не**
передаются: маппинг кода риска в действие опирается только на
`riskValidationResult`, `dealContext` и `currentStatus`. Reduce-only intent
и направление, если нужны, доступны из `dealContext` (`deal.direction`,
runtime graph) — отдельных параметров под них нет.

`boolean liveRiskExists` отдельным параметром не передаётся — это
производное состояние, выводится из `DealContext + currentStatus + runtime
graph`.

## Границы

Сам команды не исполняет; политику маппинга кодов риска в действие держит
здесь, исполнение `RiskBlockAction` — у FSM handler (см.
`docs/processes/risk-evaluation.md`).
