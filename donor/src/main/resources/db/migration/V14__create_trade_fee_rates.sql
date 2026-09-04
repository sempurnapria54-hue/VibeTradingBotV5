-- Ставки комиссий комиссионных групп счёта (заход по ставке комиссии, шаг 7).
--
-- Строка на ГРУППУ, не на инструмент: ставка есть атрибут комиссионного
-- уровня счёта, а справочник инструмента несёт лишь ключ своей группы
-- (docs/models/domain/other/TradeFeeRate.md). Ось группы — ПАРА СЫРЫХ
-- значений источника: доменная проекция схлопывает нераспознанные типы в
-- UNKNOWN, и две разные группы столкнулись бы в одном ключе.
--
-- Ставки — varchar по названному исключению численной конвенции
-- (docs/rules/persistence-representation.md): значение хранится в форме
-- источника, и эта форма — часть факта.
--
-- Уникального ограничения на тройку НЕТ намеренно: правило истории —
-- «значение изменилось → новая строка», то есть строк на группу со
-- временем несколько, а актуальна последняя. Ограничение из дока
-- («уникальность среди актуальных строк») требует признака актуальности,
-- которого у модели нет; вместо него — индекс резолва и порядок по id.
--
-- Бэкфилла нет: до конца фазы 1 таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

create table trade_fee_rates
(
    id                      bigserial primary key,
    exchange_id             bigint       not null references exchanges (id),
    external_instrument_type varchar(64) not null,
    external_fee_group_id   varchar(64)  not null,
    instrument_type         varchar(64)  not null,
    external_taker_fee_rate varchar(64)  not null,
    external_maker_fee_rate varchar(64)  not null,
    external_fee_level      varchar(64),
    refresh_count           bigint       not null default 1,
    created_at              timestamptz,
    created_by              varchar(64),
    modified_at             timestamptz,
    modified_by             varchar(64),
    external_created_at     timestamptz,
    external_modified_at    timestamptz
);

comment on table trade_fee_rates is
    'Ставки комиссий комиссионных групп счёта: строка на группу, история — новой строкой';

comment on column trade_fee_rates.external_taker_fee_rate is
    'Ставка taker как ИЗДЕРЖКА: знак биржевой конвенции снят при маппинге';

comment on column trade_fee_rates.refresh_count is
    'Счётчик подтверждений строки: движение времени изменения записано, а не побочно';

-- Резолв ставки: по паре сырых значений внутри биржи, актуальная — последняя.
create index ix_trade_fee_rate_group
    on trade_fee_rates (exchange_id, external_instrument_type, external_fee_group_id, id desc);
