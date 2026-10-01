-- Режим счёта и режим позиций площадки на момент снимка средств
-- (docs/models/domain/core/BalanceContainer.md §Персистентность) — посылки
-- контура «только свои средства» и нетто-позиций
-- (docs/rules/trading-constraints.md).
--
-- Значение — имя доменного перечня. Пусто значаще — снимок режима не нёс либо
-- площадка назвала значение вне словаря; преконтроль читает пустоту выходом
-- из контура, а не умолчанием (docs/spec/risk-limits.json,
-- accountModeOutOfContour).
--
-- Проект до прод-рубежа: таблица пуста, бэкфилла не требуется
-- (.claude/rules/pre-launch-schema-changes.md).

alter table balance_containers add column account_mode varchar(64);

alter table balance_containers add column position_mode varchar(64);

comment on column balance_containers.account_mode is
    'Режим счёта площадки на момент снимка; пусто — снимок режима не нёс';

comment on column balance_containers.position_mode is
    'Режим позиций счёта на момент снимка; пусто — снимок режима не нёс';
