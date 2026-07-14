# Snapshot v57

**Дата:** 2026-06-20.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — исполнение шага 5 фазы 1
(риск-преконтроль) — закрыта:** `CODE` → `SYNC_DOCS_FROM_CODE` → пост-хок
концепт-гейт §6a пройдены, шаг переведён в `DONE`. Заход — **плановое
завершение темы** (не continuation). Снапшот обычного состава; новый чат
стартует с PK-префлайта, затем **шаг 6 (FSM)**. Сменяет v56 (там концепт шага 5
был закрыт, шаг готов к `CODE`).

## Состояние

Фаза 1 роадмапа — `IN_PROGRESS`; шаги 1-5 `DONE`, **шаг 5 закрыт полностью**
(код написан, доки синхронизированы, §6a чист), шаги 6-11 `HOLD`. Ветка
`claude-audit`. Работа этой сессии — **staged, не закоммичено** (CC не
коммитит); **в этот раз тронут код** (`src/`, 47 файлов) + реконсиляция доков.

## Путь к точке (от v56)

v56 закрыл концепт шага 5 (готов к `CODE`). Эта сессия исполнила шаг 5
end-to-end по `roadmap-step-execution`:

**1. `CODE` — 47 файлов, компилируется чисто (`clean test-compile`, JBR-25).**
Материализованы: `InstrumentExternalRules` (доменная модель + JSONB-навес на
`instruments`, миграция `V8`, маппер + JSON-конвертер, DataService,
`InstrumentExternalRulesSyncJob` + фасад + конфиг + домаппинг OKX-полей);
расчётный слой (`MarketPriceData`-сборка по REST ticker; полные `Calculated*`-RVO
+ enum'ы; `CalculationContext(Factory)`, `PriceCalculator`, `SizeCalculator` с
risk-bounded сайзингом, `StrategyActionCalculator`); risk-слой (`RiskValidator`,
`RiskBlockResolver`, RVO). Аппрув — три **независимых** фокуса
(`conventions`/`performance`/`disaster`, без блокеров); safety-фиксы закрыты
(ctVal=0, `NumberFormatException` на сырьё, гард SL/TP/trailing>0, clamp
reduce-fraction, дубль чтения фазы).

**2. `SYNC_DOCS_FROM_CODE` (docs←code).** Независимый `divergence` → ~44
расхождения по 16 докам → реконсилировано (`knowledge-curator`); ренейм дока
`InstrumentExternalRulesService.md` → `InstrumentExternalRulesDataService.md`
(компонента в коде — DataService).

**3. §6a пост-хок концепт-гейт: `DOCS_CHECK_3` → `GAPS_CLOSE_3` →
`DOCS_CHECK_4` (чисто).** 2 находки. **C1** — механизм controlled-ошибки расчёта
выровнен (заведена §«Механизм сигнализации» в `CalculationError.md`:
суб-калькуляторы бросают `CalculationException`, `StrategyActionCalculator` ловит
→ `CalculationError` в `ERROR`-результате; внешний контракт слоя возвратный).
**C2** — комиссии: по решению пользователя отнесены к **шагу 7** (decision держит
концептуальным входом, код-учёт отложен — §«Учёт комиссий — отложен к шагу 7»).

**4. `DONE` + архивация.** Шаг 5 → `DONE`; 5 progress-отчётов перенесены в
`.claude/work/history/2026-06-20-phase-1-step-5-risk-precontrol/` + короткое
summary. Ролляп фазы — `IN_PROGRESS` (без изменений).

## Среда

Без изменений против v56 по контуру. **Нюанс сборки:** `java`/`mvn` не на PATH,
`mvnw` нет — CC собирал через JBR-25 (IDEA, `…/idea-2026.1/…/jbr`) как
`JAVA_HOME` + wrapper Maven 3.9.11 (`~/.m2/wrapper/dists/apache-maven-3.9.11-…`),
offline (`-o`). demo-среда (Vault test + demo + Postgres-test + сеть OKX)
доступна; prod вне контура.

## Следующий шаг

Новый чат — PK-префлайт, затем **шаг 6 (FSM)** по `roadmap-step-execution`
(docs-first: `TOOLING` → `DOCS_CHECK_N`/`GAPS_CLOSE_N` → `CODE` → …). Шаг 6
подхватывает форварды:

- **Error-политика** проектируется на шаге 6 и **гейтит его `CODE`** (закрывает
  TBD `codestyle` §«Обработка ошибок»; ретро-закрывает майоры шагов 2/4).
- **Жёсткие гейты `DONE` 6/7:** D-B3 (SUBMIT recovery-by-clientId) и D-M1
  (concurrency-guard исполнения команды) — оркестрационную петлю нельзя включать,
  пока не закрыты (`backlog.md` §Хвост шага 4).
- **Форварды шага 5 → шаг 6:** бесстоповый risk-creating вход не размещаем
  (аномалия); остаток INSTR-Q2 (тайминг set-leverage). FSM — владелец вызова
  `RiskValidator`/`StrategyActionCalculator` (кто/когда зовёт): эти компоненты
  написаны на шаге 5 как точки композиции, в FSM ещё не подключены.

## Принципы

Docs-first. Риск на сделку — база **свободного** депозита
(`externalAvailableEquity`); плечо связано лимитом риска (своего кэпа нет).
`InstrumentExternalRules` — JSONB-навес на `Instrument`. Калькулятор-слой:
внешний контракт возвратный (`StrategyActionCalculationResult`), внутри —
`CalculationException` (throw/catch на оркестраторе). `RiskValidator` **не ходит
в биржу** (читает persisted rules). Числа (лимит риска, комиссии, проскок,
пороги) — провизорные/отложенные, не выдумываются.

## Отложено / на будущее

- **Комиссии в риск-расчёте** → шаг 7 (вместе с fee-моделью / `trade-fee`;
  decision держит концептуальным входом, код-учёт отложен — `per-trade-risk-policy.md`
  §«Учёт комиссий — отложен к шагу 7», `backlog.md` §Шаг 7).
- **Бесстоповый risk-creating вход = аномалия, не размещаем** → шаг 6
  (`backlog.md` §Шаг 6).
- **Простой жёсткий кэп плеча на сделку** (зазор «узкий стоп → высокое плечо») —
  отсрочка (вариант (а)); revisit после бэктеста (`backlog.md` §Шаг 5).
- **Запас на проскок за стоп** — в фазе 1 не закладывается.
- **Контроль экспозиции** (риск на биржу — фаза 3; межбиржевой портфель —
  мультибиржевой этап).
- **Численный лимит риска на сделку** — provisional (бэктест/пользователь).

## Открытые вопросы

17 открытых — **без изменений против v56** (`open-questions.md` в этой сессии не
правился): DEAL-Q1, DEAL-Q2, **INSTR-Q2** (тайминг set-leverage → шаг 6),
ORCH-Q1, CMD-Q4, CMD-Q5, CMD-Q6, OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4, STRAT-Q4,
IND-Q1 (фаза 4), STRUCT-Q1 (фаза 2), PHASE-Q1, PHASE-Q2 (+ остаток). Все
non-gating для шага 5. Комиссии (C2) проведены как форвард-пункт `backlog.md`
§Шаг 7, не как открытый вопрос.

## Гейты делегирования

- **`code-writer`** — отработал `CODE` шага 5 (47 файлов).
- **`reviewer`** — отработали фокусы `conventions`/`performance`/`disaster`
  (`CODE`), `divergence` (`SYNC`), `concept` (§6a `DOCS_CHECK_3`/`_4`) —
  независимыми субагентами, не автор кода/доков.
- **`knowledge-curator`** — реконсиляция `SYNC_DOCS_FROM_CODE` + `GAPS_CLOSE_3`
  (вкл. ренейм компонент-дока).
- **Следующий чат (шаг 6):** `solution-designer` (концепт FSM + error-политика),
  затем `code-writer`; `reviewer` фокусы `concept`+`trading` в петле.
- **`integrator`** — правки OKX-доков этой сессии — статус потребления/маппинг
  наших полей; офдок-факты не менялись.

## Режим работы

**Шаг 5 закрыт (`DONE`).** Новый чат — шаг 6 (FSM), docs-first с нуля под-шагов.
Не отладка пайплайна, не продолжение шага 5.

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v57`** (заменяет
  v56 в префлайте — **обновить PK после коммита**).
- **Staged, не закоммичено (дельта шага 5, ~76 файлов):**
  - **код (47):** новые пакеты `domain.command.calc` (RVO + enum'ы +
    `CalculationContext(Factory)`/`Price`/`Size`/`StrategyActionCalculator`),
    `domain.command.risk` (`RiskValidator`/`RiskBlockResolver` + RVO); доменные
    `InstrumentExternalRules` (+ snapshot), `MarketPriceData` (+ snapshot);
    мапперы/конвертеры; persistence (`InstrumentEntity` JSONB-колонка, репозитории,
    DataServices); интеграция (`IntegrationService`+OKX impl, `InstrumentOkxResponse`);
    job + фасад + конфиг; правки `ServiceCommandFactory`; миграция
    `V8__add_instrument_external_rules.sql`; `application-{prod,test}.yaml`.
  - **доки (21):** реконсиляция `SYNC` + `GAPS_CLOSE_3` (calc/risk компонент-доки,
    `InstrumentExternalRules` model+mapping, `InstrumentOkxResponse`,
    `MarketPriceData` model+mapping, `per-trade-risk-policy`, процессы); ренейм
    `InstrumentExternalRulesService.md` → `…DataService.md`.
  - **пайплайн:** `backlog.md` (§Шаг 6 + §Шаг 7 форварды), `roadmap/phase-1.md`
    (статусы CODE/SYNC/§6a/DONE); history (summary + подпапка с 5 отчётами);
    этот снапшот.
- **Untracked (не наши / транзиентные):** `.claude/work/run-logs/*.log`,
  `fill-plan.py`, `tradingbot.iml`, `vault.hcl`.
- **`external-source-sync`:** офдок-факты OKX не менялись — ре-синхронизация не
  нужна.
