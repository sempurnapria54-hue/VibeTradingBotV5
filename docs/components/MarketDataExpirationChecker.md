# MarketDataExpirationChecker

## На какой вопрос отвечает этот файл

Кто проверяет свежесть рыночных данных.

## Назначение

`MarketDataExpirationChecker` — runtime-сервис проверки свежести рыночных
данных. Не хранит состояние в БД и **не** меняет `Strategy.Status`.
Отвечает **одним битом**: нужным данным можно доверять или нельзя.

## Контракт

- `Boolean isFresh(referencePoint, expirationDuration)` — свежесть одного
  значения под срок его настройки-владельца; на нём стои́т раздача готовых
  результатов (`docs/components/IndicatorService.md`,
  `docs/components/MarketStructureService.md`);
- `Boolean stepDataFresh(StrategyStep step, ConditionEvaluationContext
  context)` — свежи ли данные, нужные **именно этому шагу**: каждая
  настройка, на которую ссылается его условие, отдала значение.

**Отсутствие и устаревание на поверхности шага неразличимы, и различать их
незачем:** обе пустоты означают «данным доверять нельзя» и ведут к одной
реакции (`docs/spec/market-data-freshness.json`, величина `reaction`).
Устаревшее значение до контекста оценки не доходит — свежесть под срок
настройки-владельца гейтят раздатчики, — поэтому отсутствие ключа в
контексте и есть признак «не свежо».

**Четырёхзначного результата у сервиса нет, и это решение, а не пропуск.**
Перечень `NOT_EXPIRED` / `PARTIALLY_EXPIRED` / `EXPIRED` / `MISSING`
вместе с перечнем причин различал бы то, чего не читает ни один
потребитель: реакция резолвится по одному биту. Прежняя редакция
контракта объявляла две точки входа, возвращавшие такой результат
(`checkForEntry`, `checkForStep`), — они сняты вместе с самим типом.

## Источник сроков

`expirationDuration` из `StrategyIndicatorSetting`,
`StrategyMarketStructureSetting`. У `StrategyMarketPhaseSetting`
`expirationDuration` **нет** — `MarketPhase` не персистируется, свежесть
фазы наследуется от свежести её входов (индикаторов/структур; см.
`docs/models/domain/other/MarketPhase.md`).

## Вычисление свежести (на чтение)

Свежесть вычисляется на чтение, в БД не хранится:
форма — `docs/spec/market-data-freshness.json` (`expiredAt`,
`referencePoint`), здесь она не переписывается. `confirmedAt` — гейт без
look-ahead, не точка отсчёта. Результат ключуется настройкой-владельцем (owner-ключевание,
`docs/rules/market-data-freshness.md`): у строки один
владелец, под его `expirationDuration` и оценивается свежесть — общей
строки с несколькими запрашивающими больше нет. Правило —
`docs/rules/market-data-freshness.md`.

## Граница ответственности

Поведение при expired/missing задаётся не здесь, а в
`StrategyStep.marketDataExpiredSetting` (`WAIT` / `BLOCK_STEP` /
`GRACEFUL_CLOSE` / `KILL_SWITCH`, см. `docs/models/domain/aggregate/Strategy.md`).
Настройка — **пара** реакций, защищённой и незащищённой позиции; ветвь
выбирает предикат покрытия транша, и этот выбор тоже не здесь — дом
`docs/rules/market-data-freshness.md`.
Применение результата в FSM — `docs/processes/deal-management.md`,
`docs/lifecycles/Deal.md`. Сквозное правило свежести —
`docs/rules/market-data-freshness.md`.
