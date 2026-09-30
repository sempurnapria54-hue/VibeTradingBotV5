-- Базовая схема сервиса auth: тенанты, членства, реестр биржевых счетов.
--
-- Своя цепочка миграций с V1: схема донора — история, и переиспользованию
-- не подлежит (.claude/rules/pre-launch-schema-changes.md).
--
-- Ключей биржевых счетов здесь нет ни в каком виде: они в Vault по пути
-- <окружение>/exchange-accounts/<internal_id>
-- (docs/architecture/platform.md §Безопасность).

create table tenants (
    id            bigserial primary key,
    internal_id   varchar(64)  not null,
    name          varchar(256) not null,
    status        varchar(32)  not null,
    created_at    timestamptz  not null,
    created_by    varchar(64),
    modified_at   timestamptz,
    modified_by   varchar(64),
    constraint uk_tenant_internal_id unique (internal_id)
);

comment on table tenants is 'Тенант — единица владения капиталом, ключами и риск-аппетитом';
comment on column tenants.status is 'ACTIVE | SUSPENDED; при SUSPENDED набор риска прекращается, живые сделки сопровождаются до терминала';

create table memberships (
    id            bigserial primary key,
    internal_id   varchar(64)  not null,
    user_id       varchar(128) not null,
    tenant_id     varchar(64)  not null,
    role          varchar(32)  not null,
    created_at    timestamptz  not null,
    created_by    varchar(64),
    modified_at   timestamptz,
    modified_by   varchar(64),
    constraint uk_membership_internal_id unique (internal_id),
    constraint uk_membership_user_tenant unique (user_id, tenant_id),
    constraint fk_membership_tenant foreign key (tenant_id) references tenants (internal_id)
);

comment on table memberships is 'Членство: пользователь x тенант x роль';
comment on column memberships.role is 'OWNER | TRADER | VIEWER; при одном субъекте всегда OWNER, различает со второго субъекта (фаза 5)';

-- Инвариант «у тенанта ровно одно членство с ролью OWNER»: тенанта без
-- владельца не бывает — отвечать за потерю было бы некому. Выражен
-- ЧАСТИЧНЫМ уникальным индексом, а не колонкой на тенанте: колонка была бы
-- вторым носителем той же истины и разошлась бы с членством при первой же
-- передаче владения (docs/models/domain/core/Tenant.md).
--
-- Индекс даёт единственность, но не существование: «хотя бы один OWNER»
-- ограничением схемы не выражается — его держит исполнитель заведения
-- тенанта, создающий тенанта и его членство одной транзакцией.
create unique index uk_membership_single_owner
    on memberships (tenant_id)
    where role = 'OWNER';

create table exchange_accounts (
    id            bigserial primary key,
    internal_id   varchar(64)  not null,
    tenant_id     varchar(64)  not null,
    exchange_code varchar(32)  not null,
    label         varchar(128) not null,
    contour       varchar(16)  not null,
    status        varchar(32)  not null,
    created_at    timestamptz  not null,
    created_by    varchar(64),
    modified_at   timestamptz,
    modified_by   varchar(64),
    constraint uk_exchange_account_internal_id unique (internal_id),
    constraint uk_exchange_account_label unique (tenant_id, exchange_code, label),
    constraint fk_exchange_account_tenant foreign key (tenant_id) references tenants (internal_id)
);

comment on table exchange_accounts is 'Реестровая часть биржевого счёта тенанта; ключи — в Vault, торговое состояние — в базе trading_core';
comment on column exchange_accounts.contour is 'LIVE | DEMO; допустимость в окружении проверяется при регистрации (docs/spec/environment-contour.json, contourAdmitted)';
