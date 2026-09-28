-- Снимок предела повторов снят с обеих таблиц строк исполнения.
--
-- Предел читается живьём из политики по типу текущей команды
-- (docs/components/RetryPolicyService.md §«Авторитет предела — политика,
-- читается живьём»), а колонку не заполняла ни одна тропа: пустота читалась
-- бы как «предел не объявлен» там, где он есть и действует. Объявленная
-- величина обязана иметь писателя (docs/rules/writer-named-for-every-value.md);
-- читателя у снимка не было, и колонка снимается, а не наполняется.
--
-- Проект до прод-рубежа: таблицы пусты, переносить нечего
-- (.claude/rules/pre-launch-schema-changes.md).

alter table deal_strategy_action_states drop column max_attempts;

alter table deal_system_action_states drop column max_attempts;
