-- След отвергнутого по правам вызова у сервиса `strategies`.
--
-- Строку заводит сервис, у которого отказ произошёл И есть своя база
-- (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого есть
-- база»); у `strategies` база своя, и отказ без строки не наблюдался бы ничем,
-- кроме лога, — а лог носителем наблюдаемости не является
-- (docs/models/domain/other/AccessDenial.md §Назначение).
--
-- Форма таблицы одна у всех писателей: сущность лежит в общем артефакте и
-- отображается каждым сервисом с базой
-- (docs/models/domain/other/AccessDenial.md §Персистентность).
--
-- Природа факта — происшествие: у каждой попытки свой момент, дедупа нет
-- по построению, и схлопывание попыток занижало бы ровно ту частоту, ради
-- которой строка и заводится.
--
-- Проект до прод-рубежа: таблиц, которые пришлось бы заполнять, нет
-- (.claude/rules/pre-launch-schema-changes.md).

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
