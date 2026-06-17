# Пилотный план тестов API OKX

## На какой вопрос отвечает этот файл

Как проверяем API OKX в пилотном скоупе (live-цена, негативный
слой, цикл ордера, trailing-cancel, пустые креды, prod read-only).

## Статус

**Перегенерирован с нуля 2026-06-15 под действующие правила**
(`test-design` / `test-collection` / шаблон `test-plan`). Замещает
интеримную версию (аппрув 2026-06-12), сгенерированную до того, как
методология тест-коллекций стала правилами. Ключевое изменение:
**price-gap закрыт** — добавлен proxy-эндпоинт live-цены
(`GET /api/proxy/okx/market-price` → `getMarketPriceData` →
`MarketPriceDataExternalSnapshot`), цена ордера теперь тянется с
биржи, а не выдумывается. Форма кейса приведена к шаблонной (таблица
на кейс, строка = запрос).

**На аппрув** (DESIGN-only заход: пилот останавливается после этапа
REVIEW+APPROVE; RUN/разбор — отдельным заходом). Процесс —
`.claude/processes/source-api-testing.md`. Поле «факт» пустое до RUN.

## Скоуп

Пилот, обкатка формы плана. Триггер — разовый запуск по запросу
пользователя (регенерация под действующие правила). Покрывает:

- **live-цена** — `getMarketPriceData` (новый proxy-passthrough),
  источник неисполнимой цены для C и standalone read на prod;
- **негативный слой** (без состояния) — cancel несуществующего,
  getOrder по фейковому id;
- **цепочку ордера** по графу предусловий — live-цена → place →
  getOrder → cancel → проверка отменённого;
- **И-2** — trailing `move_order_stop` через `cancel-advance-algos`
  на demo (рантайм-снятие конфликта спеки);
- **I3** — поведение клиентского слоя на пустых OKX-кредах;
- **prod read-only** — balance, instrument, market-price, position.

Объект — **API OKX через наш клиентский слой** (`IntegrationService`
/ `OkxRestClient`, граничная поверхность `OkxProxyController`).
Домен (Deal, FSM, executors) вне охвата. Пилот — подмножество
поверхности (полный план по всей поверхности — отдельным заходом).

## Среда

- **demo** (test-профиль, demo-креды, header `x-simulated-trading:
  1` через `okx.simulated`) — все write-операции (place / cancel
  ордеров и algo) и их подтверждающие чтения, плюс live-цена цепочки.
- **prod read-only** (prod-профиль, prod read-креды) — отдельный
  блок только чтений; **никаких write-эффектов на prod**.
- **изолированная конфигурация пустых кредов** (`OkxProperties` без
  `api-key`/`secret`/`passphrase`) — кейс I3; сети не достигает.

Инструмент пилота — `ETH-USDT-SWAP` (адаптер: `tdMode=isolated`,
`posSide=net` — хардкод, `docs/integrations/okx/rules/adapter-constants.md`).
Спека (сверка 2026-06-15): `minSz=0.01`, `lotSz=0.01`, `tickSz=0.01`,
`ctVal=0.1` — целочисленная цена кратна `tickSz`.

## Сквозные проверки (красная нить)

Применяются в каждом релевантном кейсе, не отдельными кейсами:

- **ACK ≠ runtime truth.** `sCode=0` на place / cancel — приём
  запроса, не подтверждение факта; факт подтверждается отдельным
  чтением (`docs/integrations/okx/contracts/order.md`,
  `docs/rules/ack-not-runtime-truth.md`).
- **Адаптер-инварианты в ответе.** Каждый order/algo/position-ответ
  несёт `tdMode=isolated`, `posSide=net`, а order — `reduceOnly`,
  совпадающий с запросом; расхождение — `EXCHANGE_INVARIANT_VIOLATION`
  (`docs/integrations/okx/rules/{adapter-constants,reduce-only-invariant}.md`).
  **Surface-gap:** инварианты на `OrderExternalSnapshot` наружу не
  выведены — через прокси не наблюдаемы (помечено в кейсах, проверка
  не выдумывается).
- **Per-element `sCode`.** Реджект приходит в `data[0].sCode`, не
  только в top-level `code`.

## Surface-расхождения прокси (общие для плана)

Граница ревью — прокси-поверхность; ниже неё кейс не лезет:

1. **proxy getOrder/getAlgoOrder — одиночный вызов, не
   evidence-cycle.** `GET /order` / `/algo-order` зовут один
   `trade-order` / `order-algo` с `verifyCode`-throw на ошибочный
   top-level `code`, а **не** доменный refresh evidence-cycle
   (pending → history → archive). Терминал `MISSING_AFTER_REFRESH`
   живёт в refresh-executor, на прокси не воспроизводится (N2/C4/A4).
2. **Адаптер-инварианты** `tdMode`/`posSide`/`reduceOnly` на
   снапшоте ордера не выведены — не наблюдаемы через прокси (C2).
3. **Сырой клиентский уровень** (например уровень (a) в N2 —
   `OkxRestClient.getOrder` напрямую) отдельным proxy-эндпоинтом не
   покрыт; запрос не выдумывается.
4. **Свечи на прокси нет** — `OkxProxyController` candle-passthrough'а
   не несёт; чтение свечей идёт market-data путём, не через прокси
   (prod-блок, см. ниже).

## Негативный слой (без состояния) — demo, гоняется первым

### N1. Cancel несуществующего ордера

- **Объект:** `cancelOrder` (proxy `DELETE /api/proxy/okx/order`).
- **Предусловие-состояние:** отсутствие ордера (заведомо
  несуществующий `ordId`).
- **Среда:** demo. **Teardown:** не требуется (эффекта нет).

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `cancelOrder(instId=ETH-USDT-SWAP, ordId=<несущ.>)` | HTTP 200; `ExchangeAck.success=false`; `ExchangeAck.code=51603`; ack несёт `code` + `message` | Реджект отражён в `ExchangeAck` (не throw): per-element `data[0].sCode=51603` (order does not exist) прокинут в ack с HTTP 200. **Находка по дизайну:** HTTP 500 вместо ack = рассогласование реджект-кодов (`backlog`: 500 вместо 422/409) — проверка фейлит и показывает его | _…_ |

### N2. getOrder по фейковому id

- **Объект:** `getOrder` — два уровня наблюдения.
- **Предусловие-состояние:** отсутствие ордера (фейковый `ordId`).
- **Среда:** demo. **Teardown:** не требуется.
- **Связь:** 51603-on-not-found — предпосылка D-B3 (SUBMIT
  recovery-by-clientId, `backlog.md`); пилот фиксирует, как код
  доходит до клиентского слоя.

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| (a) `OkxRestClient.getOrder(instId, ordId=<фейк>, null)` напрямую | — (**surface-gap:** сырой клиент не покрыт прокси) | Сырой клиент: `sCode=51603`. Запрос через прокси не выдумывается — фиксируется как gap | _…_ |
| (b) proxy `GET /api/proxy/okx/order?ordId=<фейк>` | HTTP 200; тело `null` **или** `externalStatus≠live` | Резолюция «не найден» (HTTP 200, нет живого ордера). **Surface-расхождение 1**: это одиночный `trade-order`, не evidence-cycle. Если OKX отдаёт 51603 top-level → `verifyCode` throw → HTTP 500: фейл проверки = находка (реджект исключением, а не «не найден») | _…_ |

## Цепочка ордера (по графу предусловий) — demo, WRITE

Граф: **C-price** (live-цена) → **C1** (place) → **C2** (getOrder
live) → **C3** (cancel) → **C4** (getOrder canceled). Нога amend
убрана (развилка закрыта вариантом (a), аппрув 2026-06-12 — см.
«Закрытая развилка: нога amend»).

- **Объект:** цепочка `getMarketPriceData` → `placeOrder` →
  `getOrder` → `cancelOrder` → `getOrder`.
- **Предусловие-состояние:** достаточный demo-баланс, нет
  ордеров/позиций по инструменту.
- **Среда:** demo. **WRITE — реальный ордер на бирже** (на
  prod-профиле — реальные деньги; пилот — demo).

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **C-price.** `getMarketPriceData(instId)` (proxy `GET /market-price`) | HTTP 200; `externalLastPrice` присутствует и `> 0` | Снапшот `MarketPriceDataExternalSnapshot` с live `externalLastPrice`/`Ask`/`Bid`. Коллекция кладёт `last` в переменную и выводит **неисполнимую** цену C1: `c1_px = floor(last · 0.5)` (limit buy ≈ −50 % от рынка — практически не исполнится; кратно `tickSz=0.01`) | _…_ |
| **C1.** `placeOrder(side=buy, sz=min, px=c1_px, reduceOnly=false)` (proxy `POST /order`) | HTTP 200; `success=true`; `code=0`; `externalId` (ordId) непустой | `data[0].sCode=0`, `ordId` в `ExchangeAck.externalId`. **`sCode=0` ≠ live** — подтверждается в C2. Цена — из C-price, не константа. Выход → `c_ordId` | _…_ |
| **C2.** `getOrder(instId, ordId=c_ordId)` (proxy `GET /order`) | HTTP 200; `externalId=c_ordId`; `externalStatus=live`; снапшот несёт `side`/`size`/`price`/`externalStatus` | ACK из C1 стал **live**-ордером (не filled/canceled) — ACK ≠ runtime truth, а не эхо запроса. **Surface-расхождение 2:** инварианты `tdMode`/`posSide`/`reduceOnly` на снапшоте не выведены — не проверяются | _…_ |
| **C3.** `cancelOrder(instId, ordId=c_ordId)` (proxy `DELETE /order`) | HTTP 200; `success=true`; `code=0` | `data[0].sCode=0` в `ExchangeAck`. **Это ACK, не финал** — `CANCELED` подтверждается в C4 | _…_ |
| **C4.** `getOrder(instId, ordId=c_ordId)` (proxy `GET /order`) | HTTP 200; `externalStatus=canceled` (не `live`) | Финал cancel (не ACK). **Surface-расхождение 1:** одиночный `trade-order` (отменённый ордер ещё доступен по `ordId`), не evidence-cycle | _…_ |
| **Teardown C.** при исполнении C1 (рынок дошёл) — `closePosition(instId, ccy)`; прогон прерван между C1 и C3 — `cancelOrder` по `c_ordId` | — | После C3 ордер отменён → биржа чистая. Цена C1 далеко от рынка → исполнение практически исключено; исполнение — зафиксировать наблюдением и закрыть позицию | _…_ |

## И-2 — trailing `cancel-advance-algos` (demo, WRITE)

Снятие конфликта спеки рантаймом: `cancel-advance-algos` выведен из
офдока 2025-04-24, но `OkxRestClient.cancelAdvanceAlgos` существует
и клиентский слой ветвит cancel по семье algo (И-1(а),
`docs/integrations/okx/contracts/algo-order.md`). Цель — проверить
**cancel-путь advance-семьи**, не торговую корректность trailing.

- **Объект:** цепочка `placeAlgoOrder` → `getAlgoOrder` →
  `cancelAlgoOrder`(advance) → `getAlgoOrder`.
- **Предусловие-состояние:** нет — стартуем **без открытой позиции**;
  рантайм-резолюция A0 (ниже). Trailing цены с биржи не требует
  (callback в %, не px).
- **Среда:** demo. **WRITE — реальные algo/ордер/позиция.**

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **A0 (опц.).** `placeOrder(side=buy, sz=min, market)` — открыть позицию | HTTP 200; `success=true` | **Условная нога** (рантайм-резолюция аппрува): выполнять только если A1 реджектит reduce-only trailing без позиции. Парная нога — A-td | _…_ |
| **A1.** `placeAlgoOrder(conditionType=TRAILING_PERCENTS, direction=SELL, sz=min, reduceOnly=true, trailingPercents)` (proxy `POST /algo-order`) | HTTP 200; `success=true`; `code=0`; `externalId` (algoId) непустой | `data[0].sCode=0`, `algoId` в ack. При реджекте «нет позиции» — A0 и повтор A1. Выход → `a_algoId` | _…_ |
| **A2.** `getAlgoOrder(instId, algoId=a_algoId)` (proxy `GET /algo-order`) | HTTP 200; снапшот несёт `externalId` + `externalStatus`; `externalStatus≠canceled` | Trailing из A1 — `live`/`effective` | _…_ |
| **A3.** `cancelAlgoOrder(conditionType=TRAILING_PERCENTS, algoId)` → ветвь `cancel-advance-algos` (proxy `DELETE /algo-order`) | HTTP 200; `success=true` (**гипотеза**) — фейл = сигнал находки, не pass/fail | **Ядро И-2. Вердикт частично неизвестен** (рантайм-снятие). Гипотеза: эндпоинт жив на demo, `sCode=0`. Если demo отвечает «endpoint не существует»/иной ошибкой → делистинг подтверждён = **находка интегратору** (правка `algo-order.md`), не заранее известный pass/fail. `code`/`message` логируются | _…_ |
| **A4.** `getAlgoOrder(instId, algoId=a_algoId)` (proxy `GET /algo-order`) | HTTP 200; `externalStatus=canceled` **или** пустой ответ (наблюдение, не молчаливый pass) | Финал cancel trailing. **Surface-расхождение 1:** одиночный `order-algo`, не evidence-cycle | _…_ |
| **Teardown И-2.** если открывалась позиция (A0) — `closePosition(instId, ccy)` (A-td); прогон прерван — отменить trailing (`cancel-advance-algos`) и закрыть позицию | — | Trailing отменён (A3) + позиция закрыта → биржа чистая | _…_ |

## I3 — пустые OKX-креды (клиентский слой)

### I3-1. Приватный вызов на незаполненных кредах

- **Объект:** любой приватный вызов клиентского слоя (например
  `getBalance`) при `OkxProperties` без кредов.
- **Предусловие-состояние:** изолированная конфигурация — `api-key`/
  `secret`/`passphrase` не заданы (не demo, не prod).
- **Среда:** локально, без сети. **Teardown:** не требуется.
- **Тип:** probe известного незакрытого бага I3.

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getBalance(ccy)` (proxy `GET /balance`) на пустых кредах | HTTP ≠ 200; тело содержит `credential` (целевое fail-fast) | **Целевое (I3 closed):** внятная ошибка «OKX credentials not configured». **Текущее (баг I3, `backlog` §Инфра-долг I3):** NPE в `OkxSigningInterceptor.sign()` (`getSecret().getBytes()` на `null`). `OkxSigningInterceptor` отрабатывает **до** отправки — сети не достигает. NPE → I3 открыт; внятная ошибка → I3 закрыт | _…_ |

## Prod read-only блок (prod-креды, только чтения)

Без write-эффектов. Teardown не нужен (чтения без эффекта);
контроль — что ни один write на prod не вызван.

### P1. getBalance (private read)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getBalance(ccy=USDT)` (proxy `GET /balance`) | HTTP 200; снапшот несёт `externalTotalEquity` + `balances[]`; есть деталь по `USDT` | Account-level снапшот баланса, `code=0`, details по USDT | _…_ |

### P2. getInstrument (public)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getInstrument(instId=ETH-USDT-SWAP, instType=SWAP)` (proxy `GET /instrument`) | HTTP 200; `externalInstrumentId=ETH-USDT-SWAP`; снапшот несёт `externalMinSize`/`externalTickSize`/`externalContractMultiplier` | Снапшот инструмента (min/max size, precision, contract multiplier). `externalMinSize` — опора `min_sz` demo-цепочек C/A | _…_ |

### P3. getMarketPriceData (public) — новый эндпоинт

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getMarketPriceData(instId=ETH-USDT-SWAP)` (proxy `GET /market-price`) | HTTP 200; `externalInstrumentId=ETH-USDT-SWAP`; `externalLastPrice`/`externalAskPrice`/`externalBidPrice` присутствуют и `> 0`; `externalTimestamp` присутствует | Снапшот `MarketPriceDataExternalSnapshot` с live last/ask/bid + ts (ms). Standalone-валидация нового passthrough'а (публичный read, без кредов; на prod read-only безопасен). Тот же эндпоинт функционально используется в C-price | _…_ |

### P4. getPosition (private read)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `getPosition(instId=ETH-USDT-SWAP)` (proxy `GET /position`) | HTTP 200; тело `null` (нет позиции) **или** снапшот с `externalId` — оба валидны | Снапшот позиции; **пустая/нет позиции — валидный исход** (read-only, ничего не открываем): `getPosition` при пустом `data` → `null` → HTTP 200 с пустым телом | _…_ |

### Surface-gap: свечи (P-candles, не покрыто)

`OkxRestClient.getLatestCandles`/`getHistoryCandles` существуют, но
**на `OkxProxyController` candle-эндпоинта нет** (surface-расхождение
4). Чтение свечей идёт market-data путём, не через прокси — запрос
**не выдумывается**. Находка плана: зафиксировать отсутствие
proxy-passthrough для свечей (кандидат на закрытие отдельным
эндпоинтом, как закрыт price-gap).

## Закрытая развилка: нога amend

Скоуп просил цикл «place → getOrder → **amend** → cancel». При
DESIGN вскрыто (сверка с кодом + консультация интегратора): **в
клиентском слое метода amend нет** — `OkxRestClient`,
`IntegrationService`, `OkxProxyController` его не несут; amend снят
делтой REPLACE-only (`docs/decisions/replace-not-amend.md`).
Доменный ремодел — REPLACE (cancel-old + place-new), его оркестрация
в `ServiceCommandFactory` ещё не реализована (`backlog.md` §Хвост
шага 4).

**Закрыто вариантом (a) — аппрув 2026-06-12.** Нога amend убрана из
пилота; цикл = place → getOrder → cancel → проверка отменённого.
Осмысленный будущий тест ремодела — **REPLACE-цепочка** (place-new +
факт + cancel-old по риск-классу), **гейтится приземлением
REPLACE-оркестрации** в `ServiceCommandFactory` (`backlog.md` §Хвост
шага 4); заводится отдельным заходом по триггеру «новый эндпоинт».

Отклонённые: **(b)** сырой passthrough `amend-order` — неиспользуемый
продакшн-код под путь, от которого домен отказался (`codestyle`
§«Неиспользуемый код»); **(c)** REPLACE сейчас — оркестрация не
реализована, нечего гонять.

## Связи

- Процесс контура — `.claude/processes/source-api-testing.md`.
- Шаблон — `.claude/templates/docs/test-plan.md`.
- Скиллы — `.claude/skills/{test-design,test-collection,test-review,test-run}.md`.
- Роль-автор — `.claude/agents/tester.md`.
- Контракты OKX — `docs/integrations/okx/contracts/`
  (`order.md`, `algo-order.md`, `position.md`, `balance.md`,
  `instrument.md`, `market-price-data.md`, `mark-price.md`, `candle.md`).
- Правила источника — `docs/integrations/okx/rules/`
  (`adapter-constants.md`, `reduce-only-invariant.md`).
- Модель цены — `docs/components/models/MarketPriceData.md`,
  `docs/models/mapping/MarketPriceData.md`.
- Прокси-поверхность —
  `src/main/java/com/example/tradingbot/api/controller/OkxProxyController.java`.
