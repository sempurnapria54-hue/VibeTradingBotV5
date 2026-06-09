# Определение `MarketPhase.Type` — авторскими условиями, не скорингом

## На какой вопрос отвечает этот файл

Почему `MarketPhase.Type` определяется авторскими условиями (по образцу
`StrategyCondition`), а не скоринговым алгоритмом, и какие следствия у
этого перехода.

## Контекст

Прежняя концепция определяла фазу скоринговым алгоритмом: `MarketPhaseJob`
по `MarketPhaseParams` (`algorithmType` `STRUCTURE_ONLY`/`INDICATORS_ONLY`/
`STRUCTURE_AND_INDICATORS`, пороги `minTrendScore`/`minRangeScore`,
`confirmationBars`) считал двойной `trendScore`/`rangeScore`,
классифицировал по порогам и сохранял `confidenceScore` как выигравший
score. Семантика самого скоринга не была задана (`DOCS_CHECK`, находка
**Н3** «фаза/скоринг» — name-level, гейт `CODE`).

На проработке Н3 модель определения фазы переосмыслена: вместо
системного скоринга — **авторские правила**, параллельно
`StrategyCondition`. Автор стратегии описывает условия определения фазы
(операнды — индикаторы каталога и `MarketStructure`); по определённой
фазе `EntryScannerJob` выбирает `StrategyDetail`. Проработка с вариантами
и кренами по пяти подвопросам отвалидирована пользователем
(`.claude/work/progress/phase-1-step-3-market-phase-conditional-redesign.md`).

## Принятое решение

`MarketPhase.Type` определяется **авторскими условиями**:

1. **Носитель правил.** `StrategyMarketPhaseSetting.phaseRules:
   List<StrategyMarketPhaseRule>` — упорядоченный **first-match-список**
   клауз `{ level, type, condition: StrategyCondition }`. Проверяются по
   `level` ASC; первая клауза с истинным `condition` задаёт `Type`; ни
   одна → `UNKNOWN` (неявный консервативный дефолт). Операндный пул —
   существующие `indicatorSettings` / `marketStructureSettings` той же
   настройки (ссылка по `key`). Условия — в **контексте классификации
   фазы**: операнды `INDICATOR`/`MARKET_STRUCTURE`/`PRICE`/`CONSTANT`/
   `TIME`, без `MARKET_PHASE` (само-референция) и runtime-источников
   сделки; `ruleType` — сравнивающие и структурно-событийные, без
   lifecycle-сделки и `MARKET_PHASE_IS` (тест эффективности рынка (ER) —
   через `INDICATOR_COMPARE` над ER-операндом каталога `EFFICIENCY_RATIO`;
   выделенного `EFFICIENCY_BELOW_THRESHOLD` нет — fork A,
   `docs/decisions/efficiency-ratio-as-catalog-indicator.md`). Whitelist —
   create-валидация (400). Детали — `docs/models/domain/aggregate/Strategy.md`
   §StrategyMarketPhaseRule.

2. **`MarketPhaseParams` распущен целиком, без остатка.** Все четыре поля
   удалены: `algorithmType` (какие источники питают фазу — теперь видно
   из того, какие операнды автор пишет), `minTrendScore`/`minRangeScore`
   (планка свидетельства — теперь литералы-константы в условиях),
   `confirmationBars` (фаза-дебаунса нет). Тип и JSONB-колонка `params`
   контейнера исчезают; `StrategyMarketPhaseSetting` не несёт ни
   `params`, ни дебаунса.

3. **Исполнение в job.** Тонкий stateless `MarketPhaseClassifier`
   (чистый first-match) поверх переиспользуемого
   `StrategyConditionEvaluator`; `MarketPhaseJob` остаётся тонким
   (`docs/components/MarketPhaseClassifier.md`,
   `docs/components/MarketPhaseJob.md`).

4. **`confidenceScore` удалён** из `MarketPhase` и из контракта job.
   Детерминированный булев исход не даёт непрерывной уверенности; поле
   никем не потреблялось (`SIGNAL_SCORE_REACHED`/`SIGNAL` уже удалены —
   `strategy-signal-is-entry-condition.md`).

5. **`MarketStructure` — операнд правил фазы.** Структура остаётся
   вычисляемым шаримым результатом (свинг-пивоты / кластеризация уровней
   по `minTouches` / пробой с буфером и подтверждением — не затронуты);
   правила фазы ссылаются на неё операндом `MARKET_STRUCTURE`. Тест
   `MarketStructure.Type` равенством — через `MARKET_STRUCTURE_IS`
   (зеркало `MARKET_PHASE_IS`), per-`ruleType` контракт дозаполняется
   инкрементально.

### Анти-whipsaw — операнд-уровневый

Фаза-уровневого дебаунса нет. Устойчивость классификации обеспечивается
на уровне операндов: сглаживающими периодами индикаторов (`period`,
`signalPeriod`, `smoothPeriod` — описаны один раз на индикаторе,
переиспользуются всеми клаузами по `key`) и структурным
`breakoutConfirmationBars`. Дискретный hold-N-bars гистерезис **не
вводится** (S0); при доказанной потребности — инкрементом полем
`confirmationBars` на сравнивающее правило (`INDICATOR_COMPARE`/
`CROSSOVER`), не на индикатор-значение и не на фазу. Приемлемость
остаточного перескока фазы на стыке режимов — численный риск-аппетит
автора стратегии (хвост пользователя), принят пользователем.

## Отвергнутая альтернатива — двойной скоринг

`trendScore`/`rangeScore` ∈ [0,1] по `algorithmType`, классификация по
порогам `minTrendScore`/`minRangeScore` + маржа, `confidenceScore` =
выигравший score, `confirmationBars` как дебаунс смены фазы.

**Почему отвергнута:**

- **Унификация с грамматикой условий.** Авторская модель переиспользует
  ту же `StrategyCondition`/`StrategyConditionEvaluator`, что и
  entry-условия — снимает **отдельный движок скоринга** и **дубль
  грамматики** (DRY; «дублирующего кода быть не должно»).
- **Арифметики score в торговом корпусе нет.** Готовой формулы «trend
  score / range score» под наши params корпус не даёт (Н3: корпус даёт
  принципы — ER-шум, свинги, подтверждение баром, консервативный
  UNKNOWN, — но не арифметику). Скоринг требовал бы инженерной формулы
  без источникового грунта.
- **«Что считать трендом» — на автора стратегии.** Решение о пороге/
  комбинации свидетельств — продуктово-рисковое (риск-аппетит автора),
  не системный алгоритм. Авторские условия переносят это решение автору
  явными константами операндов; роль `trading-specialist` сжимается до
  меню грунтованных примитивов.

## Следствия

- `docs/models/domain/aggregate/Strategy.md` — §StrategyMarketPhaseSetting
  (`phaseRules` вместо `params`), новый §StrategyMarketPhaseRule
  (клауза + контекстный whitelist), §MarketPhaseParams удалён,
  `MARKET_STRUCTURE_IS` в перечне `ruleType`, §Персистентность
  (`phase_rules` JSONB вместо `params`).
- `docs/models/domain/other/MarketPhase.md` — `confidenceScore` удалён,
  определение `Type` переформулировано (авторские правила, не скоринг).
- `docs/components/MarketPhaseJob.md` — исполнение через классификатор;
  `algorithmType`/score убраны.
- `docs/components/MarketPhaseClassifier.md` — новый компонент (stateless
  first-match поверх `StrategyConditionEvaluator`).
- `docs/decisions/strategy-tree-persistence.md` — `MarketPhaseParams`
  заменён `phaseRules` (JSONB-колонка контейнера).
- `docs/decisions/strategy-condition-authoring-contract.md` — грамматика
  условий переиспользуется фазой в суженном контексте; `MARKET_STRUCTURE_IS`
  как инкремент.
- Закрывает часть «фаза/скоринг» находки **Н3**.

## Связи

- Проработка с кренами — `.claude/work/progress/phase-1-step-3-market-phase-conditional-redesign.md`.
- Модель — `docs/models/domain/aggregate/Strategy.md`
  (§StrategyMarketPhaseRule, §Условия).
- Контракт авторинга условий — `docs/decisions/strategy-condition-authoring-contract.md`.
- Удаление `SIGNAL`/score-слоя — `docs/decisions/strategy-signal-is-entry-condition.md`.
- Структура рынка (выживает как операнд) — `docs/models/domain/other/MarketStructure.md`.
- ER как операнд каталога (fork A; снят выделенный
  `EFFICIENCY_BELOW_THRESHOLD`) —
  `docs/decisions/efficiency-ratio-as-catalog-indicator.md`.
