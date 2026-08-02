# RefreshBalanceExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_BALANCE_COMMAND` (компонент-executor): что делает,
особый контракт (не normal null, не RiskValidator).

## Назначение

Получает `REFRESH_BALANCE_COMMAND` — read-only команда обновления account-level
balance snapshot. Получает от `IntegrationService` уже validated
`BalanceContainerExternalSnapshot`, создаёт `BalanceContainer` при
отсутствии, обновляет account-level поля и полностью заменяет список
`Balance` (см. `docs/models/domain/core/BalanceContainer.md`).

Особенности: баланс не управляемая торговая сущность (нет active/closed
lifecycle, нет status resolver), `REFRESH_BALANCE_COMMAND` не проходит через
`RiskValidator` (см. `docs/rules/risk-validator-scope.md`). Normal `null`
contract не используется: успешный refresh обязан вернуть валидный
snapshot с обязательной `settleCurrency`; пустой response / нет
settleCurrency / invalid fields → controlled external/account error (см.
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/models/mapping/Balance.md`).

Не знает про raw OKX response и не валидирует OKX-specific поля: цепочка
`IntegrationService → raw DTO → validation → BalanceContainerMapper →
BalanceContainerExternalSnapshot → upsert BalanceContainer → replace
balances`. Команда попадает в историю исполнения (см.
`docs/rules/audit-not-runtime-source.md`).
