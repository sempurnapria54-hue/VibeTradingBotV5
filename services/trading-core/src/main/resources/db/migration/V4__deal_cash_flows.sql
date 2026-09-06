-- Категорийная разбивка результата сделки (шаг 7 фазы 2, компонент
-- «исполнители площадки: добыча состояния»).
--
-- Таблица заводится ВМЕСТЕ со своим писателем — исполнителем добычи
-- движений средств. Прошлый заход написал её без него и снял: слой без
-- вызывающего читался бы следующим как построенный
-- (.claude/rules/codestyle.md §«Неиспользуемый код»).
--
-- Радиус строки — биржевой СЧЁТ, а не площадка: движения наблюдаются по
-- ключам счёта, и на нём же стои́т ось ключа идемпотентности
-- (docs/models/domain/other/DealCashFlow.md). Донорская колонка звалась
-- exchange_id — расхождение снято переименованием поля в общей
-- библиотеке прошлым заходом.
--
-- Ссылка на сделку NULLABLE намеренно: движение вне окна живой сделки
-- законно и остаётся неслинкованным (docs/spec/cash-flow-linkage.json).
--
-- Бэкфилла нет: до прод-рубежа таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

create table deal_cash_flows
(
    id                             bigserial primary key,
    deal_id                        bigint references deals (id),
    exchange_account_id            bigint         not null references exchange_accounts (id),
    category                       varchar(64)    not null,
    amount                         numeric(36, 18) not null,
    position_balance_change        numeric(36, 18),
    external_fee                   numeric(36, 18),
    ccy                            varchar(64)    not null,
    applied_rate                   numeric(36, 18),
    rate_status                    varchar(64)    not null,
    applied_rate_candle_instrument varchar(64),
    applied_rate_candle_timeframe  varchar(64),
    applied_rate_candle_open_time  timestamptz,
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
    constraint uk_deal_cash_flow_account_bill unique (exchange_account_id, external_bill_id)
);

comment on table deal_cash_flows is
    'Категорийная разбивка результата сделки: строка на одно движение счёта источника';

comment on column deal_cash_flows.deal_id is
    'Сделка-владелец; проставляется предикатом линковки при сохранении, пустая ссылка — движение вне окна живой сделки';

comment on column deal_cash_flows.exchange_account_id is
    'Биржевой счёт, по ключам которого движение наблюдено; ось ключа идемпотентности — идентификатор записи номенклатура одного счёта';

comment on column deal_cash_flows.external_fee is
    'Знаковая комиссионная компонента записи, сырая; пустота нулём не подменяется';

comment on column deal_cash_flows.position_balance_change is
    'Знаковое изменение маржи позиции; у isolated-финансирования несёт сумму расчёта при нулевом amount';

comment on column deal_cash_flows.rate_status is
    'Состояние курса пересчёта: пустота курса перегружена тремя смыслами, признак заведён значением';

-- Чтения по сделке: сверка разбивки на терминальном ребре и догон курса.
create index ix_deal_cash_flow_deal
    on deal_cash_flows (deal_id);
