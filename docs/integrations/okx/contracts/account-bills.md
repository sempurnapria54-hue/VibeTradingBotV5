# OKX contracts: account bills

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по bill-записям аккаунта (7d, 3m,
deep-архив с 2021): endpoint'ы, query, лимиты, пагинация.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Trading Account → REST API», секции «Get bills details (last
7 days)», «Get bills details (last 3 months)», «Apply bills details
(since 2021)», «Get bills details (since 2021)», «Get bill types»).
При расхождении с офдоком побеждает офдок; синхронизация —
перевыкачка + дифф при каждом заходе интегратора по источнику и по
задаче «актуализируй» (`.claude/processes/api-docs-completion.md`,
канал чтения — `.claude/skills/integration-okx.md`). Последняя
сверка: 2026-06-11 (прогон 3 — bills-archive поле-уровнево,
deep-архив дистиллирован).

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

Применимо к окнам ≤ 3 месяцев (`bills-archive`).

## Deep-архив с 2021 (двухшаговый async-флоу)

Глубже 3 месяцев — bills с 1 февраля 2021 (кроме текущего квартала),
поквартально, через генерацию файла (офдок: «Apply/Get bills details
(since 2021)»; только unified account):

1. **Заявка:** `POST /api/v5/account/bills-history-archive`.
   Permission `Read`; rate limit **12 req / сутки** по User ID.
   Body: `year`, `quarter` (`Q1`-`Q4`), `type` (опц., список через
   запятую). Ответ: `result` (`true` — ссылка уже есть; `false` —
   генерится, проверять через ~2 ч, при пике дольше; после 3 ч без
   ссылки — саппорт), `ts`.
2. **Получение:** `GET /api/v5/account/bills-history-archive
   ?year=...&quarter=...`. Permission `Read`; rate limit
   10 req / 2 s. Ответ: `fileHref` (ссылка живёт ~5.5 ч; повторная
   заявка того же квартала не нужна в течение 30 дней), `state`
   (`finished` / `ongoing` / `failed` — при failed подать заявку
   снова), `ts`.

Файл — CSV (zip), записи в обратном хронологическом порядке по
`billId`; состав колонок — как у bill-записи (instType, billId,
subType, ts, balChg/posBalChg, bal/posBal, sz, px (семантика зависит
от subType), ccy, pnl, fee, mgnMode, instId, ordId, execType,
interest, tag, fillTime, tradeId, clOrdId, fill*-поля). Для окон
старше квартала границы диапазона — [начало квартала, начало
следующего), для файлов после 2024-10-11.

## Справочник bill types

`GET /api/v5/account/subtypes` (офдок: «Get bill types»). Permission
`Read`; rate limit 20 req / 2 s по User ID. Актуальный перечень
`type`/`subType` bill-записей (вместо хардкода списка в доке).
Funding-подтипы, используемые в P&L-наводке выше: `173` (expense) /
`174` (income).

## IntegrationService контракт

Endpoint'ы — private REST с подписью. На controlled error
(`code != "0"`, parse, invariant) — exception в adapter
(`docs/rules/controlled-exchange-exceptions.md`).
