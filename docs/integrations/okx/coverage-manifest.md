# OKX: манифест покрытия поверхности API

## На какой вопрос отвечает этот файл

Какова полнота покрытия поверхности OKX REST API нашими
интеграционными доками — что задокументировано, что пробел, что вне
продуктового периметра.

## Назначение

Полная карта поверхности OKX v5 REST API по разделам. Каждая строка
несёт **статус** покрытия и **провенанс** факта. Полнота проверяется
по манифесту: пустых строк нет — раздел либо покрыт, либо явно вне
периметра / отложен с причиной. Ведётся по процессу
`.claude/processes/api-docs-completion.md` (владелец — `integrator`).

### Легенда

**Статус:** `есть-док` (задокументирован) · `обновлён` / `создан` (в
этом прогоне) · `пробел` (в периметре, ещё не задокументирован) ·
`вне-периметра` (док не заводим, с причиной) · `сознательно-вне` (в
периметре, отложено решением, с якорем).

**Провенанс:** `офдок` — существование подтверждено официальным
источником OKX (okx.com) в этом или прошлом прогоне; `сторонний` —
пока только из скелета/стороннего источника, официальным доком не
подтверждено.

### Источниковый дефицит (важно)

Официальный док OKX — JS-SPA: доступными веб-инструментами **надёжно
читается существование/путь эндпоинта** (через okx.com-поиск), но
**не поле-уровневый инвентарь**. Поэтому `офдок` здесь = подтверждённое
существование (и, как правило, путь); **полные инвентари полей для
строк `пробел` требуют чтения официального дока** и в этом прогоне не
заводились. Точные пути `сторонний`-строк — тоже к офдок-подтверждению.
Разблокировка корпуса — эскалация в отчёте прогона 2.

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
| Place batch orders | POST `/trade/batch-orders` | пробел | офдок | до 20 ордеров; скан **В-4** (batch-write) |
| Cancel order | POST `/trade/cancel-order` | есть-док | офдок | `order.md` |
| Cancel batch orders | POST `/trade/cancel-batch-orders` | пробел | офдок | скан **В-4** |
| Amend order | POST `/trade/amend-order` | есть-док | офдок | `order.md` |
| Amend batch orders | POST `/trade/amend-batch-orders` | пробел | офдок | скан **В-4** |
| Close position | POST `/trade/close-position` | есть-док | офдок | `position.md` |
| Order details | GET `/trade/order` | есть-док | офдок | `order.md` |
| Pending orders | GET `/trade/orders-pending` | есть-док | офдок | `order.md` (звено evidence-cycle) |
| Order history 7d | GET `/trade/orders-history` | есть-док | офдок | `order.md` |
| Order history 3m | GET `/trade/orders-history-archive` | есть-док | офдок | `order.md` |
| Fills 3d | GET `/trade/fills` | есть-док | офдок | `fills.md`, `OkxFillResponse` |
| Fills 3m | GET `/trade/fills-history` | есть-док | офдок | `fills.md` (звено) |
| Mass cancel | POST `/trade/mass-cancel` | пробел | офдок | существует (okx.com); кандидат скана |
| Cancel All After (DMS) | POST `/trade/cancel-all-after` | пробел | офдок | dead-man's switch; скан **В-1** (шаг 8 safety) |
| Order precheck | POST `/trade/order-precheck` | пробел | офдок | скан **В-2** (шаг 5 преконтроль) |
| Account rate limit | GET `/trade/account-rate-limit` | пробел | сторонний | диагностика лимитов; низкий приоритет |
| Easy convert / one-click repay | POST `/trade/easy-convert`, `/one-click-repay` | вне-периметра | сторонний | конвертация/репэй, не торговый цикл |

## Algo Trading (`/api/v5/trade/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Place algo order | POST `/trade/order-algo` | есть-док | офдок | `algo-order.md`; trigger/oco/conditional/move_order_stop |
| Cancel algo (ordinary) | POST `/trade/cancel-algos` | есть-док | офдок | `algo-order.md`; trigger/oco/conditional |
| **Cancel advance algo** | POST `/trade/cancel-advance-algos` | **обновлён** | офдок | **И-1**: trailing `move_order_stop` / iceberg / twap; добавлен в `algo-order.md` |
| Amend algo | POST `/trade/amend-algos` | есть-док | офдок | `algo-order.md` |
| Algo details | GET `/trade/order-algo` | есть-док | офдок | `algo-order.md` |
| Algo pending | GET `/trade/orders-algo-pending` | есть-док | офдок | `algo-order.md` (звено) |
| Algo history 3m | GET `/trade/orders-algo-history` | есть-док | офдок | `algo-order.md` |

## Account (`/api/v5/account/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Get balance | GET `/account/balance` | есть-док | офдок | `balance.md`, `OkxBalanceResponse` |
| Get positions | GET `/account/positions` | есть-док | офдок | `position.md`, `OkxPositionResponse` |
| Positions history | GET `/account/positions-history` | пробел | офдок | скан **В-3** (шаг 7 P&L); закрытые позиции |
| Account & position risk | GET `/account/account-position-risk` | пробел | сторонний | агрегат риска; низкий приоритет |
| Bills 7d | GET `/account/bills` | есть-док | офдок | `account-bills.md`, `OkxAccountBillResponse` |
| Bills archive 3m | GET `/account/bills-archive` | пробел | сторонний | архив биллов |
| Account config | GET `/account/config` | пробел | офдок | acctMode/posMode/autoBorrow/greeks (okx.com) |
| Set position mode | POST `/account/set-position-mode` | пробел | сторонний | net/long-short; конфиг счёта |
| Set leverage | POST `/account/set-leverage` | пробел | офдок | плечо |
| Leverage info | GET `/account/leverage-info` | пробел | офдок | |
| Max order size | GET `/account/max-size` | пробел | офдок | max contracts (okx.com) |
| Max avail size | GET `/account/max-avail-size` | пробел | сторонний | |
| Fee rates | GET `/account/trade-fee` | пробел | офдок | комиссии; точность P&L (шаг 7) |
| Position tiers | GET `/account/position-tiers` | пробел | сторонний | margin tiers; риск (шаг 5); путь к подтверждению |
| Interest / borrow-repay / VIP loan / spot-margin | various | вне-периметра | сторонний | margin/loan вне скоупа SWAP-бота фазы 1 |
| Set greeks / isolated-mode / quick-margin | various | вне-периметра | сторонний | опции / спот-маржа |

## Market Data (`/api/v5/market/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Tickers | GET `/market/tickers` | есть-док | офдок | `market-price-data.md` |
| Ticker | GET `/market/ticker` | есть-док | офдок | `market-price-data.md`, `OkxTickerResponse` |
| Candles | GET `/market/candles` | есть-док | офдок | `candle.md`, `CandleOkxResponse` |
| History candles | GET `/market/history-candles` | есть-док | офдок | покрыто `candle.md` (деталь — свериться) |
| Order book | GET `/market/books` | пробел | офдок | стакан; фазе 1 не нужен (стратегия на свечах) |
| Order book full | GET `/market/books-full` | пробел | сторонний | |
| Public trades | GET `/market/trades` | пробел | офдок | последние сделки рынка |
| Trades history | GET `/market/history-trades` | пробел | сторонний | |
| Index tickers | GET `/market/index-tickers` | пробел | офдок | |
| Index candles | GET `/market/index-candles` | пробел | сторонний | |
| Mark price candles | GET `/market/mark-price-candles` | пробел | сторонний | релевантно `tpTriggerPxType=mark` |
| Platform 24h volume | GET `/market/platform-24-volume` | вне-периметра | сторонний | |

## Public Data (`/api/v5/public/`)

| Операция | Метод · путь | Статус | Провенанс | Примечание |
|---|---|---|---|---|
| Instruments | GET `/public/instruments` | есть-док | офдок | `instrument.md`, `InstrumentOkxResponse` (okx.com пример подтверждён) |
| Mark price | GET `/public/mark-price` | пробел | офдок | |
| Price limit | GET `/public/price-limit` | пробел | офдок | риск/преконтроль (шаг 5) |
| Funding rate | GET `/public/funding-rate` | пробел | офдок | стоимость удержания SWAP |
| Funding rate history | GET `/public/funding-rate-history` | пробел | офдок | P&L (funding) шаг 7 |
| Open interest | GET `/public/open-interest` | пробел | офдок | |
| Server time | GET `/public/time` | пробел | сторонний | синхронизация времени/подписи |
| Insurance fund | GET `/public/insurance-fund` | пробел | сторонний | |
| Delivery/exercise, estimated-price, opt-summary, underlying, discount-rate | various | вне-периметра | сторонний | FUTURES delivery / OPTIONS — вне скоупа SWAP |

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
Лимиты WS частично задокументированы — `rules/ws-limits.md` (`есть-док`).

## Инфраструктурные доки (вне разбивки по эндпоинтам)

`contracts/service-urls.md` (базовые URL/окружения), правила
`rules/adapter-constants.md`, `rules/timeframe-constants.md`,
`rules/reduce-only-invariant.md`, `contracts/fills-archive.md` +
`OkxFillsArchiveResponse` (архив 3m+, `сознательно-вне` активного —
**OKX-Q2**, шаг 7) — `есть-док`.

## Связи

- Процесс — `.claude/processes/api-docs-completion.md`.
- Скилл OKX (входные точки, конвенции, ограничения) —
  `.claude/skills/integration-okx.md`.
- Отчёт прогона 2 (скан + эскалация источникового дефицита) —
  `.claude/work/progress/phase-1-step-4-integrator-run-2.md`.
- Контракты и нативные модели — `docs/integrations/okx/contracts/`,
  `docs/models/integrations/okx/`.
