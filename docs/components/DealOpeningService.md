# DealOpeningService

## На какой вопрос отвечает этот файл

Кто атомарно создаёт `Deal` (компонент-сервис): что делает, чего не
делает.

## Назначение

`DealOpeningService` атомарно создаёт `Deal`. Торговое решение о входе не
принимает — его принял `EntryScannerJob` через проверку
`ENTRY`/`GRID_ENTRY` condition. Получает уже выбранные данные
(`instrumentId`, `strategyDetailId`, `marketPhase`, `entryReason`,
`entryStepType`, направление, минимальный entry context).

## Делает

1. финальная защитная проверка, что по инструменту всё ещё нет активной
   сделки (gatekeeper: exchange/instrument/strategy/risk статусы);
2. создаёт `Deal`, ставит `Deal.Status = PRECHECK`;
3. сохраняет pinned-связи с инструментом и `StrategyDetail`;
4. передаёт `marketPhase` и подробный entry context в аудит/timeline
   (в `Deal` подробный entry context не хранится);
5. сохраняет `entryReason` / `entryStepType`;
6. возвращает созданную сделку.

## Не делает

Не ищет стратегию, не выбирает `StrategyDetail`, не анализирует
индикаторы, не проверяет condition, не создаёт order/algo-order, не
ходит на биржу, не запускает FSM. Создание `Deal` — часть жизненного
цикла, но **не** часть FSM: FSM сопровождает уже созданную сделку (см.
`docs/processes/deal-management.md`).
