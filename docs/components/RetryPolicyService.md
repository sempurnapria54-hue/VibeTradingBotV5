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

## Авторитет `maxAttempts` (policy, читается живьём)

`maxAttempts` определён в двух местах — поле `ServiceCommandRetryPolicy`
(из конфига, per-command) и поле базы `Retryable` (на строке исполнения
`DealActionState`). **Авторитет — policy (настройка), читается живьём**
каждый тик: предел повторов — операционная крутилка, правка должна
браться сразу везде. Поле на сущности, **если оставляем**, — **снимок для
истории** (что было лимитом на момент попытки), не операторное значение;
retry-петля сверяет `attemptCount >= maxAttempts` по **policy**, а не по
снимку на сущности.

Альтернатива (авторитет — поле сущности, фиксируется при создании)
**отвергнута**: тогда правка предела не бралась бы вживую (N11,
`DOCS_CHECK_2` шага 4).

## Предел — по команде, счётчик — по исполнению

**Предел** (`maxAttempts`) резолвится **по типу текущей команды**;
**счётчик** (`attemptCount`) — сквозной бюджет отказов **одного
исполнения действия** (β, `docs/decisions/command-action-boundary.md`
§4), без обнуления при продвижении стадии. У многозвенного системного
действия предел, применимый к текущей попытке, меняется со звеном — это
намеренно: звенья одного действия однородны по цене отказа (добыча и
завершение разнесены по разным действиям), а клауза записана здесь, чтобы
не читаться как дефект.

**Расхождение доков с кодом (находка `DOCS_CHECK_8`-развилки, пункт
`CODE`).** Механизм конфигурации построен (`ServiceCommandRetryProperties`:
`default-policy` + per-command `policies`, чтение живьём), но **секции
`service-command-retry` нет ни в одном конфиге** — `getPolicy` возвращает
`null`, и `canRetry` падает NPE в catch-ветке учёта отказа, подменяя
исходную ошибку. `CODE`: завести секцию + защитить `getPolicy` от
отсутствующего default'а (`.claude/work/backlog.md` §Шаг 7).

## Retry-состояние (Retryable / RetryError)

Retry-состояние хранится в базовом `Retryable`, от которого наследуется
persisted строка исполнения `DealActionState`
(`docs/models/domain/other/DealActionState.md`; оба вида — STRATEGY и
SYSTEM):

- `Retryable`: `attemptCount`, `maxAttempts` (снимок; авторитет — policy,
  см. выше), `nextRetryAt`, `lastError` (`RetryError`).
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

Для `SUBMIT_*`, `CANCEL_*`, `CLOSE_POSITION_COMMAND` перед повтором обязателен
refresh/search на бирже — предыдущий запрос мог реально выполниться, даже
если ответ не получен:

```text
SUBMIT retry  -> найти по client id; найден -> восстановить; нет -> отправить заново
CANCEL retry  -> refresh; уже terminal -> cancel успешен; live -> повторить
CLOSE retry   -> REFRESH_POSITION_COMMAND; позиции нет -> close успешен; active -> повторить
```

После рестарта pending-команды как очередь не восстанавливаются (см.
`docs/rules/command-lifecycle.md`); нужная команда выбирается заново по
фактам.
