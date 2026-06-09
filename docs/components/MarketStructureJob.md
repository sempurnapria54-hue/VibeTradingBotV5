# MarketStructureJob

## На какой вопрос отвечает этот файл

Кто считает структуру рынка (компонент-job): что делает, что не делает.

## Назначение

`MarketStructureJob` заранее готовит уровни рынка и сохраняет
`MarketStructure` / `MarketPriceLevel` (см.
`docs/models/domain/other/MarketStructure.md`). Настройки —
`StrategyMarketStructureSetting`; основной источник данных — закрытые
свечи; дополнительный — готовые `IndicatorValue` (ER / ATR как шумовой
фильтр). Вычисление структуры делегирует
`docs/components/MarketStructureResolver.md` — job **тонкий**.

Данные нужны для входов от диапазона, grid, SL за структурный уровень,
breakout-условий и сопровождения позиции.

## Делает

- читает стратегии **всех статусов кроме `DELETED`** и их
  `StrategyMarketStructureSetting` (перечень — как в правиле свежести,
  `docs/rules/market-data-freshness.md`);
- читает закрытые свечи окна и готовые `IndicatorValue` (ER / ATR),
  объявленные стратегией;
- зовёт `MarketStructureResolver.resolve(...)` — тот выводит `type`,
  `levels`, `breakoutEvent`, `confirmedAt`, окно (семантика —
  `MarketStructure.md` §Семантика классификации; уровни/пробой/шумовой
  фильтр по свечам сам не ищет);
- сохраняет `MarketStructure` и `MarketPriceLevel`.

Job — тонкий: классификацию структуры держит `MarketStructureResolver`,
готовые индикаторы считает `IndicatorJob`.

## Не делает

- не создаёт сделку;
- не ставит ордера;
- не переносит SL;
- не исполняет команды.

## Идемпотентность

Считает по закрытым свечам, уникальность `UNIQUE(instrument_id,
config_id, window_end_at)` (ключ по идентичности считаемого через реестр
конфигураций — см.
`docs/decisions/market-data-result-identity-keying.md`). Если структура
сломалась — сохраняет новый результат (например, `type = UNKNOWN`), а не
правит старый.

**Checkpoint — производный, отдельного состояния нет.** «Докуда
посчитано» = `max(window_end_at)` по таблице результатов для
(`instrument_id` + `config_id`); «докуда считать» = время закрытия
последней закрытой свечи в группе. Отдельная persisted checkpoint-модель
не заводится.
