-- Строковые колонки структуры рынка — к норме строковых колонок
-- varchar(64) (docs/rules/persistence-representation.md §«Конвенции
-- колонок»).
--
-- ПОЧЕМУ ДОБОР, А НЕ ПРАВКА V1. Применённая миграция не правится ни в
-- одном байте (.claude/rules/codestyle.md §«Применённая миграция
-- неизменяема — включая комментарии»).
--
-- Все четыре колонки — строки значений доменных перечней, и их длина была
-- подобрана под сегодняшние значения: ровно та классификация по природе
-- значения, которую норма снимает. Расширение безопасно — строки не
-- переписываются, ключ и индексы на эти колонки не опираются.
--
-- Бэкфилла нет и не требуется: до прод-рубежа таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

alter table market_structures alter column "type" type varchar(64);

alter table market_structures alter column breakout_broken_level_type type varchar(64);

alter table market_structures alter column breakout_direction type varchar(64);

alter table market_price_levels alter column "type" type varchar(64);
