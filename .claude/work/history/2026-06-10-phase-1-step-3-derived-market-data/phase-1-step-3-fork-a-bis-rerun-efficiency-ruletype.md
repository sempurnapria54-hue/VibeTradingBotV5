# Шаг 3 Фазы 1 — перепрогон A-bis: судьба `EFFICIENCY_BELOW_THRESHOLD`

> **Статус: отвалидировано и зафиксировано в канон.** A-bis → **(b)
> `EFFICIENCY_BELOW_THRESHOLD` свёрнут в `INDICATOR_COMPARE`** (связкой с
> fork A → (2)). Критерий грамматики вынесен принципом —
> `docs/rules/condition-ruletype-granularity.md`; решение —
> `docs/decisions/efficiency-ratio-as-catalog-indicator.md`. Канон:
> `Strategy.md` (перечень/whitelist без `EFFICIENCY_BELOW_THRESHOLD`),
> `strategy-condition-authoring-contract.md`,
> `market-phase-conditional-classification.md`. Точечное применение
> критерия к `VOLUME_FILTER_PASSED`/`*_IS` — отложено (не пакетно). Этот
> файл — рабочая проработка, не источник правды.

## На какой вопрос отвечает этот файл

Оставить `EFFICIENCY_BELOW_THRESHOLD` именованным `ruleType` или свернуть
в `INDICATOR_COMPARE` — перепрогон под-развилки A-bis из
`phase-1-step-3-fork-a-rerun-er-location.md` с дообучением (критерий
оправданности sugar-`ruleType`).

## Контекст и статус автономии

- **Ступень — Предложение** (концепт `solution-designer`; грамматика
  условий, см. `.claude/processes/question-delegation.md`).
  **Проработка-предложение, не правка канона.**
- **Флаг действия CC: предложил.**
- Дисциплина пометок: грамматика/архитектура — *выводимо из
  established/codestyle*; торговое — *источник говорит* /
  *предполагаю·инженерное*.
- **Зависимость от fork A.** A-bis стоит на результате fork A → **(2) ER
  в каталоге индикаторов** (ER — операнд по `key`). Если выбор по fork A
  иной, A-bis переоткрывается. Обе развилки валидируются связкой.

## Что перепрогоняем и чего не хватило

Прежний крен A-bis — **(a) оставить `EFFICIENCY_BELOW_THRESHOLD`
грунтованным sugar**, по аналогии с `RANGE_BREAKOUT_CONFIRMED`
(именованный доменный предикат читаемее). Выбор пользователя — **(b)
свернуть в `INDICATOR_COMPARE`**.

**Дефицит крена (a):** аналогия к `RANGE_BREAKOUT_CONFIRMED` приведена
без проверки **структурного различия** предикатов — а оно и есть
различитель оправданности sugar. Дообучение даёт критерий ниже.

## Критерий оправданности именованного `ruleType` (sugar vs алиас)

Именованный доменный `ruleType` **оправдан** над генерик-примитивом
сравнения, когда инкапсулирует семантику, **не выразимую одним
сравнением операнд-vs-операнд**:

- составное условие / несколько входов (буфер + подтверждение + ссылка
  на уровень);
- темпоральная/стейтфул-семантика (текущее vs прошлое);
- предвычисленный составной флаг/событие (читается готовым);
- событие/тайминг без сравнения значений;
- runtime-факт жизненного цикла сделки вне грамматики рыночных операндов.

Именованный `ruleType` — **чистый алиас** (→ свернуть), когда сводится
ровно к одному `<X>_COMPARE` над каталожным/рыночным операндом и
константой, без добавочной семантики. *Выводимо из codestyle* (DRY,
«дублирующего кода быть не должно»).

## Сверка структуры

### `EFFICIENCY_BELOW_THRESHOLD` — чистый алиас

После fork A → (2) ER — каталожный `IndicatorValue` (операнд по `key`).
«ER ниже порога» = ER `LT` CONSTANT — **ровно один `INDICATOR_COMPARE`**.
Данные evaluator'а: `IndicatorValue (ER)` + `CONSTANT` — та же форма, что
у любого `INDICATOR_COMPARE` (`StrategyConditionEvaluator.md` §Данные).
Проверка на скрытую сложность — её нет:

- **Скрытая нормализация?** Нет: ER нормирован **по определению** ∈ [0,1]
  (`|чистый ход|/Σ|побарных|`), порог — обычное число в [0,1]. *Источник
  говорит* [Kaufman: ER нормирован на чистый ход].
- **Несколько операндов / составной флаг?** Нет: один операнд (ER) vs
  одна константа; не буфер, не подтверждение, не предвычисленное событие.
- **Подтверждение/гистерезис внутри?** Нет: анти-whipsaw —
  операнд-уровневый (период индикатора); дискретный hold-N, если
  понадобится, — поле S1 на **сравнивающем правиле**, едино для
  `INDICATOR_COMPARE` (`market-phase-conditional-classification.md`
  §Анти-whipsaw). Значит не довод держать отдельный тип.

**Дополнительный довод за свёртку (найден на перепрогоне).** Имя
`..._BELOW_...` зашивает оператор `LT` — это не составная семантика, а
**сужение** генерика. Но ER-условия нужны в **обе** стороны: низкий ER →
шум/range, высокий ER → тренд (*источник говорит* [Kaufman: ER→1 тренд,
ER→0 шум]). «ER выше порога» именованным типом не покрыт — для тренд-фаз
автор всё равно берёт `INDICATOR_COMPARE` (ER `GT` …). Держать
`EFFICIENCY_BELOW_THRESHOLD` = иметь sugar на одну сторону и генерик на
другую — **неконсистентная грамматика**. Свёртка обеих сторон в
`INDICATOR_COMPARE` единообразна. *Выводимо из established + codestyle.*

### `RANGE_BREAKOUT_CONFIRMED` — sugar оправдан (аналогия неприменима)

Не одно сравнение: пробой подтверждён, когда цена закрылась за уровнем на
`≥ breakoutBufferPercents` **и** удержалась `≥ breakoutConfirmationBars`
закрытых баров (`phase-1-step-3-gaps-close-4-design.md` §1.1 #5). Данные
evaluator'а: `MarketStructure + MarketPriceData`
(`StrategyConditionEvaluator.md`) — читается **предвычисленное составное
событие** структуры (буфер + N-баровое подтверждение + ссылка на
уровень), не статическое сравнение двух операндов. По критерию — sugar
оправдан. К `EFFICIENCY_BELOW_THRESHOLD` (одно сравнение) прецедент
**неприменим** — это и есть исправляемый дефицит крена (a).

### Применение критерия по перечню

| `ruleType` | Форма | Вердикт |
|---|---|---|
| `EFFICIENCY_BELOW_THRESHOLD` | ER `LT` CONST (после fork A) | **алиас → свернуть (b)** |
| `RANGE_BREAKOUT_CONFIRMED` | составное событие структуры (буфер + N-бар) | sugar оправдан |
| `TREND_CHANGED` | темпоральное (`IndicatorValue + MarketPhase`, текущее vs смена) | sugar оправдан |
| `CANDLE_CLOSED` | событие/тайминг (`timeframe`), без сравнения значений | оправдан |
| `MARKET_PHASE_IS` / `MARKET_STRUCTURE_IS` | enum-равенство операнд vs CONST-ENUM | см. ниже (кандидат, не сворачиваем) |
| `VOLUME_FILTER_PASSED` | контракт не зафиксирован | см. ниже (кандидат, не сворачиваем) |
| `PROFIT_/LOSS_PERCENTS_REACHED` | `Position.avgPrice` + якорь (STRAT-Q4) | не алиас (runtime-сделка + открытый якорь) |
| `NO_OPEN_POSITION` … `MAIN_PROTECTION_EXISTS` | факты жизненного цикла сделки | не алиас |

## Консистентность грамматики — отдельный вопрос, не сворачиваем пакетом

Критерий задевает ещё двух кандидатов, но их **не сворачиваем в этой
итерации** (по постановке — отметить отдельным вопросом):

- **`VOLUME_FILTER_PASSED`** — per-`ruleType` контракт не зафиксирован
  (`strategy-condition-authoring-contract.md` §Открытое). Может оказаться
  алиасом (объём/OBV `GT` порог) **или** составным (объём vs средняя,
  подтверждение). Квалифицировать при фиксации его контракта, не здесь.
- **`MARKET_PHASE_IS` / `MARKET_STRUCTURE_IS`** — enum-равенство; формально
  свелись бы к генерик-сравнению `EQ` над операндом `MARKET_PHASE`/
  `MARKET_STRUCTURE` + CONST-ENUM. Но это бо́льший сдвиг концепции
  (числовое сравнение vs enum-равенство — разные валидационные контракты;
  `MARKET_STRUCTURE_IS` только что введён редизайном). Свёртка enum-IS-
  типов — самостоятельная развилка.

**Отдельный вопрос (на завести, не решать здесь):** общий принцип
грамматики — «когда именованный предикат оправдан над генерик-сравнением»
— и аудит перечня `StrategyConditionRuleType` по нему (кандидаты:
`VOLUME_FILTER_PASSED`, `MARKET_PHASE_IS`/`MARKET_STRUCTURE_IS`). Пакетно
не сворачиваем — критерий применяем точечно при фиксации контракта
каждого типа.

## Миграционный след свёртки (b)

Свёртка — **doc/enum-правка, без миграции данных**: валидатор стратегий и
Strategy API — артефакты под-шага `CODE` (ещё не реализованы),
персистентных стратегий нет. Правится:

1. **Перечень.** Удалить `EFFICIENCY_BELOW_THRESHOLD` из
   `StrategyConditionRuleType` (`Strategy.md` §Условия, строка перечня).
2. **Whitelist фазы.** Убрать `EFFICIENCY_BELOW_THRESHOLD` из списка
   допустимых `ruleType` контекста классификации фазы (`Strategy.md`
   §StrategyMarketPhaseRule). **Возможность не теряется:** ER-сравнение
   проходит через `INDICATOR_COMPARE` (уже в списке сравнивающих) над
   ER-операндом каталога.
3. **Контракт авторинга.** Убрать `EFFICIENCY_BELOW_THRESHOLD` из
   «дозаполняется при реализации» (`strategy-condition-authoring-contract.md`
   §Открытое) — отдельный per-`ruleType` контракт не нужен; покрыт уже
   зафиксированным контрактом `INDICATOR_COMPARE` (operator + оба
   операнда, ≥1 INDICATOR).
4. **Замена в авторинге (механическая).** Клауза
   `{ ruleType: EFFICIENCY_BELOW_THRESHOLD, percents: P }` →
   `{ ruleType: INDICATOR_COMPARE, leftOperand: { sourceType: INDICATOR,
   indicatorKey: "<er>" }, operator: LT, rightOperand: { sourceType:
   CONSTANT, valueType: NUMBER, value: "<порог>" } }`.

## Пересобранный крен A-bis (на валидацию)

**(b) Свернуть `EFFICIENCY_BELOW_THRESHOLD` в `INDICATOR_COMPARE`.**
После fork A → (2) ER — каталожный операнд; «ER ниже порога» = одно
`INDICATOR_COMPARE` (ER `LT` CONST) без добавочной семантики → чистый
алиас. Прецедент `RANGE_BREAKOUT_CONFIRMED` неприменим: тот инкапсулирует
составное событие структуры (буфер + N-баровое подтверждение), а ER-тест
— нет. Усиливающий довод: ER-условия нужны в обе стороны (low→range,
high→trend), а sugar покрывает только «below» → `INDICATOR_COMPARE` всё
равно нужен → держать оба неконсистентно.

- **Не сворачиваем пакетом:** `VOLUME_FILTER_PASSED`,
  `MARKET_PHASE_IS`/`MARKET_STRUCTURE_IS` — кандидаты того же критерия,
  выносятся **отдельным вопросом** (аудит грамматики), точечно при
  фиксации их контрактов.
- **Зависимость:** A-bis стоит на fork A → (2); валидируются связкой.
- **Хвост пользователя:** значение порога ER в условии (риск-аппетит).
- **Отложено на `CODE`/инкремент:** аудит грамматики по критерию sugar;
  контракт `VOLUME_FILTER_PASSED`.
- **Автономия CC: предложил** — канон не правлен.

## Целевые доки после валидации (не правим сейчас)

- `docs/models/domain/aggregate/Strategy.md` — удаление
  `EFFICIENCY_BELOW_THRESHOLD` из `StrategyConditionRuleType` и из
  whitelist §StrategyMarketPhaseRule.
- `docs/decisions/strategy-condition-authoring-contract.md` — убрать
  `EFFICIENCY_BELOW_THRESHOLD` из открытых per-`ruleType`.
- При содержательности — строка в
  `docs/decisions/market-phase-conditional-classification.md` либо
  расширение decision по fork A (ER в каталоге + ER-условие через
  `INDICATOR_COMPARE`, без выделенного `ruleType`).
- Новый открытый вопрос — «критерий именованный-предикат-vs-генерик и
  аудит `StrategyConditionRuleType`» (`open-questions.md`).

## Связи

- Перепрогон fork A (ER → каталог) —
  `.claude/work/progress/phase-1-step-3-fork-a-rerun-er-location.md` §A-bis.
- Контракт авторинга / per-`ruleType` —
  `docs/decisions/strategy-condition-authoring-contract.md`.
- Перечень `ruleType`, whitelist фазы —
  `docs/models/domain/aggregate/Strategy.md` §Условия,
  §StrategyMarketPhaseRule.
- Семантика `RANGE_BREAKOUT_CONFIRMED`/`TREND_CHANGED` (составные) —
  `docs/components/StrategyConditionEvaluator.md` §Данные.
