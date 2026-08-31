# RefreshBalanceExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_BALANCE_COMMAND`.

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

## Первое наблюдение базы риска

**Пустую `Exchange.riskBase` заполняет этот исполнитель — той же
транзакцией, что приземляет снимок.** Обновив account-level поля и
заменив список `Balance`, исполнитель смотрит на строку биржи, которой
принадлежит снимок: база пуста — он записывает в неё доступный остаток
расчётной валюты и той же записью проставляет `Exchange.riskBaseCurrency`;
база непуста — не трогает её ни при каком остатке. Запись однократная и
только из пустоты; что происходит с базой дальше, решает её дом, и здесь
это не пересказывается. Дом политики базы —
`docs/rules/risk-policy.md`.

**Операнд** — `Balance.externalAvailableBalance` строки
`externalCurrency = Instrument.externalSettlementCurrency`. Расчётную
валюту исполнитель резолвит по инструменту, ради которого команда
эмитирована; это его единственная зависимость от инструмента, и она
названа: без неё строку операнда не выбрать. У приземлившегося снимка эта
строка есть по построению — снимок без расчётной валюты не приземляется
вовсе, а даёт контролируемую ошибку счёта.

**Момент достижим на пустой базе, и это проверено от сценария.**
`REFRESH_BALANCE_COMMAND` — read-only команда, преконтроль риска её не
проверяет (`docs/rules/risk-validator-scope.md`), поэтому пустая база её
не отвергает. Первая сделка биржевого счёта доходит до этой команды
раньше, чем до преконтроля: обработчик обеспечивает свежий
`BalanceContainer` **до** расчёта размера и преконтроля risk-sensitive
действия, а при отсутствующем снимке добывает его этой командой и уходит
на новый проход (`docs/processes/risk-evaluation.md`). К расчёту и
преконтролю база уже записана — контур стартует без отдельной тропы
онбординга биржевого счёта и без ручной подстановки числа в базу.

**Неположительный остаток базой не становится.** Наблюдением считается
строго положительный остаток; ноль и отрицательный оставляют базу пустой.
Довод — **различимость**: записанный ноль неотличим от «база наблюдалась
и равна нулю», а средств могло не быть лишь в момент снимка; пустая база
этот факт различает и говорит «наблюдения не было». Отказ при этом тот
же, что у пустой базы, и он громкий: risk-creating действие отвергается
кодом `BALANCE_INVALID` (`docs/components/RiskValidator.md`). Обоснование
самой политики базы — `.claude/decisions/risk-base-follows-balance.md`;
здесь оно не пересказывается.

**Отчёт ненаблюдения — состав:**

| Поле | Значение |
|---|---|
| `code` | `RISK_BASE_NOT_OBSERVED` (дом кода — этот файл) |
| `kind` | `STATE` — факт держится: пока остаток неположителен, команда тикает каждым проходом, и `EVENT` дал бы строку на каждый тик |
| `severity` | `NON_CRITICAL` — kill-switch в составе реакции нет, отказ и так громкий |
| `scope` | **биржа**: база живёт на строке `Exchange` (`docs/models/domain/core/Exchange.md`) |

Правила дедупа и критичности — `docs/models/domain/other/AnomalyReport.md`.

**Ненаблюдение различимо в данных.** Остаток оказался неположительным —
исполнитель заводит журнальный отчёт с машинным кодом
`RISK_BASE_NOT_OBSERVED`; сам снимок при этом приземляется штатно. Без
отчёта пустая база не отличала бы «команда ни разу не отрабатывала» от
«отработала, наблюдать было нечего», а благоприятного умолчания у
непроверенного признака быть не может (`docs/concept.md`).

## Связи

- База риска и её движение — `docs/rules/risk-policy.md`.
- Строка биржевого счёта — `docs/models/domain/core/Exchange.md`.
- Снимок средств — `docs/models/domain/core/BalanceContainer.md`.
- Носитель наблюдаемости — `docs/models/domain/other/AnomalyReport.md`.
