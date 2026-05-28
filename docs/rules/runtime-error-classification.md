# Классификация runtime-ошибок

## На какой вопрос отвечает этот файл

Какое у нас правило классификации unexpected runtime-ошибок:
`RuntimeErrorCode`, общий `EXCHANGE_ERROR`, retryable-политика.

## Правило

Unexpected exceptions **не** превращаются в `CalculationError` и
`RiskValidationResult` — это технические ошибки runtime-flow. Они ловятся
на границе `DealOrchestratorJob` / FSM execution boundary и
классифицируются тремя кодами `RuntimeErrorCode`:

- `INTERNAL_ERROR` — баг приложения, NPE, mapper error, unexpected state,
  внутренняя техническая проблема;
- `EXCHANGE_ERROR` — ошибка взаимодействия с биржей: timeout, connection
  reset, gateway/API error, ошибка exchange client;
- `VALIDATION_ERROR` — нарушение инварианта/валидации, которое не должно
  было попасть в runtime.

### EXCHANGE_ERROR — общий код

`EXCHANGE_ERROR` используется вместо отдельных `UNKNOWN_RESULT` /
`EXCHANGE_TIMEOUT` (на первом этапе они не используются). FSM не строит
отдельную ветку по `EXCHANGE_ERROR`: каждый проход сначала обновляет
exchange facts через refresh/search/history, затем анализирует
`DealContext`, `DealActionState`, runtime-сущности.

### Retryable-политика

- `EXCHANGE_ERROR` может быть retryable;
- `INTERNAL_ERROR` и `VALIDATION_ERROR` по умолчанию non-retryable.

Реакция:

```text
Deal -> ERROR -> ErrorHandler / safety-flow
если ошибка связана с action:
  retryable EXCHANGE_ERROR -> DealActionState = RETRY_PENDING
  INTERNAL_ERROR / VALIDATION_ERROR -> DealActionState = FAILED
```

### Legacy

Enum `RetryErrorType` (NETWORK / EXCHANGE_TIMEOUT / EXCHANGE_REJECTED /
VALIDATION / DATABASE / UNKNOWN_RESULT / UNKNOWN) — исторический черновик,
**вытеснен** `RuntimeErrorCode`; для новой runtime-политики не
используется.

## Первоисточник и смежное

Правило сквозное по командам/калькуляторам/risk (повторяется в СК §11.4.1,
КЛ §20.1, FSM, АУ — единого владельца-сущности нет, см.
`.claude/decisions/rule-source-of-truth.md`); владеет enum
`RuntimeErrorCode`. Граница controlled vs unexpected ошибок —
`docs/components/models/CalculationError.md`,
`docs/components/models/RiskValidationResult.md`. Retry-механика —
`docs/components/RetryPolicyService.md`.
