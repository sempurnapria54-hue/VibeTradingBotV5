# DOCS_CHECK_1 — шаг 7 фазы 1 «Сделки и P&L»

## На какой вопрос отвечает этот файл

Каков исход первой сквозной проверки концепции (`concept-review` +
`trading-review`) под шаг 7 — есть ли пробелы, мешающие писать код расчёта
`resultProfit` и агрегации в `Deal`.

## Контекст

- **Под-шаг:** `DOCS_CHECK_1` (процесс `roadmap-step-execution.md`). `TOOLING`
  пройден без новых артефактов (фокусы `concept`/`trading` активны).
- **Scope шага 7 (граница 6↔7):** петля/FSM/финализационная механика — шаг 6
  (DONE). Шаг 7 сужен до: (1) расчёт самого **числа** `resultProfit` на терминале
  (вкл. PnL `EMERGENCY_CLOSED`; контракт «когда обязателен» — DEAL-Q2, закрыт на
  шаге 6); (2) агрегация фактов исполнения в `Deal`. Заменяет placeholder-ZERO
  шага 6.
- **Прогон:** три независимых ревьюер-субагента — concept ×2 (линза 1:
  механика агрегации/финализации; линза 2: данные/модели PnL) + trading
  (корректность числа). CC верифицировал ключевые атрибуции грепом/спот-чеком.

## Охват

**Проверено:** `Deal.md` (aggregate, §Итоговый PnL) + `lifecycles/Deal.md`
(§Терминальный контракт); процесс `deal-management.md`; финализационные
компоненты (`DealOrchestratorJob`, `MarkDealClosedExecutor`,
`FinalizeDealExitExecutor`, `FinalizeDealEntryExecutor`, `MarkDealErrorExecutor`,
`DealFinalizationCommandFactory`, `RefreshFillsExecutor`) + `DealFinalizationState`;
риск-компоненты по хвосту комиссий (`per-trade-risk-policy.md`, `RiskValidator`,
`SizeCalculator`); интеграции OKX (`account-bills.md`, `position.md` §История,
`funding-rate.md`, `trade-fee.md`, `fills.md`, native `OkxFillResponse`/
`OkxPositionResponse`/`OkxAccountBillResponse`); `models/mapping/` (в т.ч.
`TradeFill.md` — стаб); `rules/audit-not-runtime-source.md`; `coverage-manifest.md`;
`open-questions.md` (OKX-Q1/Q2/Q3, DEAL-Q2).

**Вне охвата:** глубина моделей/mapping PnL за нерешённым источником (ранний стоп
стадии 0); deep-архив fills 3m+ (OKX-Q2, не гейтит свежую сделку).

## Стадия остановки

**Стадия 0 — гейт не чист.** Гейтящий скоуп-вопрос (источник данных
`resultProfit`) не решён и порождает прямое doc↔doc противоречие. Ниже по стадиям
(процессы стадии 1, модели/mapping стадии 2 вглубь) не идём — против неопределённого
источника специфицировать сущность-носитель бессмысленно.

## Пробелы

### G1 — ЦЕНТРАЛЬНЫЙ БЛОКЕР (стадия 0). Источник данных `resultProfit` не выбран; три дока указывают на три источника; торговый инвариант требует net-число

**Сходятся все три ревьюера** (concept#1 F1, concept#2 G1, trading TR-1). Тип:
несогласованность doc↔doc + неотвеченные вопросы (OKX-Q1/OKX-Q3) + торговый блокер
«модель не выражает обязательное правило».

**Три несводимых кандидата (верифицировано CC):**
1. **fills / `TradeFill`** — `Deal.md:88-90` (owner поля, «первоисточник правила»):
   «`resultProfit` считается через `REFRESH_FILLS` / `TradeFill` facts, **не** через
   `BalanceContainer` diff» (категорично). OKX-Q1 открыт.
2. **bills / `DealCashFlow`** — `account-bills.md:80-86`: `Deal.resultProfit =
   sum(DealCashFlow.amount)`; там же аргумент (стр.33): «bills полнее — покрывают и
   funding, и rebate». OKX-Q3 открыт.
3. **positions-history / `realizedPnl`** — `position.md:67-68`: `realizedPnl = pnl +
   fee + fundingFee + liqPenalty` одним эндпоинтом (форвард В-3).

**Торговый инвариант (trading TR-1, источник):** торгово-осмысленное «число прибыли
сделки» — **net** от всех издержек (комиссии + funding на SWAP + liqPenalty на
аварии); иначе downstream-ожидаемость/R-распределение завышены [Tharp гл.6
с.130-133,138-146; издержки — Kaufman гл.16; funding≈carry, корпусный ∅ по funding
перпов, вывод по аналогии — Carver ST гл.7 с.153-154]. **fills-путь торгово
неполон:** `OkxFillResponse` несёт только `fee`/`feeCcy` — ни `fundingFee`, ни
`liqPenalty` (верифицировано CC). То есть owner-`Deal.md` категорично выбрал путь,
тождественный отвергнутому в OKX-Q3 варианту «fills-only — проще, но менее точно».
Корректная композиция **уже задокументирована** как `positions-history.realizedPnl`.

**Гейтит CODE:** да. `resultProfit` конструируется в шаге 7, сущность-носитель
маппится из внешнего DTO — от выбора зависит, какой native DTO мапится, какая
доменная модель нужна (`TradeFill` / `DealCashFlow` / positions-history-snapshot) и
нужен ли per-fill entry/exit-матчинг. Decision-дока, фиксирующего источник, нет
(верифицировано CC: `docs/decisions/` пусто по `resultProfit`/`realizedPnl`/
`DealCashFlow`).

**Владелец:** `solution-designer` (конструкция) + вход `trading-specialist`
(обязательность funding/liqPenalty) + `integrator` (native positions-history, если
выбран путь 3); **хвост пользователя** — policy точность-vs-простота (сужен торговым
инвариантом: net требуется). **Целевой док:** новый `decision` (источник-истины
`resultProfit`) + реконсиляция `Deal.md` §Итоговый PnL / `account-bills.md` /
`position.md` §История + закрытие OKX-Q1/OKX-Q3.

### G2 — Агрегирующая модель PnL только name-level (гейтирована за G1)

concept#1 F2 / concept#2 D1-D3. Тип: name-level без структуры. Верифицировано CC:
`DealCashFlow` — **модель-дока нет** вовсе; `TradeFill.md` — стаб («persisted entity
не введён», OKX-Q1); native `OkxPositionsHistoryResponse` — нет (только
`OkxPositionResponse` для `/positions`, который **явно отбрасывает**
`realizedPnl/fee/fundingFee/liqPenalty`). Какой бы источник ни победил —
агрегирующая сущность структурно не задана. **Разрешается на выбранном пути G1**,
вглубь не проверяется (ранний стоп).

### G3 — Компонент-владелец шаг-7-расчёта не назначен (механика финализации)

concept#1 F3. Тип: name-level без структуры. `MarkDealClosedExecutor.md` пишет
placeholder и обещает, что «шаг 7 заменит расчётным числом», но **кто вычисляет** —
не сказано; `FinalizeDealExitExecutor.md:24-26` и `account-bills.md:89-95` явно:
расчёт **не входит** в `FINALIZE_DEAL_EXIT`; `RefreshFillsExecutor` «`Deal` напрямую
не обновляет — это делает FSM handler». Итог: компонент, производящий число и
пишущий его на терминальном ребре, концепцией не назначен. Отдельный пробел
механики (не только данных). **Владелец:** `solution-designer` (+ `code-writer`).

### G4 — fills-путь не агрегирует exit-fills algo-ордеров (SL/TP/partial)

trading TR-2 (structural), верифицировано CC (`RefreshFillsExecutor.md:16` —
матчинг fills с `AlgoOrder`/`Position` = forward-debt). Нормальные выходы — algo
(`STOP_LOSS`/`TAKE_PROFIT`/`OCO`/trailing/`PARTIAL_*`, по `SizeCalculator`/lifecycle);
их exit-fills сделке не атрибутируются → даже fills-путь структурно недостроен для
realized-PnL нормального выхода. Усиливает G1 (fills недостаточны). Гейтирована за
G1.

### G5 — Число `resultProfit` на `EMERGENCY_CLOSED` не специфицировано

concept#1 F4 / concept#2 G3 / trading TR-3. Тип: неотвеченный вопрос (хвост DEAL-Q2
→ шаг 7). `lifecycles/Deal.md` §Терминальный контракт + `MarkDealClosedExecutor.md`:
«что с числом на ошибочном терминале — деталь шага 7, не блокируется инвариантом
чистого закрытия». Значение (best-effort / null / ZERO) не задано, хотя в скоупе
шага 7. **Торговый крен (TR-3, БЛОКЕР):** аварийный/ликвидационный терминал несёт
критичный левый хвост R-распределения [Tharp гл.6 с.145-146; Vince гл.1 с.15];
число обязано быть **фактическим** realized P&L (вкл. `liqPenalty`), не ноль/
отсутствие — иначе хвост усечён, ожидаемость завышена. `positions-history` даёт
`realizedPnl` + `triggerPx` для ликвидации/ADL. Разрешается вместе с G1 (тот же
источник определяет доступность `liqPenalty`).

### G6 — Комиссии в risk-amount/сайзинге: policy отложена к шагу 7 + нюанс скоупа

concept#2 G2 / trading TR-4. Тип: неотвеченный вопрос (policy) + скоуп-уточнение.
`per-trade-risk-policy.md:83-94` §«Учёт комиссий — отложен к шагу 7»;
`RiskValidator`/`SizeCalculator` — «комиссии в фазе 1 опущены». **Нюанс скоупа
(concept#2):** комиссии-в-сайзинге — правка риск-контроля на **входе**, а не расчёта
`resultProfit` на терминале; формально может быть вне суженного скоупа шага 7 —
нужно подтвердить границу. **Торговая оценка (TR-4, не-блокер):** для трендследящего
класса (широкий стоп ~3 ATR, низкий turnover) материальность комиссии низка, но знак
смещения всегда рисковый и **складывается однонаправленно** с опущенным запасом на
проскок [Tharp гл.9 с.242-243]. Опция «оставить вне» обязана нести явное
обоснование, привязанное к классу. **Владелец:** хвост пользователя (policy) +
`trading-specialist` + `solution-designer` (источник ставки — `trade-fee`).

## Не-находки / не гейтит (зафиксировано)

- **`trade-fee` — согласовано** (concept#2 D4): `trade-fee.md:22-24` — ставки для
  прогноза/сверки, фактические комиссии — из fills/bills. Для самого `resultProfit`
  fee берётся из фактического источника, не из `trade-fee`. Не находка.
- **OKX-Q2 (`TradeFillsArchive`, >3м)** — не гейтит (свежая сделка закрывается в
  окне 3d/3m). Живёт в своём файле.
- **TR-5 — кросс-ccy компоненты** (`feeCcy`, валюта funding) vs одновалютный фильтр
  `resultProfitCurrency` в `account-bills.md` §Использование: гигиена/форвард, для
  `ETH-USDT-SWAP` (всё USDT) даунсайд узкий; name-level к step-7-реализации, чтобы
  кросс-ccy не терялось молча.

## Блокирующие открытые вопросы

- **OKX-Q1** (persisted `TradeFill` + как ложится на `resultProfit`) — открыт,
  владелец шаг 7, гейтит (G1/G2).
- **OKX-Q3** (bills/`DealCashFlow` как источник, funding-полнота) — открыт, владелец
  шаг 7, гейтит (G1/G2). Смежные форварды В-3 (positions-history), В-6 (funding, два
  пути), В-7 (trade-fee) — все «→ шаг 7».
- **DEAL-Q2 остаток** (число на `EMERGENCY_CLOSED`) — явно отложен на шаг 7 (G5).

## Сводка

**6 пробелов.** Все сходятся к одному центру — **G1** (источник данных
`resultProfit`), гейт стадии 0. G2/G3/G4 разрешаются на выбранном пути G1; G5
(аварийное число) и G6 (комиссии в сайзинге) — отдельные step-7-хвосты, частично
завязаны на G1. Торговых блокеров три (TR-1/TR-2/TR-3 = G1/G4/G5), все в скоупе шага
7, не cross-cutting; торговый инвариант «число = net realized P&L на любом терминале»
задаёт направление (fills-only исключён).

**Крены/владельцы:**
- G1 — концепт+торговый крен есть (net → positions-history `realizedPnl` или bills
  `DealCashFlow`; fills-only исключён); **хвост пользователя** — выбор пути (точность
  vs простота реализации) + судьба OKX-Q1 (нужен ли persisted `TradeFill` для
  аудита независимо от источника числа).
- G6 — **настоящий policy-выбор пользователя** (включать ли комиссию в сайзинг +
  входит ли это в скоуп шага 7).
- G5 — торговый крен дан (фактический net, вкл. liqPenalty); фиксируется на G1.

**Исход: `DOCS_CHECK_1` НЕ чист → `GAPS_CLOSE_1`.** Приоритет: G1 (выбор источника +
реконсиляция трёх доков + OKX-Q1/Q3) → на нём G2/G3/G4; затем G5 (аварийное число) и
G6 (комиссии-policy). После закрытия — перезапуск `DOCS_CHECK_2` на стадиях 1-2.
