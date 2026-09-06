-- Базовая схема сервиса `strategies` — владельца определений стратегий
-- (docs/architecture/data-ownership.md §Раскладка).
--
-- ФОРМА ДЕРЕВА ОБЩАЯ С КОПИЕЙ ЯДРА, и различия названы там же, где форма
-- (docs/models/domain/aggregate/Strategy.md §Персистентность):
--   * только у владельца — tenant_internal_id: строки счёта у сервиса нет
--     вовсе, и тенанта по ней не резолвить
--     (docs/architecture/tenant-and-exchange.md §«Торговая строка
--     называет счёт, и радиусы читаются от него»);
--   * только у копии — числовые FK на проекции счёта и инструмента плюс
--     идентичность вычисления у объявлений каталога: и то и другое —
--     локальные связи ядра, у владельца им не на что ссылаться и некому
--     их писать (docs/rules/writer-named-for-every-value.md).
--
-- Контекст определения назван ИДЕНТИЧНОСТЯМИ: числовые ключи баз границу
-- сервиса не пересекают (docs/architecture/data-ownership.md
-- §Идентификаторы). Ссылочной целостности к чужим реестрам схема поэтому
-- не держит — её держит проверка создания и активации, читающая соседа
-- (docs/rules/strategy-validation.md).
--
-- Проект до прод-рубежа: таблицы пусты, бэкфилла не требуется,
-- обязательные колонки вводятся напрямую
-- (.claude/rules/pre-launch-schema-changes.md).

-- ---------------------------------------------------------------------
-- 1. Дерево определения
-- ---------------------------------------------------------------------

create table strategies (
    id                          bigserial primary key,
    internal_id                 varchar(64)  not null,
    tenant_internal_id          varchar(64)  not null,
    exchange_account_internal_id varchar(64) not null,
    instrument_internal_id      varchar(64)  not null,
    name                        varchar(128) not null,
    status                      varchar(32)  not null,
    created_at                  timestamptz,
    created_by                  varchar(64),
    modified_at                 timestamptz,
    modified_by                 varchar(64),
    external_created_at         timestamptz,
    external_modified_at        timestamptz,
    constraint uk_strategy_internal_id unique (internal_id)
);

comment on column strategies.tenant_internal_id is
    'Тенант-владелец; пишется приёмником создания из контекста вызова, не из тела';

comment on column strategies.created_by is
    'Автор черновика: имя принципала (пришёл поверхностью) либо класс собственного прохода (породил модуль сервиса)';

-- Инвариант жизненного цикла на радиусе пары: одна активная стратегия на
-- пару «счёт, инструмент». Радиус пары, а не инструмента: инструмент
-- принадлежит площадке, и у двух счетов одной площадки он один.
create unique index uk_strategy_active_per_account_instrument
    on strategies (exchange_account_internal_id, instrument_internal_id)
    where status = 'ACTIVE';

-- Выборка определений тенанта — поверхность чтения работает в его контексте.
create index ix_strategy_tenant on strategies (tenant_internal_id);

create table strategy_market_phase_settings (
    id                   bigserial primary key,
    strategy_id          bigint not null,
    phase_rules          jsonb  not null,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint uk_strategy_market_phase_setting_strategy unique (strategy_id),
    constraint fk_strategy_market_phase_setting_strategy foreign key (strategy_id) references strategies (id)
);

comment on column strategy_market_phase_settings.phase_rules is
    'Клаузы классификации фазы: упорядоченный список, порядок = позиция в списке';

create table strategy_indicator_settings (
    id                  bigserial primary key,
    strategy_id         bigint      not null,
    key                 varchar(64) not null,
    indicator_type      varchar(32) not null,
    params              jsonb       not null,
    destiny             varchar(32) not null,
    expiration_duration varchar(32) not null,
    constraint uk_strategy_indicator_setting_key unique (strategy_id, key),
    constraint fk_strategy_indicator_setting_strategy foreign key (strategy_id) references strategies (id)
);

create table strategy_market_structure_settings (
    id                   bigserial primary key,
    strategy_id          bigint      not null,
    key                  varchar(64) not null,
    timeframe            varchar(32) not null,
    efficiency_ratio_key varchar(64),
    atr_key              varchar(64),
    params               jsonb       not null,
    destiny              varchar(32) not null,
    expiration_duration  varchar(32) not null,
    constraint uk_strategy_market_structure_setting_key unique (strategy_id, key),
    constraint fk_strategy_market_structure_setting_strategy foreign key (strategy_id) references strategies (id)
);

create table strategy_details (
    id                                            bigserial primary key,
    strategy_id                                   bigint      not null,
    market_phase_type                             varchar(32) not null,
    phase_entry_policy                            varchar(32) not null,
    risk_per_action_percent                       numeric(36, 18),
    cumulative_risk_per_deal_multiplier           numeric(36, 18),
    strategy_simultaneous_risk_per_deal_percent   numeric(36, 18),
    strategy_catastrophic_risk_per_deal_multiplier numeric(36, 18),
    target_risk_reward_ratio                      numeric(36, 18),
    created_at                                    timestamptz,
    created_by                                    varchar(64),
    modified_at                                   timestamptz,
    modified_by                                   varchar(64),
    external_created_at                           timestamptz,
    external_modified_at                          timestamptz,
    constraint uk_strategy_detail_strategy_phase unique (strategy_id, market_phase_type),
    constraint fk_strategy_detail_strategy foreign key (strategy_id) references strategies (id)
);

comment on column strategy_details.risk_per_action_percent is
    'Поактный потолок риска: сколько берёт ОДНО действие, % базы риска';

-- Риск-поля nullable: у неторгуемой детали риска нет вовсе.

create table strategy_tranches (
    id                      bigserial primary key,
    strategy_detail_id      bigint      not null,
    key                     varchar(64) not null,
    level_count             integer     not null,
    level_step              numeric(36, 18),
    position_reopen_allowed boolean,
    created_at              timestamptz,
    created_by              varchar(64),
    modified_at             timestamptz,
    modified_by             varchar(64),
    external_created_at     timestamptz,
    external_modified_at    timestamptz,
    constraint uk_strategy_tranche_detail_key unique (strategy_detail_id, key),
    constraint fk_strategy_tranche_detail foreign key (strategy_detail_id) references strategy_details (id),
    constraint ck_strategy_tranche_level_count check (level_count >= 1),
    -- Шаг объявляется тогда и только тогда, когда уровней больше одного.
    constraint ck_strategy_tranche_level_step check ((level_count > 1) = (level_step is not null))
);

-- level_count NOT NULL и без умолчания: пустое место мажорировалось бы
-- единицей, то есть В РАЗРЕШАЮЩУЮ сторону неравенства
-- N_overlap × riskPerActionPercent ≤ strategySimultaneousRiskPerDealPercent
-- (docs/models/domain/aggregate/Strategy.md §Персистентность).

create table strategy_steps (
    id                          bigserial primary key,
    strategy_tranche_id         bigint,
    tranche_status              varchar(32),
    strategy_detail_id          bigint,
    deal_status                 varchar(32),
    step_index                  integer     not null,
    step_type                   varchar(32) not null,
    condition                   jsonb       not null,
    market_data_expired_setting jsonb       not null,
    created_at                  timestamptz,
    created_by                  varchar(64),
    modified_at                 timestamptz,
    modified_by                 varchar(64),
    external_created_at         timestamptz,
    external_modified_at        timestamptz,
    constraint fk_strategy_step_tranche foreign key (strategy_tranche_id) references strategy_tranches (id),
    constraint fk_strategy_step_detail foreign key (strategy_detail_id) references strategy_details (id),
    -- Ровно один родитель и ровно один ключ группировки — вместе, одним
    -- ограничением: строка с обоими родителями читалась бы двумя уровнями
    -- сразу.
    constraint ck_strategy_step_single_owner check (
        (strategy_tranche_id is not null and tranche_status is not null
             and strategy_detail_id is null and deal_status is null)
        or (strategy_detail_id is not null and deal_status is not null
             and strategy_tranche_id is null and tranche_status is null)
    )
);

create unique index uk_strategy_step_tranche_status_index
    on strategy_steps (strategy_tranche_id, tranche_status, step_index)
    where strategy_tranche_id is not null;

create unique index uk_strategy_step_detail_deal_status_index
    on strategy_steps (strategy_detail_id, deal_status, step_index)
    where strategy_detail_id is not null;

create table strategy_actions (
    id                   bigserial primary key,
    strategy_step_id     bigint      not null,
    strategy_detail_id   bigint      not null,
    action_kind          varchar(32) not null,
    key                  varchar(64) not null,
    action_type          varchar(32) not null,
    target_action_key    varchar(64),
    target_action_id     bigint,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint uk_strategy_action_detail_key unique (strategy_detail_id, key),
    constraint fk_strategy_action_step foreign key (strategy_step_id) references strategy_steps (id),
    constraint fk_strategy_action_detail foreign key (strategy_detail_id) references strategy_details (id),
    -- Self-FK резолвится при сохранении дерева; deferrable — порядок
    -- вставки строк действий внутри транзакции не ограничивает.
    constraint fk_strategy_action_target foreign key (target_action_id) references strategy_actions (id)
        deferrable initially deferred,
    constraint ck_strategy_action_no_self_target check (target_action_id <> id)
);

create table strategy_order_actions (
    id                     bigint primary key,
    order_type             varchar(32) not null,
    direction              varchar(16) not null,
    allocation_percents    numeric(36, 18),
    position_reducing_only boolean,
    placement              jsonb,
    attached_protection    jsonb,
    constraint fk_strategy_order_action_base foreign key (id) references strategy_actions (id)
);

create table strategy_algo_order_actions (
    id                      bigint primary key,
    condition_type          varchar(32) not null,
    stop_loss_settings      jsonb,
    trailing_settings       jsonb,
    close_fraction_percents numeric(36, 18),
    trigger_profit_percents numeric(36, 18),
    trigger_price_type      varchar(16),
    constraint fk_strategy_algo_order_action_base foreign key (id) references strategy_actions (id)
);

-- Вырожденная подтаблица вида POSITION: собственных полей у него нет.
create table strategy_position_actions (
    id bigint primary key,
    constraint fk_strategy_position_action_base foreign key (id) references strategy_actions (id)
);

-- ---------------------------------------------------------------------
-- 2. Outbox
-- ---------------------------------------------------------------------
--
-- Переход статуса и его событие пишутся ОДНОЙ транзакцией; публикует
-- отдельное реле и помечает опубликованное
-- (docs/architecture/data-ownership.md §«Outbox и доставка»,
-- docs/components/OutboxRelayJob.md — дом формы реле любого
-- производителя). Конверт один на все события платформы, поэтому его
-- поля стоя́т колонками, а содержимое — jsonb.

create table outbox_events (
    id            bigserial primary key,
    event_id      varchar(64)  not null,
    tenant_id     varchar(64)  not null,
    event_type    varchar(128) not null,
    version       integer      not null,
    occurred_at   timestamptz  not null,
    trace_context varchar(256),
    topic         varchar(128) not null,
    payload       jsonb        not null,
    published_at  timestamptz,
    -- Идентичность события уникальна: повтор записи дублем не станет.
    constraint uk_outbox_event_id unique (event_id)
);

-- Реле читает неопубликованное окном в порядке записи: порядок несущий,
-- события одного тенанта обязаны прийти в порядке происшествия.
create index ix_outbox_unpublished on outbox_events (id) where published_at is null;
