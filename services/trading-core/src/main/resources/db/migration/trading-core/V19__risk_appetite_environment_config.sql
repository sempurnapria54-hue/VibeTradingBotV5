-- Числа риск-аппетита переехали в конфигурацию окружения
-- (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
-- отказ»; основание — .claude/decisions/risk-appetite-environment-config.md).
-- Строки тенанта под них у ядра больше нет: числа одни на всех тенантов
-- окружения, и принимает их ядро при старте, а не операция владельца.
--
-- Множитель катастрофического потолка у копии детали стратегии снят вместе с
-- глобальным: потолок нотинала сделки держит предел плеча конфигурации, и
-- правой части множитель больше не задаёт — колонка без читателя
-- (.claude/decisions/deal-leverage-ceiling.md).
--
-- Проект до прод-рубежа: потери данных нет — числа назначаются заново осями
-- окружения (.claude/rules/pre-launch-schema-changes.md).

drop table tenant_risk_appetites;

alter table strategy_details drop column strategy_catastrophic_risk_per_deal_multiplier;
