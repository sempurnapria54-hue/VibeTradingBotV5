-- Четвёрка риск-чисел детали стратегии (заход по читателю чисел риска, шаг 7).
--
-- Прежняя колонка risk_per_trade_percent несла ПОАКТНЫЙ потолок, а имя
-- обещало потолок сделки: потолков на сделку в правиле три, и ни один из
-- них этой колонкой не считался (docs/rules/risk-policy.md §«Четыре
-- потолка на разные вопросы»). Переименование, а не новая колонка:
-- величина та же, менялось только её имя.
--
-- Три новых числа объявляет автор стратегии; умолчаний у них нет —
-- у торгуемой детали они обязательны, и незаданное отвергает создание
-- (docs/spec/strategy-reference.json, hasRequiredRiskFields).
--
-- Бэкфилла нет: до конца фазы 1 таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

alter table strategy_details
    rename column risk_per_trade_percent to risk_per_action_percent;

alter table strategy_details
    add column cumulative_risk_per_deal_multiplier          numeric(36, 18),
    add column strategy_simultaneous_risk_per_deal_percent  numeric(36, 18),
    add column strategy_catastrophic_risk_per_deal_multiplier numeric(36, 18);

comment on column strategy_details.risk_per_action_percent is
    'Поактный потолок риска: сколько берёт ОДНО действие, % базы риска';

comment on column strategy_details.strategy_simultaneous_risk_per_deal_percent is
    'Максимум одновременного риска сделки; вкладывается в конфигурационный максимум';

comment on column strategy_details.strategy_catastrophic_risk_per_deal_multiplier is
    'Множитель катастрофического потолка; сверяется с конфигурационным пределом на создании';
