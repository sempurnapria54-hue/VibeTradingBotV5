# CalculatedSize

## На какой вопрос отвечает этот файл

Что это за `CalculatedSize`.

## Назначение

`CalculatedSize` — рассчитанный размер order/algo action,
результат `SizeCalculator` (см. `docs/components/SizeCalculator.md`). RVO,
не persisted (см. `.claude/decisions/runtime-value-object.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `sizeContracts` | `BigDecimal` | Размер в контрактах после округления по lot size. |
| `closeFraction` | `BigDecimal` | Доля уменьшения позиции 0..1; только для reduce-only `Order`/`AlgoOrder`. |
| `notionalUsdt` | `BigDecimal` | Номинал позиции в USDT, если рассчитывался. |
| `description` | `String` | Пояснение расчёта (целевое имя; legacy — `explanation`). |
| `sizeMode` | `SizeMode` | Режим размера (уточнённая модель). |

## Енум `SizeMode`

- `OPEN_OR_INCREASE` — размер для открытия или увеличения позиции;
- `REDUCE_ONLY` — только для уменьшения существующей позиции;
- `FULL_CLOSE` — для полного закрытия позиции;
- `NOT_REQUIRED` — для команды размер не требуется.

## Замечание

`sz` в OKX API для SWAP/FUTURES — это контракты, не USDT. Формула расчёта
контрактов через `ctVal`/`lotSz`/`minSz` — у `docs/components/SizeCalculator.md`.
Direct partial close позиции не рассчитывается; полное закрытие идёт
market-close'ом (ведёт `DealExitPendingHandler`) — и при выходе по
условию-переходу, и при явном действии шага `EXIT`; частичное уменьшение —
через reduce-only `Order`/`AlgoOrder` (см.
`docs/rules/no-partial-close.md`).
