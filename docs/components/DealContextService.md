# DealContextService

## На какой вопрос отвечает этот файл

Кто собирает `DealContext` для прохода FSM (компонент-сервис): что
собирает, границы.

## Назначение

`DealContextService` собирает `DealContext` (см.
`docs/components/models/DealContext.md`) для одного прохода FSM: `Deal` с
runtime graph (orders/algoOrders/position), `Exchange`, `Instrument`,
pinned `StrategyDetail`, последний persisted `BalanceContainer`, список
`DealActionState`.

## Границы

Собирает active entities и ограниченный `relatedInactive` (чтобы FSM
видел runtime truth, а не всю историю по инструменту). Exchange facts в
`DealContext` не кладутся сырыми: они сначала применяются
refresh-командами к БД, затем сервис собирает уже обновлённый `Deal`
runtime graph. Свежесть `BalanceContainer` не гарантирует — её проверяет
FSM/handler перед risk-sensitive flow. Свежие рыночные данные и
`CalculationContext` в `DealContext` не входят (собираются в рантайме
калькулятором).
