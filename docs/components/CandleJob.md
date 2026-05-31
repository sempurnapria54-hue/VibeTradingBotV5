# CandleJob

## На какой вопрос отвечает этот файл

Кто готовит базовые свечные данные (компонент-job): что делает, что не
делает.

## Назначение

`CandleJob` — job добычи и поддержания целостности базовых свечных
данных (процесс `docs/processes/candle-loading.md`), на которые
опираются производные расчёты рыночных данных
(`docs/processes/market-data-calculation.md`).

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

`CandleJob` — CRON-job (период из конфига, порядка раза в минуту):
ведёт каждую `CandleGroup` (инструмент + таймфрейм) по её
жизненному циклу — `docs/lifecycles/CandleGroup.md`
(`BACKFILL` → `SYNC` → `CHECK` → `REPAIR` → `ACTIVE`): историческая
выкачка до планового горизонта `Instrument.plannedCandleStartDate`,
регулярная докачка хвоста при новом баре, проверка целостности по
count, докачка дыр бинарным поиском. Идемпотентность — по
`(candleGroupId, openTimestamp)`; фактические границы —
`actualFirstUtcMillis`/`actualLastUtcMillis`, объём — `count`. На
старте `count` реконсилируется реальным `COUNT(*)` (защита от
рассинхрона после рестарта в середине пачки). Оркестрация —
процесс `docs/processes/candle-loading.md`. Политика загрузки и
целостности (глубина, расписание, докачка дыр) —
`docs/lifecycles/CandleGroup.md` §«Политика загрузки и
целостности».

Пакет — `domain.jobs`. Кроме CRON, тик запускается вне расписания
через `JobController` (`POST /api/jobs/candle-loading/trigger`):
запуск **асинхронный** (не блокирует HTTP-ответ) через фасад
`CandleJobFacade` (`@Async`) — см. `.claude/rules/codestyle.md`
§«Джобы». Триггер появления нового закрытого бара — предикат модели
`CandleGroup.hasNewClosedBar(now)`.

## Связи

- Модель и lifecycle — `docs/models/domain/other/Candle.md`,
  `docs/models/domain/other/CandleGroup.md`,
  `docs/lifecycles/CandleGroup.md`.
- Процесс — `docs/processes/candle-loading.md` (потребитель свечей
  — `docs/processes/market-data-calculation.md`).
- OKX-формат / контракт — `docs/models/mapping/Candle.md`,
  `docs/integrations/okx/contracts/candle.md`.
