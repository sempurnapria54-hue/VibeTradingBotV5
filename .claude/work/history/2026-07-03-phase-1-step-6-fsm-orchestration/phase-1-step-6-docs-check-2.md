# DOCS_CHECK_2 — шаг 6 фазы 1 (FSM + живая оркестрация)

## На какой вопрос отвечает этот файл

На каком под-шаге мы в исполнении шага 6 фазы 1 и что показал
подтверждающий прогон сквозной проверки концепции (`concept-review` +
`trading-review`) после `GAPS_CLOSE_1`.

## Контекст прогона

- **Шаг:** 6 фазы 1 — «FSM + живая оркестрация» (живая петля
  `DealOrchestratorJob`, REPLACE-оркестрация, per-deal concurrency-guard,
  механика финализации).
- **Под-шаг:** `DOCS_CHECK_2` — **подтверждающий прогон** после
  `GAPS_CLOSE_1`. Задача: верифицировать, что 15 пробелов `DOCS_CHECK_1`
  (N1-N15) и торговый блокер TR1 **фактически сели закрытыми в доках**
  (верификация атрибуции — каждый целевой док открыт, клауза найдена), и
  выловить **рябь** — новые doc↔doc несогласованности, которые могли внести
  правки `GAPS_CLOSE_1`.
- **Как прогнан:** три независимых ревьюер-субагента (не авторы
  `GAPS_CLOSE_1`-доков), адверсариальная стойка:
  - **concept-фокус, кластер A** — error-политика (N1), финализационная
    под-спина (N2-N4/DEAL-Q1), REPLACE-владелец + «действие vs команда»
    (N5-N6), set-leverage (N10);
  - **concept-фокус, кластер B** — оболочка оркестратора + concurrency +
    `maxAttempts` (N7-N8/D-M1/N11), защита risk-creating входа (N9,
    concept-сторона), Precheck-чистота (N12), гигиена (N13-N15),
    терминальный контракт (DEAL-Q2), sweep `open-questions.md`;
  - **trading-фокус** — закрытие TR1, статус forward TR2-TR4, свежий
    адверсариальный проход по поверхности `GAPS_CLOSE_1`.

## Охват

Те же доки, что `DOCS_CHECK_1` (`docs/processes/deal-management.md`,
lifecycles `Deal`/`DealActionState`/`DealFinalizationState`, компоненты
оркестрации/FSM/handler'ов/command-layer/финализационные executor'ы,
доменные модели, правила, решения, конвенции, `open-questions.md`), плюс
**новые/правленые `GAPS_CLOSE_1`-доки** как первоочередной предмет сверки:
`error-handling-policy.md`, `instrument-hold.md`,
`risk-creating-entry-protection.md`, `DealFinalizationState`
(модель+lifecycle), `deal-finalization-state-materialization.md`,
`action-orchestration-vs-command.md`, 4 финализационных executor-дока.

Вне охвата (как `DOCS_CHECK_1`): расчёт PnL/`resultProfit` (шаг 7),
полный orphan/reconciliation скан (шаг 8), безопасность (шаг 9), нижние
слои (шаги 1/3/5).

## Стадия остановки

**Стадия 2 пройдена** (гейтящих стадии-0 нет — все стадия-0 гейты
`DOCS_CHECK_1` закрыты на `GAPS_CLOSE_1`). Обход доведён до конца по всем
стадиям; находок, останавливающих обход, нет.

## Результат верификации закрытий (N1-N15 + TR1 + DEAL-Q2)

Все кластеры `DOCS_CHECK_1` подтверждены **закрытыми чисто в доках** —
сводка по верификации (file:line — в отчётах субагентов):

### Стадия-0 гейты — закрыты чисто

- **N1 (error-политика)** — `error-handling-policy.md` когерентен (внешняя
  поверхность `@ControllerAdvice`+единый DTO; async-фасад 202/409;
  внутренняя градация 4 уровня); `instrument-hold.md` (уровень 3) на месте;
  реконсиляция с `runtime-error-classification`/`exchange-hold` согласована
  во все стороны; TBD снят в `codestyle` §«Обработка ошибок».
- **N2-N4/DEAL-Q1 (финализационная под-спина)** — `DealFinalizationState`
  (модель+lifecycle+decision, инвариант `UNIQUE(deal_id, finalization_type)`);
  4 executor-дока специфицированы (read/write, терминальное ребро,
  идемпотентность, retry-anchor); путь эмиссии полон
  (`ServiceCommand.dealFinalizationStateId`, фабрика по статусу
  `DealFinalizationState`, `DealContext.finalizationStates`). **Ripple-проверки
  пройдены:** (a) стале «общей retry-policy»/`DealActionState`-дом **нигде не
  остался** (`Deal.md` переписан на `DealFinalizationState`,
  `DealActionState.md` явно переадресует финализацию); (b) граница 6↔7 чистая
  (расчёт `resultProfit` нигде в executor-доках не просочился); (c) 4 типа
  команд единообразны сквозь factory/executor/handler/lifecycle.
- **N5-N6/CMD-Q5-Q6 (REPLACE-владелец)** — `action-orchestration-vs-command.md`
  на месте; владелец REPLACE-секвенса — петля/`DealStateMachine` (по фактам),
  фабрика «одна команда за проход»; `KILL_SWITCH` — команда. Записано в
  `DealStateMachine`/`ServiceCommandFactory`/`KillSwitchExecutor`;
  `replace-not-amend` консистентен (REPLACE больше не безвладельный).
- **N10/INSTR-Q2 (set-leverage)** — `PrecheckHandler` пишет рабочее плечо
  перед каждым ордером в `PRECHECK` (idempotent); `Instrument.leverage` —
  потолок/умолчание; CODE-представление write — деталь `CODE`.

### Стадия-2 (петля / финализация / ретрай) — закрыты чисто

- **N7-N8/D-M1/N11 (оболочка + concurrency + `maxAttempts`)** —
  `DealOrchestratorJob` несёт оболочку (CRON+`enabled`+async-фасад+критерии
  выборки active+due-for-retry по `nextRetryAt`); **D-M1 внутренне
  консистентен** (БД-блок на весь проход сериализует проходы → per-deal-guard
  не нужен; in-memory отвергнут; реализация — гейт `DONE` на `CODE`);
  **`maxAttempts` — один источник истины** (авторитет policy, поле сущности —
  снимок; конкурирующих источников не осталось).
- **N9/TR1 (защита risk-creating входа)** —
  `risk-creating-entry-protection.md` (двусторонний enforcement: блок в
  `PRECHECK` → `CLOSED`+`RISK_CONTROL`; нарушение постфактум → уровень 4);
  fail-open снят (`RISK_CREATING_ENTRY_WITHOUT_STOP` в
  `RiskValidator`/`RiskCheckResult`); `EntryFinalizedHandler` больше не
  допускает live-risk-позицию без защиты.
- **N12/CMD-Q4 (Precheck-чистота)** — инструмент-скоупный exchange-read вне
  command-layer (`IntegrationService` §«Инструмент-скоупный read»,
  `PrecheckHandler`); bulk-команда в command-layer не возвращена; orphan-скан
  → шаг 8.

### Гигиена + терминальный контракт — закрыты чисто

- **N13** — стале «handler'ы мигрируются отдельно» + битый `tasks/deal.md`
  сняты с `Deal.md`/`ack-not-runtime-truth.md` (grep по `tasks/deal.md` в
  `docs/` — 0; остаток «мигрируются отдельно» — в доках **других** сущностей,
  вне скоупа N13).
- **N14** — `risk-validator-scope.md` несёт все 4 финализационных типа
  (включая `FINALIZE_DEAL_ENTRY`).
- **N15** — `account-bills.md` относит расчёт `resultProfit` к шагу 7, не
  внутрь `FINALIZE_DEAL_EXIT`.
- **DEAL-Q2** — `Deal.md` §«Терминальный контракт финализации»: финализация
  всегда доводит до терминала; «прибыль обязательна» — про чистое `CLOSED`;
  число на ошибочном `EMERGENCY_CLOSED` → шаг 7.

### open-questions sweep

**DEAL-Q1, DEAL-Q2, CMD-Q5, CMD-Q6 удалены** из `open-questions.md`
(закрыты). Остальные открытые (INSTR-Q2-остаток, ORCH-Q1, CMD-Q4,
OKX-Q1..Q4, STRAT-Q4, IND-Q1, STRUCT-Q1, PHASE-Q1/Q2) — **ни один не гейтит
механику шага 6**.

## Пробелы по типам

### Несогласованности между доками

- **R1 — `deal-management.md:63-64` несёт устаревший безусловный инвариант
  `resultProfit`. Не гейтит (гигиена / реконсиляция формулировки).**
  Строка: «`ERROR → EMERGENCY_CLOSED` — после подтверждения отсутствия live
  risk; **для terminal обязательны `resultProfit`/`resultProfitCurrency`**».
  Слово «terminal» по группировке самого `Deal.md` lifecycle (`Terminal:
  CLOSED, EMERGENCY_CLOSED`) включает **ошибочный** терминал — что
  противоречит ставшему авторитетным после DEAL-Q2 контракту: `Deal.md`
  (lifecycle :44-48, :94-97, §«Терминальный контракт финализации» :114-134)
  ограничивает обязательность `resultProfit` **чистым** `CLOSED` и
  **освобождает** `EMERGENCY_CLOSED`. Причина — правки DEAL-Q2 на
  `GAPS_CLOSE_1` уточнили контракт в lifecycle/модели/executor-доках, но не
  пробросились в обзорный процесс-док. Все **исполнительные** доки
  (executor'ы, lifecycle, handler'ы) контракт несут верно; рассинхрон только
  в `deal-management.md`. → Э1.

### Name-level без структуры

Нет.

### Неотвеченные вопросы

Нет гейтящих (sweep `open-questions.md` чист по механике шага 6).

## Эскалации

### Э1 (R1). Стале безусловный `resultProfit`-инвариант в `deal-management.md`

- **Вопрос:** `deal-management.md:63-64` приписывает обязательность
  `resultProfit` всем terminal (включая `EMERGENCY_CLOSED`), что
  расходится с DEAL-Q2-контрактом (обязателен только для чистого `CLOSED`).
- **Ожидаемый владелец:** `knowledge-curator` (реконсиляция формулировки).
- **Кто ответил + трассировка:** reviewer (`concept-review`, кластер B)
  surface-ил при ripple-проверке DEAL-Q2; сверка — `deal-management.md:55`
  (`Terminal: CLOSED, EMERGENCY_CLOSED`), `:63-64` (безусловный инвариант) vs
  `Deal.md` lifecycle :44-48/:94-97/:114-134 (обязательность — про чистое
  `CLOSED`, ошибочный терминал освобождён). Все исполнительные доки несут
  контракт верно — рассинхрон изолирован в процесс-доке.
- **Ответ (предложение):** переформулировать `deal-management.md:63-64` —
  «для **чистого** terminal `CLOSED` обязательны `resultProfit`/
  `resultProfitCurrency`; для ошибочного `EMERGENCY_CLOSED` — по
  терминальному контракту (`docs/lifecycles/Deal.md` §«Терминальный контракт
  финализации»)».
- **Варианты:** без вариантов (правка-cleanup, формулировка выводится из
  уже принятого DEAL-Q2-контракта).
- **Целевой док:** `docs/processes/deal-management.md` (одна строка).
- **Ярлык исхода:** `выводимо-Предложение`.
- **Ярлык дефицита:** —.
- **Флаг действия CC:** `предложил`.

## Торговый фокус (`trading-review`)

- **TR1 — ЗАКРЫТО.** Инвариант обязательной защиты risk-creating входа
  выражен в доках двусторонне (`risk-creating-entry-protection.md`),
  fail-open снят на всех трёх точках (`RiskValidator`/`RiskCheckResult`,
  `PrecheckHandler`, `EntryFinalizedHandler`). Грунт корректен: стоп —
  конститутив стоп-driven системы [Tharp гл.9 с.234-236]; неограниченная
  ответственность → risk-of-ruin [Vince введ. с.6, гл.5 с.63]; корпусный
  раскол (Carver — sizing без стопов) учтён и неприменим (бот стоп-driven).
- **TR2/TR3/TR4 — остаются cross-cutting forward**, не регрессировали, не
  стали гейтящими (паркованы в `per-trade-risk-policy`/`backlog`; revisit —
  бэктест/фаза 2+).
- **Свежий проход по поверхности `GAPS_CLOSE_1` — новых блокирующих
  торговых находок нет.** Проверены торгово-состоятельными: финализация
  (4 executor'а + терминальный контракт — никогда не оставляет живую
  экспозицию, защиту до подтверждённого flat не снимает); REPLACE-владелец
  (окно без защиты в protective-пути отсутствует, двойной риск в entry-пути
  исключён); `KILL_SWITCH` (защита снимается последней, окна без защиты нет);
  set-leverage перед ордером (benign — PRECHECK гарантирует отсутствие
  открытой позиции до записи плеча); concurrency = БД-блок на проход (не
  торговый хазард в фазе 1).

## Сводка

- **Находок:** 1 (R1). Эскалаций: 1 (Э1). Торговых блокеров: 0 (TR1 закрыт;
  TR2-TR4 — forward).
- **Агрегация по ярлыкам исхода:** `выводимо-Предложение` — 1 (Э1).
- **Агрегация по ярлыкам дефицита:** без дефицита — 1 (Э1).
- **Флаги действия CC:** `предложил` — 1/1.
- **Верификация закрытий `DOCS_CHECK_1`:** все 15 пробелов (N1-N15) + TR1 +
  DEAL-Q1/DEAL-Q2/CMD-Q5/CMD-Q6 подтверждены закрытыми чисто; ripple-проверки
  (a)/(b)/(c) пройдены; гейтящих open-questions нет.
- **Гейт `CODE`:** **почти чисто.** Содержательно концепция шага 6 целостна
  и готова к `CODE`; единственный остаток — **R1** (минорная негейтящая
  гигиена-рябь в обзорном процесс-доке). По строгому правилу гейта («чистый
  `DOCS_CHECK` — без открытых находок и расхождений») R1 формально держит
  прогон не-чистым.
- **Торговый гейт:** **чисто** (блокеров нет).

## Рекомендация

Микро-**`GAPS_CLOSE_2`** на одну строку — закрыть **R1** (реконсиляция
формулировки `deal-management.md:63-64` под DEAL-Q2-контракт; владелец —
`knowledge-curator`). Иных пробелов нет. После правки — подтверждающий
**`DOCS_CHECK_3`** (узкий, по затронутому доку) либо принять R1 как
тривиальную гигиену-правку в составе `GAPS_CLOSE_2`; затем гейт `CODE`
чист и шаг 6 готов к `CODE`.
