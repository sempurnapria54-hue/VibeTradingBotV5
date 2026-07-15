# OKX contracts: ставки комиссий

## На какой вопрос отвечает этот файл

Каков контракт OKX-операции чтения ставок комиссий аккаунта
(`trade-fee`): endpoint, поля, знаковая конвенция.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Trading Account → REST API», секция «Get fee rates»). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md`, канал
чтения — `.claude/skills/integration-okx.md`). Последняя сверка:
**2026-07-14** (прогон интегратора, поле-уровневая сверка против офдока —
`GAPS_CLOSE_3` шага 7, пробел H1).

## Статус использования

Используется с шага 7 (**В-7 активирован**): ставки `trade-fee` — источник
**прогнозной комиссии в риск-сайзинге** (G6, `GAPS_CLOSE_1` шага 7,
`docs/decisions/per-trade-risk-policy.md` §«Учёт комиссий»). **Дом ставки —
`TradeFeeRate`** (отдельная модель/таблица, **одна строка на группу**;
`docs/models/domain/other/TradeFeeRate.md`). На навесе инструмента
`InstrumentExternalRules` остаётся только **ключ группы**
(`externalFeeGroupId`), не ставка: ставка — атрибут комиссионного уровня
аккаунта, а не справочника инструмента. `InstrumentExternalRulesSyncJob`
дочитывает `trade-fee` **раз на тик по `instType`** (не по инструменту) и
пишет строку на группу; резолв ставки — по паре (`instType`, `groupId`).
Seam чтения не двинулся — калькуляторы получают ставку через уже
присутствующий `CalculationContext.instrumentExternalRules`, без отдельного
поля контекста и exchange-вызова из калькулятора (N9,
`docs/decisions/pnl-finalization-mechanics.md` реш.4). Фактические комиссии
исполнения (для `resultProfit`) живут в bills/positions-history
(`account-bills.md`, `docs/decisions/result-profit-source.md`); `trade-fee` —
ставки для **прогноза** (сайзинг до входа) и сверки. Wiring — шаг 7 CODE.

Native-инвентарь полей (used/unused) —
`docs/models/integrations/okx/OkxTradeFeeResponse.md`; маппинг →
`docs/models/mapping/TradeFeeRate.md` §OKX.

## GET /api/v5/account/trade-fee

Permission `Read`; rate limit 5 req / 2 s по User ID.

Query: `instType` (обяз.: SPOT/MARGIN/SWAP/FUTURES/OPTION/**EVENTS**),
`instId` (SPOT/MARGIN), `instFamily` (FUTURES/SWAP/OPTION),
`groupId` (взаимоисключим с `instId`/`instFamily`; маппинг
инструмент → fee group — через instruments endpoint).

**Наша ось запроса — группа, не инструмент:** один вызов
`trade-fee(instType=SWAP)` на тик даёт `feeGroup[]` по всем группам типа; N
вызовов на N инструментов не делаются (при лимите 5 req / 2 s это и дешевле,
и не размножает ставку по инструментам).

### Response (`data[0]`)

| Поле | Семантика |
|---|---|
| `level` | Fee tier аккаунта (например `Lv1`). |
| `feeGroup[]` | Группы ставок: `groupId`, `maker`, `taker`, `elpMaker`. Актуальный канонический источник ставок; `instType` + `groupId` определяют группу. Применимо в т. ч. к **EVENTS** (там `taker`/`maker` несут K1/K2-семантику). |
| `instType`, `ts` | Эхо типа и время данных. |
| `maker` / `taker`, `makerU` / `takerU`, `makerUSDC` / `takerUSDC` | Плоские ставки по типам маржи — помечены в офдоке deprecated (для FUTURES/SWAP читать `feeGroup`). |
| `delivery` / `exercise` | Ставки delivery (FUTURES) / exercise (OPTION). |
| `settle` | Settlement fee rate — **EVENTS-only**. |
| `ruleType`, `category`, `fiat[]` | Прочее; `category`/`fiat`/`ruleType=pre_market` — deprecated. |

Что из этого используется (used/unused, с обоснованием) —
`docs/models/integrations/okx/OkxTradeFeeResponse.md`.

### Перечень групп не хардкодим

Офдок содержит **внутреннюю нестыковку**: enum-список fee-групп неполон
относительно его же примера (SPOT `BTC-USDT` возвращает `groupId="1"`, при
том что Spot-перечень в списке начинается с `3`). Офдок сам снимает вопрос
ремаркой «actual return values shall prevail» → **перечень групп не
хардкодится**, матч динамический по значению `groupId` из ответа
(`docs/models/mapping/TradeFeeRate.md` §OKX).

### Инвариант organic-base-rates

Офдок (ремарка про market-maker incentive): указание `instId`/`instFamily`
возвращает ставки, применимые с учётом market-maker incentive; **без них
ответ несёт organic base rates** — ставок incentive-программы в нём не видно.
Ответ при этом валидный, просто не тот.

Наша ось запроса (`instType` без `instId`/`instFamily`) даёт именно organic
base rates. **Мы не участники программы → base rates корректны.** Условие
пересмотра: **вход в market-maker-программу требует пересмотра оси запроса**
`trade-fee` (иначе прогноз комиссии будет считаться по ставкам, которые к нам
уже не применяются). То же и для ELP/RPI (`elpMaker`/`rpiMaker` — не наша
ставка, пока не состоим).

### Прочие ремарки офдока (дрейф, учтён 2026-07-14)

- **«The Open API will not reflect zero-fee trading»** — промо нулевой
  комиссии в ответе `trade-fee` не отражается. Для прогноза сайзинга это
  безопасная сторона (прогноз консервативнее факта), но сверка
  прогноз↔`bills` может расходиться именно по этой причине.
- **Upcoming: `elpMaker` → `rpiMaker`** (ELP→RPI rebranding). Demo
  **2026-07-21**, прод **2026-07-28**, параллельные имена до **2026-10-31**.
  Поле лежит **внутри `feeGroup[]`** (не в плоской deprecated-шестёрке), то
  есть в структуре, которую мы читаем, — но мы берём из группы только
  `taker`/`maker`, поэтому переименование **нас не гейтит** и защитной
  механики под него не строим. Дата отмечена, т. к. прод-переключение попадает
  **внутрь горизонта шага 7**.

## Знаковая конвенция (офдок, критично для P&L)

`maker`/`taker`: **отрицательное значение = комиссия, положительное
= ребейт**. Исключение: `delivery`/`exercise` — положительные числа
как ставка комиссии. Совпадает со знаком `fee` в fills/bills
(отрицательный `fee` — списание).
