# Материал для кейсов чёрного ящика `connector-okx`

## На какой вопрос отвечает этот файл

Какой материал добыт для кейсов чёрного ящика `connector-okx`.

## Откуда он и что с ним делать

Материал снят с контура тестов API источника фазы 1 при его снятии
(2026-09-16, `.claude/decisions/source-api-contour-retired.md`). Контур
проверял **сырьё** запроса и ответа через собственную тест-обращённую
поверхность; такой поверхности у сервисов нет, и работа с OKX проверяется
как чёрный ящик `connector-okx` против двух мишеней — стаба с записанными
ответами площадки и живого demo-окружения.

**Это вход под-шага «тест-кейсы», а не сами кейсы.** Кейсы пишутся от
построенного (`.claude/skills/test-design.md`); здесь — периметр, который
они обязаны накрыть, инвариант, который обязан держать stateful-кейс, и
тропа находки, которую кейс порождает.

## 1. Периметр источника — что кейсы обязаны накрыть

**Правило покрытия:** на каждую строку периметра — прямой кейс (если
достижим) плюс негатив. Запрос с полем-дискриминатором, по которому
ветвится поведение (`ordType`, `conditionType`, семья cancel), покрывается
по каждому используемому варианту — тоже прямой плюс негатив.

**Гейт достижимости.** Архивные окна (`orders-history-archive`,
`fills-history`, закрытые позиции старше ~30 дней) в свежем demo
недостижимы: прямой кейс снимается с явным отказом и причиной, покрытие
остаётся негативом, ожидание не выдумывается.

### Trade

`POST /trade/order` · `POST /trade/batch-orders` · `POST /trade/cancel-order` ·
`POST /trade/cancel-batch-orders` · `POST /trade/amend-order` ·
`POST /trade/amend-batch-orders` · `POST /trade/close-position` ·
`GET /trade/order` · `GET /trade/orders-pending` · `GET /trade/orders-history` ·
`GET /trade/orders-history-archive` · `GET /trade/fills` ·
`GET /trade/fills-history` · `POST /trade/cancel-all-after` ·
`POST /trade/order-precheck` · `GET /trade/account-rate-limit`

### Algo Trading

`POST /trade/order-algo` · `POST /trade/cancel-algos` ·
`POST /trade/cancel-advance-algos` · `POST /trade/amend-algos` ·
`GET /trade/order-algo` · `GET /trade/orders-algo-pending` ·
`GET /trade/orders-algo-history`

### Account

`GET /account/balance` · `GET /account/positions` ·
`GET /account/positions-history` · `GET /account/account-position-risk` ·
`GET /account/bills` · `GET /account/bills-archive` ·
`POST+GET /account/bills-history-archive` · `GET /account/subtypes` ·
`GET /account/config` · `POST /account/set-position-mode` ·
`POST /account/set-leverage` · `GET /account/leverage-info` ·
`GET /account/max-size` · `GET /account/max-avail-size` ·
`GET /account/trade-fee`

### Market Data

`GET /market/tickers` · `GET /market/ticker` · `GET /market/candles` ·
`GET /market/history-candles` · `GET /market/books` ·
`GET /market/books-full` · `GET /market/trades` ·
`GET /market/history-trades` · `GET /market/index-tickers` ·
`GET /market/index-candles` · `GET /market/history-index-candles` ·
`GET /market/mark-price-candles` · `GET /market/history-mark-price-candles`

### Public Data

`GET /public/instruments` · `GET /public/mark-price` ·
`GET /public/price-limit` · `GET /public/funding-rate` ·
`GET /public/funding-rate-history` · `GET /public/open-interest` ·
`GET /public/position-tiers` · `GET /public/time` ·
`GET /public/insurance-fund`

### Чего в периметре нет

Sub-account, Grid, Recurring buy, Copy Trading, Spread/Block Trading (RFQ),
Broker, Earn/Finance/Staking/Savings, Convert, Fiat/P2P, Affiliate, Status,
маржинальные займы и сервисные операции режима счёта, опционные разделы,
SBE-фид, Funding/Asset (`/api/v5/asset/`) — алготрейдингу не нужны.
**WebSocket** остаётся сознательно-вне до своего хода
(`.claude/work/backlog.md` §«WS-слушатель фактов исполнения — по добытому
контракту каналов», §«Сбор ликвидаций — по добытому контракту WS-канала»).

Контракты и лимиты каждой операции — `docs/integrations/okx/contracts/`;
формы ответов — `docs/models/integrations/okx/`. Этот перечень их не
дублирует: он отвечает на «что обязано быть накрыто», а не «как устроено».

## 2. Инвариант восстановления состояния stateful-кейса

**Кейс, которому нужна сущность на бирже, создаёт её сам** — ордер,
позицию, algo: иначе её не проверить. Отсюда четыре требования, и они
переносятся на кейсы чёрного ящика против demo целиком.

- **Предусловия идут цепочкой по графу, а не плоской матрицей:** выход
  одного кейса — предусловие следующего (`place → getOrder → cancel →
  негативный getOrder`). Негатив, которому состояние не нужно, — отдельный
  дешёвый слой, гоняется первым.
- **Teardown живёт внутри кейса**, а не общим финалом прогона: кейс обязан
  оставлять биржу в том состоянии, в котором её застал.
- **Авторитет «вернулось ли» — проверка конца, а не teardown-хелперы.**
  Единая проверка накрывает и сущности, и настройки счёта (`acctLv`,
  `leverage`, `posMode`): невозврат к старту — жёсткий отказ плюс
  принудительная зачистка; зачистка не помогла — прогон **останавливается**,
  а не идёт дальше по грязному счёту. Хелперы вида «закрыть по-тихому» —
  попытка зачистки, не доказательство возврата.
- **Порядок прогона детерминирован**, иначе остановка бессмысленна:
  следующий кейс не должен зависеть от того, кто отработал раньше.

**Цена прогона — тоже часть кейса:** цена ордера снимается с площадки, а
не выдумывается; ордер ставится заведомо неисполнимым, а где нужен факт
исполнения — минимальный market-ордер с обязательным закрытием позиции.
Запросы идут воронкой с паузой-троттлом, ответ о превышении лимита —
backoff и повтор; ожидание осадки — поллинг до таймаута кейса.

## 3. Наблюдение → находка → правка апидоков

**Живой прогон против demo — детектор дрейфа API площадки**, и его исход
не остаётся в тесте:

1. **Наблюдение** — что площадка ответила на самом деле (сырые поля, коды,
   порядок, границы окон).
2. **Находка** — расхождение наблюдения с официальной документацией
   источника. Она адресуется **дому апидока**
   (`docs/integrations/okx/contracts/`, `docs/models/integrations/okx/`), а
   не тесту: тест, подогнанный под наблюдение без правки дока, оставляет
   корпус с ложным описанием источника.
3. **Правка** — факт вносится в док с провенансом `рантайм` и датой сверки
   в разделе «Внешний источник правды»
   (`.claude/rules/external-source-sync.md` §«Рантайм-расхождение»).
   Рантайм-факт не подменяет офдок молча и не теряется.

**Записи для стаба берутся из этих же прогонов:** стаб отвечает тем, что
площадка ответила, а не тем, что мы считаем правильным.

## Связи

- Концепция тестового шага — `.claude/work/progress/phase-2-step-12-chronicle.md`
  §«Концепция».
- Форма кейса — `.claude/skills/test-design.md`; ревью кейсов —
  `.claude/skills/test-review.md`; код тестов — `.claude/skills/test-code.md`.
- Почему контур снят — `.claude/decisions/source-api-contour-retired.md`.
