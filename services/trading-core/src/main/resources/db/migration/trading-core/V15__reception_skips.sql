-- След пропуска отравленной записи у группы ядра на теме определений.
--
-- Форма, писатель и транзакционная граница — у дома
-- (docs/rules/durable-consumer-reception.md §«След пропуска — таблица
-- `reception_skips`»); исход «пропуск, а не остановка» у ядра —
-- docs/architecture/data-ownership.md §«Копии чужих данных»; довод —
-- .claude/decisions/poison-record-observability.md.
--
-- ПРЕДМЕТ СТРОКИ — ПРОИСШЕСТВИЕ, а не состояние: строка на каждую пропущенную
-- запись. Строки не чистятся и не снимаются.
--
-- Ключ координат несущий: пропуск, чья фиксация смещения не состоялась,
-- доставляет ту же запись повторно и снова её пропускает — вставка поглощает
-- конфликт по ключу, и строка остаётся одна.
--
-- Аудита у строки нет намеренно — состав колонок закрыт домом: строка сама и
-- есть запись факта со своим моментом, а правок у неё не бывает (та же форма,
-- что у inbox_events).
--
-- Проект до прод-рубежа: таблица новая, бэкфилла не требуется
-- (.claude/rules/pre-launch-schema-changes.md).

create table reception_skips (
    id               bigserial    primary key,
    consumer_group   varchar(128) not null,
    topic            varchar(128) not null,
    record_partition integer      not null,
    record_offset    bigint       not null,
    event_id         varchar(64),
    event_type       varchar(128),
    tenant_id        varchar(64),
    cause            text         not null,
    skipped_at       timestamptz  not null,
    constraint uk_reception_skip_record unique (consumer_group, topic, record_partition, record_offset)
);

comment on table reception_skips is
    'Пропущенные отравленные записи: строка на запись, координаты возвращают её приёмом '
    '(docs/rules/durable-consumer-reception.md)';
comment on column reception_skips.event_id is
    'Идентичность события из конверта; пусто — конверт её не нёс';
comment on column reception_skips.event_type is
    'Класс события из конверта; пусто — конверт его не нёс';
comment on column reception_skips.tenant_id is
    'Ключ записи; пусто — запись его не несёт';
comment on column reception_skips.cause is
    'Первопричина отказа: класс и сообщение';
