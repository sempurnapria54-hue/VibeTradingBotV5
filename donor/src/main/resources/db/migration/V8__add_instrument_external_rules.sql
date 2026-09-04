-- Шаг 5: риск-преконтроль. Материализация InstrumentExternalRules как
-- источника ограничений инструмента (tick/lot/min size, per-order max
-- sizes, ctVal, max leverage, торгуемость). Персистентность —
-- JSONB-навес на строке-владельце instruments (нет FK-ссылок из других
-- мест → реляционная строка пользы не несёт, по правилу
-- persistence-representation). Один актуальный набор правил на
-- инструмент; audit-поля наследуются от строки instruments. Обновляется
-- InstrumentExternalRulesSyncJob по REST.

alter table instruments
    add column external_rules jsonb;
