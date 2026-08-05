# FinalizeDealExitExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `FINALIZE_DEAL_EXIT_COMMAND` (компонент-executor): что читает/пишет,
терминальное ребро, идемпотентность, retry-anchor, граница 6 ↔ 7.

## Назначение

Получает `FINALIZE_DEAL_EXIT_COMMAND` — консолидацию фактов штатного выхода после
того, как live risk снят, **и расчёт итогового `resultProfit`** (шаг 7).
**Читает** подтверждённые факты выхода (`Position` закрыта/отсутствует по
`REFRESH_POSITION_COMMAND`; нет live orders/algo; `Deal.CloseReason` определён) плюс
P&L-факты: `DealCashFlow` (категорийная разбивка, добыта **до него**
командой `REFRESH_BILLS_COMMAND`) и **положение закрытия на `Position`** (готовый
net `realizedPnl` в поле `Position.externalRealizedProfit`) — см.
§«Положение закрытия — читается со строки». Для **допуска сверки**
дочитывает операнды планового риска (`Deal.plannedRiskAmount`;
`plannedEntryPrice`/`plannedSizeContracts` ноги входа; уровень стопа её
защиты) и `ctVal` навеса — §«Сверка bills ↔ net». **Вычисляет** net-число +
сверяет сумму `DealCashFlow` с net
(`docs/decisions/result-profit-source.md`); **пишет
`resultProfit`/`resultProfitCurrency` прямо на `Deal`** (persisted) в
**одной транзакции** с durable-продвижением своего звена в исполнении
`FINALIZE_DEAL_EXIT_ACTION` (N7 — durable-носитель числа = поле `Deal`,
рестарт-safe; транзакционная клауза —
`docs/decisions/command-action-boundary.md` §5). На биржу **сам не ходит**
— интеграционное чтение остаётся за `REFRESH_*`-командами; `RiskValidator`
не вызывается (`docs/rules/risk-validator-scope.md`).

## Положение закрытия — читается со строки

Биржевой факт закрытия **персистится на `Position`** второй ногой
`REFRESH_POSITION_COMMAND` (H1/H3, `GAPS_CLOSE_7`;
`docs/models/domain/core/Position.md` §«Положение закрытия»). Для
финализатора это значит:

- **никакой вложенной команды.** Прежняя редакция (H13, `GAPS_CLOSE_6`)
  исполняла `REFRESH_POSITIONS_HISTORY` **вложенным шагом внутри**
  `FINALIZE_DEAL_EXIT_COMMAND`, потому что считала снапшот транзитным и не
  пересекающим границу прохода. Посылка снята: факт durable, и
  «команда внутри команды» — конструкция, которой канон не знает
  (`docs/rules/command-lifecycle.md`). Аналогия «как REPLACE композирует
  свои ноги» была ложной: ноги REPLACE секвенсит **петля по проходам**
  (`docs/decisions/action-orchestration-vs-command.md`);
- **финализатор читает `Position` как обычные входные факты** — тем же
  чтением, которым берёт `Position.status` и `DealCashFlow`;
- **идемпотентность — на уровне действия**, анкер — строка исполнения
  **`FINALIZE_DEAL_EXIT_ACTION`** (вид SYSTEM,
  `docs/models/domain/other/DealActionState.md`). Рестарт до завершения
  перезапускает звено: число пересчитывается из тех же persisted-полей и
  пишется в той же транзакции. Рестарт после завершения — no-op.
- **поля пусты (запись закрытия не добыта) → звено не эмитится и не
  завершается** (узел 4 `DOCS_CHECK_8`, вариант (а)):
  `FINALIZE_DEAL_EXIT_COMMAND` эмитится `SystemActionExecutor` только по
  **терминальному исходу добычи** (`REFRESH_DEAL_CONTEXT_ACTION` довёл
  цикл), а без числа не завершается. Исчерпание бюджета добычи →
  ошибочная тропа + холд инструмента. Тропа «неисчислимо» — только
  аварийный контур (`docs/decisions/pnl-finalization-mechanics.md`
  §«Асимметрия троп отказа добычи»).

**Что это не меняет.** Отвергнутый в реш.1 вариант «fetch внутри
`FinalizeDealExitExecutor`» остаётся отвергнутым: на биржу ходит
**refresh-команда** со своим retry и своей идемпотентностью, финализатор
читает уже приземлённый факт. Паттерн «refresh populates → finalize
consolidates» сохранён и стал буквальным: populate пишет в свою строку,
consolidate её читает. `REFRESH_BILLS_COMMAND` — тоже **отдельная** эмитируемая
команда: её факт durable (`DealCashFlow` в БД).

## Расчёт прибыли (шаг 7) и сверка

**Расчёт `Deal.resultProfit` — здесь.** Шаг 6 поставил *механику* финализации
(retry-state, терминальное ребро, триггер, идемпотентность) и
интерим-placeholder ZERO; шаг 7 наделяет `FINALIZE_DEAL_EXIT_COMMAND` **расчётом и
записью числа** на `Deal`: net из `Position.externalRealizedProfit` +
разбивка из `DealCashFlow` + сверка. `MARK_DEAL_CLOSED_COMMAND`
(`MarkDealClosedExecutor`) число **не пишет** — читает готовое `Deal.resultProfit`,
ассертит непустоту и ставит терминал `CLOSED` (N7). Placeholder ZERO снят.
- **Сверка bills ↔ net (N10):** число **всегда** = positions-history net (bills
  его не подменяют). Сверяется Σ`amount` **по строкам settle-ccy**.
  Расхождение **сверх epsilon** → **`AnomalyReport`** (аудит-аномалия,
  `scope = INSTRUMENT`, `severity = NON_CRITICAL` — тропа без kill-switch,
  `docs/lifecycles/AnomalyReport.md`) — **не блокирует** финализацию, сделка
  идёт в `CLOSED` (`docs/decisions/pnl-finalization-mechanics.md` реш.5).
  Аномалию **по валюте** финализатор не ставит: она ставится раньше, на
  записи движения (`RefreshBillsExecutor`, H4 `GAPS_CLOSE_7`) — там же, где
  доступен операнд сравнения (расчётная валюта инструмента) и где движение
  пересчитывается.
- **Epsilon — форма и операнды.**
  `epsilon = max( 0.01 settle-ccy, min( 0.5% × Σ|amount|, k × ожидаемая
  комиссия сделки ) )` (H15 `DOCS_CHECK_10`; пол вынесен наружу — H8
  `DOCS_CHECK_11`). **Ожидаемая комиссия считается из persisted-операндов
  сделки, ставка не перечитывается** (H7 `DOCS_CHECK_11`):
  `plannedRiskAmount − |plannedEntryPrice − stop| × plannedSizeContracts ×
  ctVal`, где `plannedRiskAmount` — поле `Deal`, `plannedEntryPrice` /
  `plannedSizeContracts` — поля **ноги входа** (`Order`/`AlgoOrder`, H3),
  уровень стопа — на защите этой ноги, `ctVal` — единственное, что
  финализатор дочитывает из `InstrumentExternalRules`
  (`docs/components/InstrumentExternalRulesDataService.md` §Использование).
  `TradeFeeRate` финализатор **не читает** — от её свежести и доступности
  epsilon не зависит. Операнд планового риска пуст (популяция
  «позиция создана вне приложения», H23) ⇒ омиссионный член не участвует,
  `epsilon` вырождается в композиционный, факт помечается `AnomalyReport`.
  Разбор — `docs/decisions/pnl-finalization-mechanics.md` реш.5 §epsilon.
- **Область сверки — за вычетом исключённых типов (H14 `DOCS_CHECK_10`):**
  Σ`amount` идёт по строкам settle-ccy **вне списка исключений биржи**
  (типы, не принадлежащие экономике сделки). Плюс отдельная проверка:
  **непустая корзина `OTHER`** у сделки → `AnomalyReport`
  `UNCLASSIFIED_CASH_FLOW` — так новый тип источника обнаруживается сам,
  а не раздувает сумму молча
  (`docs/models/mapping/DealCashFlow.md` §«Область сверки задаётся списком
  исключений по бирже»).
- **Валюта результата — пишется по авторитету, биржевая сверяется
  (H10 `DOCS_CHECK_10`):** `Deal.resultProfitCurrency` берётся из
  **расчётной валюты инструмента** — поля
  `Instrument.externalSettlementCurrency`, читаемого с
  `DealContext.instrument` (H6 `DOCS_CHECK_11`,
  `docs/decisions/instrument-currencies-home.md`; тот же резолв, что у
  `RefreshBillsExecutor`, навес `InstrumentExternalRules` не
  задействуется), а `Position.externalResultCurrency`
  **сверяется** с ней; не совпало → `AnomalyReport`
  `RESULT_CURRENCY_MISMATCH` (`severity = NON_CRITICAL`), **расчёт не
  блокируется и терминал проходит**. Операнд пуст → валюта берётся с
  `Position` + `AnomalyReport` `SETTLE_CURRENCY_UNAVAILABLE` (явная
  деградация с пометкой, `docs/models/domain/aggregate/Deal.md` §«Валюта
  результата: один авторитет»).
- **Число — в settle-ccy целиком (H5, `GAPS_CLOSE_6`):**
  `resultProfit` = net из положения закрытия **+ Σ(`amount` × `appliedRate`)**
  по строкам чужой `ccy`. Биржевой net считается в settle-ccy и издержку,
  уплаченную вне неё, не содержит — без слагаемого число завышало бы
  результат молча. Курс уже зафиксирован на записи, поэтому финализатор
  котировку **не запрашивает** — контракт «на биржу сам не ходит» держится
  (CCY-Q1 закрыт, H4 `GAPS_CLOSE_7`).
- Внутренняя декомпозиция расчёта (выделять ли отдельный калькулятор) — деталь
  CODE шага 7. Структуры носителей —
  `docs/models/mapping/PositionCloseResult.md`,
  `docs/models/domain/other/DealCashFlow.md`.

## Терминальное ребро

Не терминал сделки. Поддерживает выходную проверку `EXIT_PENDING → CLOSED`
(`docs/components/ExitPendingHandler.md`), готовя факты к `MARK_DEAL_CLOSED_COMMAND`.

## Идемпотентность и retry

- **Retry-anchor** — строка исполнения `FINALIZE_DEAL_EXIT_ACTION`
  (вид SYSTEM, база `Retryable`;
  `docs/models/domain/other/DealActionState.md`,
  `docs/decisions/command-action-boundary.md`).
- **Идемпотентность** — частичный ключ живого исполнения (`deal_id`,
  `system_action_type`) + факты: повтор на уже консолидированном выходе —
  no-op.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; эмиссия звеньев —
`docs/components/SystemActionExecutor.md`.
