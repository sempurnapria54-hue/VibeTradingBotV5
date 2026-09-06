-- Приведение дерева стратегии и аудита транша к целевой форме (шаг 7 фазы 2,
-- компонент «порт копии определения стратегии»).
--
-- Базовая миграция ядра собиралась из донора, и область стратегии попала в
-- неё в форме, которую донор оставил позади четырьмя своими миграциями:
-- phase_rules вместо params (V3), снятие контейнерных JSONB-настроек (V5),
-- четвёрка риск-чисел детали (V15), уровень объявления шага и снятие level
-- у действия (V20). Расхождение поймано при кодировании читателя дерева:
-- док (docs/models/domain/aggregate/Strategy.md §StrategyDetail,
-- §Персистентность) описывает целевую форму, а колонок под неё не было.
--
-- Правится НОВОЙ миграцией, а не редакцией V1: базовая уже накатана на живую
-- базу стенда, и её контрольная сумма неизменяема
-- (.claude/rules/codestyle.md §«Применённая миграция неизменяема»).
--
-- Проект до прод-рубежа: таблицы пусты, бэкфилл не требуется, обязательные
-- колонки вводятся напрямую (.claude/rules/pre-launch-schema-changes.md).

-- ---------------------------------------------------------------------
-- 1. Настройка фазы: клаузы вместо параметров, настроек на контейнере нет
-- ---------------------------------------------------------------------
--
-- Фаза вычисляется на лету и своих часов не имеет: таймфрейм и срок
-- свежести на её настройке носителями не являются. Индикаторы и структуры
-- объявляются каталогом стратегии и адресуются по ключу.

alter table strategy_market_phase_settings drop column timeframe;
alter table strategy_market_phase_settings drop column expiration_duration;
alter table strategy_market_phase_settings drop column indicator_settings;
alter table strategy_market_phase_settings drop column market_structure_settings;
alter table strategy_market_phase_settings drop column params;
alter table strategy_market_phase_settings add column phase_rules jsonb not null;

comment on column strategy_market_phase_settings.phase_rules is
    'Клаузы классификации фазы: упорядоченный список, порядок = позиция в списке';

-- ---------------------------------------------------------------------
-- 2. Деталь: четвёрка риск-чисел, собственных настроек нет
-- ---------------------------------------------------------------------
--
-- Прежняя колонка несла ПОАКТНЫЙ потолок, а имя обещало потолок сделки:
-- потолков на сделку три, и ни один ею не считался
-- (docs/rules/risk-policy.md §«Четыре потолка на разные вопросы»).

alter table strategy_details
    rename column risk_per_trade_percent to risk_per_action_percent;

alter table strategy_details
    add column cumulative_risk_per_deal_multiplier            numeric(36, 18),
    add column strategy_simultaneous_risk_per_deal_percent    numeric(36, 18),
    add column strategy_catastrophic_risk_per_deal_multiplier numeric(36, 18);

-- Индикаторы и структуры детали адресуются по ключу на каталог стратегии;
-- собственных настроек деталь не держит.
alter table strategy_details drop column indicator_settings;
alter table strategy_details drop column market_structure_settings;

-- Признак переоткрытия — свойство одного входа, а не всей фазы: он живёт
-- на объявлении транша, где V1 его уже завёл.
alter table strategy_details drop column position_reopen_allowed;

comment on column strategy_details.risk_per_action_percent is
    'Поактный потолок риска: сколько берёт ОДНО действие, % базы риска';

comment on column strategy_details.strategy_simultaneous_risk_per_deal_percent is
    'Максимум одновременного риска сделки; вкладывается в конфигурационный максимум';

comment on column strategy_details.strategy_catastrophic_risk_per_deal_multiplier is
    'Множитель катастрофического потолка; сверяется с конфигурационным пределом на создании';

-- ---------------------------------------------------------------------
-- 3. Шаг: уровень объявления читается по родителю строки
-- ---------------------------------------------------------------------
--
-- Шаг принадлежит траншу, кроме узкой агрегатной поверхности (EXIT и
-- FAIL_SAFE уровня сделки). Тип шага носителем уровня не является: он
-- предмет проверки создания.

alter table strategy_steps add column strategy_tranche_id bigint;
alter table strategy_steps add column deal_status varchar(32);
alter table strategy_steps add constraint fk_strategy_step_tranche
    foreign key (strategy_tranche_id) references strategy_tranches (id);

alter table strategy_steps alter column strategy_detail_id drop not null;
alter table strategy_steps alter column tranche_status drop not null;

-- Прежний ключ мерил деталь и не различал транша: N объявлений с шагами
-- одного статуса конфликтовали бы по step_index.
alter table strategy_steps drop constraint uk_strategy_step_detail_tranche_status_index;

create unique index uk_strategy_step_tranche_status_index
    on strategy_steps (strategy_tranche_id, tranche_status, step_index)
    where strategy_tranche_id is not null;

create unique index uk_strategy_step_detail_deal_status_index
    on strategy_steps (strategy_detail_id, deal_status, step_index)
    where strategy_detail_id is not null;

-- Ровно один родитель и ровно один ключ группировки — вместе, одним
-- ограничением: строка с обоими родителями попала бы в оба уникальных
-- индекса и читалась бы двумя уровнями сразу.
alter table strategy_steps add constraint ck_strategy_step_single_owner
    check (
        (strategy_tranche_id is not null and tranche_status is not null
             and strategy_detail_id is null and deal_status is null)
        or (strategy_detail_id is not null and deal_status is not null
             and strategy_tranche_id is null and tranche_status is null)
    );

-- ---------------------------------------------------------------------
-- 4. Действие: уровень — свойство транша, а не действия
-- ---------------------------------------------------------------------

alter table strategy_actions drop column level;

-- ---------------------------------------------------------------------
-- 5. Транш сделки: полный набор полей аудита
-- ---------------------------------------------------------------------
--
-- Строка наследует поля аудита наравне с остальными строками ядра
-- (docs/models/domain/aggregate/DealTranche.md §Персистентность,
-- docs/models/domain/other/Auditable.md). Одиночный updated_at конвенции
-- слоя не соответствует: имя поля последнего изменения — modified_at, а
-- писателя изменения и биржевых меток без остальных колонок назвать негде.

alter table deal_tranches drop column updated_at;
alter table deal_tranches
    add column created_by           varchar(64),
    add column modified_at          timestamptz,
    add column modified_by          varchar(64),
    add column external_created_at  timestamptz,
    add column external_modified_at timestamptz;
