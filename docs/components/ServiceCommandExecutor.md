# ServiceCommandExecutor

## На какой вопрос отвечает этот файл

Кто исполняет атомарную команду и маршрутизирует её в конкретный executor
(компонент): контракт, общая семантика групп, обработка controlled
exceptions.

## Назначение

`ServiceCommandExecutor` исполняет одну атомарную `ServiceCommand` (см.
`docs/components/models/ServiceCommand.md`), маршрутизируя её в конкретный
executor по типу payload. Executor не принимает торговых решений.

Контракт (ориентир, не требование к точным именам):

```java
ServiceCommandExecutionResult execute(P payload, DealContext dealContext);
```

## Общая семантика групп executor'ов

Конкретные executor'ы следуют этим правилам и не вводят несовместимую
семантику:

- **`CREATE_*`** — создаёт локальную runtime-сущность в БД (доменный
  статус, рассчитанные параметры, `DealActionState.target`), сохраняет
  сущность и `DealActionState` одной транзакцией; на биржу не ходит.
- **`SUBMIT_*`** — отправляет созданную сущность на биржу или
  восстанавливает факт отправки по stable client id (`internalId →
  clOrdId` / `algoClOrdId`); перед повтором ищет сущность по client id.
  ACK не runtime truth.
- **`CANCEL_*`** — отправляет отмену; ACK не truth; `closeReason` не
  перетирается, если уже установлен.

> **Амендных executor'ов нет.** `AMEND_*`-команды сняты
> (`docs/decisions/replace-not-amend.md`); ремоделирование — REPLACE-
> оркестрация существующих `CREATE_*`/`SUBMIT_*`/`REFRESH_*`/`CANCEL_*`
> (порядок ног по риск-классу действия), новых executor'ов не
> требуется.
- **`REFRESH_*`** — читает exchange facts через `IntegrationService`, применяет
  status resolver, обновляет сущность, заполняет `closeReason` только если
  текущий `== null`; торговых решений не принимает, cleanup не запускает,
  audit/history как runtime-source не использует. Право обхода
  нескольких эндпоинтов **внутри одной команды** — общий принцип
  класса `REFRESH_*` (`docs/rules/command-lifecycle.md` §«Атомарность
  не означает „один HTTP-запрос“»,
  `docs/rules/execution-hierarchy.md`), не привилегия отдельных
  команд: у `REFRESH_ORDER_COMMAND` / `REFRESH_ALGO_ORDER_COMMAND` это
  эскалация live → pending → history → archive (обрывается на первом
  успешном, полный обход — только при не-найдено, терминал
  `MISSING_AFTER_REFRESH` выносит сама команда), у
  `REFRESH_POSITION_COMMAND` — две ноги, у `REFRESH_BILLS_COMMAND` —
  пагинация; лестница эндпоинтов — деталь конкретной команды в её
  компонент-доке. Владение циклом —
  `docs/decisions/refresh-evidence-cycle-ownership.md`.

> **Pending/history эндпоинты** (`orders-pending` / `orders-history` /
> `orders-algo-pending` / `orders-algo-history`) — звенья evidence-cycle,
> который entity-refresh-исполнитель (`RefreshOrderExecutor` /
> `RefreshAlgoOrderExecutor`) обходит **внутри одной команды**
> (`docs/decisions/refresh-evidence-cycle-ownership.md`). Самостоятельных
> `REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` / `REFRESH_ALGO_ORDERS`
> / `REFRESH_ALGO_ORDER_HISTORY` **нет** (CMD-Q3 закрыт: refresh-набор —
> ровно по одной команде на сущность). Перечисление **неизвестных**
> live-сущностей по инструменту (orphan / чужой риск; Precheck-cleanliness,
> AnomalyJob) bulk-командой больше не покрыто — **CMD-Q4**.

- **`FINALIZE_*` / `MARK_*`** — финализационные команды над самой `Deal`
  (`FINALIZE_DEAL_ENTRY_COMMAND` / `FINALIZE_DEAL_EXIT_COMMAND` / `MARK_DEAL_CLOSED_COMMAND` /
  `MARK_DEAL_EMERGENCY_CLOSED_COMMAND` / `MARK_DEAL_ERROR_COMMAND`) — **звенья системных
  действий** `FINALIZE_DEAL_ENTRY_ACTION` / `FINALIZE_DEAL_EXIT_ACTION` /
  `FINALIZE_DEAL_ERROR_ACTION` (`docs/components/SystemActionExecutor.md`):
  консолидируют подтверждённые факты входа/выхода и делают
  терминальные/статусные рёбра сделки — запись в `Deal` идёт **одной
  транзакцией** с durable-продвижением исполнения
  (`docs/decisions/command-action-boundary.md` §5). На биржу сами не ходят
  (опираются на уже добытые `REFRESH_*`-факты), торговых решений не
  принимают, `RiskValidator` не вызывают
  (`docs/rules/risk-validator-scope.md`). Идемпотентны через факты `Deal`
  + частичный ключ живого исполнения. Retry-anchor — строка исполнения
  SYSTEM-вида (`docs/models/domain/other/DealActionState.md`). Семантика
  каждого — `docs/components/FinalizeDealEntryExecutor.md`,
  `docs/components/FinalizeDealExitExecutor.md`,
  `docs/components/MarkDealClosedExecutor.md`,
  `docs/components/MarkDealErrorExecutor.md`,
  `docs/components/MarkDealEmergencyClosedExecutor.md`. Граница 6 ↔ 7: расчёт
  `resultProfit` — шаг 7, механика финализации — шаг 6.

ACK как runtime truth не считается ни для submit/cancel/close (см.
`docs/rules/ack-not-runtime-truth.md`). Жизненный цикл команды и принцип
«одна команда за проход» — `docs/rules/command-lifecycle.md`.

## Controlled exchange exceptions

Refresh/executor boundary ловит controlled exception, обновляет
runtime-сущность и отдаёт факты FSM/handler'у (таксономия и реакция —
`docs/rules/controlled-exchange-exceptions.md`). Resolver FSM-решение не
принимает и сущность не сохраняет; client/adapter сделку в новый статус
напрямую не переводит.

## Retry

При неуспехе executor'а — через `docs/components/RetryPolicyService.md`:
`attemptCount`++, `nextRetryAt`, `lastError`, retry-anchor → `RETRY_PENDING`;
при исчерпании бюджета исполнения → `FAILED`. **Retry-anchor один** —
строка исполнения действия (`dealActionStateId`; оба вида — STRATEGY и
SYSTEM; `docs/decisions/command-action-boundary.md`). Cleanup-команды
анкера не несут — потому что **исполнения-действия у них нет**; учёт
отказов cleanup — форвард на `TradeGuardJob` (H16 `DOCS_CHECK_11`;
прежняя формулировка «серия считается на инструмент-scope» **снята** —
счётчика, на который она ссылалась, не существовало,
`docs/components/models/ServiceCommand.md`).

**Холд этот executor не поднимает** (H8 `DOCS_CHECK_12`). Его выход —
durable-факт: строка исполнения в `FAILED`. Блокировку по этому факту
ставит **`HoldService`**, которого зовёт детектор; на тропе сделки детектор
— `DealOrchestratorJob`, в той же точке, где он прерывает цикл команд по
неуспешному результату (`docs/components/HoldService.md`,
`docs/rules/instrument-hold.md` §«Носитель серии»). Так `FULL`-реакция
(kill-switch teardown) не исполняется **из середины цикла команд**, поверх
сделки, чей переход ещё не применён.

## Контракт броска: что, при каком условии и как отличимо (H1 `DOCS_CHECK_15`)

Детектор узнаёт об основании для холда **перехватом исключения**
(`docs/components/HoldService.md` §«Момент вызова»), поэтому исполнитель
обязан это исключение **бросать**, а контракт броска — быть записанным.
Прежде он записан не был: доки назначали перехват единственной точкой
подъёма, а исполнитель исключение гасил, возвращая
`ServiceCommandExecutionResult.failure(...)`; канала не существовало ни в
докáх, ни в коде (H1 `DOCS_CHECK_15`, решение пользователя).

**Условие броска — одно, и оно durable.** Исключение уходит наружу
**только после того, как попытки исчерпаны окончательно и строка
исполнения переведена в `FAILED`**. Пока строка в `RETRY_PENDING`,
исключение не бросается: executor возвращает
`ServiceCommandExecutionResult.failure(...)`, оркестратор прерывает цикл
команд прохода, повтор берёт следующий тик. Порядок обязателен: сперва
учёт отказов и `FAILED`, затем бросок — перехватчик обязан видеть
durable-факт, а не узнавать о нём из исключения.

**Что бросается — ровно два типа, и оба только отсюда:**

| Тип | Когда |
|---|---|
| `ControlledExchangeException` (все подклассы) | нарушение контракта источника; non-retryable ⇒ бюджет исчерпан сразу, строка помечена `FAILED`, исключение **пробрасывается как есть** (тип и есть носитель природы отказа; `docs/rules/controlled-exchange-exceptions.md`) |
| `RetryBudgetExhaustedException` — **новый тип**, вводится этим шагом | бюджет попыток исполнения исчерпан на любой другой первопричине (ретраябельный `ExchangeIntegrationException`, non-retryable `INTERNAL_ERROR`/`VALIDATION_ERROR`, ACK-реджект, легитимно-пустой исход исчерпанного цикла). Несёт идентичность блокируемого объекта (`instrumentId`, `exchangeId`), тип команды и `dealActionStateId`; первопричина — в `cause` |

**Триггерить реакцию по возвращённому результату запрещено.** Реакция,
привязанная к «неуспешному результату», встала бы на **первом**
ретраябельном таймауте, а бюджет попыток стал бы недостижим — это тот же
отказ, ради которого момент вызова был назначен перехватом, а не
результатом (`docs/components/HoldService.md` §«Момент вызова»).

**Как оркестратор отличает это от собственных исключений** (сборка
`DealContext`, прогон FSM, применение перехода) — двумя независимыми
признаками:

- **область перехвата** — выделенный `catch` стоит вокруг **шага 6**
  прохода (диспетчеризация команд), а не вокруг прохода целиком; сборка
  контекста и применение перехода в неё не попадают по построению;
- **имена типов** — выделенный `catch` ловит **поимённо**
  `ControlledExchangeException` и `RetryBudgetExhaustedException` и стоит
  **до** общего `catch (RuntimeException)`. Оба типа бросаются только
  отсюда; всё прочее уходит в общий обработчик и уводит сделку в `ERROR`
  штатным путём (`docs/components/DealOrchestratorJob.md` §«Перехват
  реакции»).

**`classify()` контролируемые исключения больше не схлопывает.** Прежде
`ControlledExchangeException` сводился к `VALIDATION_ERROR` и терял тип до
точки резолва — при таком схлопывании отображение «тип исключения →
реакция» неисполнимо. Тип сохраняется до броска; классификация кода ошибки
для `lastError` этому не мешает (пункт не-схемной дельты —
`docs/decisions/pnl-finalization-mechanics.md` §Следствия).

**Ветка `Deal.status = ERROR`: бросок радиусной реакции не поднимается,
но бюджет расходуется** (H3/H4 `DOCS_CHECK_15`, решение пользователя;
прежняя редакция «`attemptCount` не растёт» **снята**). Когда сделка уже в
`ERROR` (аварийная тропа), различаются две природы отказа — тем же
разграничителем, что и везде (`docs/rules/error-handling-policy.md`
§«Нарушение контракта интеграции — радиус по неизвестности поражения»):

| Природа отказа на аварийной тропе | Учёт | Бросок |
|---|---|---|
| **отказ канала добычи** (транспорт, парс, исчерпанный цикл) — радиус известен: обесценена отчётность, не управление риском | штатный: `attemptCount`++ → `RETRY_PENDING` → `FAILED` по исчерпанию | **нет**. Durable-исход — `FAILED` строки `REFRESH_DEAL_CONTEXT_ACTION`; он же разрешает эмиссию терминала (`docs/components/SystemActionExecutor.md` §«Вывод стадии») |
| **дефект содержимого ответа** (`ControlledExchangeException`) — радиус поражения неизвестен | non-retryable ⇒ `FAILED` сразу | **да**, как на штатной тропе: правило контролируемых исключений действует на обеих тропах одинаково (H4 `DOCS_CHECK_15`) |

- **Что снято.** Прежняя редакция приравнивала к «недоступно» **обе**
  природы и не расходовала бюджет. Первое снято: приравнивание
  контролируемого исключения к «недоступно» было молчаливым проглатыванием
  нарушения контракта там, где оно наблюдается впервые. Второе снято
  вместе с ним: без расхода бюджета у «приравненного недоступно» не
  оставалось **durable-носителя** — проход за проходом видел ту же пустоту,
  эмитил ту же команду и сделка зависала в `ERROR`, то есть ровно тот
  исход, ради которого асимметрия вводилась.
- **Прецедент учёта.** Тот же выбор уже сделан для легитимно-пустого
  исхода исчерпанного цикла (H8 `DOCS_CHECK_14`); аварийная тропа
  подведена под него, отдельного правила учёта у неё больше нет.
- **Что осталось от асимметрии.** Ровно одно: на аварийной тропе
  исчерпание бюджета **добычи** не поднимает радиусную реакцию (довод
  радиуса — позиция закрыта, обесценена отчётность). Дискриминатор
  прежний — `Deal.status` из `DealContext`, durable-факт, а не знание
  вызывающего; сменилось то, **чем** он управляет: не ростом счётчика, а
  броском.
- **Применитель — этот executor**, единственная ветка учёта отказов.
  Прежде приравнивание было сформулировано на
  `MarkDealEmergencyClosedExecutor`, который самого отказа **не
  наблюдает**; там осталась ссылка на применителя, а не правило.

Через этот retry/terminal-учёт проходит **и** брошенное executor'ом
исключение, **и** возвращённый (не брошенный) неуспешный результат —
`ServiceCommandExecutionResult.failure(...)` (ACK-реджект биржи), **и
легитимно-пустой исход исчерпанного цикла** refresh-команды (H8
`DOCS_CHECK_14`: исчерпанный цикл расходует `attemptCount` наравне с
отказом — исключение только у классов, где отрицательный ответ сам
является терминалом команды; `docs/rules/command-lifecycle.md`
следствие 3). Иначе
retry-anchor завис бы: сделка пере-сабмитила бы команду каждый тик (на
пустом исходе — вечно и молча). При
неуспешном результате `DealOrchestratorJob` прерывает цикл команд текущего
прохода (см. `docs/components/DealOrchestratorJob.md`).
