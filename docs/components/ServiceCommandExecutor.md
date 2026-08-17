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
  audit/history как runtime-source не использует. Для сущностей с
  evidence-cycle (`REFRESH_ORDER_COMMAND` / `REFRESH_ALGO_ORDER_COMMAND`)
  исполнитель обходит эндпоинты **внутри одной команды** (эскалация
  live → pending → history → archive), обрывается на первом успешном,
  полный обход — только при не-найдено, и сам выносит терминал
  (`MISSING_AFTER_REFRESH`); владение циклом —
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

**Ветка `Deal.status = ERROR`: жёсткий отказ не инкрементирует счётчик**
(H4 `GAPS_CLOSE_10`; носитель открыт H17 `DOCS_CHECK_11`). Когда сделка
уже в `ERROR` (аварийная тропа), жёсткий отказ чтения **приравнивается к
«недоступно»**, а не к провалу действия: `attemptCount` не растёт, путь
`RETRY_PENDING → FAILED` не запускается, и холд по нему не ставится.
Предикат — `Deal.status` из `DealContext`.

- **Применитель — этот executor**, единственная ветка учёта отказов.
  Прежде приравнивание было сформулировано на
  `MarkDealEmergencyClosedExecutor`, который самого отказа **не
  наблюдает**; там осталась ссылка на применителя, а не правило.
- **Цена, если ветки нет.** `FAILED` по `docs/rules/instrument-hold.md`
  означает «ошибочная тропа + холд инструмента» — то самое двойное
  прочтение одного отказа, которое H4 устранял; плюс сделка зависает в
  `ERROR` вопреки инварианту «всегда доходит до терминала»
  (`docs/lifecycles/Deal.md`).

Через этот retry/terminal-учёт проходит **и** брошенное executor'ом
исключение, **и** возвращённый (не брошенный) неуспешный результат —
`ServiceCommandExecutionResult.failure(...)` (ACK-реджект биржи). Иначе
retry-anchor завис бы: сделка пере-сабмитила бы команду каждый тик. При
неуспешном результате `DealOrchestratorJob` прерывает цикл команд текущего
прохода (см. `docs/components/DealOrchestratorJob.md`).
