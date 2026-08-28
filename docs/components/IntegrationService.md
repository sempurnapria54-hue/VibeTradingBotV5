# IntegrationService

## На какой вопрос отвечает этот файл

Кто является границей биржевого клиента / adapter-layer (компонент):
nullable contract, что не выходит наружу.

## Назначение

`IntegrationService` — adapter boundary к бирже: executor'ы и refresh-flow
ходят на биржу только через него. Сырой биржевой DTO (например, OKX
response) за `IntegrationService` / adapter-layer **не** выходит — наружу
возвращаются только validated `*ExternalSnapshot` (см.
`docs/rules/raw-exchange-dto-boundary.md`).

## Nullable contract

Общий контракт для read/refresh:

```text
snapshot найден                  -> ExternalSnapshot
snapshot не найден (успех)       -> null
ошибка API / parse / invariant   -> exception
```

`null` означает «не найдено в этом источнике», а не ошибку. Трактовка
`null` зависит от сущности: для `Position` успешный `null` = позиции на
бирже нет; для `Order`/`AlgoOrder` последний `null` после полного
evidence-cycle может быть error/recovery (см.
`docs/rules/external-status-resolution.md`).

## Инструмент-скоупный read (чистота / orphan) — вне command-layer

Перечисление **всего живого на инструменте** (позиции, live orders, live
algo — включая **незнакомые** боту сущности) — отдельный
инструмент-скоупный exchange-read **в `IntegrationService`**, **не**
`ServiceCommand`. Дёргается:

- **`PrecheckHandler`** (шаг 6) — чистота инструмента перед входом: нет
  открытой сделки → биржа по инструменту должна быть пуста
  (`docs/components/PrecheckHandler.md`);
- **`AnomalyJob`** (шаг 8) — orphan / чужой live risk при уже открытой
  сделке и по неведомым инструментам (`docs/components/AnomalyJob.md`).

Per-entity `REFRESH_*` покрывает только **известные** сущности сделки;
этот read видит и **неизвестные**. Возвращает validated snapshot'ы
(сырой DTO за границу не выходит). Закрывает Precheck-часть CMD-Q4;
orphan-часть — шаг 8.

## Исключение: balance

Для `REFRESH_BALANCE_COMMAND` normal `null` contract не используется: успешный
refresh обязан вернуть валидный `BalanceContainerExternalSnapshot` с
обязательной `settleCurrency`; пустой response / нет settleCurrency /
invalid fields → controlled external/account error (см.
`docs/models/domain/core/BalanceContainer.md`,
`docs/models/mapping/Balance.md`).

Контракт-интерфейс (ориентир): методы вроде `getPosition(exchange,
instrument)` возвращают snapshot или `null`, бросают exception при
API/parse/invariant error.
