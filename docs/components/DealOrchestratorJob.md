# DealOrchestratorJob

## На какой вопрос отвечает этот файл

Кто сопровождает уже созданные сделки (компонент-job): цикл работы,
границы.

## Назначение

`DealOrchestratorJob` сопровождает уже созданные `Deal`, прогоняя их
через FSM. Цикл:

1. находит активные `Deal`;
2. для каждой загружает `DealContext` (`DealContextService`);
3. запускает `DealStateMachine` (см. `docs/components/DealStateMachine.md`);
4. получает `TransitionResult`;
5. передаёт команды в `ServiceCommandExecutor`;
6. перезагружает `DealContext` после выполнения команд;
7. сохраняет новый статус сделки.

Это также execution boundary, на которой ловятся unexpected exceptions
(`RuntimeErrorCode`, см. `docs/rules/runtime-error-classification.md`).

## Не делает

Не считает индикаторы, не ищет новые входы (это `EntryScannerJob`), не
ходит на биржу напрямую, не считает цены, не исполняет REST сам. Не
смешивается с `AnomalyJob`: orchestrator ведёт известные `Deal` по FSM,
`AnomalyJob` ищет глобальные нарушения инвариантов (см.
`docs/components/AnomalyJob.md`).
