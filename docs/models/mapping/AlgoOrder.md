# AlgoOrder — mapping между слоями

## На какой вопрос отвечает этот файл

Как `AlgoOrder` переходит между слоями.

## Source-agnostic ядро

### `AlgoOrderExternalSnapshot` → `AlgoOrder`

| Snapshot field | Domain | Семантика |
|---|---|---|
| `internalId` | `AlgoOrder.internalId` | stable client id |
| `externalId` | `AlgoOrder.externalId` | биржевой algo id |
| `externalInstrumentId` | `AlgoOrder.externalInstrumentId` | биржевое имя инструмента (`instId`), переносится по имени. **Атрибут границы, а не поле строки:** колонки под него нет, в наш числовой идентификатор на границе не резолвится — дом правила `docs/models/domain/core/AlgoOrder.md`. Читает его **счёт-широкий срез**: строки там по разным инструментам, и адресует строку инструментом только это поле (`docs/components/AnomalyJob.md`) |
| `externalStatus` | — | raw статус (diagnostic, не FSM) |
| `failCode` | `AlgoOrder.failCode` | внешний код ошибки |
| `externalSize` | `AlgoOrder.externalSize` | фактический размер срабатывания |
| `externalPrice` | `AlgoOrder.externalPrice` | фактическая цена срабатывания |
| `externalTriggerTime` | `AlgoOrder.externalTriggerTime` | время срабатывания |
| `linkedOrderExternalIds` | `AlgoOrder.linkedOrderExternalIds` | связанные ordinary order ids (сохраняем; потребителя пока нет) |
| `side` | — | эхо стороны; на прочитанной копии — `AlgoOrder.direction`, переведённое из словаря площадки коннектором. **На строку не переносится**: сторона — наше намерение, эхо — операнд его сверки (подраздел сверки эха ниже) |
| `reduceOnly` | — | эхо признака «только уменьшать»; на прочитанной копии — `AlgoOrder.positionReducingOnly`. **На строку не переносится** — операнд сверки намерения (подраздел сверки эха ниже) |
| `condition.trigger.stopLoss.externalValue` | — | внешнее значение SL trigger |
| `condition.trigger.stopLoss.externalType` | — | эхо базы SL, переведённое коннектором в доменный `TriggerPriceType`; **на строку не переносится** — операнд сверки объявленной базы |
| `condition.trigger.takeProfit.externalValue` | — | внешнее значение TP trigger |
| `condition.trigger.takeProfit.externalType` | — | эхо базы TP; то же, что у SL |
| `condition.trailing.activationPrice.externalValue` | — | цена активации trailing |
| `condition.trailing.externalPrice` | **`AlgoOrder.condition.trailing.externalPrice`** | текущее значение trailing (`moveTriggerPx`) — **операнд `stopCurrent`** трейлинговой защиты |
| `externalCreatedAt` | `AlgoOrder.externalCreatedAt` | |
| `externalModifiedAt` | `AlgoOrder.externalModifiedAt` | (есть в history) |
| — | `AlgoOrder.externalLive` | **из записи не переносится**: наблюдённую живость пишет исполнитель добычи по итогу **всего** цикла, а не одной записи — ненайденность полным циклом и отказ разбора статуса записи не имеют вовсе (`docs/components/RefreshAlgoOrderExecutor.md`) |

Если источник не возвращает тип цены активации trailing,
`condition.trailing.activationPrice.externalType` остаётся `null` —
не нарушение invariant.

**Из условия на строку садится ровно одно поле —
`condition.trailing.externalPrice`, и только непустым.** Всё прочее в
условии строки — наша декларация: уровни, базы, откат и цена активации
пишутся при постановке, а их эхо либо не переносится, либо сверяется. Перенос
делает маппер ядра `updateFromFetched(fetched, @MappingTarget algoOrder)`
(коннектор отдаёт доменную модель, снапшот живёт у него) с
`nullValuePropertyMappingStrategy = IGNORE`
(`.claude/rules/codestyle.md`); замена условия целиком затёрла бы
декларацию эхом, а игнорирование его целиком теряет наблюдённый уровень
трейлинга: без него трейлинг не несёт действующего уровня и покрытием не
считается (`docs/spec/protection-coverage.json`, величина
`carriesActiveStopLevel`), а четвёртое число сделки его уровня не видит. Ветвь
трейлинга у заявки триггерного типа переносом не заводится. В jsonb
`condition` сериализуется целиком **после** переноса; частичного апдейта
колонки не вводится.

**Плоское `externalPrice` и `condition.trailing.externalPrice` — разные
факты, и их нельзя путать:** первое — `actualPx` (фактическая цена
срабатывания, пусто у живой защиты), второе — `moveTriggerPx` (текущий
уровень трейлинга). Совпадение имён — следствие конвенции «`external*` —
значение источника», а не одного значения в двух местах.

### `Domain AlgoOrder → request`

| Domain source | Request field |
|---|---|
| `Instrument.externalId` | `instId` |
| const `isolated` (adapter) | `tdMode` |
| const `net` (adapter) | `posSide` |
| `AlgoOrder.direction` (`BUY`/`SELL`) | `side` |
| `AlgoOrder.conditionType` (через resolver) | `ordType` |
| `AlgoOrder.size` | `sz` (контракты для SWAP/FUTURES) |
| `AlgoOrder.internalId` | client algo id |
| `AlgoOrder.positionReducingOnly` | `reduceOnly` |
| `Condition.trigger.stopLoss.value/.type` | SL trigger / type |
| `Condition.trigger.takeProfit.value/.type` | TP trigger / type |
| `Condition.trailing.trailingPercents` | trailing-percents поле источника — **долей**, а не процентами (перевод ниже) |
| `Condition.trailing.trailingStepValue` | trailing-value поле источника; писателя в домене нет — тип `TRAILING_VALUE`, которому оно принадлежит, создание стратегии отвергает (`docs/models/domain/core/AlgoOrder.md`), и поле уезжает пустым |
| `Condition.trailing.activationPrice.value` | trailing activation price |
| `Condition.trailing.activationPrice.type` | — : у постановки трейлинга поля базы нет, площадка считает активацию и откат по `last` (`docs/models/domain/core/AlgoOrder.md`) |

`closeFraction` (доля позиции при срабатывании) на первом этапе не
используется: размер считает `SizeCalculator`
(`closeFractionPercents + экспозиция транша + InstrumentExternalRules →
AlgoOrder.size → sz`). База доли — экспозиция **транша**, а не нетто-размер
позиции: на сетке нетто относится к нескольким траншам сразу
(`docs/spec/order-sizing.json`, операнд `tranche.exposure`).

### Status resolver (source-agnostic)

`externalStatus` → `AlgoOrder.Status` через
`AlgoOrderExternalStatusResolver`
(`docs/components/AlgoOrderExternalStatusResolver.md`). FSM raw не
использует. Таблица — per-source.

### Evidence-cycle / not found

`ExternalNotFoundException` — только после полного цикла per-source
(см. подразделы). Пустой `data=[]` одного endpoint — не финал. После
полного цикла без находки → `AlgoOrder.ERROR` + `MISSING_AFTER_REFRESH`;
у неотправленной заявки → `AlgoOrder.CANCELED` + `NOT_PLACED` без каскада
(`docs/lifecycles/AlgoOrder.md`). Наблюдённая живость (`externalLive`)
пишется на каждой ветви цикла, включая ненайденность и отказ разбора, кроме
расхождения эха (подраздел сверки эха ниже), —
таблица наблюдений в том же доке.

### Сверка эха

Добытая запись сверяется с нашей строкой по трём осям — у каждой есть
правило, опирающееся на исполненность нашего намерения **до** срабатывания:

```text
эхо reduceOnly                 == AlgoOrder.positionReducingOnly
эхо стороны                    == AlgoOrder.direction
эхо базы ноги SL и ноги TP     == объявленная база той же ноги (condition.trigger.*.type)
```

- **Признак «только уменьшать»** — на нём держится безвредность
  перепокрытия живой защитой; почему сверка есть у отдельной условной заявки
  и нет у обычной — `docs/integrations/okx/rules/reduce-only-invariant.md`.
- **Сторона** — защита, стоящая не той стороной, покрытием считается, а
  позицию не сокращает (`docs/rules/live-risk-protection.md`).
- **База триггера** — защита срабатывает только по `MARK`
  (`docs/models/domain/core/AlgoOrder.md`); эхо — единственное, чем
  уличается площадка, применившая иную базу. Та же сверка у встроенной
  защиты — `docs/models/mapping/Order.md`. У трейлинга базы нет у самой
  постановки, и ось к нему не применяется.

**Пустое эхо либо пустая декларация сверку не запускают:** молчание
источника — недобытый факт, а реакция на расхождение — аварийный контур
всего счёта. **Эхо в словарь домена переводит коннектор** (сторона — в
`Direction`, база — в `TriggerPriceType`, признак — в `Boolean`); значение
вне формы контракта роняет разбор на его стороне. **Сверяет добыча
условной заявки в ядре** (`docs/components/RefreshAlgoOrderExecutor.md`):
ожидаемое — наша строка, которой коннектор при чтении не видит.

**Расхождение — контролируемый отказ чтения**
(`ExternalInvariantViolationException`, реакция —
`docs/rules/controlled-exchange-exceptions.md`): это факт о чтении, а не о
заявке (`docs/lifecycles/AlgoOrder.md`), поэтому строка этим проходом не
пишется вовсе — ни перенос фактов, ни статус, ни наблюдённая живость:
ответ, нарушивший контракт, наблюдением не является.

**Не сверяются, и это решение:**

- `tdMode` и `posSide` — константы нашего запроса, площадка их у записи не
  меняет; посылку, которую они выражают, — режим счёта и режим позиций —
  меряет преконтроль на снимке средств (`docs/integrations/okx/contracts/account-config.md`).
- `ordType` — обратного маппинга нет (`conditional` покрывает несколько
  типов условия), а две ноги цикла из трёх запрашивают записи уже
  отфильтрованными по нему.
- Размер — `size` есть намерение, `externalSize` — факт срабатывания;
  расхождение штатно при частичном срабатывании. `actualSide` не
  хранится.

## OKX

### `AlgoOrderOkxResponse` → `AlgoOrderExternalSnapshot`

См. инвентарь нативных полей —
`docs/models/integrations/okx/AlgoOrderOkxResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `algoClOrdId` | `internalId` |
| `algoId` | `externalId` |
| `instId` | `externalInstrumentId` |
| `state` | `externalStatus` (raw) |
| `failCode` | `failCode` |
| `actualSz` | `externalSize` |
| `actualPx` | `externalPrice` |
| `triggerTime` | `externalTriggerTime` |
| `ordId` / `ordIdList` | `linkedOrderExternalIds` |
| `side` | `side` — литерал источника; в `Direction` переводится на переходе снапшота в домен |
| `reduceOnly` | `reduceOnly` (`"true"`/`"false"` → `Boolean`) |
| `slTriggerPx` / `slTriggerPxType` | `condition.trigger.stopLoss.externalValue` / `externalType` (база в `TriggerPriceType` — на переходе снапшота в домен; пусто и вне перечня → пусто) |
| `tpTriggerPx` / `tpTriggerPxType` | `condition.trigger.takeProfit.externalValue` / `externalType` (то же) |
| `activePx` | `condition.trailing.activationPrice.externalValue` |
| `moveTriggerPx` | `condition.trailing.externalPrice` |
| `cTime` | `externalCreatedAt` |
| `uTime` | `externalModifiedAt` (есть в history) |

`ordType`, `actualSide`, `tdMode`, `posSide` — не маппятся и не
сверяются (почему — подраздел сверки эха выше); `closeFraction` не
используется вовсе; `side` и `reduceOnly` едут в снапшот только
операндами сверки.

### `conditionType → ordType` (OKX)

```text
STOP_LOSS / TAKE_PROFIT / PARTIAL_STOP_LOSS / PARTIAL_TAKE_PROFIT -> conditional
OCO_FULL                                                          -> oco
TRAILING_PERCENTS / TRAILING_VALUE                                -> move_order_stop
```

Маппинг односторонний (`conditionType → ordType`): обратный не
делаем (`conditional` покрывает несколько `ConditionType`).

### Семья algo и cancel-endpoint (И-1, исход (а))

Из `ordType` выводится семья, по которой ветвится cancel-путь
(`CANCEL_ALGO_ORDER_COMMAND`):

```text
conditional / oco / trigger   -> ordinary -> POST /trade/cancel-algos
move_order_stop (TRAILING_*)  -> advance  -> POST /trade/cancel-advance-algos
```

Advance-ветка несёт пометку «endpoint вне текущего офдока, требует
runtime-подтверждения» — находка И-2
(`docs/integrations/okx/contracts/algo-order.md`
cancel-пути). Amend advance-семьи биржей не поддерживается (И-3);
следствие снято решением REPLACE-only — домен не амендит ничего
(`docs/rules/replace-not-amend.md`).

### Резолв статуса

Таблица резолва сырого статуса и ветки отказа —
`docs/spec/external-status-resolution.json` (`algoStatus`,
`refusalReason`, `closeReasonApplied`). Смысл состояний —
`docs/lifecycles/AlgoOrder.md`.

### OKX request mapping — дополнения

OKX-специфичные поля create body (через adapter): `algoClOrdId` ←
`AlgoOrder.internalId`; `callbackRatio` ←
`Condition.trailing.trailingPercents`, **сдвинутый на два знака**: домен
несёт откат процентами (`0.8` = 0,8%), площадка ждёт долю (`0.01` = 1%), и
перевод делает граница коннектора — без него откат уезжал бы в сто раз
шире объявленного; `callbackSpread` ←
`Condition.trailing.trailingStepValue`; `activePx` ←
`Condition.trailing.activationPrice.value` (если задан); SL/TP
параметры — `slTriggerPx`/`slTriggerPxType`/`slOrdPx` (`-1` =
market), `tpTriggerPx`/`tpTriggerPxType`/`tpOrdPx` (`-1` = market).

**Amend — доменом не используется** (REPLACE-only,
`docs/rules/replace-not-amend.md`): амендного request-mapping
нет; ремоделирование любого algo — REPLACE-оркестрация
(place новой с `replacesInternalId` → подтверждение фактом →
cancel старой, `REPLACED_BY_STRATEGY`). Биржевой amend-контракт
(только Stop/Trigger; advance не амендится — И-3, исторический
контекст выбора REPLACE-only) — поверхность,
`docs/integrations/okx/contracts/algo-order.md`.

**Cancel**: `instId`, `algoId` (предпочтительно) / `algoClOrdId`.
Если `externalId` неизвестен — сначала refresh/search по
`algoClOrdId`, затем cancel.

### OKX evidence-cycle / not found

Полный цикл: `GET /trade/order-algo` → `orders-algo-pending` →
`orders-algo-history`. Поиск: есть `externalId` → по `algoId`; нет →
по `algoClOrdId`.

**У ноги истории обязателен `state` либо `algoId`**
(`docs/integrations/okx/contracts/algo-order.md`; рантайм-факт прогона:
иначе `code=50015`). При непустом `externalId` обязательный операнд
закрывается `algoId`; при пустом — **`state`**, и нога истории идёт
двумя вызовами, `state=effective` и `state=canceled`. Терминальных
значений `state` у эндпоинта **три** — третье, `order_failed`, здесь не
опрашивается отдельным вызовом: запись в любом `state` предъявляет
первая нога цикла — точечный `GET /trade/order-algo` по `algoClOrdId`,
которому операнд `state` не нужен (наша запись адресуется своим
клиентским идентификатором). Временно́го окна у эндпоинта нет.

Пустой `data=[]` одного endpoint — не финал. Цикл
обходит `RefreshAlgoOrderExecutor` **внутри одной команды**
`REFRESH_ALGO_ORDER_COMMAND`; терминал `MISSING_AFTER_REFRESH` выносит он же (см.
`docs/rules/command-lifecycle.md`).

## Целевые изменения кода (checklist, не runtime-логика)

`AlgoOrder`: убрать `strategyActionId`/`externalType`/
`externalDirection`/`externalPositionSide`; добавить
`positionReducingOnly`, `PARTIALLY_COMPLETED`, `linkedOrderExternalIds`,
`externalSize`/`externalPrice`/`externalTriggerTime`, строгие
transition-методы. `Condition`: убрать `closeFraction`, оставить
`type`/`trigger`/`trailing`. `*Condition` constructors: убрать
`closeFraction`. `AlgoOrderConditionValidator`: валидировать
`type → trigger/trailing`, не `closeFraction`. `SizeCalculator`:
`closeFractionPercents + tranche exposure + instrument rules → size`. OKX
create algo mapper: `size → sz`, `closeFraction` не как основной
механизм первого этапа.
