-- Четвёрка чисел риска сделки, плановый снимок ноги и база риска счёта
-- (заход по числам риска, шаг 7).
--
-- Разделение носителей несущее: у НОГИ — write-once снимок момента
-- постановки (под какой риск сайзились), у СДЕЛКИ — производные проекции
-- по ногам всех траншей, пересчитываемые целиком, у БИРЖИ — живая база
-- счёта. Смешивать их в одном месте нельзя: снимок не пересчитывается, а
-- проекция не замораживается.
--
-- Проект до запуска: таблицы пусты, бэкфилл не требуется
-- (.claude/rules/pre-launch-schema-changes.md).

-- ── Нога: write-once снимок планового риска ─────────────────────────────
-- Шесть чисел одного момента. Пишет создатель ноги той же транзакцией,
-- что заводит строку; пересчёту не подлежат — иначе «под какой риск
-- сайзились» отвечало бы сегодняшним состоянием, а не тогдашним.
alter table orders
    add column position_id             bigint,
    add column planned_entry_price     numeric(36, 18),
    add column planned_size_contracts  numeric(36, 18),
    add column planned_risk_amount     numeric(36, 18),
    add column planned_risk_currency   varchar(64),
    add column planned_contract_value  numeric(36, 18),
    add column planned_stop_price      numeric(36, 18);

alter table orders add constraint fk_order_position
    foreign key (position_id) references positions (id);

create index ix_order_position on orders (position_id);

comment on column orders.position_id is
    'Эпизод сделки, к которому относится нога; ось отбора живого эпизода, write-once';

-- ── Сделка: четыре производные проекции плюс два снимка ─────────────────
-- Проекции пересчитываются ЦЕЛИКОМ тем писателем, чья транзакция меняет
-- операнд, и только на полном графе: на неполном они занижены, то есть
-- ослабляют кумулятивный потолок (docs/models/domain/aggregate/Deal.md).
alter table deals
    add column planned_risk_amount              numeric(36, 18),
    add column incurred_risk_amount             numeric(36, 18),
    add column current_risk_amount              numeric(36, 18),
    add column protection_relieved_risk_amount  numeric(36, 18),
    add column planned_risk_currency            varchar(64),
    add column planned_risk_equity_base         numeric(36, 18);

comment on column deals.planned_risk_amount is
    'Риск, принятый сделкой на входах (R): знаменатель R-мультипликатора и операнд кумулятивного потолка';

comment on column deals.planned_risk_equity_base is
    'База риска на момент ПЕРВОГО сайзинга сделки, write-once: делитель всех четырёх потолков живой сделки';

-- ── Биржа: живая база риска и серия убытков ─────────────────────────────
-- База следует за свободным остатком расчётной валюты В ОБЕ СТОРОНЫ и
-- автоматически: ни пола, ни потолка на ходу нет (docs/rules/risk-policy.md).
-- Пусто = база ни разу не наблюдалась; ноль наблюдением не считается —
-- записанный ноль автоматически уже не поднялся бы.
alter table exchanges
    add column risk_base              numeric(36, 18),
    add column risk_base_currency     varchar(64),
    add column consecutive_loss_count integer not null default 0;

comment on column exchanges.risk_base is
    'Живая база риска счёта; пусто — ни разу не наблюдалась';

comment on column exchanges.consecutive_loss_count is
    'Серия убыточных исходов подряд — операнд остановки по серии (docs/rules/loss-streak-halt.md)';
