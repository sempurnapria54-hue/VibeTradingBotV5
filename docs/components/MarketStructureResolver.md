# MarketStructureResolver

## На какой вопрос отвечает этот файл

Кто вычисляет структуру рынка из свечей (доменный компонент): контракт
(вход/выход), потребление готового ER, fallback, границы.

## Назначение

`MarketStructureResolver` — выделенный доменный **вычисляющий** компонент:
из закрытых свечей окна выводит `MarketStructure` (тип, ценовые уровни,
событие пробоя). Семантика классификации (свинг-пивоты, кластеризация
уровней, тесты диапазона/тренда, пробой, `UNKNOWN`) —
`docs/models/domain/other/MarketStructure.md` §Семантика классификации.
Зовётся тонким `docs/components/MarketStructureJob.md`; сам не персистит.

Имя `Resolver` (не `Analyzer`) — чтобы развести с будущей ролью «аналитик»
торгового совета (фаза 4).

## Контракт

```
resolve(
    closedCandles,                  // окно lookbackBars закрытых свечей
    optional indicatorValues,       // готовые каталожные IndicatorValue: ER (тренд/шум), ATR (пол свинг-шума)
    params                          // MarketStructureParams
) -> (
    type: MarketStructure.Type,
    levels: List<MarketPriceLevel>,
    breakoutEvent,                  // предвычисленное событие подтверждённого пробоя (сломанный уровень + направление + confirmedAt); форма — CODE
    confirmedAt: OffsetDateTime,
    windowStartAt, windowEndAt: OffsetDateTime
)
```

- **Потребляет готовые `IndicatorValue`, не пересчитывает.** ER — **единый
  каталожный источник** (fork A — `docs/decisions/efficiency-ratio-as-catalog-indicator.md`):
  резолвер ER по свечам **не считает**, берёт готовое значение
  (дискриминатор тренд/шум). ATR (пол свинг-шума) — так же, если объявлен.
- **Fallback (гибрид).** Объявленные автором каталожные индикаторы (ER
  предпочтительно; EMA/ATR, если есть) потребляются **готовыми** (единый
  источник). **Минимальный внутренний прокси** (наклон short-EMA / ATR из
  окна свечей) резолвер считает сам **только когда не объявлено ничего** —
  чтобы структуру можно было посчитать без принуждения автора объявлять
  шумовой индикатор. Объявленные индикаторы повторно не вычисляются.
- **Stateless по входу.** Результат — функция от окна свечей, `params` и
  переданных `IndicatorValue`; история прошлых результатов не читается.
  «Докуда посчитано» — производный checkpoint job'а
  (`MarketStructureJob` §Идемпотентность).
- **Событие пробоя.** Подтверждённый пробой (буфер `breakoutBufferPercents`
  + удержание `breakoutConfirmationBars` — оба из `MarketStructureParams`)
  экспонируется явным `breakoutEvent`, который условие
  `RANGE_BREAKOUT_CONFIRMED` читает готовым (детекция — здесь, не в
  условии). Точная форма `breakoutEvent` — `CODE`.

## Границы

- **Не персистит** `MarketStructure`/`MarketPriceLevel` — это
  `MarketStructureJob`.
- **Не считает каталожные индикаторы** (ER/ATR/EMA), объявленные
  стратегией, — читает их готовыми (`IndicatorJob` их считает). Внутренний
  прокси — только last-resort, когда ничего не объявлено.
- **Численные пороги** (`swingLookbackBars`, `lookbackBars`, `minTouches`,
  `breakoutBufferPercents`, `breakoutConfirmationBars`, границы ширины
  диапазона) — вход `MarketStructureParams`, хвост пользователя; резолвер
  их не дефолтит.
- **Точная арифметика** (толеранс кластеризации, `k` волатильностного
  пола, окно/формула ER, критерий «наклон EMA согласен») — деталь
  реализации (`CODE`) с торговой сверкой множителей по ходу.

## Связи

- Семантика и форма результата — `docs/models/domain/other/MarketStructure.md`.
- Тонкий job-владелец — `docs/components/MarketStructureJob.md`.
- Раздача готовой структуры потребителям — `docs/components/MarketStructureService.md`.
- ER как каталожный вход (единый источник) —
  `docs/decisions/efficiency-ratio-as-catalog-indicator.md`,
  `docs/models/domain/other/IndicatorValue.md`.
- `MarketStructure` как операнд правил фазы / `RANGE_BREAKOUT_CONFIRMED` —
  `docs/decisions/market-phase-conditional-classification.md`,
  `docs/decisions/strategy-condition-authoring-contract.md`.
