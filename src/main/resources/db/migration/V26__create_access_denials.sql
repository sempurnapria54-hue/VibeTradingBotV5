-- Журнальная строка отвергнутого по правам вызова к нашей поверхности.
--
-- Носитель наблюдаемости (docs/models/domain/other/AccessDenial.md): по нему
-- держатель видит интерес к поверхности. Лог этой роли не исполняет — он не
-- запрашивается, не агрегируется и не переживает ротацию.
--
-- Отчётом об аномалии факт не выражается: у отчёта exchange_id обязателен, а
-- у отвергнутого вызова биржи нет вовсе, и расхождения с биржевым состоянием
-- не происходит.
create table access_denials
(
    id                   bigserial primary key,
    internal_id          varchar(64)  not null,
    -- Поверхность, к которой обратились: HTTP-метод и путь.
    surface              varchar(256) not null,
    -- Класс отказа: PRINCIPAL_ABSENT | OPERATION_FORBIDDEN. Енум строкой,
    -- как и у прочих моделей (codestyle §«Слои моделей и enum'ы»).
    outcome              varchar(32)  not null,
    -- ПРИНЯТЫЙ принципал; пусто ⟺ outcome = PRINCIPAL_ABSENT. Заявленное, но
    -- не удостоверенное имя сюда не пишется — это была бы запись
    -- непроверенного как факта.
    principal            varchar(64),

    created_at           timestamptz,
    created_by           varchar(64),
    modified_at          timestamptz,
    modified_by          varchar(64),
    -- Биржевые поля остаются пустыми навсегда: у отвергнутого вызова
    -- биржевого домена нет вовсе. Набор колонок аудита бинарен — либо все
    -- шесть, либо ни одной (docs/models/domain/other/Auditable.md).
    external_created_at  timestamptz,
    external_modified_at timestamptz
);

create unique index uk_access_denial_internal_id on access_denials (internal_id);

-- Индекса под дедуп нет и не заводится: природа факта — происшествие, дедупа
-- у него нет по построению. Единственный читатель — разбор человеком по
-- времени создания.
create index ix_access_denial_created_at on access_denials (created_at desc);

comment on table access_denials is
    'Отвергнутые по правам вызовы к поверхности контура: происшествие, '
    'каждая попытка — своя строка (docs/models/domain/other/AccessDenial.md).';
