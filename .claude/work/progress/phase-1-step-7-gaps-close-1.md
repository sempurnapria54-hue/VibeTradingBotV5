# GAPS_CLOSE_1 — шаг 7 фазы 1 «Сделки и P&L»

## На какой вопрос отвечает этот файл

Как закрыты 6 пробелов `DOCS_CHECK_1` шага 7 и что реконсилировано под
выбранный источник числа `resultProfit`.

## Контекст

- **Под-шаг:** `GAPS_CLOSE_1` (процесс `roadmap-step-execution.md`), закрывает
  находки отчёта `phase-1-step-7-docs-check-1.md` (стадия 0, гейт не чист:
  источник числа не выбран → doc↔doc противоречие).
- **Вход:** согласованные с пользователем решения (источник числа выбран,
  комиссии-в-сайзинге ратифицированы). Стадия 0 расчищается; после закрытия —
  перезапуск `DOCS_CHECK_2` на стадиях 1-2.
- **Якорный decision:** `docs/decisions/result-profit-source.md` (новый).

## Закрытие по пробелам

### G1 — источник истины `resultProfit` (ЦЕНТРАЛЬНЫЙ, стадия 0) — ЗАКРЫТ

Решение (`docs/decisions/result-profit-source.md`):

- **Число** `resultProfit` = **net realized P&L**, берётся готовым из
  positions-history (`realizedPnl = pnl + fee + fundingFee + liqPenalty`,
  посчитан биржей).
- **Разбивка** (комиссия / funding / rebate / ликвидационный штраф) — из bills
  (`DealCashFlow`).
- **Сверка:** сумма bills-flows ↔ net из positions-history (контроль
  целостности).
- **fills-only отвергнут:** `OkxFillResponse` несёт только `fee`/`feeCcy`, не
  `fundingFee`/`liqPenalty` → торгово неполон (тождественен отклонённому
  «fills-only проще, но менее точно» из OKX-Q3).

**Примирены три расходящихся дока** (найденный дефект):

- `Deal.md` §Итоговый PnL — «через `REFRESH_FILLS`/`TradeFill`» → переписан под
  positions-history (число) + bills (разбивка).
- `account-bills.md` §Использование — `resultProfit = sum(bills)` → bills =
  разбивка + сверка, не первоисточник числа.
- `position.md` §История — с «не используется; форвард В-3» → **источник числа**
  (`realizedPnl`, `closeAvgPx`).

**OKX-Q3 закрыт:** funding — через positions-history (в net) + bills (subType
173/174, разбивка); `funding-rate-history` для числа не ведём.

### OKX-Q1 — persisted `TradeFill` — ЗАКРЫТ (не вводится)

- Persisted per-fill `TradeFill` **не вводим**; пофилловый аудит — вне фазы 1.
  `TradeFill.md` (стаб) приведён в соответствие; `OkxFillResponse.md`,
  `RefreshFillsExecutor.md` реконсилированы.
- **Инспекция native positions-history (на CC):** контракт несёт `closeAvgPx`
  (средняя цена выхода) и `openAvgPx` (`position.md:71`) → **fills для avg-цены
  выхода не нужны**. Order-level fill-метрики (`accFillSz`/`avgPx`) доступны
  прямо из `OkxOrderResponse` (`REFRESH_ORDER`).
- ⇒ **`REFRESH_FILLS` — кандидат на снятие.** Здесь снят только ложный claim
  «resultProfit через fills»; финальная диспозиция (снятие из
  `ServiceCommandType`, executor'а, `ExitPendingHandler`,
  `refresh-evidence-cycle-ownership`, `fills.md`, as-built код шага 6) — это
  stage-1 mechanics, **на `DOCS_CHECK_2`** (ripple в командный набор, не стадия 0).

### G5 — число на `EMERGENCY_CLOSED` — ЗАКРЫТ (остаток DEAL-Q2)

`resultProfit` на аварийном/ликвидационном терминале = **фактический realized
net** (вкл. `liqPenalty`), **не** ноль/null. Источник — positions-history
`realizedPnl` + `triggerPx` (type ликвидации/ADL). Специфицировано в
`lifecycles/Deal.md` §«Терминальный контракт финализации» + статус-описание
`EMERGENCY_CLOSED` + `MarkDealClosedExecutor.md`. Причина (торговый крен TR-3):
аварийный терминал несёт критичный левый хвост R-распределения — усечь нулём
завысило бы ожидаемость. **Остаток DEAL-Q2 закрыт.**

### G3 — компонент-владелец расчёта — НАЗНАЧЕН

- **`FinalizeDealExitExecutor`** при консолидации выхода **вычисляет** net-число
  (из positions-history-снапшота) + собирает разбивку (bills → `DealCashFlow`) +
  сверяет, стейджит `resultProfit`/`resultProfitCurrency` в результат
  финализации.
- **`MarkDealClosedExecutor`** пишет готовое число на терминальном ребре `CLOSED`
  — **placeholder-ZERO снят**.
- Внутренняя декомпозиция расчёта (выделять ли отдельный калькулятор) — деталь
  CODE; fetch-механика P&L-фактов — стадия 1, `DOCS_CHECK_2`.

### G6 — комиссии в риск-сайзинге — ВКЛЮЧЕНЫ

- Прогнозная комиссия (вход+выход, ставка из `trade-fee` taker) вычитается из
  риск-бюджета до входа: `RiskValidator`/`SizeCalculator` приведены к формуле
  `+ commissions`; `per-trade-risk-policy.md` §«Учёт комиссий» — «отложено к
  шагу 7» → **включено**. `trade-fee.md` статус — **В-7 активирован**.
- Обоснование включения без бэктеста: комиссия — детерминированная ставка биржи
  (не «выдуманное число»), в отличие от запаса на проскок (остаётся отложенным).
  Scope-нюанс (правка на входе, не терминале) снят пользователем — в скоупе шага 7.

### G4 — fills не агрегируют exit-fills algo — RESOLVED-BY-PATH

Число не из fills → агрегация exit-fills algo для realized-PnL неактуальна.
Зафиксировано в `result-profit-source.md` §Следствия.

### G2 — целевые сущности пути — ЗАФИКСИРОВАНЫ (структура — на `DOCS_CHECK_2`)

Носители: positions-history-снапшот (число) + `DealCashFlow` (разбивка); native
— `OkxPositionsHistoryResponse` (нужно завести; сейчас только
`OkxPositionResponse` для `/positions`, отбрасывающий realized-поля).
**Структурную спецификацию не разворачивали** (scope `GAPS_CLOSE_1`) — стадии
1-2, `DOCS_CHECK_2`.

## Реконсилированные доки

- **Домен/lifecycle:** `models/domain/aggregate/Deal.md` §Итоговый PnL;
  `lifecycles/Deal.md` §Терминальный контракт + `EMERGENCY_CLOSED`;
  `models/domain/core/Position.md`; `models/domain/other/DealFinalizationState.md`;
  `models/mapping/TradeFill.md`.
- **Компоненты:** `FinalizeDealExitExecutor.md`, `MarkDealClosedExecutor.md`,
  `RefreshFillsExecutor.md`, `ExitPendingHandler.md`, `RiskValidator.md`,
  `SizeCalculator.md`.
- **Интеграции OKX:** `contracts/account-bills.md`, `contracts/position.md`
  §История, `contracts/funding-rate.md`, `contracts/trade-fee.md`;
  `models/integrations/okx/OkxPositionResponse.md`, `OkxFillResponse.md`,
  `OkxAccountBillResponse.md`; `coverage-manifest.md` (В-3/В-6/В-7).
- **Решения:** `decisions/result-profit-source.md` (новый),
  `decisions/per-trade-risk-policy.md` §Учёт комиссий.
- **Пайплайн:** `open-questions.md` (OKX-Q1/OKX-Q3 удалены, остаток DEAL-Q2
  закрыт), `backlog.md` §Шаг 7 + §6 + связанные вопросы, `roadmap/phase-1.md`.

## Форвард на `DOCS_CHECK_2` (стадии 1-2)

- Структура `OkxPositionsHistoryResponse` + positions-history-снапшот + маппинг.
- Структура `DealCashFlow` + маппинг из bills + сужение native-полей до used.
- Fetch-механика P&L-фактов (refresh-команда / integration read).
- Диспозиция `REFRESH_FILLS` (снятие: enum/executor/handler/evidence-cycle/
  fills.md/код) — stage-1.
- Кросс-ccy гигиена (TR-5, name-level) — чтобы не терялось молча.

## Исход

**6 пробелов закрыты; стадия 0 расчищена.** Все концепт-развилки решены,
источник числа зафиксирован decision'ом. **→ перезапуск `DOCS_CHECK_2`** на
стадиях 1-2 (процессы / модели / mapping / native под выбранный путь). Дельта
staged для коммита в IDEA.
