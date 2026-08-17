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
  -> Deal.resultProfit
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
| `externalPosId` | `String` | биржевой id позиции (ключ адресации записи; на create-тропе — **данные**, см. ниже) |
| `externalDirection` | `String` | направление закрытой позиции (`direction`) — операнд **create-тропы** (H4 `DOCS_CHECK_11`) |
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
операндов двух из трёх пар не существует
(`docs/components/FinalizeDealExitExecutor.md` §«Расчёт прибыли и сверка»).
Прежде оба числились выведенными как «слагаемые net, потребителя нет» —
потребитель появился.

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
| `externalModifiedAt` | `Position.externalModifiedAt` + **`Deal.billsWindowEnd`** | `uTime` записи закрытия (конвенция `Auditable`, H25). На `Deal` — верхняя граница окна линковки bills, пишется той же транзакцией (узел 1 `DOCS_CHECK_8`; окно из `Position.externalModifiedAt` больше не реконструируется) | обе |
| `externalPosId` | сверка с `Position.externalId` | не перезаписывает: адресация, а не данные | update |
| `externalPosId` | `Position.externalId` (**запись**) | локальной `Position` нет ⇒ id записи и есть её идентичность | create |
| `externalDirection` | `Position.direction` | направление материализуемой позиции; на update-тропе уже заполнено live-ногой | create |
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

`externalCloseType` в `Deal` **не пишется** — он вход провенанса
аварийного терминала (`docs/decisions/pnl-finalization-mechanics.md`
реш.3), читается со строки `Position`. Запрашиваемость провенанса
ликвидации/ADL на уровне `Deal` — открытый вопрос `PNL-Q1`.

### Validation (структурная, до маппинга)

В `IntegrationService` источника:

- **Structural:** `response != null`; `code == 0`; резолвится **ровно одна
  финализированная** запись positions-history (инвариант агрегации,
  `docs/integrations/okx/contracts/position.md` §«Инвариант агрегации»;
  **N11, требует рантайм-верификации**). Множественная / нефинализированная
  запись — controlled external error, не молчаливое взятие слайса.
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
- **Numeric:** числа приходят строками; обязательные (`realizedPnl`, `ccy`)
  заполнены и парсятся; для **чистого** закрытия `realizedPnl` присутствует
  (пустое `realizedPnl` при чистом закрытии недопустимо). `closeAvgPx`
  парсится, но обязательным **не** является: его отсутствие не влияет ни
  на число, ни на терминал — пустое поле означает лишь, что сделка не
  войдёт в выборку калибровки проскока. **`ccy` валидацией принимается на
  веру намеренно** — соответствие расчётной валюте инструмента проверяет
  не граница, а финализатор (H10; отказ на границе оставил бы сделку без
  терминала). `triggerPx`
  валидацией не рассматривается вовсе — поле из снапшота выведено (H22,
  `GAPS_CLOSE_7`), поэтому расхождение доков о его применимости больше
  ничего не нагружает (закрывает остаток H19; единственный носитель
  формулировки — `docs/integrations/okx/contracts/position.md` §История,
  сверка с офдоком остаётся открытой у `integrator`).
- **Аварийный контур:** для `EMERGENCY_CLOSED` при genuinely недоступном
  net запись закрытия не найдена / числа не даёт → поля положения закрытия
  на `Position` остаются `null` → `Deal.resultProfit = null` с семантикой
  «неисчислимо» (не ноль), терминал всё равно проходит
  (`docs/decisions/pnl-finalization-mechanics.md` реш.3).

### Error policy

- **Temporary API problem** (timeout, connection reset, 5xx): нога 2
  наследует retry **своей команды** `REFRESH_POSITION_COMMAND` (командная
  машинерия, анкер — `DealActionState`); финализация ждёт факта.
- **Invalid response / инвариант агрегации нарушен** (`code != 0`,
  множественная/нефинализированная запись на `posId`, обязательные не
  парсятся): controlled external error; поля положения закрытия не
  пишутся; финализация не завершает чистый `CLOSED` без числа (инвариант
  непустоты `resultProfit`, `docs/models/domain/aggregate/Deal.md`).
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
| `fundingFee` | `externalFundingCost` |
| `posId` | `externalPosId` |
| `direction` | `externalDirection` |
| `cTime` | `externalCreatedAt` (epoch millis → `OffsetDateTime`) |
| `uTime` | `externalModifiedAt` (epoch millis → `OffsetDateTime`) |

Числовые поля парсятся в `BigDecimal`, `cTime`/`uTime` — в
`OffsetDateTime`; `empty string → null`. Список не маппимых полей (`pnl`,
`fee`, `liqPenalty`, `settledPnl`, `pnlRatio`, `mgnMode`, `posSide`,
`lever`, `uly`, `openMaxPos`, `closeTotalPos`, `nonSettleAvgPx`, а также
`triggerPx` — выведен H22; `openAvgPx` — выведен H23 `DOCS_CHECK_8`;
`closeAvgPx` из этого перечня **возвращён** H26 `DOCS_CHECK_10`,
`fundingFee` — H20, `cTime`/`direction` — H4 `DOCS_CHECK_11`) — в
`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.

**Счёт цепочки:** native used 9 = snapshot 9 = domain 9 (для create-тропы;
на update-тропе `externalDirection`/`externalCreatedAt` не применяются, а
`externalPosId` сверяется вместо записи).

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
