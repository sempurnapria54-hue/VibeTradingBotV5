# OkxAccountBillResponse (OKX account bill records)

## На какой вопрос отвечает этот файл

Какие поля у OKX bill response — одной записи денежного движения по
торговому аккаунту.

## Контекст

Raw OKX response endpoint'ов `GET /api/v5/account/bills` (последние 7
дней) и `GET /api/v5/account/bills-archive` (последние 3 месяца).

В отличие от fills (факт исполнения ордера) bills показывают **изменение
денег на аккаунте**: realized PnL, комиссии, rebate, funding fee и
прочие cashflow-события. В расчёте `Deal.resultProfit` bills дают
**категорийную разбивку** (торговая комиссия / funding / rebate /
ликвидационный штраф) и **сверку** — само заголовочное число берётся
net'ом из positions-history (`realizedPnl`), **не** из `sum(bills)`
(`docs/decisions/result-profit-source.md`).

Доменный носитель разбивки — `DealCashFlow` (**OKX-Q3 закрыт** на
`GAPS_CLOSE_1` шага 7; `docs/decisions/result-profit-source.md`); структура
`DealCashFlow` и маппинг bills → домен —
`docs/models/domain/other/DealCashFlow.md`,
`docs/models/mapping/DealCashFlow.md`. Контракт endpoint'ов и поток
применения — `docs/integrations/okx/contracts/account-bills.md`.

Raw OKX DTO не выходит за adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`).

## Инвентарь полей

Сужение до used **зафиксировано** (шаг 7, `GAPS_CLOSE_2`; ранее было отложено
на стадию 2 / `DOCS_CHECK_2`) — при специфицировании структуры `DealCashFlow`
(`docs/models/domain/other/DealCashFlow.md`,
`docs/models/mapping/DealCashFlow.md`).

### Используемые (под `DealCashFlow`)

| OKX field | Назначение → DealCashFlow |
|---|---|
| `billId` | id записи; **якорь пагинации** (`after`/`before`) + **дедуп/идемпотентность** → `externalBillId` (`UNIQUE`) |
| `type` | тип bill-записи → резолв `CashFlowCategory`; сырой → `externalType` |
| `subType` | подтип; funding `173` (expense) / `174` (income) → резолв `FUNDING`; сырой → `externalSubType`. Актуальный список — справочник OKX |
| `ts` | время bill-события (Unix ms) → `DealCashFlow.externalCreatedAt` (конвенция `Auditable`, H25 `GAPS_CLOSE_7`) |
| `balChg` | изменение баланса (знаковое) → `amount` |
| `fee` | комиссионная/rebate-компонента записи (знаковая: минус = комиссия, плюс = ребейт) → `externalFee` — **число** комиссии; категорию **не определяет**: `TRADE_FEE`/`REBATE`, как и остальные категории, резолвятся из `type`/`subType` (H9) |
| `ccy` | валюта движения (`USDT`) → `ccy` (обязательно — cross-ccy guard) |
| `ordId` | id ордера, если bill связан с ордером → `externalOrderId` (nullable) |

**Почему `fee` в used (шаг 7, `GAPS_CLOSE_3` H2; уточнено `GAPS_CLOSE_5`
H14/H15).** Ранее `fee` отбрасывался с доводом «знак несёт уже `balChg`» —
верным **только** при гранулярности «отдельная fee-запись на событие». При
**комбинированном** trade-bill (`balChg = pnl + fee` одной записью) без
`fee` комиссионная компонента не извлекается вовсе: `balChg` несёт сумму, а
не компоненты. Native `fee` даёт компоненту **явно** → суперсет безопасен
для **суммы-сверки** (Σ`balChg` = net при любой гранулярности) и
**realized-слагаемого** (`balChg − fee`). Для **суммы комиссии**
(Σ`externalFee`) гранулярность-независимости нет: что несёт `fee` на
самостоятельной fee-записи — рантайм-вопрос (контур source-api,
`.claude/tests/source-api/okx/plan.md` **§AG3.5**, фикстура общая с §AG1.5;
`docs/models/mapping/DealCashFlow.md` §«Число комиссии»). Категорию `fee`
не определяет — резолв категории идёт по `type`/`subType` (H9,
`docs/models/mapping/DealCashFlow.md` §«Резолв категории»).

### Не используется runtime фазы 1 (отбрасывается на маппинге)

Разбивке `DealCashFlow` не нужны (число — net из positions-history; категория
и сумма — из used-полей выше):

- **Прочие денежные / балансовые:** `bal` (баланс после события), `pnl` (pnl
  события — заголовочное число берётся net'ом из positions-history, а
  `REALIZED_PNL`-слагаемое разбивки резолвится из `balChg` за вычетом `fee`;
  отдельного носителя `pnl` не требует).
- **Инструмент / режим** (инструмент и валюта берутся для линковки из окна
  сделки, `instId`/`ccy` матчинга — на уровне контракта, не тела bill):
  `instType`, `instId` (участвует в матчинге как фильтр, в поле не пишется),
  `mgnMode`.
- **Позиция:** `sz` (размер), `posBalChg` (изменение баланса позиции),
  `posBal` (баланс позиции после события).
- **Переводы / примечания:** `from`, `to`, `notes`.

## Конвертация

Все числа приходят строками; numeric → `BigDecimal`, `ts` (Unix ms) →
доменное время при парсинге.
