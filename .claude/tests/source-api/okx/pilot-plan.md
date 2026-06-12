# Пилотный план тестов API OKX

## На какой вопрос отвечает этот файл

Как проверяем API OKX в пилотном скоупе (цикл ордера, негативный
слой, trailing-cancel, пустые креды, prod read-only).

## Статус

**Утверждён 2026-06-12** — аппрув пользователя без существенной
правки (чистая валидация `tester`, счёт гейта 1/3,
`.claude/work/delegation-ledger.md`). Развилка «нога amend» закрыта
вариантом (a) — см. ниже. Процесс —
`.claude/processes/source-api-testing.md`. Фактический результат и
вердикты по кейсам — в отчёте прогона (`history`).

## Скоуп

Пилот, обкатка формы плана. Триггер — разовый запуск по запросу
пользователя (первый план OKX; полный план по всем 26 контрактам —
вторым заходом после обкатки формы). Покрывает:

- **негативный слой** (без состояния) — cancel несуществующего,
  getOrder по фейковому id;
- **цепочку ордера** по графу предусловий — place → getOrder →
  cancel → проверка отменённого;
- **И-2** — trailing `move_order_stop` через `cancel-advance-algos`
  на demo (рантайм-снятие конфликта спеки);
- **I3** — поведение клиентского слоя на пустых OKX-кредах;
- **prod read-only** — balance, instruments, candles, positions.

Объект — **API OKX через наш клиентский слой** (`IntegrationService`
/ `OkxRestClient`, граничная поверхность `OkxProxyController`).
Домен (Deal, FSM, executors) вне охвата.

## Среда

- **demo** (test-профиль, demo-креды, header `x-simulated-trading:
  1` через `okx.simulated`) — все write-операции (place / cancel
  ордеров и algo) и их подтверждающие чтения.
- **prod read-only** (prod-профиль, prod read-креды) — отдельный
  блок только чтений; **никаких write-эффектов на prod**.
- **изолированная конфигурация пустых кредов** (`OkxProperties` без
  `api-key`/`secret`/`passphrase`) — кейс I3; сети не достигает.

Инструмент пилота — `ETH-USDT-SWAP` (адаптер: `tdMode=isolated`,
`posSide=net` — хардкод, `docs/integrations/okx/rules/adapter-constants.md`).

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
  Фиксируется в наблюдениях.
- **Per-element `sCode`.** Реджект приходит в `data[0].sCode`, не
  только в top-level `code`.

## Негативный слой (без состояния) — demo, гоняется первым

### N1. Cancel несуществующего ордера

- **Объект:** `cancelOrder` (proxy `DELETE /api/proxy/okx/order`).
- **Предусловие:** отсутствие ордера (заведомо несуществующий
  `ordId`).
- **Шаги:** `cancelOrder(instId=ETH-USDT-SWAP, ordId=<несущ.>)`.
- **Ожидаемый результат:** реджект — `data[0].sCode = 51603` (order
  does not exist); `ExchangeAck` отражает реджект, а не «успех».
- **Среда:** demo.
- **Наблюдения (RUN):** как клиентский слой прокидывает per-element
  `sCode` 51603 в `ExchangeAck`. _Факт: …_

### N2. getOrder по фейковому id

- **Объект:** `getOrder` — два уровня наблюдения.
- **Предусловие:** отсутствие ордера (фейковый `ordId`).
- **Шаги:** (a) `OkxRestClient.getOrder(instId, ordId=<фейк>, null)`
  напрямую; (b) `IntegrationService.getOrder(...)` /
  proxy `GET /api/proxy/okx/order` (снапшот через evidence-cycle).
- **Ожидаемый результат:** (a) сырой клиент — `sCode = 51603`;
  (b) снапшот-резолюция — evidence-cycle (order details →
  orders-pending → orders-history → archive) исчерпывается в
  терминал «не найден» (`MISSING_AFTER_REFRESH` /
  `docs/decisions/refresh-evidence-cycle-ownership.md`). **Два
  уровня различаются** — сырой код vs терминал снапшота.
- **Среда:** demo.
- **Связь:** 51603-on-not-found — предпосылка D-B3 (SUBMIT
  recovery-by-clientId, `backlog.md`); пилот фиксирует, как код
  доходит до клиентского слоя. _Факт: …_

## Цепочка ордера (по графу предусловий) — demo

Граф: C1 (place) → C2 (getOrder live) → C3 (cancel) → C4 (getOrder
canceled). Нога amend убрана (развилка закрыта вариантом (a), аппрув
2026-06-12) — см. «Закрытая развилка: нога amend».

### C1. Place limit-ордера (остаётся live)

- **Объект:** `placeOrder` (proxy `POST /api/proxy/okx/order`).
- **Предусловие:** достаточный demo-баланс (нет ордеров/позиций).
- **Шаги:** limit buy, `px` далеко ниже mark (≈ −50 %, чтобы
  практически исключить исполнение и держать ордер live),
  `sz` = минимальный по instrument-спеке, `reduceOnly=false`.
- **Ожидаемый результат:** `data[0].sCode=0`, `ordId` в
  `ExchangeAck.externalId`. **`sCode=0` ≠ live** — подтверждается в
  C2.
- **Среда:** demo.
- **Выход → предусловие C2/C3.** _Факт: …_

### C2. getOrder на размещённом — подтверждение place

- **Объект:** `getOrder` (proxy / `IntegrationService`).
- **Предусловие:** живой неисполненный ордер из C1 (`ordId`).
- **Шаги:** `getOrder(instId, ordId из C1)`.
- **Ожидаемый результат (не эхо C1):** state = `live` (не `filled`,
  не `canceled`); адаптер-инварианты в ответе (`tdMode=isolated`,
  `posSide=net`, `reduceOnly=false` как в запросе). Проверяет, что
  ACK из C1 действительно стал live-ордером (ACK ≠ runtime truth),
  плюс инварианты, — а не повтор параметров запроса.
- **Среда:** demo.
- **Выход → предусловие C3.** _Факт: …_

### C3. Cancel живого ордера

- **Объект:** `cancelOrder` (proxy `DELETE /api/proxy/okx/order`).
- **Предусловие:** живой неисполненный ордер из C1/C2 (`ordId`).
- **Шаги:** `cancelOrder(instId, ordId)`.
- **Ожидаемый результат:** `data[0].sCode=0` в `ExchangeAck`; **это
  ACK, не финал** — `CANCELED` подтверждается в C4.
- **Среда:** demo.
- **Выход → предусловие C4.** _Факт: …_

### C4. getOrder на отменённом — подтверждение cancel

- **Объект:** `getOrder`.
- **Предусловие:** отменённый ордер из C3 (`ordId`).
- **Шаги:** `getOrder(instId, ordId)`.
- **Ожидаемый результат:** снапшот — state = `canceled` (через
  evidence-cycle order details / orders-history), **не** `live`.
  Подтверждает финал cancel, не ACK.
- **Среда:** demo.
- **Наблюдения (RUN):** различить, через какое звено evidence-cycle
  виден `canceled` (details vs history). _Факт: …_

### Teardown цепочки C

- После C3 ордер отменён. Если прогон прерван между C1 и C3 —
  отменить повисший ордер (`cancelOrder` по сохранённому `ordId`).
- Limit-цена C1 далеко от рынка → исполнение практически исключено;
  **на случай исполнения** (рынок дошёл) — закрыть открывшуюся
  позицию (`closePosition`, demo) и зафиксировать как наблюдение.

## И-2 — trailing `cancel-advance-algos` (demo)

Снятие конфликта спеки рантаймом: `cancel-advance-algos` выведен из
офдока 2025-04-24, но `OkxRestClient.cancelAdvanceAlgos` существует
и клиентский слой ветвит cancel по семье algo (И-1(а),
`docs/integrations/okx/contracts/algo-order.md`). Цель — проверить
**cancel-путь advance-семьи**, не торговую корректность trailing.

### A1. Place trailing `move_order_stop`

- **Объект:** `placeAlgoOrder` (proxy `POST /api/proxy/okx/algo-order`).
- **Предусловие:** нет — стартуем **без открытой позиции**.
  Рантайм-резолюция (решение аппрува): если demo реджектит
  reduce-only trailing без позиции — открыть позицию min-size
  (`placeOrder` market buy), затем A1; тогда в цепочку добавляются
  ноги open-position (до A1) и close-position (teardown).
- **Шаги:** `placeAlgoOrder(ordType=move_order_stop, callbackRatio,
  reduceOnly=true)`; при реджекте «нет позиции» — открыть позицию
  min-size и повторить A1.
- **Ожидаемый результат:** `data[0].sCode=0`, `algoId` в ack.
- **Среда:** demo.
- **Выход → предусловие A2/A3.** _Факт: …_

### A2. getAlgoOrder — подтверждение live

- **Объект:** `getAlgoOrder` (proxy `GET /api/proxy/okx/algo-order`).
- **Предусловие:** размещённый trailing из A1 (`algoId`).
- **Шаги:** `getAlgoOrder(instId, algoId, ordType=move_order_stop)`.
- **Ожидаемый результат:** снапшот — algo `live`/`effective`.
- **Среда:** demo. _Факт: …_

### A3. Cancel через `cancel-advance-algos` (ядро И-2)

- **Объект:** `cancelAlgoOrder` для advance-семьи → клиентский слой
  ветвит на `OkxRestClient.cancelAdvanceAlgos`.
- **Предусловие:** живой trailing из A1/A2 (`algoId`).
- **Шаги:** `cancelAlgoOrder(algoOrder=move_order_stop, instId)`;
  проверить, что вызов ушёл на `cancel-advance-algos`, а не
  `cancel-algos`.
- **Ожидаемый результат — частично неизвестен (рантайм-снятие И-2).**
  Гипотеза: эндпоинт существует на demo и отвечает `sCode=0`. **Если
  demo отвечает «endpoint не существует» / иной ошибкой** — спека в
  сторону делистинга подтверждена. Незадокументированное **не
  выдумывается**: вердикт A3 = **находка интегратору** (правка
  `algo-order.md` в подтверждённую сторону), не pass/fail с заранее
  известным ожиданием.
- **Среда:** demo. _Факт: …_

### A4. getAlgoOrder — подтверждение cancel

- **Объект:** `getAlgoOrder` на отменённом.
- **Предусловие:** отменённый trailing из A3 (`algoId`).
- **Ожидаемый результат:** снапшот — algo `canceled`.
- **Среда:** demo. _Факт: …_

### Teardown И-2

- Trailing отменён (A3). Если открывалась позиция под reduce-only —
  закрыть (`closePosition`, demo). Прогон прерван — отменить trailing
  (`cancel-advance-algos`) и закрыть позицию.

## I3 — пустые OKX-креды (клиентский слой)

### I3-1. Приватный вызов на незаполненных кредах

- **Объект:** любой приватный вызов клиентского слоя (например
  `getBalance`) при `OkxProperties` без кредов.
- **Предусловие:** изолированная конфигурация — `api-key`/`secret`/
  `passphrase` не заданы (не demo, не prod).
- **Шаги:** инициировать приватный эндпоинт; `OkxSigningInterceptor`
  отрабатывает **до** отправки.
- **Ожидаемый результат (целевой, I3):** внятная ошибка
  fail-fast — «OKX credentials not configured». **Текущее
  поведение** (до фикса): NPE в `OkxSigningInterceptor.sign()`
  (`properties.getSecret().getBytes(...)` на `null`).
- **Среда:** локально, без сети.
- **Вердикт (RUN):** NPE → подтверждает баг **I3** (`backlog.md`
  §Инфра-долг I3, уже заведён) → находка/backlog; внятная ошибка →
  I3 закрыт. Кейс — **probe известного незакрытого бага**.
- **Наблюдения:** на каком вызове/строке падает, тип исключения,
  достигает ли сети. _Факт: …_

## Prod read-only блок (prod-креды, только чтения)

Без write-эффектов. Teardown не нужен (чтения без эффекта);
контроль — что ни один write на prod не вызван.

### P1. getBalance (private read)

- **Объект:** `getBalance` (proxy `GET /api/proxy/okx/balance?ccy=USDT`).
- **Среда:** prod read-only.
- **Ожидаемый результат:** снапшот баланса, `code=0`, details по
  USDT. _Факт: …_

### P2. getInstrument (public)

- **Объект:** `getInstrument` (proxy `GET /api/proxy/okx/instrument`).
- **Среда:** prod (public, без кредов).
- **Ожидаемый результат:** снапшот инструмента `ETH-USDT-SWAP`
  (min/max size, precision, contract multiplier). _Факт: …_

### P3. Candles (public)

- **Объект:** `OkxRestClient.getLatestCandles` / `getHistoryCandles`.
  **Surface-gap:** на `OkxProxyController` candle-эндпоинта нет —
  чтение идёт прямым клиентским вызовом / market-data путём.
- **Среда:** prod (public).
- **Ожидаемый результат:** список свечей по `instId`/`bar`.
- **Наблюдения:** зафиксировать отсутствие proxy-passthrough для
  свечей. _Факт: …_

### P4. getPosition (private read)

- **Объект:** `getPosition` (proxy `GET /api/proxy/okx/position?instId=ETH-USDT-SWAP`).
- **Среда:** prod read-only.
- **Ожидаемый результат:** снапшот позиции; **пустой / нет
  позиции — валидный исход** (read-only, ничего не открываем).
  _Факт: …_

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
- Роль-автор — `.claude/agents/tester.md`.
- Контракты OKX — `docs/integrations/okx/contracts/`
  (`order.md`, `algo-order.md`, `position.md`, `balance.md`,
  `instrument.md`, `candle.md`).
- Правила источника — `docs/integrations/okx/rules/`
  (`adapter-constants.md`, `reduce-only-invariant.md`).
