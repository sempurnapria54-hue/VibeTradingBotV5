# Фаза 1. Полноценная торговля одной стратегией

## На какой вопрос отвечает этот файл

Из каких шагов состоит Фаза 1 продуктового роадмапа и в каком
статусе каждый шаг.

## Назначение

Детальный роадмап Фазы 1. Главный роадмап —
`.claude/work/roadmap/roadmap.md`. Процесс исполнения шага —
`.claude/processes/roadmap-step-execution.md` (там же канонический
набор статусов шага). Скилл ведения прогресса —
`.claude/skills/update-roadmap-progress.md`.

## Цель фазы

Бот торгует одной стратегией end-to-end: получает рыночные
данные, рассчитывает индикаторы, генерирует сигналы, отправляет
команды на биржу, ведёт позиции, фиксирует P&L. Полный
production-flow одной стратегии.

## Шаги

| # | Шаг | Статус |
|---|---|---|
| 1 | Поток рыночных данных (коннект к OKX, инструменты, цены/свечи, свежесть) | DONE |
| 2 | Стратегия (абстракция: объявляет нужные индикаторы и условие сигнала; одна реализация) | DONE |
| 3 | Производные рыночные данные: индикаторы + структура рынка (`MarketStructure`) + фаза рынка (`MarketPhase`) — jobs, модели, сервисы (расчёт/чтение/сохранение значений, запрошенных стратегией) | DONE |
| 4 | Команды и их жизненный цикл (ServiceCommand: submit/replace/cancel/close/REFRESH; исполнители; lifecycle; факт и реконсиляция через REFRESH, не ACK; ведение Position/Order) | DONE |
| 5 | Риск-преконтроль (валидация перед отправкой: размер, ограничения инструмента, reduce-only, лимиты) | DONE |
| 6 | FSM + живая оркестрация (состояния и переходы сущностей + handler'ы; живая оркестрационная петля `DealOrchestratorJob` (driving), REPLACE-оркестрация, per-deal concurrency-guard, механика финализации — финализационные executor'ы / терминальные рёбра / retry-state финализации) | DONE |
| 7 | Сделки и P&L (`DealOrchestratorJob` — агрегирование в `Deal`, расчёт `resultProfit` / P&L) | DOCS_CHECK_3 |
| 8 | AnomalyJob (полноценный, операционная детекция аномалий состояния/исполнения) | HOLD |
| 9 | Безопасность (auth-инфраструктура: Spring Security, `@PreAuthorize`, `SecurityFilterChain`; остаточный хардненинг секретов Vault — политики/approle/ротация/unseal, сама привязка уже введена на инфра-шаге; реактивирует фокус `security-review`) | HOLD |
| 10 | Тесты | HOLD |
| 11 | Фронт | HOLD |

## Примечания

- **Фронт (шаг 11)** — простой, для прогонов. Полноценный фронт
  появится после архитектурного рубежа.
- **Безопасность (шаг 9)** — строит auth-инфраструктуру (Spring
  Security, `@PreAuthorize`, `SecurityFilterChain`). **Vault-привязка
  секретов введена раньше — на инфра-шаге (2026-06-12, снапшот v47):
  datasource и OKX-креды читаются из Vault per-profile.** Шаг 9
  рескоупится на остаточный хардненинг секретов (политики/approle,
  ротация, unseal) поверх уже подключённого Vault — не на его введение.
  Содержание прорабатывается docs-first на самом шаге; на нём
  реактивируется фокус `security-review`. Форвард-материал —
  `.claude/work/backlog.md` §S1 (рескоуплен) / §S2.
- **Тесты (шаг 10)** и **Фронт (шаг 11)** — отдельные шаги
  фазы, исполняются по тому же процессу docs-first.
- Под-шаги внутри каждого шага заранее не дробятся; они
  появляются в процессе исполнения (см. процесс).
- **Граница шага 6 ↔ 7 уточнена (2026-06-21).** Живая оркестрация
  отнесена к **шагу 6**, не 7. Шаг 6 — «FSM + живая оркестрация»: помимо
  статусной механики (состояния и переходы сущностей) и конструкции
  handler'ов в него входят живая оркестрационная петля
  (`DealOrchestratorJob` driving), REPLACE-оркестрация, per-deal
  concurrency-guard (D-M1) и механика финализации (финализационные
  executor'ы, терминальные рёбра, retry-state финализации). Шаг 7 —
  «Сделки и P&L» — сужен до расчёта `resultProfit` и агрегации `Deal`
  (DEAL-Q2 закрыт как терминальный контракт на шаге 6; остаётся лишь
  *число* прибыли на ошибочном терминале — деталь шага 7); формулировка «он
  же оркестрирует торговый цикл сигнал→команда→позиция» из строки шага 7
  снята (петля уехала в шаг 6). Жёсткие гейты D-B3/D-M1 привязаны к шагу 6
  (петля включается там; см. примечание ниже).
- **Жёсткие гейты `DONE` шага 6 (2026-06-12, из разбора ревью шага
  4; привязка к шагу 6 уточнена 2026-06-21 вместе с границей 6 ↔ 7 —
  петля включается на шаге 6).** **D-B3** (SUBMIT recovery-by-clientId —
  дубль ордера при ресабмите после краша между place и сохранением
  `externalId`) и **D-M1** (concurrency-guard вокруг исполнения команды —
  двойной SUBMIT при перекрытии триггеров) — деньги-дубли, латентны до
  включения оркестрационной петли. **Блокирующее условие:**
  оркестрационную петлю **нельзя включать** и шаг 6 **не уходит в `DONE`**,
  пока оба не закрыты. Это жёсткий гейт шага (проверяется при переходе
  шага 6 в `DONE`), не просто форвард-долг. Детали —
  `.claude/work/backlog.md` §Хвост шага 4.
- **Error-политика — ✅ зафиксирована на `GAPS_CLOSE_1` шага 6
  (2026-06-22).** Единая политика исключений спроектирована docs-first:
  `docs/rules/error-handling-policy.md` (внешняя поверхность — единый
  `@ControllerAdvice` + error-DTO; async-фасад 202/409; внутренняя градация
  4 уровней — лог / ретрай / холд инструмента / холд биржи) + новое правило
  `docs/rules/instrument-hold.md` (уровень 3). **TBD error-конвенции в
  `codestyle.md` §«Обработка ошибок» снят.** Неблокирующие майоры шагов 2 и
  4 (500 вместо 422/409, невыровненные коды реджектов) ретро-закрываются по
  этой политике; конкретный набор HTTP-кодов и 409-vs-идемпотентность —
  провизорный хвост пользователя.
- **Шаг 3 — `DONE` (2026-06-10).** Построены производные рыночные данные:
  индикаторы, структура рынка (`MarketStructureResolver`), stateless-фаза
  (`MarketPhaseResolver`), owner-ключевание результатов (ревизия D), миграции
  `V4`/`V5`. Хроника под-шагов (DOCS_CHECK_6/7, GAPS_CLOSE_4/5, CODE, SYNC,
  ревизия D) — `.claude/work/history/2026-06-10-phase-1-step-3-derived-market-data/phase-1-step-3-chronicle.md`;
  артефакты шага — в той же папке.
- **Шаг 4 — `DONE` (2026-06-11, повторно через гейт; рантайм-хвост закрыт
  2026-06-12).** Построен command-layer: `ServiceCommand`/`DealActionState`,
  REPLACE-only (AMEND снят), 13 исполнителей + диспетчер + retry,
  evidence-cycle REFRESH, OKX-интеграция, миграции `V6`/`V7`; тулинг
  `integrator` + офдок-докачка OKX; адверсариальное ревью закрыло
  деньги-блокеры; ретро-ревью шагов 1-3 — без блокеров. Хроника (вкл.
  ретро-ревью и рантайм-хвост) —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-chronicle.md`;
  артефакты шага — в той же папке.
- **Шаг 5 — `DONE` (2026-06-20).** Построен риск-преконтроль: риск-политика
  на сделку (`docs/decisions/per-trade-risk-policy.md`),
  `InstrumentExternalRules` (JSONB-навес, миграция `V8`, sync-job), расчётный
  слой (`PriceCalculator`/`SizeCalculator` с risk-bounded сайзингом),
  `RiskValidator`/`RiskBlockResolver`. Хроника —
  `.claude/work/history/2026-06-20-phase-1-step-5-risk-precontrol/phase-1-step-5-chronicle.md`;
  артефакты шага — в той же папке.
- **Шаг 6 — `DONE` (2026-07-03).** Построены FSM + живая оркестрация:
  `DealStateMachine` + 7 handler'ов, петля `DealOrchestratorJob` +
  `EntryScannerJob`, финализационная под-спина `DealFinalizationState`
  (миграция `V9`), жёсткие гейты D-B3/D-M1 закрыты, error-политика
  (`@RestControllerAdvice`), kill-switch → `SafetyHoldCoordinator`. Хроника —
  `.claude/work/history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-chronicle.md`;
  артефакты шага — в той же папке.
- **Шаг 7 → `DOCS_CHECK_1` (2026-07-03):** стартован шаг «Сделки и P&L»
  (`TOOLING` без новых артефактов — фокусы `concept`/`trading` активны). Scope
  (граница 6↔7): расчёт числа `resultProfit` на терминале (вкл. PnL
  `EMERGENCY_CLOSED`) + агрегация фактов в `Deal`; заменяет placeholder-ZERO
  шага 6. Форвард-долг на шаг 7: комиссии в риск-расчёте (§6a шага 5),
  `positions-history` realizedPnl-разложение (В-3), funding SWAP (В-6/OKX-Q3 —
  выбор пути), `trade-fee` (В-7), граница audit/история (шаг 8) vs PnL-число.
  Прогон — три независимых ревьюер-субагента (concept ×2 + trading); CC
  верифицировал ключевые атрибуции грепом. **Не чисто — 6 пробелов, все сходятся
  к центральному блокеру G1** (стадия 0): источник данных `resultProfit` не выбран,
  три дока противоречат (fills/`TradeFill` `Deal.md` ↔ bills/`DealCashFlow`
  `account-bills.md` ↔ positions-history/`realizedPnl` `position.md`); OKX-Q1/Q3
  открыты. Торговый инвариант (TR-1/TR-2/TR-3, блокеры) задаёт направление: число =
  **net** realized P&L (комиссии+funding+liqPenalty) на любом терминале →
  fills-only исключён (fills не несут funding/liqPenalty). G2 (агрегирующая модель
  name-level), G3 (компонент-расчёта не назначен), G4 (fills не агрегирует
  algo-exit) — на выбранном пути G1; G5 (число `EMERGENCY_CLOSED`), G6 (комиссии в
  сайзинге — policy + нюанс скоупа) — отдельные хвосты. Обход остановлен на стадии
  0. **Исход → `GAPS_CLOSE_1`** (после решений пользователя по G1-пути и G6-policy).
  Отчёт — `.claude/work/progress/phase-1-step-7-docs-check-1.md`.
- **Шаг 7 → `GAPS_CLOSE_1` (2026-07-03):** пробелы `DOCS_CHECK_1` закрыты
  согласованными с пользователем решениями (стадия 0 расчищена). **G1** — источник
  числа `resultProfit` выбран (новый `docs/decisions/result-profit-source.md`):
  заголовочное число = **net realized P&L готовым из positions-history**
  (`realizedPnl = pnl+fee+fundingFee+liqPenalty`), категорийная разбивка — из bills
  (`DealCashFlow`), сумма bills сверяется с net; **fills-only отвергнут**
  (`OkxFillResponse` без `fundingFee`/`liqPenalty`). Примирены три расходящихся дока
  (`Deal.md` §Итоговый PnL, `account-bills.md`, `position.md` §История). **OKX-Q1
  закрыт** (persisted `TradeFill` не вводится; инспекция native: positions-history
  несёт `closeAvgPx`/`openAvgPx` → fills для avg-цены не нужны; `REFRESH_FILLS` —
  кандидат на снятие, диспозиция stage-1). **OKX-Q3 закрыт** (funding — через
  bills/positions-history, не `funding-rate-history`; В-3/В-6 разрешены). **G5** —
  число на `EMERGENCY_CLOSED` = фактический realized net вкл. `liqPenalty` (остаток
  DEAL-Q2 закрыт). **G3** — расчёт назначен `FinalizeDealExitExecutor` (число +
  разбивка + сверка), запись на терминале — `MarkDealClosedExecutor`
  (placeholder-ZERO снят). **G6** — прогнозная комиссия включена в риск-сайзинг
  (ставка `trade-fee`, В-7 активирован; `per-trade-risk-policy`/`RiskValidator`/
  `SizeCalculator`). **G4** — resolved-by-path (число не из fills). **G2** — целевые
  носители (positions-history-снапшот + `DealCashFlow`, native
  `OkxPositionsHistoryResponse`) зафиксированы; **структурная спека — стадии 1-2, на
  `DOCS_CHECK_2`**. Реконсилировано 20+ доков + open-questions/backlog/manifest;
  дельта staged. Отчёт — `.claude/work/progress/phase-1-step-7-gaps-close-1.md`.
  **Исход → перезапуск `DOCS_CHECK_2`** (стадии 1-2: процессы/модели/mapping/native
  под выбранный путь).
- **Шаг 7 → `DOCS_CHECK_2` (2026-07-03):** descend на стадии 1-2 под выбранный путь
  (positions-history + bills). Три независимых ревьюер-субагента (concept ×2 —
  механика/стадия 1 и модели/стадия 2 — + trading); CC верифицировал несущие
  атрибуции грепом/`ls` (нет refresh-команд под positions-history/bills; нет
  `MARK_DEAL_EMERGENCY_CLOSED`; `MarkDealErrorExecutor` пишет только `ERROR`;
  `ErrorHandler` без `FINALIZE_DEAL_EXIT`; нет `OkxPositionsHistoryResponse`/
  positions-history-снапшота/`DealCashFlow`; нет fee-поля в `CalculationContext`).
  **Не чисто — 13 находок (11 гейтят).** Кластеры: (A) три носителя пути только
  name-level — native `OkxPositionsHistoryResponse` (N1), positions-history-снапшот
  +имя доменной сущности (N2), модель/mapping/персистенция/линковка `DealCashFlow`
  (N3-N5); (B) механика стадии 1 — **добыча фактов positions-history/bills не
  назначена** (N6, центр тяжести; ни одна `REFRESH_*` их не производит), носитель
  staged-числа между `FINALIZE_EXIT` и `MARK_CLOSED` (N7, ломается об идемпотентность),
  владелец+провенанс аварийного числа (N8); (C) поток ставки `trade-fee` в отрезанный
  от биржи сайзинг (N9); (D) реакция на расхождение сверки bills↔net + epsilon +
  cross-ccy (N10); (E) торговые блокеры — **N8** (контракт `EMERGENCY_CLOSED`
  неисполним для compute-failure-провенанса → усечение левого хвоста R, жёсткий гейт)
  и **N11** (непроверенный инвариант агрегации partial-close на `posId` → рантайм-
  верификация); (F) не гейтят — `REFRESH_FILLS`-диспозиция (N12, ripple по 6
  handler'ам) и funding-как-holding-cost без форвард-дома (N13, форвард к экспектанси/
  фаза 2). Владельцы: `solution-designer` (N2/N3/N5/N6/N7/N8/N9/N10/N12) + `integrator`
  (N1/N4/N11-рантайм) + `trading-specialist` (N8/N11/N13); хвост пользователя тонкий
  (N10 epsilon, N13 scope). Ролляп фазы без изменений (`IN_PROGRESS`: 1-6 `DONE`, 7
  в `DOCS_CHECK_2`, 8-11 `HOLD`). Отчёт — `.claude/work/progress/phase-1-step-7-docs-check-2.md`.
  **Исход → `GAPS_CLOSE_2`.**
- **Шаг 7 → `GAPS_CLOSE_2` (2026-07-04):** descend-закрытие стадий 1-2 —
  13 находок закрыты, механика материализована. **Якорь** — новый
  `docs/decisions/pnl-finalization-mechanics.md`. **N6+N12:** добыча P&L-фактов —
  новые refresh-команды **`REFRESH_POSITIONS_HISTORY`** (positions-history-снапшот)
  + **`REFRESH_BILLS`** (`DealCashFlow`), заменяют снятый `REFRESH_FILLS` (его
  order-fill-метрики покрыты `REFRESH_ORDER`); каскад снятия по ~18 докам
  (агент). **N1-N5 (носители):** созданы native `OkxPositionsHistoryResponse`,
  снапшот `PositionCloseResultExternalSnapshot` (`mapping/PositionCloseResult.md`),
  модель+mapping+таблица `DealCashFlow` (`deal_cash_flows`, FK `deal_id`,
  `UNIQUE(external_bill_id)`; линковка по окну+`instId`+`ccy`, bills не несут
  dealId). **N7:** носитель staged-числа = **поле `Deal`** — `FINALIZE_DEAL_EXIT`
  пишет `resultProfit` на `Deal` в одной транзакции с `COMPLETED` (рестарт-safe),
  `MARK_DEAL_CLOSED` ассертит+терминализует (не пишет число). **N8:** аварийный
  терминал получил владельца — новая команда/executor **`MARK_DEAL_EMERGENCY_CLOSED`**
  (`DealFinalizationType.MARK_EMERGENCY_CLOSED`, симметрично `MARK_CLOSED`);
  провенанс-контракт **исполним** — best-effort: (a) ликвидация → фактический net;
  (b) отказ расчёта → `resultProfit = null` c маркером «неисчислимо» (**не ноль**),
  число не зануляется, левый хвост R не усекается. **N9:** ставка `trade-fee` —
  дом на `InstrumentExternalRules` (навес), калькуляторы читают через
  `CalculationContext.instrumentExternalRules` (без нового поля/fetch). **N10:**
  сверка bills↔net → `AnomalyReport`, **не блокирует** финализацию (число =
  positions-history net); epsilon провизорный (хвост пользователя); cross-ccy guard.
  **N11:** инвариант агрегации positions-history выписан + **рантайм-верификация**
  (test-план §AG1.5, ⏳ PENDING, гейтит CODE). **N13:** разделяющий довод
  комиссия-в-R / funding-в-post-cost-expectancy зафиксирован, форвард-дом — фаза 2.
  Enum `ServiceCommandType` 16→18. Реконсилировано ~35 доков (4 параллельных
  агента на непересекающихся наборах + ядро). Ролляп фазы без изменений
  (`IN_PROGRESS`: 1-6 `DONE`, 7 в `GAPS_CLOSE_2`, 8-11 `HOLD`). Отчёт —
  `.claude/work/progress/phase-1-step-7-gaps-close-2.md`. **Исход → `DOCS_CHECK_3`.**
- **Шаг 7 → `DOCS_CHECK_3` (2026-07-04):** подтверждающий прогон после
  материализации стадий 1-2. Три независимых ревьюер-субагента (concept ×2 —
  механика + модели/mapping — + trading); CC верифицировал несущие атрибуции
  грепом/`ls`. **Ядро механики N6/N7/N8/N12 — проведено полно и согласованно**
  (enum=18, N7 tx-связка «FINALIZE пишет число на `Deal` / MARK_CLOSED ассертит»
  везде, N8 терминал `MARK_DEAL_EMERGENCY_CLOSED` с владельцем, PositionCloseResult-
  и DealCashFlow-пути field-level согласованы). **Не чисто — 8 находок (3 гейтят):**
  **H1 (докогейт)** — N9 fee-wiring доспецифицирован наполовину: модель
  `InstrumentExternalRules` получила fee-поля, но **нет** native `OkxTradeFeeResponse`,
  **нет** маппинга ставки, `mapping/InstrumentExternalRules` **отбрасывает `groupId`**
  (а резолв feeGroup SWAP на нём завязан), `InstrumentExternalRulesSyncJob` не описан
  дочитывать `trade-fee` → `takerFeeRate()` останется null; **H2** — гранулярность
  bills: маппинг выбрасывает native `fee`/`pnl`, если OKX эмитит комбинированный
  trade-bill (`balChg=pnl+fee`) → `TRADE_FEE` недосчитан (гейтит **разбивку**, не
  заголовочное число; рантайм-верификация + вернуть `fee` в used); **H3/N11** —
  инвариант агрегации positions-history (рантайм-гейт, уже трекается §AG1.5). Не
  гейтят: гигиена ×5 (H4 — `lifecycles/DealFinalizationState` без `MARK_EMERGENCY_CLOSED`,
  `risk-validator-scope` без него в списке, мёртвые ссылки на удалённый
  `RefreshFillsExecutor`, неполные cross-ref), H5 (`Instant` vs `OffsetDateTime` в
  снапшоте), H6 (форвард: null-drop смещает ожидаемость — «пометки достаточно» торгово
  неверно, → фаза 2), H7 (переякорить epsilon на Σ|amount|), H8 (инвариант «комиссии в
  USDT, не OKB»). **Торговый синтез:** три механизма (N8 null-drop, N11 недосчёт,
  опущенный гэп-проскок) смещают левый хвост оптимистично согласованно — форвард-фокус
  фазы ожидаемости. Владельцы `GAPS_CLOSE_3`: `integrator` (H1/H2 native/mapping/sync) +
  `knowledge-curator` (гигиена H4/H5); форвард H6-H8. Ролляп фазы без изменений
  (`IN_PROGRESS`: 1-6 `DONE`, 7 в `DOCS_CHECK_3`, 8-11 `HOLD`). Отчёт —
  `.claude/work/progress/phase-1-step-7-docs-check-3.md`. **Исход → `GAPS_CLOSE_3`**
  (узкий) + **рантайм-верификация N11/H2 до `CODE`**.
