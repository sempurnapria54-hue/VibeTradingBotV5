# RefreshPositionsHistoryExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_POSITIONS_HISTORY` (компонент-executor): что делает,
что читает финализированной записью, почему не источник числа.

## Назначение

Получает `REFRESH_POSITIONS_HISTORY` — runtime read-only команда. Загружает
историю закрытой позиции сделки с биржи (`GET /api/v5/account/positions-history`
по `posId`/`instId` закрытой позиции; глубина 3 месяца, сортировка по `uTime`;
`docs/integrations/okx/contracts/position.md` §История), маппит native-ответ в
`PositionCloseResultExternalSnapshot` (готовый net `realizedPnl` + `closeAvgPx`/
`openAvgPx` + `type`/`triggerPx` ликвидации/ADL) и наполняет снапшот-факт числа
для финализации. Цепочка `OkxPositionsHistoryResponse` → validation → snapshot;
маппинги — `docs/models/mapping/PositionCloseResult.md`. Общая семантика
`REFRESH_*` — `docs/components/ServiceCommandExecutor.md`.

## Не источник числа — транспорт снапшота

`Deal` напрямую **не обновляет.** Executor только добывает и наполняет
`PositionCloseResultExternalSnapshot`; заголовочное число `Deal.resultProfit`
пишет `FinalizeDealExitExecutor` (штатная тропа) / `MarkDealEmergencyClosedExecutor`
(аварийная), консолидируя готовый снапшот. Он — **транспорт снапшота числа**, не
его источник-владелец: net считает биржа (`realizedPnl`), финализатор читает
готовое (`docs/decisions/result-profit-source.md`).

**Читает финализированную запись (инвариант N11).** Одна сделка ↔ один `posId` ↔
**одна финализированная** запись positions-history, чей `realizedPnl` кумулятивен
по всем partial-закрытиям и доборам за жизнь позиции; читается, когда позиция
полностью закрыта (`REFRESH_POSITION` показал flat/отсутствие). Чтение
нефинализированного слайса → систематический недосчёт realized. До
рантайм-верификации (контур source-api, demo) инвариант — **предположение**
(`docs/decisions/pnl-finalization-mechanics.md` §6,
`docs/integrations/okx/contracts/position.md` §История).

Идемпотентность: повторный вызов не задваивает снапшот-факт и приводит его к
состоянию биржи (запись адресуется по `posId`). Ретраится через командную
машинерию; торговых решений не принимает, `Deal` в новый статус не переводит.
