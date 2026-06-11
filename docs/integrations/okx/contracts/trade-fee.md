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
2026-06-11 (прогон 3, поле-уровневая дистилляция).

## Статус использования

Не используется. Форвард-кандидат **В-7** (шаг 7, P&L): точность
комиссий в расчёте результата сделки. Фактические комиссии исполнения
живут в fills/bills (`fills.md`, `account-bills.md`); `trade-fee` —
ставки для прогноза/сверки.

## GET /api/v5/account/trade-fee

Permission `Read`; rate limit 5 req / 2 s по User ID.

Query: `instType` (обяз.: SPOT/MARGIN/SWAP/FUTURES/OPTION),
`instId` (SPOT/MARGIN), `instFamily` (FUTURES/SWAP/OPTION),
`groupId` (взаимоисключим с `instId`/`instFamily`; маппинг
инструмент → fee group — через instruments endpoint).

### Response (`data[0]`)

| Поле | Семантика |
|---|---|
| `level` | Fee tier аккаунта (например `Lv1`). |
| `feeGroup[]` | Группы ставок: `groupId`, `maker`, `taker`, `elpMaker`. Актуальный канонический источник ставок; `instType` + `groupId` определяют группу. |
| `instType`, `ts` | Эхо типа и время данных. |
| `maker` / `taker`, `makerU` / `takerU`, `makerUSDC` / `takerUSDC` | Плоские ставки по типам маржи — помечены в офдоке deprecated (для FUTURES/SWAP читать `feeGroup`). |
| `delivery` / `exercise` | Ставки delivery (FUTURES) / exercise (OPTION). |
| `ruleType`, `category`, `fiat[]` | Прочее; `category`/`fiat`/`ruleType=pre_market` — deprecated. |

## Знаковая конвенция (офдок, критично для P&L)

`maker`/`taker`: **отрицательное значение = комиссия, положительное
= ребейт**. Исключение: `delivery`/`exercise` — положительные числа
как ставка комиссии. Совпадает со знаком `fee` в fills/bills
(отрицательный `fee` — списание).
