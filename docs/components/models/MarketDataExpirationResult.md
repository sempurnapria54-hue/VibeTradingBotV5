# MarketDataExpirationResult

## На какой вопрос отвечает этот файл

Что это за runtime value object `MarketDataExpirationResult`: структура,
енум `Status`.

## Назначение

`MarketDataExpirationResult` — результат проверки свежести рыночных
данных, который возвращает `MarketDataExpirationChecker` (см.
`docs/components/MarketDataExpirationChecker.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

Отвечает на вопрос: нужные данные свежие, частично устарели, полностью
устарели или отсутствуют. Само поведение при устаревании задаёт не этот
объект, а `StrategyStep.marketDataExpiredSetting` (см.
`docs/models/core/Strategy.md` и `docs/rules/market-data-freshness.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `status` | `Status` | Общий статус устаревания данных. |
| `checkedAt` | `OffsetDateTime` | Когда выполнена проверка. |
| `expiredSince` | `OffsetDateTime` | С какого времени данные считаются устаревшими. |
| `reasons` | `List<String>` | Причины устаревания или отсутствия данных. |

## Енум `Status`

`NOT_EXPIRED`, `PARTIALLY_EXPIRED`, `EXPIRED`, `MISSING`.
