# ClientService

## На какой вопрос отвечает этот файл

Кто является границей биржевого клиента / adapter-layer (компонент):
nullable contract, что не выходит наружу.

## Назначение

`ClientService` — adapter boundary к бирже: executor'ы и refresh-flow
ходят на биржу только через него. Сырой биржевой DTO (например, OKX
response) за `ClientService` / adapter-layer **не** выходит — наружу
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

## Исключение: balance

Для `REFRESH_BALANCE` normal `null` contract не используется: успешный
refresh обязан вернуть валидный `BalanceContainerExternalSnapshot` с
обязательной `settleCurrency`; пустой response / нет settleCurrency /
invalid fields → controlled external/account error (см.
`docs/models/core/BalanceContainer.md`,
`docs/client/okx/rules/okx-balance-mapping.md`).

Контракт-интерфейс (ориентир): методы вроде `getPosition(exchange,
instrument)` возвращают snapshot или `null`, бросают exception при
API/parse/invariant error.
