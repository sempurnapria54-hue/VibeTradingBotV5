# PositionCloseResult — mapping между слоями

## На какой вопрос отвечает этот файл

Как положение закрытой позиции источника ложится на `Position`.

## Контекст

Mapping-слой **второй ноги `REFRESH_POSITION_COMMAND`** (live → positions-history,
`docs/components/RefreshPositionExecutor.md` §Evidence-cycle). Здесь **нет
отдельной persisted доменной сущности**: положение закрытия ложится
полями на **`Position`** (`docs/models/domain/core/Position.md`
§«Положение закрытия»), откуда его читает финализатор и пишет число в
`Deal.resultProfit`; категорийная разбивка — в `DealCashFlow`
(`docs/models/mapping/DealCashFlow.md`). Граничный объект —
`PositionCloseResultExternalSnapshot` (не persisted; как
`PositionExternalSnapshot`), единственное, что выходит за
`IntegrationService`/adapter (`docs/rules/raw-exchange-dto-boundary.md`).

Native-модель — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.
Контракт endpoint'а — `docs/integrations/okx/contracts/position.md`
§«История закрытых позиций». Источник числа (что за данные и почему
positions-history) — `docs/decisions/result-profit-source.md`; механика
финализации — `docs/decisions/pnl-finalization-mechanics.md`.
Доменное число и правила PnL — `docs/models/domain/aggregate/Deal.md`
§«Итоговый PnL». Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`.

Снапшот, как `PositionExternalSnapshot`, **не требует отдельного
`*ExternalSnapshot.md`** (нет самостоятельного persisted содержания;
`docs/models/externalSnapshot/README.md`) — его поля зафиксированы ниже.

Текущие источники: **OKX**.

## Source-agnostic ядро

### Mapping-flow

```text
positions-history REST response -> raw OkxPositionsHistoryResponse
  -> IntegrationService validation
  -> PositionCloseResultMapper -> PositionCloseResultExternalSnapshot
  -> RefreshPositionExecutor (нога 2 evidence-cycle)
  -> Position (поля положения закрытия, persisted)
  -> FinalizeDealExitExecutor | MarkDealEmergencyClosedExecutor
  -> Deal.resultProfit + Deal.closeOutcome (признак отбора)
```

Raw DTO не выходит за пределы `IntegrationService` / adapter-layer;
executor работает только с validated normalized snapshot.

**Где факт живёт между добычей и потреблением** (H1/H3, `GAPS_CLOSE_7`,
ревизует H13 `GAPS_CLOSE_6`): **на строке `Position`**. Прежняя редакция
объявляла снапшот транзитным без durable-дома и потому уводила добычу во
**вложенный шаг** финализирующего действия — конструкция, которой канон
командного слоя не знает, и которая оставляла окно линковки bills без
верхней границы (у `REFRESH_BILLS_COMMAND`, идущей отдельным проходом, доступа к
чужой памяти нет). Посылка снята: добытое **персистится на `Position`**,
границу прохода FSM пересекает штатно, вложенность не нужна вовсе.
Число на `Deal` по-прежнему пишет **финализатор**
(`docs/decisions/pnl-finalization-mechanics.md` реш.2), не refresh-executor:
`Position` несёт **биржевой факт**, `Deal` — **посчитанное число**.

### `PositionCloseResultExternalSnapshot`

| Snapshot field | Тип | Семантика |
|---|---|---|
| `externalRealizedPnl` | `BigDecimal` | готовый net realized P&L (net от всех издержек, посчитан биржей) |
| `externalResultCurrency` | `String` | валюта, в которой посчитан `realizedPnl`; **проверяемый признак**, не источник `Deal.resultProfitCurrency` (H10) |
| `externalCloseAveragePrice` | `BigDecimal` | средняя цена фактического выхода (`closeAvgPx`) — операнд калибровки запаса на проскок **на тропе attached-SL** (H26 `DOCS_CHECK_10`, операнд уточнён H21 `DOCS_CHECK_11`) |
| `externalCloseType` | `String` | тип последнего закрытия (`1`–`6`; ликвидация/ADL = `3`–`6`) |
| `externalRealizedPnlGross` | `BigDecimal` | `pnl` записи — реализованный P&L **до** издержек; правый операнд первой пары раздельной сверки (H19 `DOCS_CHECK_12`) |
| `externalFee` | `BigDecimal` | `fee` записи — знаковая комиссионная компонента (минус — комиссия, плюс — ребейт; **сырой знак**, как у `DealCashFlow.externalFee`); правый операнд второй пары сверки (H19 `DOCS_CHECK_12`) |
| `externalFundingCost` | `BigDecimal` | накопленный funding закрытой позиции (`fundingFee`), **нормализованный по знаку**: издержка, положительна когда фондирование уплачено (H20 `DOCS_CHECK_12`). Операнд де-микширования R-мультипликатора и правый операнд третьей пары сверки |
| `externalLiquidationPenalty` | `BigDecimal` | `liqPenalty` записи — ликвидационный штраф, **сырой знак** источника; правый операнд четвёртой пары сверки (H7 `DOCS_CHECK_13`) |
| `externalPosId` | `String` | биржевой id позиции (ключ адресации записи; на create-тропе — **данные**, см. ниже) |
| `externalInstrumentId` | `String` | сырой `instId` записи — операнд **структурной валидации** «запись относится к запрошенному инструменту» (H18 `DOCS_CHECK_14`); на `Position` не приземляется — сверка, не данные |
| `direction` | `Position.Direction` | направление закрытой позиции — операнд **create-тропы** (H4 `DOCS_CHECK_11`); **доменное значение, нормализованное в слое интеграции** (H7 `DOCS_CHECK_15`), симметрично `PositionExternalSnapshot.direction` живой ноги. Имя без префикса `external` — по признаку конвенции «ниже маппинга величина обязана быть проектной нормалью» (`docs/models/domain/other/InstrumentExternalRules.md` §«Конвенция `external*`») |
| `externalCreatedAt` | `OffsetDateTime` | время создания записи positions-history (`cTime`) — операнд **create-тропы** и нижней границы окна линковки (H4 `DOCS_CHECK_11`) |
| `externalModifiedAt` | `OffsetDateTime` | время обновления записи positions-history |

Числовые/временные поля нормализуются при построении снапшота (string →
`BigDecimal`/`OffsetDateTime`, empty → null), уже провалидированные как
parseable. Тип времени — `OffsetDateTime` по конвенции проекта; **имя** поля
времени источника — конвенционное `externalModifiedAt` (симметрично
`PositionExternalSnapshot`), собственного `externalUpdatedAt` снапшот больше
не заводит (H25, `GAPS_CLOSE_7`;
`docs/models/domain/other/Auditable.md` §«Единое имя времени источника»).

**`closeAvgPx` возвращён — у него назван потребитель** (H26
`DOCS_CHECK_10`, решение пользователя), но **основным операндом
калибровки быть перестал** (H21 `DOCS_CHECK_11`): он смешивает частичные
TP с исполнением стопа и не отличает стоп-аут от прочих выходов. Основной
операнд — `AlgoOrder.externalPrice` по стоповым типам условия; за
`closeAvgPx` осталась подвыборка **attached-SL**, у которой собственной
цены исполнения нет (`docs/models/domain/core/Position.md` §«Цена
фактического выхода»). Правило «поле заводится вместе с потребителем»
держится — потребитель сузился, но остался названным.

**`fundingFee`, `cTime` и `direction` внесены в снапшот** (H20 и H4
`DOCS_CHECK_11`, решения пользователя): у `fundingFee` потребитель —
де-микширование R-мультипликатора, у `cTime`/`direction` — ратифицированная
create-тропа (§ниже). До этого все три числились в отброшенных, и
операнды ратифицированных потребителей за границу `IntegrationService`
не выходили.

**`pnl` и `fee` возвращены в снапшот** (H19 `DOCS_CHECK_12`, решение
пользователя): это **прямая цена** выбранной формы контроля целостности —
сверка расширена до **раздельных пар по категориям** (Σ по категории
разбивки против соответствующего числа биржи), и без этих двух полей правых
операндов двух из четырёх пар не существует
(`docs/components/FinalizeDealExitExecutor.md` §«Расчёт прибыли и сверка»).
Прежде оба числились выведенными как «слагаемые net, потребителя нет» —
потребитель появился.

**`liqPenalty` возвращён в снапшот** (H7 `DOCS_CHECK_13`, решение
пользователя): та же прямая цена за **четвёртую** пару сверки. Прежний
довод, выводивший категорию `LIQ_PENALTY` из-под контроля («отдельного
числа биржи под неё в записи нет»), **ложен** — поле названо и контрактом
источника, и нативным инвентарём
(`docs/integrations/okx/contracts/position.md` §История). Четыре пары
исчерпывают состав net'а (`realizedPnl = pnl + fee + fundingFee +
liqPenalty`), поэтому отдельная суммарная сверка Σ`amount` ↔ net
**снята**: она не покрывает ничего сверх четырёх пар, а её область
(Σ`amount` по всей разбивке) шире состава net'а на строки, которых в
net'е нет по построению.

### Знак `fundingFee` — нормализуется здесь, и только здесь

**`fundingFee` приходит от источника знаковым** (в тождестве
`realizedPnl = pnl + fee + fundingFee + liqPenalty` слагаемые знаковые, то
есть у уплаченного фондирования значение отрицательно). **При маппинге в
доменный снапшот знак нормализуется**: ниже маппинга
`externalFundingCost` — **издержка**, положительная когда фондирование
уплачено (H20 `DOCS_CHECK_12`, решение пользователя, вариант 1).

- **Приведение выполняется в одном названном месте — здесь.** Ни
  финализатор, ни расчёт R-мультипликатора, ни сверка знак не трогают.
  Симметрично уже принятому решению по ставке комиссии
  (`docs/decisions/pnl-finalization-mechanics.md` реш.4: знак снимается
  **одним местом**).
- **Следствие для формулы де-микширования:** R-мультипликатор считается от
  `resultProfit + externalFundingCost` — **сложение**, а не вычитание
  (time-cost снимается с числителя). Имя поля (`...Cost`) при этом честно:
  оно называет издержку и её же содержит.
- **Следствие для сверки:** `DealCashFlow.amount` остаётся **сырым**
  знаковым, поэтому третья пара сравнивает Σ`amount` по `FUNDING` с
  `−externalFundingCost`. Расхождение конвенций названо явно и живёт в
  одном месте, а не всплывает у первого потребителя.
- **Цена ошибки, которую это снимает.** При чтении имени `...Cost` как
  «положительная издержка» **без** нормализации вычитание убрало бы funding
  **второй раз**: R занижался бы на `2×|funding|/R` — ошибка
  **направленная** (всегда вниз для плательщика funding, то есть типичного
  лонга по перпу), **коррелированная с длительностью удержания** (искажает
  ровно ту ось, ради выпрямления которой де-микширование вводилось) и
  **невидимая** (результат остаётся правдоподобным числом). Проект уже
  проходил этот класс на ставке комиссии (`GAPS_CLOSE_4` H2).
- **Фактический знак у источника подтверждается прогоном** — кейс
  **`AG1.7`** (`.claude/tests/source-api/okx/plan.md`), грунт уже заведён.
  Если прогон покажет, что источник отдаёт `fundingFee` уже как
  положительную издержку, меняется **реализация** нормализации (тождественная
  вместо смены знака), но не место приведения и не доменная конвенция.

**Прочий состав сужен до полей с названным потребителем** (H22,
`GAPS_CLOSE_7`; codestyle §«Неиспользуемый код»). Остаются выведенными:
`triggerPx` (цена триггера ликвидации/ADL) и
**`openAvgPx`** (H23, `DOCS_CHECK_8`): потребителя в фазе 1 нет ни у
одного, а маппинг `openAvgPx → Position.externalAverageEntryPrice` делал
колонку двуписьменной (live `avgPx` — текущая средняя, `openAvgPx` —
средняя за жизнь позиции; при доборах они расходятся, провенанс поля
становился неоднозначным). `Position.externalAverageEntryPrice` пишет
**только live-нога**; понадобится средняя за жизнь — заводится отдельное
поле, не перегружается это. Выведенные поля остаются кандидатами в
носители измеримости искажений (`PNL-Q1`). Побочно это **обесточивает
H19**: расхождение доков о применимости `triggerPx` больше не нагружено
ничем — поле не маппится вовсе.

### snapshot → `Position`

Применяет `RefreshPositionExecutor` (нога 2). **Троп две**, и состав
маппинга у них разный:

- **update-тропа** (штатная) — `Position` уже существует, статус в
  `CLOSED` перевела нога 1;
- **create-тропа** — позиция **впервые увидена уже закрытой** (открылась и
  закрылась между тиками, быстрый стоп, ликвидация), локальной `Position`
  нет вовсе, и нога 2 её **материализует** из записи positions-history
  (H9 `GAPS_CLOSE_10`; состав задан H4 `DOCS_CHECK_11`).

| Snapshot field | Domain | Семантика | Тропа |
|---|---|---|---|
| `externalRealizedPnl` | `Position.externalRealizedProfit` | биржевой net realized P&L закрытой позиции | обе |
| `externalResultCurrency` | `Position.externalResultCurrency` | валюта, в которой он посчитан | обе |
| `externalCloseAveragePrice` | `Position.externalCloseAveragePrice` | средняя цена фактического выхода; потребитель — калибровка проскока на тропе attached-SL (H26, H21) | обе |
| `externalCloseType` | `Position.externalCloseType` | провенанс закрытия (`3`–`6` = закрыла биржа) | обе |
| `externalRealizedPnlGross` | `Position.externalRealizedProfitGross` | `pnl` до издержек; потребитель — первая пара сверки (H19 `DOCS_CHECK_12`) | обе |
| `externalFee` | `Position.externalFee` | знаковая комиссионная компонента записи; потребитель — вторая пара сверки (H19 `DOCS_CHECK_12`) | обе |
| `externalFundingCost` | `Position.externalFundingCost` | накопленный funding, **знак нормализован при построении снапшота** (издержка > 0); потребители — де-микширование R (H20 `DOCS_CHECK_11`) и третья пара сверки (H19 `DOCS_CHECK_12`) | обе |
| `externalLiquidationPenalty` | `Position.externalLiquidationPenalty` | ликвидационный штраф записи (**сырой знак** источника); потребитель — четвёртая пара сверки (H7 `DOCS_CHECK_13`; строка дозаведена H1 `DOCS_CHECK_14` — без неё правый операнд пары не приземлялся) | обе |
| `externalModifiedAt` | `Position.externalModifiedAt` + **`Deal.billsWindowEnd`** | `uTime` записи закрытия (конвенция `Auditable`, H25). На `Deal` — верхняя граница окна линковки bills, пишется той же транзакцией (узел 1 `DOCS_CHECK_8`; окно из `Position.externalModifiedAt` больше не реконструируется) | обе |
| `externalPosId` | сверка с `Position.externalId` | не перезаписывает: адресация, а не данные | update |
| `externalInstrumentId` | сверка с `Instrument.externalId` запрошенного инструмента | не перезаписывает: структурная проверка принадлежности записи (H18 `DOCS_CHECK_14`) | обе |
| `externalPosId` | `Position.externalId` (**запись**) | локальной `Position` нет ⇒ id записи и есть её идентичность | create |
| `direction` | `Position.direction` | направление материализуемой позиции — **перенос доменного значения, без резолва здесь** (H7 `DOCS_CHECK_15`); на update-тропе уже заполнено live-ногой | create |
| `externalCreatedAt` | `Position.externalCreatedAt` + **`Deal.billsWindowBegin`** | `cTime` записи. На `Deal` — **нижняя** граница окна линковки bills, пишется той же транзакцией | create |

**Статус и размер на create-тропе.** Материализуемая `Position` создаётся
сразу в `CLOSED` (нога 1 её не видела и статуса не ставила);
`externalSize` закрытой позиции равен нулю по определению — размер
живой позиции этой тропой не наблюдался и не восстанавливается.

**`billsWindowBegin` — write-once с тремя писателями.** Границу пишет
live-нога (штатно), условно `SubmitOrderExecutor` и — на create-тропе —
нога 2 (`docs/models/domain/aggregate/Deal.md` §«Окно линковки»). Порядок
разрешения при конкуренции write-once задан там же; на create-тропе
предыдущих писателей по построению не было.

### `Position` → `Deal` (финализатор)

| `Position` | `Deal` | Кто пишет |
|---|---|---|
| `externalRealizedProfit` | `resultProfit` (слагаемое net) | `FinalizeDealExitExecutor` / `MarkDealEmergencyClosedExecutor` |
| `externalResultCurrency` | **не пишется** — сверяется | они же (см. ниже) |
| `externalCloseType` | `closeOutcome` (`1,2` → `NORMAL_EXIT`; `3,4` → `LIQUIDATION`; `5,6` → `FORCED_REDUCTION`; пусто либо вне `1..6` → `UNDETERMINED` + отчёт) | они же (`docs/models/domain/aggregate/Deal.md` §«Признаки отбора для отчёта») |
| `externalRealizedPnlGross`, `externalFee`, `externalFundingCost`, `externalLiquidationPenalty` | **не пишутся** — правые операнды четырёх пар сверки | `FinalizeDealExitExecutor` (сверка, не запись) |

**Валюта результата в `Deal` пишется не отсюда** (H10 `DOCS_CHECK_10`,
решение пользователя). `Deal.resultProfitCurrency` берётся из **расчётной
валюты инструмента** (`docs/models/domain/aggregate/Deal.md` §«Валюта
результата: один авторитет»), а `Position.externalResultCurrency` —
**проверяемый признак**: финализатор сверяет его с авторитетом и при
расхождении ставит `AnomalyReport` `RESULT_CURRENCY_MISMATCH`, не
блокируя расчёт. Прежняя строка таблицы («`externalResultCurrency` →
`resultProfitCurrency`») делала носителей два, и число складывалось из
net'а в валюте записи источника с cross-ccy-слагаемым в расчётной валюте
инструмента — разные валюты молча.

**`externalCloseType` переходит в `Deal.closeOutcome`** (узел F
`GAPS_CLOSE_12` + H2 `GAPS_CLOSE_13`). Он остаётся входом провенанса
аварийного терминала (`docs/decisions/pnl-finalization-mechanics.md`
реш.3) и одновременно — операндом признака отбора: писатели те же два
финализатора, значение резолвится по таблице выше, пустой или неизвестный
операнд даёт `UNDETERMINED` (не благоприятный `NORMAL_EXIT`) плюс
журнальный `AnomalyReport`. Прежняя клауза «в `Deal` не пишется» снята —
она отстала от ратификации `Deal.closeOutcome`.

**Что осталось открытым в `PNL-Q1`** — не представление провенанса
(его доносит `closeOutcome`), а нужен ли **сверх** него `triggerPx`
(цена триггера ликвидации/ADL).

### Validation (структурная, до маппинга)

В `IntegrationService` источника:

- **Structural:** `response != null`; `code == 0`; резолвится **ровно одна
  финализированная** запись positions-history (инвариант агрегации,
  `docs/integrations/okx/contracts/position.md` §«Инвариант агрегации»;
  **N11, требует рантайм-верификации**). Множественная / нефинализированная
  запись — controlled external error, не молчаливое взятие слайса.
  - **Принадлежность записи запрошенному инструменту** (H18
    `DOCS_CHECK_14`): `instId` записи совпадает с `Instrument.externalId`
    запрошенного инструмента — проверка **на границе**, по полю ответа, а
    не доверием фильтру запроса (опора «в контуре один инструмент» молча
    ломается при снятии ограничения — тот же довод, что у
    `DealCashFlow.externalInstrumentId`). Несовпадение — controlled
    external error. Наличие `instId` в `data[]` — посылка контракт-дока,
    сверка с офдоком за `integrator`
    (`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`).
  - **Ось адресации — не всегда `posId`** (H5 `DOCS_CHECK_11`; модель
    сняла ключевание — `docs/models/domain/core/Position.md` §Инварианты).
    Когда `posId` наблюдался — адресация по нему, и «ровно одна» проверяется
    на нём. Когда `posId` не наблюдался (create-тропа: позиция открылась и
    закрылась между тиками) — адресация **инструментом и временным окном**, а
    однозначность держит инвариант «одна активная сделка на инструмент»
    (`docs/rules/trading-constraints.md`).
  - **Нижняя граница окна на второй оси — `Order.externalCreatedAt` первой
    отправленной ноги входа** (H12 `DOCS_CHECK_12`, решение пользователя).
    Формулировка «окно **сделки**» здесь **снята**: границы окна сделки —
    `Deal.billsWindowBegin`/`billsWindowEnd`, и обе на create-тропе пишет
    **ровно эта же нога 2**, то есть на момент запроса операнда не
    существует. Поле уже persisted, писатель есть; ни колонки, ни вызова
    биржи правка не добавляет. `Deal.createdAt` как операнд отвергнут —
    системное время в биржевом окне есть снятое смешение часовых доменов.
  - **Известное ограничение:** подтропа адресуема только когда вход **дошёл
    до биржи**. У позиции вокруг чужого риска отправленной ноги входа нет ⇒
    операнда нижней границы нет ⇒ адресация не определена; второго
    механизма не вводится
    (`docs/components/RefreshPositionExecutor.md` §Evidence-cycle).
  - **Что считается «ровно одной» на второй оси — хвост `integrator`.**
    Какие оси запроса принимает история позиций источника и как она себя
    ведёт, если в окне по инструменту оказалось **несколько** записей
    (несколько циклов открытия-закрытия внутри одного окна; частичные
    закрытия отдельными записями), из доков не выводится — это факт
    источника. До ответа поведение второй оси **не специфицировано**, и
    create-тропа гейтится этим фактом
    (`.claude/tests/source-api/okx/plan.md`).
- **Numeric:** числа приходят строками; обязательные заполнены и парсятся.
  `closeAvgPx` парсится, но обязательным **не** является: его отсутствие не
  влияет ни на число, ни на терминал — пустое поле означает лишь, что
  сделка не войдёт в выборку калибровки проскока.

#### Контракт записи проверяется здесь, а не у финализатора (H5 `DOCS_CHECK_15`)

**Обязательные поля добытой записи валидируются на границе** — там, где
ответ впервые разбирается, — и нарушение выражается броском
`ExternalInvariantViolationException` (controlled, `docs/rules/
controlled-exchange-exceptions.md`). Дальше реакция идёт **общим путём**:
исполнитель добывающей команды доводит строку исполнения до `FAILED` и
пробрасывает исключение, оркестратор перехватывает его выделенным
обработчиком и резолвит реакцию (`docs/components/ServiceCommandExecutor.md`
§«Контракт броска», `docs/components/HoldService.md` §«Момент вызова»).

Обязательный набор:

| Поле | Требование |
|---|---|
| `realizedPnl` | заполнено и парсится |
| `ccy` | заполнено и парсится (соответствие расчётной валюте инструмента — **не** здесь: это признак, сверяет финализатор, H10 `DOCS_CHECK_10`) |
| `type` | заполнено **и внутри перечня `1..6`** (H5 `DOCS_CHECK_15`) |
| `pnl`, `fee`, `fundingFee`, `liqPenalty` | заполнены и парсятся — правые операнды четырёх пар сверки |
| `direction` | резолвится в доменный `Position.Direction` (§«Резолв направления» ниже) |

- **Что снято.** Прежняя редакция (H7/H10 `DOCS_CHECK_14`) оставляла
  проверку `type` и правых операндов **финализатору**, доводом «граница
  приземляет добытое и не реджектит — отказ границы оставил бы сделку без
  терминала». Довод **больше не держится**: отказ границы уводит сделку
  ошибочной тропой, а её терминал обеспечен durable-исходом добычи
  (`FAILED` строки `REFRESH_DEAL_CONTEXT_ACTION` разрешает эмиссию
  `FINALIZE_DEAL_ERROR_ACTION`, `docs/components/SystemActionExecutor.md`
  §«Вывод стадии»). Сделка доходит до `EMERGENCY_CLOSED` с
  `resultProfit = null`, а биржевой холд поднимается **параллельно** —
  ветки не конкурируют (H4 `DOCS_CHECK_15`).
- **Что это снимает по построению.** Финализатор перестаёт быть местом
  обнаружения нарушения контракта записи, поэтому `FULL`-реакция из
  середины цикла команд, поверх сделки, чей переход ещё не применён,
  становится **недостижимой** — конфликт с этой клаузой снят механизмом, а
  не оговоркой (`docs/components/HoldService.md` §«Кто зовёт»).
- **Что остаётся у финализатора.** Только то, чей операнд **не** приходит
  записью источника: сверка `ccy` с расчётной валютой инструмента
  (признак, `RESULT_CURRENCY_MISMATCH`) и обязанность сверки при
  нерезолвимом `ctVal` навеса (H2 `DOCS_CHECK_15`,
  `docs/components/FinalizeDealExitExecutor.md` §epsilon).
  `Deal.closeOutcome = UNDETERMINED` сохраняет смысл — но теперь ровно
  один: **запись закрытия не добыта вовсе** (аварийная ветвь (b));
  половина «добыта, но `type` вне перечня» до финализатора не доезжает.
- **Форма пустого значения несобытийных полей — предусловие, и теперь
  несущее.** Отдаёт ли источник `"0"` или пустую строку на сделке без
  funding/ликвидации — рантайм-посылка того же прогона, что знак и
  горизонт `fundingFee` (§AG1.7). Если пустая строка штатна, это факт
  модели источника: он поглощается **native-слоем** конвенцией «пусто = 0
  для несобытийного поля» (`docs/decisions/source-model-change-absorption.md`,
  фиксируется в инвентаре) — **до** проверки обязательности, иначе
  валидация границы реджектила бы каждую сделку без funding. Перенос
  проверки на границу цену ошибки поднимает: раньше она давала неверный
  признак, теперь — отказ добычи; предусловие остаётся тем же
  (`docs/decisions/pnl-finalization-mechanics.md` §«Предусловия `CODE`»
  п. 7).

`triggerPx` валидацией не рассматривается вовсе — поле из снапшота
выведено (H22, `GAPS_CLOSE_7`), поэтому расхождение доков о его
применимости больше ничего не нагружает (закрывает остаток H19;
единственный носитель формулировки —
`docs/integrations/okx/contracts/position.md` §История, сверка с офдоком
остаётся открытой у `integrator`).

#### Резолв направления (H7 `DOCS_CHECK_15`)

**Сырое `direction` записи превращается в доменный `Position.Direction` в
слое интеграции**, при построении снапшота — симметрично живой ноге, где
направление выводится из знака `pos` (`docs/models/mapping/Position.md`
§«Direction mapping»). Ниже маппинга снапшот несёт **доменное** значение,
и `Position.direction` на create-тропе получает его переносом.

- **Незнакомое либо пустое значение — `ExternalInvariantViolationException`**,
  той же тропой, что и прочие нарушения контракта записи (§выше). Отдельного
  механизма не заводится: «direction нельзя определить» уже названо
  основанием этого исключения на живой ноге
  (`docs/models/mapping/Position.md` §«Invariant checks»).
- **Почему не MapStruct `valueOf`.** Конвенция проекта конвертирует
  String ↔ enum на границе автоматически, и при несовпадении имён это дало
  бы `IllegalArgumentException` внутри транзакции звена — то есть отказ без
  класса и без реакции, на **единственной** тропе позиции, впервые
  увиденной уже закрытой (быстрый стоп, ликвидация — левый хвост).
  Защитный резолв `unknown → null` тоже отвергнут: он даёт `Position` без
  направления, а инвариант «`Position.direction` соответствует
  `Deal.direction`» на create-тропе никем не проверяется — он живёт только
  на живой ноге.
- **Какие значения источник фактически отдаёт в этом поле — хвост
  `integrator`, открыт.** Посылка «это не имена наших констант» назначенным
  владельцем **не проверена**; решение определяет, **где живёт правило**, а
  не каково оно. Таблица значений — в
  `docs/integrations/okx/contracts/position.md` §История, по итогам сверки.
- **Аварийный контур:** для `EMERGENCY_CLOSED` при genuinely недоступном
  net запись закрытия не найдена / числа не даёт → поля положения закрытия
  на `Position` остаются `null` → `Deal.resultProfit = null` с семантикой
  «неисчислимо» (не ноль), терминал всё равно проходит
  (`docs/decisions/pnl-finalization-mechanics.md` реш.3).

### Error policy

- **Temporary API problem** (timeout, connection reset, 5xx): нога 2
  наследует retry **своей команды** `REFRESH_POSITION_COMMAND` (командная
  машинерия, анкер — `DealActionState`); финализация ждёт факта.
- **Invalid response / нарушен контракт записи** (`code != 0`,
  множественная/нефинализированная запись на `posId`, обязательное поле
  пусто либо не парсится, `type` вне `1..6`, `direction` не резолвится —
  §«Контракт записи проверяется здесь»): **controlled external error**;
  поля положения закрытия не пишутся; исполнитель доводит строку до
  `FAILED` и пробрасывает исключение — оркестратор поднимает **полный
  биржевой холд**, сделка уходит ошибочной тропой и доходит до
  `EMERGENCY_CLOSED` с `resultProfit = null`. Чистый `CLOSED` без числа не
  завершается (инвариант непустоты `resultProfit`,
  `docs/models/domain/aggregate/Deal.md`).
- **Запись не найдена** — не ошибка команды: статус `CLOSED` уже поставлен
  ногой 1, поля остаются `null`, сделка уходит тропой «неисчислимо».
  Различение «жёсткий отказ чтения» vs «пусто» и реакция на каждой тропе —
  `docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия троп отказа
  добычи».

## OKX

### `OkxPositionsHistoryResponse` → `PositionCloseResultExternalSnapshot`

См. инвентарь — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `realizedPnl` | `externalRealizedPnl` |
| `ccy` | `externalResultCurrency` |
| `closeAvgPx` | `externalCloseAveragePrice` |
| `type` | `externalCloseType` |
| `pnl` | `externalRealizedPnlGross` |
| `fee` | `externalFee` |
| `fundingFee` | `externalFundingCost` |
| `liqPenalty` | `externalLiquidationPenalty` |
| `posId` | `externalPosId` |
| `instId` | `externalInstrumentId` |
| `direction` | `direction` (сырое значение → доменный `Position.Direction`, §«Резолв направления») |
| `cTime` | `externalCreatedAt` (epoch millis → `OffsetDateTime`) |
| `uTime` | `externalModifiedAt` (epoch millis → `OffsetDateTime`) |

**Таблица — место истины маппинга этого источника.** Маппер строится по
ней; поле, которого в ней нет, в снапшот не попадает. Поэтому изменение
состава снапшота обязано доезжать сюда же — перечень «не маппимых» и счёт
цепочки ниже суть части того же носителя (H1 `DOCS_CHECK_13`).

Числовые поля парсятся в `BigDecimal`, `cTime`/`uTime` — в
`OffsetDateTime`; `empty string → null`. Список не маппимых полей
(`settledPnl`, `pnlRatio`, `mgnMode`, `posSide`,
`lever`, `uly`, `openMaxPos`, `closeTotalPos`, `nonSettleAvgPx`, а также
`triggerPx` — выведен H22; `openAvgPx` — выведен H23 `DOCS_CHECK_8`;
`closeAvgPx` из этого перечня **возвращён** H26 `DOCS_CHECK_10`,
`fundingFee` — H20, `cTime`/`direction` — H4 `DOCS_CHECK_11`,
`pnl`/`fee` — H19 `DOCS_CHECK_12`, `liqPenalty` — H7 `DOCS_CHECK_13`) — в
`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.

**Счёт цепочки:** native used 13 = snapshot 13 = domain 13. Домен
считается по **различным snapshot-полям** таблицы snapshot → `Position`
(`externalPosId` идёт двумя строками — сверка на update-тропе, запись на
create-тропе — и считается один раз; строки-сверки — `externalPosId`
update, `externalInstrumentId` — входят в счёт: они часть контракта
маппера). Для create-тропы; на update-тропе
`direction`/`externalCreatedAt` не применяются, а `externalPosId`
сверяется вместо записи. Пересчитан дважды `DOCS_CHECK_14`: H1 дозавёл
строку `externalLiquidationPenalty` (до неё domain = 11, клейм «12 = 12 =
12» был ложен по третьему члену), H18 добавил `externalInstrumentId`
(12 → 13 по всем трём членам).

### OKX validation notes

- **Structural:** `code == 0`.
- **Query:** когда `posId` наблюдался — запись добывается по нему (плюс
  `instType`/`instId`); когда не наблюдался — по `instId` и временному окну
  **от `Order.externalCreatedAt` первой отправленной ноги входа**
  (`after`/`before` по `uTime`; H12 `DOCS_CHECK_12` — «окно сделки» как
  операнд снято, обе его границы на этой тропе ещё не записаны).
  Пагинация positions-history — по `uTime`
  (`limit` ≤ 100),
  `docs/integrations/okx/contracts/position.md` §«История закрытых позиций».
  Точный набор принимаемых осей и их совместимость — хвост `integrator`
  (H5 `DOCS_CHECK_11`).
- **Инвариант агрегации (N11):** `realizedPnl` кумулятивен по всем
  partial-закрытиям и доборам за жизнь `posId`; читается финализированной
  записью (позиция flat по `REFRESH_POSITION_COMMAND`). До рантайм-верификации —
  **предположение** (контур source-api, `.claude/tests/source-api/okx/plan.md`
  §AG1); гейтит корректность числа до `CODE`.
- **Семантика `fundingFee` записи — хвост `integrator`** (H20
  `DOCS_CHECK_11`): накоплен за жизнь `posId` или только за последнее
  закрытие. От ответа зависит, сверяется ли Σ`FUNDING` окна с ним напрямую.
