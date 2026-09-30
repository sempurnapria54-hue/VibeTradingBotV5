-- Inbox потребителя событий.
--
-- Заводится ВМЕСТЕ С ПЕРВЫМ ПОТРЕБИТЕЛЕМ (docs/architecture/data-ownership.md
-- §Раскладка), и первый потребитель ядра — три класса определения стратегии
-- от сервиса `strategies` (docs/architecture/contracts.md §События).
--
-- ПРЕДМЕТ ТАБЛИЦЫ — ДЕДУП, а не журнал: реле публикует раньше, чем помечает
-- опубликованное, и повторная доставка штатна. Повтор безопасен по
-- построению ровно потому, что идентичность события уникальна здесь
-- (docs/rules/idempotency-via-unique.md).
--
-- Проект до прод-рубежа: таблица пуста, бэкфилла не требуется
-- (.claude/rules/pre-launch-schema-changes.md).

create table inbox_events (
    id          bigserial primary key,
    event_id    varchar(64)  not null,
    event_type  varchar(128) not null,
    consumed_at timestamptz  not null,
    -- Идентичность события уникальна: второй ход обработки её не пройдёт.
    constraint uk_inbox_event_id unique (event_id)
);

comment on table inbox_events is
    'Дедуп доставленных событий по идентичности; журналом событий не является';
