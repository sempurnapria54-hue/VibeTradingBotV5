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
командой `REFRESH_BILLS`) и `PositionCloseResultExternalSnapshot` (готовый
net `realizedPnl`), который добывается **вложенным шагом самого действия** —
см. §«Снапшот числа — вложенный шаг, in-memory». **Вычисляет** net-число +
сверяет сумму `DealCashFlow` с net
(`docs/decisions/result-profit-source.md`); **пишет
`resultProfit`/`resultProfitCurrency` прямо на `Deal`** (persisted) в **одной
транзакции** с `DealFinalizationState(FINALIZE_EXIT).status = COMPLETED` (N7 —
durable-носитель числа = поле `Deal`, рестарт-safe). На биржу **сам не ходит**
— интеграционное чтение остаётся за `REFRESH_*`-командами; `RiskValidator` не
вызывается (`docs/rules/risk-validator-scope.md`).

## Снапшот числа — вложенный шаг, in-memory

`PositionCloseResultExternalSnapshot` **транзитен и durable-дома не имеет**
(`docs/models/mapping/PositionCloseResult.md`). Факт без durable-дома
**границу прохода FSM не пересекает** — иначе между эмиссией
`REFRESH_POSITIONS_HISTORY` отдельным проходом и `FINALIZE_DEAL_EXIT`
следующим проходом снапшоту негде жить. Поэтому (H13, `GAPS_CLOSE_6`):

- `REFRESH_POSITIONS_HISTORY` исполняется **вложенным шагом внутри**
  `FINALIZE_DEAL_EXIT`, а не отдельным проходом. Действие композирует
  существующую команду — оркестрация команд, как REPLACE композирует свои
  ноги (`docs/decisions/replace-not-amend.md`);
- снапшот живёт **в памяти** внутри выполнения действия и наружу не
  выставляется;
- **идемпотентность — на уровне действия**, анкер прежний
  `DealFinalizationState(deal, FINALIZE_EXIT)`. Рестарт до `COMPLETED`
  перезапускает действие **целиком**: вложенный refresh перечитывает
  (идемпотентное чтение), число пересчитывается и пишется в той же
  транзакции. Рестарт после `COMPLETED` — no-op.

**Что это не меняет.** Отвергнутый в реш.1 вариант «fetch внутри
`FinalizeDealExitExecutor`» остаётся отвергнутым: на биржу ходит по-прежнему
**refresh-команда** со своим retry и своей идемпотентностью, а финализатор её
**зовёт**, а не подменяет. Паттерн «refresh populates → finalize
consolidates» сохранён — сдвинулась только граница: populate отдаёт результат
**в память вызывающего действия**, а не в durable-слот, которого у этого
факта нет. `REFRESH_BILLS` остаётся **отдельной** эмитируемой командой: её
факт durable (`DealCashFlow` в БД), границу прохода он пересекает штатно.

## Расчёт прибыли (шаг 7) и сверка

**Расчёт `Deal.resultProfit` — здесь.** Шаг 6 поставил *механику* финализации
(retry-state, терминальное ребро, триггер, идемпотентность) и
интерим-placeholder ZERO; шаг 7 наделяет `FINALIZE_DEAL_EXIT` **расчётом и
записью числа** на `Deal`: net из `PositionCloseResultExternalSnapshot` +
разбивка из `DealCashFlow` + сверка. `MARK_DEAL_CLOSED`
(`MarkDealClosedExecutor`) число **не пишет** — читает готовое `Deal.resultProfit`,
ассертит непустоту и ставит терминал `CLOSED` (N7). Placeholder ZERO снят.
- **Сверка bills ↔ net (N10):** число **всегда** = positions-history net (bills
  его не подменяют). Сверяется Σ`amount` **по строкам settle-ccy**.
  Расхождение **сверх epsilon** (якорь — валовой оборот Σ`|amount|`, не
  `|net|`) или наличие cross-ccy движения (`ccy ≠ resultProfitCurrency` —
  **нарушение инварианта** «комиссии в settle-ccy»,
  `docs/rules/trading-constraints.md`) → **`AnomalyReport`** (аудит-аномалия,
  `scope = INSTRUMENT`, `severity = NON_CRITICAL` — тропа без kill-switch,
  `docs/lifecycles/AnomalyReport.md`) — **не блокирует** финализацию, сделка
  идёт в `CLOSED` (`docs/decisions/pnl-finalization-mechanics.md` реш.5).
- **Число — в settle-ccy целиком (H5, `GAPS_CLOSE_6`):**
  `resultProfit` = net из positions-history **+ Σ(`amount` × курс закрытия)**
  по строкам чужой `ccy`. Биржевой net считается в settle-ccy и издержку,
  уплаченную вне неё, не содержит — без слагаемого число завышало бы
  результат молча. Механизм получения курса — открытая развилка CCY-Q1
  (`.claude/work/questions/open-questions.md`).
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
