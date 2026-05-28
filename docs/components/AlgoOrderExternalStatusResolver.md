# AlgoOrderExternalStatusResolver

## На какой вопрос отвечает этот файл

Кто переводит внешний статус standalone algo-order в доменный (компонент-
resolver): ответственность, границы, реализация под биржу.

## Назначение

`AlgoOrderExternalStatusResolver` преобразует внешний статус/факт
standalone algo-order биржи в доменный `AlgoOrder.Status` + optional
`closeReason candidate`. Per-биржа реализация:
`OkxAlgoOrderExternalStatusResolver`. FSM/handlers с сырыми строками
биржи не работают (см. `docs/rules/external-status-resolution.md`).

## Контракт и границы

Возвращает result-object (`status + closeReason candidate`); применяет
refresh/executor layer. Resolver не сохраняет сущность, не принимает
FSM-решения, не запускает cleanup. Неизвестный статус → не молча в
`UNKNOWN`, а `ExternalStatusException` (см.
`docs/rules/controlled-exchange-exceptions.md`).

## OKX-маппинг

`live`/`pause`→ACTIVE, `partially_effective`→PARTIALLY_COMPLETED
(recovery-state, не штатная цель), `effective`→COMPLETED/TRIGGERED,
`canceled`→CANCELED, `order_failed`→`ExternalStatusException(ORDER_FAILED)`,
`partially_failed`→`ExternalStatusException(PARTIALLY_FAILED)` (детали —
`docs/client/okx/rules/okx-algo-order-mapping.md`).
