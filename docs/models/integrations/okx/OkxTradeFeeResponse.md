# OkxTradeFeeResponse (OKX fee rates)

## На какой вопрос отвечает этот файл

Какие поля у OKX trade-fee response — ответа со ставками комиссий
комиссионных групп аккаунта.

## Инвентарь полей

### Используемые (под `TradeFeeRate`)

Ставка берётся **только из `feeGroup[]`** — плоские ставки верхнего уровня
для FUTURES/SWAP помечены в офдоке deprecated (см. unused ниже).

| OKX field | Тип (raw) | Семантика |
|---|---|---|
| `feeGroup[].groupId` | string | id комиссионной группы — **ось группы**; вместе с `instType` образует ключ резолва ставки. Едет в снапшот **сырым** (`externalFeeGroupId`) |
| `feeGroup[].taker` | string-decimal | ставка taker-комиссии группы (знак источника: минус = комиссия, плюс = ребейт). При маппинге — **`× −1`**: знак снимается, ниже маппинга ставка есть издержка |
| `feeGroup[].maker` | string-decimal | ставка maker-комиссии группы; та же знаковая конвенция и тот же **`× −1`** при маппинге |
| `level` | string | комиссионный уровень аккаунта (например `Lv1`) — **часть значения группы**: его смена рождает новую строку `TradeFeeRate`, а не переписывает `level` на месте. Отсюда же и датчик: отвечает «из-за чего» скакнула ставка |
| `ts` | string-ms | время данных источника. Значением группы **не является** — метка ответа, обновляется на месте и строки не рождает (`docs/models/domain/other/TradeFeeRate.md`) |
| `instType` | string | эхо типа инструмента — **вторая ось группы** (одно и то же число `groupId` значит разное при разном `instType`). Едет в снапшот **сырым** (`externalInstrumentType`) |

**Ось резолва — пара (`instType`, `groupId`), не голый `groupId`.** Офдок Get
instruments: «instType and groupId should be used together to determine a
trading fee group».

**Обе половины ключа — сырые, не доменные проекции**.
Ключ группы — (`externalInstrumentType`, `externalFeeGroupId`), а не доменный
`instrumentType`: довод (коллизия `UNKNOWN`) — в
`docs/models/domain/other/TradeFeeRate.md`.

**Перечень групп не хардкодится.** Офдок сам предписывает не полагаться на
свой enum-список групп («actual return values shall prevail»), и список
внутри офдока неполон относительно его же примера (SPOT `BTC-USDT` →
`groupId="1"`, при том что Spot-перечень начинается с `3`) → матч
динамический, по значению `groupId` из ответа.

### Не используется runtime фазы 1 (отбрасывается на маппинге)

- **Плоские ставки верхнего уровня:** `maker` / `taker`, `makerU` / `takerU`,
  `makerUSDC` / `takerUSDC` — офдок помечает deprecated для FUTURES/SWAP и
  предписывает читать `feeGroup`. Наш контур — SWAP → не читаем.
- **`feeGroup[].elpMaker`** — ставка maker'а программы ELP/RPI (Enhanced
  Liquidity Provider → Retail Price Improvement). Лежит **внутри `feeGroup[]`**,
  то есть в структуре, которую мы читаем; не потребляется потому, что из
  группы берутся только `taker`/`maker`, а в ELP/RPI-программе мы **не
  состоим** → нам применимы organic base rates (инвариант organic-base-rates,
  `docs/integrations/okx/contracts/trade-fee.md`).
  ⚠ **Переименование в горизонте шага 7:** `elpMaker` → `rpiMaker` (ELP→RPI
  rebranding; офдок-upcoming: demo **2026-07-21**, прод **2026-07-28**,
  параллельные имена до **2026-10-31**). Нас **не гейтит** — поле unused,
  защитной механики под переименование не строим; имя зафиксировано здесь,
  чтобы следующий заход не принял `rpiMaker` за новое поле.
- **`delivery` / `exercise`** — ставки delivery (FUTURES) / exercise (OPTION);
  вне SWAP-контура. Знаковая конвенция у них обратная (положительное = ставка
  комиссии) — см. `trade-fee.md`.
- **`settle`** — EVENTS-only (settlement fee rate); EVENTS вне периметра.
- **`ruleType`, `category`, `fiat[]`** — прочее; `category` / `fiat` /
  `ruleType=pre_market` офдок помечает deprecated.

## Конвертация

Все числа приходят строками; numeric string → `BigDecimal`, `ts` (Unix ms) →
`OffsetDateTime` (конвенция типов времени проекта; `docs/rules/time-utc.md`).
Пустая/непарсящаяся ставка группы — **не** молчаливый `null`, а controlled
external error (`docs/models/mapping/TradeFeeRate.md`).

Знаковая конвенция `taker`/`maker`: **минус = комиссия, плюс = ребейт**
(офдок; совпадает со знаком `fee` в fills/bills). Конвенция **не выходит за
маппинг**: `× −1` в `docs/models/mapping/TradeFeeRate.md` переводит её в проектную нормаль «издержка». Инвентарь used/unused от этого не меняется — снятие знака
происходит после переноса поля.
