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

**Клейм полноты перечня ограничен и ограничение названо** (B10
`DOCS_CHECK_20`). Разбиение used/unused закрыто **над тем набором полей,
который был известен на `GAPS_CLOSE_2`**. Собственный контракт-док
источника называет сверх него `px`, `execType`, `interest`, `tag`,
`fillTime`, `tradeId`, `clOrdId` и `fill*`-семейство
(`docs/integrations/okx/contracts/account-bills.md` §«Deep-архив»,
состав колонок CSV) — присутствуют ли они в **JSON**-ответе, не
проверено. Снятие — кейсом контура `.claude/tests/source-api/okx/plan.md`
§AG3.6 (грунт, гоняется на фикстуре §AG1.5).

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
| `ts` | время bill-события (Unix ms) → `DealCashFlow.externalCreatedAt` (конвенция `Auditable`, H25 `GAPS_CLOSE_7`) |
| `balChg` | изменение баланса (знаковое) → `amount` |
| `fee` | комиссионная/rebate-компонента записи (знаковая: минус = комиссия, плюс = ребейт) → `externalFee` — **число** комиссии; категорию **не определяет**: `TRADE_FEE`/`REBATE`, как и остальные категории, резолвятся из `type`/`subType` (H9) |
| `ccy` | валюта движения (`USDT`) → `ccy` (обязательно — cross-ccy guard) |
| `ordId` | id ордера, если bill связан с ордером → `externalOrderId` (nullable) |
| `instId` | инструмент движения → `externalInstrumentId` (сырой, nullable). Не только фильтр матчинга: **ось предиката линковки** и колонка отбора (H11 `DOCS_CHECK_11`; переобосновано B4b `DOCS_CHECK_20`) |

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
- **Инструмент / режим:** `instType`, `mgnMode`.
  **`instId` в used переведён** (H11 `DOCS_CHECK_11`; довод
  переобоснован B4b `DOCS_CHECK_20`): он не только фильтрует матчинг, но
  и **пишется** в `DealCashFlow.externalInstrumentId` — ответ
  `/account/bills` аккаунт-широкий, поэтому инструмент есть **ось
  предиката линковки**, а колонка нужна форвард-слоту шага 8 (движения
  вне периода жизни сделок различаются по инструменту)
  (`docs/models/mapping/DealCashFlow.md`). Прежний довод — «операнд
  предиката отложенной линковки по persisted-строкам» — снят вместе с
  отложенной тропой (C1 `DOCS_CHECK_19`).
- **Позиция:** `sz` (размер), `posBalChg` (изменение баланса позиции),
  `posBal` (баланс позиции после события).
- **Переводы / примечания:** `from`, `to`, `notes`.

## Конвертация

Все числа приходят строками; numeric → `BigDecimal`, `ts` (Unix ms) →
доменное время при парсинге.
