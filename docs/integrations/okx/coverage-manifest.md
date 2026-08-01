# OKX: манифест покрытия поверхности API

## На какой вопрос отвечает этот файл

Какова полнота покрытия поверхности OKX REST API нашими
интеграционными доками — что задокументировано, что пробел, что вне
продуктового периметра.

## Внешний источник правды

Карта строится по официальному доку OKX
(`https://www.okx.com/docs-v5/en/`; changelog —
`https://www.okx.com/docs-v5/log_en/`). Синхронизация — перевыкачка
+ дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md` §4a,
канал чтения — `.claude/skills/integration-okx.md`). Последняя
сверка: 2026-06-11 (прогон 3 — поле-уровневая докачка периметра).

## Назначение

Полная карта поверхности OKX v5 REST API по разделам. Каждая строка
несёт **статус** покрытия и **провенанс** факта. Полнота проверяется
по манифесту: пустых строк нет — раздел либо покрыт, либо явно вне
периметра / отложен с причиной. Ведётся по процессу
`.claude/processes/api-docs-completion.md` (владелец — `integrator`).

### Легенда

**Статус:** `есть-док` (задокументирован) · `обновлён` / `создан` (в
этом прогоне — прогон 3, 2026-06-11) · `пробел` (в периметре, ещё не
задокументирован) · `вне-периметра` (док не заводим, с причиной) ·
`сознательно-вне` (в периметре, отложено решением, с якорем).

Дата сверки в шапке — дата **полного** свипа поверхности (прогон 3). Точечные
заходы по теме её не двигают и помечаются в примечании своей строки: заход
`GAPS_CLOSE_3` шага 7 (**2026-07-14**, пробел H1 — fee-wiring) поле-уровнево
сверил строки `Fee rates` и `Instruments`, полного свипа не делал.

**Провенанс:** `офдок` — подтверждено официальным источником OKX
(прямое чтение docs-v5 / changelog); `сторонний` — пока только из
скелета/стороннего источника, официальным доком не подтверждено;
`рантайм` (`подтверждён-прогоном`) — подтверждено живым прогоном контура
тестов API источника, **в том числе против офдока** (C3,
`.claude/decisions/source-api-target-rebase.md`). Рантайм-факт против
офдока фиксируется этим провенансом, не выдаёт себя за офдок и не
теряется (канон — `cancel-advance-algos` жив на demo вопреки офдоку
2025-04-24).

**Покрытие** (контуром тестов API источника через единственный
generic-эндпоинт `POST /api/proxy/okx/raw`, **полный in-perimeter**):
`🔴 не в плане` — in-perimeter запрос, ещё не покрыт; `🟡 в плане` —
кейсы в плане (этап DESIGN, аппрувнут); `🟢 в коде` — есть прошедшие
ревью код-тесты (этап CODE-тесты); `⚪ не-runtime` — эндпоинт **в периметре
доков, но runtime фазы 1 его не вызывает** (контракт держим справочно;
покрытие контуром не требуется — покрывать нечего в рантайме); `—` — вне
периметра (не покрывается). Метка `⚪ не-runtime` добавлена в легенду на
`GAPS_CLOSE_6` (H21): она уже применялась в таблице Trade (строки Fills),
но легендой определена не была. **Покрытие не зависит от наличия типизированного метода
клиента:** строки без метода клиента покрываются через `/raw` (тело
запроса строится руками по контракту). Прежняя метка `⚪ gap`
(«in-perimeter без метода клиента, покрыть нельзя») **снята** — под
`/raw` таких нет (`.claude/decisions/source-api-target-rebase.md`,
раздел D). Стадии и продвижение колонки —
`.claude/processes/source-api-testing.md` §Колонка покрытия. Текущая
отметка отражает код-тесты
`src/test/java/com/example/tradingbot/integration/sourceapi/okx/`
(**полное in-perimeter покрытие через `/raw`**, этап CODE-тесты,
2026-06-20): все 60 in-perimeter строк — `🟢 в коде`. План —
`.claude/tests/source-api/okx/plan.md` (DESIGN аппрувнут 2026-06-19).

### Канал чтения офдока

Источниковый дефицит прогона 2 («SPA нечитаем») закрыт
самообслуживанием: страница — статический HTML, канал — сырой fetch
+ локальный парсинг (см. `.claude/skills/integration-okx.md` §Канал
чтения, `.claude/decisions/integrator-agent.md` §Канал и хранение).
Строки `пробел` периметра докачаны до поле-уровневых доков в
прогоне 3.

## Продуктовый периметр

- **В периметре:** Trade, Algo Trading, Account, Market Data, Public
  Data; Funding/Asset — **по потребности**.
- **Вне периметра:** Sub-account, Grid, Recurring buy, Copy Trading,
  Spread/Block Trading (RFQ), Broker, Earn/Finance/Staking/Savings,
  Convert, Fiat/P2P, Affiliate, Status — не нужны алготрейдинг-боту
  фазы 1.
- **WebSocket** — `сознательно-вне`, якорь **OKX-Q4** (рубеж).

## Trade (`/api/v5/trade/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Place order | POST `/trade/order` | есть-док | 🟢 в коде | офдок | `contracts/order.md`, `OkxOrderResponse`; `placeOrder` (limit/market) |
| Place batch orders | POST `/trade/batch-orders` | **создан** | 🟢 в коде | офдок | `batch-operations.md`; до 20, лимит считается ордерами; **В-4 рассмотрено, не берём** — метода клиента нет |
| Cancel order | POST `/trade/cancel-order` | есть-док | 🟢 в коде | офдок | `order.md`; `cancelOrder` |
| Cancel batch orders | POST `/trade/cancel-batch-orders` | **создан** | 🟢 в коде | офдок | `batch-operations.md`; В-4 — метода клиента нет |
| Amend order | POST `/trade/amend-order` | есть-док | 🟢 в коде | офдок | `order.md`; доменом не используется — REPLACE-only (`replace-not-amend`); метода клиента нет |
| Amend batch orders | POST `/trade/amend-batch-orders` | **создан** | 🟢 в коде | офдок | `batch-operations.md`; В-4 — метода клиента нет |
| Close position | POST `/trade/close-position` | есть-док | 🟢 в коде | офдок | `position.md`; `closePosition` |
| Order details | GET `/trade/order` | есть-док | 🟢 в коде | офдок | `order.md`; `getOrder` |
| Pending orders | GET `/trade/orders-pending` | есть-док | 🟢 в коде | офдок | `order.md` (звено evidence-cycle); `getPendingOrders` |
| Order history 7d | GET `/trade/orders-history` | есть-док | 🟢 в коде | офдок | `order.md`; `getOrderHistory` |
| Order history 3m | GET `/trade/orders-history-archive` | есть-док | 🟢 в коде | офдок | `order.md`; архив 3м, метода клиента нет |
| Fills 3d | GET `/trade/fills` | есть-док | ⚪ не-runtime | офдок | `fills.md`, `OkxFillResponse`; **`REFRESH_FILLS` снимается** на `CODE` шага 7 (`pnl-finalization-mechanics.md`; в коде команда пока жива — H15 `GAPS_CLOSE_6`) ⇒ эндпоинт в целевом runtime фазы 1 не используется (order-fill-метрики из `OkxOrderResponse`); контракт справочно |
| Fills 3m | GET `/trade/fills-history` | есть-док | ⚪ не-runtime | офдок | `fills.md`; **не используется** в целевом runtime (снятие `REFRESH_FILLS` — на `CODE` шага 7); справочно |
| Mass cancel | POST `/trade/mass-cancel` | **вне-периметра** | — | офдок | прогон 3: только MMP-ордера, Option в Portfolio Margin — не кейс SWAP-бота (прежний статус `пробел` снят) |
| Cancel All After (DMS) | POST `/trade/cancel-all-after` | **создан** | 🟢 в коде | офдок | `cancel-all-after.md`; **В-1** → шаг 8 (safety); метода клиента нет |
| Order precheck | POST `/trade/order-precheck` | **создан** | 🟢 в коде | офдок | `order-precheck.md`; **В-2** → шаг 5; ⚠ только acctLv 3/4 (MCM/PM); метода клиента нет |
| Account rate limit | GET `/trade/account-rate-limit` | **создан** | 🟢 в коде | офдок | `account-rate-limit.md`; fill-ratio-based лимит; метода клиента нет |
| Easy convert / one-click repay | GET/POST `/trade/easy-convert*`, `/one-click-repay*` | вне-периметра | — | офдок | конвертация/репэй, не торговый цикл |

## Algo Trading (`/api/v5/trade/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Place algo order | POST `/trade/order-algo` | есть-док | 🟢 в коде | офдок | `algo-order.md`; ordType: conditional/oco/trigger/`chase`(новый)/move_order_stop/iceberg/`smart_iceberg`/twap; `placeAlgoOrder` строит conditional/oco/move_order_stop (вариант-gap: trailing-value `callbackSpread`) |
| Cancel algo (ordinary) | POST `/trade/cancel-algos` | **обновлён** | 🟢 в коде | офдок | `algo-order.md`; **И-1 закрыт (а)** — ветвление по семье; `cancelAlgos` |
| Cancel advance algo | POST `/trade/cancel-advance-algos` | **обновлён** | 🟢 в коде | офдок | **И-2:** выведен из офдока (changelog 2025-04-24); advance-ветка И-1(а) — runtime-подтверждение; `cancelAdvanceAlgos` |
| Amend algo | POST `/trade/amend-algos` | **обновлён** | 🟢 в коде | офдок | только Stop/Trigger; advance не амендится — **И-3** (следствие закрыто: REPLACE-only); доменом не используется; метода клиента нет |
| Algo details | GET `/trade/order-algo` | есть-док | 🟢 в коде | офдок | `algo-order.md`; обе семьи видны; `getAlgoOrder` |
| Algo pending | GET `/trade/orders-algo-pending` | есть-док | 🟢 в коде | офдок | `algo-order.md` (звено); ordType обеих семей; `getPendingAlgoOrders` |
| Algo history 3m | GET `/trade/orders-algo-history` | **обновлён** | 🟢 в коде | офдок | `state`: effective/canceled/order_failed (дрейф: `partially_failed` ушёл из офдока); `getAlgoOrderHistory` |

## Account (`/api/v5/account/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Get balance | GET `/account/balance` | есть-док | 🟢 в коде | офдок | `balance.md`, `OkxBalanceResponse`; `getBalance` |
| Get positions | GET `/account/positions` | есть-док | 🟢 в коде | офдок | `position.md`, `OkxPositionResponse`; `getPositions` |
| Positions history | GET `/account/positions-history` | **обновлён** | 🟢 в коде | офдок | `position.md` §История; **В-3 закрыт**: источник числа `resultProfit` (net `realizedPnl`, + `closeAvgPx`); **`GAPS_CLOSE_2`:** native `OkxPositionsHistoryResponse` создан, снапшот `PositionCloseResult`, команда **`REFRESH_POSITIONS_HISTORY`** (`pnl-finalization-mechanics.md`); инвариант агрегации — рантайм-верификация (N11, `.claude/tests/source-api/okx/plan.md` §AG1); пагинация по `uTime`; метода клиента нет |
| Account & position risk | GET `/account/account-position-risk` | **создан** | 🟢 в коде | офдок | `account-position-risk.md`; единый временной срез; метода клиента нет |
| Bills 7d | GET `/account/bills` | есть-док | 🟢 в коде | офдок | `account-bills.md`, `OkxAccountBillResponse`; **`GAPS_CLOSE_2`:** команда **`REFRESH_BILLS`** → `DealCashFlow` (разбивка P&L; `DealCashFlow.md`), линковка по окну+`instId`+`ccy`, дедуп по `billId`; метода клиента нет |
| Bills archive 3m | GET `/account/bills-archive` | **обновлён** | 🟢 в коде | офдок | `account-bills.md`; поле-уровнево сверен (прогон 3); метода клиента нет |
| Bills deep-архив (с 2021) | POST+GET `/account/bills-history-archive` | **создан** | 🟢 в коде | офдок | `account-bills.md` §Deep-архив; поквартально, async-файл; 12 заявок/сутки; метода клиента нет. **Success-контракт на demo неверифицируем** (заявка → `50026`, GET → `51604`): прямой кейс проверяется **на проде ад-хок, вне контура** — зелёный контур-тест подтверждает только demo-реджект, не success |
| Bill types | GET `/account/subtypes` | **создан** | 🟢 в коде | офдок | `account-bills.md` §Справочник bill types; метода клиента нет |
| Account config | GET `/account/config` | **создан** | 🟢 в коде | офдок | `account-config.md`; **В-9** → шаг 5 / bootstrap; `getAccountConfig` (диагностический сырой String) |
| Set position mode | POST `/account/set-position-mode` | **создан** | 🟢 в коде | офдок | `account-config.md`; метода клиента нет |
| Set leverage | POST `/account/set-leverage` | **создан** | 🟢 в коде | офдок | `account-config.md`; INSTR-Q2; метода клиента нет |
| Leverage info | GET `/account/leverage-info` | **создан** | 🟢 в коде | офдок | `account-config.md`; метода клиента нет |
| Max order size | GET `/account/max-size` | **создан** | 🟢 в коде | офдок | `max-size.md`; метода клиента нет |
| Max avail size | GET `/account/max-avail-size` | **создан** | 🟢 в коде | офдок | `max-size.md`; метода клиента нет |
| Fee rates | GET `/account/trade-fee` | **обновлён** | 🟢 в коде | офдок | `trade-fee.md`; **В-7 активирован** (G6): ставка прогнозной комиссии в риск-сайзинге; **`GAPS_CLOSE_3` (H1, сверка 2026-07-14):** native `OkxTradeFeeResponse` создан, дом ставки — **`TradeFeeRate`** (отдельная модель, строка на группу; на навесе остался лишь ключ `externalFeeGroupId`), ось запроса — группа (`instType`), резолв — пара (`instType`,`groupId`), перечень групп не хардкодим; дочитывает `InstrumentExternalRulesSyncJob`; **`GAPS_CLOSE_4`:** ось резолва — **сырая** пара (`externalInstrumentType`,`externalFeeGroupId`), не доменный enum (H7); поверхность чтения (аксессор `takerFeeRate()`) не двинулась, троп чтения навеса две, гидрирует хранилищный слой `InstrumentExternalRulesDataService` (H1); контур — **SWAP-only**, один вызов `instType=SWAP` (H8); `level` — часть значения группы (смена → новая строка, H11); знак источника (минус = комиссия) **снимается при маппинге** `× −1`, ниже маппинга ставка — издержка (H2, `mapping/TradeFeeRate.md` §«Знак ставки — снимается здесь»); несвежесть → холд **инструментов группы**, не биржи (H3/H4); **`GAPS_CLOSE_5`:** реакция на несвежесть — **мягкая**, kill-switch снят, живые сделки доживают (H2); радиус — по режиму отказа, «синк выключен» не источник холда (H4); **`GAPS_CLOSE_6`:** снятие мягкого холда — **вручную** (H2), enforcement — отдельный статус `Instrument.Status.ENTRY_BLOCKED` (H3), свежесть меряется у **обеих** половин резолва — значения ставки и ключа группы на навесе (H9); дрейф офдока: `instType` включает EVENTS, поле `settle` (EVENTS-only), «Open API will not reflect zero-fee trading», инвариант organic-base-rates; upcoming `elpMaker`→`rpiMaker` (прод 2026-07-28) — unused, не гейтит; wiring — шаг 7 CODE |
| Instruments (private) | GET `/account/instruments` | вне-периметра | — | офдок | инвентарь с учётом режима счёта; используем публичный `public/instruments` |
| Interest / borrow-repay / VIP loan / spot-margin | various | вне-периметра | — | офдок | margin/loan вне скоупа SWAP-бота фазы 1 |
| Greeks / isolated-mode / MMP / move-positions / collateral / account-mode-switch / прочее сервисное | various | вне-периметра | — | офдок | опционы / PM-сервис / переносы — вне торгового цикла фазы 1 |

## Market Data (`/api/v5/market/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Tickers | GET `/market/tickers` | есть-док | 🟢 в коде | офдок | `market-price-data.md`; клиент строит только одиночный `getTicker`, плюрал-эндпоинта нет |
| Ticker | GET `/market/ticker` | есть-док | 🟢 в коде | офдок | `market-price-data.md`, `OkxTickerResponse`; `getTicker` |
| Candles | GET `/market/candles` | есть-док | 🟢 в коде | офдок | `candle.md`, `CandleOkxResponse`; `getLatestCandles` |
| History candles | GET `/market/history-candles` | есть-док | 🟢 в коде | офдок | `candle.md`; `getHistoryCandles` |
| Order book | GET `/market/books` | **создан** | 🟢 в коде | офдок | `order-book.md`; ≤ 400 уровней; фазе 1 не нужен (стратегия на свечах); метода клиента нет |
| Order book full | GET `/market/books-full` | **создан** | 🟢 в коде | офдок | `order-book.md`; ≤ 5000 уровней; метода клиента нет |
| Public trades | GET `/market/trades` | **создан** | 🟢 в коде | офдок | `public-trades.md`; ≤ 500; метода клиента нет |
| Trades history | GET `/market/history-trades` | **создан** | 🟢 в коде | офдок | `public-trades.md`; 3 месяца; метода клиента нет |
| Index tickers | GET `/market/index-tickers` | **создан** | 🟢 в коде | офдок | `index-data.md`; метода клиента нет |
| Index candles | GET `/market/index-candles` | **создан** | 🟢 в коде | офдок | `index-data.md`; 1440 точек; метода клиента нет |
| Index candles history | GET `/market/history-index-candles` | **создан** | 🟢 в коде | офдок | `index-data.md`; метода клиента нет |
| Mark price candles | GET `/market/mark-price-candles` | **создан** | 🟢 в коде | офдок | `mark-price.md`; релевантно `tpTriggerPxType=mark`; метода клиента нет |
| Mark price candles history | GET `/market/history-mark-price-candles` | **создан** | 🟢 в коде | офдок | `mark-price.md`; метода клиента нет |
| Platform 24h volume | GET `/market/platform-24-volume` | вне-периметра | — | офдок | агрегат платформы |
| Option trades / call auction | various | вне-периметра | — | офдок | OPTION / аукцион — вне SWAP-скоупа |
| SBE Market Data | various | вне-периметра | — | офдок | бинарный фид (HFT) — вне скоупа |

## Public Data (`/api/v5/public/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Instruments | GET `/public/instruments` | **обновлён** | 🟢 в коде | офдок | `instrument.md`, `InstrumentOkxResponse`; `getInstruments`; **`GAPS_CLOSE_3` (H1):** `groupId` переведён в used — ключ комиссионной группы (пара `instType`+`groupId` резолвит ставку `trade-fee`); прежде числился среди неиспользуемых |
| Mark price | GET `/public/mark-price` | **создан** | 🟢 в коде | офдок | `mark-price.md`; **В-8** → шаг 5; метода клиента нет |
| Price limit | GET `/public/price-limit` | **создан** | 🟢 в коде | офдок | `price-limit.md`; **В-8** → шаг 5; метода клиента нет |
| Funding rate | GET `/public/funding-rate` | **создан** | 🟢 в коде | офдок | `funding-rate.md`; **В-6/OKX-Q3 разрешены** (GAPS_CLOSE_1 шага 7): funding в P&L — через bills + positions-history, не через ставки; интервал по `fundingTime`↔`nextFundingTime`; метода клиента нет |
| Funding rate history | GET `/public/funding-rate-history` | **создан** | 🟢 в коде | офдок | `funding-rate.md`; **не источник числа** `resultProfit` (funding — через bills/positions-history, `result-profit-source.md`); лишь прогноз/сверка; метода клиента нет |
| Open interest | GET `/public/open-interest` | **создан** | 🟢 в коде | офдок | `open-interest.md`; метода клиента нет |
| Position tiers | GET `/public/position-tiers` | **создан** | 🟢 в коде | офдок | `position-tiers.md`; **находка прогона 3:** путь public, не `account/` (сторонний скелет ошибался); метода клиента нет |
| Server time | GET `/public/time` | **создан** | 🟢 в коде | офдок | `server-time.md`; синхронизация подписи; метода клиента нет |
| Insurance fund | GET `/public/insurance-fund` | **создан** | 🟢 в коде | офдок | `insurance-fund.md` (офдок: «security fund»); метода клиента нет |
| Delivery/exercise, settlement, estimated price, underlying, discount-rate, premium history, exchange-rate, index-components, tick bands, series/events/markets (EVENTS), economic calendar, historical market data | various | вне-периметра | — | офдок | FUTURES delivery / OPTIONS / EVENTS / индекс-сервисы — вне скоупа SWAP |

## Funding / Asset (`/api/v5/asset/`) — по потребности

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Currencies / balances / transfer / transfer-state / asset-bills / deposit-* / withdrawal-* | various | сознательно-вне | — | сторонний | По потребности. Бот фазы 1 торгует на торговом счёте; фондовые переводы/пополнения/выводы не нужны. Заводить при появлении потребности. |

## Прочие разделы — вне периметра

`Sub-account`, `Grid Trading`, `Recurring buy`, `Copy Trading`,
`Spread Trading`, `Block Trading (RFQ)`, `Broker (ND/FD)`,
`Earn / Finance / Staking / Savings / On-chain`, `Convert`,
`Fiat / P2P`, `Affiliate`, `Status` — все `вне-периметра`
(`сторонний` скелет): не входят в продуктовый периметр алготрейдинг-бота
фазы 1. Доки не заводим.

## WebSocket — сознательно-вне

Все WS-каналы (public: `tickers`/`books`/`candles`/…; private:
`account`/`positions`/`orders`/`orders-algo`/`balance_and_position`;
trade: order ops по WS) — `сознательно-вне`, якорь **OKX-Q4** (рубеж).
Лимиты WS частично задокументированы — `rules/ws-limits.md`
(`есть-док`). Прогон 3: канал advance algo orders в офдоке
по-прежнему есть (семья advance жива в WS, при том что REST
`cancel-advance-algos` из дока выведен — контекст И-2).

## Инфраструктурные доки (вне разбивки по эндпоинтам)

`contracts/service-urls.md` (базовые URL/окружения; AWS-домены
выведены — changelog 2025-04-28), правила `rules/adapter-constants.md`,
`rules/timeframe-constants.md`, `rules/reduce-only-invariant.md`,
`contracts/fills-archive.md` + `OkxFillsArchiveResponse` (архив 3m+,
`сознательно-вне` активного — **OKX-Q2**, шаг 7) — `есть-док`.

## Связи

- Процесс — `.claude/processes/api-docs-completion.md`.
- Скилл OKX (канал чтения, command-relevant разделы, конвенции,
  ограничения) — `.claude/skills/integration-okx.md`.
- Отчёт прогона 2 (скан + эскалация источникового дефицита) —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-integrator-run-2.md`.
- Отчёт прогона 3 (докачка офдок-grade, находки И-2/И-3) —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-integrator-run-3.md`.
- Контракты и нативные модели — `docs/integrations/okx/contracts/`,
  `docs/models/integrations/okx/`.
