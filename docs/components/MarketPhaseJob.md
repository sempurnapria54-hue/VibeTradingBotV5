# MarketPhaseJob

## На какой вопрос отвечает этот файл

Кто определяет фазу рынка (компонент-job): что делает, что не делает.

## Назначение

`MarketPhaseJob` определяет фазу рынка и сохраняет `MarketPhase` (см.
`docs/models/domain/other/MarketPhase.md`). Настройка —
`StrategyMarketPhaseSetting`; источники данных — готовые `IndicatorValue`
и `MarketStructure`; параметры — `MarketPhaseParams`.

## Делает

- читает `StrategyMarketPhaseSetting` стратегий **всех статусов кроме
  `DELETED`** (перечень — как в правиле свежести,
  `docs/rules/market-data-freshness.md`);
- читает готовые `IndicatorValue` и `MarketStructure`;
- применяет `MarketPhaseParams` (`algorithmType`: `STRUCTURE_ONLY` /
  `INDICATORS_ONLY` / `STRUCTURE_AND_INDICATORS`);
- сохраняет актуальный `MarketPhase`;
- может сохранять confidence/score.

## Не делает

- не создаёт `Deal`;
- не выставляет ордера;
- не сопровождает сделку.

## Идемпотентность

Уникальность `UNIQUE(instrument_id, strategy_market_phase_setting_id,
candle_timestamp)` (`MarketPhase` ключуется контейнером-настройкой, не
реестром конфигураций — см.
`docs/decisions/market-data-result-identity-keying.md`). При смене фазы
сохраняет новый актуальный результат (например, `type = UNKNOWN`) поверх
истории, а не правит старый.

**Checkpoint — производный, отдельного состояния нет.** «Докуда
посчитано» = `max(candle_timestamp)` по таблице результатов для
(`instrument_id` + `strategy_market_phase_setting_id`); «докуда считать»
= время закрытия последней закрытой свечи в группе. Отдельная persisted
checkpoint-модель не заводится.
