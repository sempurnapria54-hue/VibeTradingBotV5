# AttachedAlgoOrderStateResolver

## На какой вопрос отвечает этот файл

Кто определяет доменный статус attached protection «по фактам»
(компонент-resolver): контракт, границы, реализация под биржу.

## Назначение

`AttachedAlgoOrderStateResolver` определяет доменный
`AttachedAlgoOrder.Status` + optional `closeReason candidate` для
embedded защитного algo-order'а внутри `Order` (см.
`docs/models/domain/core/Order.md` §`AttachedAlgoOrder`). Отдельный от
`OrderExternalStatusResolver`, потому что у OKX `attachAlgoOrds` нет
полноценного `state` — статус выводится **по набору фактов**, а не из
одной строковой статус-колонки. Per-биржа реализация:
`OkxAttachedAlgoOrderStateResolver`. FSM/handlers с сырыми фактами биржи
напрямую не работают (см. `docs/rules/external-status-resolution.md`).

## Вход и выход

- **Вход:** факты refresh-контура — `AttachedAlgoOrderExternalSnapshot`
  внутри `OrderExternalSnapshot.attachedAlgoOrders` (матч по
  `AttachedAlgoOrder.internalId == AttachedAlgoOrderExternalSnapshot.internalId`,
  OKX `attachAlgoClOrdId`), `failCode`/`failReason`, **статус parent
  `Order`** (от него зависит трактовка отсутствия attached в snapshot).
- **Выход:** result-object `status + optional closeReason candidate`;
  применяет refresh/executor layer (`closeReason` пишется write-once —
  только если текущий `== null`).

## Матрица «по фактам» — у владельца-lifecycle

Сам алгоритм трактовки фактов (PENDING vs ACTIVE; missing-attached-policy
по статусу parent: `CREATED/PENDING` → ждать; `ACTIVE/PARTIALLY_COMPLETED`
→ доп. search-cycle; `COMPLETED` → анализ позиции/fills; `CANCELED` →
`PARENT_ORDER_CANCELED`; `ERROR` → `UNKNOWN`) — владелец
`docs/lifecycles/Order.md` (§«Attached protection resolving», §«Missing
attached protection policy»). Здесь не дублируется: resolver применяет эту
матрицу, не переопределяет её.

## Границы

Возвращает result-object; сохраняет сущность и принимает решение
refresh/executor layer, не resolver. Resolver не сохраняет сущность, не
меняет `Deal`, не создаёт команды, не принимает FSM-решения, не запускает
cleanup/kill-switch. Заполненные `failCode`/`failReason` → `ERROR`-кандидат
(`PROTECTION_LOST`/`UNKNOWN` по контексту parent). Unknown/неинтерпретируемый
факт → controlled exception, не молчаливый `UNKNOWN` (см.
`docs/rules/controlled-exchange-exceptions.md`).

## Связи

- `docs/lifecycles/Order.md` (матрица фактов attached protection).
- `docs/models/domain/core/Order.md` (`AttachedAlgoOrder`,
  `AttachedAlgoOrderExternalSnapshot`).
- `docs/rules/external-status-resolution.md`,
  `docs/rules/controlled-exchange-exceptions.md`.
- Аналоги: `docs/components/OrderExternalStatusResolver.md`,
  `AlgoOrderExternalStatusResolver.md`, `PositionStatusResolver.md`.
