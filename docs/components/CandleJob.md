# CandleJob

## На какой вопрос отвечает этот файл

Кто готовит базовые свечные данные (компонент-job): что делает, что не
делает.

## Назначение

`CandleJob` — job подготовки базовых свечных данных, на которые опираются
все остальные расчёты рыночных данных (см.
`docs/processes/market-data-calculation.md`).

## Делает

- загружает свежие свечи;
- обновляет историю свечей;
- следит за закрытием свечей;
- сохраняет данные в доменные таблицы;
- не допускает look-ahead.

## Не делает

- не считает стратегические сигналы;
- не создаёт `Deal`;
- не управляет сделкой;
- не считает SL/TP.

## Правило

Для расчёта индикаторов используются только закрытые свечи — это
обеспечивает `CandleJob` и потребители (`IndicatorJob` и др.).

## Жизненный цикл загрузки свечей

`CandleJob` ведёт каждую `CandleGroup` (инструмент + таймфрейм) по
её жизненному циклу — `docs/lifecycles/CandleGroup.md`
(`BACKFILL` → `SYNC` → `CHECK` → `REPAIR` → `ACTIVE`): историческая
выкачка, регулярная докачка хвоста, проверка целостности по count,
докачка дыр. Идемпотентность — по `(candleGroupId, openTimestamp)`;
checkpoints покрытия — `coverageStartUtcMillis`/
`coverageEndUtcMillis`. Оркестрация в общем потоке рыночных данных
— `docs/processes/market-data-calculation.md`. Детали политики
дозагрузки и глубины истории отложены до `DOCS_CHECK_2` (см.
lifecycle §«Что отложено»).

## Связи

- Модель и lifecycle — `docs/models/domain/other/Candle.md`,
  `docs/models/domain/other/CandleGroup.md`,
  `docs/lifecycles/CandleGroup.md`.
- Процесс — `docs/processes/market-data-calculation.md`.
- OKX-формат / контракт — `docs/models/mapping/Candle.md`,
  `docs/integrations/okx/contracts/candle.md`.
