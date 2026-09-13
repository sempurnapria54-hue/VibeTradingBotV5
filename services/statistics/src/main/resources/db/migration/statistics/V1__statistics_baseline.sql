-- Базовая схема базы `statistics` — фактов статистики и агрегатов над ними
-- (docs/architecture/data-ownership.md §Раскладка).
--
-- ЦЕПОЧКА ОДНА: владелец данных у сервиса один. Грантов она не выдаёт —
-- чужих читателей у этой базы нет ни одного, и чужого подключения сам
-- сервис не открывает (.claude/decisions/audit-statistics-split.md).
--
-- ЧЕТЫРЕ ПРЕДМЕТА: факты двух зёрен, агрегаты тех же двух зёрен, состояние
-- приёма своей группы и след отказа доступа к своей поверхности.
--
-- ФАКТ — СТРОКА-ПРЕДШЕСТВЕННИК строки агрегата: те же ключи и те же
-- операнды, но зерном ему служит одно принятое событие, а не сутки
-- (docs/models/domain/other/StatisticsFact.md). Агрегат получается из
-- фактов свёрткой; фактов из агрегата не получается, и потому факт —
-- единственный вход пересчёта.
--
-- СОДЕРЖИМОГО СОБЫТИЯ КАК ДОСТАВЛЕНО ЗДЕСЬ НЕТ НИ В ОДНОЙ ТАБЛИЦЕ, и это
-- несущий инвариант: колонка с ним сделала бы факт журналом, а у одной
-- истории стало бы два дома (.claude/rules/policy-home.md).
--
-- ВРЕМЯ — UTC (timestamptz; сутки зерна — date, docs/rules/time-utc.md).
-- Денежные величины — numeric(36,18). Внешних ключей нет: идентичности
-- принадлежат чужим сервисам и приезжают содержимым события.

-- ---------------------------------------------------------------------
-- Факты: сделочное зерно
-- ---------------------------------------------------------------------

-- ХРАНЯТСЯ ТОЛЬКО ОПЕРАНДЫ, а не выводы из них: ценовой результат, класс
-- результата и R выводятся при свёртке и вторым носителем не заводятся
-- (docs/concept.md, П4).
--
-- ПУСТОТА ОПЕРАНДА — ЗНАЧЕНИЕ, а не пробел: «валюта не резолвилась» и
-- «результат недоступен» суть исходы, которые свёртка считает своими
-- счётчиками (docs/rules/absent-value-semantics.md). Поэтому колонки мер
-- объявлены nullable, а `not null` стои́т ровно на ключах и оси времени.
--
-- УНИКАЛЬНОСТЬ event_id — ОТМЕТКА ОБРАБОТАННОГО: следствие приёма одно, и
-- его ключ ею и служит; отдельной таблицы inbox поэтому нет
-- (docs/models/domain/other/StatisticsFact.md §«Отметка обработанного»).
create table deal_facts (
    id                           bigserial       primary key,
    event_id                     varchar(64)     not null,
    tenant_id                    varchar(64)     not null,
    exchange_account_internal_id varchar(64)     not null,
    strategy_internal_id         varchar(64),
    result_currency              varchar(16),
    closed_at                    timestamptz     not null,

    took_risk                    boolean,
    graph_complete               boolean,
    net_result                   numeric(36, 18),
    fee                          numeric(36, 18),
    funding                      numeric(36, 18),
    liquidation_penalty          numeric(36, 18),
    planned_risk                 numeric(36, 18),
    close_outcome                varchar(32),
    reconciliation_status        varchar(32),
    breakdown_incomplete         varchar(32),
    risk_benchmark_availability  varchar(32),

    constraint uk_deal_fact_event unique (event_id, closed_at)
);

comment on table deal_facts is
    'Сделочный факт: строка-предшественник строки сделочного агрегата, '
    'зерном которой служит одно принятое событие '
    '(docs/models/domain/other/StatisticsFact.md).';
comment on column deal_facts.event_id is
    'Идентичность принятого события; она же ОТМЕТКА ОБРАБОТАННОГО';
comment on column deal_facts.closed_at is
    'Момент терминала сделки. ОСЬ ВРЕМЕНИ таблицы: по ней же считаются '
    'сутки зерна при свёртке, и по ней же гипертаблица режет куски';
comment on column deal_facts.result_currency is
    'Расчётная валюта результата; пусто означает «валюта не резолвилась» — '
    'и другого смысла у этой пустоты нет';
comment on column deal_facts.took_risk is
    'Сделка приняла риск: популяция долей — она, а не все закрытые';
comment on column deal_facts.graph_complete is
    'Граф сделки полон: конъюнкт доступности ценового результата. Без него '
    'второе слагаемое на усечённой загрузке выходит нулём молча';

-- ГИПЕРТАБЛИЦА ПО ОСИ ВРЕМЕНИ СВОЕГО ЗЕРНА. Ось выбрана не по жанру
-- строки, а по ЧИТАТЕЛЮ: пересчёт отбирает окно суток зерна, и сутки зерна
-- считаются по этой же оси. Второй оси времени у факта нет — момент приёма
-- не хранится, потому что его не читает ни одна выборка.
--
-- ОСЬ ВРЕМЕНИ ВХОДИТ В УНИКАЛЬНОЕ ОГРАНИЧЕНИЕ ВТОРОЙ КОЛОНКОЙ, и это
-- требование Timescale, а не выбор: всякое уникальное ограничение
-- гипертаблицы обязано включать колонку разбиения. Дедуп от этого не
-- слабеет — event_id уникален по построению, и пара с моментом остаётся
-- уникальной ровно тогда же.
select create_hypertable('deal_facts', by_range('closed_at'));

-- ---------------------------------------------------------------------
-- Факты: зерно происшествий
-- ---------------------------------------------------------------------

-- КЛЮЧ ЗЕРНА У́ЖЕ СДЕЛОЧНОГО НА ДВЕ КОЛОНКИ, и это не экономия: расчётной
-- валюты не несёт ни одно из несомых событий, а определения стратегии нет
-- у части из них по построению радиуса — введённое ради остальных, оно
-- дало бы пустой ключ со вторым смыслом.
--
-- КОЛОНОК ИДЕНТИЧНОСТИ РАДИУСА (сделка, инструмент, определение) здесь нет,
-- и это решение: ни один объявленный счётчик по ним не отбирает, а колонка
-- без читателя была бы формой раньше предмета
-- (.claude/rules/design-simplicity.md).
create table incident_facts (
    id                           bigserial    primary key,
    event_id                     varchar(64)  not null,
    tenant_id                    varchar(64)  not null,
    exchange_account_internal_id varchar(64)  not null,
    occurred_at                  timestamptz  not null,
    event_type                   varchar(64)  not null,

    hold_rung                    varchar(32),
    anomaly_severity             varchar(32),
    operation_code               varchar(64),

    constraint uk_incident_fact_event unique (event_id, occurred_at)
);

comment on table incident_facts is
    'Факт происшествия: строка-предшественник строки агрегата происшествий '
    '(docs/models/domain/other/StatisticsFact.md).';
comment on column incident_facts.occurred_at is
    'Момент происшествия из конверта. ОСЬ ВРЕМЕНИ таблицы';
comment on column incident_facts.event_type is
    'Класс события — по нему счётчик отбирает свои строки';
comment on column incident_facts.hold_rung is
    'Жёсткость поднятой ступени; пусто = разрез у этого класса не определён';
comment on column incident_facts.anomaly_severity is
    'Критичность отчёта; пусто = разрез у этого класса не определён';
comment on column incident_facts.operation_code is
    'Код операции: им и только им различается ручная тропа. Актор строки '
    'для этого не годится — у джобы, запущенной ручным триггером, он равен '
    'принципалу';

select create_hypertable('incident_facts', by_range('occurred_at'));

-- ОТДЕЛЬНЫХ ИНДЕКСОВ У ФАКТОВ НЕТ, и это не пропуск: единственный плановый
-- читатель обоих рядов — посуточная свёртка пересчёта, отбирающая сутки ПО
-- ВСЕМ тенантам сразу. Индекс с ведущим тенантом такой скан не
-- обслуживает, а оси времени гипертаблицы ему достаточно: куски режутся
-- ровно по ней. Поверхности чтения строк у фактов нет вовсе.

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
    'Сделочное зерно агрегатов: производная проекция СВОИХ ФАКТОВ, '
    'восполнимая повторным пересчётом (docs/rules/statistics-aggregates.md).';
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

-- ---------------------------------------------------------------------
-- Состояние приёма по паре «группа × тема»
-- ---------------------------------------------------------------------

-- Ключ — ПАРА, а не одна группа: остановка приёма случается на сообщении,
-- то есть на конкретной теме, и строка на группу целиком склеивала бы
-- состояния разных производителей
-- (docs/rules/durable-consumer-reception.md).
--
-- Писателей два, и оба лежат в модуле приёма: состав пар, признак подписки
-- и момент обновления пишет тик ReceptionStateJob, величины самого приёма —
-- слушатель и его обработчик отказа. Инвариант «один пишущий» это не
-- задевает: он о втором СЕРВИСЕ, а не о втором классе одного модуля.
--
-- Истории состояний таблица не хранит: предмет строки — текущее состояние,
-- а не происшествие.
create table reception_states (
    id                        bigserial    primary key,
    consumer_group            varchar(128) not null,
    topic                     varchar(128) not null,
    observed_since            timestamptz  not null,
    subscribed                boolean      not null,
    reception_halted          boolean      not null,
    lag_gap_at                timestamptz,
    last_accepted_occurred_at timestamptz,
    updated_at                timestamptz  not null,
    constraint uk_reception_state_pair unique (consumer_group, topic)
);

comment on table reception_states is
    'Текущее состояние приёма по паре «группа потребителей × тема»: чем '
    'статистика отвечает, с какого момента её числа полны и утверждаема ли '
    'непрерывность (docs/rules/durable-consumer-reception.md).';
comment on column reception_states.observed_since is
    'Момент, с которого группа наблюдает тему НЕПРЕРЫВНО; двигается только '
    'вперёд';
comment on column reception_states.subscribed is
    'Тема сейчас в подписке группы. Строка ушедшей темы не удаляется: '
    'удаление опустило бы нижнюю границу полноты, а её факты из базы не '
    'исчезают';
comment on column reception_states.reception_halted is
    'Приём по паре остановлен. При заведении строки — ложь: остановка есть '
    'исход отказа, а не начальное состояние';
comment on column reception_states.lag_gap_at is
    'Момент обнаружения разрыва по сроку хранения; пусто = разрыва не было. '
    'ГАСЯЩЕГО ПИСАТЕЛЯ У НЕГО ЗДЕСЬ НЕТ: гасит разрыв чистка следствий, а '
    'чистки у фактов не существует — дыра в принятом безвозвратна '
    '(docs/models/domain/other/StatisticsFact.md)';
comment on column reception_states.last_accepted_occurred_at is
    'Момент происшествия последнего принятого события пары; пусто = не '
    'принято ещё ничего';
comment on column reception_states.updated_at is
    'Пишется КАЖДЫМ тактом тика, независимо от того, было ли что принимать: '
    'величина, обновляемая только при работе, не отличала бы «всё спокойно» '
    'от «потребителя нет»';

-- ---------------------------------------------------------------------
-- След отвергнутого по правам вызова
-- ---------------------------------------------------------------------

-- Строку заводит сервис, у которого отказ произошёл И есть своя база
-- (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого есть
-- база»): отказ доступа есть факт наблюдаемости, а не число отчёта.
--
-- Природа факта — происшествие: у каждой попытки свой момент, дедупа нет
-- по построению, и схлопывание попыток занижало бы ровно ту частоту, ради
-- которой строка и заводится.
create table access_denials (
    id                   bigserial    primary key,
    internal_id          varchar(64)  not null,
    surface              varchar(256) not null,
    outcome              varchar(32)  not null,
    principal            varchar(64),

    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    -- Биржевые поля остаются пустыми навсегда: у отвергнутого вызова
    -- биржевого домена нет вовсе. Набор колонок аудита бинарен — либо все
    -- шесть, либо ни одной (docs/models/domain/other/Auditable.md).
    external_created_at  timestamptz,
    external_modified_at timestamptz,
    constraint uk_access_denial_internal_id unique (internal_id)
);

comment on table access_denials is
    'Отвергнутые по правам вызовы к поверхности контура: происшествие, '
    'каждая попытка — своя строка (docs/models/domain/other/AccessDenial.md).';
comment on column access_denials.surface is
    'Куда стучались: HTTP-метод и путь. Значение приходит от вызывающего, '
    'поэтому писатель его усекает — иначе длинный путь ронял бы запись, то '
    'есть отказ стирал бы собственный след';
comment on column access_denials.outcome is
    'Класс отказа: PRINCIPAL_ABSENT | OPERATION_FORBIDDEN';
comment on column access_denials.principal is
    'ПРИНЯТЫЙ принципал; пусто ⟺ outcome = PRINCIPAL_ABSENT. Заявленное, но '
    'не удостоверенное имя сюда не пишется';

-- Единственный читатель — разбор человеком по времени создания; индекса под
-- ключ дедупа нет и не заводится.
create index ix_access_denial_created_at on access_denials (created_at desc);

-- ---------------------------------------------------------------------
-- Грантов эта цепочка не выдаёт
-- ---------------------------------------------------------------------

-- ЧЕМ ОБЕСПЕЧЕН ИНВАРИАНТ «ОДИН ПИШУЩИЙ». Владелец данных у процесса один,
-- и писателя отделяет от чужих таблиц то, что чужих учётных данных у него
-- нет вовсе (docs/architecture/data-ownership.md §Раскладка). Читателей
-- извне у этой базы нет ни одного: журнал её не читает, а периметр ходит
-- поверхностью сервиса, а не подключением.
