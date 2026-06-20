# DOCS_CHECK_1 — шаг 5 фазы 1 (Риск-преконтроль, `RiskValidator`)

## На какой вопрос отвечает этот файл

На каком под-шаге мы в исполнении шага 5 фазы 1 и какие пробелы концепции
нашёл первый прогон сквозной проверки (`concept-review` + `trading-review`).

## Контекст прогона

- **Шаг:** 5 фазы 1 — «Риск-преконтроль (валидация перед отправкой: размер,
  ограничения инструмента, reduce-only, лимиты)».
- **Под-шаг:** `DOCS_CHECK_1` (первая итерация проверки концепции). `TOOLING`
  пройден **без новых артефактов** — фокусы `concept-review` / `trading-review`
  активны (реестр `reviewer`), новых агентов/скиллов под риск-преконтроль не
  потребовалось.
- **Что шаг должен делать функционально:** материализовать risk-layer —
  валидацию уже **рассчитанного** действия (`CalculatedPrice`/`CalculatedSize`)
  до создания торговой команды: risk-per-trade, ограничения инструмента
  (min/lot/max size, tick, max leverage, торгуемость), reduce-only/safety на
  выходах, лимиты экспозиции/плеча. Поток: `StrategyActionCalculator → RiskValidator
  → RiskBlockResolver → ServiceCommandFactory`. Шаг 5 также **достраивает
  калькуляторный слой** — на шаге 4 `CalculatedStrategyAction`/`CalculatedPrice`/
  `CalculatedSize` материализованы как минимальные command-facing заглушки;
  полная структура и `StrategyActionCalculator` помечены работой шага 5
  (`CalculatedStrategyAction.md` §«Статус кода (шаг 4)»).
- **Особенность:** концепт risk-layer **в основном уже материализован**
  миграцией из архива — есть процессы (`risk-evaluation`,
  `strategy-action-calculation`), компоненты (`RiskValidator`,
  `RiskBlockResolver`, калькуляторы), RVO (`RiskValidationResult`,
  `RiskCheckResult`, `RiskBlockAction`, `Calculated*`), правила
  (`risk-validator-scope`, `trading-constraints`, `no-partial-close`,
  `reduce-only-invariant`), модель ограничений (`InstrumentExternalRules`).
  Первый прогон подтверждает зрелую механику и вычленяет то, что **гейтит
  `CODE`**: пробелы сосредоточены на **входах** валидатора.

## Охват

### Проверены (в охвате шага 5)

- **Процессы:** `docs/processes/risk-evaluation.md`,
  `docs/processes/strategy-action-calculation.md`.
- **Компоненты:** `RiskValidator.md`, `RiskBlockResolver.md`,
  `StrategyActionCalculator.md`, `SizeCalculator.md`, `PriceCalculator.md`,
  `InstrumentExternalRulesSyncJob.md`.
- **RVO / component-models:** `RiskValidationResult.md`, `RiskCheckResult.md`
  (енум `RiskCheckCode` — набор проверок), `RiskBlockAction.md`,
  `CalculatedStrategyAction.md`, `CalculatedSize.md`, `CalculatedPrice.md`,
  `CalculationContext.md`, `CalculationError.md`,
  `StrategyActionCalculationResult.md`.
- **Доменные модели:** `InstrumentExternalRules.md`, `BalanceContainer.md`.
- **Правила:** `risk-validator-scope.md`, `trading-constraints.md`,
  `no-partial-close.md`, `reduce-only-invariant.md` (OKX).
- **Mapping / интеграция:** `mapping/InstrumentExternalRules.md`,
  `integrations/okx/InstrumentOkxResponse.md`, OKX-контракты `order-precheck`,
  `max-size`, `price-limit`, `position-tiers`, `account-position-risk`,
  `coverage-manifest`.
- **Open-questions:** проход по `open-questions.md` (RISK-Q1, RISK-Q2,
  INSTR-Q1, INSTR-Q2; смежно DEAL-Q1, OKX-Q1).

### Вне охвата (помечены, не проверялись по существу)

- **FSM / оркестрация (шаг 6):** кто и когда зовёт `RiskValidator` —
  `DealStateMachine`, handler'ы (`PrecheckHandler`, …). Risk-layer спроектирован
  как точка композиции (`risk-evaluation` зовётся из `deal-management`);
  владелец вызова — шаг 6.
- **Deal / P&L (шаг 7):** финализация, `Deal.resultProfit`, bills/fills
  (OKX-Q1/Q2/Q3, DEAL-Q1/Q2).
- **AnomalyJob / set-leverage-исполнение (шаг 8 / смежно INSTR-Q2):** «чужой
  live risk», borrow/debt-детект, оркестрация выставления плеча на бирже.

## Стадия остановки

Обход дошёл до **стадии 2** (компоненты + модели) — прошёл все стадии.

- **Стадия 0 (гейтящие технические вопросы / скоуп):** чисто. Механика
  риск-преконтроля полностью специфицирована: когда вызывается / не вызывается
  (`risk-validator-scope`), поток (`risk-evaluation`), реакция на
  `ALLOWED/WARNING/BLOCKED`, маппинг `BLOCKED → RiskBlockAction → handler`,
  политика баланса (absent/stale → `REFRESH_BALANCE`, validator не вызывается).
  Гейтящего вопроса уровня «как добываем данные» нет; границы скоупа
  (finalize/FSM — поздние шаги) заданы формулировкой.
- **Стадия 1 (процессы):** чисто. `strategy-action-calculation` и
  `risk-evaluation` целостны и согласованы по границе «калькулятор не считает
  risk-policy / risk-метрики не передаются через границу» (`CalculatedRiskMetrics`
  не передаётся — согласовано в `strategy-action-calculation.md` §Границы,
  `risk-evaluation.md` §Границы, `CalculatedStrategyAction.md`).
- **Стадия 2 (компоненты + модели):** найдены пробелы (ниже) — сосредоточены на
  **входах** валидатора (`InstrumentExternalRules`, `RiskSettings`, guard
  экспозиции).

## Пробелы по типам

### Name-level без структуры (нужна структура)

- **N1 — `InstrumentExternalRules` не материализована, а шаг 5 — её потребитель.
  Гейтит `CODE`.** Модель и sync-job (`InstrumentExternalRulesSyncJob`) явно
  помечены «**Отложено за пределы шага 1** … нужна поздним шагам (… риск-преконтроль,
  проверка торгуемости инструмента) — backlog п.9; … не материализуется»
  (`InstrumentExternalRules.md:15-19`, `InstrumentExternalRulesSyncJob.md:14-25`).
  Шаг 5 **конструирует** валидацию против неё: `SizeCalculator` читает
  `ctVal/lotSz/minSz`, `PriceCalculator` — `externalTickSize`, `RiskValidator`
  заявляет её входом (`RiskValidator.md:17`) и эмитит `SIZE_BELOW_MIN`,
  `SIZE_LOT_STEP_INVALID`, `SIZE_ABOVE_LIMIT`, `EXCHANGE_MAX_LEVERAGE_EXCEEDED`,
  `INSTRUMENT_NOT_LIVE`, `INSTRUMENT_RULES_MISSING` (`RiskCheckResult.md`).
  Список полей есть, но **persistence-проекция, фактическая материализация
  sync-job'ом и сорсинг `externalState→Status` / `externalMaxLeverage` отложены и
  открыты** (INSTR-Q1 — снапшот-концепция vs persisted rules, дом справочных
  полей, возможный ренейм; INSTR-Q2 — роль `externalMaxLeverage` как потолка,
  соотнесение с `Instrument.externalLeverage`, кто/когда set-leverage). Это
  центральный гейт стадии 2. → Э1.

- **N3 — `RiskSettings` только name-level (RISK-Q1). Гейтит `CODE`.** Заявлен
  входом `RiskValidator` (`RiskValidator.md:17` «структура — RISK-Q1») и полем
  `CalculationContext` (`CalculationContext.md` «структура и материализация под
  вопросом, см. RISK-Q1»). `RiskValidator` читает его для `RISK_PER_TRADE_EXCEEDED`.
  Структура неизвестна: часть `StrategyDetail` (`riskPerTradePercent`/`maxLeverage`)
  + глобальная policy **или** отдельный RVO — не решено (RISK-Q1). Не
  pass-through: валидатор ветвится по полям. → Э2.

- **N4 — нет кода проверки экспозиции/позиционного лимита; worst-case guard
  (RISK-Q2) не специфицирован. Гейтит `CODE` + торгово-блокирующее (TR1).**
  `RiskValidator` **считает** метрику `position exposure после действия`
  (`RiskValidator.md:24-26`), но в `RiskCheckCode` **нет кода**, блокирующего по
  экспозиции/нотиналу/плечу-поверх-биржевого. Единственный потолок —
  `EXCHANGE_MAX_LEVERAGE_EXCEEDED` (биржевой максимум), а
  `trading-constraints.md:16-19` прямо фиксирует «наших потолков нет».
  `SIZE_ABOVE_LIMIT` привязан к **per-order** биржевым лимитам размера, не к
  risk-driven кэпу экспозиции. RISK-Q2 (владелец — **шаг 5**) держит этот guard
  открытым. Метрика без кода-блокера — мёртвая. → Э3 (торговый разбор — §Торговый
  фокус, TR1).

### Несогласованности между доками

- **N2 — трёхсторонняя несогласованность по per-order max-size и max-leverage
  полям. Гейтит `CODE`.** Доменная модель **объявляет** `externalMaxLimitSize`/
  `externalMaxMarketSize`/`externalMaxTriggerSize`/`externalMaxStopSize`/
  `externalMaxLeverage` (`InstrumentExternalRules.md:65-69`) и перечисляет
  использование «проверки min/max limits», «проверки биржевого max leverage»
  (`:37-39`). Но mapping **не маппит** их: `maxLmtSz/maxMktSz/maxTriggerSz/maxStopSz`
  — в «Не маппимые поля OKX … per-order лимиты — **пока не используем**»
  (`mapping/InstrumentExternalRules.md:87-92`), `externalMaxLeverage` —
  INSTR-Q2-отложен (`:71-76`); `posLmtAmt/posLmtPct/maxPlatOILmt` (позиционные
  лимиты — кандидат RISK-Q2) тоже не маппятся. А нативный DTO-док **утверждает
  обратное**: «`maxLmtSz`/… — потребляются отдельной моделью
  `InstrumentExternalRules`» (`InstrumentOkxResponse.md:53-55`). Три дока
  расходятся в том, заполняются ли эти поля → у проверок `SIZE_ABOVE_LIMIT` и
  `EXCHANGE_MAX_LEVERAGE_EXCEEDED` **нет задокументированного источника данных**.
  → Э1 (вместе с N1).

- **N6 — `RiskBlockResolver` атрибутирует `направление` и `reduce-only intent`
  полю `calculatedAction`, которого там нет. Не гейтит (medium).**
  `RiskBlockResolver.md:35-36`: «`calculatedAction` — рассчитанные
  цена/размер/**направление**/**reduce-only intent**». Но
  `CalculatedStrategyAction` несёт только `sourceAction`/`calculatedPrice`/
  `calculatedSize`/`description` (`CalculatedStrategyAction.md:23-28`) — отдельных
  полей `направление`/`reduce-only intent` нет. Направление достижимо через
  `sourceAction` (но `StrategyAlgoOrderAction`/`StrategyPositionAction` его не
  несут) и через `DealContext.deal.direction` (вход валидатора); reduce-only
  intent кодируется `CalculatedSize.sizeMode`/`closeFraction`. Автор кода по
  тексту `RiskBlockResolver` будет искать несуществующее поле — реальная
  doc↔doc неточность, не блокер. → Э5.

- **N7 — битая кросс-ссылка: `CalculatedStrategyAction` цитирует
  `RiskCheckResult.md` за клаузу «метрики считаются внутри risk-layer».
  Косметика, не гейтит.** `CalculatedStrategyAction.md:36-38` ссылается на
  `RiskCheckResult.md` за утверждение про вычисление метрик, но владелец клаузы —
  `RiskValidator.md:21-26` («Метрики (считает сам)»); `RiskCheckResult.md` такой
  клаузы не содержит. Концепт (метрики не передаются через границу) согласован;
  неверен лишь адрес ссылки. → Э5.

### Неотвеченные вопросы / нерешённый паттерн потребления (гейтят чистоту, не обход)

- **N5 — паттерн потребления constraint-эндпоинтов (live-вызов vs persisted) не
  решён; `order-precheck` неприменим в нашем режиме маржи.** `RiskValidator`
  **запрещено** ходить в биржу (`RiskValidator.md:31-33`), но данные тиров
  (`position-tiers.maxLever`/`maxSz` — реальный потолок плеча и кандидат
  позиционного лимита) и динамический `price-limit` **нигде не персистятся** и
  валидатору не привязаны (grep: `position-tiers`/`price-limit` упомянуты только
  в своих контрактах + манифесте). `order-precheck` помечен форвард-кандидатом
  В-2, но офдок: «Only applicable to Multi-currency / Portfolio margin mode»
  (`acctLv=3|4`) — наш контур isolated/Futures (`acctLv=2`,
  `trading-constraints.md`), значит серверный precheck **неприменим** и контракт
  сам заключает «не замена собственному преконтролю» (`order-precheck.md:24-30`).
  Нужно зафиксировать: (а) шаг 5 делает **собственный** преконтроль (биржевой
  precheck вне режима — out of scope); (б) откуда берётся per-tier потолок плеча
  / позиционный лимит для RISK-Q2 — persisted-проекция тиров vs единое
  `externalMaxLeverage`. Частично питает Э3 (RISK-Q2). → Э4.

## Блокирующие открытые вопросы

Из `open-questions.md` (со ссылками) — все имеют горизонт-владельца **шаг 5**,
гейтят **`CODE`**, не обход:

- **RISK-Q1** — структура/материализация `RiskSettings` (= N3). **Блокирует
  `CODE`.**
- **RISK-Q2** — worst-case guard поверх вычисленного плеча/позиции (= N4, TR1).
  Владелец явно — шаг 5. **Блокирует `CODE`** (нет кода-проверки) + торговое.
- **INSTR-Q1** — снапшот-концепция vs persisted `InstrumentExternalRules`, дом
  справочных полей, возможный ренейм (= N1). **Блокирует `CODE`.**
- **INSTR-Q2** — роль `externalMaxLeverage`/`externalLeverage` как потолка плеча,
  кто/когда set-leverage (= N1/N2). **Блокирует `CODE`** в части потолка плеча.

**Смежные, НЕ гейтящие шаг 5 (форвард к своим шагам):** DEAL-Q1 (retry-state
финализации — шаг 7), OKX-Q1 (persisted `TradeFill` — шаг 7), CMD-Q4 (orphan
live orders/algo — Precheck/AnomalyJob, шаги 6/8).

## Эскалации

Маршрут first-cut (через владельцев, `concept-review.md` §Эскалация). CC в
прогоне `DOCS_CHECK` **предлагает** (варианты/крен), не финализирует — закрытие
на `GAPS_CLOSE_1`. Все четыре центральные развилки имеют штатный горизонт-владельца
**шаг 5** — DOCS_CHECK_1 поднимает их закономерно.

### Э1 (N1 + N2 / INSTR-Q1 + INSTR-Q2). Материализация `InstrumentExternalRules` + маппинг max-size/leverage

- **Вопрос:** материализовать `InstrumentExternalRules` (persistence + маппинг +
  sync) как источник ограничений инструмента для валидатора; снять трёхстороннюю
  несогласованность по `maxLmtSz/maxMktSz/maxTriggerSz/maxStopSz` и
  `externalMaxLeverage` (объявлены + заявлены в использовании, но не маппятся).
- **Ожидаемый владелец:** `solution-designer` (форма материализации rules vs
  снапшот, INSTR-Q1) + `integrator` (маппинг недостающих OKX-полей в snapshot/
  модель) + `knowledge-curator` (размещение/возможный ренейм).
- **Кто ответил + трассировка:** reviewer (`concept-review`) surface-ил из
  зависимости валидатора/калькуляторов от модели; сверка с owner-доками —
  `InstrumentExternalRules.md` (поля + «Отложено за пределы шага 1»),
  `mapping/InstrumentExternalRules.md` («per-order лимиты — пока не используем»;
  INSTR-Q2-отложенный `externalMaxLeverage`), `InstrumentOkxResponse.md`
  (обратное утверждение «потребляются `InstrumentExternalRules`»),
  `InstrumentExternalRulesSyncJob.md`, INSTR-Q1/Q2.
- **Ответ (предложение):** на `GAPS_CLOSE_1` решить INSTR-Q1 (дом rules:
  по правилу персистентности дефолт — JSONB на строке `Instrument`, пока нет
  FK-ссылок; самостоятельная таблица — осознанное исключение); домаппить
  per-order max-size + `externalMaxLeverage` (источник `externalMaxLeverage` —
  INSTR-Q2: биржевой `lever` vs per-tier `position-tiers.maxLever`); привести три
  дока к одному утверждению.
- **Варианты + крен:** (а) `InstrumentExternalRules` остаётся самостоятельной
  persisted-моделью, материализуется из снапшота (INSTR-Q1 вар.1); (б)
  persisted-проекция снапшота с ренеймом (вар.2). **Крен — (а)** + JSONB-дом на
  `Instrument` (минимальная инвазивность, нет FK-нужды). Источник потолка плеча:
  (i) единое `externalMaxLeverage` из `/public/instruments` `lever`; (ii) per-tier
  `position-tiers.maxLever`. **Крен — (ii)** для корректного потолка (INSTR-Q2 +
  N5), но это концепт-выбор владельца.
- **Целевой док:** `InstrumentExternalRules.md` + `mapping/InstrumentExternalRules.md`
  + `InstrumentOkxResponse.md` (снять расхождение); persistence-проекция; закрытие
  INSTR-Q1, продвижение INSTR-Q2.
- **Ярлык исхода:** `варианты-с-креном` (форма + источник плеча) + `принято-в-работу`
  (домаппинг полей — рутинная интеграция).
- **Ярлык дефицита:** `работа` (проектное решение по материализации/источнику плеча).
- **Флаг действия CC:** `предложил`.

### Э2 (N3 / RISK-Q1). Структура `RiskSettings`

- **Вопрос:** есть ли отдельный RVO `RiskSettings` или risk-настройки берутся из
  `StrategyDetail` (`riskPerTradePercent`/`maxLeverage`) + глобальной policy.
- **Ожидаемый владелец:** `solution-designer` (представление — концепт-выбор) +
  `knowledge-curator` (размещение).
- **Кто ответил + трассировка:** reviewer surface-ил из входов `RiskValidator`/
  `CalculationContext`; сверка — RISK-Q1 (цитаты архива: `CalculationContext` §4,
  «Оценка рисков» §2.1/2.3), `strategy-materialization-and-validation.md` уже
  ссылается на валидацию `riskPerTradePercent` (>3% legal-но-flagged) — поле уже
  живёт в авторинге стратегии.
- **Ответ (предложение):** на `GAPS_CLOSE_1` зафиксировать risk-настройки как
  поля `StrategyDetail` (+ при необходимости глобальная policy), без отдельного
  RVO до явной потребности; закрыть RISK-Q1.
- **Варианты + крен:** (а) не отдельный RVO — из `StrategyDetail` + policy; (б)
  самостоятельный `RiskSettings` RVO. **Крен — (а)** (поле уже в `StrategyDetail`;
  RVO без новой структуры ценности не несёт) — концепт-выбор владельца.
- **Целевой док:** `CalculationContext.md` / `RiskValidator.md` (снять пометку
  RISK-Q1); при варианте (б) — новый `docs/components/models/RiskSettings.md`;
  закрытие RISK-Q1.
- **Ярлык исхода:** `варианты-с-креном`.
- **Ярлык дефицита:** `работа` (концепт-выбор представления).
- **Флаг действия CC:** `предложил`.

### Э3 (N4 / RISK-Q2 / TR1). Worst-case guard экспозиции/плеча + код-проверка

- **Вопрос:** ввести guard поверх вычисленного плеча/позиции (биржевой максимум
  на крипто-перпах ~50-100× — не guard rail) и соответствующий `RiskCheckCode`;
  валидатор уже считает `position exposure` без кода-блокера.
- **Ожидаемый владелец:** `solution-designer` (форма guard + код проверки) +
  `trading-specialist` (торговое обоснование границ). **Численные значения порога
  — хвост пользователя / бэктест-гейт фазы 2** (как STRUCT-Q1), но **существование
  проверки** корпусно-обязательно (TR1).
- **Кто ответил + трассировка:** reviewer (`concept-review` surface N4 +
  `trading-review` TR1); сверка — `RiskCheckResult.md` (нет кода экспозиции),
  `RiskValidator.md` (метрика `position exposure`), `trading-constraints.md`
  («наших потолков нет»), `position-tiers.md` (`maxSz` — кандидат позиционного
  лимита), RISK-Q2. Торговый грунт — `risk-and-sizing.md`: позиционные лимиты
  обязательны для автоматики [Carver AFTS т.4 с.651-655]; кэп экспозиции —
  единственный форвардный риск-контроль [Kaufman гл.24 с.2188,2192-2193]; ни одна
  позиция не обнуляет счёт на максимальном движении [Carver ST гл.9 с.180-181];
  будущий убыток > исторического [Vince гл.2 с.30-31].
- **Ответ (предложение):** на `GAPS_CLOSE_1` добавить в `RiskCheckCode`
  код(ы) guard'а (напр. `POSITION_EXPOSURE_LIMIT_EXCEEDED` / `LEVERAGE_ABOVE_GUARD`),
  завести правило формы guard (минимум из: кэп плеча на инструмент / позиционный
  лимит по нотиналу или доле капитала / доля открытого интереса); численные
  пороги — провизорные дефолты с пометкой «value: бэктест» (паттерн STRUCT-Q1);
  закрыть RISK-Q2.
- **Варианты + крен:** (1) кэп плеча на инструмент; (2) позиционный лимит
  (нотинал/доля капитала/доля OI); (3) **минимум из нескольких границ**; (4) иное.
  **Крен — (3)** (корпус: «минимум из…», страховка от худшего входа). Форма —
  владелец; численные пороги — пользователь/бэктест.
- **Целевой док:** `RiskCheckResult.md` (новый код), новое правило guard'а
  (`trading-constraints` / отдельный rule) + связка с `RiskValidator` метрикой;
  закрытие RISK-Q2.
- **Ярлык исхода:** `варианты-с-креном` (форма) + `принято-в-работу` (код проверки).
- **Ярлык дефицита:** `работа` (форма guard) + `подтверждение` (численные пороги —
  бэктест/пользователь).
- **Флаг действия CC:** `предложил`.

### Э4 (N5). Паттерн потребления constraint-эндпоинтов (live vs persisted; precheck вне режима)

- **Вопрос:** зафиксировать, что шаг 5 делает **собственный** преконтроль
  (`order-precheck` неприменим в isolated/Futures `acctLv=2`), и откуда валидатор
  берёт per-tier потолок плеча / позиционный лимит, раз ему запрещён live-вызов
  биржи (тиры/`price-limit` нигде не персистятся).
- **Ожидаемый владелец:** `solution-designer` (паттерн потребления) + `integrator`
  (persisted-проекция тиров, если выбрана).
- **Кто ответил + трассировка:** reviewer surface-ил из границы `RiskValidator`
  «не ходит в биржу» vs данными тиров/precheck; сверка — `RiskValidator.md:31-33`,
  `order-precheck.md:24-30` (ограничение режима + «не замена собственному
  преконтролю»), `position-tiers.md` (тиры «не используется», кандидат RISK-Q2),
  `price-limit.md`, `coverage-manifest.md`.
- **Ответ (предложение):** на `GAPS_CLOSE_1` записать: собственный преконтроль —
  основной; `order-precheck` out of scope для нашего режима (door-open при смене
  режима); per-tier потолок плеча — persisted-проекция тиров на инструмент (см.
  крен Э1-ii); `price-limit` (динамический, требует live) — отдельный кандидат, в
  валидатор шага 5 не входит, если не вводится явная live-точка. Частично
  закрывает вход RISK-Q2.
- **Варианты + крен:** потолок плеча/лимит — (а) persisted-проекция
  `position-tiers`; (б) единое `externalMaxLeverage`. **Крен — (а)** (корректный
  per-notional потолок). `price-limit` — (i) вне шага 5 / (ii) live-точка
  преконтроля. **Крен — (i)** (не вводить live в валидатор без явной потребности).
- **Целевой док:** `risk-validator-scope` / `RiskValidator` (явная запись «own
  precontrol; live-эндпоинты вне валидатора»); пометки в контрактах
  `order-precheck`/`position-tiers`/`price-limit`.
- **Ярлык исхода:** `выводимо-Предложение` (паттерн выводим из границ валидатора)
  + `варианты-с-креном` (источник потолка).
- **Ярлык дефицита:** —.
- **Флаг действия CC:** `предложил`.

### Э5 (N6, N7). Гигиена: атрибуция направления + битая кросс-ссылка

- **Вопрос:** снять doc↔doc неточность в `RiskBlockResolver` (направление/
  reduce-only intent атрибутированы `calculatedAction`, поля нет) и битую
  кросс-ссылку `CalculatedStrategyAction → RiskCheckResult`.
- **Ожидаемый владелец:** `knowledge-curator` (реконсиляция формулировок/ссылок).
- **Кто ответил + трассировка:** reviewer; N6 — сверка `RiskBlockResolver.md:35-36`
  vs `CalculatedStrategyAction.md:23-28`/`CalculatedSize.md`/`Deal.md`; N7 — сверка
  `CalculatedStrategyAction.md:36-38` vs `RiskCheckResult.md` (клаузы нет;
  владелец — `RiskValidator.md:21-26`).
- **Ответ (предложение):** N6 — переформулировать контракт `RiskBlockResolver`:
  направление берётся из `DealContext.deal.direction` (+ `sourceAction` для
  order-action), reduce-only intent — из `CalculatedSize.sizeMode`/`closeFraction`.
  N7 — перенаправить ссылку на `RiskValidator.md`.
- **Варианты + крен:** без вариантов (правки-cleanup).
- **Целевой док:** `RiskBlockResolver.md`; `CalculatedStrategyAction.md`.
- **Ярлык исхода:** `выводимо-Предложение`.
- **Ярлык дефицита:** —.
- **Флаг действия CC:** `предложил`.

## Торговый фокус (`trading-review`)

Адверсариальный проход по торговой корректности риск-преконтроля (грунт —
`.claude/library/trading/distilled/risk-and-sizing.md`).

- **TR1 (= N4) — НОВАЯ БЛОКИРУЮЩАЯ торговая находка.** Нет кода-проверки
  экспозиции/плеча поверх биржевого максимума; `position exposure` считается, но
  блокировать нечем; `trading-constraints` подтверждает «наших потолков нет».
  Корпус **единодушен**: позиционный/плечевой кэп обязателен для автоматики и —
  **единственный форвардный** риск-контроль [Carver AFTS т.4 с.651-655; Kaufman
  гл.24 с.2188,2192-2193; Carver ST гл.9 с.180-181; Vince гл.2 с.30-31]. **Жёсткий
  гейт «модель не выражает нужное торговое правило»** — в текущем `RiskCheckCode`
  нет кода guard'а. Значение порога — хвост (бэктест/пользователь), но
  **существование проверки — структурное требование**. Разбор — Э3.

- **TR2 — liquidation guard рискует быть наивным (не гейтит, флаг на `CODE`).**
  `STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION` + инвариант «ликвидация за стопом» есть и
  корпусно-состоятельны как статическая дистанция SL↔liqPx. Но: (1) стоп не
  гарантирует цену — гэп проскакивает за liqPx [Vince введ. с.6; Harris гл.4
  с.78]; дистанцию guard'а корпус требует мерить от worst-case движения/
  волатильности (ATR), а не произвольного буфера; (2) liqPx — функция плеча: тот
  же SL «слишком близко» при росте плеча; guard должен перевыводить liqPx из плеча
  **действия**, а не только из текущего `Position.liqPx`. Код-проверка выражает
  это; пробел — **семантика порога** (gap-aware / leverage-coupled), деталь
  `CODE`/калибровки. Флаг, чтобы guard не реализовали как наивное сравнение цен.

- **TR4 (= N3 / RISK-Q1) — risk-per-trade как жёсткое ограничение размера.**
  Корпус: percent-risk — основная модель сайзинга со стопом [Tharp гл.12
  с.292-296]; переплечо/недокапитализация — главные причины провала [Tharp гл.6
  с.145-146]. Код `RISK_PER_TRADE_EXCEEDED` + метрика risk% + поле
  `riskPerTradePercent` уже существуют → модель **выражает** ограничение; открыт
  лишь дом `RiskSettings` (RISK-Q1). **Не блокер** существования проверки;
  закрывается с Э2.

- **TR5 — база сайзинга (total vs adjusted vs available equity) не зафиксирована
  (не гейтит, явное решение шага 5).** `BalanceContainer` отдаёт
  `externalTotalEquity`/`externalAdjustedEquity` («может быть предпочтительной
  базой»)/`externalAvailableEquity`; `RiskValidator` считает «risk% от депозита»,
  но **какой** equity — база, не фиксирует. Выбор материален: total (раздут
  unrealized PnL) vs available (после резерва) меняет и risk%, и affordability
  [Carver ST гл.9 с.187-189; Vince гл.1 с.9; Kaufman гл.23 с.2028 — резервы].
  Модель выражает выбор; рекомендация — сделать его **явным решением** на
  `GAPS_CLOSE_1` (крен — `externalAdjustedEquity` как база risk-policy, по пометке
  модели), не случайностью `CODE`. Свежесть баланса покрыта
  (`BALANCE_NOT_FRESH`/`BALANCE_INVALID`).

- **TR6 — reduce-only защита выходов: полно, не пробел.** `reduce-only-invariant`
  (intent→echo, mismatch → `EXCHANGE_INVARIANT_VIOLATION` → HOLD) +
  `no-partial-close` (`CLOSE_FULL`-only позиции, partial через reduce-only
  order/algo, коды `PARTIAL_EXIT_*`) + `risk-validator-scope` (reduce-only exit
  валидатор не зовёт) согласованы и корпусно-состоятельны. Защита от случайного
  увеличения/разворота закрыта.

- **Вывод торгового фокуса:** **одна новая блокирующая находка — TR1** (guard
  экспозиции/плеча; жёсткий гейт `CODE`). Остальное — форвард/не-гейтит при
  корпусно-состоятельной структуре; TR5 — явное решение на `GAPS_CLOSE_1`.

## Сводка

- **Пробелов:** 7 (N1-N7). Эскалаций: 5 (Э1-Э5). Торговых находок: 6 (TR1-TR6),
  блокирующая — 1 (TR1 = N4 = Э3).
- **Агрегация по ярлыкам исхода:** `варианты-с-креном` — 4 (Э1, Э2, Э3, Э4-часть);
  `выводимо-Предложение` — 2 (Э4-часть, Э5); `принято-в-работу` — 2 (Э1
  домаппинг, Э3 код проверки).
- **Агрегация по ярлыкам дефицита:** `работа` — 3 (Э1 форма, Э2 представление,
  Э3 форма guard); `подтверждение` — 1 (Э3 численные пороги — бэктест); без
  дефицита — 2 (Э4, Э5).
- **Флаги действия CC:** `предложил` — 5/5. Финализаций нет.
- **Гейт `CODE`:** **не чисто.** Гейтят `CODE`: **N1+N2/INSTR-Q1+Q2** (источник
  ограничений инструмента не материализован + трёхсторонняя несогласованность),
  **N3/RISK-Q1** (структура `RiskSettings`), **N4/RISK-Q2/TR1** (нет
  кода/правила guard'а экспозиции — жёсткий торговый гейт). N5 — паттерн
  потребления (чистота). N6/N7 — гигиена.
- **Торговый гейт:** **блокер есть — TR1** (модель не выражает обязательный
  worst-case guard экспозиции/плеча).

## Рекомендация

Нужен **`GAPS_CLOSE_1`**:

1. **Э3 / RISK-Q2 / TR1** — ввести код(ы) guard'а экспозиции/плеча в
   `RiskCheckCode` + правило формы guard (крен: минимум из границ); пороги —
   провизорные «value: бэктест». *(жёсткий гейт `CODE` + торговый)*
2. **Э1 / INSTR-Q1+Q2 / N1+N2** — материализовать `InstrumentExternalRules`
   (persistence + домаппинг max-size/`externalMaxLeverage`); снять трёхстороннюю
   несогласованность; решить источник потолка плеча (крен: per-tier
   `position-tiers`). *(гейт `CODE`)*
3. **Э2 / RISK-Q1 / N3** — зафиксировать структуру `RiskSettings` (крен: поля
   `StrategyDetail` + policy). *(гейт `CODE`)*
4. **Э4 / N5** — записать «собственный преконтроль; live-эндпоинты вне
   валидатора; `order-precheck` вне режима»; источник тиров.
5. **TR5** — явно выбрать equity-базу сайзинга (крен: `adjustedEquity`).
6. **Э5 / N6+N7** — снять атрибуцию направления в `RiskBlockResolver` и битую
   кросс-ссылку.

После `GAPS_CLOSE_1` — `DOCS_CHECK_2` (подтверждающий прогон). Чистый `DOCS_CHECK`
— обязательное условие гейта `CODE` (`roadmap-step-execution.md` §«Гейт `CODE` —
чистый `DOCS_CHECK`»).
