# Двадцать первая пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала двадцать первая пачка параллельных секций узла 1e (заход 252) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию держателя 2026-09-30 после доковой двадцатой.
Четыре кодовых субагента на непересекающихся путях; продовая дельта — пять
модулей: `services/common/model/domain`, `services/trading-core`,
`services/market-data`, `services/audit`, `services/statistics`; пятый
субагент заполнил колонку «Факт» по отчётам прогона. Запись захода — хроника
шага, §«Сто восемьдесят четвёртый заход (252) — двадцать первая пачка
параллельных секций». Решения — Д2643-Д2653 `.claude/work/decision-digest.md`.

- **Сняты целиком** (секции `.claude/work/backlog.md`):
  - «Живой эпизод сделки сужается до конъюнкции, активная строка получает свой
    предикат» — `Deal.livePosition()` по `hasLiveRisk()`, новый
    `Deal.activeEpisode()` с одним читателем `RefreshPositionExecutor`; клетки
    U7.2, U7.19, U7.20, U3.8;
  - «Нога в ERROR снимается по наблюдённой живости на площадке» —
    `Order.externalLive` (миграция `trading-core` `V14`), `Order.mayBeLive()`,
    `DealTranche.mayBeLiveOrders()`, писатель `RefreshOrderExecutor`,
    читатели `DealTerminalGate`, `KillSwitchExecutor`,
    `AttachedAlgoOrderStateResolver.attachedParentStatus` (Д2643, Д2645-Д2647);
    клетки U9.20-U9.23, U2.15, U2.16, U10.14, B5.16, B5.17;
  - «Резолвер структуры выдаёт неподтверждённую границу и не ломает структуру
    пробоем» — `MarketStructureResolver`, javadoc `MarketStructure`; клетки
    U13.2, U13.4, U13.6, U13.7, U13.9, U14.1-U14.3, U14.7-U14.9;
  - «Ряд OBV продолжается от записанного значения, а бар без объёма значения не
    имеет» — `ObvCalculator`, `IndicatorJob`, проекция `ObvSeedRow`, javadoc
    `ObvValue`, `IndicatorCalculator` (Д2648-Д2650); клетки U9.6, U9.9, U9.10,
    B4.13 (новый класс ящика `CandleWindowBoxTest`);
  - «Пустой строковый операнд журнальной выборки отвергается — исполнитель
    читает его значением» — `JournalQuery`, `JournalReadService` (Д2651);
    клетка B8.10.
- **Сужены:** «Хвосты девятнадцатой пачки» — исполнены предикат
  `isLocallyTerminal()` на `Order`/`AlgoOrder` (Д2644), комментарий
  `lag_gap_at` новыми миграциями `V2` у `audit` и `statistics`, javadoc
  `Instrument.Status.CREATED`; «Хвосты двадцатой пачки» — исполнены javadoc
  обеих строк состояния приёма и пустой компонент позиции агрегатов у
  `statistics` (дом `docs/rules/statistics-aggregates.md`, решение
  `.claude/decisions/aggregate-cursor-empty-string.md`, Д2652, клетка B10.19);
  «Клетки, получившие ожидание двадцатой пачкой» и «…восемнадцатой пачкой» —
  сняты клетки, чей «Факт» заполнен прогоном этого захода.
- **Заведена:** «Хвосты двадцать первой пачки 2026-09-30».
- **Прогон** — `bash tools/reactor-test.sh --modules
  services/common/model/domain,services/trading-core,services/market-data,services/audit,services/statistics`:
  красный на `SchemaInputBoxTest` `trading-core` (перечень миграций без `V14`),
  прочие четыре модуля зелены; после правки — `--modules services/trading-core`:
  1855 тестов, дефектов 0; `tests` — компиляция `test-compile` зелена.
