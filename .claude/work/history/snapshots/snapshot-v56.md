# Snapshot v56

**Дата:** 2026-06-20.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — концепт-проработка шага 5 фазы 1
(риск-преконтроль) — закрыта:** `GAPS_CLOSE_1` + подтверждающий
`DOCS_CHECK_2` чист, концепт-гейт `CODE` пройден, шаг готов к `CODE`. Заход —
**плановое завершение темы** (не continuation). Снапшот обычного состава;
новый чат стартует с PK-префлайта, затем `CODE` шага 5. Сменяет v55 (тема
контура source-api там закрыта).

## Состояние

Фаза 1 роадмапа — `IN_PROGRESS`; шаги 1-4 `DONE`, **шаг 5 — концепт закрыт,
готов к `CODE`** (статус `DOCS_CHECK_2`, чистый прогон, концепт-гейт `CODE`
пройден), шаги 6-11 `HOLD`. Ветка `claude-audit`. Работа этой сессии —
**staged, не закоммичено** (CC не коммитит); правки доковые (концепт), код не
трогался.

## Путь к точке (от v55)

v55 закрыл тему контура тестов API источника. Эта сессия — концепт шага 5
(риск-преконтроль), три под-шага:

**1. `DOCS_CHECK_1` — 7 пробелов + 1 блокирующая торговая.** Risk-layer **в
основном уже материализован** миграцией из архива (процессы `risk-evaluation`/
`strategy-action-calculation`, компоненты `RiskValidator`/`RiskBlockResolver`/
калькуляторы, RVO, правила). Механика чиста; пробелы — на **входах** валидатора:
N1+N2 (`InstrumentExternalRules` не материализована + трёхсторонняя
несогласованность по max-size/leverage полям), N3 (`RiskSettings` name-level),
N4/TR1 (нет кода/правила worst-case guard'а экспозиции), N5 (паттерн
потребления), N6/N7 (гигиена). Отчёт —
`.claude/work/progress/phase-1-step-5-docs-check-1.md`.

**2. `GAPS_CLOSE_1` — риск-политика на сделку проработана с пользователем.**
Зафиксирована решением `docs/decisions/per-trade-risk-policy.md`: трёхуровневая
модель риска (сделка — фаза 1, биржа — фаза 3, межбиржевой портфель —
мультибиржевой этап); риск на сделку = убыток на стопе как % от **свободного**
депозита (`externalAvailableEquity`); плечо связано лимитом риска (своего кэпа
нет); без поправки на проскок в фазе 1; строгий блок при невмещении даже на
`minSz`; лимит провизорный; нет RVO `RiskSettings` (поля `StrategyDetail`).
`InstrumentExternalRules` **материализована** (`docs/decisions/instrument-external-rules-materialization.md`:
JSONB-навес на `Instrument`, домаппинг per-order max sizes + `lever`/`state`).
Закрыты RISK-Q1/RISK-Q2/INSTR-Q1; INSTR-Q2 сужен до тайминга set-leverage.

**3. Ратификация (а) + `DOCS_CHECK_2` — чисто.** Пользователь ратифицировал
вариант (а) (в фазе 1 кэпа плеча/экспозиции нет); заведена форвард-заметка
(отложенный жёсткий кэп плеча, зазор «узкий стоп → высокое плечо», revisit
после бэктеста/живых прогонов — `backlog.md` §Шаг 5). Подтверждающий
`DOCS_CHECK_2` независимыми ревьюер-фокусами (concept + trading): N1-N7 все
CLOSED-CLEAN, торговый гейт чист (TR1 разрешена корпусно-состоятельно — кэп
экспозиции — уровень риска на биржу/портфель, фаза 3), новых блокеров правки не
внесли; 3 микро-рассинхрона закрыты на месте. **Концепт-гейт `CODE` пройден.**
Отчёт — `.claude/work/progress/phase-1-step-5-docs-check-2.md`.

## Среда

Без изменений против v55. CC компилирует/гоняет тесты (`mvn -o test`,
corretto-25); demo-среда (Vault test + demo + Postgres-test + сеть OKX)
доступна; prod — вне контура. Для `CODE` шага 5 — обычный цикл сборки.

## Следующий шаг

Новый чат — PK-префлайт, затем **`CODE` шага 5**. Объём CODE:

- **риск-валидатор** — `RiskValidator` + `RiskBlockResolver` + RVO
  (`RiskValidationResult`/`RiskCheckResult`/`RiskBlockAction`);
  `RISK_PER_TRADE_EXCEEDED` (база свободного депозита, убыток на стопе),
  liquidation guard, instrument-constraints, reduce-only safety-коды;
- **достройка калькуляторного слоя** — `StrategyActionCalculator` +
  `CalculationContextFactory` + полные `CalculatedStrategyAction`/
  `CalculatedPrice`/`CalculatedSize` (на шаге 4 — минимальные command-facing
  заглушки `domain.command.calc`); **risk-constrained sizing** в `SizeCalculator`
  (размер под лимит риска, пол `minSz`, блок при невмещении);
- **`InstrumentExternalRules`** — материализация (JSONB-навес на `Instrument` +
  `InstrumentExternalRulesSyncJob` + маппинг домапленных полей).

CODE по процессу `roadmap-step-execution`: `code-writer` + **независимые**
адверсариальные фокусы (`conventions`/`performance`/`disaster`) с
зафиксированным исходом + аппрув → `SYNC_DOCS_FROM_CODE` (фокус `divergence`) →
пост-хок концепт-гейт §6a при концепт-инкрементах → `DONE`. FSM-вызов
валидатора (кто/когда зовёт) — шаг 6, не этот шаг.

## Принципы

Docs-first (концепт доведён до целостности до кода). Риск на сделку — база
**свободного** депозита; плечо связано лимитом риска (своего кэпа нет, отложен).
`InstrumentExternalRules` — JSONB-навес на `Instrument` (дефолт персистентности:
нет FK-целей). Собственный преконтроль — основной; `RiskValidator` **не ходит в
биржу** (читает persisted rules); серверный `order-precheck` вне нашего режима
маржи. Числа (лимит риска, проскок, пороги структуры) — провизорные, калибруются
бэктестом, не выдумываются.

## Отложено / на будущее

- **Простой жёсткий кэп плеча на сделку** (зазор «узкий стоп → высокое плечо при
  малом убытке по стопу») — ратифицированная отсрочка (вариант (а)); revisit
  после бэктеста/живых прогонов (`backlog.md` §Шаг 5, рационал —
  `per-trade-risk-policy.md` §Альтернативы).
- **Запас на проскок за стоп** — в фазе 1 не закладывается; добавить с числами
  бэктеста.
- **Контроль экспозиции** (риск на биржу — фаза 3; межбиржевой портфель —
  мультибиржевой этап) — per-tier `position-tiers`, позиционные лимиты как доля
  OI запаркованы туда.
- **Численный лимит риска на сделку** — provisional (бэктест/пользователь).
- **prod-проверка success deep-архива AG5** — ад-хок вне контура (из v55).

## Открытые вопросы

17 открытых (после закрытий этой сессии): DEAL-Q1, DEAL-Q2, **INSTR-Q2**
(сужен — тайминг set-leverage, форвард к шагу 6), ORCH-Q1, CMD-Q4, CMD-Q5,
CMD-Q6, OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4, STRAT-Q4, IND-Q1 (крипто-часть, фаза 4),
STRUCT-Q1 (фаза 2), PHASE-Q1, PHASE-Q2. **Закрыты этой сессией:** RISK-Q1
(нет RVO `RiskSettings`), RISK-Q2 (worst-case guard → уровень риска на
биржу/портфель, фаза 3), INSTR-Q1 (материализация rules). Все 17 — non-gating
для `CODE` шага 5 (INSTR-Q2-остаток — шаг 6; прочее — свои шаги/фазы).

## Гейты делегирования

- **`reviewer`** — фокусы `concept` + `trading` отработали `DOCS_CHECK_1`/`_2`
  шага 5 (независимый подтверждающий проход — субагентами, не автор правок).
- **`code-writer`** — активируется на `CODE` шага 5 (следующий чат).
- **`tester`** — контур source-api закрыт (v55), новых задач нет.
- **`integrator`** — правки OKX-доков этой сессии — только статус потребления/
  маппинг наших полей; офдок-факты не менялись, прогон интегратора/C3 не
  требовался.
- **`solution-designer`** — без изменений в счёте автономии (риск-политика —
  авторитетный вход пользователя + предложения CC, не автономное проектирование).

## Режим работы

**Концепт шага 5 закрыт.** Новый чат — `CODE` шага 5 (риск-валидатор +
достройка калькуляторного слоя + материализация `InstrumentExternalRules`). Не
отладка пайплайна, не концепт-ревью.

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v56`** (заменяет
  v55 в префлайте — **обновить PK после коммита**).
- **Закоммичено пользователем** (до этой сессии): дельта закрытия контура
  source-api (коммиты «Тесты апи…»).
- **Staged, не закоммичено (дельта концепта шага 5):** 2 новых решения
  (`per-trade-risk-policy.md`, `instrument-external-rules-materialization.md`);
  ~22 отредактированных дока (`RiskValidator`, `RiskCheckResult`,
  `RiskBlockResolver`, `CalculationContext`, `CalculatedStrategyAction`,
  `SizeCalculator`; `InstrumentExternalRules` + mapping + `SyncJob` +
  `InstrumentOkxResponse`; `Instrument` + mapping; `trading-constraints`,
  `BalanceContainer`, `Strategy`, `market-data-calculation`; контракты
  `order-precheck`/`position-tiers`/`price-limit`/`open-interest`);
  `open-questions.md`, `backlog.md`, `phase-1.md`; 2 progress-отчёта
  (`docs-check-1`/`docs-check-2`); этот снапшот.
- **Untracked (не наши / транзиентные):** `.claude/work/run-logs/*.log`,
  `fill-plan.py`, `tradingbot.iml`, `vault.hcl`.
- **`external-source-sync`:** правки OKX-доков касались только нашей стороны
  (статус потребления, маппинг наших полей) — офдок-факты OKX не менялись,
  ре-синхронизация не нужна.
