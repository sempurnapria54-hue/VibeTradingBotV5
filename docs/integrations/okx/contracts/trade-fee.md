# OKX contracts: ставки комиссий

## На какой вопрос отвечает этот файл

Каков контракт операции чтения ставок комиссий аккаунта (`trade-fee`).

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Trading Account → REST API», секция «Get fee rates»). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md`, канал
чтения — `.claude/skills/integration-okx.md`). Последняя сверка:
**2026-07-14**.

## Статус использования

Используется с шага 7 (**В-7 активирован**): ставки `trade-fee` — источник
**прогнозной комиссии в риск-сайзинге**. **Дом ставки —
`TradeFeeRate`** (отдельная модель/таблица, **одна строка на группу**;
`docs/models/domain/other/TradeFeeRate.md`). На навесе инструмента
`InstrumentExternalRules` остаётся только **ключ группы**
(`externalFeeGroupId`), не ставка: ставка — атрибут комиссионного уровня
аккаунта, а не справочника инструмента. **Писатель ряда — владелец
биржевого счёта, не синк инструментов:** чтение ставок требует ключей, а
`market-data` ходит к площадке только публичными чтениями
(`docs/architecture/contracts.md` — дом перечня синхронных вызовов);
реестр приезжает с торговым ядром. Он дочитывает `trade-fee` **раз на тик по
`instType`** (не по инструменту) и пишет строку на группу; резолв ставки — по паре (`instType`, `groupId`), обе
половины ключа — **сырые значения источника**, не доменные проекции. **Поверхность чтения не двинулась** — калькуляторы берут ставку
прежним аксессором `InstrumentExternalRules.takerFeeRate`, без отдельного
поля контекста и exchange-вызова из калькулятора (N9,
`docs/rules/pnl-reconciliation.md` реш.4). Троп чтения навеса
**две** (`CalculationContext` у калькуляторов; прямая, через
`findByInstrumentId`, — у `RiskValidator`), поэтому аксессор гидрирует
**хранилищный слой** — `docs/components/InstrumentExternalRulesDataService.md`,
единственная граница domain ↔ persistence навеса, через которую проходят обе
тропы.

Фактические комиссии исполнения (для `resultProfit`) живут в
bills/positions-history (`account-bills.md`,
`docs/models/domain/aggregate/Deal.md`); `trade-fee` — ставки для
**прогноза** (сайзинг до входа) и сверки. Wiring — в коде сопровождения сделки.

Native-инвентарь полей (used/unused) —
`docs/models/integrations/okx/TradeFeeOkxResponse.md`; маппинг →
`docs/models/mapping/TradeFeeRate.md`.

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

Перечень `instType` выше — офдок; **наш контур фазы 1 — SWAP-only**, поэтому
вызов ровно один (`instType=SWAP`). FUTURES вынесен из контура до шага с
отдельными биржами — на ось запроса это влияет так: второго вызова
(`instType=FUTURES`) в фазе 1 нет.

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
`docs/models/integrations/okx/TradeFeeOkxResponse.md`.

### Перечень групп не хардкодим

Офдок содержит **внутреннюю нестыковку**: enum-список fee-групп неполон
относительно его же примера (SPOT `BTC-USDT` возвращает `groupId="1"`, при
том что Spot-перечень в списке начинается с `3`). Офдок сам снимает вопрос
ремаркой «actual return values shall prevail» → **перечень групп не
хардкодится**, матч динамический по значению `groupId` из ответа
(`docs/models/mapping/TradeFeeRate.md`).

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
  комиссии в ответе `trade-fee` не отражается ⇒ прогноз в этом случае
  **завышает** издержку. Это не «безопасная сторона»:
  завышенный прогноз сжимает риск-бюджет и даёт позицию меньше положенной —
  систематический недосайзинг; у издержек равный вред от занижения и
  завышения. Механизм учтён в форвард-фокусе искажений ожидаемости
  (отложено);
  наблюдаемость промо — RQ-3 (.5). Сверка прогноз↔`bills` может
  расходиться именно по этой причине.
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

**Дальше границы конвенция не течёт**. Знак снимается
при маппинге — `× −1` в per-source-секции
`docs/models/mapping/TradeFeeRate.md`; ниже
маппинга ставка есть **издержка** (комиссия положительна, ребейт отрицателен),
и `abs` в формулах не появляется. Довод —
`docs/models/domain/other/TradeFeeRate.md`.

Оговорка про `fee` в bills: там знак — **факт движения** и **не
нормализуется** (он участвует в арифметике `amount − externalFee = pnl`,
`docs/models/mapping/DealCashFlow.md`). Асимметрия сознательная: нормализуется прогнозная
**ставка**, не фактическое движение.
