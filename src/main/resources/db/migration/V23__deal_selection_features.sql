-- Признаки отбора сделки для отчёта (шаг 7 фазы 1).
--
-- Четыре колонки, все пусты до терминала; пусто у них означает «признак
-- неприменим» — тропа закрытия без входа. Пишет их финализация выхода той
-- же транзакцией, что и число, а на аварийной тропе — терминальное ребро,
-- и только если число не финализировано
-- (docs/spec/deal-lifecycle.json §benchmarkAvailabilityOnTerminal).
-- Схема — docs/models/domain/aggregate/Deal.md §Персистентность.
--
-- Проект до запуска: таблицы пусты, бэкфилл не требуется
-- (.claude/rules/pre-launch-schema-changes.md).

alter table deals add column close_outcome varchar(64);
alter table deals add column reconciliation_status varchar(64);
alter table deals add column breakdown_incomplete varchar(64);
alter table deals add column risk_benchmark_availability varchar(64);

-- Выборка отчёта по терминалу и торговому исходу. Её префикс обслуживает и
-- горячую выборку активных сделок, поэтому отдельный индекс по статусу
-- снимается (docs/models/domain/aggregate/Deal.md §«Ключи и индексы»).
create index ix_deal_status_close_outcome on deals (status, close_outcome);
drop index ix_deal_status;
