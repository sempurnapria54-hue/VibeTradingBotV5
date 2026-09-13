-- Добор биржевых колонок аудита до ПОЛНОГО состава.
--
-- ПОЧЕМУ ДОБОР, А НЕ ПРАВКА V1. Применённая миграция не правится ни в
-- одном байте: Flyway держит контрольную сумму всего файла, и правка
-- уронила бы старт у того, кто миграций не трогал
-- (.claude/rules/codestyle.md §«Применённая миграция неизменяема»).
--
-- ПОЧЕМУ ВООБЩЕ. Набор колонок аудита БИНАРЕН: либо все шесть, либо ни
-- одной (docs/models/domain/other/Auditable.md §«Правило состава
-- колонок»). Схема сервиса несла четыре, то есть частичный набор, —
-- расхождение с правилом, не наблюдаемое ни одним прогоном, пока
-- базовый тип стоял здесь своей копией. С переездом типа в общий
-- артефакт отображение требует всех шести, и расхождение стало
-- предъявленным.
--
-- Бэкфилла нет и не требуется: до прод-рубежа таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md). Колонки необязательны по
-- существу: биржевого события у этих сущностей не бывает вовсе — они
-- заводятся нашей поверхностью, — и оба поля остаются пустыми. Это
-- названная цена бинарного правила, а не недосмотр.

alter table tenants
    add column external_created_at  timestamptz,
    add column external_modified_at timestamptz;

alter table memberships
    add column external_created_at  timestamptz,
    add column external_modified_at timestamptz;

alter table exchange_accounts
    add column external_created_at  timestamptz,
    add column external_modified_at timestamptz;

comment on column tenants.external_created_at is 'Момент создания в биржевом домене; у сущности реестра события источника нет — поле пусто (бинарный состав колонок аудита)';
comment on column memberships.external_created_at is 'Момент создания в биржевом домене; у сущности реестра события источника нет — поле пусто (бинарный состав колонок аудита)';
comment on column exchange_accounts.external_created_at is 'Момент создания в биржевом домене; реестровую часть счёта заводит наша поверхность — поле пусто (бинарный состав колонок аудита)';
