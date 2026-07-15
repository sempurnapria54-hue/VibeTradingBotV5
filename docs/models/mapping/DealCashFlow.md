# DealCashFlow — mapping bills → домен

## На какой вопрос отвечает этот файл

Как OKX bill-записи ложатся на доменную `DealCashFlow`, как из `type`/`subType`
резолвится `CashFlowCategory`, какие поля валидируются.

## Контекст

Mapping-слой для `DealCashFlow`. Доменная модель —
`docs/models/domain/other/DealCashFlow.md`. Native bill-поля —
`docs/models/integrations/okx/OkxAccountBillResponse.md`. Контракт endpoint'ов
и поток применения — `docs/integrations/okx/contracts/account-bills.md`.
Сквозные правила — `docs/rules/raw-exchange-dto-boundary.md`, codestyle
§Маппинг.

`DealCashFlow` — носитель **разбивки** результата (не первоисточник числа):
заголовочное `Deal.resultProfit` = net из positions-history, а сумма flows —
независимая **сверка** (`docs/decisions/result-profit-source.md`,
`docs/decisions/pnl-finalization-mechanics.md`).

Текущие источники: **OKX**.

## Mapping-flow

```text
bills REST -> raw OkxAccountBillResponse -> IntegrationService validation
  -> DealCashFlowMapper -> DealCashFlow (persist)
```

Raw OKX DTO не выходит за пределы `IntegrationService` / adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`). Наполняется командой
`REFRESH_BILLS` (`RefreshBillsExecutor`): пагинация bills (7d → 3m archive)
внутри команды, матчинг к сделке и проставление `deal_id` — при сохранении
(`docs/models/domain/other/DealCashFlow.md` §Линковка к `Deal`).

## OKX bill-поле → `DealCashFlow`-поле

| OKX field | DealCashFlow field |
|---|---|
| `balChg` | `amount` (строка → `BigDecimal`) |
| `fee` | `externalFee` (строка → `BigDecimal`; знаковая комиссионная компонента) |
| `ccy` | `ccy` |
| `ts` | `externalTs` (epoch millis → `OffsetDateTime`) |
| `billId` | `externalBillId` |
| `type` | `externalType` |
| `subType` | `externalSubType` |
| `ordId` | `externalOrderId` |

`category` **не** маппится напрямую — резолвится из `type`/`subType` (см.
ниже). `deal_id` проставляется матчингом, не маппером.

### Разделение ролей `balChg` и `fee`

Два поля отвечают на **разные вопросы** и не подменяют друг друга:

- **`balChg` → `amount`** — вопрос «сходится ли сумма»: Σ`amount` сверяется с
  net из positions-history. Верен при **любой** гранулярности bills (Σ`balChg`
  = net независимо от того, комбинирует источник pnl и комиссию в одну запись
  или эмитит их раздельно).
- **`fee` → `externalFee`** — вопрос «сколько комиссии»: комиссионная
  компонента записи, взятая **явно**, а не выведенная из знака `balChg`.
  Комиссия сделки = Σ`externalFee` по её торговым движениям (область
  суммирования — §«Область суммирования»).

**Почему число комиссии — из native `fee` (шаг 7, `GAPS_CLOSE_3`, H2).** Вывод
комиссии из знака `balChg` верен только при гранулярности «отдельная
fee-запись на событие». При **комбинированной** записи (`balChg = pnl + fee`)
он кладёт весь `pnl + fee` в одну корзину: комиссия недосчитана, realized pnl
пересчитан — разбивка (единственный смысл `DealCashFlow`) искажена. Native
`fee` даёт компоненту независимо от гранулярности → **безопасный суперсет**:
корректен в обе стороны. Фактическая гранулярность — рантайм-вопрос
(`.claude/tests/source-api/okx/plan.md` §AG1.5); на маппинг ответ не влияет.
*Суперсет — про **число**; **категорию** он не определяет — она резолвится по
типу операции (§«Резолв категории», H9).*

### Знак `externalFee` — сырой, нормализации нет

`externalFee` переносится **со знаком источника** (минус = комиссия, плюс =
ребейт), без `× −1` и без `abs`. Это **сознательная асимметрия** с прогнозной
ставкой `TradeFeeRate`, где знак снимается при маппинге
(`docs/models/domain/other/TradeFeeRate.md` §«Знак ставки», H2):

- **ставка** — прогноз, попадающий в **формулу риска** (`+ commissions`); там
  нужна проектная нормаль «издержка», иначе знак источника молча ломает
  сайзинг;
- **`externalFee`** — **факт** движения, участвующий в **арифметике
  композиции**: realized-слагаемое = `amount` − `externalFee` даёт
  `(pnl + fee) − fee = pnl` **только** на знаковом `fee`. Нормализуй его — и
  разложение комбинированной записи сломается.

Разные роли — разные конвенции. Помечено явно, чтобы следующий проход не
«выровнял» одно по другому: одинаковыми они выглядят только по имени.

**Компонента, не отдельная строка.** `fee` едет **полем на той же строке**, а
не синтетической второй `DealCashFlow`: ключ идемпотентности —
`UNIQUE(external_bill_id)` (одна строка на bill-запись,
`docs/models/domain/other/DealCashFlow.md` §Персистентность), а разложение
комбинированной записи на две строки и задвоило бы её в Σ`amount` (сломав
сверку), и не имело бы второго `billId` под ключ. Отсюда же следствие: при
комбинированной записи realized-pnl-слагаемое разбивки = `amount` −
`externalFee` (native `pnl` отдельным носителем не требуется —
`docs/models/integrations/okx/OkxAccountBillResponse.md` §«Не используется»).

## Резолв категории

**Ось резолва — тип операции, а не знак числа** (H9, `GAPS_CLOSE_4`).
`CashFlowCategory` резолвится **только** из `type`/`subType` bill-записи:
категория отвечает на вопрос «**что это было за событие**», а на него отвечает
источник своим типом операции — не арифметика знака.

| Что за событие (`type`/`subType`) | Категория |
|---|---|
| funding-платёж: `subType` `173` (expense) / `174` (income) | `FUNDING` |
| торговое движение (исполнение ордера, закрытие/частичное закрытие позиции) | `REALIZED_PNL` |
| отдельная запись списания торговой комиссии | `TRADE_FEE` |
| отдельная запись возврата/скидки комиссии | `REBATE` |
| запись ликвидационного штрафа | `LIQ_PENALTY` |
| не отнесённое к перечисленному | `OTHER` |

`LIQ_PENALTY`: само число `liqPenalty` в net берётся из positions-history (не
bills); в `DealCashFlow` категория попадает **как факт bill-записи
ликвидации**, если она присутствует в окне.

**Резолв категории — в вызывающем коде, не в маппере** (codestyle §Маппинг:
маппер делает только перенос данных, доменных решений — интерпретации
`type`/`subType` в категорию — не принимает). Актуальный перечень
`type`/`subType` — справочник OKX (`GET /api/v5/account/subtypes`,
`docs/integrations/okx/contracts/account-bills.md`), **не хардкод**: конкретные
числовые значения `type` в этот док не переписываются, кроме funding-подтипов
`173`/`174`, на которых завязана отдельная ветка.

### Почему не по знаку `externalFee` (что закрывает H9)

Промежуточная редакция (`GAPS_CLOSE_3`, правка H2) резолвила ветку
комиссия/rebate **по знаку `externalFee`**: минус → `TRADE_FEE`, плюс →
`REBATE`. Лид-ины при этом остались правильными (`DealCashFlow.md` §Структура,
поле `category`: «резолвится из `type`/`subType`»), а механизм разошёлся с
ними. Знаковая ось ломалась трижды:

1. **Нет приоритета правил.** Комбинированная запись (`balChg = pnl + fee`)
   матчила одновременно «`externalFee` минус → `TRADE_FEE`» и «реализованный
   pnl → `REALIZED_PNL`». При комбинированной гранулярности комиссия есть у
   **каждой** закрывающей записи ⇒ всё уходило в `TRADE_FEE`, а `REALIZED_PNL`
   **не назначался никогда** — категорийная ось (единственный смысл
   `DealCashFlow`) вырождалась.
2. **Нет ветки нуля.** Тест «минус/плюс» не покрывал `externalFee` = 0.
3. **Ось не та.** Знак `fee` отвечает на «сколько и в какую сторону», а не на
   «что за событие»: комбинированная торговая запись с комиссией — это
   **торговое движение** с комиссионной компонентой, а не «событие комиссии».

Резолв по типу операции снимает все три: у комбинированной записи **один**
`type` (торговое движение) → категория `REALIZED_PNL`, а комиссия едет
компонентой `externalFee` на той же строке. Приоритет не нужен — правило одно.

**`externalFee` = 0 — определённая ветка** (H9). Ноль означает «торговое
движение **без** комиссионной компоненты» (промо нулевой комиссии, maker-ребейт
0 и т. п.). На категорию не влияет — она уже пришла из `type`/`subType`; в
Σ`externalFee` вносит ноль. Отличается от `null` (поля в записи нет вовсе —
§Validation): `null` = «комиссионной компоненты у события не бывает»
(например funding), `0` = «бывает, и она нулевая». Для сумм разницы нет, для
разбора инцидента — есть.

**Число комиссии от категории не зависит.** Комиссия сделки берётся из
`externalFee` (см. §«Разделение ролей `balChg` и `fee`») **при любой**
гранулярности bills — в этом и был смысл возврата native `fee` в used на
`GAPS_CLOSE_3` (безопасный суперсет). H9 чинит **присвоение категории**, не
число: claim «суперсет корректен при любой гранулярности»
(`docs/models/integrations/okx/OkxAccountBillResponse.md`) верен для **сумм** и
неверен был для **категории**.

## Область суммирования — задаётся явно

Категория задаёт не только атрибуцию, но и **что по чему складывать** (H9,
`GAPS_CLOSE_4`). Три суммы, три разные области:

| Сумма | Область | Зачем |
|---|---|---|
| Σ`amount` | **все** строки сделки | сумма-сверка с net из positions-history |
| комиссия сделки = Σ`externalFee` | строки **торговых** категорий (`REALIZED_PNL` / `TRADE_FEE` / `REBATE`) | «сколько комиссии» |
| realized-pnl-слагаемое = Σ(`amount` − `externalFee`) | **только** строки `REALIZED_PNL` | разложение net на слагаемые |

**`FUNDING`-строки исключены** из двух последних — и это несущее уточнение, а
не педантизм. Формула «realized-pnl-слагаемое = `amount` − `externalFee`»
(§«Разделение ролей») без области суммирования применялась бы **ко всем**
строкам и **втянула бы funding в realized pnl**: funding-движение несёт
`amount` и не несёт `externalFee` ⇒ его сумма целиком попала бы в
realized-слагаемое. Funding — **holding-cost**, живёт своей категорией и в
realized pnl не входит (разделяющий довод — `per-trade-risk-policy.md` §«Учёт
комиссий», N13).

Σ`amount` при этом идёт **по всем** строкам, включая `FUNDING`: она сверяется
с net, а net из positions-history funding **содержит**
(`realizedPnl = pnl + fee + fundingFee + liqPenalty`,
`docs/decisions/result-profit-source.md`). Разные области у сверки и у
разбивки — не рассогласование, а разные вопросы.

## Validation (структурная, до маппинга)

В `IntegrationService` OKX:

- **`billId` присутствует** — ключ дедупа/идемпотентности
  (`UNIQUE(external_bill_id)`); движение без `billId` не сохраняется.
- **`ccy` присутствует** — обязательно, иначе cross-ccy движение теряется
  молча при сверке (`pnl-finalization-mechanics.md` реш.5).
- **`balChg` parseable numeric** — числа приходят строками, `amount`
  парсится в `BigDecimal`; пустая/непарсящаяся строка недопустима.
- **`fee` parseable numeric, если присутствует** — `externalFee` парсится в
  `BigDecimal`; пусто → `null` (движение без комиссионной компоненты, например
  funding). Непарсящаяся непустая строка недопустима.
- На controlled error (`code != "0"`, parse, invariant) — exception в adapter
  (`docs/rules/controlled-exchange-exceptions.md`).

## Сверка и cross-ccy

Сумма `DealCashFlow.amount` сверяется с net из positions-history. Расхождение
**сверх epsilon** и cross-ccy-движение (`ccy != resultProfitCurrency`) →
`AnomalyReport` (audit-аномалия, **не** блок финализации;
`docs/decisions/pnl-finalization-mechanics.md` реш.5).

- **Epsilon якорится на оборот, не на итог:** max(0.01 settle-ccy, 0.5% от
  Σ`|amount|`). Охраняемая ошибка — ошибка **композиции разбивки**, её масштаб
  задают валовые потоки, а не их итог (H7, `GAPS_CLOSE_3`). Величина
  (`0.01`/`0.5%`) — **провизорна** (калибровка — пользователь/бэктест), якорь —
  нет.
- **Cross-ccy — нарушение инварианта, не режим:** комиссии платятся только в
  settle-ccy (`docs/rules/trading-constraints.md` §Валюта комиссии). Движение с
  чужой `ccy` **не отбрасывается молча** фильтром — помечается аномалией.

## Связи

- Доменная модель — `docs/models/domain/other/DealCashFlow.md`.
- Native — `docs/models/integrations/okx/OkxAccountBillResponse.md`.
- Контракт — `docs/integrations/okx/contracts/account-bills.md`.
- Источник числа / роль bills — `docs/decisions/result-profit-source.md`.
- Механика финализации / сверка — `docs/decisions/pnl-finalization-mechanics.md`.
