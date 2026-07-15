# TradeFeeRate — mapping между слоями

## На какой вопрос отвечает этот файл

Как ставка комиссии источника ложится на доменную `TradeFeeRate`.

## Контекст

Mapping-слой для `TradeFeeRate`. Доменная модель —
`docs/models/domain/other/TradeFeeRate.md`. Обновляет ставку —
`docs/components/InstrumentExternalRulesSyncJob.md`. Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`.

Текущие источники: **OKX**.

## Source-agnostic ядро

### Mapping-flow

```text
trade-fee REST -> raw OkxTradeFeeResponse -> IntegrationService validation
  -> TradeFeeRateMapper -> TradeFeeRateExternalSnapshot (по одному на группу)
  -> InstrumentExternalRulesSyncJob -> TradeFeeRate (persist)
```

Raw DTO не выходит за `IntegrationService` / adapter-layer. **Один ответ
источника → N снапшотов** (по числу групп в ответе), не один: ответ несёт
массив групп, а модель — по строке на группу
(`docs/models/domain/other/TradeFeeRate.md` §«Масштаб модели»).

### `TradeFeeRateExternalSnapshot` (транзитный)

| Snapshot field | Тип | Семантика |
|---|---|---|
| `externalInstrumentType` | `String` | сырой тип инструмента (ось группы) |
| `externalFeeGroupId` | `String` | сырой id комиссионной группы (ось группы) |
| `externalTakerFeeRate` | `String` | сырая ставка taker |
| `externalMakerFeeRate` | `String` | сырая ставка maker |
| `externalFeeLevel` | `String` | комиссионный уровень аккаунта (датчик тира) |
| `externalTs` | `OffsetDateTime` | время данных источника |

Транзитный снапшот, как `PositionCloseResultExternalSnapshot`, **не требует
отдельного `*ExternalSnapshot.md`** (нет самостоятельного persisted
содержания; `docs/models/externalSnapshot/README.md`).

### snapshot → `TradeFeeRate`

`externalInstrumentType` → доменная проекция `instrumentType`
(`InstrumentType`, неизвестное → `UNKNOWN`) — резолв **при материализации**,
как у `InstrumentExternalRules`. Остальные `external*` переносятся 1:1;
`exchangeId` проставляет вызывающий (синк знает биржу), не маппер.

**Запись — не маппинг.** Правило «значение изменилось → новая строка,
совпало → `modifiedAt`» (`docs/models/domain/other/TradeFeeRate.md` §Запись)
исполняет синк/DataService, не маппер: это доменное решение о версионировании,
а маппер только переносит данные (codestyle §Маппинг).

### Validation (структурная, до маппинга)

В `IntegrationService` источника:

- **`code == 0`**, `response != null`.
- **Ставки parseable numeric** — числа приходят строками; `taker`/`maker`
  группы заполнены и парсятся. Пустая/непарсящаяся ставка — controlled
  external error (`docs/rules/controlled-exchange-exceptions.md`), **не**
  молчаливый null: null-ставка = молчаливый выпад прогноза комиссии
  (`docs/models/domain/other/InstrumentExternalRules.md` §«Ставка комиссии»,
  null-политика).
- **`groupId` группы присутствует** — без него строку не к чему ключевать.

### Error policy

- **Temporary API problem** (timeout, 5xx): тик синка логирует и уходит;
  `modifiedAt` не двигается → возраст растёт → при исчерпании порога
  срабатывает холд биржи по несвежести
  (`docs/models/domain/other/TradeFeeRate.md` §«Свежесть → холд биржи»).
  Отдельного счётчика отказов нет.
- **Invalid response** (`code != 0`, ставки не парсятся): controlled external
  error; строка не пишется — последняя известная ставка **не затирается**.

## OKX

### `OkxTradeFeeResponse` → `TradeFeeRateExternalSnapshot`

Инвентарь полей — `docs/models/integrations/okx/OkxTradeFeeResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `feeGroup[].groupId` | `externalFeeGroupId` |
| `feeGroup[].taker` | `externalTakerFeeRate` |
| `feeGroup[].maker` | `externalMakerFeeRate` |
| `instType` | `externalInstrumentType` |
| `level` | `externalFeeLevel` |
| `ts` | `externalTs` (epoch millis → `OffsetDateTime`) |

**Ось резолва — пара (`instType`, `groupId`)**, не голый `groupId`: одно и то
же число значит разное при разном `instType`. Ключ группы инструмента приходит
`GET /api/v5/public/instruments` (`groupId` →
`InstrumentExternalRules.externalFeeGroupId`,
`docs/models/mapping/InstrumentExternalRules.md`).

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
- **Инвариант organic-base-rates:** запрос **без** `instId`/`instFamily`
  возвращает organic base rates — ставок market-maker incentive в нём не
  видно. Мы не участники программы → base rates корректны. **Вход в
  MM-программу требует пересмотра оси запроса**
  (`docs/decisions/pnl-finalization-mechanics.md` реш.4).

## Связи

- Доменная модель — `docs/models/domain/other/TradeFeeRate.md`.
- Native — `docs/models/integrations/okx/OkxTradeFeeResponse.md`.
- Контракт — `docs/integrations/okx/contracts/trade-fee.md`.
- Ключ группы на инструменте —
  `docs/models/mapping/InstrumentExternalRules.md`.
- Решение — `docs/decisions/pnl-finalization-mechanics.md` реш.4.
