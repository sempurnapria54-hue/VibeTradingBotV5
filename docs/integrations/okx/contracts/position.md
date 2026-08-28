# OKX contracts: position

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по позиции: endpoint'ы, лимиты,
close-position ACK, подтверждение факта закрытия, история закрытых
позиций.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
разделы «Trading Account → REST API» — «Get positions», «Get
positions history»; «Order Book Trading → Trade» — «POST / Close
positions»). При расхождении с офдоком побеждает офдок;
синхронизация — перевыкачка + дифф при каждом заходе интегратора по
источнику и по задаче «актуализируй»
(`.claude/processes/api-docs-completion.md`, канал чтения —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(прогон 1 — соответствие positions/close-position; прогон 3 —
positions-history поле-уровнево).

## Контекст

Mapping в `Position` — `docs/models/mapping/Position.md` (раздел
`## OKX`). Native response —
`docs/models/integrations/okx/OkxPositionResponse.md`. Правила OKX —
`docs/integrations/okx/rules/`. Доменные модель/lifecycle —
`docs/models/domain/core/Position.md` / `docs/lifecycles/Position.md`.

## Endpoints

- **Получить позиции** (`REFRESH_POSITION_COMMAND`):
  `GET /api/v5/account/positions?instType=SWAP&instId={...}`.
  Permission `Read`; rate limit 10 req / 2 s по User ID. Один
  логический запрос по инструменту; дополнительно по `posId` в **live**-ноге
  не ищем — её цель в наличии/отсутствии live position по инструменту, не
  в доказательстве старого `posId` (биржа держит ~30 дней). При not-found
  команда переходит на **вторую ногу** — positions-history по `posId`
  (§«История закрытых позиций»).
  Query (все опц.): `instType`, `instId` (до 10 через запятую),
  `posId` (до 20). В net-режиме на инструмент ожидается одна запись
  с `posSide=net`; в long/short — отдельные `posSide=long`/`short`.
- **Закрыть позицию** (`CLOSE_POSITION_COMMAND`):
  `POST /api/v5/trade/close-position`. Permission `Trade`; rate
  limit 20 req / 2 s по User ID + Instrument ID. Body: `instId`
  (обяз.), `mgnMode` (обяз.; `isolated`/`cross`), `posSide` (условно
  обяз. — для net: `net`; для long/short: `long`/`short`), `ccy`
  (опц., для USDT-SWAP — `USDT`), `autoCxl` (опц. boolean —
  автоматически отменить все активные ордера по инструменту перед
  закрытием; рекомендуется `true`).

Ретраи на refresh — только при технических/API проблемах (timeout,
connection reset, 5xx, rate limit, temporary error).

## История закрытых позиций (источник числа `resultProfit` — шаг 7)

`GET /api/v5/account/positions-history`. Permission `Read`; rate
limit 10 req / 2 s по User ID. Глубина — 3 месяца, сортировка по
`uTime` (новые первыми). Офдок: «Get positions history». Статус:
**источник заголовочного числа** `Deal.resultProfit` (готовый net
`realizedPnl`) — выбран на `GAPS_CLOSE_1` шага 7 (2026-07-03; **В-3
закрыт**, `docs/decisions/result-profit-source.md`). `closeAvgPx`/
`openAvgPx` покрывают среднюю цену выхода/входа (fills для этого не
нужны).

**Добыча:** эндпоинт — **вторая нога evidence-cycle команды
`REFRESH_POSITION_COMMAND`** (live `/account/positions` → при not-found
`/account/positions-history`, внутри одной команды; H1/H3 `GAPS_CLOSE_7`,
`docs/decisions/pnl-finalization-mechanics.md` реш.1,
`docs/components/RefreshPositionExecutor.md`). Наполняет
`PositionCloseResultExternalSnapshot`, который приземляется **полями
положения закрытия на `Position`** (`docs/models/domain/core/Position.md`),
откуда число читает финализатор. Отдельной команды
`REFRESH_POSITIONS_HISTORY` нет.
Native-модель — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`;
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

> **`triggerPx` — единственный носитель применимости; точный поднабор
> `type` открыт** (H19, `GAPS_CLOSE_6`). Доки проекта разошлись на трёх
> формулировках («3/4/5», «3–6», «3/4/5/6»); прочие носители приведены к
> ссылке сюда, чтобы версия была одна. **Сверка с офдоком не выполнена:**
> заход CC по каналу чтения вернул страницу, усечённую до раздела
> positions-history — дефицит `грунт`, владелец сверки — `integrator`
> (`.claude/processes/api-docs-completion.md`).
>
> **Расхождение обесточено окончательно** (H22, `GAPS_CLOSE_7`): поле
> **выведено из маппинга** — потребителя у него в фазе 1 нет, снапшот его
> не несёт, валидация его не рассматривает
> (`docs/models/mapping/PositionCloseResult.md`). Открытая сверка остаётся
> у `integrator` как справочная; ни число `resultProfit`, ни структурная
> валидация от неё больше не зависят. Поле вернётся в маппинг вместе с
> потребителем — провенансом ликвидации/ADL (`PNL-Q1`).
- **Идентификация записи:** `posId` (истекает ~через 30 дней после полного
  закрытия — после этого новая позиция получает новый `posId`),
  `instType`/`instId`, `mgnMode`, `posSide`, `direction`, `lever`,
  `uly`, `cTime`/`uTime`.
  - **Что офдок этим НЕ утверждает** (B11 `DOCS_CHECK_20`): что до
    истечения 30 дней переоткрытая позиция получает **новый** `posId`.
    Формулировка «истекает через ~30 дней, после чего новый» читается и
    как «внутри окна id **переиспользуется**». Домен на этом месте
    утверждает обратное — «биржа даёт новой позиции новый `posId`, и это
    единственный наблюдаемый признак смены»
    (`docs/lifecycles/Position.md` §«Смена эпизода»), — то есть носители
    расходятся, и расхождение **не разрешено офдоком**.
  - **На что это опирается у нас.** Дискриминатор смены эпизода и ключ
    `uk_position_deal_external (deal_id, external_id)`, вводимый шагом 7
    (`docs/decisions/pnl-finalization-mechanics.md` §«Schema-дельта шага
    7»). При переиспользовании `posId` внутри одной сделки дискриминатор
    слеп, а ключ ловит **легитимный** второй эпизод как дубль — отказ
    вставки на штатной тропе.
  - **Снятие — рантайм-кейсом `AG1.9`** (`.claude/tests/source-api/okx/
    plan.md`), который уже спрашивает ровно это: «получает ли
    переоткрытая позиция новый `posId` в пределах одного окна». До его
    исхода утверждение домена помечено как **предположение**, а не
    факт источника.
- **Несколько записей в окне одного инструмента — норма, а не
  двусмысленность:** сделка многоэпизодна
  (`docs/decisions/multi-episode-deal.md`), и каждый эпизод (позиция
  схлопнулась в ноль и открылась заново) даёт **свою** финализированную
  запись со своим `posId`. Домен материализует их в отдельные строки
  `Position` одной сделки; `Deal.resultProfit` — Σ их `realizedPnl`.
  Инвариант кумулятивности `realizedPnl` действует **внутри** эпизода.
  Рантайм-подтверждение — `.claude/tests/source-api/okx/plan.md` §AG1.9
  (предусловие `CODE` п. 1, вторая половина).
- **Набор значений `direction` — открытая сверка `integrator`** (H7
  `DOCS_CHECK_15`). Поле — операнд create-тропы: из него резолвится
  доменный `Position.Direction`, и резолв живёт **в слое интеграции**
  (`docs/models/mapping/PositionCloseResult.md` §«Резолв направления»);
  незнакомое значение — controlled-исключение. Какие именно строки
  источник отдаёт (`long`/`short`, `net`, иное), из офдока в этот док не
  перенесено — **посылка «это не имена наших констант» не проверена**.
  Перечень заводится здесь по итогам сверки; от ответа зависит **таблица
  маппинга**, не конструкция резолва.
- **`ccy` — не идентификация, а семантика числа** (H28, `GAPS_CLOSE_7`):
  это **валюта, в которой посчитан `realizedPnl`** данной записи. Прежняя
  редакция перечисляла её в блоке идентификации рядом с `mgnMode`/`posSide`,
  и утверждение «валюта результата» стояло только на моделях
  (`OkxPositionsHistoryResponse.md`, `mapping/PositionCloseResult.md`) —
  тогда как на этом поле держится cross-ccy-инвариант
  (`docs/rules/trading-constraints.md` §«Валюта комиссии»). Носитель
  выровнен: семантику утверждает контракт-док.
  - **Оговорка контура.** Для USDT-SWAP-only контура `ccy` записи и
    расчётная валюта инструмента совпадают всегда. Если контур перестанет
    быть USDT-SWAP-only (или появится inverse-контракт), совпадение
    перестанет быть автоматическим — сравнение с расчётной валютой
    инструмента (`docs/models/mapping/DealCashFlow.md` §«Guard оживлён»)
    рассчитано и на этот случай.

### Инвариант агрегации (N11, требует рантайм-верификации)

**Инвариант:** **один эпизод** ↔ один `posId` ↔ **одна финализированная**
запись positions-history, чей `realizedPnl` **кумулятивен по ВСЕМ**
partial-закрытиям и доборам за жизнь **этой позиции**; читается
**финализированной** (позиция полностью закрыта / flat по
`REFRESH_POSITION_COMMAND`).

**Сделка может содержать несколько эпизодов** и, значит, несколько
записей в окне (§«История закрытых позиций»); `Deal.resultProfit` — Σ по
ним (`docs/decisions/multi-episode-deal.md`). Прежняя формулировка «одна
**сделка** ↔ один `posId`» снята вместе с одноэпизодной посылкой (T3
`DOCS_CHECK_18`).

**Помечено как предположение** до рантайм-верификации (контур source-api,
demo, `.claude/tests/source-api/okx/plan.md` §AG1). Верифицировать:
агрегирует ли OKX partial-выходы (partial TP `type` 1 → SL `type` 2) в
**одну** запись на `posId`, в какой момент запись **финализирована**
(§AG1.5) и отдаёт ли окно с несколькими эпизодами **отдельную** запись на
каждый `posId` (§AG1.9). Риск чтения нефинализированной / послайсовой
записи → **систематический недосчёт realized** (левый хвост
R-распределения усечён молча). **Гейтит корректность числа**
`Deal.resultProfit` → верификация до `CODE`
(`docs/decisions/pnl-finalization-mechanics.md` реш.6).

## ACK-семантика close-position

Response — ACK, не финальный статус (`docs/rules/ack-not-runtime-truth.md`).
`data[0]` содержит `instId`, `posSide`. **Нет `ordId`** и нет
финального статуса позиции — подтверждение через `REFRESH_POSITION_COMMAND`
(позиция исчезла или `pos=0`), опционально через fills и/или WS
`positions`/`orders`.
