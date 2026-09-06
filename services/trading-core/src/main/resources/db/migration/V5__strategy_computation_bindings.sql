-- Привязка объявления стратегии к идентичности вычисления у market-data.
--
-- Всякое чтение фич у владельца данных адресуется ИДЕНТИЧНОСТЬЮ вычисления
-- (тип, таймфрейм, канонические параметры), а соответствие «авторское имя
-- операнда → идентичность» держит потребитель
-- (docs/architecture/market-data-collection.md §«Фаза: клаузы приезжают
-- операндом вызова»). Без него ядро не составляет ни одного вызова к
-- market-data: ни фазы, ни значений индикаторов, ни структуры.
--
-- Колонка NULLABLE намеренно: идентичность появляется не с копией, а с
-- ОБЪЯВЛЕНИЕМ потребности у владельца, и между этими моментами лежит вызов
-- к соседу, который может быть недоступен. Пустое значение читается как
-- «потребность ещё не объявлена» — значения по такому объявлению не
-- читаются вовсе (docs/rules/absent-value-semantics.md).

alter table strategy_indicator_settings
    add column computation_config_internal_id varchar(128);

comment on column strategy_indicator_settings.computation_config_internal_id is
    'Идентичность вычисления у market-data; пишет тик объявления потребности, не автор стратегии';

alter table strategy_market_structure_settings
    add column computation_config_internal_id varchar(128);

comment on column strategy_market_structure_settings.computation_config_internal_id is
    'Идентичность вычисления у market-data; пишет тик объявления потребности, не автор стратегии';
