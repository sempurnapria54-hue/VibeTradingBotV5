-- Базовая схема базы `statistics` — агрегатов над журналом
-- (docs/architecture/data-ownership.md §Раскладка).
--
-- ЦЕПОЧКА СВОЯ, С V1, И ОНА ВТОРАЯ У СЕРВИСА: соседняя (db/migration/audit)
-- ведёт базу журнала. Эта идёт под ролью-владельцем агрегатов, и грантов
-- она не выдаёт — журнал её не читает вовсе
-- (docs/spec/owner-role-grants.json, пара «роль журнала × база агрегатов»:
-- не выдано ничего).
--
-- ТАБЛИЦ ДВЕ — ПО ОДНОЙ НА ЗЕРНО, и разводит их состав события, а не то,
-- сумма это или счёт: расчётной валюты не несёт ни одно из четырёх событий
-- зерна происшествий, а ключом сделочного зерна она стои́т
-- (docs/rules/statistics-aggregates.md §«Счётчики происшествий — своё
-- зерно, а не строка сделочного агрегата»).
--
-- СТРОКИ ВОСПОЛНИМЫ ПОВТОРНЫМ ПЕРЕСЧЁТОМ на всём, что несёт журнал,
-- поэтому глубины хранения у агрегатов нет вовсе — в отличие от журнала.
--
-- ВРЕМЯ — UTC (timestamptz; сутки зерна — date, docs/rules/time-utc.md).
-- Денежные величины — numeric(36,18). Внешних ключей нет: идентичности
-- принадлежат чужим сервисам и приезжают содержимым события.
--
-- УМОЛЧАНИЙ У КОЛОНОК НЕТ. Все величины строки пишет один ход — upsert
-- джобы пересчёта, — и `default 0` понадобился бы только существующим
-- строкам, которых не бывает (.claude/rules/pre-launch-schema-changes.md).

-- ---------------------------------------------------------------------
-- Сделочное зерно
-- ---------------------------------------------------------------------

-- Ключ зерна — «тенант × биржевой счёт × определение стратегии × сутки UTC ×
-- расчётная валюта результата»; две колонки ключа законно пусты
-- (docs/rules/statistics-aggregates.md §«Зерно строки»).
--
-- ПЕРВИЧНЫЙ КЛЮЧ СУРРОГАТНЫЙ, а не по зерну, и выбора здесь нет: в ключе
-- зерна две пустые колонки, а первичный ключ пустых значений не допускает.
-- Уникальность зерна держит uk_deal_aggregate_grain.
--
-- ХРАНЯТСЯ ТОЛЬКО СЛАГАЕМЫЕ: доля выигрышных, профит-фактор, ожидаемость и
-- средний R выводятся при чтении и вторым носителем не заводятся
-- (docs/concept.md П4). Счётчик рядом с суммой — требование П1: популяция,
-- выпавшая из суммы, обязана иметь своё число, иначе выпадает молча.
create table deal_aggregates (
    id                             bigserial      primary key,
    tenant_id                      varchar(64)    not null,
    exchange_account_internal_id   varchar(64)    not null,
    strategy_internal_id           varchar(64),
    bucket_date                    date           not null,
    result_currency                varchar(16),

    closed_deals                   integer        not null,
    risk_bearing_deals             integer        not null,
    winning_deals                  integer        not null,
    losing_deals                   integer        not null,
    neutral_deals                  integer        not null,
    result_unavailable_deals       integer        not null,
    currency_unresolved_deals      integer        not null,
    risk_unsized_deals             integer        not null,
    liquidated_deals               integer        not null,
    forced_reduction_deals         integer        not null,
    outcome_undetermined_deals     integer        not null,
    reconciliation_mismatched_deals integer       not null,
    reconciliation_not_run_deals   integer        not null,
    breakdown_incomplete_deals     integer        not null,
    breakdown_not_assessed_deals   integer        not null,
    risk_benchmark_missing_deals   integer        not null,
    r_denominator_deals            integer        not null,

    result_before_funding_sum      numeric(36, 18) not null,
    net_result_sum                 numeric(36, 18) not null,
    fee_sum                        numeric(36, 18) not null,
    funding_sum                    numeric(36, 18) not null,
    liquidation_penalty_sum        numeric(36, 18) not null,
    win_result_sum                 numeric(36, 18) not null,
    loss_result_sum                numeric(36, 18) not null,
    planned_risk_sum               numeric(36, 18) not null,
    planned_risk_excluded_sum      numeric(36, 18) not null,
    r_sum                          numeric(36, 18) not null,

    assembled_at                   timestamptz     not null,
    -- КЛАУЗА `nulls not distinct` НЕСУЩАЯ, а не украшение: в ключе зерна
    -- две законно пустые колонки, а PostgreSQL по умолчанию считает пустые
    -- значения в уникальном индексе РАЗЛИЧНЫМИ
    -- (docs/rules/idempotency-via-unique.md). Без неё upsert существующей
    -- строки не находил бы, и проекция росла бы по строке на каждый прогон
    -- пересчёта — числа таких сделок умножались бы на число пересчётов, то
    -- есть выглядели бы торговой активностью.
    constraint uk_deal_aggregate_grain unique nulls not distinct
        (tenant_id, exchange_account_internal_id, strategy_internal_id, bucket_date, result_currency)
);

comment on table deal_aggregates is
    'Сделочное зерно агрегатов: производная проекция журнала, восполнимая '
    'повторным пересчётом (docs/rules/statistics-aggregates.md).';
comment on column deal_aggregates.strategy_internal_id is
    'Определение стратегии; пусто законно — «сделка стратегии не имеет»';
comment on column deal_aggregates.result_currency is
    'Расчётная валюта результата; пусто означает «валюта не резолвилась» — '
    'и другого смысла у этой пустоты нет '
    '(docs/rules/absent-value-semantics.md)';
comment on column deal_aggregates.bucket_date is
    'Сутки UTC зерна';
comment on column deal_aggregates.risk_bearing_deals is
    'Из закрытых — принявшие риск: сделка, закрытая без входа, сюда не '
    'входит. Популяция долей — она, а не closed_deals';
comment on column deal_aggregates.r_denominator_deals is
    'Сколько сделок вошло в r_sum — вторая половина среднего R: без неё '
    'сумма ни на что не делится';
comment on column deal_aggregates.planned_risk_excluded_sum is
    'Плановый риск сделок, ВЫВЕДЕННЫХ из денежных сумм; считается на строке '
    'известной валюты';
comment on column deal_aggregates.assembled_at is
    'Момент сборки строки: показание читателю, а не операнд выбора '
    'пересчитываемого';

-- Под единственного планового читателя — отбор по тенанту и суткам.
create index ix_deal_aggregate_tenant_bucket on deal_aggregates (tenant_id, bucket_date desc);

-- ---------------------------------------------------------------------
-- Зерно происшествий
-- ---------------------------------------------------------------------

-- Ключ зерна — «тенант × биржевой счёт × сутки UTC»: пустых колонок в нём
-- нет ни одной, поэтому и клауза `nulls not distinct` здесь не нужна.
-- Суррогатный первичный ключ оставлен ради единой формы строки с соседней
-- таблицей.
--
-- Каждый счётчик называет, ЧТО он считает: отбор строк журнала по классу
-- события считает СОБЫТИЯ, а не их предметы, и счётчика остановленных
-- сделок среди них поэтому нет — ему нужен отбор по различным сделкам
-- (docs/rules/statistics-aggregates.md §«Счётчики происшествий — своё
-- зерно, а не строка сделочного агрегата»).
create table incident_aggregates (
    id                           bigserial   primary key,
    tenant_id                    varchar(64) not null,
    exchange_account_internal_id varchar(64) not null,
    bucket_date                  date        not null,

    opened_deals                 integer     not null,
    order_decisions              integer     not null,
    raised_holds                 integer     not null,
    hard_raised_holds            integer     not null,
    manually_raised_holds        integer     not null,
    anomaly_reports              integer     not null,
    critical_anomaly_reports     integer     not null,
    manual_operation_reports     integer     not null,

    assembled_at                 timestamptz not null,
    constraint uk_incident_aggregate_grain unique
        (tenant_id, exchange_account_internal_id, bucket_date)
);

comment on table incident_aggregates is
    'Зерно происшествий: суточные счётчики по классам событий '
    '(docs/rules/statistics-aggregates.md).';
comment on column incident_aggregates.order_decisions is
    'Решения о создании ОБЫЧНОЙ заявки: решение об отдельной условной '
    'заявке классом события не выражено вовсе. Перестановка ноги даёт своё '
    'решение (docs/rules/replace-not-amend.md), и число несёт её тоже';
comment on column incident_aggregates.manually_raised_holds is
    'Из поднятых ступеней — поднятые РУЧНОЙ ТРОПОЙ. Тропу различает КОД '
    'операции, а не актор строки: у джобы, запущенной ручным триггером, '
    'актор равен принципалу';
comment on column incident_aggregates.manual_operation_reports is
    'Из отчётов о происшествии — заведённые ручной тропой, а не детекцией: '
    'ручная поверхность заводит отчёт и на постановке ступени, и на снятии';
comment on column incident_aggregates.assembled_at is
    'Момент сборки строки';

-- Под того же планового читателя, что и у сделочного зерна.
create index ix_incident_aggregate_tenant_bucket on incident_aggregates (tenant_id, bucket_date desc);
