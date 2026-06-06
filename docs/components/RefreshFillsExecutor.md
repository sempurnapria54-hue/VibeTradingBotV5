# RefreshFillsExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_FILLS` (компонент-executor): что делает,
идемпотентность.

## Назначение

Получает `REFRESH_FILLS` — runtime read-only команда. Загружает fills с
биржи, сопоставляет с известными `Order` / `AlgoOrder` / `Position`
facts и обновляет вложенные runtime-сущности. Используется в финализации
сделки для итогового подсчёта profit/loss; `Deal.resultProfit` считается
на основании фактов через `REFRESH_FILLS` (правило — `docs/models/domain/aggregate/Deal.md`).

`Fill` как отдельную persisted entity на первом этапе не вводим (один
общий `RefreshFillsExecutor`; материализация `TradeFill` — backlog п.6).
`Deal` напрямую не обновляет — это делает FSM handler по фактам вложенных
сущностей после refresh-контура.

Идемпотентность: повторный вызов не задваивает filled size / fee /
realized pnl и приводит локальные факты к состоянию биржи. Общая
семантика `REFRESH_*` — `docs/components/ServiceCommandExecutor.md`.
