-- Расчётная валюта инструмента (заход по движениям счёта, шаг 7;
-- docs/models/domain/core/Instrument.md).
--
-- Авторитет валюты риска и валюты результата сделки; операнд ветки чужой
-- валюты на записи движения и ступени 0 лестницы огрубления курса
-- (docs/components/RefreshBillsExecutor.md). Пишет тропа синка
-- спецификации из снапшота источника (OKX settleCcy).
--
-- Nullable намеренно: пусто = валюта не резолвилась, и это отказ ветки,
-- а не ноль — прочтение пустоты закрывает курс статусом
-- SETTLE_CURRENCY_UNAVAILABLE, не пропуском строки.
--
-- Бэкфилла нет: до конца фазы 1 таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md); на существующих строках
-- значение появляется ближайшим синком спецификации.

alter table instruments
    add column external_settlement_currency varchar(64);

comment on column instruments.external_settlement_currency is
    'Расчётная валюта инструмента (OKX settleCcy); пусто = не резолвилась — отказ, а не ноль';
