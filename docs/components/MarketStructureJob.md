# MarketStructureJob

## На какой вопрос отвечает этот файл

Кто считает структуру рынка (компонент-job): что делает, что не делает.

## Назначение

`MarketStructureJob` заранее готовит уровни рынка и сохраняет
`MarketStructure` / `MarketPriceLevel` (см.
`docs/models/domain/other/MarketStructure.md`). Настройки —
`StrategyMarketStructureSetting`; основной источник данных — закрытые
свечи; дополнительный — готовые `IndicatorValue` (ER — тренд/шум; ATR —
толеранс кластеризации, D3), адресуемые «мягкими» ключами настройки
`efficiencyRatioKey` / `atrKey` (fork-A). Вычисление структуры делегирует
`docs/components/MarketStructureResolver.md` — job **тонкий**.

Данные нужны для входов от диапазона, grid, SL за структурный уровень,
breakout-условий и сопровождения позиции.

## Делает

- читает стратегии **всех статусов кроме `DELETED`** и их
  `StrategyMarketStructureSetting` (перечень — как в правиле свежести,
  `docs/rules/market-data-freshness.md`);
- читает закрытые свечи окна; по ключам `efficiencyRatioKey` / `atrKey`
  извлекает готовые ER/ATR-скаляры из `IndicatorValue` настроек **того же
  контейнера** и подаёт их резолверу (fork-A);
- зовёт `MarketStructureResolver.resolve(window, efficiencyRatio, atr,
  params)` — тот выводит `type`, `levels`, `breakoutEvent`, `confirmedAt`,
  окно (семантика — `MarketStructure.md` §Семантика классификации;
  уровни/пробой по свечам сам не ищет);
- сохраняет `MarketStructure` и `MarketPriceLevel`.

**Объявленный вход не готов → `UNKNOWN` (на стороне job).** Если ключ
ER/ATR объявлен, но готового/свежего значения нет — job **не** зовёт
резолвер: пишет консервативный `UNKNOWN`-результат окна (не proxy), чтобы
потребитель не торговал по недосчитанной структуре. Необъявленный ключ
(`null`) → резолвер сам идёт в прокси / fallback. Различие держит job;
резолвер видит только скаляр или `null` (fork-A —
`docs/decisions/derived-market-data-code-increments.md`).

Job — тонкий: классификацию структуры держит `MarketStructureResolver`,
готовые индикаторы считает `IndicatorJob`.

## Не делает

- не создаёт сделку;
- не ставит ордера;
- не переносит SL;
- не исполняет команды.

## Идемпотентность

Считает по закрытым свечам, уникальность `UNIQUE(instrument_id,
config_id, window_end_at)` (ключ по идентичности считаемого через реестр
конфигураций — см.
`docs/decisions/market-data-result-identity-keying.md`). Если структура
сломалась — сохраняет новый результат (например, `type = UNKNOWN`), а не
правит старый. В рамках тика расчёт дедупится по `instrumentId:configId`
(одна конфигурация считается раз, шарится).

**Краевой случай (открыт, STRUCT-Q2).** Дедуп по `config_id` означает:
две настройки с одинаковыми `timeframe + params`, но разными
`efficiencyRatioKey` / `atrKey` делят `config_id` — первый обработанный
писатель выигрывает, второй читает структуру, посчитанную по другому
ER/ATR-входу. Ключи входов в идентичность не входят; разрешение —
`docs/decisions/derived-market-data-code-increments.md` §Что осталось
открытым, `.claude/work/questions/open-questions.md` §STRUCT-Q2.

**Checkpoint — производный, отдельного состояния нет.** «Докуда
посчитано» = `max(window_end_at)` по таблице результатов для
(`instrument_id` + `config_id`); «докуда считать» = время закрытия
последней закрытой свечи в группе. Отдельная persisted checkpoint-модель
не заводится.
