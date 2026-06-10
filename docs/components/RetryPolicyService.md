# RetryPolicyService

## На какой вопрос отвечает этот файл

Кто управляет retry-политикой исполнения команд (компонент): контракт,
модель политики, retry-состояние, правило для опасных команд.

## Назначение

`RetryPolicyService` — техническая политика повтора команд (не часть
торговой стратегии). На первом этапе политика хранится в конфиге
приложения (`application.yml`, секция `service-command-retry` с
`default-policy` и per-command `policies`), а не в БД.

Контракт (ориентир): `getPolicy(commandType)` (fallback на default),
`canRetry(retryable, commandType)`, `calculateNextRetryAt(retryable,
commandType)`.

## ServiceCommandRetryPolicy (модель политики)

`commandType` (`ServiceCommandType`), `maxAttempts` (`Integer`),
`initialDelay` (`Duration`), `maxDelay` (`Duration`), `backoff`
(`RetryBackoffType`).

`RetryBackoffType`: `FIXED`, `EXPONENTIAL`.

## Retry-состояние (Retryable / RetryError)

Retry-состояние хранится в базовом `Retryable`, от которого наследуется
persisted `DealActionState` (`docs/models/domain/other/DealActionState.md`):

- `Retryable`: `attemptCount`, `maxAttempts`, `nextRetryAt`, `lastError`
  (`RetryError`).
- `RetryError`: `code` (`String`), `message` (`String`), `type`. Хранится
  объектом, а не парой строк `lastErrorCode`/`lastError`. Legacy-enum
  `RetryErrorType` (NETWORK / EXCHANGE_TIMEOUT / EXCHANGE_REJECTED /
  VALIDATION / DATABASE / UNKNOWN_RESULT / UNKNOWN) **вытеснен**: для
  runtime-классификации используется `RuntimeErrorCode` (см.
  `docs/rules/runtime-error-classification.md`).

## Как работает retry

```text
Executor упал -> ServiceCommandExecutor ловит ошибку
  -> policy по ServiceCommandType
  -> attemptCount++, nextRetryAt, lastError
  -> DealActionState = RETRY_PENDING
attemptCount >= maxAttempts -> DealActionState = FAILED -> FSM решает
```

## Опасные команды: refresh перед retry

Для `SUBMIT_*`, `CANCEL_*`, `CLOSE_POSITION` перед повтором обязателен
refresh/search на бирже — предыдущий запрос мог реально выполниться, даже
если ответ не получен:

```text
SUBMIT retry  -> найти по client id; найден -> восстановить; нет -> отправить заново
CANCEL retry  -> refresh; уже terminal -> cancel успешен; live -> повторить
CLOSE retry   -> REFRESH_POSITION; позиции нет -> close успешен; active -> повторить
```

После рестарта pending-команды как очередь не восстанавливаются (см.
`docs/rules/command-lifecycle.md`); нужная команда выбирается заново по
фактам.
