-- Идентичности контекста на копии определения стратегии.
--
-- Определение живёт у владельца (сервис `strategies`) и адресует свой
-- контекст ИДЕНТИЧНОСТЯМИ: числовые ключи баз границу сервиса не
-- пересекают (docs/architecture/data-ownership.md §Идентификаторы). Копия
-- хранит присланное как есть — это часть снимка, — а числовые FK остаются
-- локальной связью внутри базы ядра и резолвятся из этих идентичностей на
-- границе domain → persistence (docs/models/mapping/Strategy.md).
--
-- Колонки вводятся NOT NULL одним ходом: до прод-рубежа таблицы пусты,
-- бэкфилла не требуется (.claude/rules/pre-launch-schema-changes.md).
--
-- Тенанта копия НЕ хранит: у ядра есть локальная строка счёта, и тенант
-- резолвится по ней, как у сделки (docs/architecture/tenant-and-exchange.md
-- §«Торговая строка называет счёт, и радиусы читаются от него»).

alter table strategies
    add column exchange_account_internal_id varchar(64) not null;

alter table strategies
    add column instrument_internal_id varchar(64) not null;

comment on column strategies.exchange_account_internal_id is
    'Идентичность биржевого счёта из присланного снимка определения';

comment on column strategies.instrument_internal_id is
    'Идентичность инструмента из присланного снимка определения';
