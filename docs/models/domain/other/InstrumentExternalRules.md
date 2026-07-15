# InstrumentExternalRules

## На какой вопрос отвечает этот файл

Что это за модель `InstrumentExternalRules`: структура, енумы,
персистентность, как обновляется.

## Назначение

`InstrumentExternalRules` — актуальные внешние правила инструмента,
полученные от биржи и сохранённые в БД как persisted snapshot. Не про
бизнес-цикл сделки, а хранимые справочные правила инструмента → модель
`other` (см. `.claude/decisions/models-core-vs-other.md`).

> **Материализуется на шаге 5** (риск-преконтроль — первый реальный
> потребитель ограничений инструмента; решение
> `docs/decisions/instrument-external-rules-materialization.md`, закрыт
> INSTR-Q1, ренейм не требуется). Справочные валюты инструмента
> (base/quote/settle) эта модель **не держит** — они приходят в
> транзиентном `InstrumentExternalSnapshot` (см.
> `docs/models/domain/core/Instrument.md`,
> `docs/models/mapping/Instrument.md`). Авторитетный источник биржевого
> **потолка плеча** для преконтроля — `externalMaxLeverage` этой модели
> (из OKX `lever`); одноимённое сырое значение на `Instrument`
> (`externalLeverage`, заведено на шаге 1) для преконтроля не
> авторитетно (устранение дубля — мелкая чистка). Нашего кэпа плеча
> нет (плечо связано лимитом риска,
> `docs/decisions/per-trade-risk-policy.md`).

Используется для:

- округления цены;
- округления размера;
- расчёта размера в контрактах;
- проверки min/max limits;
- проверки биржевого max leverage;
- проверки, можно ли торговать инструмент (`status`);
- **прогноза комиссии в риск-сайзинге** (`takerFeeRate()`; N9, шаг 7 —
  `docs/decisions/pnl-finalization-mechanics.md` реш.4). Сама ставка **на
  навесе не хранится** — здесь только **ключ группы**
  (`externalFeeGroupId`), по которому она резолвится; см. §«Ставка комиссии».
  Калькуляторам она по-прежнему доступна через уже присутствующий
  `CalculationContext.instrumentExternalRules` — без отдельного поля контекста
  и без exchange-вызова из калькулятора.

Правила меняются редко, поэтому хранение актуального snapshot в БД
оправдано. Обновляется `InstrumentExternalRulesSyncJob` (см.
`docs/components/InstrumentExternalRulesSyncJob.md`) — на шаге 7 он вторым
источником читает `trade-fee` **раз на тик** (не на инструмент) и пишет ставки
в `TradeFeeRate`, а сюда — только ключ группы. OKX-маппинг —
`docs/models/mapping/InstrumentExternalRules.md`.

## Структура

Java-класс. Собственного `id` у класса нет — единственный ключ
`instrumentId` (логический ключ навеса: модель хранится JSONB-навесом на
строке `Instrument`, не отдельной таблицей; audit-поля наследуются от
строки-владельца — см. §Персистентность).

| Поле | Тип | Назначение |
|---|---|---|
| `instrumentId` | `Long` | Внутренний ID инструмента-владельца (ключ навеса). |
| `instrumentType` | `InstrumentType` | Нормализованный тип инструмента. |
| `contractType` | `ContractType` | Нормализованный тип контракта. |
| `status` | `Status` | Нормализованный статус инструмента. |
| `externalInstrumentType` | `String` | Сырой тип инструмента биржи. |
| `externalInstrumentId` | `String` | ID инструмента на бирже. |
| `externalContractType` | `String` | Сырой тип контракта. |
| `externalContractValue` | `String` | Стоимость контракта (`ctVal`). |
| `externalContractValueCurrency` | `String` | Валюта стоимости контракта. |
| `externalTickSize` | `String` | Шаг цены (`tickSize`). |
| `externalLotSize` | `String` | Шаг размера (`lotSize`). |
| `externalMinSize` | `String` | Минимальный размер (`minSize`). |
| `externalMaxLimitSize` | `String` | Максимальный размер limit-ордера. |
| `externalMaxMarketSize` | `String` | Максимальный размер market-ордера. |
| `externalMaxTriggerSize` | `String` | Максимальный размер trigger-ордера. |
| `externalMaxStopSize` | `String` | Максимальный размер stop-ордера. |
| `externalMaxLeverage` | `String` | Биржевой максимум плеча. |
| `externalFeeGroupId` | `String` | **Ключ комиссионной группы** инструмента (OKX `groupId`, из `/public/instruments`). Не ставка, а ось её резолва: пара (`instrumentType`, `externalFeeGroupId`) → строка `TradeFeeRate`. См. §«Ставка комиссии». |
| `externalState` | `String` | Сырой статус инструмента биржи. |

`external*`-поля хранят сырые строковые значения биржи; нормализованные
`instrumentType` / `contractType` / `status` — доменные проекции для
runtime-логики.

## Ставка комиссии — ключ здесь, значение в `TradeFeeRate`

**На навесе живёт ключ группы (`externalFeeGroupId`), не ставка.** Значение
ставки — в `docs/models/domain/other/TradeFeeRate.md` (одна строка на
комиссионную группу, отдельная таблица с историей).

**Почему не полем навеса.** Ставка — атрибут **комиссионного уровня аккаунта**
(объём/VIP-тир), а не справочника инструмента: она меняется по своей оси и на
своём такте. Копия ставки на каждом инструменте — заготовка под расхождение:
при смене тира N копий обновляются N операциями, часть может отстать → часть
инструментов торгует по старой ставке, и расхождение ничем не выражено. Одна
строка на группу делает смену тира одной записью. Подробный довод и механика
записи/свежести — `docs/models/domain/other/TradeFeeRate.md`.

**Seam при этом не двинулся.** `SizeCalculator`/`RiskValidator` читают ставку
там же, где и раньше — через `CalculationContext.instrumentExternalRules`
(нового поля контекста и exchange-вызова из калькулятора не нужно, N9).
Изменилось только **место хранения**, не поверхность чтения.

**Гидрация.** Ставка подгружается в модель **при материализации**
спец-запросом по `externalFeeGroupId` (codestyle §Вложенность и rich-модели:
если для проверки нужны связанные сущности — грузим их в эту модель
спец-запросом, а метод модели работает со своими данными; не тащим значение
параметром через сервис). Аксессоры `takerFeeRate()`/`makerFeeRate()` —
поверхность чтения, делегируют в подгруженный `TradeFeeRate`; **на навесе
ставка не персистится**.

**Null-политика.** Ставка не резолвится (нет `externalFeeGroupId` либо нет ни
одной строки `TradeFeeRate` по группе) → `takerFeeRate()` = `null` → это
**реджект** `RiskValidator` (`FEE_RATE_UNAVAILABLE`), **не** тихий пропуск
прогноза комиссии и **не** fallback-число из конфига (подставленное число
выглядит фактом, не будучи им; заниженная ставка → позиция больше положенной —
та же болезнь, что H6/H2). Синк при этом последнюю известную ставку **не
затирает** (IGNORE-null). См. `docs/components/RiskValidator.md`,
`docs/decisions/per-trade-risk-policy.md` §«Учёт комиссий».

## Rich-модель (методы)

- `isLive()` — `Boolean`, инструмент торгуем (`status == LIVE`);
- `hasSizingSpecs()` — `Boolean`, есть валидный набор для расчёта
  размера в контрактах (`ctVal`/`lotSz`/`minSz` положительны);
- числовые аксессоры сырых строк (пусто/нечисловое → `null`):
  `contractValue()` (`ctVal`), `tickSize()` (`tickSz`), `lotSize()`
  (`lotSz`), `minSize()` (`minSz`), `maxLimitSize()` (`maxLmtSz`),
  `maxMarketSize()` (`maxMktSz`), `maxLeverage()` (`lever`);
- `takerFeeRate()` / `makerFeeRate()` — `BigDecimal`, ставка комиссии из
  подгруженного `TradeFeeRate` (не с навеса; taker — прогноз комиссии в
  сайзинге, N9). Не резолвится → `null` → реджект (см. §«Ставка комиссии»).

Числовых аксессоров `maxTriggerSize()` / `maxStopSize()` **нет**: сырые
`externalMaxTriggerSize` / `externalMaxStopSize` хранятся, но проверками
фазы 1 не потребляются (числового аксессора им не заводили — по
потребности).

## Енумы

### `Status`
`LIVE`, `SUSPEND`, `PREOPEN`, `EXPIRED`, `TEST`, `UNKNOWN`.
Нормализованный статус инструмента; источник — `externalState` (OKX
`state`, то же сырое значение, что и `Instrument.externalStatus` с шага 1).
Используется для проверки торгуемости инструмента (`INSTRUMENT_NOT_LIVE`).

### `InstrumentType`
`SWAP` (бессрочный своп / perpetual), `FUTURES` (фьючерс с датой
экспирации), `SPOT`, `MARGIN` (для текущего бота не используется),
`OPTION` (для текущего бота не используется), `UNKNOWN` (тип не удалось
нормализовать).

### `ContractType`
`LINEAR`, `INVERSE`, `UNKNOWN` (тип не удалось нормализовать).

## InstrumentExternalRulesExternalSnapshot (boundary)

Выход маппера из client-модели биржи: external-поля модели до записи в
БД (`raw-exchange-dto-boundary.md`). Поля совпадают с `external*`-полями
модели (`externalInstrumentType` … `externalState`, включая
`externalFeeGroupId`), без доменных enum-полей
`instrumentType`/`contractType`/`status` — они резолвятся уже
при материализации `InstrumentExternalRules`. Сырой OKX DTO за
`IntegrationService` не выходит; конкретный OKX-маппинг —
`docs/models/mapping/InstrumentExternalRules.md`.

Ставки комиссии в этом снапшоте **нет**: она приходит другим эндпоинтом и
едет своим снапшотом в `TradeFeeRate` (`docs/models/mapping/TradeFeeRate.md`).
Один снапшот — один источник.

## Персистентность

Хранится **JSONB-навесом на строке владельца** (`instruments`), один
актуальный набор правил на инструмент — по дефолту правила персистентности
(`docs/rules/persistence-representation.md`): FK-ссылок на rules из других
мест нет, реляционная строка пользы не несёт. Собственной таблицы/`id` у
модели нет; доступ — только через `Instrument`. Единственный ключ
`instrumentId` в структуре выше — логический (ключ навеса/владельца), не
отдельная таблица. Audit-поля наследуются от строки-владельца. Обновляется только через
`InstrumentExternalRulesSyncJob` по REST (на первом этапе без WebSocket).
Обоснование — `docs/decisions/instrument-external-rules-materialization.md`.

**Ставка комиссии в навес не персистится** — только ключ группы
`externalFeeGroupId`. Значение живёт реляционной строкой в `trade_fee_rates`
(`docs/models/domain/other/TradeFeeRate.md` §Персистентность): у ставки своя
ось (группа × тир), своя история и свой измеритель свежести — навесу
инструмента этого не выразить. Разные масштабы хранения — разные носители,
несмотря на то что синк у них один.
