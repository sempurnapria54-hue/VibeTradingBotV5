# Полный план тестов API OKX (сырьё, /raw-only)

## На какой вопрос отвечает этот файл

Как проверяем API OKX **на уровне сырья** через единственный
generic-эндпоинт `POST /api/proxy/okx/raw` — **по всему in-perimeter
периметру манифеста**: прямой кейс + порождённый негатив на каждый
эндпоинт, варианты типа-дискриминатора, цепочки предусловий и teardown.

## Статус

**Полное in-perimeter покрытие через `/raw`** (2026-06-19). Регенерирован
под механизм **`/raw`-only + JsonNode** (`.claude/decisions/source-api-target-rebase.md`,
раздел D): замещает прежний субсет «манифест ∩ клиент» на типизированном
A2-passthrough. Структура **эндпоинт → кейсы → таблица** (раздел на
эндпоинт, кейс на подраздел, запрос на строку).

**DESIGN аппрувнут** (2026-06-19): план + коллекция прошли два
независимых адверсариальных ревью (оба APPROVE), §Принятые решения
§Нерешённое 1-4 закрыты. CODE-тесты написаны
(`src/test/java/com/example/tradingbot/integration/sourceapi/okx/`).

**RUN прогнан** (2026-06-20, demo/non-prod, `mvn -o test -Dgroups=source-api-live`;
сырые логи не сохранены, итоговый отчёт —
`.claude/work/history/2026-06-20-source-api-contour/REPORT-2026-06-19-source-api-stabilization.md`):
**200 тестов, 198 pass / 2 fail**,
BUILD SUCCESS (`-Dmaven.test.failure.ignore=true`). Колонка «Факт +
наблюдения (RUN)» заполнена по всем 312 строкам. Изначально **2 фейла**
(TG4.1, AG5.1) — **оба разобраны и закрыты** (см. ниже); остаточных фейлов
к разбору нет.

- **Разобрано и закрыто (TG4.1):** amend-batch заACKан (`sCode=0`), но
  `getOrder` не отразил `newSz`/`newPx` за **25s** poll → таймаут. Причина —
  **тест-тюнинг, не дефект контракта:** amend на OKX асинхронен (ACK ≠ runtime
  truth), отражение на demo иногда >25s — что **уже** учтено у одиночного
  amend (TG3.1: per-case poll 60s), но batch-кейс TG4.1 остался на дефолтных
  25s. Тест выровнен на 60s (оба `waitUntil`) → перевалидировано **TG4 3/3
  green**. Не C3 (латентность
  amend — известное demo-поведение, не новый факт контракта).
- **Пересмотрена после прогона (группа AG5, лимит 12 заявок/сутки):**
  на прогоне квота была исчерпана → все POST AG5 отдали `b.code=50011` (Too
  Many Requests). Это **предусмотренный планом** rate-limit, а не дефект.
  Тесты пересмотрены (2026-06-20), `base.isRateLimited` сделан `protected`:
  - **AG5.1** (POST-ACK) — rate-limit/квота принимается как валидный исход
    наряду с ACK `0` и demo-ошибкой `50026`.
  - **AG5.3 / AG5.4** (негативы `quarter` вне домена / пропуск) — раньше
    «проходили» через `assertBusinessReject` по `50011`, т.е. валидация
    `quarter` была **замаскирована** rate-limit'ом (ложный pass). Теперь
    rate-limit → кейс **пропускается** (`assumeFalse(isRateLimited)`), реджект
    по `quarter` ассертится только при доступной квоте.
  - Перевалидировано — AG5 **4/4, 0 fail, 2 skip** (AG5.3/4 пропущены по
    исчерпанной квоте). Чтобы реально наблюдать реджект-по-`quarter`,
    нужен прогон при доступной квоте.
- **Ключевая находка C3 — И-2 подтверждён рантаймом:**
  `cancel-advance-algos` **жив на demo** (`M19tr.cancel`/`M19trs.cancel` →
  `b.code=0, data0.sCode=0`) вопреки выводу из офдока (changelog 2025-04-24);
  фейк-id (M21.1) → `sCode=51293`. Питает `external-source-sync` /
  `coverage-manifest` (провенанс `рантайм`).
- **Сквозные наблюдения негативов:** несущ. instId → `51001`; пропуск
  обязательного параметра → `50014`; битый `bar` → `51000`; batch-частичный
  успех → top-level `b.code=2` (`Bulk operation partially successful`).

Разбор находок (маршрутизация по владельцам, C3 в апидоки) — этап 7
процесса `.claude/processes/source-api-testing.md`.

## Принцип: контур проверяет контракт биржи, не наш код

API-тесты контура проверяют **контракт API биржи OKX** — что сам
эндпоинт OKX рабочий и его можно подключить в любой момент. Мапперы,
DTO, наша сериализация — **наш код, вне scope этого контура** (их
проверка — отдельные юнит-тесты мапперов, не здесь). Пройденный тест =
гарантия подключаемости метода OKX («проверено» ≠ «обязаны подключить»).

## Механизм — `/raw`-only

Каждый запрос идёт через ОДИН эндпоинт-конверт app:

```
POST {{base_url}}/api/proxy/okx/raw
{ "method": "GET|POST|DELETE", "path": "/api/v5/...",
  "query": { ... }, "body": { ... }, "signed": true|false }
```

- `/raw` подписывает под капотом (signing interceptor); **кредов в
  Postman нет**, demo/non-prod — профилем поднятого app.
- Возврат — `OkxApiResponse<JsonNode>`: `{ code, msg, data:[…] }`. Бизнес-
  реджекты OKX — HTTP 200 с `code≠"0"`/`data[i].sCode≠"0"`. Ассерты — по
  **сырым полям JSON** (`b.code`, `b.data[0].sCode`, `b.data[0].<field>`).
- `signed`: `/trade/*`, `/account/*` → `true`; `/public/*`, `/market/*`
  → `false`.

## Скоуп

**Весь in-perimeter манифеста, не субсет.** Источник набора —
`.claude/processes/api-docs-completion.md` (строки `есть-док`/`создан`/
`обновлён`); `вне-периметра`/`сознательно-вне` исключены. **60
эндпоинтов** покрыты (см. §Инвариант полноты), каждый через `/raw` —
включая прежние client-coverage-gap'ы (метода клиента нет — тело конверта
строится руками по контракту) и прежде заблокированные негативы (сырой
битый `ordType`, `callbackSpread`, all-ccy `getBalance`).

## Среда

- **demo/non-prod** (профиль поднятого app, demo-креды, `x-simulated-trading`)
  — все запросы, включая write (place/cancel/close ордеров и algo,
  account-write) и их подтверждающие чтения.
- **prod вне контура** — prod-кейсов нет.
- **изолированная конфигурация пустых кредов** — кейс `I-cred` (ниже),
  сети не достигает, **не через `/raw`**.

Инструмент — `ETH-USDT-SWAP` (`instType=SWAP`, `instFamily=ETH-USDT`;
адаптер `tdMode=isolated`, `posSide=net` — литералы тела,
`docs/integrations/okx/rules/adapter-constants.md`). Спека (сверка
2026-06-15): `minSz=0.01`, `lotSz=0.01`, `tickSz=0.01`, `ctVal=0.1` —
цена кратна `tickSz`.

## Структурный изоморфизм (план ↔ коллекция ↔ код-тесты)

- **эндпоинт** → раздел плана (`## <ID>n …`) / папка коллекции /
  (будущий) класс код-тестов;
- **кейс** → подраздел плана (`### <ID>n.k`) + таблица / запрос (или
  под-папка для цепочки) коллекции / (будущий) тест-метод;
- **запрос** → строка таблицы / request коллекции / (будущий) шаг-ассерт.

Коллекция (`collection.postman_collection.json`) — 1:1 с этим планом.
Исполнитель прогона — код-тесты (`test-code`), бьющие в тот же `/raw`
(механика 1:1 с коллекцией); коллекция — ревью/аппрув-артефакт.

## Сквозные проверки (красная нить)

- **ACK ≠ runtime truth.** `data[0].sCode=0` на place/cancel/close —
  приём, не факт; факт подтверждается отдельным чтением.
- **Per-element `sCode`.** Реджект приходит в `data[i].sCode`, не только
  top-level `code`.
- **Сырьё OKX, не наш слой.** Ассерты на сырых полях `OkxApiResponse<JsonNode>`.
- **Под `/raw` нет passthrough-слоя негатива.** Пропуск обязательного,
  значение вне домена, сырой битый `ordType` уходят **на OKX** →
  реджект OKX (`code≠"0"`), точный код — наблюдение, если не
  документирован. Единственный «прокси-слой» негатив — **сломанный
  конверт** (нераспознаваемый `method`/нет `path`): один кейс на контур
  (M1.6). **Вариант-gap'ов из-за типизации прокси больше нет.**
- **Незадокументированное не выдумывается.** Где спека не задаёт код —
  ожидание «реджект (`code≠"0"`)», точный код = наблюдение/находка
  интегратору (C3).
- **Цель — документировать реальное поведение OKX.** Прогон → наблюдение
  → находка интегратору (C3, правка апидоков). Регрессия-детект —
  следствие, не отдельная цель контура.
- **Инвариант восстановления состояния кейса.** Каждый **stateful-кейс**
  несёт **Snapshot.start** (первый шаг: для сущностей — ассерт чистого
  старта, нет живых ордеров/позиций/algo по инструменту; для настроек —
  снимок значения `leverage`/`posMode`/`acctLv` в переменную) и
  **Verify.end** (последний шаг после restore/teardown: ассерт «конец ==
  старт» через wait-until-condition, не sleep). **В охвате** (расхождение
  → фейл кейса): настройки и сущности. **Вне охвата** (наблюдаются, кейс
  не фейлят): append-only история (растёт), комиссионный/PnL-остаток
  баланса после реального fill. Реверсивные account-write
  (`leverage`/`posMode`/`acctLv`) на demo допущены. **Модель sweep+halt
  (код-тесты):** невозврат к старту — жёсткий фейл (сущности и настройки
  одинаково); дополнительно принудительный sweep (снять/закрыть сущность;
  настройку — re-set к снапшоту); если sweep не вычистил → **halt
  прогона**. Находка C3 (невосстановимое состояние) пишется в любом
  случае.
- **Поллинг — в код-тестах, не в коллекции.** План мандатит
  wait-until-condition (поллинг) на Verify.end/canceled/filled/flat. Этот
  поллинг реализуется в **код-тестах** (`test-code`, исполнитель прогона).
  В **коллекции** соответствующие ассерты canceled/filled/flat —
  **одношаговые** (без цикла) и потому **best-effort**: на индексинг-задержке
  OKX возможен ложный промах, на RUN допускается ретрай. Коллекция —
  ревью/аппрув-артефакт; гарантию схождения через поллинг даёт исполнитель.
  Код-тесты несут **per-case throttle** (пол ≥1с между всеми запросами к
  бирже + retry-on-429; покрывает и стык между тестами) и **per-case
  poll-таймаут** (дефолт 25с, медленным кейсам — длиннее) — два разных
  per-case числа; дефолты подкручиваются на прогоне.

## Порядок прогона

Дешёвые no-state негативы (read-эндпоинты gap-групп + no-state негативы
CORE) — **первыми**; затем write-цепочки CORE (Climit, Cmarket, M19*) и
write-кейсы gap-групп (batch/amend/DMS/precheck/account-write) с teardown.
Зависимости разрешаются порядком и переменными коллекции.

## Исходы содержательного кейса — их три, и третий закрывает гейт

Решение держателя П18 валидации `GAPS_CLOSE_17` (H28 `DOCS_CHECK_17`,
вариант A). Кейс, проверяющий **условно наблюдаемое** событие (ADL,
ликвидация, фондирование — событие инициирует биржа, и заказать его на demo
нельзя), имеет **три** исхода, а не два:

| Исход | Что означает | Что с гейтом |
|---|---|---|
| **`PASSED`** | событие наблюдалось, факт зафиксирован | гейт закрыт фактом |
| **`FAILED`** | событие наблюдалось, факт противоречит посылке | гейт не закрыт; посылка правится, эскалация владельцу |
| **`OBSERVED_ABSENT`** | кейс **прогнан**, событие за время прогона не наступило | **гейт закрыт** — с явно записанной посылкой-достройкой |

**Правило.** Исход `OBSERVED_ABSENT` **закрывает** гейтящее предусловие
`CODE`, и одновременно **обязывает** записать допущение явно: в реестре
предусловий (`docs/rules/pnl-reconciliation.md` §«Предусловия
`CODE` шага 7») у соответствующего пункта появляется строка «посылка
проверкой **не подтверждена**: событие за прогон не наблюдалось;
допущение — <формулировка>». Молча закрыть гейт этим исходом нельзя.

**Названная цена принята держателем:** посылка уезжает в код помеченной, но
**непроверенной**. Альтернатива — ждать фактического наблюдения — означает,
что `CODE` может ждать **бессрочно** события, которое биржа не обязана
произвести; снятие гейтящего статуса у условно наблюдаемых пунктов
переоткрывало бы сразу три решения держателя.

**Различие с `PENDING` несущее:** `PENDING` — кейс не гонялся, `OBSERVED_ABSENT`
— гонялся и не дал события. Первое гейт не закрывает никогда.

## Инвариант полноты (полный in-perimeter)

Каждый раздел `##` = одна in-perimeter строка манифеста. Покрытие по
группам:

| Группа | Раздел | Эндпоинтов | Манифест-разделы |
|---|---|---|---|
| CORE (write/stateful + read-зависимости) | `M1`–`M21` | 21 | Trade, Algo, Account (balance/positions/config), Market (ticker/candles), Public (instruments) |
| Trade-gaps | `TG1`–`TG9` | 9 | Trade (batch×3, amend, history-archive 3m, cancel-all-after, precheck, rate-limit), Algo (amend-algos) |
| Account-gaps | `AG1`–`AG12` | 12 | Account (positions-history, risk, bills×4, set-pos-mode, set-leverage, leverage-info, max-size×2, trade-fee) |
| Market-gaps | `MG1`–`MG10` | 10 | Market (tickers, books×2, trades×2, index×3, mark-price-candles×2) |
| Public-gaps | `PG1`–`PG8` | 8 | Public (mark-price, price-limit, funding×2, open-interest, position-tiers, server-time, insurance-fund) |
| **Итого** | | **60** | весь in-perimeter ∖ {вне-периметра, сознательно-вне} |

**Колонка покрытия манифеста** (`.claude/processes/api-docs-completion.md`)
подлежит **переразметке**: прежние `⚪ gap` (in-perimeter без метода
клиента) ныне покрыты через `/raw` → `🟡 в плане`; легенда колонки —
под `/raw`-семантику. Переразметка — отдельным проходом владельца колонки
(этот план — её источник). Прежний раздел «client-coverage-gap'ы (не
покрываем)» снят: под `/raw` таких нет.

---


## M1. instruments — GET /api/v5/public/instruments (Public Data)

- **Объект:** OKX `GET /api/v5/public/instruments` (`signed:false`) через
  `POST /api/proxy/okx/raw`. **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим. **Teardown:** не требуется (read).
- Здесь же — единственный на весь CORE кейс «сломанный конверт»
  (M1.6): нераспознаваемый `method`, прокси-слой не доходит до OKX.

### M1.1 прямой — instruments(SWAP, ETH-USDT-SWAP)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/public/instruments, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].minSz`/`tickSz`/`lotSz`/`ctVal` присутствуют | Сырой инструмент (min/tick/lot size, ctVal). Опора `minSz` demo-цепочек | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M1.2 вариант — instruments(SWAP) без instId (список)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/public/instruments, query:{instType:SWAP}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; есть элемент с `instId="ETH-USDT-SWAP"` | Список SWAP-инструментов (instId опционален) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M1.3 негатив — instType вне домена (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/public/instruments, query:{instType:BOGUS, instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.code≠"0"` | Реджект OKX (некорректный `instType`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

### M1.4 негатив — несущ. instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/public/instruments, query:{instType:SWAP, instId:FOO-BAR}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` пустой | Пустой `data` (несущ. инструмент) — валидный исход | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### M1.5 негатив — пропуск обязательного instType (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/public/instruments, query:{instId:ETH-USDT-SWAP}, signed:false}` (без `instType`) | HTTP 200; `b.code≠"0"` | Реджект OKX: обязательный `instType` отсутствует. Под /raw пропуск уходит на OKX (нет прокси-гарда); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instType can not be empty.), data.size=0 |

### M1.6 негатив — сломанный конверт (прокси-слой, единственный на CORE)

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:BOGUS, path:/api/v5/public/instruments, query:{instType:SWAP}, signed:false}` (нераспознаваемый HTTP-метод) | HTTP 4xx/5xx (конверт не диспетчеризуется — сети OKX не достигает) | Прокси-слой /raw: невалидный `method` → ошибка диспетчеризации до OKX. Точный код/форма — наблюдение. Единственный «сломанный конверт» на весь CORE | RUN 2026-06-20 ✓ — http 500 — конверт не диспетчеризован (OKX не достигнут) |

### M1.7 Содержательный (шаг 7, H1 / RQ-2) — `groupId` непуст у наших SWAP-инструментов ⏳ PENDING

**Гейтит резолв ставки комиссии** (`docs/models/mapping/TradeFeeRate.md` §OKX:
ключ резолва — пара (`instType`, `groupId`)). Форм-кейс M1.1 проверяет наличие
sizing-полей; здесь — **наличие и непустоту `groupId`**, на котором стоит весь
резолв: пустой `groupId` → ставку не к чему привязать.

- **Утверждение (проверяется, не предполагается):** `/public/instruments?instType=SWAP`
  отдаёт **непустой `groupId`** для наших инструментов (`BTC-USDT-SWAP`,
  `ETH-USDT-SWAP`).
- **Почему не снято офдоком:** офдок перечисляет Perpetual-futures-группы, но
  **пример в офдоке — SPOT**, а enum-список групп **неполон относительно этого же
  примера** (SPOT `BTC-USDT` → `groupId="1"` при Spot-перечне от `3`); офдок сам
  снимает вопрос ремаркой «actual return values shall prevail»
  (`docs/integrations/okx/contracts/trade-fee.md` §«Перечень групп не хардкодим»).
  Присутствие поля у SWAP офдоком **не подтверждено** — только инспекцией.
- **Проверка:** инспекция поля — `POST /raw {method:GET, path:/api/v5/public/instruments,
  query:{instType:SWAP}, signed:false}`; для элемента с `instId="BTC-USDT-SWAP"`
  (и `ETH-USDT-SWAP`) — `groupId` присутствует и непуст. Form-only, фикстуры не
  требует.
- **Статус:** ⏳ **PENDING — до `CODE` шага 7** (если `groupId` пуст/отсутствует —
  ось резолва ставки не работает, эскалация на `solution-designer`). Провенанс —
  H1 (N9 fee-wiring), `phase-1-step-7-gaps-close-3.md`.

## M2. ticker — GET /api/v5/market/ticker (Market Data)

- **Объект:** OKX `GET /api/v5/market/ticker` (`signed:false`) через /raw.
  **Предусловие:** нет. **Среда:** demo. **Достижимость:** достижим.
  **Teardown:** не требуется. Источник live-цены для write-цепочек.

### M2.1 прямой — ticker(ETH-USDT-SWAP)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].last` присутствует и `>0`; `b.data[0].askPx`/`bidPx`/`ts` присутствуют | Сырой тикер с live last/ask/bid + ts | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M2.2 негатив — несущ. инструмент

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:FOO-BAR}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой ответ, не валидный тикер | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### M2.3 негатив — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/ticker, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX: обязательный `instId` отсутствует; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

## M3. candles — GET /api/v5/market/candles (Market Data)

- **Объект:** OKX `GET /api/v5/market/candles` (`signed:false`) через /raw.
  **Предусловие:** нет. **Среда:** demo. **Достижимость:** достижим.
  **Teardown:** не требуется.
- **Форма ответа:** `b.data` = массив массивов-строк свечи
  `[ts, o, h, l, c, vol, …]` (сырьё). `bar` case-sensitive
  (`rules/timeframe-constants.md`).

### M3.1 прямой — candles(ETH-USDT-SWAP, 1m, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/candles, query:{instId:ETH-USDT-SWAP, bar:1m, limit:10}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0]` — массив длиной ≥ 6 (ts/o/h/l/c/vol); `b.data[0][0]` — числовая строка (ts) | Последние свечи 1m | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M3.2 негатив — bar вне домена (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/candles, query:{instId:ETH-USDT-SWAP, bar:99z}, signed:false}` | HTTP 200; `b.code≠"0"` | Реджект OKX (некорректный `bar`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter bar error), data.size=0 |

### M3.3 негатив — несущ. instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/candles, query:{instId:FOO-BAR, bar:1m}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой — несущ. инструмент | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### M3.4 негатив — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/candles, query:{bar:1m}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX: обязательный `instId` отсутствует; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

## M4. history-candles — GET /api/v5/market/history-candles (Market Data)

- **Объект:** OKX `GET /api/v5/market/history-candles` (`signed:false`)
  через /raw. **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** достижим. **Teardown:** не требуется.

### M4.1 прямой — history-candles(ETH-USDT-SWAP, 1m, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/history-candles, query:{instId:ETH-USDT-SWAP, bar:1m, limit:10}, signed:false}` (after опущен) | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0]` — массив (свеча); `b.data` упорядочен по ts убыв. | Исторические свечи 1m (пагинация назад) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M4.2 вариант — пагинация назад по after

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/history-candles, query:{instId:ETH-USDT-SWAP, bar:1m, after:<ts из M4.1 data[last][0]>, limit:10}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — свечи строго старше `after` | Следующая страница назад | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=10 |

### M4.3 негатив — фильтр из будущего (вне окна)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/history-candles, query:{instId:ETH-USDT-SWAP, bar:1m, after:99999999999999}, signed:false}` | HTTP 200; `b.data` пустой **или** наблюдение | Якорь из будущего — пустой/поведение фиксируем | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=100 |

### M4.4 негатив — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/market/history-candles, query:{bar:1m}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX: обязательный `instId` отсутствует; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

## M5. balance — GET /api/v5/account/balance (Account)

- **Объект:** OKX `GET /api/v5/account/balance` (`signed:true`) через /raw.
  **Предусловие:** нет. **Среда:** demo. **Достижимость:** достижим.
  **Teardown:** не требуется. `ccy` опционален (под /raw all-ccy достижим —
  не gap).

### M5.1 прямой — balance(USDT)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/balance, query:{ccy:USDT}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].totalEq` присутствует; `b.data[0].details` — массив, несёт USDT | Account-level баланс, details по USDT | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M5.2 вариант — balance без ccy (все валюты)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/balance, signed:true}` (без `ccy`) | HTTP 200; `b.code="0"`; `b.data[0].details` — массив (все валюты) | All-ccy баланс. Под /raw `ccy` опционален — реальный вариант (прежний gap снят) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M5.3 негатив — несущ. валюта (наблюдение)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/balance, query:{ccy:ZZZ}, signed:true}` | HTTP 200; `b.code="0"` с пустым details **или** `b.code≠"0"` (наблюдение) | Поведение OKX на несущ. ccy — фиксируем фактом, не выдумываем | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |

## M6. account-config — GET /api/v5/account/config (Account)

- **Объект:** OKX `GET /api/v5/account/config` (`signed:true`) через /raw.
  Без параметров. **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** достижим. **Teardown:** не требуется. Негативы только
  auth (см. I-cred — пишет сборщик).

### M6.1 прямой — account-config()

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/config, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].acctLv` присутствует; `b.data[0].posMode` присутствует | Конфигурация счёта (acctLv/posMode); `posMode` ожидается `net_mode` (посылка адаптера) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

## M7. order details — GET /api/v5/trade/order (Order details)

- **Объект:** OKX `GET /api/v5/trade/order` (`signed:true`) через /raw.
  **Предусловие:** прямой — живой/исполненный ордер (цепочки Climit/Cmarket
  в M16). **Среда:** demo. **Teardown:** в цепочке.

### M7.1 негатив (no-state) — фейковый ordId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/order, query:{instId:ETH-USDT-SWAP, ordId:9999999999999999}, signed:true}` | HTTP 200; `b.data` пустой **или** `b.code` несёт 51603 (наблюдение) | Резолюция «не найден». Связь: 51603-on-not-found — предпосылка D-B3 (recovery-by-clientId) | RUN 2026-06-20 ✓ — http 200, b.code=51603 (Order does not exist), data.size=0 |

### M7.2 негатив (no-state) — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/order, query:{ordId:9999999999999999}, signed:true}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX: обязательный `instId` отсутствует; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### M7.3 прямой по ordId / M7.4 прямой по clOrdId

Покрыты цепочкой **Climit** (M16): Climit.get `getOrder(ordId)` live,
Climit.canceled `getOrder(ordId)` canceled, Climit.getByClId
`getOrder(clOrdId)` (вариант резолва по clOrdId). См. M16 §Climit.

## M8. orders-pending — GET /api/v5/trade/orders-pending (Pending orders)

- **Объект:** OKX `GET /api/v5/trade/orders-pending` (`signed:true`) через
  /raw. **Предусловие:** прямой богатый — живой ордер (Climit).
  **Среда:** demo. **Teardown:** в цепочке.

### M8.1 прямой (no-state) — пустой/валидный

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` (без живых ордеров) | HTTP 200; `b.code="0"`; `b.data` — массив (пустой валиден) | Список pending (пуст, если ничего не висит) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M8.2 негатив — несущ. instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:FOO-BAR}, signed:true}` | HTTP 200; `b.data` пустой **или** `b.code≠"0"` (наблюдение) | Пустой/реджект на несущ. инструмент | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### M8.3 прямой богатый

Покрыт **Climit** (M16): шаг Climit.pending между place и cancel —
`b.data` содержит живой ордер с `clOrdId`.

## M9. orders-history 7d — GET /api/v5/trade/orders-history (Order history 7d)

- **Объект:** OKX `GET /api/v5/trade/orders-history` (`signed:true`) через
  /raw. `instType` обязателен в history. **Предусловие:** прямой богатый —
  ордер в истории 7д. **Среда:** demo. **Teardown:** в цепочке.

### M9.1 прямой (no-state) — пустой/валидный

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-history, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив (пустой валиден на свежем demo) | История 7д (пуста/наполнена) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M9.2 негатив — пропуск обязательного instType (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-history, query:{instId:ETH-USDT-SWAP}, signed:true}` (без `instType`) | HTTP 200; `b.code≠"0"` | Реджект OKX: `instType` обязателен в history; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instType can not be empty.), data.size=0 |

### M9.3 прямой богатый

Покрыт **Cmarket** (M16, filled-ордер в истории) и **Climit** (M16,
canceled-ордер ~2ч). Шаги Cmarket.history / Climit.history.

## M10. fills 3d — GET /api/v5/trade/fills (Fills 3d)

- **Объект:** OKX `GET /api/v5/trade/fills` (`signed:true`) через /raw.
  **Предусловие:** прямой богатый — недавний fill (Cmarket).
  **Среда:** demo. **Достижимость:** **через исполнение** (Cmarket).
  **Teardown:** в цепочке.

### M10.1 прямой (no-state) — пустой/валидный до исполнения

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/fills, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` (до Cmarket) | HTTP 200; `b.code="0"`; `b.data` — массив (пустой валиден) | Пустой/наполненный список исполнений | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M10.2 негатив — фильтр из будущего / вне окна

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/fills, query:{instType:SWAP, instId:ETH-USDT-SWAP, begin:99999999999999}, signed:true}` | HTTP 200; `b.data` пустой | Пустой ответ на фильтр из будущего | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=0 |

### M10.3 негатив — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/fills, query:{instType:SWAP}, signed:true}` (без `instId`) | HTTP 200; `b.code="0"`; `b.data` массив (по всем инструментам SWAP) **или** `b.code≠"0"` (наблюдение) | `instId` опционален в OKX fills — наблюдаем поведение (пропуск не обязательно реджект); точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=8 |

### M10.4 прямой богатый (через исполнение)

Покрыт **Cmarket** (M16): шаг Cmarket.fills после исполнения market —
`b.data` содержит fill (`fillPx`/`fillSz`/`ordId`). Если demo не наполнил
fill — отказ по факту (находка), ожидание не выдумывается.

## M11. fills-history 3m — GET /api/v5/trade/fills-history (Fills 3m)

- **Объект:** OKX `GET /api/v5/trade/fills-history` (`signed:true`) через
  /raw. **Предусловие:** прямой богатый — fill в окне 3м (Cmarket).
  **Среда:** demo. **Teardown:** в цепочке.

### M11.1 прямой (no-state) — пустой/валидный

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/fills-history, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив (пустой валиден) | Исполнения за 3м (пусто/наполнено) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M11.2 негатив — фильтр из будущего

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/fills-history, query:{instType:SWAP, instId:ETH-USDT-SWAP, begin:99999999999999}, signed:true}` | HTTP 200; `b.data` пустой | Пустой ответ на фильтр из будущего | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=0 |

### M11.3 негатив — пропуск обязательного instType (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/fills-history, query:{instId:ETH-USDT-SWAP}, signed:true}` (без `instType`) | HTTP 200; `b.code≠"0"` **или** `b.data` (наблюдение) | `instType` обязателен в fills-history (офдок) → реджект; точный код/исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instType can not be empty.), data.size=0 |

### M11.4 прямой богатый

Покрыт **Cmarket** (M16): fill виден и в `fills-history` (окно 3м). Шаг
Cmarket.fillsHistory.

## M12. positions — GET /api/v5/account/positions (Get positions)

- **Объект:** OKX `GET /api/v5/account/positions` (`signed:true`) через
  /raw. **Предусловие:** прямой богатый — открытая позиция (Cmarket).
  **Среда:** demo. **Достижимость:** **через исполнение** (Cmarket).
  **Teardown:** в цепочке.

### M12.1 прямой (no-state) — пустой/валидный (нет позиции)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/positions, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` (нет позиции) | HTTP 200; `b.code="0"`; `b.data` пустой **или** `b.data[0].posId` присутствует | Пустая/нет позиции — валидный исход | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M12.2 негатив — несущ. instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/positions, query:{instType:SWAP, instId:FOO-BAR}, signed:true}` | HTTP 200; `b.data` пустой **или** `b.code≠"0"` | Пустой/реджект | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### M12.3 прямой богатый (через исполнение)

Покрыт **Cmarket** (M16): шаги Cmarket.position (позиция открыта,
`b.data[0].posId`/`pos`/`avgPx`) и Cmarket.positionFlat (после close —
`b.data` пуст / `pos=0`).

## M13. algo details — GET /api/v5/trade/order-algo (Algo details)

- **Объект:** OKX `GET /api/v5/trade/order-algo` (`signed:true`) через
  /raw. **Предусловие:** прямой богатый — живой/отменённый algo (M19).
  **Среда:** demo. **Teardown:** в цепочке.

### M13.1 негатив (no-state) — фейковый algoId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:9999999999999999}, signed:true}` | HTTP 200; `b.data` пустой **или** `b.code≠"0"` (наблюдение) | Резолюция «не найден» (ожидается 0 элементов) | RUN 2026-06-20 ✓ — http 200, b.code=51603 (Order does not exist), data.size=0 |

### M13.2 негатив (no-state) — ни algoId, ни algoClOrdId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP}, signed:true}` (без `algoId`/`algoClOrdId`) | HTTP 200; `b.code≠"0"` | Реджект OKX: нужно одно из `algoId`/`algoClOrdId`; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50015 (Either parameter algoId or algoClOrdId is required), data.size=0 |

### M13.3 прямой по algoId / M13.4 по algoClOrdId

Покрыты цепочками M19: getAlgo(algoId) live + canceled; вариант резолва
по `algoClOrdId` — шаг M19cond.getByClId.

## M14. algo-pending — GET /api/v5/trade/orders-algo-pending (Algo pending)

- **Объект:** OKX `GET /api/v5/trade/orders-algo-pending` (`signed:true`)
  через /raw. **Предусловие:** прямой богатый — живой algo (M19).
  **Среда:** demo. **Teardown:** в цепочке. **Вариант — `ordType`**
  (conditional/oco/move_order_stop).

### M14.1 прямой (no-state) — ordType=conditional, пустой/валидный

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив (пустой валиден) | Pending algo семьи conditional | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M14.2 вариант — ordType=oco

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:oco}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив | Pending algo семьи oco | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M14.3 вариант — ordType=move_order_stop (advance)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив | Pending algo семьи advance (trailing) видна в query | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M14.4 негатив — ordType вне домена (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:BOGUS}, signed:true}` | HTTP 200; `b.code≠"0"` | Реджект OKX (некорректный `ordType`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter ordType error), data.size=0 |

### M14.5 негатив — пропуск обязательного ordType (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` (без `ordType`) | HTTP 200; `b.code≠"0"` | Реджект OKX: `ordType` обязателен; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter ordType error), data.size=0 |

### M14.6 прямой богатый

Покрыт M19: шаг M19cond.pending (`ordType=conditional`) и M19tr.pending
(`ordType=move_order_stop`) между place и cancel — `b.data` содержит
живой algo.

## M15. algo-history — GET /api/v5/trade/orders-algo-history (Algo history 3m)

- **Объект:** OKX `GET /api/v5/trade/orders-algo-history` (`signed:true`)
  через /raw. **Предусловие:** прямой богатый — отменённый/сработавший
  algo в окне 3м (M19). **Среда:** demo. **Teardown:** в цепочке.
  **Вариант — `ordType`** (обязателен в OKX history).

### M15.1 прямой (no-state) — ordType=conditional, пустой/валидный

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-history, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив; элементы (если есть) несут `state` ∈ effective/canceled/order_failed | История algo семьи conditional за 3м | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M15.2 вариант — ordType=oco

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-history, query:{instId:ETH-USDT-SWAP, ordType:oco}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` массив | История семьи oco | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M15.3 вариант — ordType=move_order_stop

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-history, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` массив | История семьи advance (trailing) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M15.4 негатив — ordType вне домена (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-history, query:{instId:ETH-USDT-SWAP, ordType:BOGUS}, signed:true}` | HTTP 200; `b.code≠"0"` | Реджект OKX (некорректный `ordType`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50015 (Either parameter state or algoId is required), data.size=0 |

### M15.5 негатив — пропуск обязательного ordType (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/trade/orders-algo-history, query:{instId:ETH-USDT-SWAP}, signed:true}` (без `ordType`) | HTTP 200; `b.code≠"0"` | Реджект OKX: `ordType` обязателен в history; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter ordType error), data.size=0 |

### M15.6 прямой богатый

Покрыт M19trailing/M19cond: после cancel algo попадает в history со
`state=canceled` (`ordType=move_order_stop`/`conditional`). Шаг
M19*.history.

### M15.7 Содержательный (шаг 7, H21) — что означает `actualPx` сработавшего algo ⏳ PENDING

**Гейтит исполнимость операнда калибровки проскока.** Решение H21
`DOCS_CHECK_11` назначило операндом `AlgoOrder.externalPrice`
(`actualPx`) по стоповым типам условия, а вторым операндом — уровень
стопа с той же строки (`condition.trigger.stopLoss.value`). Разность
измеряет проскок **только если** `actualPx` — цена **фактического
исполнения**. Если это цена **выставления** ордера после триггера
(то есть по сути тот же триггерный уровень), разность систематически ≈ 0
и операнд бесполезен.

- **Что верифицировать:** для **сработавшего** algo-ордера (не
  отменённого) сравнить `actualPx` с `triggerPx` и с фактической ценой
  исполнения связанного ordinary-ордера (`ordId`/`ordIdList` → `M7 order
  details` → `avgPx`). Совпадает с `avgPx` ⇒ цена исполнения; совпадает
  с `triggerPx` ⇒ цена выставления.
- **Фикстура:** SL-algo на живой позиции, доведённый до срабатывания
  (либо триггер, поставленный вплотную к рынку). Смежно снимается
  семантика `actualSz` (`externalSize`) и `triggerTime`.
- **Действие при «цена выставления»:** операнд калибровки меняется —
  берётся `avgPx` связанного ordinary-ордера, а не `actualPx`;
  эскалация на `trading-specialist` + `solution-designer`, правка
  `docs/models/domain/core/Position.md` §«Цена фактического выхода» и
  `docs/rules/risk-policy.md` §«Без поправки на проскок».
- **Статус:** ⏳ **PENDING**. Провенанс — H21 `DOCS_CHECK_11` (новый
  хвост, вскрыт при опровержении посылки «полей фактического исполнения
  у алго-сущности нет»).

## M16. placeOrder — POST /api/v5/trade/order (Place order)

- **Объект:** OKX `POST /api/v5/trade/order` (`signed:true`) через /raw.
  Тело строится **руками** по контракту (`contracts/order.md`):
  `{instId, tdMode:"isolated", side, ordType:"limit"|"market", sz, px?,
  clOrdId, reduceOnly}`; `tdMode`/`posSide` — adapter-константы
  (`rules/adapter-constants.md`). **Варианты `ordType`:** `limit`
  (px задан → цепочка **Climit**, неисполнимый) и `market` (px опущен →
  цепочка **Cmarket**, исполняемый → fill → позиция). **Среда:** demo.
  **WRITE.** **Teardown:** в каждой цепочке (wait-until-condition, не sleep).

### M16.limit — вариант limit: цепочка жизненного цикла (Climit)

Граф: ticker → place(limit) → getOrder(live) → getOrder(clOrdId) →
orders-pending → cancel → getOrder(canceled) → orders-history. Цена
неисполнима (≈ `floor(last·0.5)`, кратно tickSz). **Достижимость:**
достижим. **Teardown:** cancel-страховка (поллинг getOrder до canceled,
таймаут N, иначе наблюдение).

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **Climit.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых ордеров по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых ордеров по инструменту до цепочки | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live `last` → `cl_px=floor(last·0.5)` (неисполнимая limit buy), кратно tickSz. Цена с биржи, не константа | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.place.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:buy, ordType:limit, sz:0.01, px:cl_px, clOrdId:cl_clOrdId, reduceOnly:false}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].ordId` непустой | `ordId` → `cl_ordId`. `sCode=0 ≠ live` (см. .get). `tdMode/posSide` адаптер-константы (posSide=net проставляет реальный адаптер; здесь тело руками по контракту) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.get.** `POST /raw {method:GET, path:/api/v5/trade/order, query:{instId:ETH-USDT-SWAP, ordId:cl_ordId}, signed:true}` | HTTP 200; `b.data[0].ordId=cl_ordId`; `b.data[0].state="live"`; `side`/`sz`/`px` присутствуют | ACK стал live-ордером — ACK ≠ runtime truth (покрывает M7.3) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.getByClId.** `POST /raw {method:GET, path:/api/v5/trade/order, query:{instId:ETH-USDT-SWAP, clOrdId:cl_clOrdId}, signed:true}` | HTTP 200; `b.data[0].ordId=cl_ordId` | Резолв по `clOrdId` (вариант M7.4): тот же ордер | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.pending.** `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` содержит элемент с `ordId=cl_ordId`, `state=live` | Живой ордер в pending (покрывает M8.3) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.cancel.** `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, ordId:cl_ordId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"` | ACK отмены, не финал — подтверждается .canceled (покрывает M17 прямой) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.canceled.** `POST /raw {method:GET, path:/api/v5/trade/order, query:{instId:ETH-USDT-SWAP, ordId:cl_ordId}, signed:true}` | HTTP 200; `b.data[0].state="canceled"` | Финал cancel (RUN: поллинг до condition state=canceled, таймаут N) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.history.** `POST /raw {method:GET, path:/api/v5/trade/orders-history, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` содержит `ordId=cl_ordId` (state canceled, окно ~2ч) **или** наблюдение | Отменённый в истории 7д ~2ч (покрывает M9.3); задержку индексации фиксируем фактом | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown Climit.** `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, ordId:cl_ordId}, signed:true}` (идемпотентная страховка) | HTTP 200; (sCode 0 или already-canceled/not-exist) | После цепочки биржа чистая (ни одного живого ордера). Цена далеко от рынка → исполнение исключено | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Climit.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `cl_ordId` — вернулось к чистому == старт | **Verify.end:** живых ордеров по инструменту нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep). Расхождение → фейл (инвариант) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M16.market — вариант market: цепочка исполнения (Cmarket)

Граф: ticker → place(market) → getOrder(filled) → positions(есть) →
fills → fills-history → orders-history(filled) → close-position →
positions(flat). **Исполняемый тип** — наполняет fill/позицию (гейт
достижимости). **Достижимость:** достижим (исполнением). **Teardown:**
close-position-страховка (поллинг positions до flat, таймаут N).

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **Cmarket.snapshot.** `POST /raw {method:GET, path:/api/v5/account/positions, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` пуст **или** `b.data[0].pos=0` (чистый старт — нет позиции) | **Snapshot.start:** чистый старт — нет открытой позиции по инструменту до цепочки | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live last (диагностика; market цены не требует) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.place.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:buy, ordType:market, sz:0.01, clOrdId:mk_clOrdId, reduceOnly:false}, signed:true}` (px опущен → market) | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].ordId` непустой | `ordId` → `mk_ordId`. `ordType=market` (px отсутствует). **Минимальный риск:** min sz=0.01 | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.get.** `POST /raw {method:GET, path:/api/v5/trade/order, query:{instId:ETH-USDT-SWAP, ordId:mk_ordId}, signed:true}` | HTTP 200; `b.data[0].ordId=mk_ordId`; `b.data[0].state` ∈ filled/partially_filled; `accFillSz`/`avgPx` присутствуют | Market исполнен — ACK стал фактом. RUN: поллинг до state=filled, таймаут N | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.position.** `POST /raw {method:GET, path:/api/v5/account/positions, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].posId` присутствует; `b.data[0].pos` ≠ 0; `avgPx` присутствует | Открытая позиция (покрывает M12.3). Если demo не открыл — наблюдение/находка | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.fills.** `POST /raw {method:GET, path:/api/v5/trade/fills, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` содержит элемент с `ordId=mk_ordId`; `fillPx`/`fillSz` присутствуют | Fill виден (покрывает M10.4). Если пусто — отказ по факту, ожидание не выдумывается | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.fillsHistory.** `POST /raw {method:GET, path:/api/v5/trade/fills-history, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` содержит `ordId=mk_ordId` (окно 3м) **или** наблюдение | Fill в окне 3м (покрывает M11.4) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.history.** `POST /raw {method:GET, path:/api/v5/trade/orders-history, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` содержит `ordId=mk_ordId` (state filled) **или** наблюдение | Filled-ордер в истории 7д (покрывает M9.3 богатый) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.close.** `POST /raw {method:POST, path:/api/v5/trade/close-position, body:{instId:ETH-USDT-SWAP, mgnMode:isolated, posSide:net, autoCxl:true, ccy:USDT}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"` **или** `b.data[0].instId` присутствует | ACK закрытия (market, autoCxl). close-position ACK без `ordId` (контракт). Покрывает M18 прямой | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.positionFlat.** `POST /raw {method:GET, path:/api/v5/account/positions, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` пуст **или** `b.data[0].pos=0` | Позиция закрыта (покрывает M12.3 flat). RUN: поллинг до flat, таймаут N | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown Cmarket.** `POST /raw {method:POST, path:/api/v5/trade/close-position, body:{instId:ETH-USDT-SWAP, mgnMode:isolated, posSide:net, autoCxl:true, ccy:USDT}, signed:true}` (идемпотентная страховка) | HTTP 200; (sCode 0 или «нет позиции») | После цепочки ни позиции, ни висящих ордеров (autoCxl снял) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Cmarket.verify.** `POST /raw {method:GET, path:/api/v5/account/positions, query:{instType:SWAP, instId:ETH-USDT-SWAP}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` пуст **или** `b.data[0].pos=0` — вернулось к чистому == старт | **Verify.end:** позиции по инструменту нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep). Вне охвата: комиссионный/PnL-остаток баланса после fill (наблюдается, кейс не фейлит). Расхождение `pos≠0` → фейл (инвариант) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M16.neg — негатив placeOrder

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M16.neg.size.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:buy, ordType:limit, sz:-1, px:cl_px, clOrdId:…}, signed:true}` (значение вне домена) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект отрицательного размера; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51000 (All operations failed), data.size=1 |
| **M16.neg.side.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:BOGUS, ordType:limit, sz:0.01, px:cl_px, clOrdId:…}, signed:true}` (значение вне домена) | HTTP 200; `b.data[0].sCode≠"0"` **или** `b.code≠"0"` | Реджект некорректного `side` | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51000 (All operations failed), data.size=1 |
| **M16.neg.ordType.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:buy, ordType:BOGUS, sz:0.01, px:cl_px, clOrdId:…}, signed:true}` (битый сырой `ordType` — реальный под /raw) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект некорректного `ordType`. Прежний вариант-gap снят: сырой `ordType` достижим телом конверта; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51000 (All operations failed), data.size=1 |
| **M16.neg.reqParam.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:buy, ordType:limit, px:cl_px, clOrdId:…}, signed:true}` (без `sz` — OKX-слой) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект OKX: `sz` обязателен. Под /raw пропуск уходит на OKX (нет прокси-гарда); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51000 (All operations failed), data.size=1 |

### M16.neg.dupClId — негатив-цепочка (дубль clOrdId, stateful)

Граф: Snapshot.start(pending пуст) → price(ticker → cl_px) → place#1(clOrdId) → place#2(тот же clOrdId, реджект дубля) → teardown(отменить #1 dup_ordId) → teardown #2(защитно dup_ordId2) → Verify.end(pending == старт). Свой price-шаг делает цепочку самодостаточной (cl_px). Первый place создаёт живой ордер → инвариант восстановления применим.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M16.dup.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых ордеров по инструменту | **Snapshot.start:** чистый старт — нет живых ордеров | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M16.dup.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live `last` → `cl_px=floor(last·0.5)` (неисполнимая limit buy), кратно tickSz. Делает dup-цепочку самодостаточной (свой price-шаг ставит `cl_px`, не из M16.limit) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M16.dup.place1.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:buy, ordType:limit, sz:0.01, px:cl_px, clOrdId:dup_clOrdId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].ordId` непустой | Первый place принят (`ordId` → `dup_ordId`). Неисполнимая цена | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M16.dup.place2.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{… clOrdId:dup_clOrdId}, signed:true}` (тот же `clOrdId`) | HTTP 200; `b.data[0].sCode≠"0"` (дубль clOrdId) | Реджект дубля clientId; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51016 (All operations failed), data.size=1 |
| **Teardown M16.dup.** `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, ordId:dup_ordId}, signed:true}` | HTTP 200; (sCode 0 или already-canceled/not-exist) | Отменить прошедший #1 (`dup_ordId`) — биржа чистая | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown M16.dup #2.** `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, ordId:dup_ordId2}, signed:true}` (защитно, если #2 неожиданно прошёл; `dup_ordId2` по умолчанию пуст → not-exist, идемпотентно) | HTTP 200; (sCode 0 или not-exist) | Снять защитно-захваченный #2 (`dup_ordId2`) — закрыть утечку состояния на пути «неожиданный успех дубля» | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M16.dup.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `dup_ordId` — вернулось к чистому == старт | **Verify.end:** живых ордеров нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

## M17. cancelOrder — POST /api/v5/trade/cancel-order (Cancel order)

- **Объект:** OKX `POST /api/v5/trade/cancel-order` (`signed:true`) через
  /raw. Тело `{instId, ordId | clOrdId}` (оба → биржа берёт `ordId`).
  **Среда:** demo. **WRITE.** Прямой — цепочка Climit (Climit.cancel).
  **Вариант — by `ordId` / by `clOrdId`.** **Teardown:** в цепочке.

### M17.1 негатив (no-state) — cancel несуществующего

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, ordId:9999999999999999}, signed:true}` | HTTP 200; `b.data[0].sCode≠"0"`; `b.data[0]` несёт `sCode`+`sMsg` | Реджект; код 51603 (order does not exist) — наблюдение, не выдумка (как M7.1/TG2.2). Под /raw верхнеуровневая обёртка — `OkxApiResponse`, реджект в per-element `sCode` | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51400 (All operations failed), data.size=1 |

### M17.2 негатив — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{ordId:9999999999999999}, signed:true}` (без `instId`) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект OKX: `instId` обязателен; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=50014 (All operations failed), data.size=1 |

### M17.3 негатив — отмена отменённого (состояние-конфликт)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| Повтор `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, ordId:cl_ordId}, signed:true}` после Climit.cancel | HTTP 200; `b.data[0].sCode≠"0"` (already canceled / not exist) | Реджект повторной отмены; код — наблюдение (51603/иной) | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51400 (All operations failed), data.size=1 |

### M17.4 прямой + вариант clOrdId — cancel by clOrdId (цепочка)

Прямой by `ordId` покрыт Climit (Climit.cancel). Вариант by `clOrdId` —
**самостоятельная мини-цепочка** (компактная, не зависит от Climit):
Snapshot.start → ticker → place(неисполнимый limit, `clOrdId=m17_clId`) →
cancel `{instId, clOrdId:m17_clId}` (без `ordId`) → verify canceled →
teardown (идемпотентный cancel) → Verify.end. **WRITE.** **Среда:** demo.
**Teardown:** в цепочке.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M17.4.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых ордеров по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых ордеров по инструменту | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M17.4.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live last → `m17_px=floor(last·0.5)` (неисполнимая limit buy), кратно tickSz | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M17.4.place.** `POST /raw {method:POST, path:/api/v5/trade/order, body:{instId:ETH-USDT-SWAP, tdMode:isolated, side:buy, ordType:limit, sz:0.01, px:m17_px, clOrdId:m17_clId, reduceOnly:false}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].ordId` непустой | Неисполнимый limit с `clOrdId=m17_clId`. `ordId` → `m17_ordId` (страховка teardown) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M17.4.cancel.** `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, clOrdId:m17_clId}, signed:true}` (без `ordId`) | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"` | Отмена by `clOrdId` (вариант M17.4). ACK отмены, финал — .canceled | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M17.4.canceled.** `POST /raw {method:GET, path:/api/v5/trade/order, query:{instId:ETH-USDT-SWAP, clOrdId:m17_clId}, signed:true}` | HTTP 200; `b.data[0].state="canceled"` | Финал cancel by `clOrdId` (RUN: одношаговый ассерт best-effort, ретрай при индексинг-задержке) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown M17.4.** `POST /raw {method:POST, path:/api/v5/trade/cancel-order, body:{instId:ETH-USDT-SWAP, clOrdId:m17_clId}, signed:true}` (идемпотентная страховка) | HTTP 200; (sCode 0 или already-canceled/not-exist) | Биржа чистая | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M17.4.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-pending, query:{instId:ETH-USDT-SWAP}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `m17_ordId` — вернулось к чистому == старт | **Verify.end:** живых ордеров по инструменту нет (== Snapshot.start) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

## M18. closePosition — POST /api/v5/trade/close-position (Close position)

- **Объект:** OKX `POST /api/v5/trade/close-position` (`signed:true`)
  через /raw. Тело `{instId, mgnMode:"isolated", posSide:"net",
  autoCxl:true, ccy:"USDT"}` (adapter-константы). **Среда:** demo.
  **WRITE.** Прямой — цепочка Cmarket (Cmarket.close). **Teardown:** в
  цепочке.

### M18.1 негатив (no-state) — close без позиции (состояние-конфликт)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/trade/close-position, body:{instId:ETH-USDT-SWAP, mgnMode:isolated, posSide:net, autoCxl:true, ccy:USDT}, signed:true}` без открытой позиции | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` (наблюдение) | Реджект close несущ. позиции; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51023 (Position doesn't exist.), data.size=0 |

### M18.2 негатив — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/trade/close-position, body:{mgnMode:isolated, posSide:net, autoCxl:true, ccy:USDT}, signed:true}` (без `instId`) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект OKX: `instId` обязателен; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### M18.3 прямой

Покрыт Cmarket (Cmarket.close): закрытие реальной позиции, `sCode=0`,
подтверждение Cmarket.positionFlat.

## M19. placeAlgoOrder — POST /api/v5/trade/order-algo (Place algo order)

- **Объект:** OKX `POST /api/v5/trade/order-algo` (`signed:true`) через
  /raw. Тело строится **руками** по `contracts/algo-order.md`: общие поля
  `{instId, tdMode:"isolated", posSide:"net", side, ordType, sz,
  reduceOnly, algoClOrdId}` + ordType-specific. **Варианты `ordType`:**
  `conditional` (SL/TP, `slTriggerPx`/`slOrdPx:"-1"` либо
  `tpTriggerPx`/`tpOrdPx:"-1"`), `oco` (обе ноги), `move_order_stop`
  (trailing: `callbackRatio` ИЛИ `callbackSpread`). **Среда:** demo.
  **WRITE.** **Teardown:** в каждой цепочке (wait-until-condition).
- **Предусловие reduce-only:** protective algo (`reduceOnly=true`) может
  требовать позиции. Если demo реджектит «нет позиции» — открыть min
  market-позицию (A0, как Cmarket.place), повторить, закрыть в teardown.
  Помечается в каждом варианте.

### M19.cond-sl — вариант conditional (STOP_LOSS)

Граф: ticker → place(conditional, SL) → getAlgo(live) →
getAlgo(algoClOrdId) → getPendingAlgo(conditional) → cancelAlgos(ordinary)
→ getAlgo(canceled) → getAlgoHistory.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19cond.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых algo по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых conditional-algo по инструменту до цепочки | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live last → `sl_px=floor(last·0.5)` (далёкий SL-триггер) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.place.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:conditional, sz:0.01, reduceOnly:true, algoClOrdId:cond_clId, slTriggerPx:sl_px, slTriggerPxType:mark, slOrdPx:"-1"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId` непустой | `ordType=conditional`, `slOrdPx=-1` (market после trigger). `algoId` → `cond_algoId`. При реджекте «нет позиции» — A0 + повтор | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.get.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:cond_algoId}, signed:true}` | HTTP 200; `b.data[0].algoId=cond_algoId`; `b.data[0].state≠"canceled"` | Algo live/effective (покрывает M13.3) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.getByClId.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoClOrdId:cond_clId}, signed:true}` | HTTP 200; `b.data[0].algoId=cond_algoId` | Резолв по `algoClOrdId` (вариант M13.4) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.pending.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` | HTTP 200; `b.data` содержит `algoId=cond_algoId` | Живой conditional в pending (покрывает M14.6 conditional) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.cancel.** `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:cond_algoId}], signed:true}` (**ordinary** семья) | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"` | Ветвь ordinary `cancel-algos` (покрывает M20 прямой). Тело — массив. ACK отмены | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.canceled.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:cond_algoId}, signed:true}` | HTTP 200; `b.data[0].state="canceled"` **или** `b.data` пуст (наблюдение) | Финал cancel (RUN: поллинг до canceled/пусто) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.history.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-history, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` | HTTP 200; `b.data` содержит `algoId=cond_algoId` (state canceled) **или** наблюдение | Отменённый в history 3м (покрывает M15.6) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown M19cond.** `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:cond_algoId}], signed:true}` + (если A0) close-position | HTTP 200 | Algo снят, позиция (если открывалась) закрыта — биржа чистая | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19cond.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `cond_algoId` — вернулось к чистому == старт | **Verify.end:** живых conditional-algo по инструменту нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep). Если открывалась A0 — позиция тоже flat в teardown. Расхождение → фейл (инвариант) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M19.cond-tp — вариант conditional (TAKE_PROFIT)

Цепочка самодостаточна: собственный price-шаг (не зависит от M19.cond-sl).

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19tp.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых conditional-algo по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых conditional-algo по инструменту | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tp.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live last → `tp_px=floor(last·2)` (далёкий TP-триггер), кратно tickSz. Делает цепочку самодостаточной (свой price-шаг, не из M19.cond-sl) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tp.place.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:conditional, sz:0.01, reduceOnly:true, algoClOrdId:tp_clId, tpTriggerPx:tp_px, tpTriggerPxType:mark, tpOrdPx:"-1"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId` непустой | `ordType=conditional`, `tpOrdPx=-1`. `tp_px` из своего M19tp.price. `algoId` → `tp_algoId` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tp.get.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:tp_algoId}, signed:true}` | HTTP 200; `b.data[0].state≠"canceled"` | Live | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tp.cancel.** `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:tp_algoId}], signed:true}` (ordinary) | HTTP 200; `b.data[0].sCode="0"` | Cancel ordinary | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown M19tp.** `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:tp_algoId}], signed:true}` (+ A0 close) | HTTP 200 | Чисто | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tp.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `tp_algoId` — вернулось к чистому == старт | **Verify.end:** живых conditional-algo нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep); A0-позиция (если открывалась) flat | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M19.oco — вариант oco (OCO_FULL)

Цепочка самодостаточна: собственный price-шаг (не зависит от M19.cond-sl).

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19oco.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:oco}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых oco-algo по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых oco-algo по инструменту | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19oco.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live last → `sl_px=floor(last·0.5)`, `tp_px=floor(last·2)`, кратно tickSz. Делает цепочку самодостаточной (свой price-шаг ставит обе цены, не из M19.cond-sl) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19oco.place.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:oco, sz:0.01, reduceOnly:true, algoClOrdId:oco_clId, slTriggerPx:sl_px, slTriggerPxType:mark, slOrdPx:"-1", tpTriggerPx:tp_px, tpTriggerPxType:mark, tpOrdPx:"-1"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId` непустой | `ordType=oco`, обе ноги (slOrdPx/tpOrdPx=-1). `sl_px`/`tp_px` из своего M19oco.price. `algoId` → `oco_algoId` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19oco.get.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:oco_algoId}, signed:true}` | HTTP 200; `b.data[0].state≠"canceled"` | Live oco | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19oco.cancel.** `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:oco_algoId}], signed:true}` (ordinary) | HTTP 200; `b.data[0].sCode="0"` | Cancel ordinary | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown M19oco.** `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:oco_algoId}], signed:true}` (+ A0 close) | HTTP 200 | Чисто | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19oco.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:oco}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `oco_algoId` — вернулось к чистому == старт | **Verify.end:** живых oco-algo нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep); A0-позиция (если открывалась) flat | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M19.trailing — вариант move_order_stop (callbackRatio, ядро И-2)

Граф: ticker → place(trailing) → getAlgo(live) →
getPendingAlgo(move_order_stop) → cancelAdvanceAlgos(**advance** семья) →
getAlgo(canceled) → getAlgoHistory. Снятие конфликта офдока рантаймом:
`cancel-advance-algos` выведен из офдока (changelog 2025-04-24), но
`OkxRestClient.cancelAdvanceAlgos` существует, ветвь cancel идёт по семье
advance (И-1(а), `algo-order.md`). **Вердикт cancel частично неизвестен**
(рантайм-снятие; фейл cancel = находка C3).

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19tr.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых advance-algo по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых advance-algo (trailing) по инструменту | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tr.place.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:move_order_stop, sz:0.01, reduceOnly:true, algoClOrdId:tr_clId, callbackRatio:"0.05"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId` непустой | `ordType=move_order_stop`, `callbackRatio=0.05`. `algoId` → `tr_algoId`. При реджекте «нет позиции» — A0 + повтор | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tr.get.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:tr_algoId}, signed:true}` | HTTP 200; `b.data[0].algoId=tr_algoId`; `b.data[0].state≠"canceled"` | Trailing live/effective | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tr.pending.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` | HTTP 200; `b.data` содержит `algoId=tr_algoId` | Advance виден в pending (покрывает M14.6 advance) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tr.cancel.** `POST /raw {method:POST, path:/api/v5/trade/cancel-advance-algos, body:[{instId:ETH-USDT-SWAP, algoId:tr_algoId}], signed:true}` (**advance** семья) | HTTP 200; `b.data[0].sCode="0"` (**гипотеза** — endpoint жив на demo) | **Ядро И-2 (покрывает M21 прямой).** Гипотеза: жив, `sCode=0`. Если demo вернёт «endpoint не существует»/иную ошибку → делистинг подтверждён = **находка интегратору** (C3: правка `algo-order.md`, провенанс `рантайм`). `b.code`/`b.msg` логируются | RUN 2026-06-20 ✓ — http 200, b.code=0, data0.sCode=0, data.size=1 |
| **M19tr.canceled.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:tr_algoId}, signed:true}` | HTTP 200; `b.data[0].state="canceled"` **или** `b.data` пуст (наблюдение) | Финал cancel trailing | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tr.history.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-history, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` | HTTP 200; `b.data` содержит `algoId=tr_algoId` **или** наблюдение | Trailing в history 3м (покрывает M15.6 advance) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown M19tr.** `POST /raw {method:POST, path:/api/v5/trade/cancel-advance-algos, body:[{instId:ETH-USDT-SWAP, algoId:tr_algoId}], signed:true}` (advance) + (если A0) close-position | HTTP 200 | Trailing снят, позиция (если открывалась) закрыта — биржа чистая | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19tr.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `tr_algoId` — вернулось к чистому == старт | **Verify.end:** живых advance-algo нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep); A0-позиция (если открывалась) flat. Невозврат — связан с И-2 (фейл cancel-advance = находка C3) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M19.trailing-spread — вариант move_order_stop (callbackSpread, абсолютный)

Под /raw `callbackSpread` достижим телом конверта (прежний вариант-gap
снят). Тот же граф cancel по семье advance.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19trs.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых advance-algo по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых advance-algo (trailing) по инструменту | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19trs.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live last → `trs_spread` (абсолютный spread, кратно tickSz, напр. `floor(last·0.01)`) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19trs.place.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:move_order_stop, sz:0.01, reduceOnly:true, algoClOrdId:trs_clId, callbackSpread:trs_spread}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId` непустой | `ordType=move_order_stop`, `callbackSpread` (абсолют). Реальный вариант (не gap). `algoId` → `trs_algoId`. При реджекте «нет позиции» — A0 + повтор | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19trs.get.** `POST /raw {method:GET, path:/api/v5/trade/order-algo, query:{instId:ETH-USDT-SWAP, algoId:trs_algoId}, signed:true}` | HTTP 200; `b.data[0].state≠"canceled"` | Trailing-spread live | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19trs.cancel.** `POST /raw {method:POST, path:/api/v5/trade/cancel-advance-algos, body:[{instId:ETH-USDT-SWAP, algoId:trs_algoId}], signed:true}` (advance) | HTTP 200; `b.data[0].sCode="0"` (гипотеза И-2) | Cancel advance (та же И-2-гипотеза; фейл = находка C3) | RUN 2026-06-20 ✓ — http 200, b.code=0, data0.sCode=0, data.size=1 |
| **Teardown M19trs.** `POST /raw {method:POST, path:/api/v5/trade/cancel-advance-algos, body:[{instId:ETH-USDT-SWAP, algoId:trs_algoId}], signed:true}` (advance) + (если A0) close-position | HTTP 200 | Trailing-spread снят, позиция (если открывалась) закрыта | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19trs.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:move_order_stop}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `trs_algoId` — вернулось к чистому == старт | **Verify.end:** живых advance-algo нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep); A0-позиция (если открывалась) flat. Невозврат связан с И-2 (фейл cancel-advance = находка C3) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### M19.neg — негатив placeAlgoOrder

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19.neg.ordType.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:BOGUS, sz:0.01, reduceOnly:true, algoClOrdId:…, slTriggerPx:sl_px, slTriggerPxType:mark, slOrdPx:"-1"}, signed:true}` (битый сырой `ordType` — реальный под /raw) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект некорректного `ordType`. Прежний прокси-5xx-слой снят; сырой `ordType` достижим телом конверта; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter ordType error), data.size=0 |
| **M19.neg.size.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:conditional, sz:-1, reduceOnly:true, algoClOrdId:…, slTriggerPx:sl_px, slTriggerPxType:mark, slOrdPx:"-1"}, signed:true}` (значение вне домена) | HTTP 200; `b.data[0].sCode≠"0"` **или** `b.code≠"0"` | Реджект отрицательного размера; код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter sz error), data.size=0 |
| **M19.neg.reqParam.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:conditional, reduceOnly:true, algoClOrdId:…, slTriggerPx:sl_px, slTriggerPxType:mark, slOrdPx:"-1"}, signed:true}` (без `sz` — OKX-слой) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект OKX: `sz` обязателен. Под /raw пропуск уходит на OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50015 (Either parameter sz or closeFraction is required), data.size=0 |
### M19.neg.dupClId — негатив-цепочка (дубль algoClOrdId, stateful)

Граф: Snapshot.start(algo-pending conditional пуст) → price(ticker → sl_px) → place#1(algoClOrdId) → place#2(тот же algoClOrdId, реджект/наблюдение) → teardown(снять #1 adup_algoId + защитно #2 adup_algoId2) → Verify.end(algo-pending == старт). Свой price-шаг делает цепочку самодостаточной (sl_px). Первый place может создать живой algo → инвариант восстановления применим.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **M19dup.snapshot.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых conditional-algo по инструменту | **Snapshot.start:** чистый старт — нет живых conditional-algo | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19dup.price.** `POST /raw {method:GET, path:/api/v5/market/ticker, query:{instId:ETH-USDT-SWAP}, signed:false}` | HTTP 200; `b.data[0].last>0` | live last → `sl_px=floor(last·0.5)` (далёкий SL-триггер), кратно tickSz. Делает dup-цепочку самодостаточной (свой price-шаг ставит `sl_px`, не из M19.cond-sl) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19dup.place1.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{instId:ETH-USDT-SWAP, tdMode:isolated, posSide:net, side:sell, ordType:conditional, sz:0.01, reduceOnly:true, algoClOrdId:adup_clId, slTriggerPx:sl_px, slTriggerPxType:mark, slOrdPx:"-1"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId` непустой | Первый place принят (`algoId` → `adup_algoId`). При реджекте «нет позиции» — A0 + повтор | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19dup.place2.** `POST /raw {method:POST, path:/api/v5/trade/order-algo, body:{… algoClOrdId:adup_clId, ordType:conditional, slTriggerPx:sl_px, …}, signed:true}` (тот же `algoClOrdId`) | HTTP 200; второй — `b.data[0].sCode≠"0"` (дубль) **или** наблюдение | Реджект/поведение на дубль algoClOrdId — фиксируем; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51068, data.size=1 |
| **Teardown M19dup.** `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:adup_algoId}, {instId:ETH-USDT-SWAP, algoId:adup_algoId2}], signed:true}` (явно снять #1 и защитно #2; `adup_algoId2` по умолчанию пуст → per-element not-exist, идемпотентно) | HTTP 200 | Снять прошедшие algo: #1 (`adup_algoId`) и защитно-захваченный #2 (`adup_algoId2`) — закрыть утечку состояния. A0-close (условный close-position) — только код-тесты | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **M19dup.verify.** `POST /raw {method:GET, path:/api/v5/trade/orders-algo-pending, query:{instId:ETH-USDT-SWAP, ordType:conditional}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `adup_algoId` — вернулось к чистому == старт | **Verify.end:** живых conditional-algo нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

## M20. cancelAlgos — POST /api/v5/trade/cancel-algos (Cancel algo ordinary)

- **Объект:** OKX `POST /api/v5/trade/cancel-algos` (`signed:true`) через
  /raw. Тело — **массив** `[{instId, algoId | algoClOrdId}]` (до 10).
  **Среда:** demo. **WRITE.** Прямой — M19cond/M19tp/M19oco (.cancel).
  **Вариант — семья ordinary.** **Teardown:** в цепочке.

### M20.1 негатив (no-state) — cancel несущ. algoId (ordinary)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/trade/cancel-algos, body:[{instId:ETH-USDT-SWAP, algoId:9999999999999999}], signed:true}` | HTTP 200; `b.data[0].sCode≠"0"` (algo не найден/закрыт) | Реджект cancel несущ. algo (ordinary семья); код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51400, data.size=1 |

### M20.2 прямой + вариант

Покрыт M19cond.cancel / M19tp.cancel / M19oco.cancel (ordinary `sCode=0`).

## M21. cancelAdvanceAlgos — POST /api/v5/trade/cancel-advance-algos (Cancel advance algo)

- **Объект:** OKX `POST /api/v5/trade/cancel-advance-algos` (`signed:true`)
  через /raw. Тело — **массив** `[{instId, algoId | algoClOrdId}]`.
  **Среда:** demo. **WRITE.** Прямой — M19trailing (.cancel, ядро И-2).
  **Вариант — семья advance.** **Teardown:** в цепочке.

### M21.1 негатив (no-state) — cancel несущ. algoId (advance)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/trade/cancel-advance-algos, body:[{instId:ETH-USDT-SWAP, algoId:9999999999999999}], signed:true}` | HTTP 200; `b.data[0].sCode≠"0"` **или** иная ошибка (наблюдение — И-2: endpoint выведен из офдока) | Реджект cancel несущ. advance-algo. **Если ответ — «endpoint не существует»** (не per-element реджект) → подтверждение делистинга = находка интегратору (C3), не выдумка | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51293 (Operation failed.), data.size=1 |

### M21.2 прямой + вариант

Покрыт M19trailing.cancel / M19trailing-spread.cancel (advance `sCode=0`
— гипотеза И-2; фейл = находка).


## TG1. Place batch orders — POST /api/v5/trade/batch-orders (Trade, signed)

- **Объект:** OKX `POST /api/v5/trade/batch-orders`, `signed:true`. Тело — **массив** order-item'ов (до 20), строится руками по `batch-operations.md`: `{instId,tdMode:"isolated",side,ordType:"limit",sz,px,clOrdId}` (адаптер-константа `tdMode:"isolated"` литералом — `rules/adapter-constants.md`).
- **Предусловие:** live-цена с биржи (`GET /api/v5/market/ticker`, `signed:false`) → неисполнимая limit (`floor(last·0.5)`, кратно tickSz `0.01`). Два разных `clOrdId` (pre-request).
- **Среда:** demo. **WRITE** — реальные неисполнимые ордера на demo.
- **Достижимость:** прямой достижим (неисполнимые limit, не филятся).
- **Teardown:** `POST /api/v5/trade/cancel-batch-orders` обоими `ordId`; идемпотентная страховка. Поллинг `getPendingOrders` до отсутствия `tg_ordId1`/`tg_ordId2`, таймаут N, иначе наблюдение.

### TG1.1 прямой — цепочка place-batch (2 неисполнимых limit) → cancel-batch

Граф: getTicker → place-batch[2] → getPendingOrders(оба live) → cancel-batch[2] → getPendingOrders(пусто). RUN: поллинг до условия.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **TG1.snapshot.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых ордеров по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых ордеров по инструменту до цепочки | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG1.price.** `POST /raw {GET /api/v5/market/ticker, query{instId}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].last>0` | live `last` → `tg_px=floor(last·0.5)` (неисполнимая buy). Цена с биржи, не константа | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG1.place.** `POST /raw {POST /api/v5/trade/batch-orders, body:[{instId,tdMode:"isolated",side:"buy",ordType:"limit",sz:0.01,px:tg_px,clOrdId:tg_clId1},{…clOrdId:tg_clId2}], signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[1].sCode="0"`; `b.data[0].ordId` и `b.data[1].ordId` непустые | Оба ордера приняты (поэлементный ACK, `batch-operations.md`). `ordId` → `tg_ordId1`/`tg_ordId2`. ACK ≠ runtime truth | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG1.pending.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` содержит элементы с `ordId=tg_ordId1` и `tg_ordId2` (`state="live"`) | Оба живых ордера в pending. RUN: поллинг до появления, таймаут N | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG1.cancel.** `POST /raw {POST /api/v5/trade/cancel-batch-orders, body:[{instId,ordId:tg_ordId1},{instId,ordId:tg_ordId2}], signed:true}` (покрывает TG2 прямой) | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[1].sCode="0"` | Оба отменены (ACK). Подтверждается .flat | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG1.flat.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит `tg_ordId1`/`tg_ordId2` | Pending без наших ордеров. RUN: поллинг до отсутствия | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown TG1.** `POST /raw {POST /api/v5/trade/cancel-batch-orders, body:[{instId,ordId:tg_ordId1},{instId,ordId:tg_ordId2}], signed:true}` (идемпотентная страховка) | HTTP 200; (`sCode="0"` или already-canceled/not-exist) | Биржа чистая (ни одного живого ордера). Цена далеко от рынка → исполнение исключено | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG1.verify.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `tg_ordId1`/`tg_ordId2` — вернулось к чистому == старт | **Verify.end:** живых ордеров по инструменту нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep). Расхождение → фейл (инвариант) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG1.2 негатив — частичный реджект (битый item в пакете)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/batch-orders, body:[{instId,tdMode:"isolated",side:"buy",ordType:"limit",sz:0.01,px:tg_px,clOrdId:…},{instId,tdMode:"isolated",side:"buy",ordType:"limit",sz:-1,px:tg_px,clOrdId:…}], signed:true}` (второй item — sz вне домена) | HTTP 200; `b.data[1].sCode≠"0"`; первый — `b.data[0].sCode="0"` **или** `b.data[0].sCode≠"0"` (наблюдение) | Поэлементный исход вне PM (`batch-operations.md`): валидный item проходит, битый реджектится в `data[1].sCode`. Точный код — наблюдение. Если item1 прошёл — `ordId` → `tg_strayOrdId` (см. teardown) | RUN 2026-06-20 ✓ — http 200, b.code=2, data0.sCode=0 (Bulk operation partially successful), data.size=2 |
| **Teardown TG1.2.** `POST /raw {POST /api/v5/trade/cancel-order, body:{instId,ordId:tg_strayOrdId}, signed:true}` (защитно, если item1 неожиданно прошёл; `tg_strayOrdId` по умолчанию пуст → not-exist, идемпотентно) | HTTP 200; (`sCode="0"` или not-exist) | Снять защитно-захваченный item1 (`tg_strayOrdId`) — закрыть утечку состояния batch-dup | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG1.3 негатив — пропуск обязательного instId в item (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/batch-orders, body:[{tdMode:"isolated",side:"buy",ordType:"limit",sz:0.01,px:tg_px,clOrdId:…}], signed:true}` (item без `instId`) | HTTP 200; `b.data[0].sCode≠"0"` **или** `b.code≠"0"` | Под /raw passthrough-слоя нет — пропуск обязательного уходит на OKX → реджект. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=50014, data.size=1 |

---

## TG2. Cancel batch orders — POST /api/v5/trade/cancel-batch-orders (Trade, signed)

- **Объект:** OKX `POST /api/v5/trade/cancel-batch-orders`, `signed:true`. Тело — массив `{instId, ordId|clOrdId}` (до 20; оба → биржа берёт `ordId`, `batch-operations.md`).
- **Предусловие:** прямой богатый требует живых ордеров — покрыт цепочкой TG1 (TG1.cancel). Здесь — no-state негатив.
- **Среда:** demo. **WRITE.**
- **Достижимость:** прямой — через TG1; негатив достижим без состояния.
- **Teardown:** не требуется (негатив cancel'ит несуществующее).

### TG2.1 прямой

Покрыт TG1 (TG1.cancel by `ordId`): отмена двух живых ордеров пакетом, `b.code="0"`, оба `data[i].sCode="0"`, подтверждение TG1.flat.

### TG2.2 негатив (no-state) — cancel несуществующих ордеров

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/cancel-batch-orders, body:[{instId,ordId:"9999999999999999"},{instId,ordId:"9999999999999998"}], signed:true}` | HTTP 200; `b.data[0].sCode≠"0"`; `b.data[1].sCode≠"0"` (order does not exist) | Поэлементный реджект несущ. `ordId`. Точный код (51603/иной) — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51400 (All operations failed), data.size=2 |

### TG2.3 негатив — пропуск идентификатора в item (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/cancel-batch-orders, body:[{instId}], signed:true}` (item без `ordId`/`clOrdId`) | HTTP 200; `b.data[0].sCode≠"0"` **или** `b.code≠"0"` | Реджект OKX: ни `ordId`, ни `clOrdId`. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51003, data.size=1 |

---

## TG3. Amend order — POST /api/v5/trade/amend-order (Trade, signed)

- **Объект:** OKX `POST /api/v5/trade/amend-order`, `signed:true`. Body: `instId` + одно из `ordId`/`clOrdId`, `newSz`/`newPx`, `cxlOnFail`, `pxAmendType` (`order.md`). `newSz` — новый **полный** размер. Доменом не используется (REPLACE-only), контракт — поверхность биржи.
- **Предусловие:** прямой требует живого ордера — поставить неисполнимый limit (как Climit), затем amend `px`/`sz`, отменить в teardown. Live-цена с биржи.
- **Среда:** demo. **WRITE.**
- **Достижимость:** прямой достижим (неисполнимый limit).
- **Teardown:** `cancel-order` по `ord_ordId`; идемпотентная страховка. Поллинг `getOrder` до `state=canceled`, таймаут N.

### TG3.1 прямой — цепочка place(limit) → amend(px/sz) → getOrder → cancel

Граф: getTicker → place(limit неисполнимый) → amend(newPx/newSz) → getOrder(подтверждение newPx/newSz) → cancel. RUN: поллинг до условия.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **TG3.snapshot.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых ордеров по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых ордеров по инструменту до цепочки | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG3.price.** `POST /raw {GET /api/v5/market/ticker, query{instId}, signed:false}` | HTTP 200; `b.data[0].last>0` | live `last` → `am_px=floor(last·0.5)`, `am_newPx=floor(last·0.4)` (обе неисполнимы) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG3.place.** `POST /raw {POST /api/v5/trade/order, body{instId,tdMode:"isolated",side:"buy",ordType:"limit",sz:0.01,px:am_px,clOrdId:am_clId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].ordId` непустой | `ordId` → `am_ordId`. Неисполнимый limit для amend | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG3.amend.** `POST /raw {POST /api/v5/trade/amend-order, body{instId,ordId:am_ordId,newSz:"0.02",newPx:am_newPx,cxlOnFail:false}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].ordId=am_ordId` | ACK amend (`sCode=0` ≠ «изменение подтверждено», `order.md`). Подтверждение — getOrder | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG3.get.** `POST /raw {GET /api/v5/trade/order, query{instId,ordId:am_ordId}, signed:true}` | HTTP 200; `b.data[0].ordId=am_ordId`; `b.data[0].sz="0.02"`; `b.data[0].px=am_newPx`; `state="live"` | Amend применён — `sz`/`px` обновлены. RUN: поллинг до отражения newSz/newPx | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown TG3.** `POST /raw {POST /api/v5/trade/cancel-order, body{instId,ordId:am_ordId}, signed:true}` (идемпотентная страховка) | HTTP 200; (`sCode="0"` или already-canceled/not-exist) | Ордер снят — биржа чистая | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG3.verify.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `am_ordId` — вернулось к чистому == старт | **Verify.end:** живых ордеров по инструменту нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep). Расхождение → фейл (инвариант) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG3.2 негатив — amend несуществующего ордера

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/amend-order, body{instId,ordId:"9999999999999999",newPx:am_newPx}, signed:true}` | HTTP 200; `b.data[0].sCode≠"0"` **или** `b.code≠"0"` (order does not exist) | Реджект amend несущ. ордера. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51503 (All operations failed), data.size=1 |

### TG3.3 негатив — amend без изменений (ни newSz, ни newPx)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/amend-order, body{instId,ordId:"9999999999999999"}, signed:true}` (нет `newSz`/`newPx`) | HTTP 200; `b.data[0].sCode≠"0"` **или** `b.code≠"0"` | Реджект OKX: amend без изменений и/или по несущ. ордеру. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51500 (All operations failed), data.size=1 |

---

## TG4. Amend batch orders — POST /api/v5/trade/amend-batch-orders (Trade, signed)

- **Объект:** OKX `POST /api/v5/trade/amend-batch-orders`, `signed:true`. Тело — массив amend-item'ов (до 20): `{instId, ordId|clOrdId, newSz/newPx, cxlOnFail}` (`batch-operations.md`). `newSz` — новый полный размер.
- **Предусловие:** прямой требует живых ордеров — поставить два неисполнимых limit (place-batch), amend оба, отменить в teardown. Live-цена с биржи.
- **Среда:** demo. **WRITE.**
- **Достижимость:** прямой достижим.
- **Teardown:** `cancel-batch-orders` обоими `ordId`; идемпотентная страховка. Поллинг до отсутствия в pending.

### TG4.1 прямой — цепочка place-batch[2] → amend-batch[2] → getOrder ×2 → cancel-batch

Граф: getTicker → place-batch[2 неисполнимых] → amend-batch[2 newPx/newSz] → getOrder(оба, подтверждение) → cancel-batch. RUN: поллинг до условия.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **TG4.snapshot.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых ордеров по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых ордеров по инструменту до цепочки | RUN 2026-06-20 ✓ — чистый старт (живых ордеров нет) |
| **TG4.price.** `POST /raw {GET /api/v5/market/ticker, query{instId}, signed:false}` | HTTP 200; `b.data[0].last>0` | live `last` → `ab_px=floor(last·0.5)`, `ab_newPx=floor(last·0.4)` (неисполнимы) | RUN 2026-06-20 ✓ — live last получен |
| **TG4.place.** `POST /raw {POST /api/v5/trade/batch-orders, body:[{instId,tdMode:"isolated",side:"buy",ordType:"limit",sz:0.01,px:ab_px,clOrdId:ab_clId1},{…clOrdId:ab_clId2}], signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[1].sCode="0"` | `ordId` → `ab_ordId1`/`ab_ordId2`. Два живых неисполнимых ордера | RUN 2026-06-20 ✓ — http 200, b.code=0, оба data[].sCode=0 (Order placed) — 2 ордера |
| **TG4.amend.** `POST /raw {POST /api/v5/trade/amend-batch-orders, body:[{instId,ordId:ab_ordId1,newSz:"0.02",newPx:ab_newPx},{instId,ordId:ab_ordId2,newPx:ab_newPx}], signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[1].sCode="0"` | Оба amend приняты (поэлементный ACK). ACK ≠ подтверждение — getOrder | RUN 2026-06-20 ✓ — http 200, b.code=0, оба data[].sCode=0 (amend-batch ACK принят) |
| **TG4.get1.** `POST /raw {GET /api/v5/trade/order, query{instId,ordId:ab_ordId1}, signed:true}` | HTTP 200; `b.data[0].sz="0.02"`; `b.data[0].px=ab_newPx` | Amend item1 применён. RUN: поллинг до отражения | RUN 2026-06-20 ✓ — amend отражён: `sz=0.02`, `px=newPx`. Изначально фейл (poll-таймаут кейса 25s мал для amend-reflection на demo); тест выровнен на 60s (как одиночный amend TG3.1) → green. Не дефект контракта — известная асинхронность amend (ACK ≠ runtime truth) |
| **TG4.get2.** `POST /raw {GET /api/v5/trade/order, query{instId,ordId:ab_ordId2}, signed:true}` | HTTP 200; `b.data[0].px=ab_newPx` | Amend item2 применён | RUN 2026-06-20 ✓ — amend отражён: `px=newPx` (poll 60s, после выравнивания) |
| **Teardown TG4.** `POST /raw {POST /api/v5/trade/cancel-batch-orders, body:[{instId,ordId:ab_ordId1},{instId,ordId:ab_ordId2}], signed:true}` (идемпотентная страховка) | HTTP 200; (`sCode="0"` или already-canceled/not-exist) | Оба ордера сняты — биржа чистая | RUN 2026-06-20 ✓ — teardown отработал: cancel-batch b.code=0, оба data[].sCode=0 → биржа очищена |
| **TG4.verify.** `POST /raw {GET /api/v5/trade/orders-pending, query{instId}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `ab_ordId1`/`ab_ordId2` — вернулось к чистому == старт | **Verify.end:** живых ордеров по инструменту нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep). Расхождение → фейл (инвариант) | RUN 2026-06-20 ✓ — Verify.end: живых ордеров нет (== Snapshot.start); цепочка зелёная после выравнивания poll (`validate-tg4-02.log`) |

### TG4.2 негатив — частичный реджект (битый item: несущ. ordId)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/amend-batch-orders, body:[{instId,ordId:ab_ordId1,newPx:ab_newPx},{instId,ordId:"9999999999999999",newPx:ab_newPx}], signed:true}` (item2 — несущ. ordId) | HTTP 200; `b.data[1].sCode≠"0"`; `b.data[0].sCode="0"` **или** наблюдение | Поэлементный исход: валидный amend проходит, несущ. реджектится в `data[1].sCode`. Точный код — наблюдение. Teardown: снять прошедшие | RUN 2026-06-20 ✓ — http 200, b.code=2, data0.sCode=0 (Bulk operation partially successful), data.size=2 |

### TG4.3 негатив — пропуск изменений и идентификатора в item (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/amend-batch-orders, body:[{instId}], signed:true}` (item без `ordId`/`clOrdId` и без `newSz`/`newPx`) | HTTP 200; `b.data[0].sCode≠"0"` **или** `b.code≠"0"` | Реджект OKX: нет идентификатора и нет изменений. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51003, data.size=1 |

---

## TG5. Order history 3m — GET /api/v5/trade/orders-history-archive (Trade, signed)

- **Объект:** OKX `GET /api/v5/trade/orders-history-archive`, `signed:true`. Фильтры: `instType` (обязателен), `instId`, `ordType`, `state`, пагинация `after`/`before` по `ordId`, `limit ≤ 100` (`order.md`). Архив последних 3 месяцев.
- **Предусловие:** богатое наполнение требует ордеров в архивном окне.
- **Среда:** demo. **READ.**
- **Достижимость:** **гейт достижимости** — архивное окно на свежем demo может быть пусто/недостижимо. Прямой = валидный пустой массив (`b.code="0"`, `data=[]` допустим) ИЛИ отказ по достижимости с причиной (богатое наполнение не выдумывается). Негатив достижим.
- **Teardown:** не требуется (read).

### TG5.1 прямой (пустой/валидный — гейт достижимости)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET /api/v5/trade/orders-history-archive, query{instType:"SWAP",instId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив (пустой валиден) | Архив 3м: на свежем demo может быть пуст. Богатое наполнение не выдумывается — отказ по достижимости с причиной, если требуется содержимое | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG5.2 негатив — пропуск обязательного instType (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET /api/v5/trade/orders-history-archive, query{instId}, signed:true}` (без `instType`) | HTTP 200; `b.code≠"0"` | Под /raw passthrough-слоя нет — пропуск обязательного `instType` уходит на OKX → реджект. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instType can not be empty.), data.size=0 |

### TG5.3 негатив — instType вне домена (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET /api/v5/trade/orders-history-archive, query{instType:"BOGUS"}, signed:true}` | HTTP 200; `b.code≠"0"` | Реджект значения вне домена. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

### TG5.4 негатив — state вне домена (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET /api/v5/trade/orders-history-archive, query{instType:"SWAP",state:"BOGUS"}, signed:true}` | HTTP 200; `b.code≠"0"` | Реджект `state` вне домена (`filled`/`canceled`/`mmp_canceled`). Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51600 (Status not found), data.size=0 |

---

## TG6. Cancel All After (DMS) — POST /api/v5/trade/cancel-all-after (Trade, signed)

- **Объект:** OKX `POST /api/v5/trade/cancel-all-after`, `signed:true`. Body: `timeOut` (String, обяз.; `0` или [10,120]; `0` — выключить DMS), опц. `tag` (`cancel-all-after.md`). Rate limit 1 req/s.
- **Предусловие:** нет.
- **Среда:** demo. **WRITE, но безопасно:** `timeOut="0"` отключает DMS, pending-ордера не отменяет.
- **Достижимость:** прямой достижим (`timeOut="0"`).
- **Teardown:** не требуется — `timeOut="0"` оставляет DMS выключенным; ордеров не трогает.
- **Self-restoring:** `timeOut=0` не оставляет остаточного состояния (DMS уже выключен) — отдельный Snapshot.start/Verify.end не нужен.

### TG6.1 прямой — timeOut=0 (DMS выключен)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/cancel-all-after, body{timeOut:"0"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].triggerTime="0"` (DMS выключен); `b.data[0].ts` присутствует | DMS выключен — безопасно (`triggerTime=0`, `cancel-all-after.md`). Ордеров не трогает | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG6.2 негатив — timeOut вне допустимого диапазона (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/cancel-all-after, body{timeOut:"5"}, signed:true}` (вне домена: не `0`, не [10,120]) | HTTP 200; `b.code≠"0"` | Реджект `timeOut` вне диапазона. Точный код — наблюдение. **Безопасно:** `5` < минимума → DMS не активируется | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter timeOut error), data.size=0 |

### TG6.3 негатив — битый timeOut (нечисловой, OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/cancel-all-after, body{timeOut:"abc"}, signed:true}` | HTTP 200; `b.code≠"0"` | Реджект нечислового `timeOut`. Точный код — наблюдение. Безопасно: запрос не активирует DMS | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter timeOut error), data.size=0 |

### TG6.4 негатив — пропуск обязательного timeOut (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/cancel-all-after, body{}, signed:true}` (без `timeOut`) | HTTP 200; `b.code≠"0"` | Под /raw пропуск обязательного уходит на OKX → реджект. Точный код — наблюдение. Безопасно: DMS не активируется | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter timeOut can not be empty.), data.size=0 |

---

## TG7. Order precheck — POST /api/v5/trade/order-precheck (Trade, signed)

- **Объект:** OKX `POST /api/v5/trade/order-precheck`, `signed:true`. Dry-run пре-оценка влияния ордера на счёт — **ордер НЕ ставится**. Body — подмножество place order: `{instId,tdMode:"isolated",side,ordType,sz,px}` (`order-precheck.md`).
- **Предусловие:** precheck применим **только acctLv 3/4 (MCM/PM)** (`order-precheck.md`). Кейс делает его применимым **разведочно**: изолированно (аккаунт пустой) переключает `acctLv`→3, прогоняет precheck, **восстанавливает** исходный `acctLv` (инвариант восстановления состояния).
- **Среда:** demo/non-prod. **Stateful (настройка `acctLv`).**
- **Достижимость:** прямой достижим **через переключение `acctLv`**. Переключение — `POST /api/v5/account/set-account-level` (эндпоинт **вне периметра**, здесь — **тест-фикстура**, не покрывается как цель). Если demo не даёт переключить/вернуть `acctLv` — **находка** (реальное поведение + флаг остаточного `acctLv`), снапшот precheck не выдумывается.
- **Teardown:** restore `acctLv` к исходному + Verify.end (`acctLv` == старт).
- **`tdMode` под MCM — per-contract.** При принудительном acctLv 3 (MCM)
  `tdMode` (тело шлёт `isolated`) интерпретируется иначе, чем под cross/isolated
  аккаунт. Поэтому результат precheck **документируется как наблюдение**:
  field-presence (`adjEq`/`imr`/`mmr`/`mgnRatio`) ассертится **только при
  `code="0"`**; иначе реджект = **наблюдение** (не жёсткий fail, не выдумка) —
  промах happy-ассерта может быть не связан с достижимостью.

### TG7.1 разведочный (цепочка) — Snapshot.start → set acctLv 3 → precheck → restore → Verify.end

Граф: config(acctLv→`start_acctLv`) → set-account-level(3) → ticker → precheck → set-account-level(`start_acctLv`) → config(verify acctLv==старт). **Изолированно** (аккаунт пустой — предусловие смены acctLv). RUN: поллинг до условия.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **TG7.snapshot.** `POST /raw {GET /api/v5/account/config, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].acctLv` → `start_acctLv` | **Snapshot.start:** снимок исходного `acctLv` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG7.setLv.** `POST /raw {POST /api/v5/account/set-account-level, body{acctLv:"3"}, signed:true}` (фикстура, вне периметра) | HTTP 200; `b.code="0"` (эхо `acctLv="3"`) **или** реджект | Перевод в MCM для применимости precheck. Реджект (есть позиции/не разрешено) → **находка**, далее precheck = наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51070 (You do not meet the requirements for switching to this account mode…), data.size=0 |
| **TG7.price.** `POST /raw {GET /api/v5/market/ticker, query{instId}, signed:false}` | HTTP 200; `b.data[0].last>0` | live `last` → `pc_px=floor(last)` (реалистичная limit-цена) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG7.check.** `POST /raw {POST /api/v5/trade/order-precheck, body{instId,tdMode:"isolated",side:"buy",ordType:"limit",sz:0.01,px:pc_px}, signed:true}` | HTTP 200; **если `code="0"`:** `b.data[0].adjEq`/`imr`/`mmr`(/`mgnRatio`) присутствуют (снапшот до/после, `order-precheck.md`); **иначе** реджект зафиксировать (`b.code`/`b.msg` — наблюдение, не fail) | **Документируем реальный ответ precheck.** `tdMode` под MCM — per-contract: field-presence ассертится только при `code="0"`, иначе реджект = наблюдение (не выдумка), промах может быть не связан с достижимостью. Ордер НЕ ставится | RUN 2026-06-20 ✓ — http 200, b.code=51010 (You can't complete this request under your current account mode.), data.size=0 |
| **TG7.restore.** `POST /raw {POST /api/v5/account/set-account-level, body{acctLv:"{{start_acctLv}}"}, signed:true}` | HTTP 200; `b.code="0"` (эхо `start_acctLv`) | Восстановить исходный `acctLv`. Невозврат → **находка** + флаг остаточного состояния | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |
| **TG7.verify.** `POST /raw {GET /api/v5/account/config, signed:true}` (поллинг) | HTTP 200; `b.data[0].acctLv == start_acctLv` | **Verify.end:** `acctLv` восстановлен. Расхождение → фейл (инвариант) / находка | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG7.2 негатив — пропуск обязательного instId (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/order-precheck, body{tdMode:"isolated",side:"buy",ordType:"limit",sz:0.01}, signed:true}` (без `instId`) | HTTP 200; `b.code≠"0"` | Под /raw пропуск обязательного уходит на OKX → реджект (либо «неприменимо» вне MCM/PM). Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51010 (You can't complete this request under your current account mode.), data.size=0 |

---

## TG8. Account rate limit — GET /api/v5/trade/account-rate-limit (Trade, signed)

- **Объект:** OKX `GET /api/v5/trade/account-rate-limit`, `signed:true`. Без параметров запроса. Fill-ratio-based лимит суб-аккаунта (`account-rate-limit.md`).
- **Предусловие:** нет.
- **Среда:** demo. **READ.**
- **Достижимость:** прямой достижим.
- **Teardown:** не требуется (read).

### TG8.1 прямой — account-rate-limit

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET /api/v5/trade/account-rate-limit, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].accRateLimit` присутствует; `b.data[0].ts` присутствует | Текущий лимит суб-аккаунта. Расширенные поля (`nextAccRateLimit`/`fillRatio`/`mainFillRatio`) — `""` вне VIP 5+ (`account-rate-limit.md`), не ассертим значение | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG8.2 негатив — сломанный конверт (лишний path-сегмент, OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET /api/v5/trade/account-rate-limit-bogus, signed:true}` (несуществующий path на OKX) | HTTP 200; `b.code≠"0"` **или** иная ошибка (наблюдение) | Несуществующий endpoint OKX → реджект/ошибка. Точный код — наблюдение (`b.code`/`b.msg` логируются) | RUN 2026-06-20 ✓ — http 500 |

---

## TG9. Amend algo — POST /api/v5/trade/amend-algos (Algo, signed)

- **Объект:** OKX `POST /api/v5/trade/amend-algos`, `signed:true`. **Только Stop/Trigger** — advance-семья (`move_order_stop`/iceberg/twap/trailing) **не амендится** (И-3, `algo-order.md`). Body: `instId` (обяз.), `algoId`/`algoClOrdId` (одно обяз.), `cxlOnFail`, `newSz`; TP/SL-ветка: `newSlTriggerPx`/`newSlOrdPx`/`new*TriggerPxType`; trigger: `newTriggerPx`/`newOrdPx`. Доменом не используется (REPLACE-only).
- **Предусловие:** прямой требует живого conditional/trigger algo — поставить conditional (как M19cond, reduceOnly с возможной A0-позицией), amend `newSlTriggerPx`, отменить в teardown. Live-цена с биржи. Advance не амендится — отдельный негатив/наблюдение.
- **Среда:** demo. **WRITE.**
- **Достижимость:** прямой достижим (conditional). Amend advance — негатив (нормативно не поддержан).
- **Teardown:** `cancel-algos` (ordinary семья) по `aa_algoId`; (если A0) `close-position`. Поллинг `getAlgoOrder` до `state=canceled`/пусто.

### TG9.1 прямой — цепочка place(conditional) → amend(newSlTriggerPx) → getAlgo → cancel

Граф: getTicker → (A0 если «нет позиции») → place(conditional SL) → amend(newSlTriggerPx) → getAlgo(подтверждение) → cancel(ordinary). RUN: поллинг до условия.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **TG9.snapshot.** `POST /raw {GET /api/v5/trade/orders-algo-pending, query{instId, ordType:"conditional"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` не содержит живых conditional-algo по инструменту (чистый старт) | **Snapshot.start:** чистый старт — нет живых conditional-algo по инструменту до цепочки | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG9.price.** `POST /raw {GET /api/v5/market/ticker, query{instId}, signed:false}` | HTTP 200; `b.data[0].last>0` | live `last` → `aa_slPx=floor(last·0.5)`, `aa_newSlPx=floor(last·0.4)` (далёкие SL-триггеры) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG9.place.** `POST /raw {POST /api/v5/trade/order-algo, body{instId,tdMode:"isolated",posSide:"net",side:"sell",ordType:"conditional",sz:0.01,reduceOnly:true,slTriggerPx:aa_slPx,slOrdPx:"-1",slTriggerPxType:"mark",algoClOrdId:aa_clId}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId` непустой | `ordType=conditional`, `slOrdPx=-1` (market после trigger). `posSide:net` — adapter-инвариант (как все M19 algo-place). `algoId` → `aa_algoId`. При реджекте «нет позиции» — A0 (min market-позиция) + повтор | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG9.amend.** `POST /raw {POST /api/v5/trade/amend-algos, body{instId,algoId:aa_algoId,newSlTriggerPx:aa_newSlPx,cxlOnFail:false}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].sCode="0"`; `b.data[0].algoId=aa_algoId` | ACK amend conditional (Stop/Trigger поддержан, `algo-order.md`). ACK ≠ подтверждение | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG9.get.** `POST /raw {GET /api/v5/trade/order-algo, query{instId,algoId:aa_algoId}, signed:true}` | HTTP 200; `b.data[0].algoId=aa_algoId`; `b.data[0].slTriggerPx=aa_newSlPx` **или** наблюдение | Amend применён — `slTriggerPx` обновлён. RUN: поллинг до отражения | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **Teardown TG9.** `POST /raw {POST /api/v5/trade/cancel-algos, body:[{instId,algoId:aa_algoId}], signed:true}` + (если A0) `close-position` | HTTP 200; (`sCode="0"` или already/not-exist) | Algo снят, позиция (если открывалась) закрыта — биржа чистая | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **TG9.verify.** `POST /raw {GET /api/v5/trade/orders-algo-pending, query{instId, ordType:"conditional"}, signed:true}` (поллинг до условия) | HTTP 200; `b.code="0"`; `b.data` не содержит `aa_algoId` — вернулось к чистому == старт | **Verify.end:** живых conditional-algo по инструменту нет (== Snapshot.start) через wait-until-condition (таймаут N, не sleep); A0-позиция (если открывалась) flat. Расхождение → фейл (инвариант) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### TG9.2 негатив — amend advance (trailing не амендится, И-3)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/amend-algos, body{instId,algoId:"9999999999999999",newSz:"0.02"}, signed:true}` (попытка amend по advance-типу / несущ. — нормативно advance не амендится, И-3) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект: advance-семья не поддержана `amend-algos` (`algo-order.md` И-3) и/или algo не найден. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51527, data.size=1 |

### TG9.3 негатив — amend несуществующего algo

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/amend-algos, body{instId,algoId:"9999999999999999",newSlTriggerPx:aa_newSlPx}, signed:true}` | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Реджект amend несущ. algo. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51527, data.size=1 |

### TG9.4 негатив — пропуск идентификатора algo (OKX-слой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {POST /api/v5/trade/amend-algos, body{instId,newSlTriggerPx:aa_newSlPx}, signed:true}` (без `algoId`/`algoClOrdId`) | HTTP 200; `b.code≠"0"` **или** `b.data[0].sCode≠"0"` | Под /raw пропуск обязательного идентификатора уходит на OKX → реджект. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=1, data0.sCode=51003, data.size=1 |


## AG1. Positions history — GET /api/v5/account/positions-history (Account)

- **Объект:** OKX `GET /api/v5/account/positions-history`, `signed:true`. Через `POST /api/proxy/okx/raw`.
- **Предусловие:** нет (read-only); прямой содержательный результат требует закрытой позиции в окне ~3 месяцев. На свежем demo история пуста.
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **частично достижим** — запрос проходит (HTTP 200, `code="0"`), но `data` валидно пуст на свежем demo (закрытых позиций нет). Содержательной строки истории нет → проверяем форму массива, не значения полей. Глубокий архив за пределами 3м недостижим (см. contract §История — 3 месяца).
- **Teardown:** не требуется (read-only).

### AG1.1 Прямой — пустое окно валидно (форма)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/positions-history, query:{instType:SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив (Array.isArray); при непустом `data[0]` — присутствуют `posId`,`instId`,`mgnMode`,`posSide`,`realizedPnl`,`uTime` | Запрос принят; на свежем demo `data=[]` валидно (закрытых позиций нет); при наличии истории — структура элемента по contract (`realizedPnl=pnl+fee+fundingFee+liqPenalty`). Пустой `data` НЕ ошибка | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG1.2 Негатив — битое значение `mgnMode` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/positions-history, query:{instType:SWAP, mgnMode:bogus}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `mgnMode` вне домена (`cross`/`isolated`). Точный код — наблюдение, если не документирован | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter mgnMode error), data.size=0 |

### AG1.3 Негатив — битое значение `type` (вне домена 1..6)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/positions-history, query:{instType:SWAP, type:99}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `type` вне домена (1..6). Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter type error), data.size=0 |

### AG1.4 Негатив — пагинация по `uTime` вне окна (after в будущем → пусто)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/positions-history, query:{instType:SWAP, after:1, limit:10}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив (ожидается пустой) | Пагинация по `uTime`: `after=1` (эпоха) отрезает всё новее 1мс → `data=[]`. Пустой результат вне окна валиден, не реджект. Если OKX реджектит формат `after` — код в наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=0 |

### AG1.5 Содержательный (шаг 7, N11) — семантика агрегации partial-close ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловие п. 1)

**Гейтит корректность числа `Deal.resultProfit`** (`docs/rules/pnl-reconciliation.md` реш.6). Форм-кейсы AG1.1-1.4 проверяют структуру пустого/битого ответа; здесь — **содержательный инвариант агрегации**, который выбранный путь берёт на веру.

- **Что верифицировать:** после позиции с **частичным выходом** (partial TP `type` 1) и последующим **полным закрытием** (SL/close `type` 2) — отдаёт ли OKX **ОДНУ финализированную запись на `posId`**, чей `realizedPnl` **кумулятивен по обоим слайсам** (не только по последнему), и **в какой момент** запись финализирована (риск чтения послайсовой/нефинализированной записи → систематический недосчёт realized, усечение левого хвоста R).
- **Требует фикстуры-цепочки** (не form-only): open position → partial reduce-only close → full close (или SL-триггер) → `REFRESH_POSITION` подтверждает flat → read `positions-history` по `posId`. На свежем demo нужна реальная закрытая позиция в окне — содержательный прогон, не пустой массив AG1.1.
- **Ожидание (проверяется, не предполагается):** `Array.isArray(data)`; для `posId` сделки — **ровно одна** запись; `realizedPnl ≈ Σ(pnl+fee+fundingFee+liqPenalty)` по всей жизни позиции (сверить с суммой bills за окно); `closeTotalPos` = полный закрытый объём.
- **Статус:** ⏳ **PENDING — до `CODE` шага 7** (интегратор/тестер: собрать фикстуру, прогнать на demo, зафиксировать факт; если OKX **не** агрегирует в одну запись — путь числа корректируется, эскалация на `solution-designer`). Провенанс — N11 отчёта `phase-1-step-7-docs-check-2.md`.
- **Фикстура — общая, прогон один.** На эту же цепочку (закрытая позиция +
  bills за окно) садятся, **не создавая лишнего прогона**: **AG3.5 / H2-рантайм**
  (гранулярность bills — комбинированная запись vs отдельные), **AG12.5 / RQ-3**
  (ставка `feeGroup` × notional ≈ фактический `fee` в bills), **AG3.4 / RQ-4**
  (`ccy` fee-bills = USDT, инвариант settle-ccy). Собирается один раз,
  инспектируется на все вопросы разом.
- **Дополнение вопросника (H24, `GAPS_CLOSE_7`) — метка entry-fee против
  `cTime` позиции — ✅ СНЯТО КОНСТРУКЦИЕЙ, прогон не нужен** (H9
  `DOCS_CHECK_16`, решение пользователя).
  - **Что спрашивалось:** нижней границей окна был `cTime` открытия
    позиции; если биржа штампует комиссию **входа** временем раньше
    (`ts(entry-fee) < cTime(position)`), входная нога комиссии выпадала бы
    из окна на **каждой** сделке — расхождение сверки сверх допуска
    (крупная сделка: notional 10 000, taker 0.05% ⇒ fee 5.0 против допуска
    max(0.01; 0.5%·Σ|amount| ≈ 2.5); мелкая: fee 0.05 против 0.025).
  - **Почему вопрос больше не имеет силы:** нижнюю границу пишет
    **единственный** писатель — `SubmitOrderExecutor`,
    `Order.externalCreatedAt` первой отправленной ноги, **всегда** при
    постановке (`docs/models/domain/aggregate/Deal.md` §«Почему у нижней
    границы один писатель»). Комиссия входа возникает при **филле**, филл —
    не раньше постановки ⇒ `ts(entry-fee) ≥ externalCreatedAt` **по
    построению**, при любом ответе источника. Окно шире, но лишних движений
    не захватывает: активная сделка на инструмент одна
    (`docs/models/domain/other/DealCashFlow.md` §«Линковка к `Deal`»).
  - **Наблюдение остаётся полезным, но не обязательным:** если фикстура и
    так собрана, сравнение `ts(entry-fee)` с `cTime` фиксируется как факт
    источника в `docs/integrations/okx/contracts/account-bills.md`. Гейта
    и действия при отрицательном ответе за ним больше нет.
  - **Статус:** ✅ **СНЯТ** (`GAPS_CLOSE_16`). Провенанс — H24 отчёта
    `phase-1-step-7-docs-check-7.md`, закрытие — H9 `DOCS_CHECK_16`.

### AG1.6 Содержательный (шаг 7, H5) — оси адресации записи без `posId` ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловие п. 6)

**Гейтит create-тропу** «позиция впервые увидена уже закрытой»
(`docs/components/RefreshPositionExecutor.md`,
`docs/models/mapping/PositionCloseResult.md` §Validation). Модель сняла
ключевание записи по `posId`: когда позиция открылась и закрылась между
тиками оркестратора, `posId` локально не наблюдался, и запись адресуется
**инструментом и окном сделки**. Что источник при этом принимает и как
себя ведёт — из доков не выводится.

- **Что верифицировать:** (1) какие **оси запроса** `positions-history`
  принимает помимо `posId` — `instId` + `after`/`before` по `uTime`,
  `instType`, их совместимость и обязательность; (2) поведение, когда в
  окне по инструменту оказалось **несколько** записей — несколько циклов
  открытия-закрытия внутри окна и/или частичные закрытия отдельными
  записями: сколько строк возвращается, как они упорядочены, различимы ли
  циклы; (3) отдаёт ли запись `cTime` и `direction` (операнды
  материализации `Position` на create-тропе, H4 `DOCS_CHECK_11`).
- **Фикстура — та же §AG1.5** плюс один быстрый цикл open→close **внутри
  одного окна**, чтобы окно содержало более одной записи.
- **Действие при «несколько записей неразличимы»:** структурная валидация
  второй ноги для этой оси не выразима ⇒ эскалация на
  `solution-designer` (возможно, create-тропа сужается до окон с ровно
  одной записью, а остальные уходят в `AnomalyReport`).
- **Статус:** ⏳ **PENDING**. Провенанс — H5 `DOCS_CHECK_11`.

### AG1.7 Содержательный (шаг 7, H20) — семантика и знаки числовых полей записи ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловия п. 7 — горизонт `fundingFee`; п. 14 — форма пустого значения; знаки (2) гейта не образуют)

`fundingFee` возвращён в used-набор (H20 `DOCS_CHECK_11`) как
авторитетный операнд де-микширования R-мультипликатора
(`docs/rules/risk-policy.md` §«Асимметрия числителя и знаменателя»).

- **Что верифицировать (1) — горизонт `fundingFee`:** накоплен ли
  `fundingFee` финализированной записи **за всю жизнь `posId`** или только
  **за последнее закрытие**.
- **Ожидание (проверяется):** кумулятивен за жизнь `posId` — симметрично
  `realizedPnl`. От ответа зависит, сверяется ли Σ`amount` по
  `FUNDING`-строкам окна с ним **напрямую** или с поправкой.
- **Что верифицировать (2) — фактические знаки трёх операндов** (H15
  `DOCS_CHECK_16`, добавлено к тому же кейсу — фикстура та же):
  - знак `fundingFee` в записи (нормализация выполняется в одном месте,
    `docs/models/mapping/PositionCloseResult.md` §«Знак `fundingFee`»);
  - знак `fee` в записи — сейчас **заявлен утверждением без прогона**
    («минус — комиссия, плюс — ребейт»);
  - знак `liqPenalty` в записи — сейчас **не заявлен вовсе** («сырой знак»
    у трёх носителей, направление не назвал ни один);
  - знак `balChg` у bill'а **ликвидационного штрафа** — левый операнд
    четвёртой пары собирается из bills, правый из positions-history, и
    сравниваются они **через** эндпоинты.
- **Что верифицировать (3) — форма пустого значения несобытийных полей**
  (N3 `DOCS_CHECK_18`; **гейтит**, предусловие п. 14). Какую форму отдаёт
  источник для `liqPenalty` / `fundingFee` **у записи, где события не
  было**: пустую строку `""`, строковый ноль `"0"` или отсутствие ключа
  в объекте.
  - **Ожидание (проверяется):** строковый `"0"`.
  - **Зачем гейт.** Конвенция «пусто = 0 для несобытийного поля»
    применяется **до** проверки обязательности контракта записи
    (`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`
    §Конвертация); при ложности ожидания валидация границы бросила бы
    `ExternalInvariantViolationException` на **каждой** нормально
    закрывшейся сделке — то есть биржевую ступень 2 с flatten
    (`docs/rules/exchange-hold.md`).
  - **Отдельной фикстуры не требует** — та же §AG1.5: у обычной закрытой
    позиции без ликвидации `liqPenalty` несобытиен по построению, а без
    пересечения funding-границы несобытиен и `fundingFee`. Проверка
    читает **тот же** ответ, что и (1)/(2).
  - **Прежде гейт носителя не имел** (N3): предусловие п. 14 объявляло
    гейтом `AG1.7`, а кейс такой проверки не содержал.
- **Почему знаки (2) гейта не образуют** (решение держателя
  `GAPS_CLOSE_16`, статус снят): цена ошибки названа — четвёртая пара
  сравнивает `externalLiquidationPenalty` **без отрицания**, и если штраф
  приходит положительной величиной, Δ₄ = 2·|штраф| на **каждой**
  ликвидации ⇒ `MISMATCHED` детерминированно на левом хвосте. Но правка по
  факту прогона — **наличие отрицания в паре**, то есть реализация; место
  приведения и доменная конвенция не меняются, поэтому `CODE` ответа не
  ждёт. Горизонт `fundingFee` (1) гейтит по-прежнему — там при ошибке
  третья пара неверна **по построению**.
- **Фикстура — та же §AG1.5**, дополнительно нужна позиция, пережившая
  **хотя бы один** funding-расчёт (удержание через границу funding-интервала).
  Для знаков (2) нужна **ликвидированная** позиция с ненулевым
  `liqPenalty` — та же фикстура, что у §AG1 по ADL-следу (предусловие
  `CODE` п. 10).
- **Статус:** ⏳ **PENDING**. Провенанс — H20 `DOCS_CHECK_11`; расширение
  состава — H15 `DOCS_CHECK_16`.

### AG1.8 Содержательный (шаг 7, H27) — след автоделевериджа в bills ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловие п. 10)

Заведён решением держателя П17 валидации `GAPS_CLOSE_17` (H27
`DOCS_CHECK_17`, варианты A + B). Предусловие `CODE` п. 10 объявлено
гейтящим, а носителя проверки у него не существовало — ссылка на `AG1.7`
была битой: тот кейс проверяет знаки и горизонт, а не след ADL.

- **Что верифицировать:** порождает ли закрытие с `type ∈ {5,6}` (ADL,
  принудительное сокращение) записи категории `LIQ_PENALTY` в bills — то
  есть остаётся ли у **частичного** принудительного эпизода счётный след.
- **Ожидание (проверяется):** ADL закрывает позицию против контрагента, а
  не через страховой фонд, поэтому ликвидационный штраф в нём **не
  обязателен**. При нулевом `liqPenalty` и отсутствии bill-записи штрафа
  частичный эпизод не оставляет следа вовсе, а признаки отбора остаются
  чистыми.
- **Направление ущерба при ложности посылки — правый хвост:** ADL выбирает
  прибыльные позиции с высоким плечом ⇒ систематическое **занижение**
  ожидаемости. Корпус ADL объявляет тему пробелом и достраивать запрещает.
- **Фикстура — та же §AG1.5.** Дополнительно нужен эпизод ADL, а он
  **инициируется биржей** и на demo не заказывается.
- **Условие закрытия названо сразу** (вторая половина решения П17):
  кейс закрывается исходом `PASSED`/`FAILED` при наблюдении эпизода **либо**
  исходом **`OBSERVED_ABSENT`** — «прогнан вместе с §AG1.5, ADL за время
  прогона не наступил». Третий исход **закрывает гейт** и обязывает
  записать допущение явно в п. 10 реестра предусловий
  (§«Исходы содержательного кейса»). Без этой половины кейс мог бы остаться
  `PENDING` бессрочно и держать `CODE`.
- **Статус:** ⏳ **PENDING — до `CODE` шага 7** (гоняется вместе с §AG1.5).
  Провенанс — H13 `DOCS_CHECK_15` (статус гейта), H27 `DOCS_CHECK_17`
  (отсутствие носителя), решение держателя П17.

### AG1.9 Содержательный (шаг 7, T3) — окно с несколькими записями = эпизоды сделки ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловия пп. 1 и 15)

Заведён решением держателя о многоэпизодной сделке
(`docs/models/domain/aggregate/Deal.md`, T3 `DOCS_CHECK_18`). Инвариант
агрегации переформулирован с «одна сделка ↔ один `posId`» на «**один
эпизод** ↔ один `posId`», и вторая половина инварианта — поведение
источника, когда эпизодов несколько, — проверки не имела.

- **Что верифицировать:** отдаёт ли `positions-history` по временному
  окну инструмента **отдельную финализированную запись на каждый
  `posId`**, и не схлопывает ли источник эпизоды в одну запись.
  Дополнительно: получает ли переоткрытая позиция **новый** `posId`
  (а не переиспользованный старый) в пределах одного окна.
- **Ожидание (проверяется):** записей столько, сколько эпизодов; каждая
  адресуема своим `posId`; `realizedPnl` каждой кумулятивен **внутри
  своего** эпизода; пагинация по `uTime` их не склеивает.
- **Цена ошибки — направленная.** Если источник отдаёт одну запись на
  окно, `Deal.resultProfit` как Σ по строкам недосчитает эпизоды, а
  четыре пары сверки будут сравнивать разные покрытия ⇒ `MISMATCHED`
  детерминированно на всей популяции многоэпизодных сделок.
- **Фикстура — расширение §AG1.5:** после полного закрытия позиции
  открыть по тому же инструменту вторую в пределах того же окна и
  закрыть её; запросить историю окном, покрывающим обе. Эпизод
  заказуем нами (в отличие от ADL), поэтому исход `OBSERVED_ABSENT`
  здесь **неприменим** — кейс закрывается наблюдением.
- **Что решает вторая половина кейса (B11 `DOCS_CHECK_20`).** Ответ
  «`posId` переиспользован» ломает **два** носителя сразу, и оба
  правятся одним ходом:
  - **дискриминатор смены эпизода** (`docs/lifecycles/Position.md`
    §«Смена эпизода») — слеп: «позиция снова ненулевая при том же
    `posId`» перестаёт означать «тот же эпизод». Замена — сравнение
    `cTime` записи либо наблюдение перехода `externalSize → 0 → >0`;
  - **ключ `uk_position_deal_external (deal_id, external_id)** —
    ловит **легитимный** второй эпизод как дубль, то есть даёт отказ
    вставки на штатной тропе. Замена — включить `cTime` в ключ либо
    снять его вовсе, оставив `uk_position_deal_live`.
  Ответ «новый `posId`» подтверждает действующую редакцию, и правок
  нет. Офдок вопрос не решает (`docs/integrations/okx/contracts/
  position.md` §«Идентификация записи»).
- **Статус:** ⏳ **PENDING — до `CODE` шага 7** (гоняется вместе с §AG1.5).
  Провенанс — T3 `DOCS_CHECK_18`, решение держателя (многоэпизодная
  сделка); вторая половина — B11 `DOCS_CHECK_20`, и она несёт
  **самостоятельное** предусловие `CODE` п. 15 (посылка «переоткрытая
  позиция получает новый `posId` внутри окна линковки» — дискриминатор
  смены эпизода и ключ `uk_position_deal_external`), а не расширяет п. 1;
  разметка заголовка поправлена N2 `DOCS_CHECK_22`.

## AG2. Account & position risk — GET /api/v5/account/account-position-risk (Account)

- **Объект:** OKX `GET /api/v5/account/account-position-risk`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (read-only).
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим** — единый временной срез балансов+позиций; на demo всегда отдаёт `data[0]` с `ts`, `balData[]` (даже без позиций `posData[]` может быть пуст).
- **Teardown:** не требуется.

### AG2.1 Прямой — единый временной срез

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/account-position-risk, query:{instType:SWAP}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0]` присутствует; `b.data[0].ts` непусто; `b.data[0].balData` — массив; `b.data[0].posData` — массив | Единый срез: `ts` + `balData[]` (`ccy`,`eq`,`disEq`) + `posData[]` (`instType`,`instId`,`mgnMode`,`posId`,`posSide`,`pos`,`notionalUsd`). На demo без позиций `posData=[]` валидно | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG2.2 Негатив — битое значение `instType` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/account-position-risk, query:{instType:BOGUS}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `instType` вне домена (SPOT/MARGIN/SWAP/FUTURES/OPTION). Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

## AG3. Bills 7d — GET /api/v5/account/bills (Account)

- **Объект:** OKX `GET /api/v5/account/bills`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (read-only).
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим** — отдаёт массив bill-записей за 7 дней; пустой массив валиден, если движений по счёту не было.
- **Teardown:** не требуется.

### AG3.1 Прямой — массив bills, пустой валиден (форма)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/bills, query:{instType:SWAP, ccy:USDT}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив; при непустом `data[0]` — `billId`,`type`,`subType`,`ts`,`balChg`,`ccy` присутствуют | Массив bill-записей ≤7д; на demo без движений `data=[]` валидно; при наличии — поля записи по contract | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG3.2 Негатив — фильтр `begin`/`end` вне окна (begin>end → пусто или реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/bills, query:{instType:SWAP, ccy:USDT, begin:9999999999999, end:1}, signed:true}` | HTTP 200; `b.code="0"` и `b.data=[]`, ЛИБО `b.code≠"0"` | Перевёрнутое окно (`begin`>`end`): либо пустой `data` (фильтр ничего не находит), либо реджект формата. Код реджекта — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=0 |

### AG3.3 Негатив — битое значение `type` (вне домена справочника)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/bills, query:{instType:SWAP, type:99999}, signed:true}` | HTTP 200; `b.code≠"0"` ЛИБО `b.code="0"` с `b.data=[]` | `type` вне справочника: реджект OKX либо пустой результат. Точный исход/код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=12 |

### AG3.4 Содержательный (шаг 7, H8 / RQ-4) — комиссии приходят в settle-ccy ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловие п. 9 — вторая проверка)

**Единственный наблюдатель инварианта settle-ccy**
(`docs/rules/trading-constraints.md` §«Валюта комиссии»: комиссии платятся
только в валюте расчёта; оплата сторонней валютой запрещена конфигурацией,
нарушение — аномалия). Форм-кейс AG3.1 проверяет присутствие `ccy`; здесь —
**его значение** на реальных fee-записях.

- **Утверждение (проверяется, не предполагается):** комиссии SWAP приходят в
  settle-ccy — у fee-bills (`subType` торговой комиссии) `ccy="USDT"`.
- **Проактивного API-поля нет — только постфактум-детект.** В офдоке v5 нет
  настройки «платить комиссию в OKB» (скидка за OKB конфигурируется вне API);
  `feeType` — **Spot-only** и на SWAP не применим. Спросить у API «в какой
  валюте будут комиссии» **нечем** ⇒ инвариант наблюдаем **только** по факту —
  инспекцией `ccy` на пришедших bills.
- **Фикстура — та же §AG1.5** (закрытая позиция + bills за окно), лишнего
  прогона не создаёт.
- **Проверка:** на bills фикстуры отобрать fee-записи → у каждой `ccy="USDT"`.
  Любое `ccy` ≠ settle-ccy — находка (конфигурация сбита / площадка сменила
  поведение), не рабочий режим.
- **Вторая проверка — состав `realizedPnl` при cross-ccy-издержке** (H13
  `DOCS_CHECK_10`, расширение вопросника). Cross-ccy-слагаемое
  **прибавляется** к биржевому net на посылке «`realizedPnl` издержку вне
  settle-ccy **не содержит**» — посылка о поведении **источника**, до сих
  пор записанная как факт. Если она ложна, слагаемое считается **дважды**
  и число занижается.
  - **Что инспектируется:** на сделке, у которой в окне есть движение
    чужой `ccy`, сверить `realizedPnl` записи positions-history с суммой
    settle-ccy-движений bills. Совпало ⇒ посылка верна (издержки вне
    settle-ccy в net нет). Разошлось на величину чужого движения ⇒ посылка
    ложна, и формула числа обязана поменяться.
  - **Фикстура:** та же §AG1.5; отдельного прогона нет. Ветка редкая
    (нарушение инварианта), поэтому кейс исполняется **при наличии**
    cross-ccy-движения в окне; отсутствие такого движения — не провал
    кейса, а «не наблюдалось».
  - **Провенанс посылки — `предположение`** до этого ответа
    (`docs/rules/pnl-reconciliation.md` реш.5), по образцу
    инварианта агрегации N11.
- **Статус:** ⏳ **PENDING — до `CODE` шага 7** (гоняется вместе с §AG1.5; чистого прогона концепции не ждёт — единственный блокер `грунт`, `.claude/processes/roadmap-step-execution.md` §4). Провенанс — H8 отчёта
  `phase-1-step-7-gaps-close-3.md`; вторая проверка — H13
  `phase-1-step-7-docs-check-10.md`.

### AG3.5 Содержательный (шаг 7, H2-рантайм) — гранулярность trade-bill ⏳ PENDING

**Определяет, какая ветка разбивки `DealCashFlow` живая** (не корректность
заголовочного числа — оно net'ом из positions-history). Форм-кейсы AG3.1-3.3
проверяют структуру записи; здесь — **сколько записей на торговое событие** и
что в них лежит.

- **Утверждение (проверяется, не предполагается):** OKX эмитит на закрытие
  позиции **отдельную** fee-запись и **отдельную** pnl-запись, а не **одну
  комбинированную** (`balChg` = pnl + fee).
- **Почему не гейтит доки:** маппинг взят суперсетом — native `fee` в used,
  категории резолвятся по `type`/`subType`, `balChg` остаётся под
  сумму-сверку (`docs/models/mapping/DealCashFlow.md` §«Разделение ролей
  `balChg` и `fee`»). **Claim суперсета ограничен** (H15 `GAPS_CLOSE_5`,
  дотянуто до плана H11 `GAPS_CLOSE_6`): гранулярность-независимы
  **Σ`amount`** и **realized-слагаемое**; для **Σ`externalFee`** —
  **не держится**. Поэтому ответ меняет не маппинг, а то, какая ветка
  разбивки фактически работает и сходится ли число комиссии.
- **Расширение вопросника (H10, `GAPS_CLOSE_6`) — инспекция `fee` на
  pnl-записи.** Помимо «сколько записей на событие» проверить, **не несёт ли
  pnl-запись информационное эхо** комиссии в поле `fee` при раздельной
  гранулярности (`balChg` = pnl без комиссии, но `fee = −f` присутствует).
  Эхо задваивает `f` в Σ`externalFee` и даёт `realized = pnl + f` вместо
  `pnl`, при том что Σ`amount` сходится — **сверка этого не видит**.
  Записать факт по обоим полям обеих записей, а не только по их количеству.
- **Фикстура — та же §AG1.5** (закрытая позиция + bills за окно), лишнего
  прогона не создаёт.
- **Проверка:** на bills фикстуры для одного торгового события сопоставить
  записи → комбинированная (одна запись, `fee` ≠ 0 и `balChg` ≠ `fee`) vs
  раздельные (fee-запись с `balChg` = `fee`, отдельная pnl-запись). Зафиксировать
  факт в `docs/models/integrations/okx/OkxAccountBillResponse.md` (провенанс
  `рантайм`, `.claude/rules/external-source-sync.md`).
- **Статус:** ⏳ **PENDING — до `CODE` шага 7** (гоняется вместе с §AG1.5; чистого прогона концепции не ждёт — единственный блокер `грунт`, `.claude/processes/roadmap-step-execution.md` §4). Провенанс — H2 отчёта
  `phase-1-step-7-docs-check-3.md`.

### AG3.6 Содержательный (шаг 7, B10 `DOCS_CHECK_20`) — фактический состав полей bill-записи ⏳ PENDING

**Что проверяется:** какие поля bill-записи **фактически** приходят в JSON
`/account/bills` и `/account/bills-archive`. Инвентарь
`docs/models/integrations/okx/OkxAccountBillResponse.md` объявляет
«сужение до used зафиксировано», но собственный контракт-док источника
(`docs/integrations/okx/contracts/account-bills.md` §«Deep-архив»,
состав колонок CSV) называет поля, которых нет ни в used-, ни в
unused-перечне: `px`, `execType`, `interest`, `tag`, `fillTime`,
`tradeId`, `clOrdId`, `fill*`-семейство. Клейм полноты перечня тем самым
не обеспечен.

- **Почему это грунт, а не проектирование.** Ни одно из этих полей не
  назначено потребителю; вопрос ровно один — присутствуют ли они в
  JSON-ответе (CSV deep-архива и JSON могут различаться составом).
  Отсюда закрытие — наблюдение, не решение.
- **Фикстура — та же §AG1.5/§AG3.4/§AG3.5**, лишнего прогона не создаёт:
  на непустом `data` перечислить **все** ключи первой записи каждого
  наблюдаемого `type`.
- **Проверка:** множество ключей записи ⊇ used-перечень; разница между
  фактическим множеством и объединением used+unused инвентаря —
  наблюдение, каждое новое поле классифицируется в used/unused с
  доводом.
- **Куда записывается:** `OkxAccountBillResponse.md` (провенанс
  `рантайм`, `.claude/rules/external-source-sync.md`); при расхождении с
  офдоком — пометка в шапке §«Внешний источник правды».
- **Статус:** ⏳ **PENDING — не ждёт чистого прогона концепции**
  (единственный блокер — `грунт`,
  `.claude/processes/roadmap-step-execution.md` §4). **Гейтом `CODE` не
  является:** ни одно из полей не назначено потребителю, суперсет
  used-набора под разбивку от их наличия не меняется.

## AG4. Bills archive 3m — GET /api/v5/account/bills-archive (Account)

- **Объект:** OKX `GET /api/v5/account/bills-archive`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (read-only).
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **частично достижим** — запрос проходит (HTTP 200, `code="0"`), но на свежем demo окно 3м пусто (нет накопленной истории). Проверяем форму массива, не значения. Содержательная строка архива — гейт достижимости (нет данных на свежем demo).
- **Teardown:** не требуется.

### AG4.1 Прямой — окно 3м, пустой массив валиден (форма)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/bills-archive, query:{instType:SWAP, ccy:USDT}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив; при непустом — `billId`,`type`,`ts` присутствуют | Запрос принят; на свежем demo `data=[]` валидно (3м-история не накоплена). Форма записи как у bills 7d. Пустой `data` — не ошибка | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG4.2 Негатив — пагинация `after` по `billId` вне окна (after=1 → пусто)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/bills-archive, query:{instType:SWAP, after:1, limit:10}, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив (ожидается пустой) | Пагинация по `billId`: `after=1` отрезает все записи с `billId>1` → `data=[]`. Пустой результат валиден. Реджект формата `after` — код в наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=0 |

### AG4.3 Негатив — битое значение `instType` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/bills-archive, query:{instType:BOGUS}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `instType` вне домена. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

## AG5. Bills deep-архив — POST+GET /api/v5/account/bills-history-archive (Account)

- **Объект:** OKX `POST /api/v5/account/bills-history-archive` (заявка) + `GET …` (получение), `signed:true`. Через `/raw`.
- **Предусловие:** unified account; глубокий архив с 2021 поквартально; генерация async-файла (~2 ч, при пике до 3 ч); лимит заявки **12 req/сутки**.
- **Среда:** demo/non-prod.
- **Достижимость:** прямой содержательный (готовый `fileHref`) **НЕдостижим** на свежем demo — файл генерится async-флоу, на demo за прошлый квартал данных нет; жёсткий лимит 12 заявок/сутки делает прогон в контуре опасным (исчерпание квоты). Покрытие: POST-заявка с пометкой (ACK заявки), GET в состоянии `ongoing`/пусто, негативы. Содержательный `finished`-файл — гейт достижимости (отказ + причина: async + demo без истории + квота).
- **Прямой/success-кейс — prod-only, вне контура.** На demo success-контракт неверифицируем (валидная заявка всегда `50026`, GET → `51604` — demo не инициирует архив). Зелёный AG5 в контуре подтверждает **только demo-реджект** и негативы конверта, **не** success-контракт (`result`-ACK → `fileHref`/`state=finished`). Success проверяется **ад-хок на проде, вне контура source-api** — `prod вне контура` (§Среда) не нарушается: контур prod-кейсов не несёт.
- **Teardown:** не требуется (read-only флоу; заявка не изменяет торговое состояние).

### AG5.1 Прямой POST-заявка — ACK заявки (помечен, расходует квоту 12/сутки)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/account/bills-history-archive, body:{year:"2025", quarter:"Q1"}, signed:true}` | HTTP 200; один из валидных исходов: ACK `b.code="0"` (`data[0].result`∈{true,false}, `ts`); demo system-error `b.code="50026"`; rate-limit/квота (`b.code`∈rate-limit-кодах, напр. `50011`) | Эндпоинт достижим (HTTP 200 + структурный конверт). Валидны все три: ACK заявки; demo-ошибка 50026 (нет истории квартала); rate-limit/исчерпание квоты 12/сутки. **RUN: расходует квоту — запускать осознанно, не в цикле.** Content на demo недостижим — наблюдение | RUN 2026-06-20 ✓ — b.code=50011 (Too Many Requests): суточная квота (12/сутки) исчерпана прогонами. Тест пересмотрен — rate-limit/квота принимается как валидный предусмотренный исход (`isRateLimited`), не фейл. Перевалидировано: AG5 4/4 green (`validate-ag5-02.log`) |

### AG5.2 Прямой GET — получение файла (ожидается ongoing/нет данных)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/bills-history-archive, query:{year:"2025", quarter:"Q1"}, signed:true}` | HTTP 200; `b.code="0"`; при непустом `data[0]` — `state` ∈ {finished,ongoing,failed}, при `finished` — `fileHref` непусто | На свежем demo `state=ongoing` (генерится) или пустой `data` — `finished`-файл недостижим. `fileHref` содержательно НЕ проверяем (гейт достижимости). Реальный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51604 (Initiate a download request before obtaining the hyperlink), data.size=0 |

### AG5.3 Негатив — битое значение `quarter` (вне домена Q1..Q4)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/account/bills-history-archive, body:{year:"2025", quarter:"Q9"}, signed:true}` | HTTP 200; реджект OKX по домену `quarter` (`b.code≠"0"`), **если не rate-limit**; rate-limit/квота (`50011`) маскирует реджект → кейс пропускается (skip) | Реджект OKX — `quarter` вне домена (Q1..Q4). Точный код — наблюдение. При исчерпании квоты 12/сутки rate-limit реджектит ДО валидации → валидация quarter не достигается, кейс пропускается (не ложный pass) | RUN 2026-06-20 ⏭ SKIP — b.code=50011 (Too Many Requests): квота исчерпана, реджект-по-quarter не достигнут (маскирован rate-limit). Тест пересмотрен (`assumeFalse(isRateLimited)`): валидацию quarter перепроверить вне исчерпания квоты |

### AG5.4 Негатив — пропуск обязательного `quarter` (POST)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/account/bills-history-archive, body:{year:"2025"}, signed:true}` | HTTP 200; реджект OKX за пропуск обязательного `quarter` (`b.code≠"0"`), **если не rate-limit**; rate-limit/квота (`50011`) маскирует реджект → кейс пропускается (skip) | Реджект OKX — пропущен обязательный `quarter`. Под /raw нет passthrough-гарда → уходит на OKX. При исчерпании квоты rate-limit реджектит ДО проверки → валидация не достигается, кейс пропускается (не ложный pass) | RUN 2026-06-20 ⏭ SKIP — b.code=50011 (Too Many Requests): квота исчерпана, реджект-за-пропуск не достигнут (маскирован rate-limit). Тест пересмотрен (`assumeFalse(isRateLimited)`): перепроверить вне исчерпания квоты |

## AG6. Bill types — GET /api/v5/account/subtypes (Account)

- **Объект:** OKX `GET /api/v5/account/subtypes`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (справочник).
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим** — справочник `type`/`subType` bill-записей, всегда отдаёт перечень.
- **Teardown:** не требуется.

### AG6.1 Прямой — справочник типов bills ⚠️ ПЕРЕПРОГОН — **ГЕЙТИТ `CODE`** (предусловия пп. 2 и 16: наблюдение сохранено мощностью, не перечнем)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/subtypes, signed:true}` | HTTP 200; `b.code="0"`; `b.data` — массив непустой; `b.data[0]` несёт пары type/subType | Справочник bill types: непустой перечень. Включает funding-подтипы `173`(expense)/`174`(income). Точная форма элемента — наблюдение (сверка с офдоком при RUN). **Наблюдение записывается перечнем, а не мощностью** — см. клаузу ниже | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=32 ⚠️ **содержание не сохранено** (N5 `DOCS_CHECK_22`) — требуется перепрогон |

**Исход кейса — перечень, а не счёт** (N5 `DOCS_CHECK_22`). Прогон
2026-06-20 состоялся и вернул 32 значения, но в отчёт кейса ушла **только
мощность множества**: сами `type`/`subType` и их описания не сохранены ни
здесь, ни в контракт-доке, ни в маппинге. Это тот же дефект, который
проект уже назвал в другом месте — «факт durable, а не память прохода»:
прогон **произвёл** факт и оставил о нём счёт.

- **Почему это несущее.** Перечень — недостающий операнд **двух**
  гейтящих предусловий `CODE`: п. 16 (отображение `type`/`subType` →
  `CashFlowCategory`, `docs/models/mapping/DealCashFlow.md` §«Резолв
  категории») и п. 2 (список исключений, §AG6.2 ниже). Без содержания оба
  «грунт добыт» ложны.
- **Что делать при перепрогоне:** сохранить **все** пары `type`/`subType`
  с описанием источника — в исход кейса (датированное наблюдение).
  Действующее рабочее значение при этом живёт **не здесь**, а в
  per-exchange конфиге (`docs/models/mapping/DealCashFlow.md`): план
  фиксирует, что наблюдали и когда; конфиг — что действует. Разведение
  намеренное, то же, что уже принято для списка исключений.
- **Куда попадает разница.** Если при перепрогоне состав справочника
  разошёлся с наблюдением 2026-06-20 — расхождение фиксируется здесь же
  как рантайм-факт (`.claude/rules/external-source-sync.md`
  §«Рантайм-расхождение»).

### AG6.2 Содержательный (шаг 7, H10) — перечень типов вне экономики сделки ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловие п. 2)

**Наполняет список исключений сверки для OKX**, непустота которого —
**предусловие `CODE`** (H10 `DOCS_CHECK_11`, решение пользователя;
`docs/models/mapping/DealCashFlow.md` §«Область сверки задаётся списком
исключений по бирже»). Пустой список означает «всё входит в сверку» —
то есть контроль целостности числа отгружается погашенным, а на нём
стоит вся R-выборка.

- **Что верифицировать:** какие `type`/`subType` из справочника (32
  значения по RUN 2026-06-20) **не принадлежат экономике сделки** —
  переводы между счетами, добавление/снятие маржи, конвертации и т. п.
  Отдельно: **появляются ли маржинальные движения по позиции в окне
  сделки** при isolated-марже на SWAP — они несут `instId` и сопоставимы
  по величине с самой позицией, то есть раздували бы Σ|amount| и
  композиционный член epsilon.
- **Находка держится в обе стороны:** если маржинальные движения в окне
  **есть** — контроль был бы мёртв с первого дня; если их **нет** —
  ложна посылка, на которой построена машинерия списка исключений.
  Прогон разрешает, какая ветка истинна; пустой список не разрешает.
- **Фикстура — та же §AG1.5** (закрытая позиция + bills за окно):
  инспектировать фактический набор `type`/`subType`, попавших в окно,
  против справочника. Отдельного прогона не требует.
- **Ожидание (проверяется, не предполагается):** перечень исключаемых
  типов **непуст** и зафиксирован в конфиге исключений (дом конфига —
  отдельный хэнд-офф `integrator`, `.claude/work/backlog.md` §Шаг 7).
- **Статус:** ⏳ **PENDING**. Провенанс — H10 `DOCS_CHECK_11`.

## AG7. Set position mode — POST /api/v5/account/set-position-mode (Account)

- **Объект:** OKX `POST /api/v5/account/set-position-mode`, `signed:true`. Через `/raw`. **WRITE (настройка `posMode`), реверсивно.**
- **Предусловие:** смена режима требует **отсутствия открытых позиций/ордеров** по любому инструменту (изолированно, аккаунт пустой). Инвариант восстановления: снимок `posMode` → переключить на другой режим → restore → Verify.end.
- **Среда:** demo/non-prod. **Stateful (настройка `posMode`).**
- **Достижимость:** прямой **достижим реверсивно** — read → switch → restore → verify==старт. Если demo реджектит переключение (позиции/ордера/не разрешено) — **находка** (реальное поведение + флаг остаточного `posMode`), смену не форсируем сверх.
- **Teardown:** restore `posMode` к исходному + Verify.end (`posMode` == старт).

### AG7.1 Прямой (цепочка) — Snapshot.start → switch → restore → Verify.end

Граф: config(posMode→`cur_pos_mode`) → set-position-mode(другой режим) → set-position-mode(`cur_pos_mode`) → config(verify posMode==старт). **Изолированно** (нет позиций/ордеров). RUN: поллинг до условия.

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **AG7.snapshot.** `POST /raw {method:GET, path:/api/v5/account/config, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].posMode` ∈ {net_mode, long_short_mode} → `cur_pos_mode` | **Snapshot.start:** снимок исходного `posMode` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |
| **AG7.switch.** `POST /raw {method:POST, path:/api/v5/account/set-position-mode, body:{posMode:"<другой режим>"}, signed:true}` (long_short_mode если cur=net_mode, иначе net_mode) | HTTP 200; `b.code="0"`; `b.data[0].posMode` = другой режим (эхо) **или** реджект | Реальная смена режима. Реджект (позиции/ордера/не разрешено) → **находка**, флаг остаточного `posMode` | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |
| **AG7.restore.** `POST /raw {method:POST, path:/api/v5/account/set-position-mode, body:{posMode:"{{cur_pos_mode}}"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].posMode` = `cur_pos_mode` (эхо) | Восстановить исходный `posMode`. Невозврат → **находка** + флаг остаточного состояния | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |
| **AG7.verify.** `POST /raw {method:GET, path:/api/v5/account/config, signed:true}` (поллинг) | HTTP 200; `b.data[0].posMode == cur_pos_mode` | **Verify.end:** `posMode` восстановлен. Расхождение → фейл (инвариант) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG7.2 Негатив — битое значение `posMode` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/account/set-position-mode, body:{posMode:"bogus_mode"}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `posMode` вне домена (`net_mode`/`long_short_mode`). Состояние не меняется. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter posMode error), data.size=0 |

## AG8. Set leverage — POST /api/v5/account/set-leverage (Account)

- **Объект:** OKX `POST /api/v5/account/set-leverage`, `signed:true`. Через `/raw`. **WRITE, реверсивно.**
- **Предусловие:** инструмент `ETH-USDT-SWAP`, isolated/net (адаптер-инвариант). Прочитать текущий leverage через `GET /account/leverage-info`, установить то же/малое значение, teardown — вернуть прежнее.
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим и реверсивен** — read → set → restore. Без открытой позиции leverage инструмента меняется свободно.
- **Teardown:** обязателен — вернуть `lever` к прочитанному исходному значению вторым `set-leverage`. После кейса плечо инструмента = исходному.

### AG8.1 Прямой (цепочка) — read leverage → set → restore (реверсивно)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| **AG8.snapshot.** `POST /raw {method:GET, path:/api/v5/account/leverage-info, query:{instId:ETH-USDT-SWAP, mgnMode:isolated}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].lever` непусто → сохранить в `prev_lever` | Прочитать текущий leverage инструмента (isolated) | RUN 2026-06-20 ✓ — `[AG8.snapshot]` http 200, b.code=0, data.size=1; `lever` прочитан → `prev_lever` |
| **AG8.set.** `POST /raw {method:POST, path:/api/v5/account/set-leverage, body:{instId:"ETH-USDT-SWAP", lever:"3", mgnMode:"isolated"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].lever="3"`; `b.data[0].mgnMode="isolated"`; `b.data[0].instId="ETH-USDT-SWAP"` (эхо) | Set малого значения `lever=3`: эхо параметров. **RUN: WRITE — реверсивно, teardown ниже.** | RUN 2026-06-20 ✓ — `[AG8.set]` http 200, b.code=0, data.size=1; эхо `lever=3`/`mgnMode=isolated`/`instId` подтверждено (ассерты зелёные) |
| **AG8.restore.** `POST /raw {method:POST, path:/api/v5/account/set-leverage, body:{instId:"ETH-USDT-SWAP", lever:"{{prev_lever}}", mgnMode:"isolated"}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].lever` = `prev_lever` (эхо) | Восстановить исходный leverage. Невозврат → находка + флаг остаточного состояния | RUN 2026-06-20 ✓ — `[AG8.restore]` http 200, b.code=0, data.size=1; leverage возвращён к `prev_lever` (реджекта нет) |
| **AG8.verify.** `POST /raw {method:GET, path:/api/v5/account/leverage-info, query:{instId:ETH-USDT-SWAP, mgnMode:isolated}, signed:true}` (поллинг) | HTTP 200; `b.data[0].lever == prev_lever` | **Verify.end:** плечо восстановлено к исходному. Расхождение → фейл (инвариант восстановления состояния) | RUN 2026-06-20 ✓ — `[AG8.verify]` http 200, b.code=0, data.size=1; Verify.end: `lever == prev_lever` — восстановлено, без находки/halt |

### AG8.2 Негатив — битое значение `lever` (нечисловое)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/account/set-leverage, body:{instId:"ETH-USDT-SWAP", lever:"abc", mgnMode:"isolated"}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `lever` нечисловой/вне домена. Состояние не меняется. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter lever error), data.size=0 |

### AG8.3 Негатив — битое значение `mgnMode` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:POST, path:/api/v5/account/set-leverage, body:{instId:"ETH-USDT-SWAP", lever:"3", mgnMode:"bogus"}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `mgnMode` вне домена (`isolated`/`cross`). Состояние не меняется. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter mgnMode error), data.size=0 |

## AG9. Leverage info — GET /api/v5/account/leverage-info (Account)

- **Объект:** OKX `GET /api/v5/account/leverage-info`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (read-only); `mgnMode` обязателен.
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим** — отдаёт leverage инструмента при заданном `mgnMode`.
- **Teardown:** не требуется.

### AG9.1 Прямой — leverage инструмента (isolated)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/leverage-info, query:{instId:ETH-USDT-SWAP, mgnMode:isolated}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].mgnMode="isolated"`; `b.data[0].lever` непусто; `b.data[0].posSide` присутствует | Leverage-инфо: `instId`,`ccy`,`mgnMode`,`posSide`,`lever`. В net-режиме — одна запись | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG9.2 Негатив — пропуск обязательного `mgnMode`

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/leverage-info, query:{instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — пропущен обязательный `mgnMode`. Под /raw нет passthrough-гарда. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter mgnMode can not be empty.), data.size=0 |

### AG9.3 Негатив — битое значение `mgnMode` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/leverage-info, query:{instId:ETH-USDT-SWAP, mgnMode:bogus}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `mgnMode` вне домена. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter mgnMode error), data.size=0 |

## AG10. Max order size — GET /api/v5/account/max-size (Account)

- **Объект:** OKX `GET /api/v5/account/max-size`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (read-only); `instId` + `tdMode` обязательны.
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим** — серверная оценка `maxBuy`/`maxSell` (в контрактах для SWAP).
- **Teardown:** не требуется.

### AG10.1 Прямой — максимальный sz buy/sell

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/max-size, query:{instId:ETH-USDT-SWAP, tdMode:isolated}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].maxBuy` непусто; `b.data[0].maxSell` непусто | Серверный потолок `sz`: `maxBuy`/`maxSell` в контрактах (SWAP). `ccy` эхо | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG10.2 Негатив — пропуск обязательного `tdMode`

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/max-size, query:{instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — пропущен обязательный `tdMode`. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter tdMode can not be empty.), data.size=0 |

### AG10.3 Негатив — несуществующий `instId`

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/max-size, query:{instId:FOO-BAR-SWAP, tdMode:isolated}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `instId` не существует. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

## AG11. Max avail size — GET /api/v5/account/max-avail-size (Account)

- **Объект:** OKX `GET /api/v5/account/max-avail-size`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (read-only); `instId` + `tdMode` обязательны.
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим** — доступный баланс/эквити под сделку (`availBuy`/`availSell`).
- **Teardown:** не требуется.

### AG11.1 Прямой — доступный баланс/эквити buy/sell

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/max-avail-size, query:{instId:ETH-USDT-SWAP, tdMode:isolated}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].availBuy` непусто; `b.data[0].availSell` непусто | Доступность средств: `availBuy`/`availSell`. Семантика по contract (SPOT/MARGIN base/quote; cross MARGIN — в `ccy`) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### AG11.2 Негатив — пропуск обязательного `tdMode`

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/max-avail-size, query:{instId:ETH-USDT-SWAP}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — пропущен обязательный `tdMode`. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter tdMode can not be empty.), data.size=0 |

### AG11.3 Негатив — битое значение `tdMode` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/max-avail-size, query:{instId:ETH-USDT-SWAP, tdMode:bogus}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `tdMode` вне домена (`cross`/`isolated`/`cash`/`spot_isolated`). Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter tdMode error), data.size=0 |

## AG12. Fee rates — GET /api/v5/account/trade-fee (Account)

- **Объект:** OKX `GET /api/v5/account/trade-fee`, `signed:true`. Через `/raw`.
- **Предусловие:** нет (read-only); `instType` обязателен.
- **Среда:** demo/non-prod.
- **Достижимость:** прямой **достижим** — ставки комиссий аккаунта; знаковая конвенция критична (минус = комиссия, плюс = ребейт).
- **Teardown:** не требуется.

### AG12.1 Прямой — ставки комиссий + знаковая конвенция

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/trade-fee, query:{instType:SWAP, instFamily:ETH-USDT}, signed:true}` | HTTP 200; `b.code="0"`; `b.data[0].level` непусто; `b.data[0].maker` присутствует; `b.data[0].taker` присутствует; `b.data[0].feeGroup` — массив; `b.data[0].instType="SWAP"` (эхо) | Ставки: `level`, `feeGroup[]` (канонич. `maker`/`taker`/`elpMaker`), плоские `maker`/`taker` (deprecated). **Знак: отрицательный `taker` = комиссия** — наблюдать знак при RUN | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |

### AG12.2 Негатив — пропуск обязательного `instType`

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/trade-fee, query:{instFamily:ETH-USDT}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — пропущен обязательный `instType`. Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instType can not be empty.), data.size=0 |

### AG12.3 Негатив — битое значение `instType` (вне домена)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:GET, path:/api/v5/account/trade-fee, query:{instType:BOGUS}, signed:true}` | HTTP 200; `b.code≠"0"` (реджект OKX) | Реджект OKX — `instType` вне домена (SPOT/MARGIN/SWAP/FUTURES/OPTION). Точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

### AG12.4 Содержательный (шаг 7, H1 / RQ-1) — `feeGroup[]` покрывает все наши группы ⏳ PENDING

**Гейтит форму запроса** — выбранную ось «группа, не инструмент»: один вызов
`trade-fee(instType=SWAP)` на тик вместо N вызовов на N инструментов
(`docs/integrations/okx/contracts/trade-fee.md` §«Наша ось запроса»). Если ответ
покрывает не все группы наших инструментов — ось запроса не работает как задумано.

- **Утверждение (проверяется, не предполагается):** `GET /api/v5/account/trade-fee?instType=SWAP`
  **без** `instId`/`instFamily`/`groupId` возвращает `feeGroup[]`, покрывающий
  **все** `groupId`, встречающиеся у наших SWAP-инструментов.
- **Проверка:** множество `feeGroup[].groupId` из ответа **⊇** множество distinct
  `groupId` из `/public/instruments?instType=SWAP` (по нашим инструментам). Два
  read-запроса, фикстуры не требует. Зависит от RQ-2 (M1.7): при пустом `groupId`
  у инструментов сравнивать нечего.
- **Если ⊉** — запрос уходит **per-group** (≤K вызовов по `groupId`, K = число
  наших групп); в лимит 5 req / 2 s по User ID это всё равно укладывается, то
  есть исход — коррекция формы запроса, не блокер.
- **Оговорка оси:** запрос **без** `instId`/`instFamily` — намеренно (даёт organic
  base rates, инвариант organic-base-rates); добавлять их ради покрытия нельзя —
  ответ станет другим по смыслу.
- **Статус:** ⏳ **PENDING — до `CODE` шага 7**. Провенанс — H1 (N9 fee-wiring),
  `phase-1-step-7-gaps-close-3.md`.

### AG12.5 Содержательный (шаг 7, H1 / RQ-3) — ставка группы ↔ фактическая комиссия ⏳ PENDING

**Валидирует цепочку целиком** (инструмент → `groupId` → ставка `feeGroup` →
прогноз комиссии в сайзинге): AG12.1 проверяет, что ставка **приходит**, здесь —
что она **та самая**, по которой биржа реально списала.

- **Утверждение (проверяется, не предполагается):** ставка `feeGroup` для пары
  (`SWAP`, `groupId` инструмента) × notional ≈ фактическая комиссия (`fee`) в
  bills по этой же сделке.
- **Фикстура — та же §AG1.5** (закрытая позиция + bills за окно), лишнего
  прогона не создаёт.
- **Проверка:** взять `groupId` инструмента фикстуры (M1.7) → ставку
  `feeGroup[groupId].taker` (вход/выход по рынку — taker) → сверить
  `ставка × notional` с `fee` из fee-bills фикстуры (знак: минус = комиссия).
- **Что закрывает эмпирически** — оба непроверенных допущения H1 разом:
  (1) **применима ли группа** — та ли ставка резолвится по паре (`instType`,
  `groupId`); (2) **стабильна ли ставка в пределах сделки** — вход и выход
  посчитаны по одной ставке (допущение «ставка стабильна в пределах жизни
  сделки»: ставка висит на комиссионном уровне аккаунта, не на инструменте, то
  есть меняется по другой оси и на другом такте, чем справочник, к которому мы
  её крепим).
- **Известное расхождение (не фейл):** офдок — «The Open API will not reflect
  zero-fee trading»: при промо нулевой комиссии `trade-fee` отдаёт ненулевую
  ставку, а факт в bills будет нулевым. Расхождение в эту сторону — ожидаемое
  (прогноз консервативнее факта), не дефект цепочки.
- **Статус:** ⏳ **PENDING — до `CODE` шага 7** (гоняется вместе с §AG1.5; чистого прогона концепции не ждёт — единственный блокер `грунт`, `.claude/processes/roadmap-step-execution.md` §4); расхождение сверх округления → эскалация на
  `solution-designer`). Провенанс — H1 (N9 fee-wiring),
  `phase-1-step-7-gaps-close-3.md`.


## MG1. Tickers (плюрал) — GET /api/v5/market/tickers (Market Data)

- **Объект:** OKX `GET /api/v5/market/tickers`, `signed:false` (через
  `/raw`). **Предусловие:** нет. **Среда:** demo. **Достижимость:**
  прямой достижим (агрегатный read, `instType=SWAP`). **Teardown:** не
  требуется.
- **Форма ответа:** `OkxApiResponse<JsonNode>` — `data` = непустой
  массив объектов-тикеров (`instId`, `last`, `askPx`, `bidPx`, …).

### MG1.1 прямой — tickers(instType=SWAP)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/tickers, query:{instType:"SWAP"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0].instId` непустой; `b.data[0].last` — числовая строка | Срез всех SWAP-тикеров | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG1.2 негатив — пропуск обязательного instType (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/tickers, query:{}, signed:false}` (без `instType`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instType`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instType can not be empty.), data.size=0 |

### MG1.3 негатив — instType вне домена (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/tickers, query:{instType:"FOO"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой — нераспознанный `instType`; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

## MG2. Order book — GET /api/v5/market/books (Market Data)

- **Объект:** OKX `GET /api/v5/market/books`, `signed:false` (через
  `/raw`). **Предусловие:** нет. **Среда:** demo. **Достижимость:**
  прямой достижим. **Teardown:** не требуется.
- **Форма ответа:** `data[0]` — объект `{asks[], bids[], ts, seqId}`;
  уровень — массив строк `[px, sz, "0", numOrders]`.

### MG2.1 прямой — books(ETH-USDT-SWAP, sz=5)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books, query:{instId:"ETH-USDT-SWAP", sz:"5"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].asks` — массив; `b.data[0].bids` — массив; `b.data[0].asks[0]` — массив длиной 4; `b.data[0].asks[0][0]` — числовая строка (px); `b.data[0].ts` непустой | Стакан до 5 уровней на сторону | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG2.2 негатив — несущ. instId (OKX-реджект/пустой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books, query:{instId:"FOO-BAR"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой/пустой стакан | Реджект/пустой — несущ. инструмент; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID does not exist.), data.size=0 |

### MG2.3 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books, query:{}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### MG2.4 негатив — sz сверх лимита (sz>400)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books, query:{instId:"ETH-USDT-SWAP", sz:"5000"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data[0].asks` усечён ≤ 400 | Реджект/клампинг — `sz` сверх потолка 400; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter sz error.), data.size=0 |

## MG3. Order book full — GET /api/v5/market/books-full (Market Data)

- **Объект:** OKX `GET /api/v5/market/books-full`, `signed:false` (через
  `/raw`). **Предусловие:** нет. **Среда:** demo. **Достижимость:**
  прямой достижим. **Teardown:** не требуется.
- **Форма ответа:** `data[0]` — `{asks[], bids[], ts}` (без `seqId`);
  уровень — массив строк `[px, sz, numOrders]` (3 элемента).

### MG3.1 прямой — books-full(ETH-USDT-SWAP, sz=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books-full, query:{instId:"ETH-USDT-SWAP", sz:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].asks` — массив; `b.data[0].bids` — массив; `b.data[0].asks[0]` — массив длиной 3; `b.data[0].asks[0][0]` — числовая строка (px); `b.data[0].ts` непустой | Полный стакан до 10 уровней на сторону | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG3.2 негатив — несущ. instId (OKX-реджект/пустой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books-full, query:{instId:"FOO-BAR"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой/пустой стакан | Реджект/пустой — несущ. инструмент; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID does not exist.), data.size=0 |

### MG3.3 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books-full, query:{}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### MG3.4 негатив — sz сверх лимита (sz>5000)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/books-full, query:{instId:"ETH-USDT-SWAP", sz:"99999"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data[0].asks` усечён ≤ 5000 | Реджект/клампинг — `sz` сверх потолка 5000; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter sz error.), data.size=0 |

## MG4. Public trades — GET /api/v5/market/trades (Market Data)

- **Объект:** OKX `GET /api/v5/market/trades`, `signed:false` (через
  `/raw`). **Предусловие:** нет. **Среда:** demo. **Достижимость:**
  прямой достижим. **Teardown:** не требуется.
- **Форма ответа:** `data` — массив объектов-сделок (`tradeId`, `px`,
  `sz`, `side`, `source`, `ts`).

### MG4.1 прямой — trades(ETH-USDT-SWAP, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/trades, query:{instId:"ETH-USDT-SWAP", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0].tradeId` непустой; `b.data[0].px` — числовая строка; `b.data[0].side` ∈ {buy, sell} | Последние публичные сделки инструмента | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG4.2 негатив — несущ. instId (OKX-реджект/пустой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/trades, query:{instId:"FOO-BAR"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой — несущ. инструмент; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### MG4.3 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/trades, query:{}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### MG4.4 негатив — limit сверх лимита (limit>500)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/trades, query:{instId:"ETH-USDT-SWAP", limit:"9999"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` усечён ≤ 500 | Реджект/клампинг — `limit` сверх потолка 500; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=500 |

## MG5. Trades history — GET /api/v5/market/history-trades (Market Data)

- **Объект:** OKX `GET /api/v5/market/history-trades`, `signed:false`
  (через `/raw`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим (глубина 3 месяца, поля как у
  `trades`). **Teardown:** не требуется.
- **Форма ответа:** `data` — массив объектов-сделок (как у `trades`);
  пагинация по `tradeId` (`type=1`) или ts (`type=2`).

### MG5.1 прямой — history-trades(ETH-USDT-SWAP, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-trades, query:{instId:"ETH-USDT-SWAP", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0].tradeId` непустой; `b.data[0].ts` — числовая строка | Свежайшая страница исторических сделок | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG5.2 вариант — пагинация назад по after (tradeId)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-trades, query:{instId:"ETH-USDT-SWAP", type:"1", after:"<tradeId из MG5.1 data[last].tradeId>", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — массив сделок старше курсора | Следующая страница назад по `tradeId` | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=10 |

### MG5.3 негатив — несущ. instId (OKX-реджект/пустой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-trades, query:{instId:"FOO-BAR"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой — несущ. инструмент; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### MG5.4 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-trades, query:{}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

## MG6. Index tickers — GET /api/v5/market/index-tickers (Market Data)

- **Объект:** OKX `GET /api/v5/market/index-tickers`, `signed:false`
  (через `/raw`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим (`instId` — **индекс**, напр.
  `ETH-USDT`). **Teardown:** не требуется.
- **Форма ответа:** `data` — массив объектов индекса (`instId`,
  `idxPx`, `high24h`, `low24h`, `open24h`, `sodUtc0`, `sodUtc8`, `ts`).

### MG6.1 прямой — index-tickers(instId=ETH-USDT)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-tickers, query:{instId:"ETH-USDT"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0].instId="ETH-USDT"`; `b.data[0].idxPx` — числовая строка; `b.data[0].ts` непустой | Тикер индекса ETH-USDT | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG6.2 вариант — index-tickers по quoteCcy

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-tickers, query:{quoteCcy:"USDT"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0].idxPx` — числовая строка | Срез всех USDT-индексов | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG6.3 негатив — несущ. индекс instId (OKX-реджект/пустой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-tickers, query:{instId:"FOO-BAR"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой — несущ. индекс; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### MG6.4 негатив — пропуск обязательного фильтра (ни instId, ни quoteCcy)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-tickers, query:{}, signed:false}` (ни `instId`, ни `quoteCcy`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нужен один из `instId`/`quoteCcy`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50015 (Either parameter quoteCcy or instId is required), data.size=0 |

## MG7. Index candles — GET /api/v5/market/index-candles (Market Data)

- **Объект:** OKX `GET /api/v5/market/index-candles`, `signed:false`
  (через `/raw`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим (последние ≤ 1440 точек).
  **Teardown:** не требуется.
- **Форма ответа:** `data` — массив массивов-строк свечи индекса
  `[ts, o, h, l, c, confirm]` (6 элементов; без объёма).

### MG7.1 прямой — index-candles(ETH-USDT, 1m, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-candles, query:{instId:"ETH-USDT", bar:"1m", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0]` — массив длиной 6; `b.data[0][0]` — числовая строка (ts); `b.data[0][5]` ∈ {"0","1"} (confirm) | Свечи индекса 1m | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG7.2 негатив — bar вне домена (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-candles, query:{instId:"ETH-USDT", bar:"99z"}, signed:false}` | HTTP 200; `b.code≠"0"` | Реджект OKX (некорректный `bar`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter bar error), data.size=0 |

### MG7.3 негатив — несущ. индекс instId (OKX-реджект/пустой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-candles, query:{instId:"FOO-BAR", bar:"1m"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой — несущ. индекс; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### MG7.4 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/index-candles, query:{bar:"1m"}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### MG7.5 Содержательный (шаг 7, H25) — носитель курса cross-ccy ⏳ PENDING — **ГЕЙТИТ `CODE`** (предусловие п. 5)

**Назначает носитель курса пересчёта cross-ccy движений.** Решение H25
`DOCS_CHECK_11`: курс берётся **из свечи на момент операции**, секундного
разрешения при доступности, с деградацией к более грубому
(`docs/components/RefreshBillsExecutor.md` §«Носитель курса»). До
назначения эндпоинта пересчёт неисполним, и **все** cross-ccy строки
уходят в `rateStatus = RATE_UNAVAILABLE`.

- **Что верифицировать:**
  1. **доступность секундного разрешения** на нужных парах (`<CCY>-USDT`)
     — какие значения `bar` источник принимает, есть ли `1s`;
  2. **глубина хранения** каждого разрешения (сколько назад достаётся
     свеча на момент операции) — отсюда правило **деградации**: какой
     следующий интервал берётся, когда нужного нет;
  3. **какая цена берётся из свечи** — close интервала, содержащего
     момент, либо иная; от этого зависит воспроизводимость догона;
  4. **стоимость по квоте и группировка**: движений в окне может быть
     много, по-строчный запрос упирается в 5 req/s — достижимо ли брать
     диапазон одним запросом и разбирать локально;
  5. **доступность самих пар** при SWAP-only контуре — инструмент
     котировки в контур не входит; сравнить индексные свечи (`MG7`,
     `MG8`) с рыночными (`M3`, `M4`) по доступности и глубине.
- **Ожидание (проверяется, не предполагается):** индексная свеча
  доступна на нужных парах и предпочтительнее рыночной по устойчивости
  (агрегат площадок против одной), но метод клиента под неё
  **отсутствует** — это цена выбора.
- **Действие по итогу:** завести строку операции в
  `.claude/processes/api-docs-completion.md`, зафиксировать разрешение
  и правило деградации в `RefreshBillsExecutor.md` §«Носитель курса».
- **Статус:** ⏳ **PENDING**. Провенанс — H25 `DOCS_CHECK_11`.

## MG8. Index candles history — GET /api/v5/market/history-index-candles (Market Data)

- **Объект:** OKX `GET /api/v5/market/history-index-candles`,
  `signed:false` (через `/raw`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим (глубина за свежим окном, формат
  как у `index-candles`). **Teardown:** не требуется.
- **Форма ответа:** `data` — массив массивов-строк `[ts, o, h, l, c,
  confirm]` (6 элементов).

### MG8.1 прямой — history-index-candles(ETH-USDT, 1m, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-index-candles, query:{instId:"ETH-USDT", bar:"1m", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0]` — массив длиной 6; `b.data[0][0]` — числовая строка (ts); `b.data` упорядочен по ts убыв. | Свежайшая страница истории свечей индекса | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG8.2 вариант — пагинация назад по after

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-index-candles, query:{instId:"ETH-USDT", bar:"1m", after:"<ts из MG8.1 data[last][0]>", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — свечи строго старше `after` | Следующая страница назад (after — свечи старше ts) | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=10 |

### MG8.3 негатив — фильтр из будущего (вне окна)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-index-candles, query:{instId:"ETH-USDT", bar:"1m", after:"99999999999999"}, signed:false}` | HTTP 200; `b.data` пустой **или** наблюдение | Якорь из будущего — пустой/поведение фиксируем; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=100 |

### MG8.4 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-index-candles, query:{bar:"1m"}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

## MG9. Mark price candles — GET /api/v5/market/mark-price-candles (Market Data)

- **Объект:** OKX `GET /api/v5/market/mark-price-candles`,
  `signed:false` (через `/raw`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим (`instId` — **торговый
  инструмент**, последние ≤ 1440 точек). **Teardown:** не требуется.
- **Форма ответа:** `data` — массив массивов-строк `[ts, o, h, l, c,
  confirm]` (6 элементов; без объёма).

### MG9.1 прямой — mark-price-candles(ETH-USDT-SWAP, 1m, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/mark-price-candles, query:{instId:"ETH-USDT-SWAP", bar:"1m", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0]` — массив длиной 6; `b.data[0][0]` — числовая строка (ts); `b.data[0][5]` ∈ {"0","1"} (confirm) | Свечи mark price 1m | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG9.2 негатив — bar вне домена (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/mark-price-candles, query:{instId:"ETH-USDT-SWAP", bar:"99z"}, signed:false}` | HTTP 200; `b.code≠"0"` | Реджект OKX (некорректный `bar`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter bar error), data.size=0 |

### MG9.3 негатив — несущ. instId (OKX-реджект/пустой)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/mark-price-candles, query:{instId:"FOO-BAR", bar:"1m"}, signed:false}` | HTTP 200; `b.code≠"0"` **или** `b.data` пустой | Реджект/пустой — несущ. инструмент; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### MG9.4 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/mark-price-candles, query:{bar:"1m"}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### MG9.5 Содержательный (шаг 7, C1 `DOCS_CHECK_20` / N1 `DOCS_CHECK_21`) — базис `last` ↔ `mark` ⏳ PENDING — не гейтит

**Даёт величину базиса между ценовыми базами триггера защиты.** Guard
«ликвидация за стопом» сравнивает уровень стопа с ценой ликвидации, а
ликвидация у источника считается по `mark`; при
`triggerPriceType = LAST` порог обязан нести запас на базис, иначе
ликвидация достижима раньше стопа при формально выполненном инварианте
(`docs/components/RiskValidator.md`
§`STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION`,
`docs/rules/live-risk-protection.md` §«Ценовая база триггера
защиты объявляется стратегией и доезжает до биржи»). До наблюдения
величина не калибруется, поэтому `MARK` — **не рекомендация, а
единственная принимаемая база**: create отвергает
`triggerPriceType ∈ {LAST, INDEX}` реджектом
`STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK` (решение держателя на C5
`DOCS_CHECK_22`; прежняя формулировка «безопасный выбор автора
стратегии» держала посылку дисциплиной).

**Кейс стал условием снятия ограничения.** По его исходу назначается
запас на базис и `LAST`/`INDEX` возвращаются сервисной операцией — якорь
со стороны события: `.claude/work/backlog.md` §«Снятие ограничения
`MARK`-only — по измеренному базису». Гейтом `CODE` кейс от этого **не
становится**: ограничение `MARK`-only для `CODE` самодостаточно.

- **Что верифицировать:**
  1. **типичное расхождение** `last` ↔ `mark` на торгуемом инструменте:
     сопоставить одноимённые свечи `MG9.1`
     (`mark-price-candles`) и `M3`/`M4` (рыночные свечи) по общему `ts`,
     собрать распределение `|close_mark − close_last| / close_mark`;
  2. **хвостовое значение** того же отношения — на минутах с наибольшим
     ходом внутри выборки (интерес представляет именно хвост: базис
     расходится там, где стопы и срабатывают);
  3. **знак и устойчивость** расхождения: систематический сдвиг в одну
     сторону означал бы, что запас несимметричен по направлению сделки.
- **Ожидание (проверяется, не предполагается):** базис мал в спокойном
  рынке и растёт на импульсах; ни величина, ни знак заранее не
  утверждаются.
- **Действие по итогу:** записать наблюдённые значения в
  `docs/rules/live-risk-protection.md` §«Остаточный `грунт` —
  величина базиса» и, если запас нужен, назначить его там же.
- **Почему не гейтит:** при `triggerPriceType = MARK` вопрос не
  возникает вовсе, а выбор базы объявляет стратегия. Гейтом стало бы
  только требование поддержать `LAST` с калиброванным запасом.
- **Статус:** ⏳ **PENDING**. Провенанс — C1 `DOCS_CHECK_20` (принцип),
  N1 `DOCS_CHECK_21` (кейс заведён: прежде грунт был назначен на
  несуществующий кейс `§MG-базис`, и сбор был неисполним).

## MG10. Mark price candles history — GET /api/v5/market/history-mark-price-candles (Market Data)

- **Объект:** OKX `GET /api/v5/market/history-mark-price-candles`,
  `signed:false` (через `/raw`). **Предусловие:** нет. **Среда:** demo.
  **Достижимость:** прямой достижим (глубина за свежим окном, формат
  как у `mark-price-candles`). **Teardown:** не требуется.
- **Форма ответа:** `data` — массив массивов-строк `[ts, o, h, l, c,
  confirm]` (6 элементов).

### MG10.1 прямой — history-mark-price-candles(ETH-USDT-SWAP, 1m, limit=10)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-mark-price-candles, query:{instId:"ETH-USDT-SWAP", bar:"1m", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — непустой массив; `b.data[0]` — массив длиной 6; `b.data[0][0]` — числовая строка (ts); `b.data` упорядочен по ts убыв. | Свежайшая страница истории свечей mark price | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### MG10.2 вариант — пагинация назад по after

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-mark-price-candles, query:{instId:"ETH-USDT-SWAP", bar:"1m", after:"<ts из MG10.1 data[last][0]>", limit:"10"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` — свечи строго старше `after` | Следующая страница назад (after — свечи старше ts) | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=10 |

### MG10.3 негатив — фильтр из будущего (вне окна)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-mark-price-candles, query:{instId:"ETH-USDT-SWAP", bar:"1m", after:"99999999999999"}, signed:false}` | HTTP 200; `b.data` пустой **или** наблюдение | Якорь из будущего — пустой/поведение фиксируем; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=100 |

### MG10.4 негатив — пропуск обязательного instId (OKX-реджект)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {GET, /api/v5/market/history-mark-price-candles, query:{bar:"1m"}, signed:false}` (без `instId`) | HTTP 200; `b.code≠"0"` | Реджект OKX (нет обязательного `instId`); точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |


## PG1. Mark price — GET /api/v5/public/mark-price (signed:false)

- **Объект:** OKX `GET /api/v5/public/mark-price` через `/raw` (`signed:false`). Текущее значение mark price.
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим (публичные данные, инструмент `ETH-USDT-SWAP` живой на demo).
- **Teardown:** нет (read).

### PG1.1 Прямой — mark price по instType=SWAP + instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/mark-price", query:{instType:"SWAP", instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].instType="SWAP"`; `b.data[0].markPx` присутствует и парсится как число; `b.data[0].ts` присутствует | Возвращается mark price инструмента: `markPx`, `ts` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG1.2 Негатив — несуществующий instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/mark-price", query:{instType:"SWAP", instId:"NOPE-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) ИЛИ пустой `b.data` (`b.data.length=0`) | Несуществующий instId: реджект OKX либо пустой `data`; точный исход — наблюдение, если не документирован | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### PG1.3 Негатив — пропуск обязательного instType

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/mark-price", query:{instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск обязательного `instType` (под /raw нет passthrough-гарда) → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |

### PG1.4 Негатив — битое значение instType

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/mark-price", query:{instType:"WRONG", instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Значение `instType` вне домена → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

## PG2. Price limit — GET /api/v5/public/price-limit (signed:false)

- **Объект:** OKX `GET /api/v5/public/price-limit` через `/raw` (`signed:false`). Верхняя граница buy / нижняя граница sell.
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим.
- **Teardown:** нет (read).

### PG2.1 Прямой — лимиты цены по instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/price-limit", query:{instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; поля `b.data[0].buyLmt`, `b.data[0].sellLmt`, `b.data[0].enabled` присутствуют; `b.data[0].ts` присутствует | Возвращаются `buyLmt`/`sellLmt` (могут быть `""` при `enabled=false`), `enabled`, `ts` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG2.2 Негатив — несуществующий instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/price-limit", query:{instId:"NOPE-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) ИЛИ пустой `b.data` | Несуществующий instId: реджект либо пустой `data`; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### PG2.3 Негатив — пропуск обязательного instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/price-limit", query:{}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск обязательного `instId` → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

## PG3. Funding rate — GET /api/v5/public/funding-rate (signed:false)

- **Объект:** OKX `GET /api/v5/public/funding-rate` через `/raw` (`signed:false`). Прогнозная ставка ближайшего расчёта SWAP.
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим (SWAP-инструмент).
- **Teardown:** нет (read).

### PG3.1 Прямой — funding rate по instId SWAP

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/funding-rate", query:{instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].fundingRate` присутствует; `b.data[0].fundingTime` присутствует; `b.data[0].nextFundingTime` присутствует | Возвращается `fundingRate` + `fundingTime`/`nextFundingTime` (интервал расчёта — разница полей) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG3.2 Негатив — несуществующий instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/funding-rate", query:{instId:"NOPE-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) ИЛИ пустой `b.data` | Несуществующий instId: реджект либо пустой `data`; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### PG3.3 Негатив — пропуск обязательного instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/funding-rate", query:{}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск обязательного `instId` → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

## PG4. Funding rate history — GET /api/v5/public/funding-rate-history (signed:false)

- **Объект:** OKX `GET /api/v5/public/funding-rate-history` через `/raw` (`signed:false`). История ставок (глубина 3 мес), пагинация по окну `fundingTime`.
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим (свежее окно истории доступно на demo).
- **Teardown:** нет (read).

### PG4.1 Прямой — история funding по instId с limit

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/funding-rate-history", query:{instId:"ETH-USDT-SWAP", limit:"5"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` непустой и `b.data.length<=5`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].fundingTime`, `b.data[0].fundingRate`, `b.data[0].realizedRate` присутствуют | Возвращаются исторические записи: `fundingTime`, `fundingRate` (прогноз), `realizedRate` (факт) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG4.2 Прямой — пагинация по окну (before по fundingTime)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/funding-rate-history", query:{instId:"ETH-USDT-SWAP", before:"{{fundingTime из PG4.1 data[0]}}", limit:"5"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data.length<=5`; все `b.data[*].fundingTime` строго больше переданного `before` (более новое окно) | Пагинация по `before`/`fundingTime`: вернулось окно новее опорной метки; точная граница окна — наблюдение RUN | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG4.3 Негатив — пропуск обязательного instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/funding-rate-history", query:{limit:"5"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск обязательного `instId` → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instId can not be empty.), data.size=0 |

### PG4.4 Негатив — несуществующий instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/funding-rate-history", query:{instId:"NOPE-USDT-SWAP", limit:"5"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) ИЛИ пустой `b.data` | Несуществующий instId: реджект либо пустой `data`; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

## PG5. Open interest — GET /api/v5/public/open-interest (signed:false)

- **Объект:** OKX `GET /api/v5/public/open-interest` через `/raw` (`signed:false`). Открытый интерес контрактов.
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим (SWAP).
- **Teardown:** нет (read).

### PG5.1 Прямой — open interest по instType=SWAP + instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/open-interest", query:{instType:"SWAP", instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].instId="ETH-USDT-SWAP"`; `b.data[0].instType="SWAP"`; `b.data[0].oi`, `b.data[0].oiCcy`, `b.data[0].oiUsd` присутствуют; `b.data[0].ts` присутствует | Возвращается открытый интерес: `oi`/`oiCcy`/`oiUsd`, `ts` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG5.2 Негатив — несуществующий instId

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/open-interest", query:{instType:"SWAP", instId:"NOPE-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) ИЛИ пустой `b.data` | Несуществующий instId: реджект либо пустой `data`; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51001 (Instrument ID, Instrument ID code, or Spread ID doesn't exist.), data.size=0 |

### PG5.3 Негатив — пропуск обязательного instType

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/open-interest", query:{instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск обязательного `instType` → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |

### PG5.4 Негатив — битое значение instType

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/open-interest", query:{instType:"WRONG", instId:"ETH-USDT-SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Значение `instType` вне домена → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter instType error), data.size=0 |

## PG6. Position tiers — GET /api/v5/public/position-tiers (signed:false)

- **Объект:** OKX `GET /api/v5/public/position-tiers` через `/raw` (`signed:false`). ⚠ путь **PUBLIC**, не `account/` (находка прогона 3). Лимиты размера позиции, ставки маржи, max плечо по тирам.
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим (SWAP, instFamily `ETH-USDT`).
- **Teardown:** нет (read).

### PG6.1 Прямой — тиры по instType=SWAP + tdMode + instFamily

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/position-tiers", query:{instType:"SWAP", tdMode:"isolated", instFamily:"ETH-USDT"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data` непустой; `b.data[0].tier`, `b.data[0].minSz`, `b.data[0].maxSz`, `b.data[0].imr`, `b.data[0].mmr`, `b.data[0].maxLever` присутствуют | Возвращаются тиры маржи: границы размера, imr/mmr, maxLever (публичный путь подтверждается) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG6.2 Негатив — пропуск обязательного tdMode

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/position-tiers", query:{instType:"SWAP", instFamily:"ETH-USDT"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск обязательного `tdMode` → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter tdMode can not be empty.), data.size=0 |

### PG6.3 Негатив — пропуск обязательного instFamily (для SWAP)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/position-tiers", query:{instType:"SWAP", tdMode:"isolated"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск `instFamily` (обяз. для SWAP) → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50015 (Either parameter instFamily or uly is required), data.size=0 |

### PG6.4 Негатив — битое значение tdMode

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/position-tiers", query:{instType:"SWAP", tdMode:"WRONG", instFamily:"ETH-USDT"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Значение `tdMode` вне домена (`cross`/`isolated`) → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=51000 (Parameter tdMode error), data.size=0 |

## PG7. Server time — GET /api/v5/public/time (signed:false)

- **Объект:** OKX `GET /api/v5/public/time` через `/raw` (`signed:false`). Серверное время API, Unix-мс строкой. Параметров нет.
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим.
- **Teardown:** нет (read).

### PG7.1 Прямой — серверное время (без параметров)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/time", signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].ts` присутствует и парсится как целое число (Unix-мс строкой) | Возвращается серверное время `ts` | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG7.2 Негатив — битый путь (тривиальный)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/time-WRONG", signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) ИЛИ ошибка маршрутизации OKX | Несуществующий путь OKX → реджект; точный исход — наблюдение (эндпоинт без параметров, иного негатива нет) | RUN 2026-06-20 ✓ — http 500 |

## PG8. Insurance fund — GET /api/v5/public/insurance-fund (signed:false)

- **Объект:** OKX `GET /api/v5/public/insurance-fund` через `/raw` (`signed:false`). Баланс страхового фонда (офдок: «security fund»).
- **Предусловие:** нет (read, публичный).
- **Среда:** demo.
- **Достижимость:** прямой достижим (SWAP, instFamily `ETH-USDT`).
- **Teardown:** нет (read).

### PG8.1 Прямой — фонд по instType=SWAP + instFamily

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/insurance-fund", query:{instType:"SWAP", instFamily:"ETH-USDT"}, signed:false}` | HTTP 200; `b.code="0"`; `b.data[0].total` присутствует; `b.data[0].instType="SWAP"`; `b.data[0].details` присутствует (массив) | Возвращается `total` фонда + `details[]` (записи фонда) | RUN 2026-06-20 ✓ — прошёл (ожидание подтверждено) |

### PG8.2 Негатив — пропуск обязательного instType

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/insurance-fund", query:{instFamily:"ETH-USDT"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск обязательного `instType` → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50014 (Parameter instType can not be empty.), data.size=0 |

### PG8.3 Негатив — пропуск обязательного instFamily (для SWAP)

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/insurance-fund", query:{instType:"SWAP"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) | Пропуск `instFamily` (обяз. для SWAP) → реджект OKX; точный код — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=50015 (Either parameter instFamily or uly is required), data.size=0 |

### PG8.4 Негатив — битое значение type

| Запрос | Проверки (сырой JSON OKX) | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| `POST /raw {method:"GET", path:"/api/v5/public/insurance-fund", query:{instType:"SWAP", instFamily:"ETH-USDT", type:"WRONG"}, signed:false}` | HTTP 200; OKX-реджект (`b.code≠"0"`) ИЛИ пустой `b.data` | Значение `type` вне домена → реджект либо пустой `data`; точный исход — наблюдение | RUN 2026-06-20 ✓ — http 200, b.code=0, data.size=1 |


---

# I-cred — пустые OKX-креды (auth-негатив клиентского слоя)

- **Объект:** любой приватный вызов сырого клиента (напр. `getBalance`)
  при `OkxProperties` без кредов. **Предусловие:** изолированная
  конфигурация — `api-key`/`secret`/`passphrase` не заданы (не demo, не
  prod). **Среда:** локально, без сети. **Teardown:** не требуется.
  **Тип:** регрессионный гард I3 (auth-негатив). **Не в коллекции и не
  через `/raw`** — требует изолированной конфигурации без кредов (поднятый
  app креды имеет); offline-юнит-тест над `OkxSigningInterceptor`, без сети.

| Запрос | Проверки | Ожидаемый результат | Факт + наблюдения (RUN) |
|---|---|---|---|
| приватный вызов на пустых кредах | исключение до сети; `IllegalStateException`, сообщение содержит «OKX credentials not configured» | **I3 closed** (пункт закрыт и вынесен из `backlog.md`; итог — `.claude/work/history/2026-06-20-source-api-contour.md` §«Что сделано»; адрес поправлен N6 `DOCS_CHECK_22`): fail-fast внятной `IllegalStateException` «OKX credentials not configured» до подписи и сети — не голый NPE (`getSecret().getBytes()` на `null`), сети не достигает | RUN 2026-06-20 ✓ — I3 closed: `IllegalStateException` «OKX credentials not configured» до сети (offline-юнит, `validate-icred-01.log`) |

---

## Закрытая развилка: нога amend — под `/raw` покрыта

Прежде нога amend выпадала: метода клиента/типизированного прокси нет
(REPLACE-only, `docs/rules/replace-not-amend.md`). Под `/raw` тело
конверта строится руками → **amend достижим напрямую** и покрыт: `TG3`
(amend order), `TG4` (amend batch), `TG9` (amend algo). Продуктовый
REPLACE-ремодел этим не предрешается — контур лишь подтверждает, что
эндпоинт OKX рабочий.

## Принятые решения §Нерешённое (применены)

Прежние «на аппрув / валидацию» — **решены и применены** в этом плане:

1. **Реальное исполнение + реверсивные account-write на demo —
   допущено**, при **инварианте восстановления состояния кейса**
   (Snapshot.start → restore → Verify.end, см. §Сквозные проверки).
   Применено: CORE Cmarket (fill/позиция), TG1/TG3/TG4/TG9 (ордера/algo),
   AG7 (`posMode`), AG8 (`leverage`), TG7 (`acctLv`). Минимальный `sz`,
   teardown + verify во всех stateful-кейсах.
2. **Гейты достижимости архивов — приняты:** `TG5`
   (orders-history-archive 3m), `AG1` (positions-history), `AG4`
   (bills-archive), `AG5` (bills deep-архив async). Покрытие формой +
   негативом, пустые данные валидны, содержимое не выдумывается.
3. **`order-precheck` (TG7) — разведочный кейс с переключением `acctLv`**
   (изолированно: read `acctLv` → set 3/4 → precheck (документируем
   `adjEq`/`imr`/`mmr`) → restore → verify). Не-переключение/невозврат —
   находка + флаг остаточного состояния. Заменяет прежнее ветвление
   3/4 vs 1/2.
4. **Точные коды негатива** — где контракт молчит, ожидание «реджект
   (`code≠"0"`)», точный код — наблюдение RUN → находка интегратору (C3).

## Связи

- Процесс контура — `.claude/processes/source-api-testing.md`.
- Решение (ре-база / `/raw`-only, раздел D) —
  `.claude/decisions/source-api-target-rebase.md`.
- Шаблон — `.claude/templates/docs/test-plan.md`.
- Скиллы — `.claude/skills/{test-design,test-collection,test-code,test-run,test-review}.md`.
- Роль-автор — `.claude/agents/tester.md`.
- Манифест покрытия (источник набора, колонка покрытия) —
  `.claude/processes/api-docs-completion.md`.
- Контракты OKX — `docs/integrations/okx/contracts/`; правила —
  `docs/integrations/okx/rules/`.
- Generic-эндпоинт `/raw` и сырой клиент —
  `src/main/java/com/example/tradingbot/api/controller/OkxProxyController.java`,
  `integration/service/okx/OkxRestClient.java`.
