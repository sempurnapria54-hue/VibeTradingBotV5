-- Базовая схема сервиса market-data: каталог инструментов, единицы сбора
-- свечей, реестры идентичностей вычисления, результаты расчёта и ряды
-- невосполнимых срезов.
--
-- Своя цепочка миграций с V1: схема донора — история, переиспользованию
-- не подлежит (.claude/rules/pre-launch-schema-changes.md). Обязательные
-- колонки вводятся сразу целевой формой — существующих строк нет.
--
-- TIMESCALEDB ОБЯЗАТЕЛЕН, И ОТКАЗ ЗДЕСЬ НАМЕРЕННЫЙ. Ряды рынка объявлены
-- гипертаблицами (docs/architecture/data-ownership.md §«Временные ряды»);
-- образ кластера обязан нести расширение (deploy/base/data/postgres-cluster.yaml).
-- Молчаливое падение на обычные таблицы было бы ошибкой в разрешающую
-- сторону: сервис поднялся бы, а объявленной формы хранения не было бы —
-- и обнаружилось бы это на объёме, то есть поздно.
--
-- ВРЕМЯ — UTC (timestamptz либо UTC-миллисекунды, docs/rules/time-utc.md).
-- Денежные и ценовые величины — numeric(36,18). Енумы хранятся строкой
-- значением name() (.claude/rules/codestyle.md §«Слои моделей и enum'ы»).
-- Имена таблиц — во множественном числе.
--
-- ВНЕШНИХ КЛЮЧЕЙ У ГИПЕРТАБЛИЦ НЕТ, и это выбор, а не забывчивость.
-- Ссылка из ряда в каталог проверялась бы на каждой вставке чанка ровно
-- там, где идёт поток по всему листингу; целостность здесь держит
-- писатель — рядов без своего инструмента и без своей группы он не
-- пишет, потому что ключ берёт из них же. Обычные таблицы FK несут:
-- у них цена проверки не на потоке.

create extension if not exists timescaledb;

-- ---------------------------------------------------------------------
-- Каталог инструментов площадки
-- ---------------------------------------------------------------------

-- Площадка названа КОДОМ, а не числовым идентификатором: справочник
-- площадок принадлежит auth (docs/architecture/tenant-and-exchange.md
-- §«Три сущности вместо одной»), а внешнего ключа через границу сервиса
-- не бывает. Код — та же ось, по которой площадку называет реестр счетов
-- (services/auth ... exchange_accounts.exchange_code).
--
-- Плеча и режима маржи здесь НЕТ, и это не пропуск: они настройка СЧЁТА
-- на инструменте, а не свойство инструмента (tenant-and-exchange.md
-- §Инструменты). У market-data счетов нет вовсе.
--
-- Планового горизонта истории здесь тоже нет: глубину называет требование
-- потребителя, и она принадлежит единице сбора, а не инструменту
-- (docs/architecture/market-data-collection.md §«Как потребность доходит
-- до сбора»).
create table instruments (
    id                             bigserial primary key,
    internal_id                    varchar(64)  not null,
    exchange_code                  varchar(32)  not null,
    external_id                    varchar(64)  not null,
    external_type                  varchar(32)  not null,
    status                         varchar(32)  not null,
    external_status                varchar(32),
    external_settlement_currency   varchar(32),
    external_base_currency         varchar(32),
    external_quote_currency        varchar(32),
    external_rules                 jsonb,
    created_at                     timestamptz,
    created_by                     varchar(64),
    modified_at                    timestamptz,
    modified_by                    varchar(64),
    external_created_at            timestamptz,
    external_modified_at           timestamptz,
    constraint uk_instrument_internal_id unique (internal_id),
    constraint uk_instrument_exchange_external_id unique (exchange_code, external_id)
);

comment on table instruments is 'Каталог инструментов подключённых площадок; наполняется синком листинга';
comment on column instruments.status is 'CREATED | SYNC | CANDLES_LOADING | ACTIVE | CLOSED | ERROR; safety-ступени инструмента пишет trading-core, market-data их не ставит';
comment on column instruments.external_rules is 'Справочный навес InstrumentExternalRules: волатильная часть спецификации площадки';

-- ---------------------------------------------------------------------
-- Единицы сбора свечей
-- ---------------------------------------------------------------------

-- Группу заводит ТРЕБОВАНИЕ потребителя (инструмент + таймфрейм +
-- глубина), а не онбординг инструмента: собирается то, что кому-то нужно
-- (docs/processes/candle-loading.md §«Кто заводит группу»). Отсюда
-- горизонт бэкфилла — колонка ГРУППЫ: у 1m и 1D одного инструмента глубины
-- разные, и общий горизонт на инструмент качал бы годы минутных баров
-- ради дневного требования.
create table candle_groups (
    id                       bigserial primary key,
    internal_id              varchar(128) not null,
    instrument_id            bigint       not null,
    timeframe                varchar(32)  not null,
    status                   varchar(32)  not null,
    planned_first_utc_millis bigint,
    actual_first_utc_millis  bigint,
    actual_last_utc_millis   bigint,
    count                    bigint       not null default 0,
    created_at               timestamptz,
    created_by               varchar(64),
    modified_at              timestamptz,
    modified_by              varchar(64),
    external_created_at      timestamptz,
    external_modified_at     timestamptz,
    constraint uk_candle_group_internal_id unique (internal_id),
    constraint uk_candle_group_instrument_timeframe unique (instrument_id, timeframe),
    constraint fk_candle_group_instrument foreign key (instrument_id) references instruments (id)
);

comment on column candle_groups.planned_first_utc_millis is 'Горизонт бэкфилла: нижняя граница истории, заказанная потребителем; повторное требование глубже расширяет его';

-- Свечи — гипертаблица по времени открытия бара (UTC мс). Суррогатного
-- ключа нет: ключ ряда естественный (группа, открытие бара), и он же
-- держит идемпотентность загрузки — повторная выкачка того же окна не
-- создаёт вторых строк (docs/rules/idempotency-via-unique.md).
create table candles (
    candle_group_id      bigint          not null,
    open_timestamp       bigint          not null,
    open                 numeric(36, 18) not null,
    high                 numeric(36, 18) not null,
    low                  numeric(36, 18) not null,
    close                numeric(36, 18) not null,
    volume               numeric(36, 18),
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint pk_candle primary key (candle_group_id, open_timestamp)
);

-- Чанк — неделя в миллисекундах. Величина калибровочная и провизорная:
-- писатель — владелец market-data, пересмотр — по наблюдаемому размеру
-- чанка на живом объёме.
select create_hypertable('candles', 'open_timestamp',
                         chunk_time_interval => 604800000,
                         if_not_exists => true);

comment on table candles is 'Закрытые свечи групп сбора; в ряд попадают только confirm=1';

-- ---------------------------------------------------------------------
-- Реестры идентичностей вычисления
-- ---------------------------------------------------------------------

-- Производные ключуются ИДЕНТИЧНОСТЬЮ ВЫЧИСЛЕНИЯ, а не настройкой
-- стратегии: настройка живёт в базе другого сервиса, а у фич по всему
-- листингу для детекторов советника владельца нет вовсе (дом решения —
-- docs/models/domain/other/IndicatorValue.md §«Ключевание — идентичностью
-- вычисления»). Канонические параметры — текстовая нормализация формы
-- параметров: она и делает «ATR(14) на 1H» одной строкой для всех, кому
-- это значение нужно.
create table indicator_configs (
    id                   bigserial primary key,
    internal_id          varchar(64) not null,
    indicator_type       varchar(32) not null,
    timeframe            varchar(32) not null,
    params_canonical     text        not null,
    params               jsonb       not null,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint uk_indicator_config_internal_id unique (internal_id),
    constraint uk_indicator_config_identity unique (indicator_type, timeframe, params_canonical)
);

comment on table indicator_configs is 'Заказанные идентичности вычисления индикатора; обходятся джобой расчёта';

-- Ключи входов входят в идентичность структуры: два вычисления с разными
-- входами дают разные строки, иначе последнее записанное затирало бы
-- чужое (docs/models/domain/other/MarketStructure.md).
--
-- NULLS NOT DISTINCT — несущая клауза, а не украшение: входы
-- необязательны, и при обычной семантике UNIQUE две одинаковые
-- идентичности без входов считались бы разными, то есть реестр перестал
-- бы быть реестром.
create table market_structure_configs (
    id                          bigserial primary key,
    internal_id                 varchar(64) not null,
    timeframe                   varchar(32) not null,
    params_canonical            text        not null,
    params                      jsonb       not null,
    efficiency_ratio_config_id  bigint,
    atr_config_id               bigint,
    created_at                  timestamptz,
    created_by                  varchar(64),
    modified_at                 timestamptz,
    modified_by                 varchar(64),
    external_created_at         timestamptz,
    external_modified_at        timestamptz,
    constraint uk_market_structure_config_internal_id unique (internal_id),
    constraint uk_market_structure_config_identity unique nulls not distinct
        (timeframe, params_canonical, efficiency_ratio_config_id, atr_config_id),
    constraint fk_market_structure_config_er foreign key (efficiency_ratio_config_id)
        references indicator_configs (id),
    constraint fk_market_structure_config_atr foreign key (atr_config_id)
        references indicator_configs (id)
);

comment on table market_structure_configs is 'Заказанные идентичности вычисления структуры рынка вместе с идентичностями её входов';

-- ---------------------------------------------------------------------
-- Результаты расчёта
-- ---------------------------------------------------------------------

-- Обычные таблицы, а не гипертаблицы: временными рядами объявлены свечи,
-- OI, funding, срезы стакана и ликвидации (docs/architecture/data-ownership.md
-- §«Временные ряды»); производные в этот перечень не входят, и их глубина
-- следует за свечами (docs/rules/market-data-retention.md).
create table indicator_values (
    id                   bigserial primary key,
    indicator_type       varchar(32)     not null,
    instrument_id        bigint          not null,
    indicator_config_id  bigint          not null,
    candle_timestamp     timestamptz     not null,
    atr                  numeric(36, 18),
    ema                  numeric(36, 18),
    rsi                  numeric(36, 18),
    macd_line            numeric(36, 18),
    signal_line          numeric(36, 18),
    histogram            numeric(36, 18),
    upper_band           numeric(36, 18),
    middle_band          numeric(36, 18),
    lower_band           numeric(36, 18),
    bandwidth            numeric(36, 18),
    percent_b            numeric(36, 18),
    stoch_k              numeric(36, 18),
    stoch_d              numeric(36, 18),
    obv                  numeric(36, 18),
    efficiency_ratio     numeric(36, 18),
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint uk_indicator_value_identity unique (instrument_id, indicator_config_id, candle_timestamp),
    constraint fk_indicator_value_config foreign key (indicator_config_id) references indicator_configs (id),
    constraint fk_indicator_value_instrument foreign key (instrument_id) references instruments (id)
);

create index ix_indicator_value_latest
    on indicator_values (instrument_id, indicator_config_id, candle_timestamp desc);

create table market_structures (
    id                          bigserial primary key,
    instrument_id               bigint          not null,
    market_structure_config_id  bigint          not null,
    type                        varchar(32)     not null,
    window_start_at             timestamptz     not null,
    window_end_at               timestamptz     not null,
    confirmed_at                timestamptz,
    breakout_broken_level_type  varchar(32),
    breakout_direction          varchar(16),
    breakout_level_price        numeric(36, 18),
    breakout_confirmed_at       timestamptz,
    created_at                  timestamptz,
    created_by                  varchar(64),
    modified_at                 timestamptz,
    modified_by                 varchar(64),
    external_created_at         timestamptz,
    external_modified_at        timestamptz,
    constraint uk_market_structure_identity unique (instrument_id, market_structure_config_id, window_end_at),
    constraint fk_market_structure_config foreign key (market_structure_config_id)
        references market_structure_configs (id),
    constraint fk_market_structure_instrument foreign key (instrument_id) references instruments (id)
);

create index ix_market_structure_latest
    on market_structures (instrument_id, market_structure_config_id, window_end_at desc);

create table market_price_levels (
    id                   bigserial primary key,
    market_structure_id  bigint          not null,
    type                 varchar(32)     not null,
    price                numeric(36, 18) not null,
    detected_at          timestamptz,
    confirmed_at         timestamptz,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint fk_market_price_level_structure foreign key (market_structure_id)
        references market_structures (id) on delete cascade
);

create index ix_market_price_level_structure on market_price_levels (market_structure_id);

-- Фазы рынка таблицы не имеют намеренно: фаза не персистируется —
-- вычисляется на лету на момент запроса по клаузам потребителя
-- (docs/rules/market-data-retention.md §«MarketPhase под правило не
-- подпадает»).

-- ---------------------------------------------------------------------
-- Невосполнимые срезы
-- ---------------------------------------------------------------------

-- Уровни — JSONB в строке владельца, не отдельной таблицей: FK на них
-- ниоткуда не ведёт, а нормализация дала бы сорок строк на срез вместо
-- одной — на проходе раз в минуту по всему листингу это два порядка
-- объёма ряда, который НЕ ЧИСТИТСЯ
-- (docs/models/domain/other/MarketOrderBook.md §Персистентность).
create table order_book_snapshots (
    instrument_id        bigint not null,
    external_timestamp   bigint not null,
    observed_timestamp   bigint not null,
    bids                 jsonb  not null,
    asks                 jsonb  not null,
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint pk_order_book_snapshot primary key (instrument_id, external_timestamp)
);

-- Чанк — сутки в миллисекундах: срез снимается раз в интервал по всему
-- листингу, и суточный чанк держит проход целиком. Величина провизорна,
-- пересмотр — по наблюдаемому объёму.
select create_hypertable('order_book_snapshots', 'external_timestamp',
                         chunk_time_interval => 86400000,
                         if_not_exists => true);

create table ticker_snapshots (
    instrument_id        bigint not null,
    external_timestamp   bigint not null,
    observed_timestamp   bigint not null,
    last_price           numeric(36, 18),
    volume               numeric(36, 18),
    mark_price           numeric(36, 18),
    index_price          numeric(36, 18),
    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint pk_ticker_snapshot primary key (instrument_id, external_timestamp)
);

select create_hypertable('ticker_snapshots', 'external_timestamp',
                         chunk_time_interval => 86400000,
                         if_not_exists => true);

comment on column ticker_snapshots.mark_price is 'Приходит отдельным агрегатным чтением; пустота означает «чтение не дошло», подстановка последней цены запрещена';
comment on column ticker_snapshots.index_price is 'То же, что у mark_price: своё чтение, своя пустота';
