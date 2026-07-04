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
| `ccy` | `ccy` |
| `ts` | `externalTs` (epoch millis → `OffsetDateTime`) |
| `billId` | `externalBillId` |
| `type` | `externalType` |
| `subType` | `externalSubType` |
| `ordId` | `externalOrderId` |

`category` **не** маппится напрямую — резолвится из `type`/`subType` (см.
ниже). `deal_id` проставляется матчингом, не маппером.

## Резолв категории

`type`/`subType` bill-записи → `CashFlowCategory`:

- **funding** — `subType` `173` (expense) / `174` (income) → `FUNDING`
  (знак движения в `amount`);
- **комиссия / rebate** — по знаку движения (`amount` ← `balChg`, несёт знак
  fee-события): минус (списание) → `TRADE_FEE`; плюс (возврат) → `REBATE`;
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
- На controlled error (`code != "0"`, parse, invariant) — exception в adapter
  (`docs/rules/controlled-exchange-exceptions.md`).

## Сверка и cross-ccy

Сумма `DealCashFlow.amount` сверяется с net из positions-history. Расхождение
**сверх epsilon** и cross-ccy-движение (`ccy != resultProfitCurrency`,
например комиссия в OKB) → `AnomalyReport` (audit-аномалия, **не** блок
финализации; `docs/decisions/pnl-finalization-mechanics.md` реш.5). Движение с
чужой `ccy` **не отбрасывается молча** фильтром — помечается аномалией.

## Связи

- Доменная модель — `docs/models/domain/other/DealCashFlow.md`.
- Native — `docs/models/integrations/okx/OkxAccountBillResponse.md`.
- Контракт — `docs/integrations/okx/contracts/account-bills.md`.
- Источник числа / роль bills — `docs/decisions/result-profit-source.md`.
- Механика финализации / сверка — `docs/decisions/pnl-finalization-mechanics.md`.
