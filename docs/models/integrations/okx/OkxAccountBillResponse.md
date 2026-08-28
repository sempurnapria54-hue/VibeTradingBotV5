# OkxAccountBillResponse (OKX account bill records)

## На какой вопрос отвечает этот файл

Какие поля у OKX bill response — одной записи денежного движения по
торговому аккаунту.

## Инвентарь полей

Сужение до used **зафиксировано** — при специфицировании структуры `DealCashFlow`
(`docs/models/domain/other/DealCashFlow.md`,
`docs/models/mapping/DealCashFlow.md`).

- **Гейтом `CODE` ограничение не является:** ни одно из названных полей
  не имеет назначенного потребителя, а суперсет used-набора под разбивку
  (`billId`, `type`, `subType`, `ts`, `balChg`, `fee`, `ccy`, `ordId`,
  `instId`) от их наличия не меняется.
- **Пока не снято, перечень «Не используется» читается как «не
  используется из известного», а не как закрытый список.**

### Используемые (под `DealCashFlow`)

| OKX field | Назначение → DealCashFlow |
|---|---|
| `billId` | id записи; **якорь пагинации** (`after`/`before`) + **дедуп/идемпотентность** → `externalBillId` (`UNIQUE`) |
| `type` | тип bill-записи → резолв `CashFlowCategory`; сырой → `externalType` |
| `subType` | подтип; funding `173` (expense) / `174` (income) → резолв `FUNDING`; сырой → `externalSubType`. Актуальный список — справочник OKX |
| `ts` | время bill-события (Unix ms) → `DealCashFlow.externalCreatedAt` |
| `balChg` | изменение баланса (знаковое) → `amount` |
| `fee` | комиссионная/rebate-компонента записи (знаковая: минус = комиссия, плюс = ребейт) → `externalFee` — **число** комиссии; категорию **не определяет**: `TRADE_FEE`/`REBATE`, как и остальные категории, резолвятся из `type`/`subType` (H9) |
| `ccy` | валюта движения (`USDT`) → `ccy` (обязательно — cross-ccy guard) |
| `ordId` | id ордера, если bill связан с ордером → `externalOrderId` (nullable) |
| `instId` | инструмент движения → `externalInstrumentId` (сырой, nullable). Не только фильтр матчинга: **ось предиката линковки** и колонка отбора |

**Почему `fee` в used.** Ранее `fee` отбрасывался с доводом «знак несёт уже `balChg`» —
верным **только** при гранулярности «отдельная fee-запись на событие». При
**комбинированном** trade-bill (`balChg = pnl + fee` одной записью) без
`fee` комиссионная компонента не извлекается вовсе: `balChg` несёт сумму, а
не компоненты. Native `fee` даёт компоненту **явно** → суперсет безопасен
для **суммы-сверки** (Σ`balChg` = net при любой гранулярности) и
**realized-слагаемого** (`balChg − fee`). Для **суммы комиссии**
(Σ`externalFee`) гранулярность-независимости нет: что несёт `fee` на
самостоятельной fee-записи — рантайм-вопрос (контур source-api,
`.claude/tests/source-api/okx/plan.md` **.5**, фикстура общая с.5;
`docs/models/mapping/DealCashFlow.md`). Категорию `fee`
не определяет — резолв категории идёт по `type`/`subType` (H9,
`docs/models/mapping/DealCashFlow.md`).

### Не используется runtime фазы 1 (отбрасывается на маппинге)

Разбивке `DealCashFlow` не нужны (число — net из positions-history; категория
и сумма — из used-полей выше):

- **Прочие денежные / балансовые:** `bal` (баланс после события), `pnl` (pnl
  события — заголовочное число берётся net'ом из positions-history, а
  `REALIZED_PNL`-слагаемое разбивки резолвится из `balChg` за вычетом `fee`;
  отдельного носителя `pnl` не требует).
- **Позиция:** `sz` (размер), `posBalChg` (изменение баланса позиции),
  `posBal` (баланс позиции после события).
- **Переводы / примечания:** `from`, `to`, `notes`.

## Конвертация

Все числа приходят строками; numeric → `BigDecimal`, `ts` (Unix ms) →
доменное время при парсинге.
