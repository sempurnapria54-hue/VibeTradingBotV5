-- Расщепление таблицы строк исполнения на две (шаг 7 фазы 1).
--
-- Стратегийные и системные исполнения хранятся отдельно, и вид действия
-- кодируется ТАБЛИЦЕЙ, а не nullable-колонкой рода: вместе с колонкой
-- исчезает и неоднозначность ключа. В стратегийной таблице ссылка на узел
-- стратегии обязательна, в системной — тип системного действия.
-- Схема — docs/models/domain/other/DealActionState.md §Инварианты.
--
-- Проект до запуска: таблицы пусты, бэкфилл не требуется, обязательные
-- колонки вводятся ALTER'ом напрямую
-- (.claude/rules/pre-launch-schema-changes.md).

-- Идентичность строки исполнения — ОДНА на оба вида: анкер команды
-- (ServiceCommand.dealActionStateId) однозначен одним числом, и второго
-- поля-дискриминатора в команде не заводится.
create sequence deal_action_state_seq;

-- ── Стратегийные исполнения ──────────────────────────────────────────────
alter table deal_action_states rename to deal_strategy_action_states;

alter index uk_deal_action_state_tranche_live rename to uk_deal_strategy_action_state_tranche_live;
alter index uk_deal_action_state_deal_level_live rename to uk_deal_strategy_action_state_deal_level_live;
alter index ix_deal_action_state_deal rename to ix_deal_strategy_action_state_deal;
alter table deal_strategy_action_states
    rename constraint fk_deal_action_state_strategy_action to fk_deal_strategy_action_state_action;
alter table deal_strategy_action_states
    rename constraint fk_deal_action_state_deal_tranche to fk_deal_strategy_action_state_tranche;
alter index deal_action_states_pkey rename to deal_strategy_action_states_pkey;

alter table deal_strategy_action_states alter column id drop identity;
alter table deal_strategy_action_states alter column id set default nextval('deal_action_state_seq');

-- Цель — две скалярные колонки вместо JSONB: тип цели входит в ключ
-- уникальности, а JSONB индексом ключа не служит.
alter table deal_strategy_action_states add column target_entity_type varchar(64);
alter table deal_strategy_action_states add column target_entity_id bigint;
alter table deal_strategy_action_states drop column target;

alter table deal_strategy_action_states add column created_at timestamptz;
alter table deal_strategy_action_states add column created_by varchar(64);
alter table deal_strategy_action_states add column modified_at timestamptz;
alter table deal_strategy_action_states add column modified_by varchar(64);
alter table deal_strategy_action_states add column external_created_at timestamptz;
alter table deal_strategy_action_states add column external_modified_at timestamptz;

-- ── Системные исполнения ─────────────────────────────────────────────────
-- Транш колонку несёт: системное действие бывает потраншевым
-- (консолидация входа транша). Target-колонок нет — цель системного
-- действия ключом уникальности не является.
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

-- Ключи ЧАСТИЧНЫЕ, по живым статусам, и форм две: NULL в уникальном
-- индексе Postgres сам с собой НЕ конфликтует, поэтому индекс по трём
-- колонкам пропустил бы два живых исполнения одного агрегатного действия.
-- Транш входит в ключ ровно настолько, насколько действие потраншевое:
-- N траншей сетки, консолидирующих вход одновременно, дают N законных
-- живых исполнений одного типа.
create unique index uk_deal_system_action_state_tranche_live
    on deal_system_action_states (deal_id, deal_tranche_id, tranche_episode_seq, system_action_type)
    where deal_tranche_id is not null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

create unique index uk_deal_system_action_state_deal_level_live
    on deal_system_action_states (deal_id, system_action_type)
    where deal_tranche_id is null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

create index ix_deal_system_action_state_deal on deal_system_action_states (deal_id);

-- Прежние ключи стратегийной таблицы перечисляли снятое значение статуса
-- CANCELED; действующий перечень терминальных — COMPLETED / FAILED /
-- SKIPPED (docs/lifecycles/DealActionState.md).
drop index uk_deal_strategy_action_state_tranche_live;
drop index uk_deal_strategy_action_state_deal_level_live;

create unique index uk_deal_strategy_action_state_tranche_live
    on deal_strategy_action_states (deal_id, deal_tranche_id, tranche_episode_seq, strategy_action_id)
    where deal_tranche_id is not null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

create unique index uk_deal_strategy_action_state_deal_level_live
    on deal_strategy_action_states (deal_id, strategy_action_id)
    where deal_tranche_id is null
      and status not in ('COMPLETED', 'FAILED', 'SKIPPED');

-- ── Финализационный стек снят ────────────────────────────────────────────
-- Отдельного анкера под финализацию больше нет: системное действие несёт
-- свою строку наравне со стратегийным. Строки не переносятся — таблица
-- пуста по пре-лонч политике.
delete from deal_finalization_states;
drop table deal_finalization_states;
