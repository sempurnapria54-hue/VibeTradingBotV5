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
`docs/models/integrations/okx/OkxAccountBillResponse.md`. Доменный носитель
разбивки — `DealCashFlow` (**OKX-Q3 закрыт**: bills — **категорийная разбивка
+ сверка**, не первоисточник числа; заголовочное `Deal.resultProfit` = net из
positions-history, `docs/decisions/result-profit-source.md`). Структура
`DealCashFlow` и маппинг bills → домен —
`docs/models/domain/other/DealCashFlow.md`,
`docs/models/mapping/DealCashFlow.md`.

Различие fills и bills:
- **Fill** — факт исполнения ордера (что произошло на рынке).
- **Bill** — запись движения денег по аккаунту (что изменило баланс).

Bills покрывают и funding, и rebate, и другие cashflow, не привязанные
напрямую к executions — поэтому дают **категорийную разбивку** результата
(торговая комиссия / funding / rebate / ликвидационный штраф). Само
**число** `resultProfit` берётся готовым net'ом из positions-history
(`realizedPnl`), а сумма bills-flows **сверяется** с ним (контроль
целостности), не подменяет его.

## Endpoints

- **Bills 7 дней:** `GET /api/v5/account/bills`. Permission: Read.
  Rate limit: **5 req / s** по User ID.
- **Bills 3 месяца:** `GET /api/v5/account/bills-archive`. Permission:
  Read. Rate limit: 5 req / 2 s по User ID.

## Query (одинаковые для обоих)

- `instType` (опц.) — `SPOT/MARGIN/SWAP/FUTURES/OPTION`.
- `ccy` (опц.) — валюта bill-записи (`USDT`).
- `type` (опц.) — тип bill-записи (актуальный список — справочник
  OKX). **Рантайм (2026-06-19, demo, контур source-api / AG3):**
  вне-доменный `type` (например `99999`) **игнорируется** — биржа
  отдаёт `code=0` с нефильтрованными записями, а не реджект
  (провенанс `рантайм`).
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

Bills добываются командой **`REFRESH_BILLS_COMMAND`** (`RefreshBillsExecutor`):
пагинация `7d → 3m` (`bills` → `bills-archive`) проходится **внутри
команды**; результат наполняет доменный носитель разбивки `DealCashFlow`
(`docs/models/domain/other/DealCashFlow.md`).

```text
0. Предусловие: сделка УДЕРЖИВАЕТ слот по инструменту (Deal.status вне
   CLOSED/EMERGENCY_CLOSED). Только это делает матчинг по окну + instId
   однозначным — второй сделки по инструменту в это время не существует
   (uk_deal_active_instrument). После терминала линковка не выполняется.

1. Определить окно сделки — СОБСТВЕННЫЕ ПОЛЯ Deal (узел 1 DOCS_CHECK_8;
   docs/decisions/command-action-boundary.md §7):
   - begin = Deal.billsWindowBegin (пишет наблюдатель факта открытия);
   - end   = Deal.billsWindowEnd (пишет вторая нога REFRESH_POSITION_COMMAND
            транзакционно с полями положения закрытия).
   Из чужих колонок (Position.externalCreatedAt/externalModifiedAt,
   Deal.modifiedAt) окно НЕ реконструируется; end пуст (факт закрытия не
   добыт) -> привязка ждёт, суррогатом окно не закрывается.
   Границы включительные. Верхняя граница нужна, чтобы не тянуть лишнего;
   разделяет сделки инвариант слота (п.0), а не она.

2. Запросить bills (пагинация 7d→3m внутри REFRESH_BILLS_COMMAND):
   GET /api/v5/account/bills?instType=SWAP&begin=...&end=...
   ccy в запрос НЕ идёт (H5): фильтр по валюте убивал бы cross-ccy guard —
   нарушающая запись не приходила бы в ответ вовсе.

3. Линковка к сделке (bills НЕ несут dealId) — по окну + инструменту:
   - ts     ∈ [begin, end] окна сделки;
   - instId == externalId инструмента сделки (Instrument резолвится из
     DealContext: в runtime graph Deal он не входит).
   Валюта критерием матчинга НЕ является — она проверяемый атрибут (п.4).
   Выход матчинга закрепляется как DealCashFlow.deal_id при сохранении.

4. Сохранить как DealCashFlow (категорийная разбивка); резолв категории
   (type/subType → CashFlowCategory) — НА ЗАПИСИ, в вызывающем коде
   executor'а, не в маппере и не при финализации: category на строке
   NOT NULL (docs/models/domain/other/DealCashFlow.md §Структура).
   Ветка по валюте НА ЗАПИСИ (операнд — РАСЧЁТНАЯ ВАЛЮТА ИНСТРУМЕНТА,
   не Deal.resultProfitCurrency: последнее пишет FINALIZE_DEAL_EXIT_COMMAND, то есть
   ПОСЛЕ этого прохода, и здесь оно всегда null — H4):
   - ccy == расчётная валюта инструмента -> штатно;
   - ccy != расчётная валюта инструмента -> персист + линковка (deal_id
     проставляется так же) + ПЕРЕСЧЁТ СРАЗУ (курс — отдельным вызовом биржи
     на момент обработки, кладётся в DealCashFlow.appliedRate) +
     AnomalyReport (нарушение инварианта «комиссии только в settle-ccy»,
     docs/rules/trading-constraints.md).
     Запись НЕ отбрасывается и НЕ теряется.

5. Сверка (при финализации):
   sum(DealCashFlow.amount) по строкам расчётной валюты сверяется с net из
   positions-history (realizedPnl). Строки чужой ccy в эту сумму не
   складываются (разные валюты), они входят в число эквивалентом — см. п.6.
   Расхождение сверх epsilon (якорь — валовой оборот sum(|amount|),
   не |net|) → AnomalyReport (аудит-аномалия, НЕ блок финализации; см.
   pnl-finalization-mechanics.md реш.5).

6. Итоговое число — в settle-ccy (USDT):
   Deal.resultProfit = net из positions-history
                     + sum(amount × appliedRate) по строкам чужой ccy.
   Не-settle-ccy издержка в биржевой net не входит, поэтому без этого
   слагаемого число завышало бы результат молча (H5, реш.5). Курс уже
   зафиксирован на записи (п.4) — финализация его не запрашивает.
```

**Область суммирования — не одна** (H9, `GAPS_CLOSE_4`). Σ`amount` в п.5 идёт
по **всем** строкам сделки **в расчётной валюте**, включая `FUNDING` (net из
positions-history его содержит), — это сумма-сверка. Realized-слагаемое
разбивки, наоборот, считается **только** по строкам `REALIZED_PNL`, `FUNDING`
из него исключён (holding-cost, не realized pnl). Строки чужой `ccy` — своя,
четвёртая область (H5, `GAPS_CLOSE_6`): в валютные суммы они не складываются,
а входят слагаемым в число по зафиксированному `appliedRate` (п.6). Области —
`docs/models/mapping/DealCashFlow.md` §«Область суммирования — задаётся явно».

> **Граница 6 ↔ 7 и источник числа.** Само **число** `Deal.resultProfit` =
> net из positions-history (`realizedPnl`), **не** `sum(DealCashFlow.amount)`
> (`docs/decisions/result-profit-source.md`). *Расчёт* (число + разбивка +
> сверка) — **шаг 7**, владелец `FinalizeDealExitExecutor`; запись на
> терминале — `MarkDealClosedExecutor`. Механика финализации шага 6 поставила
> ребро/retry/placeholder ZERO; шаг 7 наделяет её расчётом. Терминальный
> контракт (число на аварийном терминале — фактический net) —
> `docs/lifecycles/Deal.md` §«Терминальный контракт финализации» (DEAL-Q2).

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

**Рантайм (2026-06-19, demo, контур source-api / AG5; провенанс
`рантайм`):** содержательный архив на свежем demo недостижим.
Валидная **заявка** (`POST … {year:2025, quarter:Q1}`) вернула
реджект `50026` «System error. Try again later.» (бэкенд генерации на
demo недоступен / нет истории квартала), а не `result`-ACK.
**Получение** до успешной заявки — реджект `51604` «Initiate a
download request before obtaining the hyperlink». Негативы конверта
отрабатывают штатно: битый/пропущенный `quarter` → `51000` «Parameter
quarter error». Эндпоинт **достижим** (HTTP 200 + структурный конверт
OKX); неполнота — ограничение demo, не дефект контракта.

**Success-контракт — проверяется на проде (ад-хок, вне контура
source-api).** Полный happy-path (заявка → `result`-ACK → `GET` →
`fileHref` при `state=finished` → CSV) на demo неверифицируем: demo не
инициирует архив (заявка → `50026`, `GET` → `51604`). Зелёный AG5-кейс
контура подтверждает только demo-реджект и негативы конверта, **не**
success — его верификация выполняется ад-хок на проде вне контура.

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
