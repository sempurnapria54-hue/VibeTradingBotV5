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
