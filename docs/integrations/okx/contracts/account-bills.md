# OKX contracts: account bills

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по bill-записям аккаунта (7d, 3m):
endpoint'ы, query, лимиты, пагинация.

## Контекст

Native responses —
`docs/models/integrations/okx/OkxAccountBillResponse.md`. Persisted
сущность `AccountBill` / `DealCashFlow` не введена — см. **OKX-Q3** в
`.claude/work/questions/open-questions.md`.

Различие fills и bills:
- **Fill** — факт исполнения ордера (что произошло на рынке).
- **Bill** — запись движения денег по аккаунту (что изменило баланс).

Для финального PnL сделки bills могут быть **полнее**, потому что
покрывают и funding, и rebate, и другие cashflow, не привязанные
напрямую к executions.

## Endpoints

- **Bills 7 дней:** `GET /api/v5/account/bills`. Permission: Read.
  Rate limit: **5 req / s** по User ID.
- **Bills 3 месяца:** `GET /api/v5/account/bills-archive`. Permission:
  Read. Rate limit: 5 req / 2 s по User ID.

## Query (одинаковые для обоих)

- `instType` (опц.) — `SPOT/MARGIN/SWAP/FUTURES/OPTION`.
- `ccy` (опц.) — валюта bill-записи (`USDT`).
- `type` (опц.) — тип bill-записи (актуальный список — справочник
  OKX).
- `subType` (опц.) — подтип. Funding: `173` (expense) / `174`
  (income).
- `after` / `before` — пагинация **по `billId`**.
- `begin` / `end` — фильтр по времени (Unix ms).
- `limit` — ≤ 100 (default 100).

`after`/`before` × `begin`/`end`: биржа сначала фильтрует по
`begin`/`end`, затем применяет пагинацию.

## Пагинация назад

1. Запрос без `after`.
2. Из ответа берём `min(billId)`.
3. Следующий запрос с `after = min(billId)`.
4. Стоп: пустой `data`.

## Использование (намерение, не текущая реализация)

```text
1. Определить окно сделки:
   - begin = время первого подтверждённого entry/execution/cashflow факта;
   - end   = время последнего exit/finalization факта.

2. Запросить bills:
   GET /api/v5/account/bills?instType=SWAP&ccy=USDT&begin=...&end=...

3. Отфильтровать в коде:
   - instId == Deal.instrument.externalId;
   - ccy   == Deal.resultProfitCurrency;
   - type/subType относятся к PnL / fee / rebate / funding.

4. Сохранить как DealCashFlow.

5. FINALIZE_DEAL_EXIT считает:
   Deal.resultProfit = sum(DealCashFlow.amount)
```

Применимо к окнам ≤ 3 месяцев (`bills-archive`). Глубже 3 месяцев —
deep archive flow (на момент миграции отдельного endpoint'а в архиве
не зафиксировано).

## ClientService контракт

Endpoint'ы — private REST с подписью. На controlled error
(`code != "0"`, parse, invariant) — exception в adapter
(`docs/rules/controlled-exchange-exceptions.md`).
