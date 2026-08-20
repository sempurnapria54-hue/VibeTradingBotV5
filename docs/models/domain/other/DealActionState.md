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

Действия двух видов (`action_kind`):

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
`docs/components/RetryPolicyService.md`).

| Поле | Тип | Обязательно | Назначение |
|---|---|---|---|
| `id` | `Long` | да | Идентичность **исполнения** (суррогатная). |
| `dealId` | `Long` | да | Сделка, в рамках которой идёт исполнение. |
| `actionKind` | `ActionKind` | да | Вид действия: `STRATEGY` / `SYSTEM` (дискриминатор). |
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

Ключи — **частичные, по живым статусам, с целью в ключе** (модель — место
истины ключа, `docs/rules/idempotency-via-unique.md` §«Уникальность среди
живых»):

```text
живые статусы = PLANNED | CREATED | SUBMITTED | RETRY_PENDING
action_ref    = strategy_action_id (STRATEGY) | system_action_type (SYSTEM)

unique (deal_id, action_ref, target_entity_type, target_entity_id)
       where status in (живые) and target_entity_id is not null
unique (deal_id, action_ref)
       where status in (живые) and target_entity_id is null
```

- Ноги грида различаются **целью** и живут одновременно; «незапущенное»
  (`target == null`) исполнение узла — не более одного (вторая нога
  планируется следующим проходом — штатный режим loop-driven петли).
- У системных действий цель — сама сделка ⇒ одно живое исполнение на
  (сделку, тип действия). **Операнды заполняются оба**:
  `target_entity_type = DEAL`, `target_entity_id = deal_id` — значит
  SYSTEM-строку ловит **первый** (не-null) частичный индекс (H24
  `DOCS_CHECK_10`; `docs/components/SystemActionExecutor.md` §Контракт
  описывал ту же гарантию через вторую ветку — формулировки выровнены).
  `NONE`/`null` у SYSTEM-строк не используется: цель у них есть всегда.
- Завершённые строки ключ **не держит** — исполнение терминально,
  следующее заводится свободно.
- Ключ защищает от дубля **планирования** (второй строки-намерения); от
  дубля ордера на бирже защищает stable client id
  (`internalId → clOrdId`, `docs/components/RetryPolicyService.md`
  §«Опасные команды»).
- NULL-семантика: `strategy_action_id` nullable, поэтому жёсткий
  `UNIQUE` по нему не работает — отсюда частичные индексы по
  `action_kind` (`docs/rules/idempotency-via-unique.md`
  §«NULL-семантика»).
- `strategyActionId` хранится **только** здесь, не в
  `Order`/`AlgoOrder`/`Position`.
- Target-колонки заполняет executor при создании/материализации сущности,
  не FSM напрямую.
- Строка переживает рестарт: command-layer пересобирает нужную команду по
  `status` + target + exchange facts; pending `ServiceCommand` как
  очередь не восстанавливаются (`docs/rules/command-lifecycle.md`).

## Транзакционная клауза записи в `Deal`

Звено, чей исход — запись в `Deal` (число, консолидация, статусное
ребро), делает эту запись **в одной транзакции** с durable-продвижением
своего исполнения (N7 + валидация 4;
`docs/decisions/command-action-boundary.md` §5). Без этого вывод стадии из
фактов ломается на рестарте: факт записан, а продвижение — нет (или
наоборот).

## Персистентность

- Таблица `deal_action_states`. **Миграция шага 7 (ALTER):**
  `strategy_action_id` → nullable; `+action_kind`, `+system_action_type`,
  `+target_entity_type`, `+target_entity_id`; прежний
  `uk_deal_action_state_deal_action` снимается, ставятся частичные
  индексы §Инварианты.

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
- у STRATEGY-строк, заводимых после ввода, `action_kind = STRATEGY`,
  `system_action_type = null`.

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
- Enum'ы (`action_kind`, `system_action_type`, `status`,
  `target_entity_type`) — строкой (codestyle §Слои моделей и enum'ы).

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
