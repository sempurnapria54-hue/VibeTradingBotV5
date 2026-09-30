# Одиннадцатая пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала одиннадцатая пачка параллельных секций узла 1e (заход 242) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию держателя 2026-09-30 после доковой десятой.
Три субагента на непересекающихся путях, продовая дельта — пять модулей:
`services/strategies`, `services/common/model/domain`,
`services/common/strategy-engine`, `services/market-data`,
`services/trading-core`. Запись захода — хроника шага, §«Сто семьдесят
четвёртый заход (242) — одиннадцатая пачка параллельных секций». Решения —
Д2466-Д2475 `.claude/work/decision-digest.md`.

- **Сняты целиком** (секции `.claude/work/backlog.md`):
  - «Ненайденность в контексте тенанта у `strategies` исполнена не по дому» —
    чтение и переход по чужому либо несуществующему определению отвечают
    классом `STRATEGY_REQUEST_REJECTED` с реджект-кодом `STRATEGY_NOT_FOUND`
    (`StrategyNotFoundException`), чужой и несуществующий счёт на создании —
    одним текстом; клетки B1.5, B3.2, B3.3, B4.8, находка F-10
    `.claude/tests/cases/strategies.md`;
  - «Пустое условие истинно, и у клаузы фазы это даёт безусловную фазу» —
    отказ `STRATEGY_CONDITION_EMPTY` у шага и у клаузы фазы
    (`docs/rules/strategy-validation.md`), группа U35
    `.claude/tests/cases/strategy-definition-validation.md`;
  - «Тип `CANDLE_CLOSED` константно истинен, а объявлен защитой от
    look-ahead» — значение снято из перечня, валидатора, оценщика, api-модели
    и доков; раздел контракта — `docs/rules/strategy-condition-contract.md`
    §«Тип без операнда не объявляется»;
  - «Кроссовер с ценовым операндом ложен всегда: предыдущей цены в контексте
    нет» — `market-data` наполняет раскладку `previousPrices`
    (`docs/components/MarketPhaseService.md` §«Прошлое цены читается вместе с
    ценой момента и из своего ряда»), ядро несёт её в операнды, гейт покрытия
    мерит `StrategyCondition#pastPriceKeys()`; `strategies` отвергает
    пересечение с ценой без индикатора-пары и объёмный фильтр с неиндикаторным
    операндом.
- **Сняты пункты сборной секции** «Код, разошедшийся с построенным пачкой
  секций 2026-09-29»: описание буфера пробоя у `MarketStructureParamsApiModel`
  приведено к построенному (процент цены уровня); `swingLookbackBars` проверяет
  валидатор с кодом `STRATEGY_STRUCTURE_SWING_LOOKBACK_NOT_POSITIVE`; копия
  онбординговых статусов листинга у `InstrumentController` названа другим
  читателем (`docs/lifecycles/Instrument.md` §«Листинг наружу — статусы, на
  которых ведётся навес правил»).
- **Заведена** секция «Хвосты одиннадцатой пачки 2026-09-30»; в секцию
  «Элементы грамматики условий, объявленные перечнями и не исполняемые
  интерпретатором» добавлен пункт о непрочитанном поле `timeframe`.
- **Прогон** — `bash tools/reactor-test.sh --modules
  services/common/model/domain,services/common/strategy-engine,services/market-data,services/trading-core,services/strategies`:
  3752 теста, дефектов 0, с первого прогона; компиляция `tests` и
  `services/bff` против изменённых модулей (`test-compile`) чиста.
