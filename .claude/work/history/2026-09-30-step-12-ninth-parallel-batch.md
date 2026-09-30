# Девятая пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала девятая пачка параллельных секций узла 1e (заход 240) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию держателя 2026-09-30 после доковой восьмой.
Две волны субагентов на непересекающихся путях, продовая дельта — три модуля:
`services/common/model/domain`, `services/common/strategy-engine`,
`services/trading-core`; у `services/strategies` тронут только тестовый код.
Запись захода — хроника шага, §«Сто семьдесят второй заход (240) — девятая
пачка параллельных секций». Решения — Д2436-Д2446
`.claude/work/decision-digest.md`.

- **Сняты целиком** (секции `.claude/work/backlog.md`):
  - «Проверка торгуемости в коде запирает защиту вопреки дому риска» —
    `RiskValidator` кладёт `INSTRUMENT_NOT_LIVE` только на действии блок-сета,
    `RiskBlockResolver` держит код в карв-ауте живого риска; клетки `U2.19`-`U2.23`,
    `U22.10` `.claude/tests/cases/trading-core-risk.md`;
  - «Отдельная защита без триггерной цены засчитывается разрешимым уровнем» —
    состояние недостижимо (Д2437), код не менялся;
  - «Временная ошибка расчёта писателя не имеет, а ядро по ней ветвится» —
    писатель временного типа в `strategy-engine`
    (`docs/components/models/CalculationError.md`), ядро перетипизирует
    временную с исчерпанным бюджетом (`StrategyWorkRunner`, клетки `U22.20`,
    `U22.21` `.claude/tests/cases/trading-core-fsm.md`);
  - «Имя индекса пары котировки собирает доменный код ядра конкатенацией» —
    `ExchangeOperationsClient#indexInstrumentId`,
    `docs/components/RefreshBillsExecutor.md`;
  - «Пустая сумма движения в сверке читается нулём, а спека отказывает» —
    `DealReconciliationCalculator#requiredAmount`, клетка `U4.13`, находка `F2`
    `.claude/tests/cases/trading-core-calc.md`;
  - «Имя окружения у ядра объявлено и читателя не имеет» — величина,
    ключ конфигурации и переменная `deploy/base/services/trading-core.yaml`
    сняты; клетка `B13.8` `.claude/tests/cases/trading-core.md` снята.
- **Сняты пункты сборных секций:** выбор последней сработавшей защиты — в
  модели (`DealTranche`, клетки `U5.11`-`U5.13` и `U5.9`, `U5.10`
  `.claude/tests/cases/domain-model-predicates.md`, пассаж
  `docs/lifecycles/DealTranche.md`); javadoc `PriceCalculator`,
  `CalculationErrorCodes`; заголовок `U7` и строка перечня результата
  `.claude/tests/cases/strategy-engine-calculation.md`; клетки `U7.8`, `U7.9`
  `.claude/tests/cases/trading-core-fsm.md`; исход накопительных точек
  `RISK_APPETITE_NOT_CONFIGURED`; разрешающий вердикт
  `docs/components/RiskBlockResolver.md`; проба `PastReadDeclarationTest`.
- **Перевооружены на оставшуюся половину соседей:** «Кроссовер с ценовым
  операндом ложен всегда» (наполнение у `market-data` и ядра, отказ у
  `strategies`), «Тип `CANDLE_CLOSED` константно истинен» (снятие из
  перечня — `strategies`), «Пустое условие истинно» (отказ созданием —
  `strategies`).
- **Прогон** — `bash tools/reactor-test.sh --modules
  services/common/model/domain,services/common/strategy-engine,services/trading-core,services/strategies`:
  3379 тестов, дефектов 0; компиляция `services/market-data` против
  изменённых библиотек чиста.
