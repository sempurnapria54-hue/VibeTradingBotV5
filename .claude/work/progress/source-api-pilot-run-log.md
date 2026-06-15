# Прогон пилота source-api OKX — живой лог

## На какой вопрос отвечает этот файл

Какие факты и находки дал живой прогон пилотного плана OKX (рабочий
лог; консолидируется в отчёт `history` по завершении).

## Среда

- **Demo**, профиль `test`, `http://localhost:8080/api/proxy/okx`,
  поднято пользователем, подтверждено 2026-06-12.
- Инструмент `ETH-USDT-SWAP`: `minSz=0.01`, `lotSz=0.01`,
  `tickSz=0.01`, `ctVal=0.1`, status `live` (proxy getInstrument +
  публичные specs совпали).
- Demo-баланс: availBal `5000` USDT, totalEq `77300.66`.

## Фаза 1 — demo (завершена для этого прогона)

| Кейс | Ожидание | Факт | Вердикт |
|---|---|---|---|
| N1 cancel несущ. | reject 51603 в ack | `ExchangeAck{success:false, code:null, message:null}` (ordId эхо) | reject отражён, **код 51603 потерян** → F1 |
| N2 getOrder фейк | 51603 raw / снапшот-терминал | **HTTP 500** (`verifyCode:347` бросает на 51603, `OkxProxyController.getOrder:59` не ловит) | не graceful → F2 |
| C1 place (×2) | sCode=0 + ordId | `{success:false, externalId:"", code:null}` — **и до, и после выравнивания demo-аккаунта** | place **отклонён непрозрачно**, причина не в аккаунте → F3 |
| safety orphan-check | — | getOrder?clOrdId (оба id) → 500 not-found; getPosition → null | **орфанов нет, exchange чист** |
| C2–C4 цепочка | — | **отложены на следующий прогон** | blocked F3 |
| A1–A4 (И-2) | — | **отложены на следующий прогон** | blocked F3 |

**Teardown:** остаточного состояния на demo нет — N1 no-op; оба place
ничего не создали (подтверждено orphan-check: оба clOrdId not-found,
позиции нет).

## Находки

- **F1 — write-op реджект непрозрачен: ни кода в ack, ни лога**
  (грунт по коду). Read-ops (`getOrder`/`getAlgoOrder`/`getPosition`/
  `getBalance`) идут через `verifyCode` — бросает+логирует «OKX error
  […] code= msg=» на top-level ошибке. **Write-ops** (`placeOrder:147`,
  `cancelOrder:155`, `placeAlgoOrder:163`, `cancelAlgoOrder:175`,
  `closePosition:188`) идут через `toOrderAck`/`toAlgoAck`, **минуя
  `verifyCode`**. `toOrderAck:212` бросает+логирует `writeFailure`
  (с реальными code/msg) **только при пустом `data`**; при непустом
  `data` зовёт `integrationToAck(data[0])` **без лога**. DTO
  `OrderAckOkxResponse` именован верно (`ordId`/`clOrdId`/`sCode`/`sMsg`).
  На N1/C1 пришёл непустой `data[0]` с эхо `clOrdId`, но `sCode`/`ordId`
  = null → `integrationToAck` даёт `code:null, success:false,
  externalId:""`, **без лога**. Итог: причина write-реджекта **не
  surface-ится нигде**; raw-тело OKX не сохраняется → корень (почему
  `sCode` пуст при непустом `data`) недиагностируем извне.
- **F2 — getOrder на несуществующем ордере → HTTP 500** (причина
  подтверждена трейсом). `OkxIntegrationService.getOrder` →
  `verifyCode` (OkxIntegrationService.java:347) **бросает**
  `ExchangeIntegrationException: OKX error [trade-order] code=51603
  msg=Order does not exist` → необработанное → HTTP 500. Две грани:
  (a) **raw `getOrder` не null-safe на not-found** — бросает, не
  возвращает терминал `MISSING_AFTER_REFRESH` (тот живёт в
  REFRESH-исполнителе, прокси его не открывает); важно для D-B3
  (recovery-by-clientId требует null-on-not-found на 51603);
  (b) **брошенное `ExchangeIntegrationException` уходит голым 500**
  без кода вызывающему — упирается в TBD error-политику (шаг 6).
- **F3 — place блокирует demo write-цепочку; два слоя причины.**
  Подпись работает (balance-чтение прошло) → реджект на стороне OKX.
  - **Слой 1 (закрыт) — В-9 bootstrap.** До выравнивания demo-аккаунта
    ручное размещение того же ордера в OKX demo UI surface-ило
    config-причину (дословная формулировка пользователем не
    зафиксирована). Заземляет В-9: адаптер хардкодит `posSide=net`/
    `tdMode=isolated` и предполагает SWAP-capable аккаунт **без
    bootstrap-проверки**; при несоответствии place отлетает.
    Пользователь выровнял аккаунт (one-way/net + SWAP-capable +
    плечо ETH-USDT-SWAP isolated).
  - **Слой 2 (остаточный блокер) — дефект place-пути.** После
    выравнивания place **всё равно** отклонён тем же непрозрачным
    ack (`success:false, code:null`) → **корень не аккаунт**.
    Параметры под контролем валидны (side=buy, sz=minSz, px в полосе,
    reduceOnly=false). Непрозрачность — из-за F1; корень (дефект
    формирования запроса или разбора ответа: непустой `data[0]` без
    `sCode`) **недиагностируем извне** без raw-лога OKX-тела на
    write, которого код не делает. Владелец — integrator/код.
  - **Орфанов нет** (orphan-check). Demo-цепочка (C2–C4) и И-2
    (A1–A4) **отложены на следующий прогон** — решение пользователя
    (вариант 1 → при остаточном блокере fallback на вариант 2).

## Фаза 2 — I3 (решение пользователя: confirmed-by-code)

Кейс I3-1 принят **подтверждённым по коду**, живой прогон **не
выполняется** (live deferred). `OkxSigningInterceptor.sign()` →
`properties.getSecret().getBytes()` → **NPE на null-секрете** (не
fail-fast «OKX credentials not configured») — подтверждает
`backlog.md` §I3. Фаза 2 закрыта этим.

## Статус — RUN на паузе (переезд в новый чат)

**Сделано:** Фаза 1 demo частично (негативы N1/N2 + place C1 ×2 —
находки F1/F2/F3, teardown чист, орфанов нет); Фаза 2 I3
(confirmed-by-code).

**Не сделано — продолжается в новом чате:** Фаза 3 prod read-only
(P1–P4, app поднимает пользователь на prod-профиле) → отчёт прогона
→ ревью отчёта (`test-review`) → маршрутизация находок → history.

**Отложено в demo (следующий прогон):** цепочка C2–C4 и И-2 (A1–A4)
— блокер F3-слой-2 (дефект place-пути).

**Маршрутизацию находок не финализировать** (ступень Предложение) —
часть разбора отчёта.

**Средовой дефицит RUN** (нет headless-бута / Vault-токена в shell
CC) — заведён пайплайн-задачей: `backlog.md` §Средовой дефицит
автономного RUN тестов.

## F1 — фикс видимости write-реджекта (код, 2026-06-12)

**Сделано (только видимость, propagation не трогали; F3-корень
по-прежнему открыт — этот фикс его *открывает* к диагностике):**

- **Raw-тело OKX на write-ops в логе.** Новый
  `OkxWriteLoggingInterceptor` (на приватном `okxAuthRestClientHttp`,
  после подписи) логирует сырое тело ответа на POST-запросах (= все
  write: place/cancel order, place/cancel algo, close position) на
  уровне INFO; GET (read) проходят без буферизации. Тело
  переотдаётся downstream через `BufferedClientHttpResponse` (чтобы
  десериализатор прочитал повторно). Секреты в заголовках запроса не
  логируются.
- **Ack-код/сообщение не null на реджекте.** `OrderMapper`/
  `AlgoOrderMapper.integrationToAck` теперь принимают top-level
  `code`/`msg` ответа и через `StringUtils.firstNonBlank` падают на
  них, если per-order `sCode`/`sMsg` пусты (наблюдалось на N1/C1).
  `OkxIntegrationService.toOrderAck`/`toAlgoAck` прокидывают
  `response.getCode()`/`getMsg()`.

`mvn compile` зелёный; MapStruct перегенерён; авто-тестов на этот
контур нет (`.claude/tests/source-api` — план, не JUnit).

## Probe — живой захват write-реджекта (2026-06-12)

Один demo-place (C1) на поднятом `test`-профиле (app поднял CC через
`tools/boot-test.sh` с переданным пользователем Vault-токеном;
Postgres-test `:5441`, Vault `:8200`). Параметры: `ETH-USDT-SWAP`,
`side=buy`, `sz=0.01` (minSz), `px=837` (≈ −50 % mark 1674.22, вне
исполнения), `reduceOnly=false`, `clOrdId=f3probe1781282363`.

**Захвачено:**

- **ack (F1 сработал — код/сообщение непустые):**
  `{code:"1", message:"All operations failed", success:false,
  externalId:"", internalId:"f3probe1781282363"}`.
- **raw OKX write response (F1 сработал — раньше терялось), из лога
  `OkxWriteLoggingInterceptor`:**
  ```
  OKX write raw response [/api/v5/trade/order] status=200
  body={"code":"1","data":[{"clOrdId":"f3probe1781282363","ordId":"",
  "sCode":"51010","sMsg":"You can't complete this request under your
  current account mode. ","subCode":"","tag":"tb","ts":"..."}],
  "msg":"All operations failed",...}
  ```
- **фактический OKX-реджект (вход для F3):** per-element
  **`sCode=51010`**, `sMsg="You can't complete this request under
  your current account mode."`, top-level `code=1` /
  `msg="All operations failed"`.

**Наблюдение для F3 (только фиксация, не диагностируем здесь):**
в raw-теле per-element `sCode=51010` **присутствует**, но в ack
`code=1` (top-level fallback) — т.е. `OrderAckOkxResponse.sCode`
десериализовался в **null**, и F1-fallback подставил top-level.
Это и есть корневой узел F3: код 51010 живёт в `data[0].sCode`, но
не доезжает до DTO (десериализация per-element `sCode` не
срабатывает). Разбор — отдельным заходом. Содержательно 51010
(«account mode») перекликается с F3-слой-1 (В-9 bootstrap account
mode), но это для разбора F3, не вывод probe.

**Teardown — орфанов нет, exchange чист:** place отклонён
(`ordId=""`, ничего не создано); `getOrder?clOrdId=f3probe…` → HTTP
500 на `51603 Order does not exist` (известное F2: read-path
`verifyCode` бросает на not-found) — подтверждает, что ордера нет;
`getPosition` → HTTP 200 пусто (позиции нет). Отмены/закрытия не
требовалось (реджект, не fill).

**App после probe остановлен CC** (поднимался только под этот
захват; рестарт — `tools/boot-test.sh` или IDEA).

## F3a — per-element sCode не доезжал до DTO (фикс, 2026-06-12)

**Диагноз подтверждён фактическим биндом (не рассуждением).** Юнит-срез
`OkxAckDeserializationTest` десериализует сырой элемент ack обоими
Jackson на classpath:

- **Jackson 2** (`com.fasterxml`) — `sCode` биндится корректно
  (`"51010"`).
- **Jackson 3** (`tools.jackson`, дефолт SB4/Spring 7, которым и
  десериализует ответ `RestClient`) — `sCode` → **null**.

Корень: Lombok `beanspec` даёт аксессоры `getsCode()`/`setsCode()`;
выводимое Jackson 3 имя свойства из такого аксессора (строчная первая +
заглавная вторая буквы) НЕ матчит ключ JSON `sCode`. Jackson 2 по
legacy-mangling совпадает, Jackson 3 — нет. Гипотеза из задачи (связка
Lombok-геттер × Jackson-интроспекция) подтверждена и **уточнена до
Jackson 3**.

**Фикс:** явный `@JsonProperty("sCode")`/`@JsonProperty("sMsg")` на полях
`OrderAckOkxResponse` и `AlgoOrderAckOkxResponse`
(`com.fasterxml.jackson.annotation.JsonProperty` чтится и Jackson 3).
Top-level fallback из F1 **оставлен** как страховка — но больше не
триггерится, т.к. per-element `sCode` теперь биндится.

**Проверка:**

- Юнит: `OkxAckDeserializationTest` — 4 теста (order/algo × Jackson 2/3),
  до фикса 2 падали (Jackson 3 → null), после — все зелёные;
  `mvn test` BUILD SUCCESS.
- Живой re-place (C1, `clOrdId=f3afix…`): ack теперь
  `{code:"51010", message:"You can't complete this request under your
  current account mode. ", success:false}` — **per-element код**, не
  top-level `"1"`. Teardown чист (getOrder→500/51603 not-found,
  getPosition пусто). App остановлен.

**Связанная находка (вне скоупа F3a — НЕ чинил):** тот же корень
(Jackson 3 + Lombok beanspec, строчная-первая/заглавная-вторая) бьёт по
read-DTO с полями `cTime`/`uTime` (`OkxBalanceResponse`,
`OkxPositionResponse`, `OrderOkxResponse`, `OkxBalanceDetailResponse`,
`OkxAlgoOrderResponse`). Признак уже виден в probe: health-check
`getBalance` отдал `externalUpdatedAt:null` (маппится из `uTime`).
Это отдельная правка (те же `@JsonProperty`), не входит в F3a —
вынести задачей/в backlog (решение пользователя).

**F3b** (сам реджект `51010 account mode` / В-9 bootstrap) — по-прежнему
открыт, отдельный заход с входом пользователя; F3a его не трогал.

## F4 — Jackson 3 мангли́нг в read-DTO (interim-фикс, 2026-06-12)

Закрыта «связанная находка» F3a (interim). `@JsonProperty("cTime")`/
`@JsonProperty("uTime")` проставлены на 5 read-DTO (`OkxBalanceResponse`,
`OkxBalanceDetailResponse`, `OkxPositionResponse`, `OrderOkxResponse`,
`OkxAlgoOrderResponse`).

**Проверка эмпирикой:** `OkxReadDtoDeserializationTest` десериализует
полный per-field payload каждого DTO под Jackson 3 и проверяет бинд
каждого поля (рефлексией, мимо аксессоров). До фикса падали ровно
`cTime`/`uTime` (по `uTime` в balance-DTO); после — все зелёные.
`mvn test` BUILD SUCCESS (9 тестов: 5 read + 4 ack).

**Sweep по текущим OKX-DTO:** grep на точный паттерн lower-upper даёт
ровно 7 уже-починенных полей (4 ack F3a + cTime/uTime F4); иного
остатка по OKX нет (request/response/nested `AttachAlgoOrdOkxResponse`
чисты).

**Системная находка заведена** в `backlog.md` §Инфра-долг **I4**
(смежна I2): защита от рецидива (глобальный конфиг Jackson 3 vs
конвенция) и охват будущих/иных источников — открыты на routing
интегратора, не в этом заходе.

## F3b — диагностика account config (read, 2026-06-12)

Диагностический read `GET /api/v5/account/config` на demo (test-профиль),
чтобы узнать фактический режим аккаунта, как его видит OKX.

**Surface-gap подтверждён:** метода/DTO под `account/config` в клиентском
слое **нет** (как со свечами P3). Прочитано прямым signed GET через тот
же приватный клиент (`okxAuthRestClientHttp`): добавлены диагностический
`OkxRestClient.getAccountConfig()` (сырое тело String) +
`Constants.Okx.ACCOUNT_CONFIG_PATH` + диагностический endpoint
`GET /api/proxy/okx/account-config`. **Это диагностический код, не
продуктизация** (полноценный account-config через домен/снапшот — это
В-9 bootstrap-check, отдельный заход; здесь только чтение факта).

**Факт (raw `code:"0"`):**

- **`acctLv` = "1" → Spot mode** (1 Spot / 2 Spot&Futures / 3
  Multi-currency / 4 Portfolio).
- **`posMode` = "net_mode"** — совпадает с хардкодом адаптера
  `posSide=net`; **не причина**.
- `perm` = `"read_only,trade"` — trade-право есть; **не причина**.
- `settleCcy=USD`, `acctStpMode=cancel_maker`, `mgnIsoMode=auto_transfers_ccy`.

Сырое тело (uid/mainUid/ip отредактированы — не диагностичны):
```
{"code":"0","data":[{"acctLv":"1","acctStpMode":"cancel_maker",
"autoLoan":false,"ctIsoMode":"automatic","enableSpotBorrow":false,
"feeType":"0","greeksType":"PA","ip":"<redacted>","kycLv":"2",
"label":"BotV5-test","level":"Lv1","levelTmp":"","liquidationGear":"-1",
"mainUid":"<redacted>","mgnIsoMode":"auto_transfers_ccy","opAuth":"0",
"perm":"read_only,trade","posMode":"net_mode","roleType":"0",
"settleCcy":"USD","settleCcyList":[],"spotBorrowAutoRepay":false,
"spotOffsetType":"","spotRoleType":"0","spotTraderInsts":[],"stgyType":"0",
"traderInsts":[],"type":"0","uid":"<redacted>"}],"msg":""}
```

**Диагноз F3b:** аккаунт в **Spot-mode (`acctLv=1`)** — SWAP
(`ETH-USDT-SWAP`, перп-фьючерс) в этом режиме недоступен, требуется
`acctLv ≥ 2` (Spot&Futures / Multi-currency / Portfolio) → place отлетает
на `51010 "You can't complete this request under your current account
mode."`. Корень — **уровень аккаунта**, не `posMode` и не права.
Переключение режима demo-аккаунта — **решение пользователя** (не трогал;
В-9 bootstrap-check тоже не трогал). App после чтения остановлен.

## Отложенная demo-цепочка C1→C4 + И-2 — попытка, заблокирована на C1 (2026-06-12)

Прогон отложенной части после сообщения «demo переключён на `acctLv ≥ 2`».
**Самогейт сработал на C1 — цепочка остановлена, не форсирована.**

- **C1 (place)** buy limit `px=834` (≈ −50 % mark 1668.69) `sz=0.01`
  `reduceOnly=false`, `clOrdId=c1…` → **снова `51010`**: ack
  `{code:"51010", message:"You can't complete this request under your
  current account mode. ", success:false}`; raw `data[0].sCode=51010`
  (F1/F3a отрабатывают — код per-element виден).
- **Перепроверка `/account/config` тут же:** **`acctLv` всё ещё `"1"`**
  (Spot mode), `posMode=net_mode`, `perm=read_only,trade`, та же учётка
  (`mainUid==uid`, label BotV5-test). → **Переключение на стороне OKX не
  вступило в силу для аккаунта, который видит бот** (тот же demo-аккаунт,
  по-прежнему Spot mode).
- **C2–C4, A1–A4 — не прогонялись** (гейт C1). Teardown: place отклонён,
  ничего не создано; orphan-check — `getOrder?clOrdId`→500/`51603`
  not-found, `getPosition` пусто; позиций/ордеров не открывали. Exchange
  чист. App остановлен.

**Вывод для разбора (не финализирую — ступень Предложение):** диагноз
F3b прежний (Spot-mode `acctLv=1`); цепочка разблокируется только когда
OKX начнёт отдавать боту `acctLv ≥ 2`. Пользователю — проверить, что
режим переключён **именно на demo-аккаунте `BotV5-test`** (uid тот же,
что в `/account/config`) и применился (возможна задержка / переключение
не на той учётке / demo-режим имеет отдельную настройку). По готовности —
перепрогнать (C1 самогейтом).

## Цепочка — попытка 2 с фронт-гейтом, заблокирована на фронт-гейте (2026-06-12)

После повторного сообщения «demo переключён». Этот заход — **фронт-гейт
по `acctLv` ДО любого write**: перечитан `/account/config` перед place.

- **Фронт-гейт: `acctLv` снова `"1"`** (Spot mode), `posMode=net_mode`,
  `perm=read_only,trade`, label `BotV5-test`. → **не прошёл — ни одного
  write не делал** (place не пробовал, в отличие от попытки 1). Цепочка и
  И-2 не запускались.
- App остановлен.

**Уточнение гипотезы для разбора:** наиболее вероятная причина — у OKX
**demo (simulated) trading — отдельный аккаунт со своим режимом**; бот
ходит туда через `x-simulated-trading: 1`, и `/account/config` читает
именно его. Если переключали режим в основном (live) UI, demo-аккаунт
остаётся `acctLv=1`. Проверить/переключить нужно режим **demo-аккаунта**
в OKX Demo Trading, не live. Перепрогон — по готовности (фронт-гейт
сам подтвердит `acctLv ≥ 2`).

## Цепочка — попытка 3: фронт-гейт пройден, C1 встал на новом блокере 50033 (2026-06-12)

Гипотеза про demo-аккаунт подтвердилась: пользователь переключил режим
**demo-аккаунта** → фронт-гейт пройден.

- **Фронт-гейт пройден:** `/account/config` → **`acctLv = "2"`**
  (Spot and futures), `posMode=net_mode`, `perm=read_only,trade`, label
  `BotV5-test`. **51010 (account mode) — снят.**
- **C1 (place)** buy limit `px=835` (≈ −50 % mark 1669.97) `sz=0.01`
  `reduceOnly=false` → **новый реджект `50033`**: ack
  `{code:"50033", message:"Instrument restricted. Remove restrictions in
  your trading permission settings (web only). ", success:false}`; raw
  `data[0].sCode=50033`.
- **Самогейт сработал** — C2–C4, A1–A4 не прогонялись. Teardown: place
  отклонён, ничего не создано; orphan-check — `getOrder?clOrdId`→500/
  `51603` not-found, `getPosition` пусто. Exchange чист. App остановлен.

**Новый блокер (для разбора, не финализирую):** `50033 "Instrument
restricted"` — на demo-аккаунте включено **ограничение торговли
инструментом** (перпы/деривативы) в trading-permission settings. Это
**не** API-key perm (`perm` уже несёт `trade`) и **не** account mode
(acctLv=2 ок) — отдельная account-level настройка торговых разрешений,
снимается **только в web-UI OKX** (по тексту OKX: «web only»).
Пользователю — снять ограничение на инструмент/деривативы в trading
permission settings demo-аккаунта. По готовности — перепрогон (C1
самогейтом). **Прогресс:** последовательно сняты 51010 → теперь 50033;
блокеры разные, цепочка ещё ни одного успешного write не дала.

## Цепочка C1→C4 + И-2 — ПРОЙДЕНА ПОЛНОСТЬЮ (попытка 4, 2026-06-12)

Пользователь снял ограничение деривативов. Фронт-гейт `acctLv=2`,
`posMode=net_mode`. Все три слоя блокеров сняты (51010 → 50033 → place
проходит). Demo-цепочка и И-2 отработали целиком.

### Цепочка C (ordinary order) — все кейсы pass

| Кейс | Действие | Факт | Вердикт |
|---|---|---|---|
| **C1** | place buy limit `px=834.3` (≈ −50 % mark 1668.62) `sz=0.01` `reduceOnly=false` | ack `{code:"0", success:true, externalId(ordId)=3650340451041157120}`; raw `sCode=0 "Order placed"` | **pass** — принят |
| **C2** | getOrder by ordId | `externalStatus=live`, `accumulatedFillSize=0`, id/internalId совпали | **pass** — ACK стал live-ордером (ACK ≠ runtime truth) |
| **C3** | cancel by ordId | ack `{code:"0", success:true}`; raw путь `/trade/cancel-order` `sCode=0` | **pass** — отмена принята |
| **C4** | getOrder by ordId | `externalStatus=canceled`, `accFillSz=0`; `externalCreatedAt/ModifiedAt` непустые | **pass** — финал canceled (не ACK) |

### И-2 (trailing `move_order_stop` через `cancel-advance-algos`) — pass + находка

| Кейс | Действие | Факт | Вердикт |
|---|---|---|---|
| **A1** | placeAlgo `move_order_stop` `direction=SELL` `sz=0.01` `reduceOnly=true` `trailingPercents=0.05`, **без позиции** | ack `{code:"0", success:true, externalId(algoId)=3650349050982977536}`; `sCode=0` | **pass** — принят |
| **A2** | getAlgoOrder by algoId | `externalStatus=live`, `failCode=0`, `condition.trailing.externalPrice=1585.54` (трекает ~−5 %) | **pass** — live |
| **A3** | cancelAlgo (`conditionType=TRAILING_PERCENTS` → advance) | ack `{code:"0", success:true}`; **raw путь `/api/v5/trade/cancel-advance-algos`** `sCode=0` | **pass + находка интегратору** (ниже) |
| **A4** | getAlgoOrder by algoId | `externalStatus=canceled`, `failCode=0` | **pass** — финал canceled |

**Teardown — exchange чист:** позиция не открывалась (A1 принял
reduce-only trailing без позиции); C-ордер `canceled`, A-algo `canceled`,
`getPosition` пусто. Орфанов нет, закрывать нечего.

### Находки / наблюдения (НЕ финализирую маршрутизацию — Предложение)

- **И-2 / A3 — `cancel-advance-algos` жив на demo (находка интегратору).**
  Эндпоинт, выведенный из офдока OKX 2025-04-24, по факту **существует и
  отвечает `sCode=0`** на demo; клиентский слой корректно ветвит
  advance-семью (trailing/`move_order_stop`) на `/trade/cancel-advance-algos`
  (подтверждено raw-путём из лога). → обновить
  `docs/integrations/okx/contracts/algo-order.md` в подтверждённую сторону
  (эндпоинт живой, не делистнут). Незадокументированного не выдумывал —
  факт из прогона.
- **Наблюдение:** demo принимает **reduce-only trailing без открытой
  позиции** (rests pending) — рантайм-резолюция плана A1 (открыть позицию
  min-size) **не понадобилась**.
- **Наблюдение (C2):** адаптер-инварианты `tdMode/posSide/reduceOnly`
  **не surface-ятся** в `OrderExternalSnapshot` (они adapter-validation,
  в снапшот не маппятся) — C2-проверка инвариантов по снапшоту
  ненаблюдаема; подтверждение place дал `state=live` + `accFillSz=0`.
- **F4 подтверждён вживую:** `externalCreatedAt/ModifiedAt` (из
  `cTime/uTime`) непусты и на order, и на algo-чтении — фикс F4 работает на
  живом read-пути, не только в юните.
- **Блокеры F3b — слоистые, сняты последовательно:** `51010` (account
  mode → acctLv≥2) → `50033` (instrument restriction → web-UI demo) →
  place проходит. Все три — account-side настройки demo, не код.

App остановлен.
