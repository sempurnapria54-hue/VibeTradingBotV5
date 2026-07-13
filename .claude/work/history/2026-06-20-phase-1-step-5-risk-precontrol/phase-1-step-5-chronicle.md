# Хроника шага 5 Фазы 1 — риск-преконтроль

## На какой вопрос отвечает этот файл

Какова хроника прохождения шага 5 Фазы 1 по под-шагам
(перенесена из phase-1.md при расщеплении 2026-07-06).

## Хроника

- **Шаг 5 → `DOCS_CHECK_1` (2026-06-20):** стартован шаг риск-преконтроля
  (`TOOLING` пройден без новых артефактов — фокусы `concept`/`trading` активны).
  Первый прогон сквозной проверки: risk-layer **в основном уже материализован**
  миграцией из архива (процессы `risk-evaluation`/`strategy-action-calculation`,
  `RiskValidator`/`RiskBlockResolver`/калькуляторы, RVO, правила). Механика чиста
  (стадии 0-1); пробелы сосредоточены на **входах** валидатора (стадия 2). **Не
  чисто** — гейт `CODE` закрыт. Гейтят `CODE`: **N1+N2/INSTR-Q1+Q2**
  (`InstrumentExternalRules` не материализована, а шаг 5 — её потребитель; +
  трёхсторонняя несогласованность по max-size/`externalMaxLeverage` полям —
  объявлены/использованы, но не маппятся), **N3/RISK-Q1** (структура `RiskSettings`
  только name-level), **N4/RISK-Q2/TR1** (нет `RiskCheckCode` и правила worst-case
  guard'а экспозиции/плеча — `position exposure` считается, блокировать нечем;
  единственный потолок — биржевой максимум, на крипто-перпах не guard rail).
  **Торговый фокус — одна блокирующая находка TR1** (жёсткий гейт «модель не
  выражает обязательный worst-case guard», корпус единодушен). N5 — паттерн
  потребления constraint-эндпоинтов (live vs persisted; `order-precheck` вне
  нашего режима маржи). N6/N7 — гигиена (атрибуция направления в `RiskBlockResolver`,
  битая кросс-ссылка). Все четыре центральные развилки имеют штатный
  горизонт-владельца шаг 5. Нужен `GAPS_CLOSE_1`. Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-1.md`.
- **Шаг 5 → `GAPS_CLOSE_1` (2026-06-20):** пробелы `DOCS_CHECK_1` закрыты.
  **Риск-политика на сделку** проработана с пользователем и зафиксирована
  решением `docs/decisions/per-trade-risk-policy.md` (трёхуровневая модель
  риска: сделка — фаза 1, биржа — фаза 3, межбиржевой портфель — мультибиржевой
  этап; риск на сделку = убыток на стопе как % от **свободного** депозита, входы
  цена входа/стопа/размер/плечо/комиссии; плечо связано лимитом риска — отдельного
  кэпа нет; без поправки на проскок в фазе 1; строгий блок при невмещении даже на
  `minSz`; числовой лимит провизорный). Этим закрыты **RISK-Q2/N4/TR1** (worst-case
  guard экспозиции — уровень риска на биржу/портфель, отложен; в фазе 1 контроль —
  лимит риска на сделку, код `RISK_PER_TRADE_EXCEEDED` уже есть; новый
  exposure-код не вводится), **RISK-Q1/N3** (нет RVO `RiskSettings` — поля
  `StrategyDetail`; поле `CalculationContext.riskSettings` упразднено) и **TR5**
  (база — `externalAvailableEquity`). **InstrumentExternalRules материализована**
  (N1/N2/INSTR-Q1) решением
  `docs/decisions/instrument-external-rules-materialization.md` (JSONB-навес на
  `Instrument`; домапплены per-order max sizes + `lever→externalMaxLeverage` +
  `state→externalState` — снята трёхсторонняя несогласованность; источник потолка
  плеча — инструмент-уровневый, per-tier `position-tiers` — форвард). **INSTR-Q2**
  закрыт в части роли плеча/HOLD; остаток — тайминг set-leverage (форвард к шагу 6).
  **N5** — собственный преконтроль основной, `order-precheck` вне режима маржи,
  live-эндпоинты (tiers/price-limit) вне валидатора фазы 1. **N6/N7** — гигиена
  снята. Распространено по доменным/процессным/контрактным докам + закрыты
  RISK-Q1/Q2/INSTR-Q1 в `open-questions.md`. Далее — подтверждающий `DOCS_CHECK_2`.
- **Шаг 5 → ратификация (а) + `DOCS_CHECK_2` (2026-06-20):** контроль риска
  ратифицирован пользователем (вариант (а): в фазе 1 отдельного потолка
  плеча/экспозиции нет). Заведена форвард-заметка: простой жёсткий предел плеча
  на сделку рассматривался против зазора «узкий стоп → высокое плечо» и
  сознательно отложен — revisit после бэктеста/живых прогонов
  (`backlog.md` §Шаг 5; рационал — `docs/decisions/per-trade-risk-policy.md`
  §Альтернативы). Подтверждающий `DOCS_CHECK_2` (независимые ревьюер-фокусы
  concept+trading) — **чисто**: все находки `DOCS_CHECK_1` (N1-N7) CLOSED-CLEAN,
  торговый гейт чист (TR1 разрешена корпусно-состоятельно — кэп экспозиции —
  уровень риска на биржу/портфель, фаза 3), новых блокеров правки `GAPS_CLOSE_1`
  не внесли. 3 микро-рассинхрона (`BalanceContainer` база, `Strategy`
  «% от капитала», `InstrumentExternalRules` Auditable-формулировка) закрыты на
  месте. **Концепт-гейт `CODE` пройден.** Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-2.md`. Готовность к `CODE` —
  перевод за пользователем.
- **Шаг 5 → `CODE` (2026-06-20):** написан код по утверждённой концепции
  (47 файлов в working tree, staged; компилируется `clean test-compile`,
  без deprecation). Материализован: `InstrumentExternalRules` (доменная модель
  + JSONB-навес на `instruments`, миграция `V8`, маппер + JSON-конвертер,
  domain↔persistence DataService, `InstrumentExternalRulesSyncJob` + фасад +
  конфиг + домаппинг OKX-полей max-size/`lever`/`ctType`/`ctValCcy`); расчётный
  слой (`MarketPriceData`-сборка по REST ticker; `Calculated*`-RVO достроены до
  полной структуры + enum'ы; `CalculationContext(Factory)`, `PriceCalculator`,
  `SizeCalculator` с risk-bounded сайзингом, `StrategyActionCalculator`); risk-слой
  (`RiskValidator`, `RiskBlockResolver`, RVO `RiskValidationResult`/`RiskCheckResult`/
  `RiskBlockAction` + `RiskCheckCode`). **Аппрув-гейт:** прогнаны три независимых
  адверсариальных ревьюер-фокуса (`conventions`/`performance`/`disaster`; `security`
  деактивирован до шага 9) — без блокеров; clean-code находки и реальные
  safety-фиксы (ctVal=0 → controlled error, `NumberFormatException` на сырьё →
  null, гард SL/TP/trailing > 0 после округления, clamp reduce-fraction 0..1,
  устранён дубль чтения фазы) закрыты на месте. Отчёт и форвард-заметки —
  `.claude/work/progress/phase-1-step-5-code.md`. Финальный аппрув CODE и переход к
  `SYNC_DOCS_FROM_CODE` — за пользователем. **Концепт-инкременты на CODE**
  (требуют пост-хок концепт-гейта §6a): `CalculationContext` несёт каталоги
  настроек индикаторов/структуры для резолва по ключу; внутренний
  `CalculationException`; `InstrumentExternalRulesDataService` вместо
  doc-имени `InstrumentExternalRulesService`.
- **Шаг 5 → `SYNC_DOCS_FROM_CODE` (2026-06-20):** доки приведены к
  утверждённому коду (docs←code). Независимый фокус `divergence` выписал ~44
  расхождения по 16 докам; реконсилированы `knowledge-curator`. Ключевое:
  `InstrumentOkxResponse` — добавлены реально присутствующие поля
  (`ctType`/`ctValCcy`/`maxLmtSz`/`maxMktSz`/`maxTriggerSz`/`maxStopSz`), снято
  ложное «не входят»; `MarketPriceData` (модель+маппинг) — снят forward-блок
  «класса ещё нет» (код есть); `CalculationContextFactory` —
  `InstrumentExternalRulesService`→`InstrumentExternalRulesDataService`, убран
  `MarketPhaseService`, добавлен `StrategyDataService`; `RiskValidator` — входы
  (читает rules сам, сигнатура 2 арг.), фактические проверки, без комиссий
  (фаза 1); `RiskCheckResult`/`CalculatedPrice` — размечен реально эмитимый/
  используемый субсет vs forward; `PriceCalculator` — расширенный словарь
  `StrategyPriceSource` помечен forward (резолв через `baseType`/
  `StopLossCalculationType`/`TrailingSettings`); `InstrumentExternalRules` —
  снято фантомное поле `id`. Каскад: doc `InstrumentExternalRulesService.md`
  (компонента, которой в коде нет) → `git mv` в
  `InstrumentExternalRulesDataService.md` + переписан. Отчёт —
  `.claude/work/progress/phase-1-step-5-sync-docs-from-code.md`. **Остаётся до
  `DONE`:** пост-хок концепт-гейт §6a (`concept-review` по пост-sync докам для
  концепт-инкрементов CODE: каталоги настроек в `CalculationContext`,
  внутренний `CalculationException`) — в этом прогоне не запускался.
- **Шаг 5 → `DOCS_CHECK_3` (пост-хок концепт-гейт §6a, 2026-06-20):**
  независимый `concept-review` по пост-sync докам — **не чисто**, 2
  несогласованности doc↔doc, обе гейтят `DONE`. **C1** — механизм
  controlled-ошибки расчёта описан двумя способами (возврат `CalculationError`
  в калькулятор-доках vs бросок `CalculationException`/`NO_MARKET_PRICE` в
  `CalculationContextFactory.md`); `CalculationException` нигде не определён,
  catch-граница не описана, код не зарегистрирован. Разрешение —
  docs←code-выравнивание под as-built (слой возвращает Result; суб-калькуляторы
  бросают внутри, orchestrator ловит), без развилки пользователя. **C2**
  (явно запрошенный code↔doc-чек) — комиссии: `per-trade-risk-policy.md` числит
  их входом риск-расчёта, `RiskValidator.md`/`SizeCalculator.md` — «опущены
  (фаза 1)»; decision откладывает только проскок, не комиссии → прямое
  расхождение. C2 — policy-развилка пользователя (`trading-specialist`-хвост).
  Increment 1 (каталоги `CalculationContext`) — когерентен. Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-3.md`. Нужен `GAPS_CLOSE_3`
  (C1 curator-выравнивание + C2 по решению пользователя), затем `DOCS_CHECK_4`.
- **Шаг 5 → `GAPS_CLOSE_3` + `DOCS_CHECK_4` (2026-06-20) — чисто.** **C1**
  закрыт docs←code-выравниванием механизма controlled-ошибки (заведена §«Механизм
  сигнализации» в `CalculationError.md`: суб-калькуляторы бросают
  `CalculationException`, `StrategyActionCalculator` ловит → `CalculationError` в
  `ERROR`-результате; `NO_MARKET_PRICE` как пример кода; формулировки
  `PriceCalculator`/`SizeCalculator`/`CalculationContextFactory`/
  `StrategyActionCalculator`/`strategy-action-calculation` выровнены).
  **C2** — по решению пользователя комиссии отнесены к **шагу 7**: decision держит
  их концептуальным входом, код-учёт отложен (§«Учёт комиссий — отложен к шагу 7»
  в `per-trade-risk-policy.md`); `RiskValidator`/`SizeCalculator` ссылаются на
  отсрочку; форвард-пункт в `backlog.md` §Шаг 7. Подтверждающий `DOCS_CHECK_4`
  (независимый) — **чисто** (C1/C2 CLOSED-CLEAN, новых doc↔doc-несогласованностей
  нет). **Все гейты `DONE` (CODE-фокусы / SYNC `divergence` / §6a концепт) пройдены
  с зафиксированным исходом — перевод в `DONE` за пользователем.** Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-3.md` §Закрытие.
- **Шаг 5 → `DONE` (2026-06-20).** Все условия §7 выполнены с зафиксированным
  исходом: CODE (фокусы `conventions`/`performance`/`disaster`, находки закрыты),
  `SYNC_DOCS_FROM_CODE` (`divergence` прогнан, реконсилировано), пост-хок
  концепт-гейт §6a (`DOCS_CHECK_3 → GAPS_CLOSE_3 → DOCS_CHECK_4` — чисто).
  Ролляп фазы — без изменений (`IN_PROGRESS`: шаги 1-5 `DONE`, 6-11 `HOLD`).
  Открытый хвост — **non-gating форвард**: комиссии в риск-расчёте → шаг 7;
  бесстоповый risk-creating вход → шаг 6; остаток INSTR-Q2 (тайминг
  set-leverage) → шаг 6; STRAT-Q4 (якорь allocation %); провизорный численный
  лимит риска (бэктест/пользователь).
