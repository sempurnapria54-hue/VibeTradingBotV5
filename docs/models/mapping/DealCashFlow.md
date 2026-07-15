# DealCashFlow — mapping bills → домен

## На какой вопрос отвечает этот файл

Как OKX bill-записи ложатся на доменную `DealCashFlow`, как из `type`/`subType`
резолвится `CashFlowCategory`, какие поля валидируются.

## Контекст

Mapping-слой для `DealCashFlow`. Доменная модель —
`docs/models/domain/other/DealCashFlow.md`. Native bill-поля —
`docs/models/integrations/okx/OkxAccountBillResponse.md`. Контракт endpoint'ов
и поток применения — `docs/integrations/okx/contracts/account-bills.md`.
Сквозные правила — `docs/rules/raw-exchange-dto-boundary.md`, codestyle
§Маппинг.

`DealCashFlow` — носитель **разбивки** результата (не первоисточник числа):
заголовочное `Deal.resultProfit` = net из positions-history, а сумма flows —
независимая **сверка** (`docs/decisions/result-profit-source.md`,
`docs/decisions/pnl-finalization-mechanics.md`).

Текущие источники: **OKX**.

## Mapping-flow

```text
bills REST -> raw OkxAccountBillResponse -> IntegrationService validation
  -> DealCashFlowMapper -> DealCashFlow (persist)
```

Raw OKX DTO не выходит за пределы `IntegrationService` / adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`). Наполняется командой
`REFRESH_BILLS` (`RefreshBillsExecutor`): пагинация bills (7d → 3m archive)
внутри команды, матчинг к сделке и проставление `deal_id` — при сохранении
(`docs/models/domain/other/DealCashFlow.md` §Линковка к `Deal`).

## OKX bill-поле → `DealCashFlow`-поле

| OKX field | DealCashFlow field |
|---|---|
| `balChg` | `amount` (строка → `BigDecimal`) |
| `fee` | `externalFee` (строка → `BigDecimal`; знаковая комиссионная компонента) |
| `ccy` | `ccy` |
| `ts` | `externalTs` (epoch millis → `OffsetDateTime`) |
| `billId` | `externalBillId` |
| `type` | `externalType` |
| `subType` | `externalSubType` |
| `ordId` | `externalOrderId` |

`category` **не** маппится напрямую — резолвится из `type`/`subType` (см.
ниже). `deal_id` проставляется матчингом, не маппером.

### Разделение ролей `balChg` и `fee`

Два поля отвечают на **разные вопросы** и не подменяют друг друга:

- **`balChg` → `amount`** — вопрос «сходится ли сумма»: Σ`amount` сверяется с
  net из positions-history. Верен при **любой** гранулярности bills (Σ`balChg`
  = net независимо от того, комбинирует источник pnl и комиссию в одну запись
  или эмитит их раздельно).
- **`fee` → `externalFee`** — вопрос «сколько комиссии»: комиссионная
  компонента записи, взятая **явно**, а не выведенная из знака `balChg`.
  Комиссия сделки = Σ`externalFee` по её движениям.

**Почему не по знаку `balChg` (шаг 7, `GAPS_CLOSE_3`, H2).** Резолв «минус →
`TRADE_FEE`, плюс → `REBATE`» верен только при гранулярности «отдельная
fee-запись на событие». При **комбинированной** записи (`balChg = pnl + fee`)
он кладёт весь `pnl + fee` в одну категорию: комиссия недосчитана, realized
pnl пересчитан — разбивка (единственный смысл `DealCashFlow`) искажена. Native
`fee` даёт компоненту независимо от гранулярности → **безопасный суперсет**:
корректен в обе стороны. Фактическая гранулярность — рантайм-вопрос
(`.claude/tests/source-api/okx/plan.md` §AG1.5); на маппинг ответ не влияет.

**Компонента, не отдельная строка.** `fee` едет **полем на той же строке**, а
не синтетической второй `DealCashFlow`: ключ идемпотентности —
`UNIQUE(external_bill_id)` (одна строка на bill-запись,
`docs/models/domain/other/DealCashFlow.md` §Персистентность), а разложение
комбинированной записи на две строки и задвоило бы её в Σ`amount` (сломав
сверку), и не имело бы второго `billId` под ключ. Отсюда же следствие: при
комбинированной записи realized-pnl-слагаемое разбивки = `amount` −
`externalFee` (native `pnl` отдельным носителем не требуется —
`docs/models/integrations/okx/OkxAccountBillResponse.md` §«Не используется»).

## Резолв категории

`type`/`subType` bill-записи → `CashFlowCategory`:

- **funding** — `subType` `173` (expense) / `174` (income) → `FUNDING`
  (знак движения в `amount`);
- **комиссия / rebate** — по знаку **`externalFee`** (← native `fee`, несёт знак
  fee-события), **не** по знаку `amount`/`balChg`: минус (списание) →
  `TRADE_FEE`; плюс (возврат) → `REBATE`. Число комиссии берётся из
  `externalFee`, а не из `amount` (см. §«Разделение ролей `balChg` и `fee`»);
- **ликвидация** — bill-запись ликвидационного штрафа → `LIQ_PENALTY`.
  Само число `liqPenalty` в net берётся из positions-history (не bills); в
  `DealCashFlow` `LIQ_PENALTY` попадает **как категория из bill-записи
  ликвидации**, если она присутствует в окне;
- **реализованный pnl** — движение реализованного pnl по факту закрытия →
  `REALIZED_PNL`;
- **прочее** — не отнесённое к перечисленному → `OTHER`.

**Резолв категории — в вызывающем коде, не в маппере** (codestyle §Маппинг:
маппер делает только перенос данных, доменных решений — интерпретации
`type`/`subType` в категорию — не принимает). Актуальный перечень
`type`/`subType` — справочник OKX (`GET /api/v5/account/subtypes`,
`docs/integrations/okx/contracts/account-bills.md`), не хардкод.

## Validation (структурная, до маппинга)

В `IntegrationService` OKX:

- **`billId` присутствует** — ключ дедупа/идемпотентности
  (`UNIQUE(external_bill_id)`); движение без `billId` не сохраняется.
- **`ccy` присутствует** — обязательно, иначе cross-ccy движение теряется
  молча при сверке (`pnl-finalization-mechanics.md` реш.5).
- **`balChg` parseable numeric** — числа приходят строками, `amount`
  парсится в `BigDecimal`; пустая/непарсящаяся строка недопустима.
- **`fee` parseable numeric, если присутствует** — `externalFee` парсится в
  `BigDecimal`; пусто → `null` (движение без комиссионной компоненты, например
  funding). Непарсящаяся непустая строка недопустима.
- На controlled error (`code != "0"`, parse, invariant) — exception в adapter
  (`docs/rules/controlled-exchange-exceptions.md`).

## Сверка и cross-ccy

Сумма `DealCashFlow.amount` сверяется с net из positions-history. Расхождение
**сверх epsilon** и cross-ccy-движение (`ccy != resultProfitCurrency`) →
`AnomalyReport` (audit-аномалия, **не** блок финализации;
`docs/decisions/pnl-finalization-mechanics.md` реш.5).

- **Epsilon якорится на оборот, не на итог:** max(0.01 settle-ccy, 0.5% от
  Σ`|amount|`). Охраняемая ошибка — ошибка **композиции разбивки**, её масштаб
  задают валовые потоки, а не их итог (H7, `GAPS_CLOSE_3`). Величина
  (`0.01`/`0.5%`) — **провизорна** (калибровка — пользователь/бэктест), якорь —
  нет.
- **Cross-ccy — нарушение инварианта, не режим:** комиссии платятся только в
  settle-ccy (`docs/rules/trading-constraints.md` §Валюта комиссии). Движение с
  чужой `ccy` **не отбрасывается молча** фильтром — помечается аномалией.

## Связи

- Доменная модель — `docs/models/domain/other/DealCashFlow.md`.
- Native — `docs/models/integrations/okx/OkxAccountBillResponse.md`.
- Контракт — `docs/integrations/okx/contracts/account-bills.md`.
- Источник числа / роль bills — `docs/decisions/result-profit-source.md`.
- Механика финализации / сверка — `docs/decisions/pnl-finalization-mechanics.md`.
