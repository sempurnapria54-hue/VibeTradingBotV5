-- Операнды окна линковки движений на сделке (заход по движениям счёта,
-- шаг 7; docs/models/domain/aggregate/Deal.md).
--
-- bills_window_begin — нижняя граница окна линковки; write-once, пишет
-- SubmitOrderExecutor по биржевому времени первой отправленной входной
-- заявки. NOT NULL не закрепляется намеренно: пустота — провенанс
-- суррогата (docs/spec/cash-flow-linkage.json §lowerBound), и пропуск
-- писателя обязан быть виден предикатом, а не замаскирован базой.
--
-- bills_fetched_through — до какого момента движения добыты; пишет
-- RefreshBillsExecutor монотонно вперёд. Пусто = не добывали, а не
-- «добыли, движений нет».
--
-- Бэкфилла нет: до конца фазы 1 таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

alter table deals
    add column bills_window_begin timestamptz,
    add column bills_fetched_through timestamptz;

comment on column deals.bills_window_begin is
    'Нижняя граница окна линковки движений; write-once, биржевое время первой отправленной входной заявки';

comment on column deals.bills_fetched_through is
    'До какого момента движения добыты; монотонно вперёд, пусто = добыча не выполнялась';
