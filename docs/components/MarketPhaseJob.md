# MarketPhaseJob

## На какой вопрос отвечает этот файл

Кто определяет фазу рынка (компонент-job): что делает, что не делает.

## Назначение

`MarketPhaseJob` определяет фазу рынка и сохраняет `MarketPhase` (см.
`docs/models/domain/other/MarketPhase.md`). Настройка —
`StrategyMarketPhaseSetting`; источники данных — готовые `IndicatorValue`
и `MarketStructure`; правила классификации — авторские `phaseRules` (см.
`docs/decisions/market-phase-conditional-classification.md`).

## Делает

- читает `StrategyMarketPhaseSetting` стратегий **всех статусов кроме
  `DELETED`** (перечень — как в правиле свежести,
  `docs/rules/market-data-freshness.md`);
- читает готовые `IndicatorValue` и `MarketStructure`, собирает контекст
  оценки (без `Position`-фактов — контекстный whitelist это гарантирует);
- зовёт `docs/components/MarketPhaseClassifier.md` — stateless first-match
  по `phaseRules` поверх `StrategyConditionEvaluator` (первая истинная
  клауза задаёт `Type`, ни одна → `UNKNOWN`);
- сохраняет актуальный `MarketPhase`.

Job — тонкий: классификацию держит `MarketPhaseClassifier`, истинность
условий — `StrategyConditionEvaluator`. Скоринга (`trendScore`/
`rangeScore`/`confidenceScore`) и `algorithmType` нет — заменены
авторскими условиями.

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
