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
| `sizeContracts` | `BigDecimal` | Размер в контрактах после округления по lot size. У reduce-only выхода это итоговый размер заявки: на обоих полных исходах — экспозиция транша целиком, а не округлённая доля (`exitSizeFinal` в `docs/spec/order-sizing.json`). |
| `closeFraction` | `BigDecimal` | Доля уменьшения позиции 0..1; только для reduce-only `Order`/`AlgoOrder`. |
| `notionalUsdt` | `BigDecimal` | Номинал позиции в USDT, если рассчитывался. |
| `description` | `String` | Пояснение расчёта (целевое имя; legacy — `explanation`). |
| `sizeMode` | `SizeMode` | Режим размера (уточнённая модель). |
| `exitOutcome` | `ExitOutcome` | Исход округления reduce-only выхода. Заполняется только при `sizeMode = REDUCE_ONLY`; у прочих режимов пусто — округлять долю там нечего. |

## Енум `SizeMode`

- `OPEN_OR_INCREASE` — размер для открытия или увеличения позиции;
- `REDUCE_ONLY` — только для уменьшения существующей позиции;
- `FULL_CLOSE` — для полного закрытия позиции;
- `NOT_REQUIRED` — для команды размер не требуется.

## Енум `ExitOutcome`

Исход округления объявленной доли выхода. Форма ветвления и примеры —
`docs/spec/order-sizing.json` (`exitOutcome`); здесь — что значение
означает и кто его читает.

- `PARTIAL` — штатный частичный выход объявленной доли; остаток
  жизнеспособен;
- `FULL_BY_FRACTION` — объявленная доля забрала экспозицию целиком,
  округление ни при чём;
- `FULL` — «частично» на этом размере невыразимо, поэтому выход целиком:
  либо размер выхода ниже минимального торгового, либо ниже него остаток
  (дробь, которую нечем ни защитить, ни закрыть —
  `docs/rules/no-partial-close.md`);
- `SKIPPED` — размер ниже минимального, а остаток достаточен: действие не
  исполняется.

**Читает — per-type `StrategyActionExecutor` действия выхода** (поток:
`docs/processes/strategy-action-calculation.md`), в момент, когда решает,
отправлять ли рассчитанное действие: `SKIPPED` — действие не отправляется,
журнальный отчёт `PARTIAL_EXIT_BELOW_MIN_SIZE`; `FULL` — отправляется,
журнальный отчёт `PARTIAL_EXIT_ROUNDED_TO_FULL`; `PARTIAL` и
`FULL_BY_FRACTION` — отправляется без отчёта. Разведение двух полных
выходов существует ровно ради этого решения: по одному значению на оба
исхода читатель не отличил бы округление от объявленной доли.

Размер заявки читатель берёт из `sizeContracts` и сам его не пересчитывает:
на обоих полных исходах там уже стои́т экспозиция транша целиком.

`ExitOutcome` не подменяет `SizeMode`: режим — объявленное намерение
действия на входе расчёта, исход — что получилось после округления.

## Замечание

`sz` в OKX API для SWAP/FUTURES — это контракты, не USDT. Форма расчёта
контрактов через `ctVal`/`lotSz`/`minSz` — `docs/spec/order-sizing.json`,
смысл — `docs/components/SizeCalculator.md`.
Direct partial close позиции не рассчитывается; полное закрытие идёт
market-close'ом (ведёт `DealExitPendingHandler`) — и при выходе по
условию-переходу, и при явном действии шага `EXIT`; частичное уменьшение —
через reduce-only `Order`/`AlgoOrder` (см.
`docs/rules/no-partial-close.md`).
