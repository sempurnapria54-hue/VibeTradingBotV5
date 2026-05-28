# MarketStructureJob

## На какой вопрос отвечает этот файл

Кто считает структуру рынка (компонент-job): что делает, что не делает.

## Назначение

`MarketStructureJob` заранее готовит уровни рынка и сохраняет
`MarketStructure` / `MarketPriceLevel` (см.
`docs/models/domain/other/MarketStructure.md`). Настройки —
`StrategyMarketStructureSetting`; основной источник данных — закрытые
свечи; дополнительный (если нужен алгоритму) — `IndicatorValue`.

Данные нужны для входов от диапазона, grid, SL за структурный уровень,
breakout-условий и сопровождения позиции.

## Делает

- ищет swing high / swing low;
- считает range low / range high;
- определяет support / resistance;
- может использовать `IndicatorValue` как фильтр шума;
- сохраняет `MarketStructure` и `MarketPriceLevel`.

## Не делает

- не создаёт сделку;
- не ставит ордера;
- не переносит SL;
- не исполняет команды.

## Идемпотентность

Считает по закрытым свечам, уникальность `UNIQUE(instrument_id,
strategy_market_structure_setting_id, window_end_at)`. Если структура
сломалась — сохраняет новый результат (например, `type = UNKNOWN`), а не
правит старый.
