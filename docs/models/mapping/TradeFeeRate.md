# TradeFeeRate — mapping между слоями

## На какой вопрос отвечает этот файл

Как ставка комиссии источника переходит в `TradeFeeRate`.

## Source-agnostic ядро

### Mapping-flow

```text
trade-fee REST -> raw TradeFeeOkxResponse -> IntegrationService validation
  -> TradeFeeRateMapper -> TradeFeeRateExternalSnapshot (по одному на группу)
  -> синк владельца счёта -> TradeFeeRate (persist)
```

Raw DTO не выходит за `IntegrationService` / adapter-layer. **Один ответ
источника → N снапшотов** (по числу групп в ответе), не один: ответ несёт
массив групп, а модель — по строке на группу
(`docs/models/domain/other/TradeFeeRate.md`).

### `TradeFeeRateExternalSnapshot` (транзитный)

| Snapshot field | Тип | Семантика |
|---|---|---|
| `externalInstrumentType` | `String` | сырой тип инструмента (ось группы) |
| `externalFeeGroupId` | `String` | сырой id комиссионной группы (ось группы) |
| `externalTakerFeeRate` | `String` | ставка taker как **издержка** (знак источника уже снят, см.) |
| `externalMakerFeeRate` | `String` | ставка maker как **издержка** (то же) |
| `externalFeeLevel` | `String` | комиссионный уровень аккаунта (датчик тира) |
| `externalModifiedAt` | `OffsetDateTime` | время данных источника |

Транзитный снапшот, как `PositionCloseResultExternalSnapshot`, **не требует
отдельного `*ExternalSnapshot.md`** (нет самостоятельного persisted
содержания; `.claude/rules/structure.md`).

### snapshot → `TradeFeeRate`

`externalInstrumentType` → доменная проекция `instrumentType`
(`InstrumentType`, неизвестное → `UNKNOWN`) — резолв **при материализации**,
как у `InstrumentExternalRules`. Остальные `external*` переносятся 1:1;
`exchangeAccountId` проставляет вызывающий (синк знает счёт), не маппер.

**Проекция `instrumentType` — не ось резолва.** Ключ группы —
пара **сырых** (`externalInstrumentType`, `externalFeeGroupId`)
(`docs/models/domain/other/TradeFeeRate.md`): именно потому,
что `UNKNOWN` схлопывает разные нераспознанные сырые типы в одно значение,
ключевать группу проекцией нельзя — две группы источника столкнулись бы в
одном ключе. Проекция остаётся для runtime-логики.

`refreshCount` маппером **не переносится** — его ведёт синк (см.).

**Запись — не маппинг.** Правило «значение группы изменилось → новая строка,
совпало → инкремент `refreshCount` + обновление `externalModifiedAt` последней»
(`docs/models/domain/other/TradeFeeRate.md`) исполняет
синк/DataService, не маппер: это доменное решение о версионировании, а маппер
только переносит данные (codestyle).

### Validation (структурная, до маппинга)

В `IntegrationService` источника:

- **`code == 0`**, `response != null`.
- **Ставки parseable numeric** — числа приходят строками; `taker`/`maker`
  группы заполнены и парсятся. Пустая/непарсящаяся ставка — controlled
  external error (`docs/rules/controlled-exchange-exceptions.md`), **не**
  молчаливый null: null-ставка = молчаливый выпад прогноза комиссии
  (`docs/models/domain/other/InstrumentExternalRules.md`,
  null-политика).
- **`groupId` группы присутствует** — без него строку не к чему ключевать.

### Error policy

- **Temporary API problem** (timeout, 5xx): тик синка логирует и уходит;
  строка не подтверждается → `refreshCount` не инкрементится → `modifiedAt`
  не двигается → возраст растёт → при исчерпании порога срабатывает **холд
  инструментов группы** по несвежести
  (`docs/models/domain/other/TradeFeeRate.md`). Отдельного счётчика отказов нет — довод см. там же,.
- **Invalid response** (`code != 0`, ставки не парсятся): controlled external
  error; строка не пишется — последняя известная ставка **не затирается**.

**Механизм «не затирается» — отсутствие записи, не IGNORE-null**. Последняя известная ставка сохраняется потому, что на
отказе **не пишется ничего**: история append-only, актуальная = последняя
строка по `createdAt` — она просто остаётся последней и стареет.
`updateFromSnapshot` с `nullValuePropertyMappingStrategy = IGNORE` — механизм
**навеса** (перезапись полей строки-владельца), к таблице с историей
неприменим: перезаписывать нечего. Прежняя атрибуция («сохраняет IGNORE-null»)
— рудимент редакции «ставка лежит на навесе инструмента», снятой вместе с
переездом дома ставки. **Намерение не менялось** и торгово верно: известная
несвежая ставка строго информативнее `null` — но именно поэтому её
устаревание обязано быть **видимым** (холд по возрасту), а не тихо
подставляться в сайзинг.

## OKX

### `TradeFeeOkxResponse` → `TradeFeeRateExternalSnapshot`

Инвентарь полей — `docs/models/integrations/okx/TradeFeeOkxResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `feeGroup[].groupId` | `externalFeeGroupId` |
| `feeGroup[].taker` | `externalTakerFeeRate` (**× −1**, см.) |
| `feeGroup[].maker` | `externalMakerFeeRate` (**× −1**, там же) |
| `instType` | `externalInstrumentType` |
| `level` | `externalFeeLevel` |
| `ts` | `externalModifiedAt` (epoch millis → `OffsetDateTime`) |

**Ось резолва — пара (`instType`, `groupId`)**, не голый `groupId`: одно и то
же число значит разное при разном `instType`. **Полный ключ строки —
тройка** с `exchangeAccountId` (`docs/models/domain/other/TradeFeeRate.md`): счёт подставляет вызывающий, в источнике его нет, поэтому
на оси маппинга речь о паре. Ключ группы инструмента приходит
`GET /api/v5/public/instruments` (`groupId` →
`InstrumentExternalRules.externalFeeGroupId`,
`docs/models/mapping/InstrumentExternalRules.md`).

### Знак ставки — снимается здесь

**Обе ставки группы умножаются на −1 при переносе в снапшот.** OKX-конвенция:
«отрицательное значение = комиссия, положительное = ребейт»
(`docs/integrations/okx/contracts/trade-fee.md`).
Проектная нормаль — **издержка**: комиссия положительна, ребейт отрицателен.
`× −1` переводит одно в другое.

**Носитель — строка, поэтому негация не «сама собой»**.
`externalTakerFeeRate`/`externalMakerFeeRate` — `String` и в снапшоте, и на
модели (сырые ставки приходят строками, нативного дока). Значит
`× −1` — не арифметика над полем, а **шаг маппинга**: строка парсится в
`BigDecimal`, отрицается и сериализуется обратно в строку носителя. Место —
per-source-секция маппера (доменное решение о конвенции здесь не
принимается, переносится только значение, codestyle); непарсящаяся
ставка до негации не доходит — это controlled external error.
Числовые аксессоры модели (`takerFeeRate`) парсят **уже нормализованную**
строку и `abs` не делают.

**Это место — единственное**. Конвенция знака —
свойство **источника**, поэтому и снимается в per-source-секции маппинга:
другая площадка отдаст ставку в своей конвенции и получит здесь же свой
множитель, а всё, что ниже маппинга (домен, аксессоры, формулы риска), про
знак источника не знает и `abs` не делает. Довод и закрываемый отказ —
`docs/models/domain/other/TradeFeeRate.md`.

**Ребейт не теряется.** `× −1` (а не `abs`) сохраняет различимость: ребейт
уезжает отрицательной издержкой и в формуле `+ commissions` корректно
**уменьшает** убыток на стопе. `abs` превратил бы его в издержку — тихая
ошибка в консервативную сторону, но всё равно ошибка. В фазе 1 taker-ребейт
не наблюдаем (мы не в MM/ELP-программах —,
инвариант organic-base-rates), поэтому практически taker-издержка
положительна; конструкция от этого не зависит.

**Перечень групп не хардкодится.** Офдок OKX сам предписывает не полагаться на
enum-список групп («actual return values shall prevail»; список внутри офдока
неполон относительно его же примера) → матч динамический, по значению
`groupId` из ответа.

**Флэт-ставки не читаем.** `maker`/`taker`, `makerU`/`takerU`,
`makerUSDC`/`takerUSDC` верхнего уровня — deprecated для FUTURES/SWAP; ставка
берётся **только** из `feeGroup[]`
(`docs/integrations/okx/contracts/trade-fee.md`).

### OKX validation notes

- **Ось запроса — группа, не инструмент:** один вызов
  `trade-fee(instType=SWAP)` на тик возвращает `feeGroup[]` по группам; N
  вызовов на N инструментов не нужно (rate limit 5 req / 2 s по User ID).
  Покрытие ответа (все ли наши `groupId` в нём) — **рантайм-вопрос RQ-1**
  (`.claude/tests/source-api/okx/plan.md`).
  **Частичное покрытие — определённый исход, не пробел спеки.** Группа, не
  пришедшая в ответе, не подтверждается → её строка не инкрементится →
  стареет → по исчерпании порога холдятся **инструменты этой группы**, и
  только они (`docs/models/domain/other/TradeFeeRate.md`). Свежесть измеряется **по группе**, поэтому вопрос
  «чей возраст берём при N группах» не возникает. RQ-1 проверяет **посылку**
  дизайна (покрытие полное ⇒ холд не срабатывает на здоровой бирже), а не
  добирает недостающую спеку.
- **Инвариант organic-base-rates:** запрос **без** `instId`/`instFamily`
  возвращает organic base rates — ставок market-maker incentive в нём не
  видно. Мы не участники программы → base rates корректны. **Вход в
  MM-программу требует пересмотра оси запроса**
  (`docs/rules/pnl-reconciliation.md` реш.4).

## Связи

- Доменная модель — `docs/models/domain/other/TradeFeeRate.md`.
- Native — `docs/models/integrations/okx/TradeFeeOkxResponse.md`.
- Контракт — `docs/integrations/okx/contracts/trade-fee.md`.
- Ключ группы на инструменте —
  `docs/models/mapping/InstrumentExternalRules.md`.
- Решение — `docs/rules/pnl-reconciliation.md` реш.4.
