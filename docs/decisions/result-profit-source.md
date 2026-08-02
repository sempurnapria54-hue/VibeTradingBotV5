# Источник истины `Deal.resultProfit` (число и разбивка)

## На какой вопрос отвечает этот файл

Откуда берётся `Deal.resultProfit` — итоговое число и категорийная
разбивка — и почему именно так, а не иначе.

## Контекст

Шаг 7 фазы 1 считает **число** `resultProfit` на терминале (замена
step-6 интерим-placeholder ZERO) и агрегирует факты исполнения в `Deal`.
На `DOCS_CHECK_1` шага 7 (2026-07-03) вскрыт центральный блокер **G1**:
три дока указывали на три несводимых источника числа —

- fills / `TradeFill` (`Deal.md` §Итоговый PnL, категорично «через
  `REFRESH_FILLS` / `TradeFill`»),
- bills / `DealCashFlow` (`account-bills.md`: `resultProfit =
  sum(DealCashFlow.amount)`),
- positions-history / `realizedPnl` (`position.md` §История),

— прямое doc↔doc противоречие (гейт стадии 0). Торговый инвариант
(trading TR-1): торгово-осмысленное «число прибыли сделки» — **net** от
всех издержек (торговые комиссии + funding на SWAP + ликвидационный штраф
на аварии); иначе downstream-ожидаемость / R-распределение завышены.
fills-путь торгово неполон.

## Решение

- **Заголовочное число** `resultProfit` = **net realized P&L**, берётся
  **готовым** из positions-history (`GET /api/v5/account/positions-history`):
  `realizedPnl = pnl + fee + fundingFee + liqPenalty` — посчитан **биржей**,
  одним запросом, без нашей композиции слагаемых.
- **Категорийная разбивка** (торговая комиссия / funding / rebate /
  ликвидационный штраф) — из bills (`GET /api/v5/account/bills[-archive]`),
  доменно `DealCashFlow`.
- **Контроль целостности:** сумма bills-flows **в settle-ccy** сверяется с net
  из positions-history. Расхождение сверх epsilon (и наличие cross-ccy
  движения) → **`AnomalyReport`**, **не блокирует** финализацию (число
  авторитетно = net; N10, `docs/decisions/pnl-finalization-mechanics.md`
  реш.5).
- **Валюта** — `resultProfitCurrency` (для `ETH-USDT-SWAP` — `USDT`).
- **Число — в settle-ccy целиком** (H5, `GAPS_CLOSE_6`; момент курса
  уточнён H4, `GAPS_CLOSE_7`): `resultProfit` = net из positions-history
  **+ эквивалент движений чужой `ccy`**, пересчитанных по курсу на **момент
  обработки движения** (курс фиксируется полем `DealCashFlow.appliedRate`;
  редакция «по курсу на момент закрытия» снята). Биржевой net считается в
  settle-ccy и издержку, уплаченную вне неё, не содержит — без этого
  слагаемого число завышало бы результат молча. Cross-ccy — нарушение
  инварианта (`docs/rules/trading-constraints.md` §«Валюта комиссии»),
  поэтому слагаемое **сопровождается** `AnomalyReport`, а не заменяет его.
  Механизм курса — **CCY-Q1 закрыт** (`docs/decisions/pnl-finalization-mechanics.md`
  реш.5).
- **Аварийный/ликвидационный терминал (`EMERGENCY_CLOSED`, G5):** число =
  **фактический realized net** (вкл. `liqPenalty`) если доступен из
  positions-history (`realizedPnl`; провенанс ликвидации/ADL — по `type` и
  опциональному `triggerPx`, применимость которого держит единственный
  носитель `docs/integrations/okx/contracts/position.md` §История);
  если недоступен (провенанс отказа расчёта) —
  best-effort **`null` с маркером «неисчислимо»**, **не ноль**
  (провенанс-контракт разведён на `GAPS_CLOSE_2`, N8 —
  `docs/decisions/pnl-finalization-mechanics.md` реш.3,
  `docs/lifecycles/Deal.md` §«Терминальный контракт финализации»).
- **Целевые носители пути (G2) — материализованы (`GAPS_CLOSE_2`):**
  граничный `PositionCloseResultExternalSnapshot`, приземляющийся полями
  положения закрытия на **`Position`**
  (`docs/models/mapping/PositionCloseResult.md`, число) + `DealCashFlow`
  (`docs/models/domain/other/DealCashFlow.md` + `docs/models/mapping/DealCashFlow.md`,
  разбивка); native — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.
- **Компонент-владелец расчёта (G3) + механика — `GAPS_CLOSE_2`:**
  `FinalizeDealExitExecutor` **вычисляет** net-число + собирает разбивку + сверку
  и **пишет `resultProfit` прямо на `Deal`** (в одной транзакции с `COMPLETED` —
  durable-носитель = поле `Deal`, N7); `MarkDealClosedExecutor` **ассертит** и
  ставит терминал `CLOSED` (число не пишет; **placeholder-ZERO снят**). Добыча
  фактов — вторая нога `REFRESH_POSITION_COMMAND` (положение закрытия → поля
  `Position`) и команда `REFRESH_BILLS_COMMAND` (разбивка → `DealCashFlow`). Полная
  механика — `docs/decisions/pnl-finalization-mechanics.md`.

## Отвергнутые источники / альтернативы

- **fills-only (`TradeFill`)** — отвергнут: `OkxFillResponse` несёт только
  `fee`/`feeCcy`, **ни** `fundingFee`, **ни** `liqPenalty` → торгово неполон
  (число завышено на funding/liqPenalty). Тождественен варианту «fills-only —
  проще, но менее точно», уже отклонённому в OKX-Q3.
- **bills-sum как единственный источник числа** — отклонён как
  *первоисточник*: positions-history отдаёт **готовый net одним запросом**
  (биржа уже сложила слагаемые); заставлять нас складывать `sum(bills)`
  вместо готового `realizedPnl` — лишняя реконструкция и лишний риск
  расхождения. Bills остаются **разбивкой + независимой сверкой**, не
  первичным «сложи сам».
- **positions-history без bills** — недостаточно для аудита/анализа: нет
  категорийной атрибуции (сколько комиссии vs funding vs штраф). Разбивка
  нужна отдельным носителем.
- **funding через `funding-rate-history`** (OKX-Q3, путь 2) — для числа
  **не ведём**: `realizedRate` — ставки расчётных периодов без привязки к
  позиции; фактический funding уже сидит в positions-history (внутри net) и
  в bills (`subType` 173/174). `funding-rate-history` — прогноз/сверка, не
  источник P&L. **OKX-Q3 закрыт.**

## Персист fills (OKX-Q1)

- Persisted per-fill `TradeFill` **не вводим**; пофилловый аудит — вне
  фазы 1. **OKX-Q1 закрыт.** `docs/models/mapping/TradeFill.md` (стаб)
  приведён в соответствие.
- **`RefreshFillsExecutor` / `REFRESH_FILLS`:** число P&L больше не через
  fills. Инспекция native: positions-history несёт **`closeAvgPx`** (средняя
  цена выхода) и `openAvgPx` (`docs/integrations/okx/contracts/position.md`
  §История) → fills для avg-цены выхода **не нужны**; order-level fill-метрики
  (`accFillSz`/`avgPx`) доступны прямо из `OkxOrderResponse` (`REFRESH_ORDER_COMMAND`).
  ⇒ `REFRESH_FILLS` **снимается** — решение принято на `GAPS_CLOSE_2` (N12),
  **исполнение — на `CODE` шага 7**: убрать из `ServiceCommandType`, удалить
  `RefreshFillsExecutor`, провести каскад по handler'ам/evidence-cycle/`fills.md`.
  В коде на момент записи команда и executor **ещё живы** (`ServiceCommandType.java`,
  `RefreshFillsExecutor.java`) — формулировка целевая, не свершившаяся
  (H15, `GAPS_CLOSE_6`). P&L-факты добывают вторая нога `REFRESH_POSITION_COMMAND`
  (положение закрытия) и новая `REFRESH_BILLS_COMMAND`
  (`docs/decisions/pnl-finalization-mechanics.md` реш.1).

## Следствия

- Реконсилированы под выбранный путь: `docs/models/domain/aggregate/Deal.md`
  §Итоговый PnL, `docs/integrations/okx/contracts/account-bills.md`
  §Использование, `docs/integrations/okx/contracts/position.md` §История,
  `docs/models/domain/other/DealActionState.md` (носитель исполнений;
  прежний `DealFinalizationState.md` упразднён —
  `docs/decisions/command-action-boundary.md`),
  `docs/models/domain/core/Position.md`,
  `docs/models/integrations/okx/OkxPositionResponse.md`,
  `docs/models/integrations/okx/OkxAccountBillResponse.md`,
  `docs/models/integrations/okx/OkxFillResponse.md`,
  `docs/components/MarkDealClosedExecutor.md`,
  `docs/components/FinalizeDealExitExecutor.md`,
  `docs/models/mapping/TradeFill.md`. (Тогда же был реконсилирован док
  `RefreshFillsExecutor`; **впоследствии снят вместе с командой** —
  `REFRESH_FILLS` упразднён на `GAPS_CLOSE_2` шага 7, N12, его функцию несёт
  `REFRESH_ORDER_COMMAND`: `docs/decisions/pnl-finalization-mechanics.md` реш.1.)
- Закрыты **OKX-Q1**, **OKX-Q3**; остаток **DEAL-Q2** (число на
  `EMERGENCY_CLOSED`) закрыт вместе с G5.
- **G4** (fills не агрегируют exit-fills algo-ордеров) — **resolved-by-path**:
  число не из fills → exit-fill-матчинг algo для realized-P&L неактуален.
- **Структурная спецификация** носителей (`OkxPositionsHistoryResponse`,
  positions-history-снапшот, `DealCashFlow`) + fetch-механика — **стадии 1-2,
  `DOCS_CHECK_2`** (не разворачивается здесь по scope `GAPS_CLOSE_1`).

## Связи

- `docs/models/domain/aggregate/Deal.md` §Итоговый PnL.
- `docs/lifecycles/Deal.md` §«Терминальный контракт финализации» (G5).
- `docs/integrations/okx/contracts/position.md` §История (positions-history,
  `realizedPnl`/`closeAvgPx`).
- `docs/integrations/okx/contracts/account-bills.md` (bills — разбивка +
  сверка).
- `docs/integrations/okx/contracts/funding-rate.md` (funding-rate-history —
  не источник числа).
- `docs/components/FinalizeDealExitExecutor.md`,
  `docs/components/MarkDealClosedExecutor.md` (владелец расчёта / терминальная
  запись).
- `.claude/decisions/rule-source-of-truth.md` (правило «первоисточник у
  владельца модели»: число `resultProfit` — у `Deal`, источник фактов — здесь).
