# RefreshBillsExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_BILLS` (компонент-executor): что делает, пагинация
внутри команды, дедуп и линковка `deal_id`.

## Назначение

Получает `REFRESH_BILLS` — runtime read-only команда (по отношению к бирже;
локально **персистит** разбивку). Загружает bill-записи по окну сделки
(`GET /api/v5/account/bills` (7d) → `GET /api/v5/account/bills-archive` (3m),
пагинация назад по `billId` **внутри одной команды** — паритет evidence-cycle,
`docs/decisions/refresh-evidence-cycle-ownership.md`), фильтруя по окну сделки
`begin`/`end` + `instId`. **По валюте не фильтрует** — ни в запросе, ни в
матчинге (H5, `GAPS_CLOSE_6`). Маппит записи в `DealCashFlow` (категорийная
разбивка: торговая комиссия / funding / rebate / ликвидационный штраф) и
**персистит с дедупом по `externalBillId`**. Цепочка `OkxAccountBillResponse`
→ validation → `DealCashFlow`; маппинг и структура —
`docs/models/mapping/DealCashFlow.md`, контракт —
`docs/integrations/okx/contracts/account-bills.md`. Общая семантика `REFRESH_*`
— `docs/components/ServiceCommandExecutor.md`.

## Линковка `deal_id` по окну

Bills **не несут `dealId`** (запись движения денег по аккаунту, не по сделке).
Линковка `DealCashFlow.deal_id` делается по **окну + `instId`**: `begin` =
`Position.externalCreatedAt` сделки (позиции нет — `externalCreatedAt` первого
отправленного `Order`), `end` = `externalUpdatedAt` снапшота positions-history
(недоступен — момент терминализации); `instId` = `Deal.instrument.externalId`.
Границы включительные (H14, `GAPS_CLOSE_6`;
`docs/models/domain/other/DealCashFlow.md` §«Линковка к `Deal`»).

**Валюта — проверяемый атрибут, не критерий матчинга** (H5, `GAPS_CLOSE_6`).
Ветка на записи: `ccy == Deal.resultProfitCurrency` → штатно; иначе →
**персист + линковка + `AnomalyReport`** (`severity = NON_CRITICAL`, нарушение
инварианта settle-ccy). Запись не отбрасывается фильтром — прежняя редакция
отсекала её и запросом (`ccy=USDT`), и предикатом линковки, из-за чего guard
не срабатывал никогда (`docs/models/mapping/DealCashFlow.md` §«Guard
оживлён»). Такая запись входит в итоговое число **USDT-эквивалентом по курсу
на момент закрытия сделки**, а в валютные суммы-сверки не складывается.

## Не источник числа — разбивка + сверка

`DealCashFlow` даёт **категорийную разбивку** и служит **сверке** (сумма flows ↔
net из positions-history), но заголовочное `Deal.resultProfit` **не** = `sum(bills)`
— число берётся готовым net'ом из positions-history (`realizedPnl`,
`docs/decisions/result-profit-source.md`). Расхождение сверх epsilon →
`AnomalyReport`, не блок финализации.

Идемпотентность: дедуп по `billId` (`externalBillId`) — повторный вызов не
задваивает `DealCashFlow`-flows и приводит их к состоянию биржи. Ретраится через
командную машинерию; торговых решений не принимает.
