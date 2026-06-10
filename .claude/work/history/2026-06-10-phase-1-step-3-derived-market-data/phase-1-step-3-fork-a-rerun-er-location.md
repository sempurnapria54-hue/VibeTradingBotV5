# Шаг 3 Фазы 1 — перепрогон fork A: где живёт ER (efficiency ratio)

> **Статус: отвалидировано и зафиксировано в канон.** Fork A → **(2) ER
> в каталоге индикаторов** (связкой с A-bis). Решение —
> `docs/decisions/efficiency-ratio-as-catalog-indicator.md`. Канон:
> `IndicatorValue.md` (`EFFICIENCY_RATIO` + `EfficiencyRatioValue`),
> `Strategy.md` (§IndicatorParams `EfficiencyRatioParams`),
> `strategy-condition-authoring-contract.md`,
> `market-phase-conditional-classification.md`. Этот файл — рабочая
> проработка, не источник правды.

## На какой вопрос отвечает этот файл

Где живёт мера эффективности/шума (ER) при условной модели фазы, когда
аналитик-скоринг распущен и фаза адресует ER авторским условием —
перепрогон развилки A файла `phase-1-step-3-gaps-close-4-design.md` в
новом контексте.

## Контекст и статус автономии

- **Ступень — Предложение** (концепт `solution-designer` + торговое ядро
  `trading-specialist`, см. `.claude/processes/question-delegation.md`).
  Проектирование модели/компонента на Предложении **не финализируется**.
- Это **проработка-предложение, не правка канона.** Канон
  (`IndicatorValue.md`, `Strategy.md`, контракт авторинга, при нужде
  decision) правится **после** валидации пользователем.
- **Флаг действия CC: предложил** (в рамках ступени).
- Дисциплина пометок: архитектурные утверждения — *выводимо из
  established/codestyle*; торговые — *источник говорит* /
  *предполагаю·инженерное*.

## Что перепрогоняем и почему

### Исходная развилка A (прежний крен)

`phase-1-step-3-gaps-close-4-design.md` §1.4, развилка A «где живёт мера
шума (ER)». ER не в каталоге индикаторов (`ATR/EMA/RSI/MACD/STOCHASTIC/
BOLLINGER_BANDS/OBV`).

- **(1)** считать ER **внутри аналитика** (внутренняя мера, не хранимый
  `IndicatorValue`) — **прежний крен**.
- **(2)** добавить ER в каталог индикаторов — **отвергалось**: «раздувает
  каталог ради внутренней метрики, **которую стратегия не адресует
  условием**».
- **(3)** прокси через существующие (EMA-наклон / ATR) — допустимо как
  дополнение к (1).

### Почему развилка переоткрыта

Прежний крен (1) и отвержение (2) держались на скоринговой модели фазы,
которая редизайном замещена
(`docs/decisions/market-phase-conditional-classification.md`, в каноне).
Оба основания рухнули:

1. **Носитель варианта (1) исчез.** Аналитик-скоринг распущен;
   `MarketPhaseClassifier` теперь **stateless first-match** поверх
   `StrategyConditionEvaluator`, по свечам ничего не считает — читает
   готовые `IndicatorValue`/`MarketStructure`/`MarketPriceData`
   (`docs/components/MarketPhaseClassifier.md` §Границы). Скрытой
   внутренней меры «внутри аналитика фазы», где ER жил бы как мера
   скоринга, **больше нет**. *Выводимо из established.*
2. **Основание отвержения (2) инвертировалось.** Фаза определяется
   авторскими условиями; `EFFICIENCY_BELOW_THRESHOLD` уже в каноне как
   `ruleType` контекста классификации фазы (`Strategy.md`
   §StrategyMarketPhaseRule + перечень `StrategyConditionRuleType`).
   Стратегия **уже адресует ER условием** — ровно отсутствием чего
   обосновывался отказ от (2). *Выводимо из established.*

То есть редизайн де-факто уже оперся на ER как операнд (EFFICIENCY-
ruleType в каноне), но fork A формально не переоценён. Перепрогон это
закрывает.

## Перепрогон: где живёт ER при условной модели

### Несущий аргумент — ER должен быть произведённым шаримым результатом

`MarketPhaseClassifier` и `StrategyConditionEvaluator` **не считают
индикаторы** — читают готовые значения (`IndicatorValue.md` §Назначение:
«Потребители читают готовые значения и **не** считают индикаторы сами:
`StrategyConditionEvaluator`…»; `MarketPhaseClassifier.md` §Границы).
Значит условие фазы вида «ER ниже порога» не может вычислять ER на лету в
evaluator'е — оно тестирует **уже посчитанное** значение ER. *Выводимо из
established.*

Кто тогда считает ER? Единственная машинерия проекта, производящая
оконные per-timeframe скалярные рыночные значения, шаримые по идентичности
конфигурации, — `IndicatorJob` → `IndicatorValue` (реестр
`indicator_configs`). Третьего «ER-job» нет и заводить его —
дублировать существующую машинерию. *Выводимо из established/codestyle.*

### ER структурно тождественен `IndicatorValue`

| Свойство `IndicatorValue` | ER |
|---|---|
| Скалярное значение по окну закрытых свечей | ER = \|чистый ход за окно\| / Σ\|побарных ходов\| — скаляр ∈ [0,1] по окну. *Источник говорит* [Kaufman, дистиллят `system-design` §5 / `strategy-patterns` §1] |
| Оконный параметр (`period`) | окно ER (`period`) |
| `warmup` выводится из типа+period (оконные → `period`) | ER — оконный, не рекурсивный → `warmup = period`. *Выводимо из established* (warmup-derive); *предполагаю·инженерное* (ER — оконный) |
| Per-`timeframe`, шарится по `config_id`, свежесть под запрашивающую настройку | то же |
| Читается evaluator'ом как готовое значение | то же |

ER — **индикатор-формы** мера (а не структура с уровнями → не
`MarketStructure`). Более того, ER в корпусе — именно измеряемый
индикатор: это база KAMA (Kaufman's Adaptive Moving Average). *Источник
говорит* [Kaufman гл. 1 «Measuring Noise» → ER/KAMA]. Каждое свойство
`IndicatorValue` на ER ложится 1:1.

### Контекст-сплит (честная развёртка, не плоский флип)

Fork A исходно покрывал ER в **двух** ролях прежнего дизайна. Под
редизайн они расходятся:

- **Роль 1 — ER как операнд классификации фазы.** Прежний дом (внутри
  скоринга фазы) исчез. Теперь фаза адресует ER авторским условием →
  ER **должен быть доступен как операнд по `key`** → **каталог индикаторов
  (вариант 2)**. Это локус развилки сейчас, и редизайн его де-факто уже
  занял.
- **Роль 2 — ER как внутренняя мера `MarketStructureAnalyzer`.**
  `MarketStructureAnalyzer`/`MarketStructureParams` редизайном **не
  затронуты** (`market-phase-conditional-classification.md` п.5) — это
  выживший **вычисляющий** компонент. Его внутреннее использование ER
  (тесты §1.1 #4 «чистый ход доминирует над шумом» и #6 UNKNOWN «шум
  слишком высок») решает `MarketStructure.Type` и наружу операндом не
  выходит — автор ссылается на результат через `MARKET_STRUCTURE_IS`, а не
  на внутренний ER структуры. Здесь вариант (1) **узко выживает**.

**Унификация ролей (рекомендация, не двойной счёт).** Контракт аналитика
структуры уже принимает **опциональный `IndicatorValue[]`** как шумовой
фильтр (§1.1: «опционально `IndicatorValue` (шумовой фильтр)»). Раз ER
становится каталожным `IndicatorValue`, аналитик структуры должен
потреблять **тот же** шаримый ER этим опциональным входом, а не
пересчитывать ER внутри себя — единый источник, без дрейфа двух ER.
Когда ER-настройка не объявлена — fallback на прокси EMA-наклон/ATR
(прежний вариант 3). Так остаток вариантов (1)/(3) сворачивается в (2)
без дубль-вычисления. *Выводимо из codestyle* (DRY, единый источник).

## Под-детали при ER → каталог (вариант 2)

1. **Тип в enum.** `IndicatorValue.Type` += `EFFICIENCY_RATIO`
   (полные слова, как прочие; домен — без сокращений).
2. **`IndicatorParams`-наследник.** `EfficiencyRatioParams(period)` —
   `period` = окно ER; база несёт `timeframe`/`warmup`.
   `warmup = period` по derive-правилу оконных индикаторов
   (упрощённый минимум create-валидации = `period`).
3. **`IndicatorValue`-наследник.** `EfficiencyRatioValue(efficiencyRatio)`
   — один скаляр `BigDecimal` ∈ [0,1] (как все значения индикаторов).
4. **Ссылка ruleType / `INDICATOR_COMPARE` на ER по `key`.**
   - Автор объявляет настройку в пуле
     `StrategyMarketPhaseSetting.indicatorSettings`:
     `{ key: "er-fast", type: EFFICIENCY_RATIO, params: { timeframe, period }, destiny: MARKET_PHASE }`.
   - Базовое сравнение — **уже выразимо существующей грамматикой**, нового
     ruleType не требует: `INDICATOR_COMPARE` с
     `leftOperand { sourceType: INDICATOR, indicatorKey: "er-fast" }`
     `operator: LT`
     `rightOperand { sourceType: CONSTANT, valueType: NUMBER, value: "0.3" }`.
     *Выводимо из established* (контракт операнда: `INDICATOR` → `indicatorKey`).
5. **Согласование с «каталог расширяем через стратегию».** Новый тип
   индикатора заводится тогда, когда стратегия его адресует — ровно этот
   случай (фаза адресует ER условием). Каталог расширяется по
   потребности, не превентивно (codestyle); прежнее возражение «раздувает
   ради внутренней метрики» снято — ER теперь авторски-адресуемый
   индикатор, а не внутренняя метрика. *Выводимо из codestyle + контракт
   авторинга* (`{ key, type, params }`).

### Под-развилка A-bis — судьба `EFFICIENCY_BELOW_THRESHOLD`

Раз ER — первоклассный каталожный индикатор, `EFFICIENCY_BELOW_THRESHOLD`
перекрывается с `INDICATOR_COMPARE` (ER `LT` CONSTANT). Развилка:

- **(a) Оставить `EFFICIENCY_BELOW_THRESHOLD` как грунтованный sugar** —
  **крен·рекомендация**. Контракт: `indicatorKey` → ER-настройка +
  `percents` (порог). Это **доменно-выразительный** предикат (как
  `RANGE_BREAKOUT_CONFIRMED` вместо ручной композиции примитивов):
  называет корпусный торговый смысл «шум слишком высок → range/no-trade»,
  самоописателен. Уже в каноне. *Источник говорит* (ER-шум как
  дискриминатор режима).
- **(b) Свернуть `EFFICIENCY_BELOW_THRESHOLD` в `INDICATOR_COMPARE`** —
  *выводимо из codestyle* (DRY): дедикейтед ruleType дублирует общее
  сравнение. Но это удаление уже-канонного типа.

Не финализируем: per-`ruleType` контракт `EFFICIENCY_BELOW_THRESHOLD`
дозаполняется при реализации (`strategy-condition-authoring-contract.md`
§«Что осталось открытым»). Перепрогон лишь **подсвечивает** A-bis как
следствие (2); решение — инкремент контракта, не эта итерация.

## Есть ли причина против (2)?

По требованию постановки — перечислить явно, чтобы это не было молчаливым
возвратом к (1). **Выжившей причины против (2) нет**; кандидаты и почему
отпали:

- **«Раздувает каталог ради внутренней метрики»** (прежнее основание) —
  **инвертировано**: ER теперь адресуется авторским условием, это
  легитимный авторский индикатор, а не внутренняя метрика.
- **«ER — не классический TA-индикатор, как ATR/EMA/RSI»** — отпадает:
  каталог определяется не членством в каноническом списке TA, а тем, что
  стратегия ссылается на это как на вычисляемый скаляр по `key`. ER
  скалярен/оконный/шарим/read-ready — структурно тождествен прочим; в
  корпусе ER — измеряемый индикатор (база KAMA). *Источник говорит.*
- **«Дубль-вычисление с внутренним ER структуры»** — реальное минорное
  напряжение, снимается унификацией (аналитик структуры потребляет тот же
  каталожный ER опциональным `IndicatorValue`-входом). Это довод **за**
  единый источник, не против (2).

## Пересобранный крен fork A (на валидацию)

**ER живёт в каталоге индикаторов (вариант 2)** — локус развилки сейчас —
роль операнда классификации фазы. ER заводится как
`IndicatorValue.Type.EFFICIENCY_RATIO` (+ `EfficiencyRatioParams(period)`,
`EfficiencyRatioValue(efficiencyRatio)`, `warmup = period`), считается
`IndicatorJob`'ом, читается evaluator'ом как готовое значение. Условие
фазы ссылается на ER по `indicatorKey` через `INDICATOR_COMPARE` (базовый
путь) либо грунтованный sugar `EFFICIENCY_BELOW_THRESHOLD` (уже в каноне).

Прежний крен (1) **не возвращается** для роли фазы (его носитель
распущен); (1) узко выживает лишь как внутренняя мера выжившего
`MarketStructureAnalyzer` и предпочтительно сворачивается в (2)
(аналитик структуры потребляет каталожный ER опциональным входом, fallback
— прокси EMA-наклон/ATR = вариант 3).

- **Хвост пользователя:** конкретное значение порога ER в условии
  (бывшая «планка свидетельства») и окно `period` ER — численный
  риск-аппетит автора, не алгоритм.
- **Отложено на `CODE`/инкремент:** per-`ruleType` контракт
  `EFFICIENCY_BELOW_THRESHOLD` и под-развилка A-bis (sugar vs свёртка в
  `INDICATOR_COMPARE`); точная арифметика ER-окна (как Н9).
- **Автономия CC:** перепрогон — **предложил**, ничего не финализировано;
  канон правится после валидации.

## Целевые доки после валидации (не правим сейчас)

- `docs/models/domain/other/IndicatorValue.md` — `Type` += `EFFICIENCY_RATIO`;
  наследник `EfficiencyRatioValue(efficiencyRatio)`.
- `docs/models/domain/aggregate/Strategy.md` — §IndicatorParams +
  `EfficiencyRatioParams(period)`.
- `docs/decisions/strategy-condition-authoring-contract.md` — per-`ruleType`
  контракт `EFFICIENCY_BELOW_THRESHOLD` (если оставлен sugar'ом) либо его
  свёртка в `INDICATOR_COMPARE` (A-bis) — инкрементом.
- При содержательной развилке (включение ER в каталог как сдвиг
  концепции «внутренняя мера → авторский операнд») — строка/уточнение в
  `docs/decisions/market-phase-conditional-classification.md` либо
  отдельный decision.
- §1.1/§1.3 `phase-1-step-3-gaps-close-4-design.md` — пометка, что
  опциональный `IndicatorValue`-вход аналитика структуры под ER — это
  каталожный ER (унификация), а не внутренний пересчёт.

## Связи

- Исходная развилка A — `.claude/work/progress/phase-1-step-3-gaps-close-4-design.md` §1.4.
- Редизайн условной фазы (распустивший носитель (1)) —
  `docs/decisions/market-phase-conditional-classification.md`;
  проработка — `.claude/work/progress/phase-1-step-3-market-phase-conditional-redesign.md`.
- Модель индикатора — `docs/models/domain/other/IndicatorValue.md`,
  `docs/models/domain/aggregate/Strategy.md` §IndicatorParams.
- Контракт авторинга (ссылка операнда по `key`, per-`ruleType` инкремент) —
  `docs/decisions/strategy-condition-authoring-contract.md`.
- Классификатор фазы (читает готовое, не считает) —
  `docs/components/MarketPhaseClassifier.md`.
