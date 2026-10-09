# OKX contracts: ordinary order

## На какой вопрос отвечает этот файл

Каков контракт операций по ordinary order.

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, раздел «Order
Book Trading → Trade»). Процедура сверки и приоритет офдока —
`.claude/rules/external-source-sync.md`. Последняя сверка: 2026-06-11
(соответствие спеке подтверждено); 2026-10-08 — точечно момент постановки
встроенной защиты (сверх раздела «Place order» — объявление OKX от
2026-07-01; адреса — в разделе о встроенной защите ниже).

## Единица размера (`sz`, `accFillSz`) — контракты у SWAP/FUTURES

**Открытая сверка `integrator`**. Дистиллят офдока
единицу `sz` / `accFillSz` не называет, а поле несущее: у нас
`accumulatedFillSize` входит операндом в три из четырёх чисел риска
через отношение к `plannedSizeContracts`
(`docs/models/domain/aggregate/Deal.md`). Действующая
запись — «для SWAP/FUTURES контракты»
(`docs/models/domain/core/Order.md`), и она **предположение**
до наблюдения.

- **Проверка — на уже существующих кейсах**: сопоставить
  отправленный `sz` с `accFillSz` исполненного ордера и с `pos` записи
  positions-history — совпадение по величине подтверждает единицу
  «контракты» на всех трёх поверхностях.
- **Цена ошибки — направленная и тихая:** если `accFillSz` придёт в
  базовой валюте, отношение к `plannedSizeContracts` (контракты)
  завысит или занизит `incurredRiskAmount` в `ctVal` раз, и ни одна
  проверка этого не увидит — обе величины положительны и правдоподобны.

## Endpoints

- **Create** (`SUBMIT_ORDER_COMMAND`): `POST /api/v5/trade/order`. Permission
  `Trade`; rate limit 60 req / 2 s по User ID + Instrument ID.
- **Amend** (доменом **не используется** — REPLACE-only,
  `docs/rules/replace-not-amend.md`; контракт — поверхность
  биржи): `POST /api/v5/trade/amend-order`. Permission `Trade`; rate
  limit 60 req / 2 s по User ID + Instrument ID.
  `newPx`/`newSz`/`attachAlgoOrds` — изменения должны включать
  уже исполненную часть для `partially_filled`. `cxlOnFail` (boolean)
  — биржа отменит ордер, если amend упал. `pxAmendType=0|1` — `1`
  разрешает автокорректировку цены в допустимый диапазон.
- **Cancel** (`CANCEL_ORDER_COMMAND`): `POST /api/v5/trade/cancel-order`.
  Permission `Trade`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Body: `instId` + одно из `ordId` / `clOrdId` (если оба — биржа
  использует `ordId`).
- **Order details** (`REFRESH_ORDER_COMMAND`): `GET /api/v5/trade/order`.
  Permission `Read`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Query: `instId` обязателен, одно из `ordId` / `clOrdId`. Если
  оба — биржа возвращает по `ordId`. Если `clOrdId` переиспользован,
  биржа возвращает **последний** ордер с этим `clOrdId`. **Ненайденный
  ордер приходит отказом, а не пустыми данными:** `code=51603`
  («Order does not exist»), `data` пуст (провенанс `рантайм`: наблюдено на
  demo 2026-06-20 поиском по несуществующему `clOrdId`). Граница читает его
  как «не найдено», а не как отказ: по нему отправка ставит неотправленную
  ногу, а цикл добычи идёт к живым заявкам и истории.
- **Pending** (звено цикла `REFRESH_ORDER_COMMAND`): `GET /api/v5/trade/orders-pending`.
  Permission `Read`; rate limit 60 req / 2 s по User ID. Фильтры:
  `instType`, `instId`, `ordType`, `state` (`live`/`partially_filled`),
  пагинация `after`/`before` по `ordId`, `limit` ≤ 100. **Применяется и
  счёт-широко** (`instType=SWAP`, `instId` не задаётся): срез живых
  заявок счёта читает проактивная детекция
  (`docs/components/AnomalyJob.md`), и лимит здесь по User ID, а не по
  инструменту — поштучный обход рос бы с числом инструментов и выбирал
  бы ту же корзину, что торговая петля. `limit` задаётся явно потолком
  страницы: полная страница означает усечение, и оно обязано быть
  наблюдаемым.
- **History** (звено цикла `REFRESH_ORDER_COMMAND`):
  `GET /api/v5/trade/orders-history` (последние 7 дней; permission
  `Read`; rate limit 40 req / 2 s по User ID),
  `GET /api/v5/trade/orders-history-archive` (последние 3 месяца; rate
  limit 20 req / 2 s по User ID). Отменённые без исполнений хранятся в
  `orders-history` только ~2 часа. Фильтры: `instType` (обязателен в
  history), `instId`, `ordType`, `state` (`filled`/`canceled`/
  `mmp_canceled`), `category` (`normal`/`adl`/`full_liquidation`/
  `partial_liquidation`/`delivery`/`twap` и др.), `begin`/`end` по
  `cTime` (только в history-7d), пагинация `after`/`before` по `ordId`,
  `limit` ≤ 100.

## Встроенная защита: когда площадка её ставит

**Пока родитель жив — `live` либо `partially_filled`, — защиты на площадке
нет: налитая часть живого родителя стопа не имеет.** Площадка ставит
защиту самостоятельной условной заявкой в один из двух моментов и на
объём налива; без налива защита не ставится вовсе. Правило одно для
встроенного стопа и встроенного трейлинга (объявление ниже называет оба).

| Состояние родителя | Защита на площадке | Провенанс |
|---|---|---|
| жив без налива (`live`) | нет; стои́т элементом `attachAlgoOrds` в теле родителя | офдок; наблюдено 2026-08-30 (C17a) |
| жив с частичным наливом (`partially_filled`) | **нет** — момент постановки ещё не наступил | офдок, **перечислением** моментов постановки; demo-прогоном не наблюдено |
| исполнен целиком (`filled`) | ставится, объём — налив родителя | офдок; demo-прогоном не наблюдено |
| снят с непустым наливом (`canceled`, `accFillSz > 0`) — и нашей отменой, и снятием остатка площадкой (`ioc`) | ставится, объём — налив родителя; **кроме родителя со Split TP** — у него защита ставится только на полном наливе (оговорка ниже) | объявление OKX 2026-07-01; наблюдено 2026-08-30 (C17b, C17c) |
| снят без налива | не ставится; элемент остаётся в теле снятого родителя | офдок; наблюдено 2026-08-30 (C17a) |

**Первоисточники.**

- Офдок, «Order Book Trading → Trade → POST / Place order»
  (`https://www.okx.com/docs-v5/en/#order-book-trading-trade-post-place-order`),
  примечание «For placing order with TP/SL»: «Attached TP/SL orders become
  active only after the parent order is filled. If the parent is cancelled
  before any fill, the attached TP/SL is also discarded» и «TP/SL algo order
  will be generated only when this order is filled; if the parent order is
  cancelled before any fill, no TP/SL algo order will be generated». Поле
  `attachAlgoClOrdId` того же раздела: «It will be posted to algoClOrdId
  when placing the attached algo order once the general order is filled
  completely».
- Объявление OKX «OKX Announcement on Optimizing Order Placement for Order
  Attached Take-Profit Stop-Loss», 2026-07-01
  (`https://www.okx.com/en-gb/help/okx-announcement-on-optimizing-order-placement-for-order-attached-take`):
  до обновления защита ставилась «only … after the main order was fully
  filled»; после — «will be placed when the main order is partially filled
  with the remaining portion cancelled, or when the main order is fully
  filled. The TP/SL order quantity will be the actual filled amount of the
  main order. If the main order is attached with Split TP, the attached
  order will only be placed after the main order is fully filled».

**Что сказано прямо, а что выведено.** Прямо — перечень моментов постановки
(полный налив; частичный налив со снятым остатком) и объём (налив родителя).
Живой частично налитый родитель в перечень не входит, и отсутствие защиты у
него — **чтение перечня**, а не отдельная фраза офдока; фразы, которая
ставила бы защиту на налитую часть живого родителя, нет ни в разделе, ни в
логе изменений (`https://www.okx.com/docs-v5/log_en/`, сверено 2026-10-08;
само обновление 2026-07-01 лог изменений тоже не записывает).
Наблюдением состояние не подтверждено: прогон 2026-08-30 читал условные
заявки только **после** отмены родителя. Слот наблюдения — чтение
`orders-algo-pending` по `attachAlgoClOrdId` между частичным наливом и
отменой.

**Оговорка Split TP — и гард контура, на котором строка «снят с наливом —
ставится» держится.** Объявление исключает родителя со Split TP — несколькими
элементами тейка со своими объёмами (`sz` элемента): такому родителю защита
ставится только на полном наливе, и снятый с частичным наливом остаётся без
неё. Безусловной строка таблицы верна потому, что контур Split TP не шлёт:
встроенная защита ноги — **один элемент, только стоп** (`slTriggerPx`,
`slTriggerPxType`, `slOrdPx`), без полей тейка и без `sz`. Держится это сегодня
построением, а не проверкой: создатель заявки собирает ровно один элемент
защиты, а элемент запроса площадки полей тейка и объёма не несёт. Появление
тейка либо объёма во встроенной защите снимает гард, и тогда строка таблицы
обязана получить ветвь по составу элемента — а постановка при снятии с наливом
перестаёт быть безусловной у всех читателей момента постановки (судьба защиты
по фактам родителя — `docs/lifecycles/Order.md`).

**Текст офдока отстаёт от объявления, и это не рантайм-расхождение.**
Описание поля `attachAlgoClOrdId` по-прежнему говорит «once the general
order is filled completely» — редакция до обновления 2026-07-01. Постановку
на снятии с наливом подтверждают и примечание того же раздела («cancelled
before any fill» — оговорка, имеющая смысл, только если снятие **после**
налива защиту ставит), и объявление, и наблюдение 2026-08-30.

**Что из этого следует для потребителя** (правила — у своих домов, здесь не
переписываются): живая входная нога с частичным наливом несёт **голую**
налитую экспозицию, хотя элемент защиты стои́т в её теле; закрывает это
окно только терминал родителя. Дом судьбы защиты по фактам родителя —
жизненный цикл заявки (`docs/lifecycles/Order.md`).

## ACK-семантика

ACK любой create/amend/cancel (`sCode=0`) не является runtime truth
(`docs/rules/ack-not-runtime-truth.md`). Финальные статусы
подтверждаются через order details / pending / history / archive.

### Create response (ACK)

`POST /trade/order` → `data[0]` с `ordId`, `clOrdId`, `tag`, `ts`
(когда OKX закончил обработку), `sCode`, `sMsg`. Top-level
`inTime`/`outTime` — диагностические времена REST-шлюза
(микросекунды), в домен не маппятся. `ordId` после successful submit
сохраняется как `Order.externalId`; статус — `PENDING` до
refresh/search/history.

### Amend response (ACK)

`POST /trade/amend-order` → `data[0]` с `ordId`, `clOrdId`, `reqId`
(если был передан), `ts`, `sCode`, `sMsg`. `sCode=0` — запрос
принят, не «изменение подтверждено». Подтверждение — через
`REFRESH_ORDER_COMMAND` или WS `orders`.

### Cancel response (ACK)

`POST /trade/cancel-order` → `data[0]` с `ordId`, `clOrdId`, `sCode`,
`sMsg`. `sCode != 0` — отказ (ордер уже filled/canceled/не найден).
Финальное `CANCELED` — через refresh / WS.

## Пагинация

`after`/`before` — якорь по `ordId` (не времени), `limit ≤ 100`. Для
глубокой выкачки: `after = min(ordId)` ответа → следующая страница.
История 7 дней дополнительно поддерживает `begin`/`end` по `cTime`
(ms).

## Evidence-cycle

Состав цикла и параметры каждого источника — **дом
`docs/models/mapping/Order.md`**, раздел evidence-cycle; здесь перечень не
дублируется. Цикл добычи материализованной защиты адресует условные заявки и живёт в
контракте `docs/integrations/okx/contracts/algo-order.md`.
