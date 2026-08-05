# MarkDealEmergencyClosedExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_EMERGENCY_CLOSED_COMMAND` (компонент-executor): аварийное
терминальное ребро, что читает/пишет, best-effort число и его провенанс,
идемпотентность, retry-anchor.

## Назначение

Получает `MARK_DEAL_EMERGENCY_CLOSED_COMMAND` — **аварийное терминальное ребро**
`ERROR → EMERGENCY_CLOSED`, **симметрично `MARK_DEAL_CLOSED_COMMAND`** (штатному
терминалу). **Читает** подтверждённое отсутствие live risk (снято/доказано
`ErrorHandler` перед терминалом, `docs/components/ErrorHandler.md`) и
**положение закрытия на `Position`** — persisted-поля, наполненные второй
ногой `REFRESH_POSITION_COMMAND` (H1/H3, `GAPS_CLOSE_7`; симметрично
`docs/components/FinalizeDealExitExecutor.md` §«Положение закрытия —
читается со строки»). Вложенной команды нет: факт durable, границу прохода
FSM пересекает штатно, добыл его тот же `REFRESH_POSITION_COMMAND`, которым
`ErrorHandler` доказывал отсутствие позиции. **Пишет** терминал
`Deal.status = EMERGENCY_CLOSED` +
`closeReason = EMERGENCY_CLOSE` + **best-effort число** `resultProfit`/
`resultProfitCurrency` — в одной транзакции с завершением своего
исполнения `FINALIZE_DEAL_ERROR_ACTION` (второе исполнение действия —
терминал; `docs/components/SystemActionExecutor.md`). Торговых решений не
принимает; `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Число — best-effort, провенанс (реш.3)

Число на аварийном терминале — **best-effort**, два провенанса разведены
(`docs/decisions/pnl-finalization-mechanics.md` §3, `docs/lifecycles/Deal.md`
§«Терминальный контракт финализации»):

- **(a) реальная ликвидация/ADL** (позицию закрыла биржа —
  `Position.externalCloseType ∈ 3..6`): `realizedPnl` доступен полем
  `Position.externalRealizedProfit` → число считается **по той же формуле,
  что на чистой тропе**: net **плюс cross-ccy-слагаемое**
  Σ(`amount` × `appliedRate`) по строкам с `rateStatus = APPLIED`
  (§«Состав числа — тот же, что у штатной тропы»).
- **(b) net недоступен** (чистая тропа не смогла посчитать → ушла в `ERROR`;
  поля положения закрытия на `Position` пусты — записи нет либо `Position`
  локально не заводилась): `resultProfit = null` с семантикой
  **«неисчислимо»** (**НЕ ноль**); сделка терминализуется **всё равно** (не
  зависает живым риском), факт помечается лог + `AnomalyReport`
  (`severity = NON_CRITICAL` — журнальная тропа без kill-switch,
  `docs/lifecycles/AnomalyReport.md`).
- **Жёсткий отказ чтения приравнивается к «пусто»** на этой тропе — не к
  провалу действия (H15, `GAPS_CLOSE_7`;
  `docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия троп отказа
  добычи»). Иначе исполнение уходит в `FAILED`, `SystemActionExecutor`
  команду больше не эмитит, и сделка зависает в `ERROR` вопреки инварианту
  «сделка всегда доходит до терминала».
  - **Применяет приравнивание не этот executor**, а
    `ServiceCommandExecutor` — единственная ветка учёта отказов; предикат
    — `Deal.status = ERROR` из `DealContext`
    (`docs/components/ServiceCommandExecutor.md` §Retry, ветка
    `Deal.status = ERROR`). Здесь — только ссылка: этот executor самого
    отказа **не наблюдает** (H4 `GAPS_CLOSE_10`; носитель открыт H17
    `DOCS_CHECK_11`).

## Состав числа — тот же, что у штатной тропы (H12 `DOCS_CHECK_10`)

**Best-effort относится к доступности числа, а не к его составу.** Когда
net доступен, аварийный терминал пишет `resultProfit` по **той же
формуле**, что и `FinalizeDealExitExecutor`: net из
`Position.externalRealizedProfit` **+ Σ(`amount` × `appliedRate`)** по
строкам чужой `ccy` (`docs/models/domain/aggregate/Deal.md` §«Итоговый
PnL»). Прежде здесь писался «фактический realized net» без слагаемого — и
расхождение с определением поля нигде не было помечено как сознательное,
в отличие от асимметрии троп отказа (§5a
`docs/decisions/pnl-finalization-mechanics.md`), записанной явно.

- **Операнды на аварийной тропе есть.** `ErrorHandler` эмитит
  bills-звено, `appliedRate` зафиксирован **на записи** движения
  (`docs/components/RefreshBillsExecutor.md` §Пересчёт) — терминал за
  котировкой на биржу **не ходит**, контракт «терминал off-exchange»
  держится. Цена складывается из одного: чтение строк `deal_cash_flows`
  по `deal_id` (индекс уже есть). Новых полей, вызовов биржи и миграций
  правка не добавляет.
- **Довод против асимметрии — направление смещения.** Отсутствие
  слагаемого завышает число ровно на величину издержки, уплаченной вне
  settle-ccy, и завышает **молча**; аварийная корзина при этом и так
  смещена (`pnl-finalization-mechanics.md` реш.3 — пропуск null'ов
  outcome-коррелирован). Два смещения в одну сторону не гасят друг друга.
- **Валюта — по общему авторитету** (H10): `resultProfitCurrency`
  берётся из расчётной валюты инструмента —
  `Instrument.externalSettlementCurrency`, читаемого с
  `DealContext.instrument` (H6 `DOCS_CHECK_11`,
  `docs/decisions/instrument-currencies-home.md`); тропа та же, что у
  штатного финализатора и у `RefreshBillsExecutor`.
  `Position.externalResultCurrency` сверяется как признак
  (`docs/models/domain/aggregate/Deal.md` §«Валюта результата: один
  авторитет»).
- **Сверку bills ↔ net аварийный терминал не гоняет** — **ни на одной из
  двух ветвей** (H22 `DOCS_CHECK_11`, решение пользователя). Разбивка на
  этой тропе заведомо неполна; на ветке (b) `AnomalyReport` о расхождении
  дублировал бы `RESULT_PROFIT_UNAVAILABLE`, на ветке (a) — звенел бы на
  заведомо неполных данных. Слагаемое от сверки не зависит: оно считается
  по строкам, которые есть.
  - **Прежний довод покрывал только ветку (b)** и был дан безусловно: на
    ветке (a) число **есть**, `RESULT_PROFIT_UNAVAILABLE` **не ставится**
    (его условие — `null`), дублировать нечего. Отсутствие сверки на этой
    ветке теперь — не следствие довода про дубль, а **самостоятельное
    решение**.
  - **Признак «не проверено» вписывается в саму аварийную запись** —
    отдельного отчёта о непроверенности **нет**: `AnomalyReport` аварийного
    терминала несёт признак того, что число этой сделки контроль
    целостности не проходило. **Одна запись на эпизод**, а не одна на
    ветку и не одна на строку: эпизод — это авария, и её описывает один
    факт.
  - **Числа аварийной ветки выводятся из популяции явно.** Сделка,
    завершённая аварийным терминалом, в R-выборку **не входит** — не
    молчаливым выпадением, а по признаку из той же аварийной записи.
    Иначе непроверенное число попало бы в корзину, которая уже признана
    **outcome-коррелированной** (в net ветки (a) входит `liqPenalty`, и
    экономика закрытия там наиболее нестандартна), — то есть смещение
    попало бы туда, где оно дороже всего
    (`docs/models/domain/aggregate/Deal.md` §«Рамка R-выборки»).
  - **Почему не «гонять сверку на (a)».** Операнды на тропе есть (строки
    `deal_cash_flows` уже читаются ради cross-ccy-слагаемого), то есть
    технически вариант дешёв. Отвергнут по существу: разбивка неполна **по
    построению обеих ветвей**, поэтому сверка дала бы шум, а не сигнал, и
    `EVENT`-журнал, на счётности которого стоит рамка R-выборки,
    обесценился бы. Вместо контроля — явный вывод из популяции.

**Маркер — nullability** (без нового поля): на `EMERGENCY_CLOSED` `resultProfit
!= null` = фактический net; `resultProfit == null` = «неисчислимо» — **отличимо
от посчитанного нуля** (ноль = вычисленный нулевой P&L). **Число не зануляется**
— недоступность помечается, не подменяется нулём (F-T1: null-случай исключается
из R-выборки как unknown, левый хвост не усекается молча). Инвариант
«`resultProfit` обязателен» — про чистое `CLOSED`; на аварийном терминале он
**не блокируется**.

## Терминальное ребро

`ERROR → EMERGENCY_CLOSED` (`docs/lifecycles/Deal.md`). `EMERGENCY_CLOSED` —
**ошибочный terminal**: FSM handler'а не имеет. Executor ставит терминал только
после подтверждённого `ErrorHandler`'ом снятия live risk (иначе — не
терминализирует, сделка остаётся под safety-flow в `ERROR`). Команду эмитит
`ErrorHandler`.

## Идемпотентность и retry

- **Retry-anchor** — строка исполнения `FINALIZE_DEAL_ERROR_ACTION`
  (второе исполнение действия; вид SYSTEM, база `Retryable`;
  `docs/models/domain/other/DealActionState.md`).
- **Идемпотентность** — факт `Deal.status = EMERGENCY_CLOSED`: повтор на
  уже терминализованной сделке — no-op.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; эмиссия звеньев —
`docs/components/SystemActionExecutor.md`.
