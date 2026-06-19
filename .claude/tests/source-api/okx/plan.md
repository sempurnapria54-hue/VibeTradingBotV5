# Полный план тестов API OKX (сырьё)

## На какой вопрос отвечает этот файл

Как проверяем API OKX **на уровне сырья** (сырой клиент `OkxRestClient`
+ сырые DTO через A2-passthrough) — **по всей доступной поверхности**:
весь in-perimeter периметр манифеста ∩ сырой клиент, прямой кейс +
негатив на каждый запрос, каждый вариант типа-дискриминатора, цепочки
предусловий и teardown.

## Статус

**Полное покрытие манифест ∩ клиент** (2026-06-19). Замещает пилотный
субсет (обкатка формы) и регенерирован в новой структуре **метод →
кейсы → таблица** (класс на метод, тест на кейс,
`.claude/skills/test-design.md`). Цель — **сырьё**: проверки на сыром
`OkxApiResponse<T>` (`code`/`msg`/`data[].sCode`, типизированные
`*OkxResponse`/`*Ack`), не на снапшоте/`ExchangeAck`.

**На аппрув** (DESIGN-заход: план + коллекция до REVIEW+APPROVE;
код-тесты `test-code` + RUN — отдельным заходом после аппрува). Процесс
— `.claude/processes/source-api-testing.md`. Колонка «Факт +
наблюдения» пустая до RUN (этот же документ — бланк отчёта).

**Включает расширение A2-passthrough** (`OkxProxyController`): добавлены
7 read-эндпоинтов, ранее не выведенных на passthrough (candles ×2,
orders-pending/history, algo-pending/history, fills-history) — чтобы
коллекция была 1:1 со всем периметром клиента. Все 7 — read, write-риска
нет (см. §Нерешённое — на валидацию).

## Скоуп

**Тщательно, не субсет.** Цель — **in-perimeter манифест ∩ сырой
клиент**: каждый запрос периметра (`docs/integrations/okx/coverage-manifest.md`,
строки `есть-док`/`создан`/`обновлён`), реализованный методом
`OkxRestClient`. Триггер — разовый (полная регенерация). Покрыт 21 метод
клиента (см. §Инвариант полноты). Строки периметра **без** метода
клиента → client-coverage-gap (перечислены, запрос не выдумывается).

Объект — **сырьё OKX**: `OkxRestClient` через A2-passthrough
(`OkxProxyController`, `/api/proxy/okx/*`). Снапшоты/мапперы вне цели.

## Среда

- **demo/non-prod** (профиль `test`, demo-креды, `x-simulated-trading: 1`
  через `okx.simulated`) — все запросы, включая write (place/cancel/close
  ордеров и algo) и их подтверждающие чтения.
- **prod вне контура** — prod-кейсов нет (проверяет пользователь ад-хок
  вне контура).
- **изолированная конфигурация пустых кредов** (`OkxProperties` без
  `api-key`/`secret`/`passphrase`) — кейс I-cred; сети не достигает.

Подпись/креды — на стороне app (signing interceptor); в Postman/
passthrough кредов нет. Инструмент — `ETH-USDT-SWAP` (адаптер:
`tdMode=isolated`, `posSide=net` — хардкод,
`docs/integrations/okx/rules/adapter-constants.md`). Спека (сверка
2026-06-15): `minSz=0.01`, `lotSz=0.01`, `tickSz=0.01`, `ctVal=0.1` —
цена кратна `tickSz`.

## Сквозные проверки (красная нить)

Применяются в каждом релевантном кейсе, не отдельными кейсами:

- **ACK ≠ runtime truth.** `data[0].sCode=0` на place/cancel/close —
  приём запроса, не подтверждение факта; факт подтверждается отдельным
  чтением (`docs/rules/ack-not-runtime-truth.md`).
- **Per-element `sCode`.** Реджект приходит в `data[0].sCode`, не только
  в top-level `code`.
- **Сырьё, не снапшот.** Проверки на сыром DTO (`OkxApiResponse<T>`),
  используемые поля + коды ошибок.
- **Адаптер-инварианты — на request-стороне, не в ответе.**
  `tdMode=isolated`/`posSide=net` строятся мапперами в request-DTO
  (реальная сериализация прогоняется) и наблюдаемы в **write-логах**
  (`OkxWriteLoggingInterceptor`). В ответных `OrderOkxResponse`/
  `OkxAlgoOrderResponse` их **нет** — тонкий DTO их не биндит (decision 2,
  used-fields-only); это не surface-gap, а выбор худого DTO.
- **Незадокументированное не выдумывается.** Где спека не задаёт норму
  (точный код ошибки, поведение demo) — ожидание = «реджект (code≠0)» с
  логированием факта; точный код — наблюдение/находка интегратору, не
  выдумка.

## Граница: passthrough-слой vs OKX-слой негатива

Часть параметров passthrough **типизирована** (`conditionType` →
`enum valueOf`, `sz`/`px` → `BigDecimal`, обязательные `@RequestParam`):
битый вход перехватывается **до** OKX (`valueOf`/`BigDecimal` бросают →
HTTP 5xx; пропуск обязательного `@RequestParam` → HTTP 4xx). Такой
негатив — **passthrough-слой** (проверяет гард прокси, не OKX), помечен
в кейсе. Доменный негатив **OKX-слоя** (битый `instId`-строка, `sz=-1`,
неизвестный `instType`-строка) проходит насквозь и реджектится **OKX**
(`code≠0`). Где типизированный параметр не даёт послать на OKX
заведомо-битое доменное значение (напр. неизвестный `ordType`-строку для
algo — прокси принимает `conditionType`-enum, не сырой `ordType`) — это
помечается как **вариант-gap негатива** (через сырьё клиента не
достижимо), не выдумывается.

## Инвариант полноты (in-perimeter ∩ клиент)

21 метод `OkxRestClient` ↔ 21 строка периметра манифеста. Колонка
покрытия манифеста — 🟡 в плане по всем перечисленным.

| Метод клиента | Манифест-строка | Раздел плана | Варианты |
|---|---|---|---|
| `getInstruments` | Instruments (public) | M1 | instId есть/нет |
| `getTicker` | Ticker | M2 | — |
| `getLatestCandles` | Candles | M3 | — |
| `getHistoryCandles` | History candles | M4 | — |
| `getBalance` | Get balance | M5 | ccy есть/нет |
| `getAccountConfig` | Account config | M6 | — (диагностический сырой String) |
| `getOrder` | Order details | M7 | by `ordId` / by `clOrdId` |
| `getPendingOrders` | Pending orders | M8 | — |
| `getOrderHistory` | Order history 7d | M9 | — |
| `getFills` | Fills 3d | M10 | — |
| `getFillsHistory` | Fills 3m | M11 | — |
| `getPositions` | Get positions | M12 | — |
| `getAlgoOrder` | Algo details | M13 | by `algoId` / by `algoClOrdId` |
| `getPendingAlgoOrders` | Algo pending | M14 | `ordType` conditional/oco/move_order_stop |
| `getAlgoOrderHistory` | Algo history 3m | M15 | `ordType` conditional/oco/move_order_stop |
| `placeOrder` | Place order | M16 | `ordType` **limit / market** |
| `cancelOrder` | Cancel order | M17 | by `ordId` / by `clOrdId` |
| `closePosition` | Close position | M18 | — |
| `placeAlgoOrder` | Place algo order | M19 | conditional(SL/TP) / oco / move_order_stop |
| `cancelAlgos` | Cancel algo (ordinary) | M20 | семья **ordinary** |
| `cancelAdvanceAlgos` | Cancel advance algo | M21 | семья **advance** |

### client-coverage-gap'ы (в периметре, метода клиента нет — не покрываем)

Запрос **не выдумывается** (кандидаты на реализацию — отдельные доменные
решения):

- **Trade:** batch orders (place/cancel/amend), amend order, order
  history 3m (archive), cancel-all-after, order-precheck,
  account-rate-limit.
- **Algo:** amend-algos.
- **Account:** positions-history, account-position-risk, bills (7d/
  archive/deep), bill types, set-position-mode, set-leverage,
  leverage-info, max-size (×2), trade-fee.
- **Market:** tickers (плюрал), order-book (×2), public-trades (×2),
  index (tickers/candles/history), mark-price-candles (×2).
- **Public:** mark-price, price-limit, funding-rate (×2), open-interest,
  position-tiers, server-time, insurance-fund.

### вариант-gap'ы (клиент вариант собрать не может)

- **`placeAlgoOrder` trailing-value (`callbackSpread`)** — маппер мапит
  `callbackSpread` из `condition.trailing.trailingStepValue`, но
  passthrough-эндпоинт параметра под него не несёт (только
  `trailingPercents` → `callbackRatio`). Wire-вариант
  `move_order_stop` с абсолютным spread через контур **не достижим** —
  вариант-gap. `TRAILING_VALUE` через passthrough даёт тот же wire, что
  `TRAILING_PERCENTS` (callbackRatio), отличаясь лишь доменной меткой.
- **`placeAlgoOrder` PARTIAL_STOP_LOSS / PARTIAL_TAKE_PROFIT** —
  `resolveAlgoOrdType` мапит их в `conditional` (wire-идентично
  STOP_LOSS/TAKE_PROFIT); отдельного wire-варианта нет (покрыт
  conditional). Доменная reduce-only-доля — не сырьё интеграции.
- **`placeOrder` / `placeAlgoOrder` неизвестный сырой `ordType`-строка**
  — прокси принимает доменный `side`/`conditionType`, сырой `ordType`
  строится маппером; послать заведомо-битый `ordType` на OKX через
  типизированный прокси нельзя — негатив-вариант-gap (OKX-слой не
  достижим через сырьё клиента).

## Гейт достижимости

Полный план **снимает** пилотный отказ R5 (getFills): прямой богатый
кейс fills/позиций достижим **через исполнение** — минимальный реальный
**market**-ордер на demo (M16 market-цепочка) → fill → позиция →
зависимые прямые кейсы (`getFills`/`getFillsHistory`/`getPositions`/
`getOrderHistory` filled) → teardown (закрытие позиции). Ожидание:
market исполняется на demo (simulated book). **Если demo не наполняет
fill** (наблюдение прогона) — прямые кейсы fills отклоняются по факту,
ожидание не выдумывается (находка).

Остаётся недостижимым (отказ, где встречается): архивные окна без метода
клиента (3м-архивы — gap'ы), ~30д-закрытые позиции, история свежему
demo-аккаунту недоступная. Для методов клиента таких отказов в полном
плане нет (исполнение наполняет состояние).

## Порождённый негатив (методология `test-design`)

На каждый запрос (вкл. write) выведены применимые категории (битый
параметр / неизвестный ключ / значение вне домена / несущ. id / дубль /
отмена отменённого / фильтр вне окна / auth / состояние-конфликт),
скрещённые с кодами ошибок OKX. Где код документирован — указан; где нет
— ожидание «реджект (code≠0)», точный код = наблюдение.

---

# Читалки public / market (без состояния, без auth)

## M1. getInstruments — GET /public/instruments (Public Data)

- **Объект:** `OkxRestClient.getInstruments(instType, instId)`
  (passthrough `GET /instrument`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим. **Teardown:** не требуется (read).

### M1.1 прямой — getInstruments(SWAP, ETH-USDT-SWAP)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getInstruments(instType=SWAP, instId=ETH-USDT-SWAP)` | HTTP 200; `code="0"`; `data[0].instId=ETH-USDT-SWAP`; `minSz`/`tickSz`/`lotSz`/`ctVal` присутствуют | Сырой инструмент (min/tick/lot size, ctVal). Опора `minSz` demo-цепочек | _…_ |

### M1.2 вариант — getInstruments(SWAP) без instId (список)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getInstruments(instType=SWAP)` (instId опущен) | HTTP 200; `code="0"`; `data` — непустой массив; есть элемент с `instId=ETH-USDT-SWAP` | Список SWAP-инструментов (instId опционален) | _…_ |

### M1.3 негатив — instType вне домена (OKX-слой)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getInstruments(instType=BOGUS, instId=ETH-USDT-SWAP)` | HTTP 200; `code≠"0"` | Реджект OKX (некорректный `instType`); точный код — наблюдение | _…_ |

### M1.4 негатив — несущ. instId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getInstruments(instType=SWAP, instId=FOO-BAR)` | HTTP 200; `data` пустой | Пустой `data` (несущ. инструмент) — валидный исход | _…_ |

### M1.5 негатив — пропуск обязательного instType (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getInstruments` без `instType` | HTTP 4xx (Spring: отсутствует обязательный `@RequestParam instType`) | Гард прокси: 400 до OKX | _…_ |

## M2. getTicker — GET /market/ticker (Market Data)

- **Объект:** `getTicker(instId)` (passthrough `GET /ticker`).
  **Предусловие:** нет. **Среда:** demo. **Достижимость:** достижим.
  **Teardown:** не требуется. Источник live-цены для write-цепочек.

### M2.1 прямой — getTicker(ETH-USDT-SWAP)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getTicker(instId=ETH-USDT-SWAP)` | HTTP 200; `code="0"`; `data[0].last` присутствует и `>0`; `askPx`/`bidPx`/`ts` присутствуют | `OkxApiResponse<OkxTickerResponse>` с live last/ask/bid + ts | _…_ |

### M2.2 негатив — несущ. инструмент

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getTicker(instId=FOO-BAR)` | HTTP 200; `code≠"0"` **или** `data` пустой | Реджект/пустой ответ, не валидный тикер | _…_ |

### M2.3 негатив — пропуск обязательного instId (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getTicker` без `instId` | HTTP 4xx (отсутствует обязательный `@RequestParam`) | Гард прокси: 400 до OKX | _…_ |

## M3. getLatestCandles — GET /market/candles (Market Data)

- **Объект:** `getLatestCandles(instId, bar, limit)` (passthrough
  `GET /candles`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** достижим. **Teardown:** не требуется.
- **Форма ответа:** `OkxApiResponse<List<String>>` — `data` = массив
  массивов-строк свечи `[ts, o, h, l, c, vol, …]` (сырьё, не типизирован).

### M3.1 прямой — getLatestCandles(ETH-USDT-SWAP, 1m, limit=10)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getLatestCandles(instId=ETH-USDT-SWAP, bar=1m, limit=10)` | HTTP 200; `code="0"`; `data` — непустой массив; `data[0]` — массив длиной ≥ 6 (ts/o/h/l/c/vol); `data[0][0]` — числовая строка (ts) | Последние свечи 1m (докачка хвоста) | _…_ |

### M3.2 негатив — bar вне домена (OKX-слой)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getLatestCandles(instId=ETH-USDT-SWAP, bar=99z)` | HTTP 200; `code≠"0"` | Реджект OKX (некорректный `bar`); точный код — наблюдение | _…_ |

### M3.3 негатив — несущ. instId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getLatestCandles(instId=FOO-BAR, bar=1m)` | HTTP 200; `code≠"0"` **или** `data` пустой | Реджект/пустой — несущ. инструмент | _…_ |

### M3.4 негатив — пропуск обязательного bar (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getLatestCandles(instId=ETH-USDT-SWAP)` без `bar` | HTTP 4xx (отсутствует обязательный `@RequestParam bar`) | Гард прокси: 400 до OKX | _…_ |

## M4. getHistoryCandles — GET /market/history-candles (Market Data)

- **Объект:** `getHistoryCandles(instId, bar, after, limit)` (passthrough
  `GET /history-candles`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** достижим. **Teardown:** не требуется.

### M4.1 прямой — getHistoryCandles(ETH-USDT-SWAP, 1m, limit=10)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getHistoryCandles(instId=ETH-USDT-SWAP, bar=1m, limit=10)` (after опущен) | HTTP 200; `code="0"`; `data` — непустой массив; `data[0]` — массив (свеча); `data` упорядочен по ts убыв. | Исторические свечи 1m (пагинация назад) | _…_ |

### M4.2 вариант — пагинация назад по after

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getHistoryCandles(instId=ETH-USDT-SWAP, bar=1m, after=<ts из M4.1 data[last][0]>, limit=10)` | HTTP 200; `code="0"`; `data` — свечи строго старше `after` | Следующая страница назад (after — свечи старше ts) | _…_ |

### M4.3 негатив — фильтр из будущего (вне окна)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getHistoryCandles(instId=ETH-USDT-SWAP, bar=1m, after=99999999999999)` | HTTP 200; `data` пустой **или** наблюдение | Якорь из будущего — пустой/поведение фиксируем. Note: `after` в passthrough — `Long`; нечисловой `after` отсекается биндингом @RequestParam (4xx), потому домен-негатив здесь — «вне окна», не «нечисло» | _…_ |

### M4.4 негатив — пропуск обязательного bar (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getHistoryCandles(instId=ETH-USDT-SWAP)` без `bar` | HTTP 4xx | Гард прокси: 400 до OKX | _…_ |

---

# Читалки private (auth, без состояния)

## M5. getBalance — GET /account/balance (Account)

- **Объект:** `getBalance(ccy)` (passthrough `GET /balance`).
  **Предусловие:** нет. **Среда:** demo. **Достижимость:** достижим.
  **Teardown:** не требуется.

### M5.1 прямой — getBalance(USDT)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getBalance(ccy=USDT)` | HTTP 200; `code="0"`; `data[0].totalEq` присутствует; `data[0].details` — массив, несёт USDT | Account-level баланс, details по USDT | _…_ |

### M5.2 вариант-gap — getBalance без ccy (все валюты)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getBalance` без `ccy` | HTTP 4xx — **passthrough-слой:** `ccy` в прокси `@RequestParam` обязателен (без `required=false`) → 400 | Вариант «все валюты» через сырой клиент достижим (`ccy` опционален в клиенте), но **не на passthrough** — вариант-gap прокси, фиксируем (не выдумываем happy-путь) | _…_ |

### M5.3 негатив — несущ. валюта (наблюдение)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getBalance(ccy=ZZZ)` | HTTP 200; `code="0"` с пустым details **или** `code≠"0"` (наблюдение) | Поведение OKX на несущ. ccy — фиксируем фактом, не выдумываем | _…_ |

## M6. getAccountConfig — GET /account/config (Account, диагностический)

- **Объект:** `getAccountConfig()` (passthrough `GET /account-config`).
  Возвращает **сырой String** (не `OkxApiResponse<T>`) — диагностическое
  чтение acctLv/posMode. **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** достижим. **Teardown:** не требуется. Без параметров
  — негативы только auth (см. I-cred).

### M6.1 прямой — getAccountConfig()

| Запрос | Проверки (сырое тело) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getAccountConfig()` | HTTP 200; тело — JSON-строка; содержит `"code":"0"`, `acctLv`, `posMode` | Сырой account config (acctLv/posMode). Проверка на текстовом теле (не типизирован) | _…_ |

---

# Читалки private с состоянием (no-state негатив здесь; прямой — через цепочки)

> Прямое (богатое) покрытие методов ниже даёт состояние, создаваемое
> write-цепочками M16/M19; здесь — no-state негатив + ссылка на цепочку.

## M7. getOrder — GET /trade/order (Order details)

- **Объект:** `getOrder(instId, ordId, clOrdId)` (passthrough
  `GET /order`). **Предусловие:** прямой — живой/исполненный ордер
  (цепочки Climit/Cmarket в M16). **Среда:** demo. **Teardown:** в
  цепочке.

### M7.1 негатив (no-state) — фейковый ordId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getOrder(instId=ETH-USDT-SWAP, ordId=9999999999999999)` | HTTP 200; `data` пустой **или** `code` несёт 51603 (наблюдение) | Резолюция «не найден». Под сырьё — прямой `trade-order`, не evidence-cycle (тот в refresh-executor, вне клиента). Связь: 51603-on-not-found — предпосылка D-B3 (recovery-by-clientId) | _…_ |

### M7.2 негатив (no-state) — пропуск обязательного instId (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getOrder(ordId=…)` без `instId` | HTTP 4xx | Гард прокси: 400 до OKX | _…_ |

### M7.3 прямой по ordId / M7.4 прямой по clOrdId

Покрыты цепочкой **Climit** (M16): Climit.get `getOrder(ordId)` live,
Climit.canceled `getOrder(ordId)` canceled, Climit.getByClId
`getOrder(clOrdId)` (вариант резолва по clOrdId). См. M16 §Climit.

## M8. getPendingOrders — GET /trade/orders-pending (Pending orders)

- **Объект:** `getPendingOrders(instId)` (passthrough `GET /orders-pending`).
  **Предусловие:** прямой богатый — живой ордер (Climit). **Среда:** demo.
  **Teardown:** в цепочке.

### M8.1 прямой (no-state) — пустой/валидный

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPendingOrders(instId=ETH-USDT-SWAP)` (без живых ордеров) | HTTP 200; `code="0"`; `data` — массив (пустой валиден) | Список pending (пуст, если ничего не висит) — валидный исход | _…_ |

### M8.2 негатив — несущ. instId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPendingOrders(instId=FOO-BAR)` | HTTP 200; `data` пустой **или** `code≠"0"` (наблюдение) | Пустой/реджект на несущ. инструмент | _…_ |

### M8.3 прямой богатый

Покрыт **Climit** (M16): шаг Climit.pending между place и cancel —
`data` содержит живой ордер с `cl_ordId`.

## M9. getOrderHistory — GET /trade/orders-history (Order history 7d)

- **Объект:** `getOrderHistory(instId)` (passthrough `GET /orders-history`).
  **Предусловие:** прямой богатый — ордер в истории 7д (отменённый
  хранится ~2ч; исполненный — 7д). **Среда:** demo. **Teardown:** в
  цепочке.

### M9.1 прямой (no-state) — пустой/валидный

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getOrderHistory(instId=ETH-USDT-SWAP)` | HTTP 200; `code="0"`; `data` — массив (пустой валиден на свежем demo) | История 7д (пуста/наполнена) — валидный исход | _…_ |

### M9.2 негатив — несущ. instId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getOrderHistory(instId=FOO-BAR)` | HTTP 200; `data` пустой **или** `code≠"0"` (наблюдение) | Пустой/реджект на несущ. инструмент | _…_ |

### M9.3 прямой богатый

Покрыт **Cmarket** (M16, filled-ордер в истории) и **Climit** (M16,
canceled-ордер в истории ~2ч). Шаги Cmarket.history / Climit.history.

## M10. getFills — GET /trade/fills (Fills 3d)

- **Объект:** `getFills(instId, after, limit)` (passthrough `GET /fills`).
  **Предусловие:** прямой богатый — недавний fill (Cmarket).
  **Среда:** demo. **Достижимость:** **через исполнение** (Cmarket).
  **Teardown:** в цепочке.

### M10.1 прямой (no-state) — пустой/валидный до исполнения

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getFills(instId=ETH-USDT-SWAP)` (до Cmarket) | HTTP 200; `code="0"`; `data` — массив (пустой валиден) | Пустой/наполненный список исполнений | _…_ |

### M10.2 негатив — фильтр из будущего / вне окна

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getFills(instId=ETH-USDT-SWAP, after=99999999999999)` | HTTP 200; `data` пустой | Пустой ответ на якорь из будущего | _…_ |

### M10.3 негатив — пропуск обязательного instId (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getFills` без `instId` | HTTP 4xx | Гард прокси: 400 до OKX | _…_ |

### M10.4 прямой богатый (через исполнение)

Покрыт **Cmarket** (M16): шаг Cmarket.fills после исполнения market —
`data` содержит fill (`fillPx`/`fillSz`/`ordId`). Если demo не наполнил
fill — отказ по факту (находка), ожидание не выдумывается.

## M11. getFillsHistory — GET /trade/fills-history (Fills 3m)

- **Объект:** `getFillsHistory(instId, after, limit)` (passthrough
  `GET /fills-history`). **Предусловие:** прямой богатый — fill в окне 3м
  (Cmarket). **Среда:** demo. **Teardown:** в цепочке.

### M11.1 прямой (no-state) — пустой/валидный

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getFillsHistory(instId=ETH-USDT-SWAP)` | HTTP 200; `code="0"`; `data` — массив (пустой валиден) | Исполнения за 3м (пусто/наполнено) | _…_ |

### M11.2 негатив — фильтр из будущего

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getFillsHistory(instId=ETH-USDT-SWAP, after=99999999999999)` | HTTP 200; `data` пустой | Пустой ответ на якорь из будущего | _…_ |

### M11.3 негатив — несущ. instId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getFillsHistory(instId=FOO-BAR)` | HTTP 200; `data` пустой **или** `code≠"0"` | Пустой/реджект | _…_ |

### M11.4 прямой богатый

Покрыт **Cmarket** (M16): fill виден и в `fills-history` (окно 3м
включает свежие). Шаг Cmarket.fillsHistory.

## M12. getPositions — GET /account/positions (Get positions)

- **Объект:** `getPositions(instId)` (passthrough `GET /position`).
  **Предусловие:** прямой богатый — открытая позиция (Cmarket).
  **Среда:** demo. **Достижимость:** **через исполнение** (Cmarket).
  **Teardown:** в цепочке.

### M12.1 прямой (no-state) — пустой/валидный (нет позиции)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPositions(instId=ETH-USDT-SWAP)` (нет позиции) | HTTP 200; `code="0"`; `data` пустой **или** `data[0].posId` присутствует | Пустая/нет позиции — валидный исход | _…_ |

### M12.2 негатив — несущ. instId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPositions(instId=FOO-BAR)` | HTTP 200; `data` пустой **или** `code≠"0"` | Пустой/реджект | _…_ |

### M12.3 прямой богатый (через исполнение)

Покрыт **Cmarket** (M16): шаги Cmarket.position (позиция открыта,
`data[0].posId`/`pos`/`avgPx`) и Cmarket.positionFlat (после close —
`data` пуст / `pos=0`).

---

# Читалки algo (no-state негатив; прямой — через цепочки M19)

## M13. getAlgoOrder — GET /trade/order-algo (Algo details)

- **Объект:** `getAlgoOrder(instId, algoId, algoClOrdId)` (passthrough
  `GET /algo-order`). **Предусловие:** прямой богатый — живой/отменённый
  algo (цепочки M19). **Среда:** demo. **Teardown:** в цепочке.

### M13.1 негатив (no-state) — фейковый algoId

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getAlgoOrder(instId=ETH-USDT-SWAP, algoId=9999999999999999)` | HTTP 200; `data` пустой **или** `code≠"0"` (наблюдение) | Резолюция «не найден» (ожидается 0 элементов) | _…_ |

### M13.2 негатив (no-state) — пропуск обязательного instId (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getAlgoOrder(algoId=…)` без `instId` | HTTP 4xx | Гард прокси: 400 до OKX | _…_ |

### M13.3 прямой по algoId / M13.4 по algoClOrdId

Покрыты цепочками M19: getAlgo(algoId) live + canceled; вариант резолва
по `algoClOrdId` — шаг M19cond.getByClId.

## M14. getPendingAlgoOrders — GET /trade/orders-algo-pending (Algo pending)

- **Объект:** `getPendingAlgoOrders(instId, ordType)` (passthrough
  `GET /orders-algo-pending`). **Предусловие:** прямой богатый — живой
  algo (M19). **Среда:** demo. **Teardown:** в цепочке. **Вариант —
  `ordType`** (conditional/oco/move_order_stop).

### M14.1 прямой (no-state) — ordType=conditional, пустой/валидный

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPendingAlgoOrders(instId=ETH-USDT-SWAP, ordType=conditional)` | HTTP 200; `code="0"`; `data` — массив (пустой валиден) | Pending algo семьи conditional | _…_ |

### M14.2 вариант — ordType=oco

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPendingAlgoOrders(instId=ETH-USDT-SWAP, ordType=oco)` | HTTP 200; `code="0"`; `data` — массив | Pending algo семьи oco | _…_ |

### M14.3 вариант — ordType=move_order_stop (advance)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPendingAlgoOrders(instId=ETH-USDT-SWAP, ordType=move_order_stop)` | HTTP 200; `code="0"`; `data` — массив | Pending algo семьи advance (trailing) видна в query | _…_ |

### M14.4 негатив — ordType вне домена (OKX-слой)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPendingAlgoOrders(instId=ETH-USDT-SWAP, ordType=BOGUS)` | HTTP 200; `code≠"0"` | Реджект OKX (некорректный `ordType`); точный код — наблюдение | _…_ |

### M14.5 негатив — пропуск обязательного ordType (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPendingAlgoOrders(instId=ETH-USDT-SWAP)` без `ordType` | HTTP 4xx | Гард прокси: 400 до OKX | _…_ |

### M14.6 прямой богатый

Покрыт M19: шаг M19cond.pending (`ordType=conditional`) и M19tr.pending
(`ordType=move_order_stop`) между place и cancel — `data` содержит живой
algo.

## M15. getAlgoOrderHistory — GET /trade/orders-algo-history (Algo history 3m)

- **Объект:** `getAlgoOrderHistory(instId, ordType)` (passthrough
  `GET /orders-algo-history`). **Предусловие:** прямой богатый —
  отменённый/сработавший algo в окне 3м (M19). **Среда:** demo.
  **Teardown:** в цепочке. **Вариант — `ordType`** (обязателен в OKX
  history).

### M15.1 прямой (no-state) — ordType=conditional, пустой/валидный

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getAlgoOrderHistory(instId=ETH-USDT-SWAP, ordType=conditional)` | HTTP 200; `code="0"`; `data` — массив; элементы (если есть) несут `state` ∈ effective/canceled/order_failed | История algo семьи conditional за 3м | _…_ |

### M15.2 вариант — ordType=oco / M15.3 вариант — ordType=move_order_stop

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getAlgoOrderHistory(instId=ETH-USDT-SWAP, ordType=oco)` | HTTP 200; `code="0"`; `data` массив | История семьи oco | _…_ |
| `getAlgoOrderHistory(instId=ETH-USDT-SWAP, ordType=move_order_stop)` | HTTP 200; `code="0"`; `data` массив | История семьи advance (trailing) | _…_ |

### M15.4 негатив — ordType вне домена / M15.5 негатив — пропуск ordType

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getAlgoOrderHistory(instId=ETH-USDT-SWAP, ordType=BOGUS)` | HTTP 200; `code≠"0"` | Реджект OKX | _…_ |
| `getAlgoOrderHistory(instId=ETH-USDT-SWAP)` без `ordType` | HTTP 4xx (passthrough-слой) | Гард прокси: 400 | _…_ |

### M15.6 прямой богатый

Покрыт M19trailing/M19cond: после cancel algo попадает в history со
`state=canceled` (`ordType=move_order_stop`/`conditional`). Шаг
M19*.history.

---

# Негативный слой без состояния (demo, гоняется первым)

Дешёвые no-state негативы выше распределены по методам (M1.3–M1.5,
M2.2–M2.3, M3.2–M3.4, M4.3–M4.4, M5.3, M7.1–M7.2, M8.2, M9.2, M10.2–M10.3,
M11.2–M11.3, M12.2, M13.1–M13.2, M14.4–M14.5, M15.4–M15.5, M17.1–M17.2,
M18.1–M18.2, M20.1, M21.1). При прогоне (`test-run`) они идут **первыми**
(до write-цепочек): не требуют состояния, дают быстрый сигнал.

---

# Trade writes + цепочки (demo, WRITE — реальные ордера/позиции)

## M16. placeOrder — POST /trade/order (Place order)

- **Объект:** `placeOrder(request)` (passthrough `POST /order`).
  **Варианты `ordType`:** `limit` (px задан → цепочка **Climit**,
  неисполнимый) и `market` (px опущен → цепочка **Cmarket**, исполняемый
  → fill → позиция). **Среда:** demo. **WRITE.** **Teardown:** в каждой
  цепочке.

### M16.limit — вариант limit: цепочка жизненного цикла (Climit)

Граф: getTicker → place(limit) → getOrder(live) → getOrder(clOrdId) →
getPendingOrders → cancel → getOrder(canceled) → getOrderHistory. Цена
неисполнима (≈ −50% от last, кратно tickSz). **Достижимость:** достижим.
**Teardown:** cancel-страховка.

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **Climit.price.** `getTicker(instId)` | HTTP 200; `data[0].last>0` | live `last` → `cl_px=floor(last·0.5)` (неисполнимая limit buy). Цена с биржи, не константа | _…_ |
| **Climit.place.** `placeOrder(side=buy, sz=min, px=cl_px, clOrdId=cl_clOrdId, reduceOnly=false)` | HTTP 200; `code="0"`; `data[0].sCode="0"`; `data[0].ordId` непустой | `ordId` → `cl_ordId`. `sCode=0 ≠ live` (см. .get). Маппер строит limit (px задан) + tdMode=isolated/posSide=net (write-лог) | _…_ |
| **Climit.get.** `getOrder(instId, ordId=cl_ordId)` | HTTP 200; `data[0].ordId=cl_ordId`; `data[0].state="live"`; `side`/`sz`/`px` присутствуют | ACK стал live-ордером — ACK ≠ runtime truth (покрывает M7.3) | _…_ |
| **Climit.getByClId.** `getOrder(instId, clOrdId=cl_clOrdId)` | HTTP 200; `data[0].ordId=cl_ordId` | Резолв по `clOrdId` (вариант M7.4): тот же ордер | _…_ |
| **Climit.pending.** `getPendingOrders(instId)` | HTTP 200; `code="0"`; `data` содержит элемент с `ordId=cl_ordId`, `state=live` | Живой ордер в pending (покрывает M8.3) | _…_ |
| **Climit.cancel.** `cancelOrder(instId, ordId=cl_ordId)` | HTTP 200; `code="0"`; `data[0].sCode="0"` | ACK отмены, не финал — подтверждается .canceled (покрывает M17 прямой) | _…_ |
| **Climit.canceled.** `getOrder(instId, ordId=cl_ordId)` | HTTP 200; `data[0].state="canceled"` | Финал cancel (ордер ещё доступен по ordId) | _…_ |
| **Climit.history.** `getOrderHistory(instId)` | HTTP 200; `code="0"`; `data` содержит `ordId=cl_ordId` (state canceled, окно ~2ч) **или** наблюдение (если ещё не индексирован) | Отменённый в истории 7д ~2ч (покрывает M9.3); незадокументированную задержку индексации фиксируем фактом | _…_ |
| **Teardown Climit.** `cancelOrder(instId, ordId=cl_ordId)` (идемпотентная страховка) | HTTP 200; (sCode 0 или already-canceled/not-exist) | После цепочки биржа чистая (ни одного живого ордера). Цена далеко от рынка → исполнение исключено | _…_ |

### M16.market — вариант market: цепочка исполнения (Cmarket)

Граф: getTicker → place(market) → getOrder(filled) → getPositions(есть) →
getFills → getFillsHistory → getOrderHistory(filled) → closePosition →
getPositions(flat). **Исполняемый тип** — наполняет fill/позицию
(гейт достижимости). **Достижимость:** достижим (исполнением).
**Teardown:** closePosition-страховка.

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **Cmarket.price.** `getTicker(instId)` | HTTP 200; `data[0].last>0` | live last (диагностика; market цены не требует) | _…_ |
| **Cmarket.place.** `placeOrder(side=buy, sz=min, clOrdId=mk_clOrdId, reduceOnly=false)` (px опущен → market) | HTTP 200; `code="0"`; `data[0].sCode="0"`; `data[0].ordId` непустой | `ordId` → `mk_ordId`. Маппер строит **market** (px=null → `resolveOrdType` market). **Минимальный риск:** min sz | _…_ |
| **Cmarket.get.** `getOrder(instId, ordId=mk_ordId)` | HTTP 200; `data[0].ordId=mk_ordId`; `data[0].state` ∈ filled/partially_filled; `accFillSz`/`avgPx` присутствуют | Market исполнен (filled) — ACK стал фактом исполнения | _…_ |
| **Cmarket.position.** `getPositions(instId)` | HTTP 200; `code="0"`; `data[0].posId` присутствует; `data[0].pos` ≠ 0; `avgPx` присутствует | Открытая позиция (покрывает M12.3). Если demo не открыл позицию — наблюдение/находка | _…_ |
| **Cmarket.fills.** `getFills(instId)` | HTTP 200; `code="0"`; `data` содержит элемент с `ordId=mk_ordId`; `fillPx`/`fillSz` присутствуют | Fill виден (покрывает M10.4). Если пусто — отказ по факту, ожидание не выдумывается | _…_ |
| **Cmarket.fillsHistory.** `getFillsHistory(instId)` | HTTP 200; `code="0"`; `data` содержит `ordId=mk_ordId` (окно 3м) **или** наблюдение | Fill в окне 3м (покрывает M11.4) | _…_ |
| **Cmarket.history.** `getOrderHistory(instId)` | HTTP 200; `code="0"`; `data` содержит `ordId=mk_ordId` (state filled) **или** наблюдение | Filled-ордер в истории 7д (покрывает M9.3 богатый) | _…_ |
| **Cmarket.close.** `closePosition(instId, ccy=USDT)` | HTTP 200; `code="0"`; `data[0].sCode="0"` | ACK закрытия (market, autoCxl). Покрывает M18 прямой | _…_ |
| **Cmarket.positionFlat.** `getPositions(instId)` | HTTP 200; `code="0"`; `data` пуст **или** `data[0].pos=0` | Позиция закрыта (покрывает M12.3 flat) | _…_ |
| **Teardown Cmarket.** `closePosition(instId, ccy=USDT)` (идемпотентная страховка) | HTTP 200; (sCode 0 или «нет позиции») | После цепочки ни позиции, ни висящих ордеров (autoCxl снял) | _…_ |

### M16.neg — негатив placeOrder

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M16.neg.size.** `placeOrder(side=buy, sz=-1, px=cl_px, clOrdId=…)` (значение вне домена, OKX-слой) | HTTP 200; `code≠"0"` **или** `data[0].sCode≠"0"` | Реджект отрицательного размера; точный код — наблюдение | _…_ |
| **M16.neg.side.** `placeOrder(side=BOGUS, sz=min, px=cl_px, clOrdId=…)` (значение вне домена) | HTTP 200; `data[0].sCode≠"0"` **или** `code≠"0"` | Реджект некорректного `side` (passthrough кладёт строку в request) | _…_ |
| **M16.neg.dupClId.** дважды `placeOrder(... clOrdId=<тот же>)` (дубль id) | HTTP 200; второй — `data[0].sCode≠"0"` (дубль clOrdId) | Реджект дубля clientId; teardown: отменить оба, если прошли | _…_ |
| **M16.neg.reqParam.** `placeOrder` без `sz` (passthrough-слой) | HTTP 4xx | Гард прокси: обязательный `@RequestParam sz` | _…_ |

> **Вариант-gap негатива:** заведомо-битый сырой `ordType`-строка на OKX
> через прокси не послать (прокси не принимает `ordType`, маппер строит
> limit/market из наличия `px`). Зафиксировано в §Вариант-gap.

## M17. cancelOrder — POST /trade/cancel-order (Cancel order)

- **Объект:** `cancelOrder(request)` (passthrough `DELETE /order`).
  **Среда:** demo. **WRITE.** Прямой — цепочка Climit (Climit.cancel).
  **Вариант — by `ordId` / by `clOrdId`.** **Teardown:** в цепочке.

### M17.1 негатив (no-state) — cancel несуществующего

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `cancelOrder(instId=ETH-USDT-SWAP, ordId=9999999999999999)` | HTTP 200; `data[0].sCode="51603"`; `data[0]` несёт `sCode`+`sMsg` | Реджект в `data[0].sCode=51603` (order does not exist), HTTP 200. **Находка по дизайну:** HTTP 500 (top-level throw) → рассогласование реджект-кодов (`backlog`: 500 вместо 422/409) → проверка фейлит и показывает | _…_ |

### M17.2 негатив — пропуск обязательного instId (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `cancelOrder(ordId=…)` без `instId` | HTTP 4xx | Гард прокси: 400 до OKX | _…_ |

### M17.3 негатив — отмена отменённого (состояние-конфликт)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| Повтор `cancelOrder(instId, ordId=cl_ordId)` после Climit.cancel | HTTP 200; `data[0].sCode≠"0"` (already canceled / not exist) | Реджект повторной отмены; код — наблюдение (51603/иной) | _…_ |

### M17.4 прямой + вариант clOrdId

Покрыт Climit (Climit.cancel by ordId). Вариант by `clOrdId` — отдельный
кейс коллекции (тот же граф, отмена `cancelOrder(instId, clOrdId=cl_clOrdId)`).

## M18. closePosition — POST /trade/close-position (Close position)

- **Объект:** `closePosition(request)` (passthrough `POST /position/close`).
  **Среда:** demo. **WRITE.** Прямой — цепочка Cmarket (Cmarket.close).
  **Teardown:** в цепочке.

### M18.1 негатив (no-state) — close без позиции (состояние-конфликт)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `closePosition(instId=ETH-USDT-SWAP, ccy=USDT)` без открытой позиции | HTTP 200; `data[0].sCode≠"0"` **или** `code≠"0"` (наблюдение) | Реджект close несущ. позиции; точный код — наблюдение | _…_ |

### M18.2 негатив — пропуск обязательного instId (passthrough-слой)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `closePosition(ccy=USDT)` без `instId` | HTTP 4xx | Гард прокси: 400 до OKX | _…_ |

### M18.3 прямой

Покрыт Cmarket (Cmarket.close): закрытие реальной позиции, `sCode=0`,
подтверждение Cmarket.positionFlat.

---

# Algo writes + цепочки (demo, WRITE — реальные algo)

## M19. placeAlgoOrder — POST /trade/order-algo (Place algo order)

- **Объект:** `placeAlgoOrder(request)` (passthrough `POST /algo-order`).
  **Варианты `ordType` (строит клиент):** `conditional` (через
  STOP_LOSS / TAKE_PROFIT), `oco` (OCO_FULL), `move_order_stop` (через
  TRAILING_PERCENTS). **Среда:** demo. **WRITE.** **Teardown:** в каждой
  цепочке.
- **Предусловие reduce-only:** protective algo (reduceOnly=true) может
  требовать позиции. Если demo реджектит «нет позиции» — открыть min
  market-позицию (A0, как Cmarket.place), повторить, закрыть в teardown.
  Помечается в каждом варианте.

### M19.cond-sl — вариант conditional (STOP_LOSS)

Граф: place(conditional, SL) → getAlgo(live) → getAlgo(algoClOrdId) →
getPendingAlgo(conditional) → cancelAlgo(ordinary семья) →
getAlgo(canceled) → getAlgoHistory.

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19cond.price.** `getTicker(instId)` | HTTP 200; `data[0].last>0` | live last → `sl_px=floor(last·0.5)` (далёкий SL-триггер) | _…_ |
| **M19cond.place.** `placeAlgoOrder(direction=SELL, conditionType=STOP_LOSS, sz=min, reduceOnly=true, slTriggerPx=sl_px, slTriggerPxType=MARK)` | HTTP 200; `code="0"`; `data[0].sCode="0"`; `data[0].algoId` непустой | Маппер: `ordType=conditional`, `slOrdPx=-1` (market после trigger). `algoId` → `cond_algoId`. При реджекте «нет позиции» — A0 + повтор | _…_ |
| **M19cond.get.** `getAlgoOrder(instId, algoId=cond_algoId)` | HTTP 200; `data[0].algoId=cond_algoId`; `data[0].state≠"canceled"` | Algo live/effective (покрывает M13.3) | _…_ |
| **M19cond.getByClId.** `getAlgoOrder(instId, algoClOrdId=cond_clId)` | HTTP 200; `data[0].algoId=cond_algoId` | Резолв по `algoClOrdId` (вариант M13.4) | _…_ |
| **M19cond.pending.** `getPendingAlgoOrders(instId, ordType=conditional)` | HTTP 200; `data` содержит `algoId=cond_algoId` | Живой conditional в pending (покрывает M14.6 conditional) | _…_ |
| **M19cond.cancel.** `cancelAlgoOrder(instId, conditionType=STOP_LOSS, algoId=cond_algoId)` → **ordinary** `cancel-algos` | HTTP 200; `code="0"`; `data[0].sCode="0"` | Ветвь ordinary `cancelAlgos` (покрывает M20 прямой). ACK отмены | _…_ |
| **M19cond.canceled.** `getAlgoOrder(instId, algoId=cond_algoId)` | HTTP 200; `data[0].state="canceled"` **или** `data` пуст (наблюдение) | Финал cancel | _…_ |
| **M19cond.history.** `getAlgoOrderHistory(instId, ordType=conditional)` | HTTP 200; `data` содержит `algoId=cond_algoId` (state canceled) **или** наблюдение | Отменённый в history 3м (покрывает M15.6) | _…_ |
| **Teardown M19cond.** `cancelAlgoOrder(... STOP_LOSS, algoId=cond_algoId)` + (если A0) `closePosition` | HTTP 200 | Algo снят, позиция (если открывалась) закрыта — биржа чистая | _…_ |

### M19.cond-tp — вариант conditional (TAKE_PROFIT)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19tp.place.** `placeAlgoOrder(direction=SELL, conditionType=TAKE_PROFIT, sz=min, reduceOnly=true, tpTriggerPx=<floor(last·2)>, tpTriggerPxType=MARK)` | HTTP 200; `code="0"`; `data[0].sCode="0"`; `data[0].algoId` непустой | Маппер: `ordType=conditional`, `tpOrdPx=-1`. `algoId` → `tp_algoId` | _…_ |
| **M19tp.get.** `getAlgoOrder(instId, algoId=tp_algoId)` | HTTP 200; `data[0].state≠"canceled"` | Live | _…_ |
| **M19tp.cancel.** `cancelAlgoOrder(instId, conditionType=TAKE_PROFIT, algoId=tp_algoId)` → ordinary | HTTP 200; `data[0].sCode="0"` | Cancel ordinary | _…_ |
| **Teardown M19tp.** `cancelAlgoOrder(... TAKE_PROFIT, algoId=tp_algoId)` (+ A0 close) | HTTP 200 | Чисто | _…_ |

### M19.oco — вариант oco (OCO_FULL)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19oco.place.** `placeAlgoOrder(direction=SELL, conditionType=OCO_FULL, sz=min, reduceOnly=true, slTriggerPx=floor(last·0.5), slTriggerPxType=MARK, tpTriggerPx=floor(last·2), tpTriggerPxType=MARK)` | HTTP 200; `code="0"`; `data[0].sCode="0"`; `data[0].algoId` непустой | Маппер: `ordType=oco`, обе ноги (slOrdPx/tpOrdPx=-1). `algoId` → `oco_algoId` | _…_ |
| **M19oco.get.** `getAlgoOrder(instId, algoId=oco_algoId)` | HTTP 200; `data[0].state≠"canceled"` | Live oco | _…_ |
| **M19oco.cancel.** `cancelAlgoOrder(instId, conditionType=OCO_FULL, algoId=oco_algoId)` → ordinary | HTTP 200; `data[0].sCode="0"` | Cancel ordinary | _…_ |
| **Teardown M19oco.** `cancelAlgoOrder(... OCO_FULL, algoId=oco_algoId)` (+ A0 close) | HTTP 200 | Чисто | _…_ |

### M19.trailing — вариант move_order_stop (TRAILING_PERCENTS, ядро И-2)

Граф: place(trailing) → getAlgo(live) → getPendingAlgo(move_order_stop) →
cancelAlgo(**advance** семья) → getAlgo(canceled) → getAlgoHistory.
Снятие конфликта офдока рантаймом: `cancel-advance-algos` выведен из
офдока (changelog 2025-04-24), но `OkxRestClient.cancelAdvanceAlgos`
существует, ветвь cancel идёт по семье advance (И-1(а),
`algo-order.md`). **Вердикт cancel частично неизвестен** (рантайм-снятие).

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19tr.place.** `placeAlgoOrder(direction=SELL, conditionType=TRAILING_PERCENTS, sz=min, reduceOnly=true, trailingPercents=0.05)` | HTTP 200; `code="0"`; `data[0].sCode="0"`; `data[0].algoId` непустой | Маппер: `ordType=move_order_stop`, `callbackRatio=0.05`. `algoId` → `tr_algoId`. При реджекте «нет позиции» — A0 + повтор | _…_ |
| **M19tr.get.** `getAlgoOrder(instId, algoId=tr_algoId)` | HTTP 200; `data[0].algoId=tr_algoId`; `data[0].state≠"canceled"` | Trailing live/effective | _…_ |
| **M19tr.pending.** `getPendingAlgoOrders(instId, ordType=move_order_stop)` | HTTP 200; `data` содержит `algoId=tr_algoId` | Advance виден в pending (покрывает M14.6 advance) | _…_ |
| **M19tr.cancel.** `cancelAlgoOrder(instId, conditionType=TRAILING_PERCENTS, algoId=tr_algoId)` → **advance** `cancel-advance-algos` | HTTP 200; `data[0].sCode="0"` (**гипотеза** — endpoint жив на demo) | **Ядро И-2 (покрывает M21 прямой).** Гипотеза: жив, `sCode=0`. Если demo вернёт «endpoint не существует»/иную ошибку → делистинг подтверждён = **находка интегратору** (C3: правка `algo-order.md`, провенанс `рантайм`), не заранее известный pass/fail. `code`/`msg` логируются | _…_ |
| **M19tr.canceled.** `getAlgoOrder(instId, algoId=tr_algoId)` | HTTP 200; `data[0].state="canceled"` **или** `data` пуст (наблюдение) | Финал cancel trailing | _…_ |
| **M19tr.history.** `getAlgoOrderHistory(instId, ordType=move_order_stop)` | HTTP 200; `data` содержит `algoId=tr_algoId` **или** наблюдение | Trailing в history 3м (покрывает M15.6 advance) | _…_ |
| **Teardown M19tr.** `cancelAlgoOrder(... TRAILING_PERCENTS, algoId=tr_algoId)` (advance-ветвь) + (если A0) `closePosition` | HTTP 200 | Trailing снят, позиция (если открывалась) закрыта — биржа чистая | _…_ |

### M19.neg — негатив placeAlgoOrder

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19.neg.condType.** `placeAlgoOrder(conditionType=BOGUS, …)` (passthrough-слой) | HTTP 5xx (прокси `ConditionType.valueOf(BOGUS)` → IllegalArgumentException) | Гард прокси: 500 до OKX. **Вариант-gap:** битый сырой `ordType` на OKX через прокси не послать | _…_ |
| **M19.neg.size.** `placeAlgoOrder(conditionType=STOP_LOSS, sz=-1, …, slTriggerPx=…)` (значение вне домена, OKX-слой) | HTTP 200; `data[0].sCode≠"0"` **или** `code≠"0"` | Реджект отрицательного размера; код — наблюдение | _…_ |
| **M19.neg.reqParam.** `placeAlgoOrder` без `sz` (passthrough-слой) | HTTP 4xx | Гард прокси: обязательный `@RequestParam sz` | _…_ |
| **M19.neg.dupClId.** дважды `placeAlgoOrder(... algoClOrdId=<тот же>, conditionType=STOP_LOSS, slTriggerPx=…)` (дубль) | HTTP 200; второй — `data[0].sCode≠"0"` (дубль) **или** наблюдение | Реджект/поведение на дубль algoClOrdId — фиксируем; teardown: снять прошедшие | _…_ |

## M20. cancelAlgos — POST /trade/cancel-algos (Cancel algo ordinary)

- **Объект:** `cancelAlgos(requests)` (passthrough `DELETE /algo-order`
  с `conditionType` ordinary-семьи). **Среда:** demo. **WRITE.** Прямой —
  M19cond/M19tp/M19oco (.cancel). **Вариант — семья ordinary.**
  **Teardown:** в цепочке.

### M20.1 негатив (no-state) — cancel несущ. algoId (ordinary)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `cancelAlgoOrder(instId=ETH-USDT-SWAP, conditionType=STOP_LOSS, algoId=9999999999999999)` → ordinary | HTTP 200; `data[0].sCode≠"0"` (algo не найден/закрыт) | Реджект cancel несущ. algo (ordinary семья); код — наблюдение | _…_ |

### M20.2 прямой + вариант

Покрыт M19cond.cancel / M19tp.cancel / M19oco.cancel (ordinary `sCode=0`).

## M21. cancelAdvanceAlgos — POST /trade/cancel-advance-algos (Cancel advance algo)

- **Объект:** `cancelAdvanceAlgos(requests)` (passthrough
  `DELETE /algo-order` с `conditionType` advance-семьи). **Среда:** demo.
  **WRITE.** Прямой — M19trailing (.cancel, ядро И-2). **Вариант — семья
  advance.** **Teardown:** в цепочке.

### M21.1 негатив (no-state) — cancel несущ. algoId (advance)

| Запрос | Проверки (сырой DTO) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `cancelAlgoOrder(instId=ETH-USDT-SWAP, conditionType=TRAILING_PERCENTS, algoId=9999999999999999)` → advance | HTTP 200; `data[0].sCode≠"0"` **или** иная ошибка (наблюдение — И-2: endpoint выведен из офдока) | Реджект cancel несущ. advance-algo. **Если ответ — «endpoint не существует»** (не per-element реджект) → подтверждение делистинга = находка интегратору (C3), не выдумка | _…_ |

### M21.2 прямой + вариант

Покрыт M19trailing.cancel (advance `sCode=0` — гипотеза И-2; фейл =
находка).

---

# I-cred — пустые OKX-креды (auth-негатив клиентского слоя)

- **Объект:** любой приватный вызов сырого клиента (напр. `getBalance`)
  при `OkxProperties` без кредов. **Предусловие:** изолированная
  конфигурация — `api-key`/`secret`/`passphrase` не заданы (не demo, не
  prod). **Среда:** локально, без сети. **Teardown:** не требуется.
  **Тип:** probe бага I3 (auth-негатив). **Не в коллекции** — требует
  изолированной конфигурации без кредов (поднятый app креды имеет);
  проверяется код-тестом / ручным бутом.

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getBalance(ccy)` на пустых кредах | HTTP≠200; тело содержит `credential` (целевое fail-fast) | **Целевое (I3 closed):** внятная ошибка «OKX credentials not configured». **Текущее (баг I3, `backlog` §I3):** NPE в `OkxSigningInterceptor.sign()` (`getSecret().getBytes()` на `null`) — до отправки, сети не достигает. NPE → I3 открыт; внятная ошибка → закрыт | _…_ |

---

## Закрытая развилка: нога amend

Цикл «place → getOrder → amend → cancel» — в клиентском слое метода
amend **нет** (`OkxRestClient`/`OkxProxyController` его не несут; amend
снят делтой REPLACE-only, `docs/decisions/replace-not-amend.md`).
**Закрыто вариантом (a) (аппрув 2026-06-12):** нога amend убрана; цикл =
place → getOrder → cancel → проверка отменённого (Climit). Будущий тест
ремодела — **REPLACE-цепочка**, гейтится приземлением REPLACE-оркестрации
в `ServiceCommandFactory` (`backlog.md` §Хвост шага 4); отдельным заходом
по триггеру «новый эндпоинт».

## Нерешённое (на аппрув / валидацию)

1. **Расширение A2-passthrough 7 read-эндпоинтами** (`OkxProxyController`:
   candles ×2, orders-pending/history, algo-pending/history, fills-history).
   Сделано — иначе коллекция не 1:1 со всем периметром клиента. Все read,
   write-риска нет; passthrough — инструмент тестирования, не продуктовый
   API. **На валидацию:** оставить расширение или вернуть эти методы в
   passthrough-exposure-gap (план покрывает, коллекция — нет).
2. **Market-цепочка Cmarket ставит реальный исполняемый ордер на demo**
   (fill + позиция, минимальный sz, teardown closePosition). Снимает
   пилотный отказ fills/позиций. **На валидацию:** допустимо ли реальное
   исполнение на demo в контуре (риск — минимальный sz; teardown
   обязателен), или ограничиться неисполнимыми (тогда fills/позиции —
   отказ по достижимости, как в пилоте).
3. **Вариант-gap'ы** (trailing-value `callbackSpread`; битый сырой
   `ordType`-негатив; all-ccy `getBalance` без passthrough-параметра) —
   зафиксированы, не выдуманы. Закрывать ли passthrough-параметрами под
   них — отдельное решение (расширение прокси под доменно-неиспользуемое
   = против `codestyle` §«Неиспользуемый код»; крен — не закрывать).
4. **Точные коды ошибок негатива** — где контракт не документирует,
   ожидание = «реджект (code≠0)», точный код — наблюдение прогона
   (находка интегратору на C3), не выдумка.

## Связи

- Процесс контура — `.claude/processes/source-api-testing.md`.
- Шаблон — `.claude/templates/docs/test-plan.md`.
- Скиллы — `.claude/skills/{test-design,test-collection,test-code,test-run,test-review}.md`.
- Роль-автор — `.claude/agents/tester.md`.
- Решение о ре-базе / A2 — `.claude/decisions/source-api-target-rebase.md`.
- Манифест покрытия (колонка покрытия) — `docs/integrations/okx/coverage-manifest.md`.
- Контракты OKX — `docs/integrations/okx/contracts/` (`order.md`,
  `algo-order.md`, `position.md`, `fills.md`, `candle.md`, `instrument.md`,
  `balance.md`); правила — `docs/integrations/okx/rules/`.
- A2-passthrough —
  `src/main/java/com/example/tradingbot/api/controller/OkxProxyController.java`;
  сырой клиент — `OkxRestClient`; сырые DTO — `integration/model/okx/{request,response}`.
