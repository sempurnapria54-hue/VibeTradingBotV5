# FinalizeDealExitExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `FINALIZE_DEAL_EXIT` (компонент-executor): что читает/пишет,
терминальное ребро, идемпотентность, retry-anchor, граница 6 ↔ 7.

## Назначение

Получает `FINALIZE_DEAL_EXIT` — консолидацию фактов штатного выхода после
того, как live risk снят, **и расчёт итогового `resultProfit`** (шаг 7).
**Читает** подтверждённые факты выхода (`Position` закрыта/отсутствует по
`REFRESH_POSITION`; нет live orders/algo; `Deal.CloseReason` определён) плюс
P&L-факты: `DealCashFlow` (категорийная разбивка, добыта **до него**
командой `REFRESH_BILLS`) и **положение закрытия на `Position`** (готовый
net `realizedPnl` в поле `Position.externalRealizedProfit`) — см.
§«Положение закрытия — читается со строки». **Вычисляет** net-число +
сверяет сумму `DealCashFlow` с net
(`docs/decisions/result-profit-source.md`); **пишет
`resultProfit`/`resultProfitCurrency` прямо на `Deal`** (persisted) в **одной
транзакции** с `DealFinalizationState(FINALIZE_EXIT).status = COMPLETED` (N7 —
durable-носитель числа = поле `Deal`, рестарт-safe). На биржу **сам не ходит**
— интеграционное чтение остаётся за `REFRESH_*`-командами; `RiskValidator` не
вызывается (`docs/rules/risk-validator-scope.md`).

## Положение закрытия — читается со строки

Биржевой факт закрытия **персистится на `Position`** второй ногой
`REFRESH_POSITION` (H1/H3, `GAPS_CLOSE_7`;
`docs/models/domain/core/Position.md` §«Положение закрытия»). Для
финализатора это значит:

- **никакой вложенной команды.** Прежняя редакция (H13, `GAPS_CLOSE_6`)
  исполняла `REFRESH_POSITIONS_HISTORY` **вложенным шагом внутри**
  `FINALIZE_DEAL_EXIT`, потому что считала снапшот транзитным и не
  пересекающим границу прохода. Посылка снята: факт durable, и
  «команда внутри команды» — конструкция, которой канон не знает
  (`docs/rules/command-lifecycle.md`). Аналогия «как REPLACE композирует
  свои ноги» была ложной: ноги REPLACE секвенсит **петля по проходам**
  (`docs/decisions/action-orchestration-vs-command.md`);
- **финализатор читает `Position` как обычные входные факты** — тем же
  чтением, которым берёт `Position.status` и `DealCashFlow`;
- **идемпотентность — на уровне действия**, анкер прежний
  `DealFinalizationState(deal, FINALIZE_EXIT)`. Рестарт до `COMPLETED`
  перезапускает действие: число пересчитывается из тех же persisted-полей
  и пишется в той же транзакции. Рестарт после `COMPLETED` — no-op.
- **поля пусты** (запись закрытия не добыта) → число не считается; сделка
  идёт тропой «неисчислимо», выходную проверку `EXIT_PENDING → CLOSED` это
  не гейтит (`docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия
  троп отказа добычи»).

**Что это не меняет.** Отвергнутый в реш.1 вариант «fetch внутри
`FinalizeDealExitExecutor`» остаётся отвергнутым: на биржу ходит
**refresh-команда** со своим retry и своей идемпотентностью, финализатор
читает уже приземлённый факт. Паттерн «refresh populates → finalize
consolidates» сохранён и стал буквальным: populate пишет в свою строку,
consolidate её читает. `REFRESH_BILLS` — тоже **отдельная** эмитируемая
команда: её факт durable (`DealCashFlow` в БД).

## Расчёт прибыли (шаг 7) и сверка

**Расчёт `Deal.resultProfit` — здесь.** Шаг 6 поставил *механику* финализации
(retry-state, терминальное ребро, триггер, идемпотентность) и
интерим-placeholder ZERO; шаг 7 наделяет `FINALIZE_DEAL_EXIT` **расчётом и
записью числа** на `Deal`: net из `Position.externalRealizedProfit` +
разбивка из `DealCashFlow` + сверка. `MARK_DEAL_CLOSED`
(`MarkDealClosedExecutor`) число **не пишет** — читает готовое `Deal.resultProfit`,
ассертит непустоту и ставит терминал `CLOSED` (N7). Placeholder ZERO снят.
- **Сверка bills ↔ net (N10):** число **всегда** = positions-history net (bills
  его не подменяют). Сверяется Σ`amount` **по строкам settle-ccy**.
  Расхождение **сверх epsilon** (якорь — валовой оборот Σ`|amount|`, не
  `|net|`) → **`AnomalyReport`** (аудит-аномалия,
  `scope = INSTRUMENT`, `severity = NON_CRITICAL` — тропа без kill-switch,
  `docs/lifecycles/AnomalyReport.md`) — **не блокирует** финализацию, сделка
  идёт в `CLOSED` (`docs/decisions/pnl-finalization-mechanics.md` реш.5).
  Аномалию **по валюте** финализатор не ставит: она ставится раньше, на
  записи движения (`RefreshBillsExecutor`, H4 `GAPS_CLOSE_7`) — там же, где
  доступен операнд сравнения (расчётная валюта инструмента) и где движение
  пересчитывается.
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
(`docs/components/ExitPendingHandler.md`), готовя факты к `MARK_DEAL_CLOSED`.

## Идемпотентность и retry

- **Retry-anchor** — `DealFinalizationState(deal, FINALIZE_EXIT)` (база
  `Retryable`, см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- **Идемпотентность** — через `UNIQUE(deal_id, finalization_type)`: повтор
  на уже консолидированном выходе — no-op → `COMPLETED`.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; модель retry-state —
`docs/models/domain/other/DealFinalizationState.md`.
