# DOCS_CHECK_3 — шаг 5 фазы 1 (риск-преконтроль) — пост-хок концепт-гейт §6a

## На какой вопрос отвечает этот файл

Прошёл ли пост-хок концепт-гейт §6a (concept-review по пост-sync докам) чисто,
и какие концепт-пробелы он нашёл.

## Контекст прогона

- **Шаг:** 5 фазы 1 — риск-преконтроль. **Под-шаг:** §6a (пост-хок концепт-гейт),
  ведётся как `DOCS_CHECK_3` (сквозная нумерация итераций шага).
- **Почему:** на CODE въехали концепт-инкременты, миновавшие концепт-гейт
  (`roadmap-step-execution.md` §6a): каталоги настроек в `CalculationContext` +
  резолверы по ключу; внутренний `CalculationException`. После
  `SYNC_DOCS_FROM_CODE` (17 доков правлены) — concept-review по приведённым докам.
- **Независимость:** проход выполнен независимым `concept-review`-агентом (не
  автор кода/доков). Граница — только доки; единственное исключение —
  явно запрошенный code↔doc-чек по комиссиям (C2).

## Охват и стадия

Прошёл все стадии (стадия 2). Стадия 0 чиста — гейтящих открытых вопросов /
скоупа нет. Increment 1 (каталоги `CalculationContext` + `findIndicatorValueByKey`/
`findMarketStructureByKey`, источник — `StrategyDataService.findActiveByInstrumentIdWithSettings`)
— **когерентен**, doc↔doc-расхождений нет. Материализация
`InstrumentExternalRules` (трёхсторонняя сверка N2) — когерентна, SYNC новых
несогласованностей не внёс.

## Пробелы — 2 несогласованности doc↔doc, обе гейтят DONE

### C1 — механизм controlled-ошибки расчёта описан двумя способами (increment 2)

- **Тип:** несогласованность doc↔doc. **Гейтит DONE.**
- **Суть:** калькулятор-доки описывают возврат controlled `CalculationError`
  (`PriceCalculator.md:114`, `SizeCalculator.md:81-82`,
  `CalculationContextFactory.md:39`, `StrategyActionCalculator.md:44`,
  `StrategyActionCalculationResult.md`), а `CalculationContextFactory.md:42-43` —
  **бросок** `temporary CalculationException` с кодом `NO_MARKET_PRICE`. Тип
  `CalculationException` нигде не определён (один упоминание во всех доках);
  граница перехвата (orchestrator catches → ERROR) не описана; код
  `NO_MARKET_PRICE` не зарегистрирован ни в каком каталоге (`CalculationError.code`
  — freeform `String`).
- **Владелец:** `solution-designer` (если развилка throw/return) /
  `knowledge-curator` (если выравнивание формулировок).
- **Разрешение (крен, docs←code от as-built):** внешний контракт калькулятор-слоя
  (`StrategyActionCalculator`) — **возврат** `StrategyActionCalculationResult`
  (`ERROR` несёт `CalculationError`); `CalculationException` — **внутренний**
  механизм: суб-калькуляторы (`Price`/`Size`/`ContextFactory`) бросают,
  `StrategyActionCalculator` ловит и превращает в `ERROR`-результат. Привести
  доки к этому: пометить `CalculationException` внутренним механизмом (структура
  + отношение к `CalculationError`), описать catch-границу в
  `StrategyActionCalculator.md` / `strategy-action-calculation.md`, выровнять
  формулировки суб-калькуляторов (внутри — бросок, наружу слоя — возврат
  Result), зафиксировать `NO_MARKET_PRICE` как `CalculationError.code`. Это
  docs←code-выравнивание под утверждённый as-built (не новая конструкция) →
  `GAPS_CLOSE_3` (curator). Развилки уровня пользователя нет.

### C2 — комиссии в формуле риска на сделку (явно запрошенный чек)

- **Тип:** несогласованность doc↔doc (decision ↔ компонент-доки). **Гейтит DONE.**
- **Суть:** `per-trade-risk-policy.md:47-49` числит **комиссии (вход+выход)**
  входом риск-расчёта и формулой `… × ctVal + commissions`; `RiskValidator.md:30`
  и `SizeCalculator.md:57` — «**commissions в фазе 1 опущены**».
- **Decision сам разделяет комиссии и проскок:** комиссии — полноправный вход
  (§Определение и база); проскок — отдельный раздел §«Без поправки на проскок
  (фаза 1)», отложен к бэктесту. Т.е. decision откладывает **только проскок**,
  комиссии держит входом. Компонент-доки же опустили **комиссии** — то, что
  decision не откладывал. Прямое расхождение, не покрытое отложенным проскоком.
- **Владелец:** `trading-specialist` (торговая policy: что входит в денежный риск
  на сделку) + `knowledge-curator` (приведение). **Развилка вынесена
  пользователю** (policy-хвост) — см. ниже.

## Сводка

- Пробелов: 2, оба — несогласованность doc↔doc, оба гейтят DONE.
- Name-level: 0. Блокирующих открытых вопросов: 0 (RISK-Q1/Q2, INSTR-Q1
  закрыты; остаток INSTR-Q2 и STRAT-Q4 — non-gating форвард).
- **Вердикт: НЕ чисто — нужен `GAPS_CLOSE_3`**, затем `DOCS_CHECK_4` для
  верификации, только потом `DONE`.
- **C1** — docs←code-выравнивание (curator), развилки пользователя нет.
- **C2** — policy-развилка пользователя (комиссии входят в фаза-1 расчёт vs
  откладываются), разбирается отдельно.

## Закрытие — `GAPS_CLOSE_3` + `DOCS_CHECK_4` (2026-06-20) — чисто

### C1 — закрыт (docs←code-выравнивание)

Заведена §«Механизм сигнализации» в `CalculationError.md`: контролируемую
ошибку расчёта суб-калькуляторы (`CalculationContextFactory`/`PriceCalculator`/
`SizeCalculator`) сигнализируют броском внутреннего `CalculationException`
(несёт `CalculationError`); `StrategyActionCalculator` перехватывает →
`StrategyActionCalculationResult(ERROR)`; внешний контракт слоя — возврат
Result. `NO_MARKET_PRICE` — пример temporary-кода. Формулировки выровнены в
`StrategyActionCalculator.md`, `CalculationContextFactory.md`,
`PriceCalculator.md`, `SizeCalculator.md`, `strategy-action-calculation.md`.

### C2 — комиссии отнесены к шагу 7 (решение пользователя)

`per-trade-risk-policy.md` — комиссии остаются концептуальным входом, но
код-уровневый учёт отложен: новая §«Учёт комиссий — отложен к шагу 7 (фаза 1)»
(привязка к появлению fee-модели / `trade-fee`, не к неизвестности величины;
финальная policy — на шаге 7). `RiskValidator.md`/`SizeCalculator.md` помечают
«опущены в фазе 1» со ссылкой на отсрочку. Форвард-пункт — `backlog.md` §Шаг 7.

### `DOCS_CHECK_4` (независимый verify) — CLEAN

C1 и C2 — CLOSED-CLEAN (когерентная кросс-док история; ссылки на новые §
резолвятся). Новых doc↔doc-несогласованностей правки `GAPS_CLOSE_3` не внесли.
Битых кросс-ссылок / устаревших «На какой вопрос отвечает» нет.

### Итог

§6a пройден чисто. **Все условия `DONE` (§7) выполнены с зафиксированным исходом:**
CODE-фокусы (`conventions`/`performance`/`disaster`), SYNC `divergence`, §6a
концепт-гейт. Перевод шага 5 в `DONE` — за пользователем. Форварды (non-gating):
комиссии → шаг 7; бесстоповый risk-creating вход → шаг 6 (`backlog.md`); остаток
INSTR-Q2 (тайминг set-leverage) → шаг 6; STRAT-Q4 (якорь allocation%).
