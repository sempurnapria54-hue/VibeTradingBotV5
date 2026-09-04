-- Трек D, этап A: фаза рынка перестаёт персистироваться — вычисляется на
-- лету при потреблении (MarketPhaseResolver/MarketPhaseService), своих
-- часов и срока свежести у неё нет. Снимаются: таблица market_phases
-- (+ её индекс/FK) и контейнерные колонки timeframe / expiration_duration
-- настройки фазы. StrategyMarketPhaseRule.level убран (порядок = позиция в
-- списке) — поле жило только в JSONB phase_rules, схемной правки не требует.
-- Персистентных стратегий ещё нет — изменение схемы безопасно.
-- См. docs/decisions/market-phase-stateless.md.

drop table market_phases;

alter table strategy_market_phase_settings drop column timeframe;
alter table strategy_market_phase_settings drop column expiration_duration;
