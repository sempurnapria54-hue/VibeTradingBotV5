# SizeCalculator

## На какой вопрос отвечает этот файл

Кто рассчитывает размер действия (компонент-калькулятор размера):
контракт, формула расчёта контрактов, инвариант partial exit.

## Назначение

`SizeCalculator` рассчитывает размер order/algo action и
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

### Вход (open/increase)

Желаемый notional берётся от свободного депозита:
`desiredNotional = externalAvailableEquity × (allocationPercents / 100)`,
затем `contracts = desiredNotional / (entryPrice × ctVal)`. Если у входа
есть стоп — `contracts` кэпится лимитом риска на сделку (см. ниже).
Итог округляется вниз по `lotSz` и снизу ограничен `minSz`. База
аллокации — `externalAvailableEquity` (якорь процента — открытый вопрос
STRAT-Q4).

### Reduce-only / algo

Размер закрытия = `Position.externalSize × fraction` (fraction зажат в
`[0..1]`), округлён вниз по `lotSz`, снизу ограничен `minSz`. Для
не-частичных algo (`STOP_LOSS`/`TAKE_PROFIT`/`OCO_FULL`/trailing)
fraction = 1 (`externalSize` целиком); для частичных (`PARTIAL_*`) —
`closeFractionPercents`. Полного закрытия позиции как **действия** нет —
market-close ведёт `ExitPendingHandler` командой `CLOSE_POSITION_COMMAND`, вне
`SizeCalculator`.

## Сайзинг под лимит риска на сделку

Для risk-creating входа со стопом размер **ограничен лимитом риска на
сделку**: убыток на стопе не должен превышать
`StrategyDetail.riskPerTradePercent × BalanceContainer.externalAvailableEquity`
(`docs/decisions/per-trade-risk-policy.md`). Убыток на стопе для линейного
контракта — `|entryPrice − stopPrice| × contracts × ctVal + commissions`
(прогнозная комиссия вход+выход по **taker**-ставке — worst-case; **включена с
шага 7** — G6, `docs/decisions/per-trade-risk-policy.md` §«Учёт комиссий»).

**Считается закрытой формой, не подбором** (H10, `GAPS_CLOSE_7`) — комиссия
пропорциональна размеру, поэтому размер решается из неравенства, а не
итерируется:

```text
contracts ≤ budget / ( ctVal × ( |entryPrice − stopPrice|
                               + rate × (entryPrice + stopPrice) ) )
```

Каждая нога комиссии — **по своей цене** (вход по `entryPrice`, выход по
`stopPrice`): оценка обеих по цене входа занижала бы комиссию выхода для
SHORT и давала бы шортам систематически больший размер. Формула одна на оба
направления. Полный довод и отвергнутые альтернативы —
`docs/decisions/per-trade-risk-policy.md` §«Закрытая форма сайзинга».
**Ставка** читается через `context.instrumentExternalRules.takerFeeRate()` —
без отдельного поля контекста и без exchange-вызова из калькулятора (N9,
`docs/decisions/pnl-finalization-mechanics.md` реш.4). **Дом ставки** — не
навес: ставка живёт в `TradeFeeRate` (одна строка на комиссионную группу,
`docs/models/domain/other/TradeFeeRate.md`), на навесе инструмента — только
**ключ группы** `externalFeeGroupId`, а аксессор гидрирует хранилищный слой —
`docs/components/InstrumentExternalRulesDataService.md` §«Гидрация ставки
комиссии» (H1, `GAPS_CLOSE_4`). Для калькулятора seam тот же — поверхность
чтения не изменилась. Аксессор отдаёт **издержку** (знак биржевой конвенции
снят при маппинге, `docs/models/domain/other/TradeFeeRate.md` §«Знак ставки»),
поэтому `+ commissions` верно как написано и `abs` не нужен: ребейт уезжает
отрицательной издержкой и корректно уменьшает убыток на стопе. Ставка не
резолвится → вход блокирует `RiskValidator`
(`FEE_RATE_UNAVAILABLE`), калькулятор ставку не выдумывает. Лимит риска —
связывающий потолок над желаемым объёмом (`allocationPercents` — желаемая доля,
лимит риска — связывающий cap).

- размер округляется по `lotSz` и **снизу ограничен** `minSz`;
- если даже на `minSz` убыток на стопе превышает лимит — `SizeCalculator`
  возвращает размер на `minSz`-полу, а `RiskValidator` блокирует вход
  (`RISK_PER_TRADE_EXCEEDED` → `RISK_CONTROL`, строгое неоткрытие, без аварии);
- запас на проскок за стоп в фазе 1 не закладывается (убыток по цене стопа).

Верхний биржевой предел плеча/размера остаётся за `RiskValidator`
(`EXCHANGE_MAX_LEVERAGE_EXCEEDED`, `SIZE_ABOVE_LIMIT`); отдельного нашего кэпа
плеча нет (`docs/decisions/per-trade-risk-policy.md`).

## Инвариант partial exit

`SizeCalculator` **не** рассчитывает direct partial close позиции
(полное закрытие идёт market-close'ом, ведёт его `ExitPendingHandler` —
независимо от того, объявлен выход условием-переходом или явным действием
шага `EXIT`, `docs/rules/no-partial-close.md`). Частичное уменьшение —
только через `StrategyOrderAction` reduce-only или `StrategyAlgoOrderAction`
partial TP / reduce-only; размер закрывающего `Order`/`AlgoOrder`
считается так, чтобы action не увеличивал позицию (`closeFraction` 0..1,
см. `docs/rules/no-partial-close.md`).

Если размер нельзя безопасно посчитать (нет `ctVal`/`lotSz`/`minSz`, нельзя
привести к минимальной торговой единице) — сигнализируется контролируемая
ошибка расчёта (бросок `CalculationException` → `CalculationError` в
`ERROR`-результате `StrategyActionCalculator`, см.
`docs/components/models/CalculationError.md` §«Механизм сигнализации»).
