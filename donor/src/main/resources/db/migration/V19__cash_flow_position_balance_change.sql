-- Изменение маржи позиции на строке разбивки (заход по движениям счёта,
-- шаг 7; docs/models/domain/other/DealCashFlow.md).
--
-- Сырой факт источника (OKX posBalChg). Несущее наблюдение прогона AG1.7
-- 2026-09-02: на isolated-марже расчёт финансирования ложится в маржу
-- позиции при нулевом balChg — posBalChg funding-bill'а равен fundingFee
-- записи закрытия до последнего знака. Без колонки строка финансирования
-- персистилась бы с ложным нулём, а движение терялось бы для сверки
-- (bills глубже трёх месяцев конвейеру недоступны).
--
-- Как поле входит в пары сверки — решает дом сверки
-- (docs/rules/pnl-reconciliation.md), не схема.
--
-- Бэкфилла нет: до конца фазы 1 таблицы пусты
-- (.claude/rules/pre-launch-schema-changes.md).

alter table deal_cash_flows
    add column position_balance_change numeric(36,18);

comment on column deal_cash_flows.position_balance_change is
    'Знаковое изменение маржи позиции (OKX posBalChg); у isolated-финансирования несёт сумму расчёта при нулевом amount';
