# RefreshBillsExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_BILLS_COMMAND` (компонент-executor): что делает, пагинация
внутри команды, дедуп и линковка `deal_id`.

## Назначение

Получает `REFRESH_BILLS_COMMAND` — runtime read-only команда (по отношению к бирже;
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
Линковка `DealCashFlow.deal_id` делается по **окну + `instId`**: границы
окна — **собственные поля сделки** `Deal.billsWindowBegin` /
`Deal.billsWindowEnd` (узел 1 `DOCS_CHECK_8`;
`docs/models/domain/aggregate/Deal.md` §«Окно линковки bills»);
инструмент — `externalId` инструмента сделки (резолвится из
`Deal.instrumentId` через `DealContext`, см. §Инструмент ниже). Границы
включительные (H14, `GAPS_CLOSE_6`;
`docs/models/domain/other/DealCashFlow.md` §«Линковка к `Deal`»).

**`billsWindowEnd` пуст → привязка ждёт.** Факт закрытия не добыт —
executor персистит записи (дедуп по `externalBillId`), но линковку этим
проходом не делает и окно суррогатом не закрывает; добычу факта ретраит
`REFRESH_DEAL_CONTEXT_ACTION`, исчерпание бюджета уводит сделку ошибочной
тропой + холд инструмента.

**Однозначность держит инвариант «одна активная сделка на инструмент»**, не
точность верхней границы: пока сделка удерживает слот, второй сделки по
инструменту нет. **Отсюда условие исполнимости:** executor линкует движения
**только пока `Deal` не терминализован** (статус вне
`CLOSED`/`EMERGENCY_CLOSED`). После терминала слот освобождён — окно +
`instId` перестают быть однозначными, и отложенная/повторная добыча bills
по этому окну не выполняется.

## Инструмент

`instId` берётся из `Instrument.externalId`, а `Instrument` в runtime graph
`Deal` **не входит** — приходит через `DealContext`
(`docs/models/domain/aggregate/Deal.md` §«Runtime graph»). Путь
`Deal.instrument.externalId` на модели отсутствует; executor резолвит
инструмент из контекста прохода (H27, `GAPS_CLOSE_7`).

**Валюта — проверяемый атрибут, не критерий матчинга** (H5, `GAPS_CLOSE_6`;
операнд и момент курса — H4, `GAPS_CLOSE_7`).
Ветка на записи: `ccy` == **расчётной валюте инструмента** → штатно; иначе →
**персист + линковка + пересчёт + `AnomalyReport`** (`severity = NON_CRITICAL`,
нарушение инварианта settle-ccy). Запись не отбрасывается фильтром — прежняя
редакция отсекала её и запросом (`ccy=USDT`), и предикатом линковки, из-за чего
guard не срабатывал никогда (`docs/models/mapping/DealCashFlow.md` §«Guard
оживлён»).

**Операнд — инструмент, не сделка.** Сравнивать с `Deal.resultProfitCurrency`
нельзя: его пишет `FINALIZE_DEAL_EXIT_COMMAND`, то есть **после** этого прохода, и на
момент записи оно `null` — предикат сравнивал бы с пустым
(`docs/models/mapping/DealCashFlow.md` §«Операнд сравнения»). Носитель
расчётной валюты инструмента — предложение на валидации (`GAPS_CLOSE_7`).

**Пересчёт — здесь и сразу, не при финализации.** Executor запрашивает курс
**отдельным вызовом биржи на момент обработки** и кладёт его в
`DealCashFlow.appliedRate` той же строки. Итоговое слагаемое числа —
Σ(`amount` × `appliedRate`) (считает финализатор); в валютные суммы-сверки
строка не складывается. Пересчёт на записи, а не на финализации, снимает
зависимость guard'а от факта закрытия и фиксирует курс в момент, когда
движение наблюдалось.

## Не источник числа — разбивка + сверка

`DealCashFlow` даёт **категорийную разбивку** и служит **сверке** (сумма flows ↔
net из positions-history), но заголовочное `Deal.resultProfit` **не** = `sum(bills)`
— число берётся готовым net'ом из positions-history (`realizedPnl`,
`docs/decisions/result-profit-source.md`). Расхождение сверх epsilon →
`AnomalyReport`, не блок финализации.

Идемпотентность: дедуп по `billId` (`externalBillId`) — повторный вызов не
задваивает `DealCashFlow`-flows и приводит их к состоянию биржи. Ретраится через
командную машинерию; торговых решений не принимает.
