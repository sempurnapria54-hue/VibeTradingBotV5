# DealActionState

## На какой вопрос отвечает этот файл

Что это за модель `DealActionState`: строка-исполнение действия (оба вида
— STRATEGY и SYSTEM), структура, енумы, retry-состояние, ключи
уникальности, персистентность.

Статусы и переходы — в `docs/lifecycles/DealActionState.md`.

## Назначение

`DealActionState` — **persisted** операционная модель **одного исполнения
действия** в рамках `Deal`. Отвечает на вопрос: «на каком шаге находится
это исполнение и какую runtime-сущность оно породило/затрагивает». Несёт
идемпотентность/recovery/retry command-layer'а.

Действия двух видов (`actionKind` — доменный дискриминатор; в схеме вид
кодируется таблицей, H15 `DOCS_CHECK_14`):

- **`STRATEGY`** — исполнение узла авторского дерева стратегии
  (`StrategyAction` по `strategyActionId`): CREATE/REPLACE/CANCEL-ноги
  ордеров и algo-ордеров.
- **`SYSTEM`** — исполнение системного действия (`systemActionType`) —
  последовательности команд без `StrategyAction`: добыча фактов и
  финализация (`docs/decisions/command-action-boundary.md`). Прежняя
  отдельная сущность `DealFinalizationState` **упразднена** — её ключ,
  ретрай и роль перенесены сюда.

**Строка = исполнение, не действие.** Действие стратегии исполняется в
сделке **многократно** (грид: повторное срабатывание узла, одноимённые
узлы дерева, параллельные ноги в полёте), системные действия повторяются
циклами добычи. Идентичность исполнения — суррогатный `id`; «не завести
второй экземпляр того же намерения» обеспечивают частичные ключи
(§Инварианты). Строки исполнений **копятся** (`COMPLETED` жёстко
терминален, слот не переиспользуется); политика очистки — фаза 3.

Не торговая бизнес-сущность (не про PnL — тем владеет `Deal`), а
операционное состояние исполнения — `docs/models/domain/other/`
(`docs/decisions/deal-action-state-materialization.md`,
`.claude/decisions/model-layer-ontology.md`).

`DealActionState` — единственный держатель связи `StrategyAction ↔
runtime-сущность`: `Order`/`AlgoOrder`/`Position` **не** хранят
`strategyActionId` (см. `docs/rules/audit-not-runtime-source.md`).

## Структура

Java-модель, наследует retry-состояние от базового `Retryable` (см.
`docs/components/RetryPolicyService.md`) **и `Auditable`** (H15
`DOCS_CHECK_15`, решение пользователя).

**Модель признана аудируемой** — и это решение по существу, а не оформление
под миграцию. Прежде `DealActionState` в перечне наследников `Auditable`
не значился (`DealActionStateEntity` нёс комментарий «не наследует
`AuditableEntity`»), при этом новая таблица-близнец объявлялась «с
audit-колонками», а старая их не несла: писатель `CREATE TABLE` не мог
выписать состав, и любой из трёх исходов (шесть колонок / четыре / ни
одной) был бы незаписанным отступлением, застывающим в схеме.

- **Набор — полный, по общему правилу**
  (`docs/models/domain/other/Auditable.md` §«Правило состава
  audit-колонок»): все шесть колонок, обе таблицы. Частичных наборов не
  вводится, и само правило не правится — исключение из бинарного правила
  стоило бы дороже двух незаполненных колонок.
- **Цена принята явно:** `externalCreatedAt` / `externalModifiedAt` у
  исполнения останутся пустыми навсегда — у строки-исполнения нет события
  источника. Это та же цена, которую правило состава уже назвало для
  любого наследника без внешнего события.
- **Выигрыш:** отметка времени создания строки (`createdAt`) появляется
  **до** того, как понадобится, — и она же операнд запланированной
  политики очистки копящихся строк (фаза 3, §Назначение). Без неё
  ретеншен пришлось бы вводить вместе с колонкой и бэкфиллом.

| Поле | Тип | Обязательно | Назначение |
|---|---|---|---|
| `id` | `Long` | да | Идентичность **исполнения** (суррогатная). |
| `dealId` | `Long` | да | Сделка, в рамках которой идёт исполнение. |
| `actionKind` | `ActionKind` | да | Вид действия: `STRATEGY` / `SYSTEM` (доменный дискриминатор). **В схему не персистится** — вид кодируется таблицей (H15 `DOCS_CHECK_14`, §Персистентность). |
| `strategyActionId` | `Long` | у STRATEGY | Узел стратегии, чьё исполнение отслеживается. `null` у SYSTEM-строк — узла стратегии за системным действием нет. |
| `systemActionType` | `SystemActionType` | у SYSTEM | Тип системного действия. `null` у STRATEGY-строк. |
| `targetEntityType` | `TargetEntityType` | нет | Тип runtime-сущности, на которую нацелено исполнение. Колонка (не jsonb): операнд ключа уникальности. |
| `targetEntityId` | `Long` | нет | Локальный `id` этой сущности; `null`, пока сущность не создана (`PLANNED`) и для бессущностных исполнений. |
| `status` | `DealActionStateStatus` | да | Статус исполнения (см. lifecycle). |

Retry-поля из базы `Retryable`: `attemptCount`, `maxAttempts` (снимок),
`nextRetryAt`, `lastError` (`RetryError`, jsonb). **Счётчик — сквозной
бюджет отказов одного исполнения** (без обнуления при продвижении
стадии); **предел** читается живьём по типу текущей команды
(`docs/components/RetryPolicyService.md` §«Предел — по команде, счётчик —
по исполнению»).

**Стадия исполнения выводится из подтверждённых фактов**, полем не
хранится: явная стадия дублировала бы факты и протухала на рестарте
(`docs/rules/command-lifecycle.md` — очередь команд из сохранённого
состояния не восстанавливается). Курсора пройденных звеньев и флага
«рестарт с нуля» нет. Что считается подтверждённым фактом звена каждого
системного действия — `docs/components/SystemActionExecutor.md`.

## Енумы

### `ActionKind`

- `STRATEGY` — исполнение узла авторского дерева стратегии.
- `SYSTEM` — исполнение системного действия.

### `SystemActionType`

- `REFRESH_DEAL_CONTEXT_ACTION` — добыча свежих фактов с биржи
  (звенья — добывающие `REFRESH_*`-команды). Cleanup
  (`CANCEL_*`/`CLOSE_POSITION_COMMAND`) звеном **не является**.
- `FINALIZE_DEAL_ENTRY_ACTION` — консолидация результата входа (звено —
  `FINALIZE_DEAL_ENTRY_COMMAND`); завершение пишет
  `Deal.status = ENTRY_FINALIZED` **той же транзакцией**
  (`docs/decisions/command-action-boundary.md` §5).
- `FINALIZE_DEAL_EXIT_ACTION` — число и чистый терминал (звенья —
  `FINALIZE_DEAL_EXIT_COMMAND` → `MARK_DEAL_CLOSED_COMMAND`).
- `FINALIZE_DEAL_ERROR_ACTION` — аварийная тропа (звенья —
  `MARK_DEAL_ERROR_COMMAND` / `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`; вход в
  `ERROR` и терминал — **два отдельных исполнения**).

### `DealActionStateStatus`

- `PLANNED` — исполнение выбрано, команды ещё не было (`targetEntityId ==
  null` у сущностных).
- `CREATED` — `CREATE_*` создал локальную сущность; target заполнен.
- `SUBMITTED` — `SUBMIT_*`/`CANCEL_*`/`CLOSE_POSITION_COMMAND` отправлен
  на биржу; факт не подтверждён (ACK не runtime truth).
- `COMPLETED` — исполнение доведено, факты подтверждены. **Жёстко
  терминален**: строка не переиспользуется, новая надобность в том же
  действии — новое исполнение (новая строка).
- `RETRY_PENDING` — звено упало на retryable-ошибке; ждёт `nextRetryAt`.
- `FAILED` — бюджет исчерпан либо ошибка non-retryable; сделка идёт
  ошибочной тропой (`docs/rules/runtime-error-classification.md`).
- `SKIPPED` — исполнение стало неактуальным и не продолжается.

### `TargetEntityType`

- `ORDER` — ordinary order.
- `ALGO_ORDER` — standalone algo-order.
- `POSITION` — позиция.
- `DEAL` — сама сделка: цель **системных действий** (финализация, добыча
  контекста сделки).
- `BALANCE` — баланс (`REFRESH_BALANCE_COMMAND`).
- `NONE` — исполнение без runtime-target-сущности (`targetEntityId ==
  null`).

## REPLACE-действия (две ноги, одна запись на ногу)

`StrategyActionType.REPLACE_ACTION` (`docs/decisions/replace-not-amend.md`)
исполняется как CREATE-надмножество: нога порождает **новую**
runtime-сущность (target = новая, `replacesInternalId` = `internalId`
замещаемой) плюс cancel-ногу по старой. Новых статусов нет —
`StrategyActionOrchestrator` (через per-type `StrategyActionExecutor`)
выводит следующую команду **из фактов** («одна актуальная команда за
проход»):

- protective (`positionReducingOnly = true`): новой нет → `CREATE_*`; не
  отправлена → `SUBMIT_*`; новая подтверждена и старая жива → `CANCEL_*`
  старой (`REPLACED_BY_STRATEGY`); старая терминальна → `COMPLETED`;
- entry (не reduce-only): зеркально — cancel-нога первой.

Замещаемая сущность резолвится из `DealContext.actionStates` по цепочке
замещений от target-action.

## Инварианты

**Стратегийные и системные исполнения хранятся в двух таблицах** (H15
`DOCS_CHECK_14`, решение пользователя): `deal_strategy_action_states` и
`deal_system_action_states`, у каждой **свои** ключи; частичных индексов
по `action_kind` на общей таблице не заводится, псевдо-операнда
`action_ref` не существует. Прежняя запись ключа через `action_ref` была
неисполнима (колонки с таким именем нет), а форма ключа на общей таблице
упиралась в `NULL`-семантику nullable-ссылки — здесь она **исчезает
вместе с nullable-колонкой**: в стратегийной таблице `strategy_action_id`
`NOT NULL`, в системной `system_action_type` `NOT NULL`.

Ключи — **частичные, по живым статусам** (модель — место
истины ключа, `docs/rules/idempotency-via-unique.md` §«Уникальность среди
живых»):

**У каждого ключа есть имя** (B2 `DOCS_CHECK_21`): безымянный объект
заставляет писателя миграции имя придумать, а сборка schema-дельты
объявляет, что безымянных объектов в ней не остаётся
(`docs/decisions/pnl-finalization-mechanics.md` §«Schema-дельта шага 7»).

```text
живые статусы = PLANNED | CREATED | SUBMITTED | RETRY_PENDING

deal_strategy_action_states:
  uk_deal_strategy_action_state_target
    unique (deal_id, strategy_action_id, target_entity_type, target_entity_id)
           where status in (живые) and target_entity_id is not null
  uk_deal_strategy_action_state_action
    unique (deal_id, strategy_action_id)
           where status in (живые) and target_entity_id is null

deal_system_action_states:
  uk_deal_system_action_state_action
    unique (deal_id, system_action_type)
           where status in (живые)
```

Имена разводятся **операндом различения**: `…_target` — ключ ног,
различаемых целью; `…_action` — ключ «одно живое исполнение на узел»
(в стратегийной таблице узел — `strategy_action_id`, в системной —
`system_action_type`). Все три укладываются в лимит идентификатора
PostgreSQL (63 байта): самое длинное —
`uk_deal_strategy_action_state_target`, 36 символов.

- Ноги грида различаются **целью** и живут одновременно; «незапущенное»
  (`target == null`) исполнение узла — не более одного (вторая нога
  планируется следующим проходом — штатный режим loop-driven петли).
- У системных действий цель — сама сделка ⇒ одно живое исполнение на
  (сделку, тип действия); операнды цели в системный ключ **не входят** —
  они производны (`DEAL`, `deal_id`), и target-колонок системная таблица
  не несёт вовсе (§Персистентность). `NONE`/`null` у системных исполнений
  не используется: цель у них есть всегда.
- Завершённые строки ключ **не держит** — исполнение терминально,
  следующее заводится свободно.
- Ключ защищает от дубля **планирования** (второй строки-намерения); от
  дубля ордера на бирже защищает stable client id
  (`internalId → clOrdId`, `docs/components/RetryPolicyService.md`
  §«Опасные команды»).
- `strategyActionId` хранится **только** здесь, не в
  `Order`/`AlgoOrder`/`Position`.
- Target-колонки заполняет executor при создании/материализации сущности,
  не FSM напрямую.
- Строка переживает рестарт: command-layer пересобирает нужную команду по
  `status` + target + exchange facts; pending `ServiceCommand` как
  очередь не восстанавливаются (`docs/rules/command-lifecycle.md`).

**Совместимость с ратифицированной топологией — проверена, цена
названа.** Топология строк («строка = исполнение, не действие»:
многократные исполнения, копление, `COMPLETED` жёстко терминален,
сквозной бюджет отказов) **не пересматривается** — обе таблицы держат те
же строки-исполнения с теми же статусами и retry-базой. Пересматривается
**носитель** принятой топологии V2 («общая таблица, nullable ссылка,
частичные ключи» → две таблицы, `NOT NULL`-ссылки, свои ключи) — явным
решением, не молча. Запланированная следующая итерация топологии
(политика очистки копящихся строк — фаза 3) совместима: чистка идёт
per-таблица, и горизонты двух видов могут различаться. **Цена:** вторая
entity и ветвление `DataService` по виду; `DealContext.actionStates`
собирается из двух таблиц (два чтения вместо одного); переименование
существующей таблицы (дёшево — таблицы пусты,
`.claude/rules/pre-launch-schema-changes.md`); появление системного
действия с целью ≠ `DEAL` потребует добавить системной таблице
target-колонки (условие названо). **Выигрыш:** класс дефекта «`NULL`'ы в
`UNIQUE` различны» устранён схемой, а не предикатом; каждый читатель уже
читает свой вид (`StrategyActionOrchestrator` — STRATEGY,
`SystemActionExecutor` — SYSTEM) — таблица совпадает с читателем;
колонка-дискриминатор `action_kind` в схеме не нужна (вид кодируется
таблицей; доменный енум `ActionKind` остаётся — вид различает домен).

## Транзакционная клауза записи в `Deal`

Звено, чей исход — запись в `Deal` (число, консолидация, статусное
ребро), делает эту запись **в одной транзакции** с durable-продвижением
своего исполнения (N7 + валидация 4;
`docs/decisions/command-action-boundary.md` §5). Без этого вывод стадии из
фактов ломается на рестарте: факт записан, а продвижение — нет (или
наоборот).

## Персистентность

**Две таблицы, одна доменная модель** (H15 `DOCS_CHECK_14`). Домен —
один класс `DealActionState` с дискриминатором `actionKind` (семантика
исполнения общая: статусы, target, retry-база `Retryable`);
разводится **хранение**: две entity
(`DealStrategyActionStateEntity` / `DealSystemActionStateEntity`),
маппинг по виду делает `DataService` — единственная граница
domain ↔ persistence.

- **`deal_strategy_action_states`** (существующая `deal_action_states`,
  **переименовывается** — таблицы пусты, `ALTER RENAME` дёшев).
  **Миграция шага 7:** rename; `strategy_action_id` остаётся `NOT NULL`;
  **`+fk_deal_strategy_action_state_deal`** — FK `deal_id → deals(id)`
  (B6 `DOCS_CHECK_18`): колонка `NOT NULL` с `V6`, а FK у неё не было,
  тогда как таблица-близнец `deal_system_action_states` его получает.
  Асимметрия снята выравниванием **вверх** — тем же доводом симметрии,
  которым выровнен audit-состав; цена нулевая (таблицы пусты,
  `.claude/rules/pre-launch-schema-changes.md`);
  `+target_entity_type` (**`varchar(64)`, nullable** — енум
  `TargetEntityType` строкой), `+target_entity_id` (**`bigint`,
  nullable**) — расплющенный `RuntimeTarget`; **+ шесть audit-колонок**
  (`external_created_at`, `external_modified_at`, `created_at`,
  `modified_at` — `timestamptz`; `created_by`, `modified_by` —
  `varchar(64)`; все nullable, H15 `DOCS_CHECK_15`); прежний жёсткий
  `uk_deal_action_state_deal_action` снимается, ставятся два частичных
  ключа §Инварианты (`uk_deal_strategy_action_state_target`,
  `uk_deal_strategy_action_state_action`).
  - **Существующий FK переименовывается вместе с таблицей** (B13
    `DOCS_CHECK_21`): `fk_deal_action_state_strategy_action` →
    `fk_deal_strategy_action_state_strategy_action`. Довод тот же, что у
    вводимого рядом `fk_deal_strategy_action_state_deal`, — симметрия
    имён с таблицей-близнецом; к соседу довод прежде не применялся, и
    таблица уезжала бы с именем FK от прежнего имени таблицы. Цена
    нулевая: `ALTER … RENAME CONSTRAINT`, таблицы пусты.
  - **`status` выравнивается вверх до `varchar(64)`** (B11
    `DOCS_CHECK_21`): существующая колонка — `varchar(32)` (`V6`), норма
    шага и колонка близнеца — `varchar(64)`. Функционального разрыва
    нет (самое длинное значение енума — 13 символов), но норма была
    объявлена без `ALTER`, то есть сборка и место истины расходились бы
    молча. Асимметрия снимается тем же выравниванием вверх и тем же
    доводом симметрии, которым выровнен audit-состав; цена нулевая. Колонок `action_kind` / `system_action_type` таблица
  **не получает** — вид кодируется таблицей.
  - **Типы и nullability двух новых колонок названы здесь** (H19
    `DOCS_CHECK_15`), а не оставлены выводимыми: место истины схемы —
    §Персистентность модели (`docs/rules/persistence-representation.md`),
    и предыдущая правка того же шага дописала типы `deals` / `positions` /
    `instruments` именно потому, что писатель миграции выбирает длину по
    аналогии и расходится с соседями добросовестно. Две колонки того же
    шага остались без типов — дописаны.
  - **Связка «nullable-колонка в уникальном ключе» — названа явно.**
    `target_entity_id` nullable и при этом входит в операнды **первого**
    частичного ключа §Инварианты. Дефекта здесь нет **потому, что предикат
    индекса сам отсекает `NULL`** (`… and target_entity_id is not null`), а
    строки с пустой целью охраняет **второй** ключ с противоположным
    предикатом. Без этой записи читатель обязан вывести её сам — а класс
    «`NULL`'ы в `UNIQUE` различны, значит ключ мёртв» проект уже ловил
    дважды (`docs/rules/idempotency-via-unique.md` §«Уникальность среди
    живых»).
- **`deal_system_action_states`** — **новая** (`CREATE TABLE`).
  Поколоночно (B4 `DOCS_CHECK_21`: оборот «как у близнеца» покрывал
  только audit-состав, а типы и nullability retry-колонок оставались
  выводимыми — тот самый механизм «писатель миграции выбирает по
  аналогии и расходится с соседями добросовестно», которым обоснована
  предыдущая правка этого же раздела):

  | Колонка | Тип | Nullability | Комментарий |
  |---|---|---|---|
  | `id` | `bigint generated by default as identity` | `NOT NULL` | `primary key` (безымянный, inline) — форма та же, что у близнеца (`V6`) и у сестёр шага; `bigserial` + именованный PK сняты B4 `DOCS_CHECK_23`: в проекте нет ни одного такого объекта |
  | `deal_id` | `bigint` | `NOT NULL` | FK → `deals` (`fk_deal_system_action_state_deal`) |
  | `system_action_type` | `varchar(64)` | `NOT NULL` | енум `SystemActionType` строкой |
  | `status` | `varchar(64)` | `NOT NULL` | енум `DealActionStateStatus` строкой |
  | `attempt_count` | `integer` | `null` | `Retryable`; nullable — как у близнеца (`V6`) |
  | `max_attempts` | `integer` | `null` | `Retryable`; nullable — как у близнеца |
  | `next_retry_at` | `timestamptz` | `null` | `Retryable`; пусто, пока ретрай не запланирован |
  | `last_error` | `jsonb` | `null` | `Retryable`; пусто, пока ошибок не было |
  | шесть audit-колонок | `timestamptz` / `varchar(64)` | `null` | тот же состав и та же nullability, что у близнеца |

  Target-колонок нет: цель системного действия — всегда сама сделка
  (`deal_id`); ключ — частичный, `uk_deal_system_action_state_action`
  (§Инварианты).
- **Обе таблицы несут полный набор audit-колонок** (H15 `DOCS_CHECK_15`,
  решение пользователя). Прежде дельта объявляла audit только у новой
  таблицы, а старая их не несла: одна доменная модель, две entity, у одной
  audit есть, у другой нет — конфигурация, которой правило «`Auditable` по
  слоям» не описывает. Асимметрия снята выравниванием **вверх**: модель
  наследует `Auditable` (§Структура), обе таблицы получают шесть колонок.
  Имена — по фактическому `AuditableEntity` (`modified_at`/`modified_by`,
  не `updated_*`), nullability — как у всех существующих таблиц проекта.
- **Длины строковых колонок — единая норма `varchar(64)`** (H18
  `DOCS_CHECK_15`): `status`, `system_action_type`, `target_entity_type`,
  `created_by`, `modified_by`. Прежняя категоризация по типу значения
  схлопнута (`docs/rules/persistence-representation.md` §«Строковые
  колонки: длины»); внутришаговая несимметрия (`varchar(32)` у одной
  27-символьной enum-колонки против `varchar(64)` у другой) снята вместе
  с ней.
- `DROP TABLE deal_finalization_states` — по правилу переноса ниже (её
  роль перенесена в системную таблицу).

#### Поисковые индексы обеих таблиц (H10 `DOCS_CHECK_16`)

Прежде §Персистентность перечисляла **ключи** и ни одного поискового
индекса — при том, что тот же шаг **снимает** `uk_deal_action_state_deal_action`,
единственное, что покрывало доступ по `deal_id` своим префиксом
(`V6__create_deal_runtime_tables.sql`, комментарий к блоку индексов говорит
это дословно). Заменяющие ключи — **частичные, по живым статусам**, поэтому
терминальные строки (`COMPLETED` / `FAILED` / `SKIPPED`) после миграции не
покрыты ничем, а читаются они на **каждом** проходе: сборка `DealContext`,
резолв цепочки REPLACE. Строки при этом **копятся** — ретеншен отложен на
фазу 3.

Миграция шага ставит по одному индексу на таблицу:

```text
create index ix_deal_strategy_action_state_deal
    on deal_strategy_action_states (deal_id);
create index ix_deal_system_action_state_deal
    on deal_system_action_states (deal_id);
```

Оба — **полные, не частичные**: они восстанавливают утраченное покрытие,
а утрачено оно именно на терминальных строках.

**Индекса под due-for-retry не вводится — и это записано, а не пропущено.**
Проход оркестратора **сделко-ориентирован, а не строко-ориентирован**:
внешнее множество — активные сделки, отбираемые по `Deal.status`
(префикс `ix_deal_status_close_outcome`,
`docs/models/domain/aggregate/Deal.md` §Персистентность), их число
ограничено инвариантом слота (одна активная сделка на инструмент), а строки
исполнений каждой из них всё равно читаются по `deal_id` при сборке
`DealContext`. «Наступил ли `nextRetryAt`» — предикат **над уже
загруженными** строками, отдельной выборки по таблицам исполнений не
существует (`docs/components/DealOrchestratorJob.md` §«Критерии выборки»).
Второй путь отбора по непрунимой таблице был бы структурой, которая платится
стоимостью записи без выигрыша чтения, — тем же доводом, каким шаг дропает
`ix_deal_status`.

Денормализовать `nextRetryAt` на `Deal` (чтобы отбирать сделки одним
запросом) **не требуется** по той же причине и отвергается отдельно:
связность растёт — два места знают одно.

### Правило переноса `deal_finalization_states` (H19 `DOCS_CHECK_10`)

Прежняя формулировка «перенос строк (вид SYSTEM) + `DROP TABLE`» на
уровне значений была не задана и по коду не однозначна. Правило
зафиксировано, и оно **разное для двух половин**.

**(а) Строки финализации — не переносятся.** `DELETE` (`TRUNCATE`) +
`DROP TABLE deal_finalization_states`; мигрируется только **структура**.

- **Довод — переносить нечего:** фаза 1 в проде не работает, живых
  финализационных строк, которые стоило бы сохранить, нет. Это факт
  состояния проекта (шаг 7 из 11, кода целевой дельты ещё нет), а не
  допущение.
- **Довод против переноса как такового:** отображение **не
  однозначно**. `DealFinalizationType` — четыре значения
  (`FINALIZE_ENTRY`/`FINALIZE_EXIT`/`MARK_CLOSED`/`MARK_ERROR`), а
  `FINALIZE_EXIT` и `MARK_CLOSED` — **звенья одного**
  `FINALIZE_DEAL_EXIT_ACTION` ⇒ перенос 2:1, упирающийся в новый
  частичный уникальный индекс живых. Схлопывание двух строк в одну
  требует правила «какую берём» и «что с `attempt_count`/`last_error`» —
  то есть **конструирует состояние исполнения, которого никогда не
  было**, вместе с фальшивой историей попыток. Статусы не совпадают тоже
  (`PENDING|COMPLETED|RETRY_PENDING|FAILED` против семи
  `DealActionStateStatus`), и `PENDING → PLANNED` пришлось бы объявлять
  отдельным правилом.
- **Цена названа:** если к моменту `CODE` в БД окажутся строки
  финализации (локальные прогоны), они будут потеряны — это операционные
  данные отладки, не торговая история; `Deal` и его число живут в своей
  таблице и переносом не затрагиваются.

**(б) `target` расплющивается в колонки — без бэкфилла** (H25
`GAPS_CLOSE_13`, решение владельца):

- вводятся `target_entity_type` / `target_entity_id`, jsonb-колонка
  `target` **дропается**; двух представлений одного факта не оставляем;
- **`UPDATE` из jsonb не выполняется:** переносить нечего — до конца
  фазы 1 проект считается незапущенным, таблицы пусты
  (`.claude/rules/pre-launch-schema-changes.md`). Прежняя редакция
  предписывала бэкфилл «у существующих строк», то есть применяла к
  собственной миграции шага не то допущение, которое шаг применяет рядом
  трижды (валютные колонки `instruments`, `external_modified_at`,
  `anomaly_reports.kind`);
- побочно снят операнд, которого не существовало: ключи jsonb-сериализации
  `RuntimeTarget` в доках не зафиксированы, и `UPDATE` привязал бы миграцию
  к текущей форме сериализации, ничем не закреплённой;
- расплющивание идёт в **стратегийной** таблице; системная таблица
  target-колонок не несёт вовсе (H15 `DOCS_CHECK_14`, §Персистентность).

**Почему асимметрия (а) и (б) — по оси существования строк.**
Финализационные строки **и** STRATEGY-строки к моменту миграции одинаково
отсутствуют (правило фазы), поэтому «переносится / не переносится»
различает не судьбу данных, а **судьбу колонок**: `target` расплющивается,
таблица `deal_finalization_states` дропается целиком. Прежний довод об
осмысленности STRATEGY-строк отвечал на другой вопрос — «стоит ли строки
сохранять», — тогда как правило фазы утверждает, что их **нет**.
Осмысленность связи «узел стратегии → runtime-сущность» остаётся доводом
за **колонки** (`docs/rules/audit-not-runtime-source.md`), а не за
бэкфилл.
- `targetEntityType`/`targetEntityId` — **колонки**, не jsonb: поля стали
  операндами ключа уникальности — исключение из «вложенное → jsonb»
  (`docs/rules/persistence-representation.md`; прежний вложенный
  `RuntimeTarget` расплющен).
- `lastError` (`RetryError`) — jsonb на строке.
- Enum'ы (`system_action_type`, `status`,
  `target_entity_type`) — строкой (codestyle §Слои моделей и enum'ы);
  колонки `action_kind` нет ни в одной таблице (вид кодируется таблицей).

## Чего не хранит

- Стадию исполнения (выводится из фактов) и историю исполнения команд
  (audit — отдельный слой, `docs/rules/audit-not-runtime-source.md`).
- `role`/`level`/`strategyActionKey` (контекст — через `StrategyAction`).
- Параметры команды (`ServiceCommandPayload` — runtime, не персистится).
- Kill-switch: он не действие — наблюдается `AnomalyReport`
  (`docs/decisions/command-action-boundary.md` §2).

## Связи

- Держатель связи для `Order` / `AlgoOrder` / `Position`.
- STRATEGY-строки: читает `StrategyActionOrchestrator` / per-type
  `StrategyActionExecutor`; SYSTEM-строки: читает
  `docs/components/SystemActionExecutor.md`; пишут executor'ы и
  `RetryPolicyService`.
- Решения — `docs/decisions/command-action-boundary.md`,
  `docs/decisions/deal-action-state-materialization.md` (ревизовано в
  части ключа и представления target),
  `docs/decisions/deal-finalization-state-materialization.md`
  (упразднение прежней сущности).
- Retry-база — `docs/components/RetryPolicyService.md`.
