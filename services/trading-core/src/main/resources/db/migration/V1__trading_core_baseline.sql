-- Базовая схема сервиса trading-core.
--
-- ЧТО ЗДЕСЬ ЛЕЖИТ. Перечень предметов базы объявлен один раз —
-- docs/architecture/data-ownership.md §Раскладка, строка `trading_core`;
-- здесь их исполнимая форма. Порядок разделов — порядок зависимостей:
-- проекции чужих реестров → копия определения стратегии → агрегат сделки
-- → строки исполнения → деньги и зеркало счёта → аномалии → outbox.
--
-- РАДИУС ТОРГОВОЙ СТРОКИ — БИРЖЕВОЙ СЧЁТ, А НЕ ПЛОЩАДКА. Всякая строка,
-- у которой в доноре стоял `exchange_id`, здесь называет
-- `exchange_account_id`: инструмент принадлежит площадке, а риск, ступень
-- и деньги — счёту (docs/architecture/tenant-and-exchange.md §«Торговая
-- строка называет счёт, и радиусы читаются от него»).
--
-- ПРОЕКЦИИ, А НЕ ВТОРАЯ ИСТИНА. Реестр счетов принадлежит `auth`, каталог
-- инструментов — `market-data`; ядро держит их проекции, потому что
-- числовой ключ границу сервиса не пересекает, а правила инструмента
-- читаются на каждом сайзинге (docs/models/domain/core/Instrument.md
-- §«Проекция у торгового ядра», docs/models/domain/core/ExchangeAccount.md
-- §Персистентность). Писатель проекционных колонок один — тик синка.
--
-- ДЕНЕЖНЫЕ И ПРОЦЕНТНЫЕ ВЕЛИЧИНЫ — numeric(36,18); enum'ы — строкой
-- (.claude/rules/codestyle.md §«Слои моделей и enum'ы»); время — UTC
-- (timestamptz, docs/rules/time-utc.md); имена таблиц — во множественном
-- числе.
--
-- Таблицы пусты по построению: до прод-рубежа бэкфилла не бывает
-- (.claude/rules/pre-launch-schema-changes.md), поэтому обязательные
-- колонки вводятся сразу.

-- ---------------------------------------------------------------------
-- 1. Проекции чужих реестров и числа тенанта
-- ---------------------------------------------------------------------

-- Проекция реестра счетов ПЛЮС торговое состояние счёта. Одна таблица, а
-- не две: ключ у них общий, а правило «у таблицы один писатель» адресует
-- сервисы, не тропы. Тропы разведены по колонкам — проекционные пишет
-- тик синка, торговые пишет торговый код ядра.
create table exchange_accounts (
    id                     bigserial primary key,
    internal_id            varchar(64) not null,
    -- Тенант назван его внешней идентичностью: числовой ключ базы `auth`
    -- границу сервиса не пересекает.
    tenant_internal_id     varchar(64) not null,
    exchange_code          varchar(32) not null,
    label                  varchar(128),
    contour                varchar(16) not null,
    status                 varchar(32) not null,
    -- Момент снимка проекции: операнд гейта свежести, как у рыночных
    -- данных (Instrument.md §«Срок свежести проекции»).
    projected_at           timestamptz not null,
    -- Торговое состояние счёта. Пусто у базы риска — это отказ, а не
    -- ноль (docs/rules/risk-policy.md §«База следует за балансом»).
    risk_base              numeric(36, 18),
    risk_base_currency     varchar(32),
    consecutive_loss_count integer     not null default 0,
    blind_pass_count       integer     not null default 0,
    safety_rung            varchar(32) not null,
    created_at             timestamptz,
    created_by             varchar(64),
    modified_at            timestamptz,
    modified_by            varchar(64),
    external_created_at    timestamptz,
    external_modified_at   timestamptz,
    constraint uk_exchange_account_internal_id unique (internal_id)
);

create index ix_exchange_account_tenant on exchange_accounts (tenant_internal_id);

-- Проекция каталога инструментов. Состав — то, что ядро читает, и ничего
-- сверх: свечей, групп сбора и производных здесь нет — их ядро читает у
-- владельца по месту.
create table instruments (
    id                           bigserial primary key,
    internal_id                  varchar(64) not null,
    exchange_code                varchar(32) not null,
    external_id                  varchar(64) not null,
    external_type                varchar(32) not null,
    status                       varchar(32) not null,
    external_settlement_currency varchar(32),
    external_base_currency       varchar(32),
    external_quote_currency      varchar(32),
    external_rules               jsonb,
    projected_at                 timestamptz not null,
    created_at                   timestamptz,
    created_by                   varchar(64),
    modified_at                  timestamptz,
    modified_by                  varchar(64),
    external_created_at          timestamptz,
    external_modified_at         timestamptz,
    constraint uk_instrument_internal_id unique (internal_id),
    constraint uk_instrument_exchange_external_id unique (exchange_code, external_id)
);

-- Числа риск-аппетита тенанта. Колонки NULLABLE намеренно: пустота есть
-- отказ, а не ноль (docs/models/domain/core/Tenant.md §Персистентность).
-- Строку заводит тик синка реестра счетов — тенанта ядро узнаёт из счёта.
create table tenant_risk_appetites (
    id                                          bigserial primary key,
    tenant_internal_id                          varchar(64) not null,
    global_simultaneous_risk_per_deal_percent   numeric(36, 18),
    global_catastrophic_risk_per_deal_multiplier numeric(36, 18),
    global_consecutive_loss_limit               integer,
    created_at                                  timestamptz,
    created_by                                  varchar(64),
    modified_at                                 timestamptz,
    modified_by                                 varchar(64),
    external_created_at                         timestamptz,
    external_modified_at                        timestamptz,
    constraint uk_tenant_risk_appetite_tenant unique (tenant_internal_id)
);

-- Ступень и настройки СЧЁТА НА ИНСТРУМЕНТЕ. В проекции каталога им места
-- нет: её перезаписывает синк и запись ядра он бы затирал
-- (Instrument.md §«Ступень и настройки счёта на инструменте»).
create table account_instrument_states (
    id                   bigserial primary key,
    exchange_account_id  bigint      not null,
    instrument_id        bigint      not null,
    safety_rung          varchar(32) not null,
    margin_mode          varchar(32),
    leverage             integer,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    -- Ключ — ПАРА: ступень, поднятая отказами одного счёта, не описывает
    -- состояние другого. Он же гасит гонку двух ленивых читателей.
    constraint uk_account_instrument_state unique (exchange_account_id, instrument_id),
    constraint fk_account_instrument_state_account foreign key (exchange_account_id)
        references exchange_accounts (id),
    constraint fk_account_instrument_state_instrument foreign key (instrument_id)
        references instruments (id)
);

-- ---------------------------------------------------------------------
-- 2. Копия активированного определения стратегии
-- ---------------------------------------------------------------------
--
-- Копия неизменяема и живёт у ядра, чтобы торговля не останавливалась
-- вместе с недоступным владельцем определений
-- (docs/architecture/data-ownership.md §«Копии чужих данных»). До шага 8
-- фазы 2 её пишет команда приёма определения на поверхности ядра; форма
-- при смене писателя не меняется.

create table strategies (
    id                   bigserial primary key,
    internal_id          varchar(64)  not null,
    exchange_account_id  bigint       not null,
    instrument_id        bigint       not null,
    name                 varchar(128) not null,
    status               varchar(32)  not null,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint uk_strategy_internal_id unique (internal_id),
    constraint fk_strategy_account foreign key (exchange_account_id) references exchange_accounts (id),
    constraint fk_strategy_instrument foreign key (instrument_id) references instruments (id)
);

-- Инвариант жизненного цикла на радиусе счёта: одна активная стратегия
-- на пару «счёт, инструмент».
create unique index uk_strategy_active_per_account_instrument
    on strategies (exchange_account_id, instrument_id)
    where status = 'ACTIVE';

create table strategy_market_phase_settings (
    id                        bigserial primary key,
    strategy_id               bigint      not null,
    timeframe                 varchar(32) not null,
    params                    jsonb       not null,
    indicator_settings        jsonb,
    market_structure_settings jsonb,
    expiration_duration       varchar(32) not null,
    created_at                timestamptz,
    created_by                varchar(64),
    modified_at               timestamptz,
    modified_by               varchar(64),
    external_created_at       timestamptz,
    external_modified_at      timestamptz,
    constraint uk_strategy_market_phase_setting_strategy unique (strategy_id),
    constraint fk_strategy_market_phase_setting_strategy foreign key (strategy_id) references strategies (id)
);

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
    id                        bigserial primary key,
    strategy_id               bigint      not null,
    market_phase_type         varchar(32) not null,
    phase_entry_policy        varchar(32) not null,
    risk_per_trade_percent    numeric(36, 18),
    target_risk_reward_ratio  numeric(36, 18),
    indicator_settings        jsonb,
    market_structure_settings jsonb,
    position_reopen_allowed   boolean,
    created_at                timestamptz,
    created_by                varchar(64),
    modified_at               timestamptz,
    modified_by               varchar(64),
    external_created_at       timestamptz,
    external_modified_at      timestamptz,
    constraint uk_strategy_detail_strategy_phase unique (strategy_id, market_phase_type),
    constraint fk_strategy_detail_strategy foreign key (strategy_id) references strategies (id)
);

-- Объявление траншей детали: сколько уровней и с каким шагом.
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

create table strategy_steps (
    id                          bigserial primary key,
    strategy_detail_id          bigint      not null,
    tranche_status              varchar(32) not null,
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
    constraint uk_strategy_step_detail_tranche_status_index unique (strategy_detail_id, tranche_status, step_index),
    constraint fk_strategy_step_detail foreign key (strategy_detail_id) references strategy_details (id)
);

create table strategy_actions (
    id                   bigserial primary key,
    strategy_step_id     bigint      not null,
    strategy_detail_id   bigint      not null,
    action_kind          varchar(32) not null,
    key                  varchar(64) not null,
    action_type          varchar(32) not null,
    level                integer,
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
-- 3. Агрегат сделки
-- ---------------------------------------------------------------------

create table deals (
    id                              bigserial primary key,
    internal_id                     varchar(64) not null,
    exchange_account_id             bigint      not null,
    instrument_id                   bigint      not null,
    -- Пусто у восстановительной сделки: у неё нет объявления, по которому
    -- она открыта (инвариант ниже связывает пустоту с причиной входа).
    strategy_detail_id              bigint,
    status                          varchar(32) not null,
    direction                       varchar(16) not null,
    entry_reason                    varchar(32),
    entry_market_phase              varchar(64),
    shutdown_reason                 varchar(32),
    close_reason                    varchar(32),
    close_outcome                   varchar(64),
    result_profit                   numeric(36, 18),
    result_profit_currency          varchar(16),
    -- Риск сделки: план, принятое за жизнь, живое сейчас и снятое
    -- защитой (docs/rules/risk-policy.md §«Четыре потолка»).
    planned_risk_amount             numeric(36, 18),
    incurred_risk_amount            numeric(36, 18),
    current_risk_amount             numeric(36, 18),
    protection_relieved_risk_amount numeric(36, 18),
    planned_risk_currency           varchar(64),
    planned_risk_equity_base        numeric(36, 18),
    -- Сверка движений средств и её окно (docs/rules/pnl-reconciliation.md).
    coverage_proven_through         timestamptz,
    bills_window_begin              timestamptz,
    bills_fetched_through           timestamptz,
    reconciliation_status           varchar(64),
    breakdown_incomplete            varchar(64),
    risk_benchmark_availability     varchar(64),
    created_at                      timestamptz,
    created_by                      varchar(64),
    modified_at                     timestamptz,
    modified_by                     varchar(64),
    external_created_at             timestamptz,
    external_modified_at            timestamptz,
    constraint uk_deal_internal_id unique (internal_id),
    constraint fk_deal_account foreign key (exchange_account_id) references exchange_accounts (id),
    constraint fk_deal_instrument foreign key (instrument_id) references instruments (id),
    constraint fk_deal_strategy_detail foreign key (strategy_detail_id) references strategy_details (id),
    -- Деталь объявления есть тогда и только тогда, когда вход не
    -- восстановительный: восстановленная сделка объявления не имеет.
    constraint ck_deal_detail_matches_entry_reason
        check ((strategy_detail_id is null) = (entry_reason = 'RECOVERY'))
);

create index ix_deal_status_close_outcome on deals (status, close_outcome);
create index ix_deal_account_instrument_status on deals (exchange_account_id, instrument_id, status);

-- Инвариант «одна незакрытая сделка на пару счёт-инструмент» на уровне
-- БД — защита в глубину к гейту входа. Радиус пары, а не инструмента:
-- один и тот же инструмент на разных счетах — разные торговые строки.
create unique index uk_deal_active_account_instrument
    on deals (exchange_account_id, instrument_id)
    where status not in ('CLOSED', 'EMERGENCY_CLOSED');

create table deal_tranches (
    id                   bigserial primary key,
    internal_id          varchar(64) not null,
    deal_id              bigint      not null,
    strategy_tranche_id  bigint,
    level                integer,
    status               varchar(32) not null,
    -- Номер эпизода: растёт при переоткрытии ТЕМ ЖЕ траншем. Без него
    -- строки прошлого эпизода неотличимы от строк текущего.
    episode_seq          integer     not null default 1,
    entry_step_type      varchar(64),
    close_reason         varchar(64),
    -- Слагаемые экспозиции; сама экспозиция производна и не хранится.
    entry_filled         numeric(36, 18),
    reduce_only_filled   numeric(36, 18),
    protection_closed    numeric(36, 18),
    created_at           timestamptz,
    updated_at           timestamptz,
    constraint uk_deal_tranche_internal_id unique (internal_id),
    constraint fk_deal_tranche_deal foreign key (deal_id) references deals (id),
    constraint fk_deal_tranche_strategy_tranche foreign key (strategy_tranche_id)
        references strategy_tranches (id)
);

create index ix_deal_tranche_deal on deal_tranches (deal_id);
create index ix_deal_tranche_deal_status on deal_tranches (deal_id, status);

-- Один живой транш на объявленный уровень: веер материализуется ровно
-- столько раз, сколько уровней объявлено.
create unique index uk_deal_tranche_declaration
    on deal_tranches (deal_id, strategy_tranche_id, level)
    where strategy_tranche_id is not null and status <> 'CLOSED';

create table positions (
    id                            bigserial primary key,
    deal_id                       bigint      not null,
    external_id                   varchar(64),
    status                        varchar(32) not null,
    close_reason                  varchar(32),
    direction                     varchar(16),
    external_size                 numeric(36, 18),
    external_average_entry_price  numeric(36, 18),
    external_mark_price           numeric(36, 18),
    external_liquidation_price    numeric(36, 18),
    external_margin               numeric(36, 18),
    external_unrealized_profit    numeric(36, 18),
    external_realized_profit      numeric(36, 18),
    external_realized_profit_gross numeric(36, 18),
    external_result_currency      varchar(16),
    external_close_average_price  numeric(36, 18),
    external_close_type           varchar(64),
    external_fee                  numeric(36, 18),
    external_funding_cost         numeric(36, 18),
    external_liquidation_penalty  numeric(36, 18),
    created_at                    timestamptz,
    created_by                    varchar(64),
    modified_at                   timestamptz,
    modified_by                   varchar(64),
    external_created_at           timestamptz,
    external_modified_at          timestamptz,
    constraint fk_position_deal foreign key (deal_id) references deals (id)
);

create index ix_position_deal on positions (deal_id);

-- Эпизод позиции опознаётся тройкой: сделка, идентификатор площадки и
-- момент открытия. Переоткрытие тем же идентификатором — новый эпизод.
create unique index uk_position_deal_external
    on positions (deal_id, external_id, external_created_at)
    where external_id is not null;

create table orders (
    id                     bigserial primary key,
    deal_id                bigint      not null,
    deal_tranche_id        bigint,
    position_id            bigint,
    internal_id            varchar(64) not null,
    external_id            varchar(64),
    status                 varchar(32) not null,
    close_reason           varchar(32),
    type                   varchar(32) not null,
    side                   varchar(16),
    external_status        varchar(32),
    price                  numeric(36, 18),
    size                   numeric(36, 18),
    accumulated_fill_size  numeric(36, 18),
    average_price          numeric(36, 18),
    fee                    numeric(36, 18),
    position_reducing_only boolean,
    replaces_internal_id   varchar(64),
    -- Замысел сайзинга: то, из чего размер выведен. Хранится, потому что
    -- проверка «взято по плану» без плана невыразима.
    planned_entry_price    numeric(36, 18),
    planned_stop_price     numeric(36, 18),
    planned_size_contracts numeric(36, 18),
    planned_contract_value numeric(36, 18),
    planned_risk_amount    numeric(36, 18),
    planned_risk_currency  varchar(64),
    created_at             timestamptz,
    created_by             varchar(64),
    modified_at            timestamptz,
    modified_by            varchar(64),
    external_created_at    timestamptz,
    external_modified_at   timestamptz,
    constraint uk_order_internal_id unique (internal_id),
    constraint fk_order_deal foreign key (deal_id) references deals (id),
    constraint fk_order_deal_tranche foreign key (deal_tranche_id) references deal_tranches (id),
    constraint fk_order_position foreign key (position_id) references positions (id)
);

create index ix_order_deal on orders (deal_id);
create index ix_order_deal_tranche on orders (deal_tranche_id);
create index ix_order_position on orders (position_id);

create table attached_algo_orders (
    id                      bigserial primary key,
    order_id                bigint      not null,
    internal_id             varchar(64) not null,
    external_attached_id    varchar(64),
    external_id             varchar(64),
    status                  varchar(32) not null,
    close_reason            varchar(32),
    type                    varchar(32) not null,
    external_status         varchar(32),
    external_type           varchar(32),
    fail_code               varchar(64),
    trigger_price_type      varchar(32),
    size                    numeric(36, 18),
    stop_loss_trigger_price numeric(36, 18),
    created_at              timestamptz,
    created_by              varchar(64),
    modified_at             timestamptz,
    modified_by             varchar(64),
    external_created_at     timestamptz,
    external_modified_at    timestamptz,
    constraint uk_attached_algo_order_internal_id unique (internal_id),
    constraint fk_attached_algo_order_order foreign key (order_id) references orders (id)
);

create index ix_attached_algo_order_order on attached_algo_orders (order_id);

create table algo_orders (
    id                        bigserial primary key,
    deal_id                   bigint      not null,
    deal_tranche_id           bigint,
    internal_id               varchar(64) not null,
    external_id               varchar(64),
    status                    varchar(32) not null,
    close_reason              varchar(32),
    condition_type            varchar(32) not null,
    condition                 jsonb,
    size                      numeric(36, 18),
    direction                 varchar(16),
    position_reducing_only    boolean,
    replaces_internal_id      varchar(64),
    external_status           varchar(32),
    fail_code                 varchar(32),
    external_size             numeric(36, 18),
    external_price            numeric(36, 18),
    external_trigger_time     timestamptz,
    linked_order_external_ids jsonb,
    created_at                timestamptz,
    created_by                varchar(64),
    modified_at               timestamptz,
    modified_by               varchar(64),
    external_created_at       timestamptz,
    external_modified_at      timestamptz,
    constraint uk_algo_order_internal_id unique (internal_id),
    constraint fk_algo_order_deal foreign key (deal_id) references deals (id),
    constraint fk_algo_order_deal_tranche foreign key (deal_tranche_id) references deal_tranches (id)
);

create index ix_algo_order_deal on algo_orders (deal_id);
create index ix_algo_order_deal_tranche on algo_orders (deal_tranche_id);

-- ---------------------------------------------------------------------
-- 4. Строки исполнения
-- ---------------------------------------------------------------------
--
-- Строки исполнения объявленного действия и системной команды разведены
-- по таблицам: у них разные ключи и разные жизненные циклы. Сиквенс —
-- ОДИН на обе: идентичность строки исполнения одна, и пересечение
-- идентификаторов сделало бы её неоднозначной в логах и ссылках.

create sequence deal_action_state_seq;

create table deal_strategy_action_states (
    id                   bigint      not null default nextval('deal_action_state_seq') primary key,
    deal_id              bigint      not null,
    deal_tranche_id      bigint,
    tranche_episode_seq  integer,
    strategy_action_id   bigint      not null,
    status               varchar(32) not null,
    -- Предмет исполнения: чем строка кончилась в модели. Тип и ключ, а не
    -- ссылка: цели у разных действий разных родов.
    target_entity_type   varchar(64),
    target_entity_id     bigint,
    attempt_count        integer,
    max_attempts         integer,
    next_retry_at        timestamptz,
    last_error           jsonb,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint fk_deal_strategy_action_state_deal foreign key (deal_id) references deals (id),
    constraint fk_deal_strategy_action_state_tranche foreign key (deal_tranche_id) references deal_tranches (id),
    constraint fk_deal_strategy_action_state_action foreign key (strategy_action_id) references strategy_actions (id)
);

create index ix_deal_strategy_action_state_deal on deal_strategy_action_states (deal_id);

-- Ключи ЧАСТИЧНЫЕ, по живым статусам, и форм две: NULL в уникальном
-- индексе Postgres сам с собой не конфликтует, поэтому один индекс по
-- трём колонкам пропустил бы два живых исполнения агрегатного действия.
create unique index uk_deal_strategy_action_state_tranche_live
    on deal_strategy_action_states (deal_id, deal_tranche_id, tranche_episode_seq, strategy_action_id)
    where deal_tranche_id is not null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

create unique index uk_deal_strategy_action_state_deal_level_live
    on deal_strategy_action_states (deal_id, strategy_action_id)
    where deal_tranche_id is null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

create table deal_system_action_states (
    id                   bigint      not null default nextval('deal_action_state_seq') primary key,
    deal_id              bigint      not null,
    deal_tranche_id      bigint,
    tranche_episode_seq  integer,
    system_action_type   varchar(64) not null,
    status               varchar(64) not null,
    attempt_count        integer,
    max_attempts         integer,
    next_retry_at        timestamptz,
    last_error           jsonb,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint fk_deal_system_action_state_deal foreign key (deal_id) references deals (id),
    constraint fk_deal_system_action_state_tranche foreign key (deal_tranche_id) references deal_tranches (id)
);

create index ix_deal_system_action_state_deal on deal_system_action_states (deal_id);

create unique index uk_deal_system_action_state_tranche_live
    on deal_system_action_states (deal_id, deal_tranche_id, tranche_episode_seq, system_action_type)
    where deal_tranche_id is not null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

create unique index uk_deal_system_action_state_deal_level_live
    on deal_system_action_states (deal_id, system_action_type)
    where deal_tranche_id is null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

-- ---------------------------------------------------------------------
-- 5. Деньги: зеркало средств счёта, движения и ставки комиссии
-- ---------------------------------------------------------------------

create table balance_containers (
    id                       bigserial primary key,
    exchange_account_id      bigint not null,
    external_updated_at      timestamptz,
    external_total_equity    numeric(36, 18),
    external_adjusted_equity numeric(36, 18),
    external_available_equity numeric(36, 18),
    created_at               timestamptz,
    created_by               varchar(64),
    modified_at              timestamptz,
    modified_by              varchar(64),
    external_created_at      timestamptz,
    external_modified_at     timestamptz,
    -- Снимок средств один на счёт: он и есть зеркало, а не история.
    constraint uk_balance_container_account unique (exchange_account_id),
    constraint fk_balance_container_account foreign key (exchange_account_id)
        references exchange_accounts (id)
);

create table balances (
    id                         bigserial primary key,
    balance_container_id       bigint      not null,
    external_currency          varchar(32) not null,
    external_updated_at        timestamptz,
    external_equity            numeric(36, 18),
    external_cash_balance      numeric(36, 18),
    external_available_balance numeric(36, 18),
    external_frozen_balance    numeric(36, 18),
    created_at                 timestamptz,
    created_by                 varchar(64),
    modified_at                timestamptz,
    modified_by                varchar(64),
    external_created_at        timestamptz,
    external_modified_at       timestamptz,
    constraint fk_balance_container foreign key (balance_container_id) references balance_containers (id)
);

create index ix_balance_container on balances (balance_container_id);

-- Разбивка движений средств сделки. Строка привязана к счёту всегда, к
-- сделке — не всегда: нераспознанная строка движения остаётся в корзине
-- и ждёт отображения (docs/rules/pnl-reconciliation.md).
create table deal_cash_flows (
    id                            bigserial primary key,
    exchange_account_id           bigint       not null,
    deal_id                       bigint,
    category                      varchar(64)  not null,
    amount                        numeric(36, 18) not null,
    ccy                           varchar(64)  not null,
    external_fee                  numeric(36, 18),
    position_balance_change       numeric(36, 18),
    applied_rate                  numeric(36, 18),
    rate_status                   varchar(64)  not null,
    applied_rate_candle_instrument varchar(64),
    applied_rate_candle_timeframe varchar(64),
    applied_rate_candle_open_time timestamptz,
    external_instrument_id        varchar(64),
    external_bill_id              varchar(64)  not null,
    external_type                 varchar(64)  not null,
    external_sub_type             varchar(64),
    external_order_id             varchar(64),
    created_at                    timestamptz,
    created_by                    varchar(64),
    modified_at                   timestamptz,
    modified_by                   varchar(64),
    external_created_at           timestamptz,
    external_modified_at          timestamptz,
    -- Идемпотентность разбора движений: строка площадки заводится один
    -- раз на счёт (docs/rules/idempotency-via-unique.md).
    constraint uk_deal_cash_flow_account_bill unique (exchange_account_id, external_bill_id),
    constraint fk_deal_cash_flow_account foreign key (exchange_account_id) references exchange_accounts (id),
    constraint fk_deal_cash_flow_deal foreign key (deal_id) references deals (id)
);

create index ix_deal_cash_flow_deal on deal_cash_flows (deal_id);

-- Ставка комиссии — атрибут комиссионного УРОВНЯ СЧЁТА, и читается она с
-- ключами счёта: у market-data ключей нет, все его чтения публичные
-- (docs/models/domain/other/TradeFeeRate.md).
create table trade_fee_rates (
    id                       bigserial primary key,
    exchange_account_id      bigint      not null,
    external_instrument_type varchar(64) not null,
    external_fee_group_id    varchar(64) not null,
    instrument_type          varchar(64) not null,
    external_taker_fee_rate  varchar(64) not null,
    external_maker_fee_rate  varchar(64) not null,
    external_fee_level       varchar(64),
    refresh_count            bigint      not null default 1,
    created_at               timestamptz,
    created_by               varchar(64),
    modified_at              timestamptz,
    modified_by              varchar(64),
    external_created_at      timestamptz,
    external_modified_at     timestamptz,
    constraint fk_trade_fee_rate_account foreign key (exchange_account_id) references exchange_accounts (id)
);

-- Чтение идёт за последней ставкой группы: ключ группы плюс убывающий id.
create index ix_trade_fee_rate_group
    on trade_fee_rates (exchange_account_id, external_instrument_type, external_fee_group_id, id desc);

-- ---------------------------------------------------------------------
-- 6. Отчёты аномалий
-- ---------------------------------------------------------------------

create table anomaly_reports (
    id                   bigserial primary key,
    internal_id          varchar(64) not null,
    exchange_account_id  bigint      not null,
    instrument_id        bigint,
    subject_external_id  varchar(64),
    scope                varchar(16) not null,
    status               varchar(32) not null,
    severity             varchar(16) not null,
    code                 varchar(64) not null,
    message              varchar(1024),
    internal_before      jsonb,
    external_before      jsonb,
    internal_after       jsonb,
    external_after       jsonb,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint uk_anomaly_report_internal_id unique (internal_id),
    constraint fk_anomaly_report_account foreign key (exchange_account_id) references exchange_accounts (id),
    constraint fk_anomaly_report_instrument foreign key (instrument_id) references instruments (id)
);

-- Дедуп читает последний отчёт по ключу происшествия; индекса под сам
-- ключ дедупа нет намеренно — разбор человеком идёт по времени
-- (docs/models/domain/other/AnomalyReport.md).
create index ix_anomaly_report_standing
    on anomaly_reports (exchange_account_id, code, severity, instrument_id, subject_external_id, created_at desc);

-- ---------------------------------------------------------------------
-- 7. Outbox
-- ---------------------------------------------------------------------
--
-- Решение и его событие пишутся ОДНОЙ транзакцией; публикует отдельное
-- реле и помечает опубликованное (docs/architecture/data-ownership.md
-- §«Outbox и доставка», docs/components/OutboxRelayJob.md). Конверт —
-- один на все события платформы (docs/architecture/contracts.md
-- §«Конверт события»), поэтому его поля стоя́т колонками, а содержимое —
-- jsonb: форму содержимого знает только производитель класса.

create table outbox (
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
create index ix_outbox_unpublished on outbox (id) where published_at is null;
