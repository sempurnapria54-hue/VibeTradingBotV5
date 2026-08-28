# MarketStructureResolver

## На какой вопрос отвечает этот файл

Кто вычисляет структуру рынка из свечей (доменный компонент): контракт
(вход/выход), потребление готового ER, fallback, границы.

## Назначение

`MarketStructureResolver` — выделенный доменный **вычисляющий** компонент:
из закрытых свечей окна выводит `MarketStructure` (тип, ценовые уровни,
событие пробоя). Семантика классификации (свинг-пивоты, кластеризация
уровней, тесты диапазона/тренда, пробой, `UNKNOWN`) —
`docs/models/domain/other/MarketStructure.md` классификации.
Зовётся тонким `docs/components/MarketStructureJob.md`; сам не персистит.

Имя `Resolver` (не `Analyzer`) — чтобы развести с будущей ролью «аналитик»
торгового совета (фаза 4).

## Контракт

```
resolve(
    closedCandles,                  // окно lookbackBars закрытых свечей
    efficiencyRatio: BigDecimal?,   // готовый каталожный ER-скаляр (тренд/шум); null — ER-вход не объявлен
    atr: BigDecimal?,               // готовый каталожный ATR-скаляр (толеранс кластеризации, D3); null — не объявлен
    params                          // MarketStructureParams
) -> (
    type: MarketStructure.Type,
    levels: List<MarketPriceLevel>,
    breakoutEvent,                  // предвычисленное событие подтверждённого пробоя (сломанный уровень + направление + confirmedAt)
    confirmedAt: OffsetDateTime,
    windowStartAt, windowEndAt: OffsetDateTime
)
```

- **Потребляет готовые ER/ATR-скаляры, не пересчитывает.** ER — **единый
  каталожный источник** (fork A — `docs/rules/condition-ruletype-granularity.md`,
  `docs/models/domain/other/MarketStructure.md`): резолвер ER по
  свечам, когда он объявлен, **не считает**, берёт готовый скаляр
  (дискриминатор тренд/шум). ATR (толеранс кластеризации уровней, D3) — так
  же. Скаляры извлекает из готового `IndicatorValue` и подаёт
  `MarketStructureJob` по «мягким» ключам
  `StrategyMarketStructureSetting.efficiencyRatioKey` / `atrKey`.
- **Fallback.** `efficiencyRatio == null` (ER-вход не объявлен) → резолвер
  считает **минимальный внутренний прокси**: нетто-ход окна / суммарный
  побарный ход по ценам закрытия (мини-ER), чтобы структуру можно было
  посчитать без принуждения автора объявлять шумовой индикатор. `atr ==
  null` → толеранс кластеризации откатывается на долю цены. Объявленные
  индикаторы повторно не вычисляются.
- **«Объявлено, но не готово» решает job, не резолвер.** Резолвер видит
  только скаляр или `null`. Различие «не объявлено» (→ прокси) vs
  «объявлено, но не готово / устарело» (→ консервативный `UNKNOWN`) держит
  `MarketStructureJob`: при необъявленном ключе он подаёт `null` (резолвер
  идёт в прокси), при объявленном-но-неготовом пишет `UNKNOWN`-результат
  сам, резолвер не зовёт.
- **Stateless по входу.** Результат — функция от окна свечей, `params` и
  переданных ER/ATR-скаляров; история прошлых результатов не читается.
  «Докуда посчитано» — производный checkpoint job'а
  (`MarketStructureJob`).
- **Событие пробоя.** Подтверждённый пробой (буфер `breakoutBufferPercents`
  + удержание `breakoutConfirmationBars` — оба из `MarketStructureParams`)
  экспонируется явным `breakoutEvent`, который условие
  `RANGE_BREAKOUT_CONFIRMED` читает готовым (детекция — здесь, не в
  условии). Точная форма `breakoutEvent` — `CODE`.

## Границы

- **Не персистит** `MarketStructure`/`MarketPriceLevel` — это
  `MarketStructureJob`.
- **Не считает каталожные индикаторы** (ER/ATR), объявленные стратегией, —
  получает их готовыми скалярами от job (`IndicatorJob` их считает).
  Внутренний прокси (мини-ER по ценам закрытия) — только когда ER-вход не
  объявлен.
- **Геометрические пороги** (`swingLookbackBars`, `lookbackBars`,
  `minTouches`, `minRangeWidthPercents`/`maxRangeWidthPercents`,
  `breakoutBufferPercents`, `breakoutConfirmationBars`) — вход
  `MarketStructureParams`, хвост пользователя; резолвер их не дефолтит
  (отсутствуют → консервативный `UNKNOWN`).
- **Калибруемые пороги** `trendEfficiencyThreshold` (D2) и
  `levelToleranceAtrMultiplier` (D3) — тоже вход `params`, но при `null`
  резолвер применяет **провизорные дефолты** (значения провизорны,
  STRUCT-Q1; калибровка — фаза 2).
- **Точная арифметика** (окно/формула ER, fallback-толеранс долей цены,
  провизорные дефолты) — деталь реализации (`CODE`) с торговой сверкой
  множителей по ходу.

## Связи

- Семантика и форма результата — `docs/models/domain/other/MarketStructure.md`.
- Тонкий job-владелец — `docs/components/MarketStructureJob.md`.
- Раздача готовой структуры потребителям — `docs/components/MarketStructureService.md`.
- ER как каталожный вход (единый источник) —
  `docs/rules/condition-ruletype-granularity.md`,
  `docs/models/domain/other/IndicatorValue.md`.
- Пороги структуры (D2/D3), проводка ER/ATR-входов (fork-A), краевой случай
  идентичности — `docs/models/domain/other/MarketStructure.md`.
- `MarketStructure` как операнд правил фазы / `RANGE_BREAKOUT_CONFIRMED` —
  `docs/models/domain/other/MarketPhase.md`,
  `docs/rules/strategy-condition-contract.md`.
