# OKX contracts: position

## На какой вопрос отвечает этот файл

Каков контракт операций по позиции.

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, разделы «Trading
Account → REST API» — «Get positions», «Get positions history»; «Order
Book Trading → Trade» — «POST / Close positions»). Процедура сверки и
приоритет офдока — `.claude/rules/external-source-sync.md`. Последняя
сверка: 2026-06-11 (соответствие positions/close-position;
positions-history поле-уровнево); 2026-10-01 — точечно семантика `autoCxl`
у close-position.

**Рантайм-расхождение (2026-08-30, провенанс `рантайм`)** — одно, и оно
несущее: офдок называет `posId` идентификатором позиции и переиспользования
не оговаривает, а прогон контура показал, что **источник выдаёт
переоткрытой позиции тот же `posId`**, и фильтр `posId` в запросе истории
записи не сужает. Правило «побеждает офдок» на этот факт **не
распространяется**: он не толкование дока, а наблюдённое поведение
(`.claude/rules/external-source-sync.md`). Состав
— в разделе об инварианте агрегации ниже.

## Endpoints

- **Получить позиции** (`REFRESH_POSITION_COMMAND`):
  `GET /api/v5/account/positions?instType=SWAP&instId={...}`.
  Permission `Read`; rate limit 10 req / 2 s по User ID. Один
  логический запрос по инструменту; дополнительно по `posId` в **live**-ноге
  не ищем — её цель в наличии/отсутствии live position по инструменту, не
  в доказательстве старого `posId` (биржа держит ~30 дней). При not-found
  команда переходит на **вторую ногу** — positions-history **по
  инструменту и временному окну**, не по `posId`: фильтр `posId` записи не
  сужает (раздел об инварианте агрегации ниже).
  Query (все опц.): `instType`, `instId` (до 10 через запятую),
  `posId` (до 20). В net-режиме на инструмент ожидается одна запись
  с `posSide=net`; в long/short — отдельные `posSide=long`/`short`.
  **Второе применение того же эндпоинта — срез без сужения по
  инструменту** (`instType=SWAP`, `instId` не задаётся): его читают
  обходы, которым нужны живые позиции по МНОЖЕСТВУ инструментов
  (`docs/components/AnomalyJob.md`). Поштучный запрос там растёт с
  числом инструментов и выбирает **ту же корзину лимита**, что и
  торговая петля, — лимит здесь по User ID, а не по инструменту.
- **Закрыть позицию** (`CLOSE_POSITION_COMMAND`):
  `POST /api/v5/trade/close-position`. Permission `Trade`; rate
  limit 20 req / 2 s по User ID + Instrument ID. Body: `instId`
  (обяз.), `mgnMode` (обяз.; `isolated`/`cross`), `posSide` (условно
  обяз. — для net: `net`; для long/short: `long`/`short`), `ccy`
  (опц., для USDT-SWAP — `USDT`), `autoCxl` (опц. boolean — отменить ли
  автоматически стоящие **заявки на закрытие** при закрытии позиции
  рыночной заявкой; офдок OKX, сверен 2026-10-01: «Whether any pending
  orders for closing out needs to be automatically canceled when close
  position via a market order»). Без флага стоящая reduce-only заявка
  закрытие отвергает кодом `51168` («You have reduce-only type of open
  order(s), please proceed after canceling existing order(s)»). Входные
  заявки — не reduce-only — флаг не снимает, и закрытие они переживают.

Ретраи на refresh — только при технических/API проблемах (timeout,
connection reset, 5xx, rate limit, temporary error).

## История закрытых позиций (источник числа `resultProfit`)

`GET /api/v5/account/positions-history`. Permission `Read`; rate
limit 10 req / 2 s по User ID. Глубина — 3 месяца, сортировка по
`uTime` (новые первыми). Офдок: «Get positions history». Статус:
**источник заголовочного числа** `Deal.resultProfit` (готовый net
`realizedPnl`; `docs/models/domain/aggregate/Deal.md`). `closeAvgPx`/
`openAvgPx` покрывают среднюю цену выхода/входа (fills для этого не
нужны).

**Добыча:** эндпоинт — **вторая нога evidence-cycle команды
`REFRESH_POSITION_COMMAND`**. Наполняет
`PositionCloseResultExternalSnapshot`, который приземляется **полями
положения закрытия на `Position`** (`docs/models/domain/core/Position.md`),
откуда число читает финализатор. Отдельной команды
`REFRESH_POSITIONS_HISTORY` нет.
Native-модель — `docs/models/integrations/okx/PositionsHistoryOkxResponse.md`;
mapping native→snapshot→`Position`→`Deal` —
`docs/models/mapping/PositionCloseResult.md`.

- **Query (все опц.):** `instType`, `instId`, `mgnMode`
  (`cross`/`isolated`), `type` (тип последнего закрытия: `1`
  частичное / `2` полное / `3` ликвидация / `4` частичная ликвидация
  / `5` ADL не полностью / `6` ADL полностью), `posId`,
  `after`/`before` — пагинация **по `uTime`** (не по id; записи с
  одинаковым `uTime` приходят одной страницей), `limit` ≤ 100.
- **P&L-поля элемента:** `realizedPnl` = `pnl` + `fee` +
  `fundingFee` + `liqPenalty` (+ `settledPnl` cross-FUTURES);
  `pnl` (без комиссий), `fee` (минус — комиссия, плюс — ребейт),
  `fundingFee` (накопленный), `liqPenalty`, `pnlRatio`.
- **Цены/объёмы:** `openAvgPx`, `closeAvgPx`, `openMaxPos`
  (максимум позиции), `closeTotalPos` (накопленный закрытый объём),
  `triggerPx` (цена триггера ликвидации/ADL — **опционально**, см. ниже),
  `nonSettleAvgPx`/`settledPnl` (cross FUTURES).

### Перечни значений полей элемента

Оба перечня — **дистиллят офдока, наблюдением не подтверждённый**: какие
значения источник отдаёт фактически, устанавливает прогон против него.
Пометка стоит здесь, чтобы перечень не выглядел проверенным.

| Поле элемента | Сырые значения | Куда резолвится |
|---|---|---|
| `direction` | `long`, `short` | доменное `Position.Direction` (`LONG` / `SHORT`) — резолв в слое интеграции; незнакомое либо пустое значение есть **нарушение контракта** ⇒ ступень 2 (`docs/models/mapping/PositionCloseResult.md`, дом резолва направления). Те же два значения источник использует для `posSide` в long/short-режиме |
| `type` | `1` частичное закрытие, `2` полное, `3` ликвидация, `4` частичная ликвидация, `5` ADL не полностью, `6` ADL полностью | доменное `Deal.closeOutcome` — отображение и ветка непригодного операнда — в `docs/models/mapping/PositionCloseResult.md`, доме резолва торгового исхода. Тот же перечень служит значениями query-параметра `type` выше |

### Инвариант агрегации — верифицирован прогоном (провенанс `рантайм`)

**Инвариант:** **один эпизод** ↔ **одна
финализированная** запись positions-history, чей `realizedPnl`
**кумулятивен по ВСЕМ** partial-закрытиям и доборам за жизнь **этого
эпизода**; читается финализированной (позиция полностью закрыта / flat по
`REFRESH_POSITION_COMMAND`).

**Верифицировано 2026-08-30** прогоном против demo-окружения источника:

| Свойство источника | Наблюдение |
|---|---|
| partial-выходы агрегируются в **одну** финализированную запись | у эпизода «открыть 0.02 → закрыть 0.01 → закрыть остаток» запись одна, `closeTotalPos` — полный закрытый объём, `realizedPnl` кумулятивен и точно равен `pnl + fee + fundingFee + liqPenalty` |
| окно с несколькими эпизодами отдаёт **отдельную** запись на каждый | записей столько, сколько эпизодов; источник их не схлопывает |
| **`posId` эпизод не адресует** | `posId` переиспользуется — восемь последовательных эпизодов за ~110 минут получили один и тот же `posId`, различаясь только `cTime` |

**Расхождение с офдоком — рантайм-факт**
(`.claude/rules/external-source-sync.md`): офдок
называет `posId` идентификатором позиции и не оговаривает
переиспользования, а фильтр `posId` в запросе истории **записи не
сужает** — возвращает все записи инструмента. Отсюда контракт: запрос
истории адресует записи **инструментом и временным окном**, а
адресуемая единица эпизода — **пара** `(posId, cTime)`. Доменный дом
единицы — `docs/models/domain/core/Position.md`.

Риск чтения нефинализированной / послайсовой записи (систематический
недосчёт realized, левый хвост R-распределения усечён молча) снимает
агрегация источника; риск склейки двух эпизодов в одну строку снимает
адресация парой.

## ACK-семантика close-position

Response — ACK, не финальный статус (`docs/rules/ack-not-runtime-truth.md`).
`data[0]` содержит `instId`, `posSide`. **Нет `ordId`** и нет
финального статуса позиции — подтверждение через `REFRESH_POSITION_COMMAND`
(позиция исчезла или `pos=0`), опционально через fills и/или WS
`positions`/`orders`.
