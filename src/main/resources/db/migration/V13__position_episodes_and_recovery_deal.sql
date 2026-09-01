-- Эпизоды позиции и восстановительная тропа сделки (кодовый заход 5 шага 7).
--
-- Строка positions становится строкой ЭПИЗОДА: живой не более одного,
-- закрытые остаются. Адресуемая единица эпизода — ПАРА «биржевой
-- идентификатор, биржевое время создания»: источник переиспользует
-- posId у переоткрытой позиции, поэтому прежний ключ «одна позиция на
-- сделку» отвергал бы легитимный второй эпизод
-- (docs/models/domain/core/Position.md §Персистентность).
--
-- На сделке появляются: фаза рынка при входе, порог доказанного
-- покрытия (наблюдатель — нога 2 добычи позиции) и допущение пустой
-- детали у восстановительной тропы — с биекцией «пусто ⟺ RECOVERY»
-- ограничением, а не соглашением (docs/models/domain/aggregate/Deal.md
-- §Персистентность).
--
-- Бэкфилла нет: до конца фазы 1 таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

alter table positions
    add column external_realized_profit       numeric(36, 18),
    add column external_result_currency       varchar(16),
    add column external_close_average_price   numeric(36, 18),
    add column external_close_type            varchar(64),
    add column external_realized_profit_gross numeric(36, 18),
    add column external_fee                   numeric(36, 18),
    add column external_funding_cost          numeric(36, 18),
    add column external_liquidation_penalty   numeric(36, 18);

alter table positions drop constraint uk_position_deal;

create unique index uk_position_deal_external
    on positions (deal_id, external_id, external_created_at)
    where external_id is not null;

alter table deals
    add column entry_market_phase      varchar(64),
    add column coverage_proven_through timestamptz;

alter table deals alter column strategy_detail_id drop not null;

alter table deals
    add constraint ck_deal_detail_matches_entry_reason
        check ((strategy_detail_id is null) = (entry_reason = 'RECOVERY'));
