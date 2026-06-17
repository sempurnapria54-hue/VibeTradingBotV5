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

**Провенанс:** `офдок` — подтверждено официальным источником OKX
(прямое чтение docs-v5 / changelog); `сторонний` — пока только из
скелета/стороннего источника, официальным доком не подтверждено;
`рантайм` (`подтверждён-прогоном`) — подтверждено живым прогоном контура
тестов API источника, **в том числе против офдока** (C3,
`.claude/decisions/source-api-target-rebase.md`). Рантайм-факт против
офдока фиксируется этим провенансом, не выдаёт себя за офдок и не
теряется (канон — `cancel-advance-algos` жив на demo вопреки офдоку
2025-04-24).

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

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Place order | POST `/trade/order` | есть-док | офдок | `contracts/order.md`, `OkxOrderResponse` |
| Place batch orders | POST `/trade/batch-orders` | **создан** | офдок | `batch-operations.md`; до 20, лимит считается ордерами; **В-4 рассмотрено, не берём** |
| Cancel order | POST `/trade/cancel-order` | есть-док | офдок | `order.md` |
| Cancel batch orders | POST `/trade/cancel-batch-orders` | **создан** | офдок | `batch-operations.md`; В-4 |
| Amend order | POST `/trade/amend-order` | есть-док | офдок | `order.md`; доменом не используется — REPLACE-only (`replace-not-amend`) |
| Amend batch orders | POST `/trade/amend-batch-orders` | **создан** | офдок | `batch-operations.md`; В-4 |
| Close position | POST `/trade/close-position` | есть-док | офдок | `position.md` |
| Order details | GET `/trade/order` | есть-док | офдок | `order.md` |
| Pending orders | GET `/trade/orders-pending` | есть-док | офдок | `order.md` (звено evidence-cycle) |
| Order history 7d | GET `/trade/orders-history` | есть-док | офдок | `order.md` |
| Order history 3m | GET `/trade/orders-history-archive` | есть-док | офдок | `order.md` |
| Fills 3d | GET `/trade/fills` | есть-док | офдок | `fills.md`, `OkxFillResponse` |
| Fills 3m | GET `/trade/fills-history` | есть-док | офдок | `fills.md` (звено) |
| Mass cancel | POST `/trade/mass-cancel` | **вне-периметра** | офдок | прогон 3: только MMP-ордера, Option в Portfolio Margin — не кейс SWAP-бота (прежний статус `пробел` снят) |
| Cancel All After (DMS) | POST `/trade/cancel-all-after` | **создан** | офдок | `cancel-all-after.md`; **В-1** → шаг 8 (safety) |
| Order precheck | POST `/trade/order-precheck` | **создан** | офдок | `order-precheck.md`; **В-2** → шаг 5; ⚠ только acctLv 3/4 (MCM/PM) |
| Account rate limit | GET `/trade/account-rate-limit` | **создан** | офдок | `account-rate-limit.md`; fill-ratio-based лимит |
| Easy convert / one-click repay | GET/POST `/trade/easy-convert*`, `/one-click-repay*` | вне-периметра | офдок | конвертация/репэй, не торговый цикл |

## Algo Trading (`/api/v5/trade/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Place algo order | POST `/trade/order-algo` | есть-док | офдок | `algo-order.md`; ordType: conditional/oco/trigger/`chase`(новый)/move_order_stop/iceberg/`smart_iceberg`/twap |
| Cancel algo (ordinary) | POST `/trade/cancel-algos` | **обновлён** | офдок | `algo-order.md`; **И-1 закрыт (а)** — ветвление по семье |
| Cancel advance algo | POST `/trade/cancel-advance-algos` | **обновлён** | офдок | **И-2:** выведен из офдока (changelog 2025-04-24); advance-ветка И-1(а) — runtime-подтверждение |
| Amend algo | POST `/trade/amend-algos` | **обновлён** | офдок | только Stop/Trigger; advance не амендится — **И-3** (следствие закрыто: REPLACE-only); доменом не используется |
| Algo details | GET `/trade/order-algo` | есть-док | офдок | `algo-order.md`; обе семьи видны |
| Algo pending | GET `/trade/orders-algo-pending` | есть-док | офдок | `algo-order.md` (звено); ordType обеих семей |
| Algo history 3m | GET `/trade/orders-algo-history` | **обновлён** | офдок | `state`: effective/canceled/order_failed (дрейф: `partially_failed` ушёл из офдока) |

## Account (`/api/v5/account/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Get balance | GET `/account/balance` | есть-док | офдок | `balance.md`, `OkxBalanceResponse` |
| Get positions | GET `/account/positions` | есть-док | офдок | `position.md`, `OkxPositionResponse` |
| Positions history | GET `/account/positions-history` | **обновлён** | офдок | `position.md` §История; **В-3** → шаг 7; пагинация по `uTime`; `realizedPnl=pnl+fee+fundingFee+liqPenalty` |
| Account & position risk | GET `/account/account-position-risk` | **создан** | офдок | `account-position-risk.md`; единый временной срез |
| Bills 7d | GET `/account/bills` | есть-док | офдок | `account-bills.md`, `OkxAccountBillResponse` |
| Bills archive 3m | GET `/account/bills-archive` | **обновлён** | офдок | `account-bills.md`; поле-уровнево сверен (прогон 3) |
| Bills deep-архив (с 2021) | POST+GET `/account/bills-history-archive` | **создан** | офдок | `account-bills.md` §Deep-архив; поквартально, async-файл; 12 заявок/сутки |
| Bill types | GET `/account/subtypes` | **создан** | офдок | `account-bills.md` §Справочник bill types |
| Account config | GET `/account/config` | **создан** | офдок | `account-config.md`; **В-9** → шаг 5 / bootstrap |
| Set position mode | POST `/account/set-position-mode` | **создан** | офдок | `account-config.md` |
| Set leverage | POST `/account/set-leverage` | **создан** | офдок | `account-config.md`; INSTR-Q2 |
| Leverage info | GET `/account/leverage-info` | **создан** | офдок | `account-config.md` |
| Max order size | GET `/account/max-size` | **создан** | офдок | `max-size.md` |
| Max avail size | GET `/account/max-avail-size` | **создан** | офдок | `max-size.md` |
| Fee rates | GET `/account/trade-fee` | **создан** | офдок | `trade-fee.md`; **В-7** → шаг 7; знак: минус = комиссия |
| Instruments (private) | GET `/account/instruments` | вне-периметра | офдок | инвентарь с учётом режима счёта; используем публичный `public/instruments` |
| Interest / borrow-repay / VIP loan / spot-margin | various | вне-периметра | офдок | margin/loan вне скоупа SWAP-бота фазы 1 |
| Greeks / isolated-mode / MMP / move-positions / collateral / account-mode-switch / прочее сервисное | various | вне-периметра | офдок | опционы / PM-сервис / переносы — вне торгового цикла фазы 1 |

## Market Data (`/api/v5/market/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Tickers | GET `/market/tickers` | есть-док | офдок | `market-price-data.md` |
| Ticker | GET `/market/ticker` | есть-док | офдок | `market-price-data.md`, `OkxTickerResponse` |
| Candles | GET `/market/candles` | есть-док | офдок | `candle.md`, `CandleOkxResponse` |
| History candles | GET `/market/history-candles` | есть-док | офдок | `candle.md` |
| Order book | GET `/market/books` | **создан** | офдок | `order-book.md`; ≤ 400 уровней; фазе 1 не нужен (стратегия на свечах) |
| Order book full | GET `/market/books-full` | **создан** | офдок | `order-book.md`; ≤ 5000 уровней |
| Public trades | GET `/market/trades` | **создан** | офдок | `public-trades.md`; ≤ 500 |
| Trades history | GET `/market/history-trades` | **создан** | офдок | `public-trades.md`; 3 месяца |
| Index tickers | GET `/market/index-tickers` | **создан** | офдок | `index-data.md` |
| Index candles | GET `/market/index-candles` | **создан** | офдок | `index-data.md`; 1440 точек |
| Index candles history | GET `/market/history-index-candles` | **создан** | офдок | `index-data.md` |
| Mark price candles | GET `/market/mark-price-candles` | **создан** | офдок | `mark-price.md`; релевантно `tpTriggerPxType=mark` |
| Mark price candles history | GET `/market/history-mark-price-candles` | **создан** | офдок | `mark-price.md` |
| Platform 24h volume | GET `/market/platform-24-volume` | вне-периметра | офдок | агрегат платформы |
| Option trades / call auction | various | вне-периметра | офдок | OPTION / аукцион — вне SWAP-скоупа |
| SBE Market Data | various | вне-периметра | офдок | бинарный фид (HFT) — вне скоупа |

## Public Data (`/api/v5/public/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Instruments | GET `/public/instruments` | есть-док | офдок | `instrument.md`, `InstrumentOkxResponse` |
| Mark price | GET `/public/mark-price` | **создан** | офдок | `mark-price.md`; **В-8** → шаг 5 |
| Price limit | GET `/public/price-limit` | **создан** | офдок | `price-limit.md`; **В-8** → шаг 5 |
| Funding rate | GET `/public/funding-rate` | **создан** | офдок | `funding-rate.md`; **В-6** → шаг 7; интервал по `fundingTime`↔`nextFundingTime` |
| Funding rate history | GET `/public/funding-rate-history` | **создан** | офдок | `funding-rate.md`; В-6 — рядом с OKX-Q3 (два пути к funding в P&L) |
| Open interest | GET `/public/open-interest` | **создан** | офдок | `open-interest.md` |
| Position tiers | GET `/public/position-tiers` | **создан** | офдок | `position-tiers.md`; **находка прогона 3:** путь public, не `account/` (сторонний скелет ошибался) |
| Server time | GET `/public/time` | **создан** | офдок | `server-time.md`; синхронизация подписи |
| Insurance fund | GET `/public/insurance-fund` | **создан** | офдок | `insurance-fund.md` (офдок: «security fund») |
| Delivery/exercise, settlement, estimated price, underlying, discount-rate, premium history, exchange-rate, index-components, tick bands, series/events/markets (EVENTS), economic calendar, historical market data | various | вне-периметра | офдок | FUTURES delivery / OPTIONS / EVENTS / индекс-сервисы — вне скоупа SWAP |

## Funding / Asset (`/api/v5/asset/`) — по потребности

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Currencies / balances / transfer / transfer-state / asset-bills / deposit-* / withdrawal-* | various | сознательно-вне | сторонний | По потребности. Бот фазы 1 торгует на торговом счёте; фондовые переводы/пополнения/выводы не нужны. Заводить при появлении потребности. |

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
