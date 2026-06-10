# ER (efficiency ratio) — индикатор каталога, а не внутренняя мера

## На какой вопрос отвечает этот файл

Почему efficiency ratio (ER) включён в каталог индикаторов как
авторски-адресуемый операнд, а ER-условие выражается `INDICATOR_COMPARE`
без выделенного `ruleType`.

## Контекст

ER — несущий измеритель шума/тренда корпуса: `ER = |чистый ход за окно| /
Σ|побарных ходов|`, ER→1 тренд, ER→0 шум/боковик (Kaufman, база KAMA).
В прежнем дизайне фаза определялась скоринговым алгоритмом, и ER жил
**внутренней мерой аналитика-скоринга** (fork A находки **Н3**, прежний
крен — «ER внутри аналитика»; каталог `ATR/EMA/RSI/MACD/STOCHASTIC/
BOLLINGER_BANDS/OBV` ER не содержал).

Редизайн условной фазы
(`docs/decisions/market-phase-conditional-classification.md`) распустил
скоринг: `MarketPhaseClassifier` стал stateless first-match поверх
`StrategyConditionEvaluator`, по свечам ничего не считает — читает готовые
значения. Носитель прежнего крена («внутри аналитика фазы») исчез;
одновременно фаза стала **адресовать ER авторским условием**. Fork A
переоценён в новом контексте
(`.claude/work/progress/phase-1-step-3-fork-a-rerun-er-location.md`),
под-развилка A-bis — судьба выделенного `EFFICIENCY_BELOW_THRESHOLD`
(`.claude/work/progress/phase-1-step-3-fork-a-bis-rerun-efficiency-ruletype.md`).

## Принятое решение

1. **ER — тип каталога индикаторов.** `IndicatorValue.Type` +=
   `EFFICIENCY_RATIO`; наследники `EfficiencyRatioParams(period)` (ER —
   оконный, `warmup = period`) и `EfficiencyRatioValue(efficiencyRatio)`
   (скаляр `BigDecimal` ∈ [0,1], нормирован по определению). ER считается
   `IndicatorJob`'ом по закрытым свечам, шарится по идентичности
   конфигурации, свежесть — под запрашивающую настройку (как прочие
   индикаторы). Читается `StrategyConditionEvaluator`'ом как готовое
   значение — evaluator/classifier индикаторы не считают.

2. **ER-условие — через `INDICATOR_COMPARE`, без выделенного `ruleType`.**
   «ER ниже/выше порога» = ER-операнд (по `indicatorKey`) `LT`/`GT`
   `CONSTANT` — ровно одно `INDICATOR_COMPARE`. Выделенный
   `EFFICIENCY_BELOW_THRESHOLD` **удалён** (A-bis): по критерию
   `docs/rules/condition-ruletype-granularity.md` это чистый алиас одного
   сравнения (нет составной/темпоральной семантики; нормализация — в
   определении ER), а он покрывал лишь сторону «below», тогда как ER нужен
   в обе стороны (низкий → шум/range, высокий → тренд). Генерик
   единообразен и DRY.

3. **Контекст-сплит ролей ER, единый источник.** `MarketStructureResolver`
   пережил редизайн как **вычисляющий** компонент; его внутреннее
   использование ER (тест доминирования чистого хода над шумом /
   консервативный `UNKNOWN`) **не пересчитывается** — резолвер потребляет
   **тот же каталожный ER** готовым скаляром по «мягкому» ключу
   `StrategyMarketStructureSetting.efficiencyRatioKey` (того же контейнера),
   который извлекает `MarketStructureJob`. Один источник ER, без
   дубль-вычисления. **Fallback на прокси — нетто-ход окна / суммарный
   побарный ход** (мини-ER по ценам закрытия), считается резолвером **только
   когда `efficiencyRatioKey` не объявлен** (не EMA-наклон — уточнено на
   `CODE`/sync). **Объявлен, но не готов / устарел → консервативный
   `UNKNOWN`** (на стороне job, не proxy). Проводка ER/ATR-входов и пороги
   структуры (D2/D3) досведены батчем `CODE` —
   `docs/decisions/derived-market-data-code-increments.md` (где `MarketStructureParams`
   получили `trendEfficiencyThreshold` / `levelToleranceAtrMultiplier`).

## Отвергнутые альтернативы

- **ER внутри аналитика (прежний крен fork A).** Носитель распущен —
  аналитика-скоринга фазы больше нет; классификатор/evaluator по свечам
  не считают. Для роли фазы у варианта нет дома; узкий остаток
  (внутренний ER структуры) свёрнут в каталожный ER (п.3), без второго
  вычисления.
- **ER вне каталога, но условие — выделенный `EFFICIENCY_BELOW_THRESHOLD`,
  считающий ER на лету.** Противоречит границе «evaluator не считает
  индикаторы», теряет шаринг/warmup/свежесть; и сам тип — чистый алиас
  (см. критерий грамматики).
- **Оставить `EFFICIENCY_BELOW_THRESHOLD` грунтованным sugar** (по
  аналогии `RANGE_BREAKOUT_CONFIRMED`). Аналогия неприменима:
  `RANGE_BREAKOUT_CONFIRMED` инкапсулирует составное событие структуры
  (буфер + N-баровое подтверждение, читается предвычисленным), а ER-тест —
  одно сравнение. Чистый алиас → DRY-свёртка.

## Следствия

- `docs/models/domain/other/IndicatorValue.md` — `Type` +=
  `EFFICIENCY_RATIO`; наследник `EfficiencyRatioValue(efficiencyRatio)`;
  заметка о роли ER и едином источнике.
- `docs/models/domain/aggregate/Strategy.md` — §IndicatorParams +
  `EfficiencyRatioParams(period)`; §StrategyMarketPhaseRule — ER через
  `INDICATOR_COMPARE`, `EFFICIENCY_BELOW_THRESHOLD` убран из whitelist;
  §StrategyConditionRuleType — `EFFICIENCY_BELOW_THRESHOLD` убран из
  перечня.
- `docs/decisions/strategy-condition-authoring-contract.md` —
  `EFFICIENCY_BELOW_THRESHOLD` убран из открытых per-`ruleType`; ссылка на
  критерий грамматики.
- `docs/decisions/market-phase-conditional-classification.md` — ER-тест
  через `INDICATOR_COMPARE` в контексте фазы; перекрёстная ссылка.
- Новый принцип грамматики — `docs/rules/condition-ruletype-granularity.md`.
- Закрывает часть **fork A** («где живёт ER») находки **Н3**; миграции
  данных нет (валидатор/API — артефакты `CODE`, персистентных стратегий
  нет).

## Связи

- Перепрогон fork A / A-bis (проработка-предложение) —
  `.claude/work/progress/phase-1-step-3-fork-a-rerun-er-location.md`,
  `.claude/work/progress/phase-1-step-3-fork-a-bis-rerun-efficiency-ruletype.md`.
- Условная фаза (распустила носитель прежнего крена) —
  `docs/decisions/market-phase-conditional-classification.md`.
- Критерий именованный-`ruleType`-vs-генерик —
  `docs/rules/condition-ruletype-granularity.md`.
- Модель индикатора — `docs/models/domain/other/IndicatorValue.md`,
  `docs/models/domain/aggregate/Strategy.md` §IndicatorParams.
- Структура рынка (потребитель ER как опционального входа) —
  `docs/models/domain/other/MarketStructure.md`.
- Досведение проводки ER/ATR-входов (fork-A) и пороги структуры (D2/D3) —
  `docs/decisions/derived-market-data-code-increments.md`.
