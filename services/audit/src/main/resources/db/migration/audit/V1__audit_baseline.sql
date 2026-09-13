-- Базовая схема базы `audit` — журнала событий по тенанту, строки состояния
-- приёма по паре «группа × тема» и следа отвергнутого по правам вызова
-- (docs/architecture/data-ownership.md §Раскладка).
--
-- ЦЕПОЧКА СВОЯ, С V1, И ОНА НЕ ЕДИНСТВЕННАЯ У СЕРВИСА. У процесса два
-- владельца данных и две базы, поэтому и цепочек миграций две: эта лежит в
-- db/migration/audit и идёт под ролью-владельцем журнала, соседняя
-- (db/migration/statistics) — под ролью-владельцем агрегатов. Обязательные
-- колонки вводятся сразу целевой формой: существующих строк нет
-- (.claude/rules/pre-launch-schema-changes.md).
--
-- ВРЕМЯ — UTC (timestamptz, docs/rules/time-utc.md). Енумы хранятся строкой
-- значением name() (.claude/rules/codestyle.md §«Слои моделей и enum'ы»).
-- Имена таблиц — во множественном числе.
--
-- ВНЕШНИХ КЛЮЧЕЙ НЕТ НИ ОДНОГО, и это не пропуск: идентичности, которые
-- несут строки журнала, принадлежат чужим сервисам (тенант — auth, сделка и
-- определение стратегии — trading-core), а ссылки через границу сервиса не
-- бывает (docs/architecture/data-ownership.md). Целостность здесь держит
-- производитель события: значения приезжают содержимым, а не резолвятся
-- запросом.
create table audit_records (
    id                           bigserial primary key,
    event_id                     varchar(64)  not null,
    tenant_id                    varchar(64)  not null,
    event_type                   varchar(128) not null,
    occurred_at                  timestamptz  not null,
    recorded_at                  timestamptz  not null,
    version                      integer      not null,
    trace_context                varchar(256),
    exchange_account_internal_id varchar(64),
    instrument_internal_id       varchar(64),
    deal_internal_id             varchar(64),
    strategy_internal_id         varchar(64),
    content                      jsonb        not null,
    -- Он же дедуп доставки: строка журнала И ЕСТЬ отметка «событие
    -- обработано», и ложится она той же транзакцией, что и следствие
    -- (docs/rules/idempotency-via-unique.md). Отдельной таблицы inbox у
    -- этого потребителя поэтому нет — она была бы вторым носителем той же
    -- истины.
    constraint uk_audit_record_event_id unique (event_id)
);

comment on table audit_records is
    'Журнал событий по тенанту: строка на каждое принятое сообщение '
    '(docs/models/domain/other/AuditRecord.md).';
comment on column audit_records.event_id is
    'Идентификатор события из конверта; он же ключ дедупа доставки';
comment on column audit_records.occurred_at is
    'Момент происшествия — из конверта, от производителя';
comment on column audit_records.recorded_at is
    'Момент приёма журналом; первый операнд нижней границы полноты';
comment on column audit_records.event_type is
    'Класс события строкой. Ширина шире конвенции: её назначил производитель '
    '(docs/models/domain/other/AuditRecord.md, о строковых колонках)';
comment on column audit_records.content is
    'Содержимое события навесом: реляционно не раскладывается, операнды '
    'ключа и радиусы отбора вынесены колонками '
    '(docs/rules/persistence-representation.md)';
comment on column audit_records.deal_internal_id is
    'Радиус отбора: пусто, когда содержимое идентичности не несёт '
    '(docs/rules/absent-value-semantics.md)';

-- ИНДЕКСОВ ПЯТЬ, и у каждого назван читатель
-- (docs/models/domain/other/AuditRecord.md §Персистентность). Лишних здесь
-- не бывает дважды: журнал только пишется, а в prod не чистится вовсе —
-- каждый индекс оплачивается каждой вставкой и растёт без предела.

-- Под пересчёт проекции: он отбирает окно суток ПО ВСЕМ тенантам сразу, и
-- ведущая колонка tenant_id такой скан не обслуживает.
--
-- ИНДЕКСА ПО ПАРЕ (event_type, occurred_at) ЗДЕСЬ НЕТ, и это снятие вместе
-- с предметом: его единственным читателем была группировка пересчёта
-- агрегатов, ходившая в эту базу чужим подключением. Статистика уехала
-- своим сервисом и считает по СВОИМ фактам
-- (.claude/decisions/audit-statistics-split.md); индекс без читателя стоил
-- бы записи на каждой вставке журнала и не обслуживал бы ни одной выборки.
-- Отбор журнала человеком: свой тенант в обратном хронологическом порядке.
create index ix_audit_record_tenant_occurred on audit_records (tenant_id, occurred_at desc);

-- Необязательные отборы журнальной выборки по радиусу. ЧАСТИЧНЫЕ: пустое
-- значение колонки радиуса означает «содержимое идентичности не несёт», и
-- строк с пустым значением в этих индексах не бывает по построению отбора.
create index ix_audit_record_tenant_deal on audit_records (tenant_id, deal_internal_id)
    where deal_internal_id is not null;
create index ix_audit_record_tenant_strategy on audit_records (tenant_id, strategy_internal_id)
    where strategy_internal_id is not null;

-- Под первый операнд нижней границы полноты: граница едет с КАЖДОЙ выдачей
-- чисел ЖУРНАЛЬНОЙ выборки — того потребителя, чьи числа выдаются
-- (docs/rules/durable-consumer-reception.md). На строки агрегатов статистики
-- обязательство отсюда не распространяется: у неё своя группа, своя строка
-- состояния и свои две величины.
create index ix_audit_record_recorded on audit_records (recorded_at);

-- ---------------------------------------------------------------------
-- Состояние приёма по паре «группа × тема»
-- ---------------------------------------------------------------------

-- Ключ — ПАРА, а не одна группа: остановка приёма случается на сообщении,
-- то есть на конкретной теме, и строка на группу целиком склеивала бы
-- состояния разных производителей
-- (docs/models/domain/other/AuditRecord.md §«Строка состояния приёма —
-- таблица reception_states»).
--
-- Писателей два, и оба лежат в модуле приёма: состав пар, признак подписки
-- и момент обновления пишет тик ReceptionStateJob, величины самого приёма —
-- слушатель и его обработчик ошибок. Инвариант «один пишущий» это не
-- задевает: он о втором СЕРВИСЕ, а не о втором классе одного модуля.
--
-- Истории состояний таблица не хранит: предмет строки — текущее состояние,
-- а не происшествие; историю несёт сам журнал.
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
    'журнал отвечает, с какого момента он полон и утверждаема ли '
    'непрерывность (docs/models/domain/other/AuditRecord.md).';
comment on column reception_states.observed_since is
    'Момент, с которого группа наблюдает тему НЕПРЕРЫВНО; двигается только '
    'вперёд';
comment on column reception_states.subscribed is
    'Тема сейчас в подписке группы. Строка ушедшей темы не удаляется: '
    'удаление опустило бы нижнюю границу полноты, а её строки из журнала не '
    'исчезают';
comment on column reception_states.reception_halted is
    'Приём по паре остановлен. При заведении строки — ложь: остановка есть '
    'исход отказа, а не начальное состояние';
comment on column reception_states.lag_gap_at is
    'Момент обнаружения разрыва по сроку хранения; пусто = разрыва не было. '
    'Гасит его чистка непроизводственного окружения, когда разрыв уходит за '
    'нижнюю границу';
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

-- Лежит в базе ЖУРНАЛА, не агрегатов, и адрес назван явно
-- (docs/models/domain/other/AccessDenial.md §Персистентность): отказ
-- доступа есть факт наблюдаемости, а не число отчёта.
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
-- нет вовсе — та же форма, что у всякого сервиса с одним владельцем
-- (docs/architecture/data-ownership.md §Раскладка).
--
-- ПРЕЖДЕ ЗДЕСЬ СТОЯЛ ГРАНТ ЧТЕНИЯ роли статистики вместе с назначением прав
-- по умолчанию: у процесса было два владельца, и грант восстанавливал
-- отделение, исчезавшее по построению. Раздел сервиса снял и второго
-- владельца, и единственного читателя гранта — пересчёт агрегатов считает
-- по собственным фактам своей базы
-- (.claude/decisions/audit-statistics-split.md).
