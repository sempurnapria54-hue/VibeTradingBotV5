# SYNC_DOCS_FROM_CODE — шаг 5 фазы 1 (риск-преконтроль)

## На какой вопрос отвечает этот файл

Как доки приведены к утверждённому коду шага 5 (docs←code) и что
остаётся до `DONE`.

## Контекст

- **Шаг:** 5 фазы 1 — риск-преконтроль. **Под-шаг:** `SYNC_DOCS_FROM_CODE`
  (одиночный проход docs←code после аппрува `CODE`).
- **Направление:** код утверждён и считается истиной; доки приводятся к нему.
- **Метод:** независимый фокус `divergence` (детект, не автор кода) → список
  add/change/remove → реконсиляция `knowledge-curator`
  (`.claude/skills/reconcile-knowledge.md`).

## Детект (фокус `divergence`)

~44 расхождения по 16 докам (≈20 add, ≈22 change, ≈3 remove). Высший приоритет —
фактические противоречия, не просто добавления.

## Реконсиляция (docs←code) — затронуто 17 доков + 1 ренейм

- **`docs/models/integrations/okx/InstrumentOkxResponse.md`** — добавлены реально
  присутствующие в DTO поля (`ctType`, `ctValCcy`, `maxLmtSz`, `maxMktSz`,
  `maxTriggerSz`, `maxStopSz`); снято ложное утверждение «не входят»; один DTO
  питает и snapshot идентичности (шаг 1), и rules-snapshot (шаг 5).
- **`docs/components/models/MarketPriceData.md`**, **`docs/models/mapping/MarketPriceData.md`**
  — снят forward-блок «Java-класса ещё нет / маппер снят»; код существует
  (RVO + snapshot + маппер + сервис); отмечен `midPrice()` и две стадии маппера.
- **`docs/components/CalculationContextFactory.md`** —
  `InstrumentExternalRulesService`→`InstrumentExternalRulesDataService`; убран
  `MarketPhaseService` (не вызывается, `marketPhase` = null в фазе 1); добавлен
  `StrategyDataService.findActiveByInstrumentIdWithSettings`; отмечен
  `CalculationException(NO_MARKET_PRICE)`.
- **`docs/components/models/CalculationContext.md`** — добавлены `indicatorSettings`/
  `marketStructureSettings` + резолверы `findIndicatorValueByKey`/
  `findMarketStructureByKey`; `marketPhase` помечен «не заполняется в фазе 1».
- **`docs/components/RiskValidator.md`** — входы переписаны под фактическую
  сигнатуру `validate(CalculatedStrategyAction, DealContext)` (rules читает сам
  через DataService); комиссии помечены опущенными (фаза 1); добавлен список
  фактических проверок и их `RiskCheckCode`.
- **`docs/components/models/RiskCheckResult.md`** — коды размечены
  «эмитится в фазе 1» vs «определён, не эмитится (forward/handler/anomaly)»;
  отмечено, что валидатор строит только BLOCKED-результаты.
- **`docs/components/models/CalculatedPrice.md`** — `sendPriceToExchange`
  `boolean`→`Boolean`; размечен реально эмитимый субсет `StrategyPricePurpose`;
  добавлены поля под-объектов `Resolved*`.
- **`docs/components/models/CalculationError.md`** — `retryable` `boolean`→`Boolean`.
- **`docs/components/PriceCalculator.md`** — расширенный словарь
  `StrategyPriceSource` помечен forward; резолв описан через фактические драйверы
  (`StrategyPriceBaseType` вкл. SUPPORT/RESISTANCE, малый субсет
  `StrategyPriceSource`, `StopLossCalculationType`, `TrailingSettings`);
  MARK/INDEX → проксируются на last (фаза 1); ветки conditionType.
- **`docs/components/SizeCalculator.md`** — комиссии опущены (фаза 1); описаны
  entry-сайзинг (notional от `availableEquity × allocation%`, риск-кэп при
  наличии стопа) и reduce/full-close (`externalSize × clamp[0..1]`, lot-round,
  min-floor); база — `externalAvailableEquity` (якорь % — STRAT-Q4).
- **`docs/models/domain/other/InstrumentExternalRules.md`** — снято фантомное
  поле `id` (только `instrumentId`); добавлены rich-методы (`isLive`,
  `hasSizingSpecs`, числовые аксессоры); отмечено отсутствие
  `maxTriggerSize()`/`maxStopSize()` аксессоров.
- **`docs/models/mapping/InstrumentExternalRules.md`** — уточнена стадия резолва
  enum'ов: snapshot — сырые строки, доменные проекции резолвятся при
  материализации (`snapshotToDomain`).
- **`docs/components/InstrumentExternalRulesSyncJob.md`** — добавлены
  операционные факты (статусы `{SYNC, CANDLES_LOADING, ACTIVE}`, CRON из конфига
  по умолчанию ежечасно, `enabled`, `JobExecutionGuard`, async-фасад,
  per-instrument try/catch + null-snapshot warn+skip).
- **`docs/components/ServiceCommandFactory.md`** — flow дополнен
  `CLOSE_POSITION`/`REFRESH_POSITION`/algo, CANCEL/REPLACE → нет команды,
  Condition пока type-only.
- **`docs/components/models/CalculatedStrategyAction.md`** — снят устаревший
  «Статус кода (шаг 4) / заглушки»; шаг 5 материализован.

### Каскад референтов

- `docs/components/InstrumentExternalRulesService.md` описывал компонент, которого
  в коде нет (роль выполняет persistence-`InstrumentExternalRulesDataService`).
  `git mv` → `docs/components/InstrumentExternalRulesDataService.md`, содержимое
  переписано под фактический компонент. Doc-ссылок на старое имя файла нет.
  Прозаические упоминания старого имени в `history/`/`notes/` (иммутабельный
  архив), в `backlog.md` §3 (закрытый пункт) и в дат-бюллетенях `phase-1.md`/
  CODE-заметке — записи момента, не правятся.

## Исход

- **Аппрув `SYNC_DOCS_FROM_CODE`:** фокус `divergence` прогнан с зафиксированным
  исходом, расхождения реконсилированы. Доки соответствуют коду.

## Остаётся до `DONE`

- **Пост-хок концепт-гейт §6a** (`roadmap-step-execution.md`): на CODE въехали
  концепт-инкременты (каталоги настроек в `CalculationContext` для резолва по
  ключу; внутренний `CalculationException`), миновавшие концепт-гейт. После sync
  прогнать `concept-review` по приведённым докам; найдены пробелы — узкий
  `GAPS_CLOSE` и перепрогон; чисто — шаг в `DONE`. В этом прогоне не запускался.
- Форвард (горизонт шаг 6): бесстоповый risk-creating вход не размещаем
  (`backlog.md` §Форвард-материал → Шаг 6).
