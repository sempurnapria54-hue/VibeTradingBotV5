# Order — mapping между слоями

## На какой вопрос отвечает этот файл

Как `Order` переходит между слоями.

## Source-agnostic ядро

### `OrderExternalSnapshot` → `Order`

Snapshot — нормализованный граничный объект; единственное, что
выходит из adapter (`raw-exchange-dto-boundary.md`).

| Snapshot field | Domain | Семантика |
|---|---|---|
| `internalId` | `Order.internalId` | stable client id (сверка) |
| `externalId` | `Order.externalId` | биржевой id (сохраняется при первом известном значении) |
| `externalInstrumentId` | — | биржевое имя инструмента (`instId`). Приземляется в снапшот ради **счёт-широкого среза**: он читается по счёту, и строку адресует инструментом только это поле (`docs/components/AnomalyJob.md`). В `Order` не идёт — там инструмент известен из графа сделки |
| `type` | `Order.type` | тип ордера (источник-нейтральный) |
| `side` | `Order.side` | `BUY`/`SELL` |
| `externalStatus` | — | raw статус, режим diagnostic; в FSM не используется (`external-status-resolution.md`) |
| `price` | `Order.price` | empty→null |
| `size` | `Order.size` | размер (в единицах источника; для SWAP/FUTURES — контракты) |
| `accumulatedFillSize` | `Order.accumulatedFillSize` | исполнено накопленно |
| `averagePrice` | `Order.averagePrice` | средняя цена исполнения |
| `fee` | `Order.fee` | комиссия |
| `externalCreatedAt` | `Order.externalCreatedAt` | |
| `externalModifiedAt` | `Order.externalModifiedAt` | |
| `attachedAlgoInternalId` | — | top-level attached client id; **у `Order` такого поля нет** — идентичность attached-защиты живёт на элементе `attachedAlgoOrders[]` (`internalId`), и top-level эхо в домен не приземляется |
| `takeProfitTriggerPrice` (future) | — | top-level TP trigger (для entry-with-attached-SL) |
| `stopLossTriggerPrice` | — | top-level SL trigger; **у `Order` такого поля нет** — уровень живёт на элементе `attachedAlgoOrders[].stopLossTriggerPrice` (`docs/models/domain/core/Order.md`), верифицировано по `Order.java` |
| `attachedAlgoOrders[]` | `Order.attachedAlgoOrders[]` | список `AttachedAlgoOrder` (см. ниже) |

**Поля планового риска в снапшот не входят и им не перезаписываются.**
`plannedRiskAmount`, `plannedRiskCurrency`, `plannedEntryPrice`,
`plannedSizeContracts`, `plannedContractValue`, **`plannedStopPrice`** —
**наши** величины,
произведённые риск-преконтролем и доставленные
`CreateOrderCommandPayload`'ом (`docs/components/CreateOrderExecutor.md`); источник таких фактов не отдаёт, и эхо
рефреша их не трогает — все шесть write-once (`updatable = false`).
**Тем же порядком не приземляются `liquidationDistanceRatio` (седьмое
число) и `positionId`** — первое производит преконтроль, второе пишет
`RefreshPositionExecutor` той же транзакцией, в которой эпизод
материализован или наблюдён (`docs/models/domain/core/Order.md`); из
снапшота не берётся ни то, ни другое.

Mapping-таблица обязана сказать, что этих колонок здесь нет
**намеренно**, — иначе отсутствие читается как пропуск.

### `Domain Order → request`

- **Create**: `Instrument.externalId → instId`; `isolated → tdMode`
  (adapter-константа); `net → posSide` (adapter-константа);
  `Order.side → side`; `Order.type/exec settings → ordType`;
  `Order.size → sz`; `Order.price → px` (если нужен типу);
  `Order.internalId → clOrdId`; `Order.positionReducingOnly →
  reduceOnly`; `Order.attachedAlgoOrders → attachAlgoOrds`
  (entry-with-attached-SL; состав элемента — §«Domain Order → OKX
  request» ниже). После successful submit
  `ordId` (если вернулся) сохраняется как `Order.externalId`; статус
  — `PENDING` до refresh/search/history.
- **Cancel**: `instId` + одно из `ordId` (предпочтительно) /
  `clOrdId`.

Амендного request-mapping **нет**: домен не амендит
(`docs/rules/replace-not-amend.md`) — ремоделирование ордера =
REPLACE-оркестрация (cancel-нога → подтверждение терминала с
разбором fill-race → place новой сущности с `replacesInternalId`).
Биржевой amend-контракт OKX задокументирован как поверхность
(`docs/integrations/okx/contracts/order.md`), доменом не
используется.

Per-item error классифицируется: retryable → `RETRY_PENDING`;
non-retryable → `Order.ERROR`/`Deal.ERROR` (`docs/rules/runtime-error-classification.md`).

### Status resolver (source-agnostic интерфейс)

`externalStatus` (raw из источника) → `Order.Status` через
`OrderExternalStatusResolver` (`docs/components/OrderExternalStatusResolver.md`).
FSM раз не использует raw status (`external-status-resolution.md`).
Таблица соответствий — per-source (см. подразделы).

### Order evidence-cycle / not found

`ExternalNotFoundException` — только после **полного** order
evidence-cycle (специфика per-source — см. подразделы). Пустой ответ
одного endpoint не даёт `MISSING_AFTER_REFRESH`. После полного цикла
без находки → `Order.ERROR` + `MISSING_AFTER_REFRESH` → safety-каскад
(`external-status-resolution.md`).

### `AttachedAlgoOrder` (attached protection)

Один элемент `attachedAlgoOrders[*]` → `AttachedAlgoOrderExternalSnapshot`;
матчинг по `internalId` (client id вложенного TP/SL). Status: `PENDING`
после `SUBMIT_ORDER_COMMAND`; `ACTIVE` — по предикату
`docs/spec/order-lifecycle.json`, величина `attachedBecomesActive`
(присутствия в снапшоте недостаточно: нужен непустой налив родителя и
пустой код отказа); заполненный `failCode` → `ERROR` с
`PROTECTION_PLACEMENT_FAILED`.
Судьба защиты по фактам родителя (класс состояния + налив) — `docs/lifecycles/Order.md`.

**Ценовая база триггера приезжает эхом и сверяется.** `triggerPriceType`
снапшота — операнд сверки объявленной базы `MARK`
(`docs/models/domain/core/AlgoOrder.md`): расхождение **непустого** эха с
объявленным — нарушение биржевого инварианта; пустое эхо сверку не
запускает. Состав снапшота и довод — `docs/models/domain/core/Order.md`
§«Граничные снапшоты» и §«Персистентность встроенной защиты».

## OKX

### `OrderOkxResponse` → `OrderExternalSnapshot`

См. инвентарь полей нативной модели —
`docs/models/integrations/okx/OrderOkxResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `clOrdId` | `internalId` |
| `ordId` | `externalId` |
| `ordType` | `type` |
| `side` | `side` |
| `state` | `externalStatus` (raw, не для FSM напрямую) |
| `px` | `price` (empty→null) |
| `sz` | `size` |
| `accFillSz` | `accumulatedFillSize` |
| `avgPx` | `averagePrice` |
| `fee` | `fee` |
| `cTime` | `externalCreatedAt` |
| `uTime` | `externalModifiedAt` |
| `attachAlgoOrds` | `attachedAlgoOrders` |
| `attachAlgoClOrdId` | `attachedAlgoInternalId` |
| `tpTriggerPx` | `takeProfitTriggerPrice` (future) |
| `slTriggerPx` | `stopLossTriggerPrice` |
| `reduceOnly` | **не маппится** — только invariant validation в adapter (см. правила OKX) |

### `OrderOkxResponse.attachAlgoOrds[*]` → `AttachedAlgoOrderExternalSnapshot`

| OKX field | Snapshot field | Комментарий |
|---|---|---|
| `attachAlgoId` | `externalAttachedId` | attached algo id из embedded block |
| `attachAlgoClOrdId` | `internalId` | client id — основной ключ матчинга |
| `algoId` | `externalId` | algo id после trigger/создания |
| `algoClOrdId` | не маппится | diagnostic/future |
| `tpOrdKind` | `externalType` / future | для SL-only можно не использовать |
| `sz` | `size` | string→`BigDecimal` |
| `slTriggerPx` | `stopLossTriggerPrice` | trigger SL |
| `slTriggerPxType` | `triggerPriceType` | **операнд сверки объявленной базы** `MARK`; `last`/`index`/`mark` → доменный енум, пусто → пусто |
| `failCode` | `failCode` | если заполнен → attached `ERROR` с `PROTECTION_PLACEMENT_FAILED`; **персистится** — операнд разбора тропы |
| `failReason` | `failReason` | диагностика ошибки; в колонку не садится (лог) |

У `attachAlgoOrds` нет полноценного `state` как у ordinary order —
attached резолвится по набору фактов (`docs/lifecycles/Order.md`).

### Конвертация (OKX)

`empty string → null`; numeric string → `BigDecimal`; `state`
остаётся raw в `externalStatus` (резолвинг — позже).

### `Domain Order → OKX request`

См. source-agnostic секцию выше. OKX-специфичные дополнения к create
body (через adapter, не из domain): `ccy` (валюта маржи — для
USDT-SWAP `USDT`); `tag` (метка, `tb`); `stpMode`
(`cancel_maker`/`cancel_taker`/`cancel_both`); `expTime` (header,
ms — «срок годности запроса»).

**Attached TP/SL при create (`attachAlgoOrds[*]`):**
`attachAlgoClOrdId` (client id), `tpTriggerPx`/`tpTriggerRatio`,
`tpOrdPx` (`-1` = market), `tpOrdKind` (`condition`/`limit`, default
`condition`), `slTriggerPx`/`slTriggerRatio`, `slOrdPx` (`-1` =
market), `tpTriggerPxType`/`slTriggerPxType` (`last`/`index`/`mark`,
default `last`), `sz` (для split-TP), `amendPxOnTriggerType` (`0`/`1`
cost-price SL для split). `tpTriggerPx` vs `tpTriggerRatio` —
взаимоисключимо; аналогично SL.

**`slTriggerPxType` заполняется всегда, биржевой default не
используется**: источник значения —
`AttachedAlgoOrder.triggerPriceType`, доезжающее из
`StopLossSettings.triggerPriceType` стратегии через
`AttachedProtectionPayload`. Дом принципа —
`docs/rules/live-risk-protection.md`.

**Amend OKX-specific — доменом не используется** (REPLACE-only,
`docs/rules/replace-not-amend.md`): амендные поля биржи
(`reqId`/`cxlOnFail`/`pxAmendType`/`attachAlgoOrds[*]` с
`new*`-полями) остаются описанными в контракте поверхности
(`docs/integrations/okx/contracts/order.md`,
`OrderOkxResponse.md`), в request-mapping домена не входят.
Ремодел attached protection: до fill родителя — REPLACE
родительского ордера вместе с attach-настройками; после fill
attached материализуется в standalone algo —
обычный algo-REPLACE.

### Резолв статуса

Таблица резолва сырого статуса, ветка отказа и правило
write-once для причины закрытия —
`docs/spec/external-status-resolution.json` (`orderStatus`,
`refusalReason`, `closeReasonApplied`). Причина отмены берётся из
нашего намерения, не из статуса источника: отмена по
защитному механизму биржи резолвится так же, как обычная — и, как всякая
отмена без нашего намерения, причину всё равно получает
(`docs/rules/external-status-resolution.md`).

### OKX evidence-cycle / not found

Полный цикл — **четыре источника**:

| # | Эндпоинт | Параметры запроса | Матч |
|---|---|---|---|
| 1 | `GET /api/v5/trade/order` | `instId` + `ordId` либо `clOrdId` | точечный |
| 2 | `GET /api/v5/trade/orders-pending` | `instId` | по `ordId` / `clOrdId` в ответе |
| 3 | `GET /api/v5/trade/orders-history` | `instId`, `instType` | там же |
| 4 | `GET /api/v5/trade/orders-history-archive` | то же (если history не покрывает период) | там же |

Поиск: есть `externalId` → по `ordId`; нет → по `clOrdId = internalId`.
Терминал исчерпанного цикла — `MISSING_AFTER_REFRESH`.

### OKX: цикл добычи материализованной защиты

**Это второй цикл, а не пятая нога первого.** Он ищет **другой предмет**
(встроенную защиту, развёрнутую источником в самостоятельную условную
заявку), **другим ключом** (`algoClOrdId`) и запускается **после** того,
как родитель найден, — на исходе `SEARCH_MORE`
(`docs/lifecycles/Order.md`). Правило обрыва первого цикла на него не
распространяется, и терминал у него свой.

| # | Эндпоинт | Параметры запроса | Что даёт |
|---|---|---|---|
| 1 | `GET /api/v5/trade/orders-algo-pending` | `instType`, `instId`, `ordType=conditional` | живую запись — матч по `algoClOrdId` **в ответе**; нога живых, идёт всегда |
| 2 | `GET /api/v5/trade/orders-algo-history`, вызов на `state=effective` | `instType`, `instId`, `ordType=conditional`, `state=effective` | сработавшую запись — терминал `TRIGGERED`; нога разбора истории (ветвь `ANALYSE_HISTORY` второй ступени) |
| 3 | `GET /api/v5/trade/orders-algo-history`, вызов на `state=canceled` | то же со `state=canceled` | снятую запись — терминал `CANCELED` по стоящему намерению; та же ветвь |
| 4 | `GET /api/v5/trade/orders-algo-history`, вызов на `state=order_failed` | то же со `state=order_failed` | сработавшую и неисполнившуюся запись — терминал `ERROR` / `PROTECTION_TRIGGER_FAILED` с фактическим кодом отказа; та же ветвь |

**У истории условных заявок временно́го окна нет, а `state` либо `algoId`
обязателен** (`docs/integrations/okx/contracts/algo-order.md`; рантайм-факт
прогона: без него `code=50015` «Either parameter state or algoId is
required»). `algoId` материализованной записи нам неизвестен по построению
— он не равен `attachAlgoId` родителя, — поэтому обязательный операнд
закрывается **`state`**, и ног у разбора истории **три — по числу
терминальных значений `state` у контракта эндпоинта** (`effective`,
`canceled`, `order_failed`): перечень ног выводится из контракта, а не из
пары значений, которые имелись в виду, — неопрошенный `order_failed`
исчерпывал бы разбор на существующем факте (сработала и не исполнилась —
запись достижима гонкой срабатывания защиты с закрытием позиции другим
актором). Ноги разбора идут только на ветви `ANALYSE_HISTORY` второй
ступени; исходы каждой — таблица разбора в `docs/lifecycles/Order.md`
§«Исход ненайденности — вторая ступень». Глубина задаётся пагинацией
`after` по `algoId` и `limit` ≤ 100, не окном.

**Названное ограничение: опрашиваемые `state` — не весь домен состояний
записи.** Инвентарь источника несёт и `partially_effective` /
`partially_failed`; запись защиты в таком состоянии не найдёт ни одна
нога разбора — исход «пустой разбор». **Оснований пустоты четыре, а не
одно:** эти состояния; горизонт истории в три месяца; исчерпание
пагинации; состояние `pause`, которого нет ни в перечне ног разбора, ни
в выдаче ноги живых. Четвёртую ногу построить нечем: параметр `state`
истории значений `partially_*` не принимает, `algoId` записи неизвестен.

**Исход пустого разбора — «не определено» плюс сигнал, терминал не
ставится** (дом — `docs/lifecycles/Order.md` §«Пустой разбор истории»).
Прежняя редакция ставила благоприятный терминал с диагностически ложной
причиной; денежное направление при этом держалось предусловием самой
ветви, то есть косвенно. Снятие ограничения — точечная нога details по
`algoClOrdId`, если её поведение на материализованной источником записи
будет подтверждено наблюдением.

Фильтра по клиентскому идентификатору у обоих эндпоинтов нет
(`docs/integrations/okx/contracts/algo-order.md`), поэтому запрос идёт по
инструменту и типу, а совпадение ищется **в ответе**: `algoClOrdId`
записи равен `attachAlgoClOrdId` родителя. Обратной ссылки на родителя у
записи нет, и `algoId` записи с `attachAlgoId` родителя не совпадает —
клиентский идентификатор единственный сходящийся операнд.

**Исчерпанный цикл даёт вторую ступень исхода**
(`searchExhaustedOutcome`): `PROTECTION_LOST` либо `ANALYSE_HISTORY`, а не
`MISSING_AFTER_REFRESH` — тот терминализует **заявку**, а здесь предмет
другой.

**`ordType = conditional` верен, пока встроенная защита одноместна.**
Перечень `AttachedAlgoOrder.Type` сегодня несёт только
`ATTACHED_STOP_LOSS`; появление пары «тейк + стоп» дало бы `oco`, и
запрос его не увидит — условие названо здесь, чтобы расширение перечня
не прошло молча.

**Форма записи — `AlgoOrderOkxResponse`**, а не элемент
`attachAlgoOrds[*]`: источник разворачивает встроенную защиту в
самостоятельную условную заявку. Её маппинг в снапшот встроенной защиты
описан отдельной таблицей этого же файла. Order-fill-метрики
(`accFillSz` → `accumulatedFillSize`, `avgPx` → `averagePrice`, `fee`)
приходят готовыми из того же `OrderOkxResponse` — отдельной fill-команды нет.
Доп. факты сделки (`REFRESH_POSITION_COMMAND`) запрашиваются отдельной командой;
`RefreshOrderExecutor` не сопровождает сделку целиком.

### `AlgoOrderOkxResponse` → `AttachedAlgoOrderExternalSnapshot`

Тропа второго цикла: найденная самостоятельная условная заявка
приземляется в снапшот **встроенной** защиты — доменная строка у неё та
же, потому что это она и есть, развёрнутая источником.

| Поле источника | Поле снапшота | Замечание |
|---|---|---|
| `algoClOrdId` | ключ матча | равен `attachAlgoClOrdId` родителя; связь только по нему |
| `algoId` | `externalId` | **не** равен `attachAlgoId` родителя |
| `state` | `externalStatus` | сырой статус **самостоятельной записи**: у неё он есть, в отличие от элемента `attachAlgoOrds[*]` родителя. Через резолвер внешних статусов (`docs/spec/external-status-resolution.json`) **не идёт**: его операнд `entity` защиту не принимает, а словарь отказных причин чужой — исход кодирует **нога, нашедшая запись** (таблица разбора — `docs/lifecycles/Order.md`), `state` — диагностика. Живость в **обеих** тропах предъявления выводит один предикат `attachedBecomesActive`: предъявленная самостоятельная запись — его второй дизъюнкт (`docs/spec/order-lifecycle.json`) |
| `failCode` | `failCode` | код отказа; операнд ветви `attachedFailsToPlace` в `docs/spec/order-lifecycle.json` и **операнд разбора тропы** у записи `state=order_failed` — персистится |
| `failReason` | `failReason` | причина отказа; в снапшоте поле уже объявлено, в колонку не садится (лог) |
| `sz` | `size` | объявленный размер записи — операнд покрытия (`docs/spec/protection-coverage.json`) |
| `slTriggerPx` / `slOrdPx` | уровень защиты | сторона — как у элемента `attachAlgoOrds` |
| `slTriggerPxType` | `triggerPriceType` | **операнд сверки объявленной базы** `MARK`; поле объявлено инвентарём и этой формы (`docs/models/integrations/okx/AlgoOrderOkxResponse.md`) |
| `reduceOnly` | — | в снапшот не переносится; adapter сверяет его при разборе ответа и на несовпадении бросает нарушение биржевого инварианта (`docs/rules/external-status-resolution.md`) |

**Вход резолвера при этом двухместный:** снапшот встроенной защиты
приходит либо из тела родителя (`attachAlgoOrds[*]`), либо из
самостоятельной записи цикла добычи защиты
(`docs/components/AttachedAlgoOrderStateResolver.md`).

### OKX pagination

`after`/`before` — якорь по `ordId` (не времени), `limit ≤ 100`.
Глубокая выкачка: `after = min(ordId)` → следующая страница. История
7 дней дополнительно поддерживает `begin`/`end` по `cTime` (ms).

## Целевые расхождения с текущим кодом (target refactoring)

- `createOrder` не должен принимать `tradeMode`/`positionSide`
  аргументами — `OkxIntegrationService` сам ставит `isolated`/`net`.
- `OrderResponse.state` комментарий: raw статус OKX; pending —
  `live`/`partially_filled`; details/history — `filled`/`canceled`/
  `mmp_canceled` и др. terminal.
- `OrderResponse.reduceOnly` → только adapter invariant validation,
  не в `OrderExternalSnapshot`.
