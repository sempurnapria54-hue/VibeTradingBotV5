# ServiceCommandPayload

## На какой вопрос отвечает этот файл

Что такое `ServiceCommandPayload` (параметры команды) и где живут
конкретные payload-подтипы.

## Назначение

`ServiceCommandPayload` — параметры конкретной `ServiceCommand` (см.
`docs/components/models/ServiceCommand.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

Общий принцип payload'ов: хранят **минимум** — обычно локальный ID
сущности, остальное (client id, external id, инструмент) executor берёт из
загруженной сущности. `positionSide`/`marginMode` в payload — generic
command-level intent; OKX adapter всё равно ставит `tdMode=isolated`,
`posSide=net` и валидирует response (см. `docs/models/mapping/Order.md`).

## Где описаны подтипы

Payload документируется **разделом в доке своего executor'а** (решение
`.claude/decisions/executor-payload-file-granularity.md`): без своей
команды payload смысла не имеет, его контекст — ровно один executor.

| Payload | Дом |
|---|---|
| `CreateOrderCommandPayload` (+ `AttachedProtectionPayload`) | `docs/components/CreateOrderExecutor.md` |
| `SubmitOrderCommandPayload` | `docs/components/SubmitOrderExecutor.md` |
| `CancelOrderCommandPayload` | `docs/components/CancelOrderExecutor.md` |
| `CreateAlgoOrderCommandPayload` | `docs/components/CreateAlgoOrderExecutor.md` |
| `SubmitAlgoOrderCommandPayload` | `docs/components/SubmitAlgoOrderExecutor.md` |
| `CancelAlgoOrderCommandPayload` | `docs/components/CancelAlgoOrderExecutor.md` |
| `ClosePositionCommandPayload` | `docs/components/ClosePositionExecutor.md` |

Амендных payload'ов (`AmendOrderCommandPayload` /
`AmendAlgoOrderCommandPayload`) нет — сняты вместе с
`AMEND_*`-командами (`docs/rules/replace-not-amend.md`).

## Базовый тип

`ServiceCommandPayload` — **общий маркер-базовый тип** payload'ов (без
поведения): подтипы (`CreateOrderCommandPayload`, … — см. таблицу выше)
наследуют/реализуют его. Поле `ServiceCommand.payload` типизировано этой
базой (см. `docs/components/models/ServiceCommand.md`).

**Дискриминатор — `ServiceCommandType`** на самой команде
(`ServiceCommand.type`): конкретный тип payload'а выбирается по типу
команды, отдельного поля-дискриминатора в payload'е нет.

База окупается не поведением, а контрактом и расширяемостью: даёт единый
тип поля `ServiceCommand.payload` и границу generic-диспетча
(`ServiceCommandExecutor.execute(P payload, …)`), масштабируется на новые
команды/биржевые модели. Обоснование —
`docs/components/models/ServiceCommandPayload.md`.
