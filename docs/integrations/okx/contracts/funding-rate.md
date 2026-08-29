# OKX contracts: funding rate (текущий и история)

## На какой вопрос отвечает этот файл

Каков контракт операций чтения funding rate SWAP.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Public Data → REST API», секции «Get funding rate», «Get
funding rate history»). При расхождении с офдоком побеждает офдок;
синхронизация — перевыкачка + дифф при каждом заходе интегратора по
источнику и по задаче «актуализируй»
(`.claude/processes/api-docs-completion.md`, канал чтения —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(прогон 3, поле-уровневая дистилляция).

## Статус использования

**В-6 / OKX-Q3 разрешены.** Funding в число
`resultProfit` идёт через positions-history (внутри готового net
`realizedPnl`) + bills (`subType` 173/174 — категорийная разбивка,
`account-bills.md`) — путь (1). `funding-rate-history` (`realizedRate`,
путь (2)) для числа **не ведётся**: ставки расчётных периодов без привязки к
позиции — годятся лишь для прогноза/сверки
(`docs/models/domain/aggregate/Deal.md`).

## GET /api/v5/public/funding-rate

Rate limit 10 req / 2 s по IP + Instrument ID. Query: `instId`
(обяз.; SWAP, или `ANY` — все перпы).

### Response (ключевые поля `data[0]`)

| Поле | Семантика |
|---|---|
| `fundingRate` | Прогнозная ставка ближайшего расчёта. Знак: положительная — лонги платят шортам; отрицательная — наоборот. Финальная может отличаться (см. `settFundingRate`). |
| `fundingTime` / `nextFundingTime` | Время ближайшего / следующего расчёта (ms). **Интервал определять разницей этих полей**: типично 8 ч, биржа может сжать до 6/4/2/1 ч (офдок). |
| `settFundingRate` / `settState` | Ставка текущего/прошлого расчётного цикла и его статус (`processing` / `settled`). |
| `minFundingRate` / `maxFundingRate` | Границы ставки. |
| `premium` | Премиальный индекс (формула в офдоке). |
| `method` | Механизм: `current_period` (/`next_period` — больше не поддерживается). |
| `formulaType` | Формула: `noRate` / `withRate`. |
| `interestRate`, `impactValue` | Параметры формулы (могут быть `""`). |
| `instType` / `instId`, `ts` | Идентификация и время данных. |

## GET /api/v5/public/funding-rate-history

Rate limit 10 req / 2 s по IP + Instrument ID. Глубина — 3 месяца.
Query: `instId` (обяз.), `after`/`before` — пагинация по
`fundingTime`, `limit` ≤ 400 (default 400).

Элемент: `instType`, `instId`, `fundingTime`, `fundingRate`
(прогнозная на тот период), **`realizedRate`** (фактическая),
`method`, `formulaType`.
