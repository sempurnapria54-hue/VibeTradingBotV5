-- Строки категорийной разбивки результата сделки (заход по движениям
-- счёта, шаг 7).
--
-- Строка на ОДНО движение источника (docs/models/domain/other/DealCashFlow.md).
-- deal_id nullable: ссылку проставляет предикат линковки при сохранении,
-- долгоживущая пустая ссылка — форвард-слот под движения вне периода
-- жизни сделок.
--
-- Ключ идемпотентности — (exchange_id, external_bill_id): идентификатор
-- записи — номенклатура одной площадки, ось биржи в ключе обязательна;
-- носитель ключа — схема (docs/rules/idempotency-via-unique.md), обе
-- колонки обязательны, частичного индекса ключ не требует.
--
-- Обязательность времени события (external_created_at) обеспечивается на
-- границе разбора, а не колонкой: колонка — унаследованное поле аудита.
--
-- Бэкфилла нет: до конца фазы 1 таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

create table deal_cash_flows
(
    id                             bigserial primary key,
    deal_id                        bigint references deals (id),
    category                       varchar(64)    not null,
    amount                         numeric(36,18) not null,
    external_fee                   numeric(36,18),
    ccy                            varchar(64)    not null,
    applied_rate                   numeric(36,18),
    rate_status                    varchar(64)    not null,
    applied_rate_candle_instrument varchar(64),
    applied_rate_candle_timeframe  varchar(64),
    applied_rate_candle_open_time  timestamptz,
    exchange_id                    bigint         not null references exchanges (id),
    external_instrument_id         varchar(64),
    external_bill_id               varchar(64)    not null,
    external_type                  varchar(64)    not null,
    external_sub_type              varchar(64),
    external_order_id              varchar(64),
    created_at                     timestamptz,
    created_by                     varchar(64),
    modified_at                    timestamptz,
    modified_by                    varchar(64),
    external_created_at            timestamptz,
    external_modified_at           timestamptz,
    constraint uk_deal_cash_flow_exchange_bill unique (exchange_id, external_bill_id)
);

comment on table deal_cash_flows is
    'Категорийная разбивка результата сделки: строка на одно движение счёта источника';

comment on column deal_cash_flows.deal_id is
    'Сделка-владелец; проставляется предикатом линковки при сохранении, пустая ссылка — движение вне окна живой сделки';

comment on column deal_cash_flows.external_fee is
    'Знаковая комиссионная компонента записи, сырая; пустота нулём не подменяется';

comment on column deal_cash_flows.rate_status is
    'Состояние курса пересчёта: пустота курса перегружена тремя смыслами, признак заведён значением';

-- Чтения по сделке: сверка разбивки на терминальном ребре и догон курса.
create index ix_deal_cash_flow_deal
    on deal_cash_flows (deal_id);
