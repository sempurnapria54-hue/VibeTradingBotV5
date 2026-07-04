# Механика финализации P&L (шаг 7)

## На какой вопрос отвечает этот файл

Как механически добываются P&L-факты, кто считает и пишет число
`resultProfit` (штатно и аварийно), где durable-живёт посчитанное число,
откуда берётся ставка комиссии для сайзинга, как реагирует сверка bills↔net —
и почему именно так.

## Контекст

`GAPS_CLOSE_1` выбрал **источник** числа (net из positions-history + разбивка
из bills, `docs/decisions/result-profit-source.md`), но сознательно отложил
стадия-1/2 механику. `DOCS_CHECK_2` (`phase-1-step-7-docs-check-2.md`) вскрыл 11
гейтящих пробелов механики: добыча фактов не назначена (N6); носитель
staged-числа между двумя финализационными командами (N7); аварийный терминал без
владельца + неисполнимый контракт (N8); seam ставки комиссии в сайзинг (N9);
реакция сверки (N10); носители пути только name-level (N1-N5); непроверенный
инвариант агрегации (N11). Это решение закрывает **механику**; структуры-носители
материализуются отдельными доками (native / mapping / model), ссылаясь сюда.

## Решения

### 1. Добыча P&L-фактов — новые refresh-команды, замена `REFRESH_FILLS` (N6, N12)

- Вводятся `REFRESH_POSITIONS_HISTORY` (наполняет positions-history-снапшот —
  число) и `REFRESH_BILLS` (наполняет `DealCashFlow` — разбивка), **по одной на
  новую сущность** (паритет CMD-Q3 «refresh — по одной команде на сущность»).
- `REFRESH_FILLS` **снимается** (N12): его единственная функция (пересчёт
  order-fill-метрик `accumulatedFillSize`/`averagePrice`/`fee` ordinary `Order`)
  покрыта `REFRESH_ORDER` (`OkxOrderResponse.accFillSz`/`avgPx`). Чистый swap
  1-out-2-in в refresh-вокабуляре.
- Эмитятся handler'ами: `ExitPendingHandler` (штатный выход) и `ErrorHandler`
  (аварийная тропа) **до** финализации/терминала; идемпотентны, retryable через
  командную машинерию. `REFRESH_BILLS` внутри команды проходит пагинацию bills
  (7d→3m archive) — паритет evidence-cycle (`refresh-evidence-cycle-ownership.md`).
- `FinalizeDealExitExecutor` **остаётся off-exchange** — читает готовые
  снапшот + `DealCashFlow`, добытые командами. Латентное противоречие снято
  (executor «на биржу не ходит» ⟷ факты некому добывать → теперь их добывают
  refresh-команды).
- **Альтернативы отвергнуты:** (2) integration read вне command-layer
  (CMD-Q4-образец) — размывает «финализация не ходит на биржу», теряет командный
  retry/идемпотентность на гранулярности факта; (3) fetch внутри
  `FinalizeDealExitExecutor` — концентрирует fetch+compute, ломает паттерн
  «refresh populates → finalize consolidates».

### 2. Носитель staged-числа = поле `Deal`, пишет `FINALIZE_EXIT` (N7)

- **Разрыв GAPS_CLOSE_1:** число считалось в `FINALIZE_EXIT`, писалось
  `MARK_CLOSED` — durable-слот между двумя командами (разные проходы FSM, разные
  строки `DealFinalizationState`) не назначен; ломалось об идемпотентность/рестарт.
- **Решение:** `FINALIZE_DEAL_EXIT` **пишет `resultProfit`/`resultProfitCurrency`
  прямо на `Deal`** (persisted) в **одной транзакции** с
  `DealFinalizationState(FINALIZE_EXIT) = COMPLETED` (+ персистит `DealCashFlow`).
  `MARK_DEAL_CLOSED` **читает `Deal.resultProfit`, ассертит непустоту** (инвариант
  чистого `CLOSED`), ставит `status = CLOSED`.
- **Durable-носитель = сами поля `Deal.resultProfit`** (nullable; заполнены на
  `FINALIZE_EXIT`, ассертятся на терминале). Рестарт-safe: после `COMPLETED` число
  уже на `Deal`; повторный `FINALIZE_EXIT` — no-op, `MARK_CLOSED` читает готовое.
  Транзакционная связка: `COMPLETED` ⟺ `resultProfit` persisted.
- **Ревизует framing GAPS_CLOSE_1** «`MARK_CLOSED` пишет число»: число пишет
  `FINALIZE_EXIT`, `MARK_CLOSED` терминализует + ассертит. Побочно: `resultProfit`
  может быть непустым на `EXIT_PENDING` до терминала — доброкачественно (транзитное
  финализационное состояние; §Персистентность `Deal` — nullable).

### 3. Аварийный терминал: владелец + провенанс-контракт (N8)

- **Владелец (part 1):** step-6 не назначил **запись** `EMERGENCY_CLOSED` —
  команды-терминала нет (`MarkDealErrorExecutor` пишет только `ERROR`;
  `MARK_DEAL_EMERGENCY_CLOSED` в enum отсутствовал). Вводится
  **`MARK_DEAL_EMERGENCY_CLOSED`** (команда + `MarkDealEmergencyClosedExecutor` +
  `DealFinalizationType.MARK_EMERGENCY_CLOSED`), терминальное ребро
  `ERROR → EMERGENCY_CLOSED`, **симметрично `MARK_DEAL_CLOSED`**. Эмитит
  `ErrorHandler` после подтверждённого снятия live risk. Best-effort число —
  из positions-history-снапшота (добыт `REFRESH_POSITIONS_HISTORY` перед терминалом).
- **Провенанс-контракт (part 2) — best-effort, два провенанса разведены:**
  - **(a) реальная ликвидация/ADL** (позицию закрыла биржа): `realizedPnl` +
    `liqPenalty` доступны (`type` 3-6) → пишем **фактический realized net**.
  - **(b) отказ расчёта после исчерпания retry** (чистая тропа не смогла
    посчитать → ушла в `ERROR`): `ErrorHandler` перед терминалом ещё раз пробует
    добыть (`REFRESH_POSITIONS_HISTORY`); net доступен → пишем его; **genuinely
    недоступен** → `resultProfit = null` с семантикой **«неисчислимо»** (НЕ ноль),
    сделка терминализуется **всё равно**, факт помечается (лог + `AnomalyReport`).
  - **Маркер (без нового поля):** на `EMERGENCY_CLOSED` `resultProfit != null` =
    фактический net; `resultProfit == null` = «неисчислимо» — **отличимо от нуля**
    (ноль = посчитанный нулевой P&L). Nullability несёт маркер.
  - **Торговое обоснование (F-T1):** число **не зануляется** — недоступность
    помечается, не подменяется нулём; null-случай **исключается из R-выборки как
    unknown** (не считается нулём) → левый хвост не усекается молча [Vince гл.1
    с.15; Tharp гл.6 с.158-159].
- Контракт становится **исполнимым**: «на `EMERGENCY_CLOSED` — фактический realized
  net если доступен; иначе `null` с маркером «неисчислимо»; **никогда не ноль**».

### 4. Ставка комиссии для сайзинга — дом на `InstrumentExternalRules` (N9)

- Ставка `trade-fee` (taker/maker) — **инструмент-level внешние данные**; дом —
  `InstrumentExternalRules` (JSONB-навес, рядом с tick/lot/`maxLeverage`): поля
  `externalTakerFeeRate` / `externalMakerFeeRate` + аксессоры
  `takerFeeRate()`/`makerFeeRate()`.
- `SizeCalculator`/`RiskValidator` читают taker-ставку из **уже присутствующего**
  `CalculationContext.instrumentExternalRules` — **нового поля контекста не нужно**,
  **exchange-вызова из калькуляторов не нужно** (seam закрыт существующим паттерном,
  как `externalMaxLeverage`).
- Refresh — тем же `InstrumentExternalRulesSyncJob` (дочитывает `trade-fee` для
  инструмента). Wiring sync — CODE-деталь.
- Прогноз по **taker** (worst-case: вход-маркет = taker, выход-по-стопу = taker →
  консервативно); знак «минус = комиссия» (`trade-fee.md`).

### 5. Реакция сверки bills ↔ net (N10)

- **Число всегда авторитетно = positions-history net** (bills-sum его **не
  подменяет**). Расхождение — **сигнал целостности, не ошибка числа**.
- **Не блокирует финализацию:** сделка идёт в `CLOSED` с net-числом. Расхождение
  **сверх epsilon** → **`AnomalyReport`** (audit-аномалия для разбора; `scope =
  INSTRUMENT`, `severity` умеренная) — **не** холд, **не** runtime-error уровней 1-2,
  **не** блок терминала.
- **Epsilon:** допуск на округление между эндпоинтами OKX. Предлагается
  **max(абсолютный 0.01 settle-ccy, относительный 0.5% от |net|)** — **величина на
  подтверждение пользователя** (тонкий хвост; паттерн «провизорное значение»).
- **Cross-ccy (OKB fee, F-T4):** допущение — **комиссии в settle-ccy (USDT)**.
  `DealCashFlow` с `ccy ≠ resultProfitCurrency` **не отбрасывается молча** фильтром
  — помечается `AnomalyReport` (guard). Поле `DealCashFlow.ccy` **обязательно**.

### 6. Инвариант агрегации positions-history — рантайм-верификация (N11)

- **Инвариант:** одна сделка ↔ один `posId` ↔ **одна финализированная** запись
  positions-history, чей `realizedPnl` **кумулятивен по ВСЕМ** partial-закрытиям и
  доборам за жизнь позиции; читается **финализированной** (позиция полностью
  закрыта — `REFRESH_POSITION` показал flat/отсутствие).
- **Требует рантайм-верификации** (контур source-api, demo,
  `.claude/tests/source-api/okx/plan.md`): агрегирует ли OKX partial-выходы
  (partial TP `type` 1 → SL `type` 2) в **одну** запись на `posId`, и в какой
  момент запись финализирована (риск чтения слайса/нефинализированной записи →
  систематический недосчёт realized). До верификации инвариант — **предположение**,
  помеченное в контракт-доке и test-плане. **Гейтит корректность числа** →
  верификация до `CODE`.

## Носители (материализуются отдельно, стадия 2)

- **native `OkxPositionsHistoryResponse`** — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.
- **boundary-снапшот `PositionCloseResultExternalSnapshot`** (транзитный, **не**
  persisted; number → `Deal.resultProfit`, отдельной persisted доменной сущности
  нет) + маппинги native→snapshot→`Deal` — `docs/models/mapping/PositionCloseResult.md`.
- **`DealCashFlow`** (доменная модель `other` + персистенция) —
  `docs/models/domain/other/DealCashFlow.md`; маппинг bills→`DealCashFlow` —
  `docs/models/mapping/DealCashFlow.md`.

## Следствия

- **Новые команды/executor'ы:** `REFRESH_POSITIONS_HISTORY`/
  `RefreshPositionsHistoryExecutor`, `REFRESH_BILLS`/`RefreshBillsExecutor`,
  `MARK_DEAL_EMERGENCY_CLOSED`/`MarkDealEmergencyClosedExecutor`; **снят**
  `REFRESH_FILLS`/`RefreshFillsExecutor`.
- **Реконсиляция:** `ServiceCommand` enum, `DealFinalizationCommandFactory`
  (+`MARK_EMERGENCY_CLOSED`), `DealFinalizationState` (тип + N7-нота),
  `FinalizeDealExitExecutor` (N6/N7/N10), `MarkDealClosedExecutor` (N7),
  `MarkDealErrorExecutor`/`ErrorHandler`/`ExitPendingHandler` (N6/N8/N12),
  `Deal.md` + `lifecycles/Deal.md` (N7/N8), `InstrumentExternalRules`/
  `SizeCalculator`/`RiskValidator`/`per-trade-risk-policy` (N9),
  `refresh-evidence-cycle-ownership`/`fills.md`/`risk-validator-scope`/handler'ы
  (N12), `result-profit-source` (ссылки), `coverage-manifest`.
- **N13** (funding как holding-cost на форварде) — отдельная форвард-нота
  (`per-trade-risk-policy.md` §Учёт комиссий + backlog), не здесь.

## Связи

- Источник числа (что за данные) — `docs/decisions/result-profit-source.md`.
- Терминальный контракт — `docs/lifecycles/Deal.md` §«Терминальный контракт
  финализации».
- Риск-политика и комиссии — `docs/decisions/per-trade-risk-policy.md`.
- Финализационная механика шага 6 — `docs/decisions/deal-finalization-state-materialization.md`.
- Внутренняя error-градация — `docs/rules/error-handling-policy.md`,
  `docs/rules/runtime-error-classification.md`.
