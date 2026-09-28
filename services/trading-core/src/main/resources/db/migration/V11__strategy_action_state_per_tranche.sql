-- Строка стратегийного исполнения — всегда потраншевая.
--
-- Шаг уровня сделки работает ребром сворачивания, и пакета действий не
-- запускает: действие выхода, объявленное на нём, исполняет само
-- сворачивание — каскад снимает входные ноги всех траншей, обработчик
-- координированного выхода шлёт одно закрытие
-- (docs/rules/no-partial-close.md §«Две законные формы полного выхода»).
-- Агрегатных строк стратегийного исполнения поэтому не бывает, и их ключ
-- снимается, а пустота транша запрещается схемой.
--
-- Проект до прод-рубежа: таблицы пусты, переносить нечего
-- (.claude/rules/pre-launch-schema-changes.md).

drop index uk_deal_strategy_action_state_deal_level_live;

alter table deal_strategy_action_states alter column deal_tranche_id set not null;

alter table deal_strategy_action_states alter column tranche_episode_seq set not null;
