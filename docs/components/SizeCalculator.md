# SizeCalculator

## На какой вопрос отвечает этот файл

Кто рассчитывает размер действия (компонент-калькулятор размера):
контракт, формула расчёта контрактов, инвариант partial exit.

## Назначение

`SizeCalculator` рассчитывает размер order/algo/position action и
возвращает `CalculatedSize` (см.
`docs/components/models/CalculatedSize.md`). Цену не считает, но может
использовать `CalculatedPrice` как входной параметр.

## Контракт

`CalculatedSize calculate(CalculationContext context, CalculatedPrice
calculatedPrice)`.

## Формула размера (линейный контракт SWAP/FUTURES)

`sz` в OKX API — это контракты, не USDT:

```text
baseQty   = usdtNotional / price
contracts = baseQty / ctVal
contractsRounded = roundByLotSize(contracts)
```

где `ctVal` = `InstrumentExternalRules.externalContractValue`, `lotSz` =
`externalLotSize`, `minSz` = `externalMinSize` (см.
`docs/models/domain/other/InstrumentExternalRules.md`).

## Инвариант partial exit

`SizeCalculator` **не** рассчитывает direct partial close для
`StrategyPositionAction` (только `CLOSE_FULL`). Частичное уменьшение —
только через `StrategyOrderAction` reduce-only или `StrategyAlgoOrderAction`
partial TP / reduce-only; размер закрывающего `Order`/`AlgoOrder`
считается так, чтобы action не увеличивал позицию (`closeFraction` 0..1,
см. `docs/rules/no-partial-close.md`).

Если размер нельзя безопасно посчитать (нет `lotSz`/`minSz`, нельзя
привести к минимальной торговой единице) — возвращается controlled
`CalculationError`.
