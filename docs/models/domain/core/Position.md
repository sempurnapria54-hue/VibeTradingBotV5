# Position

## На какой вопрос отвечает этот файл

Что это за торговая модель `Position`: структура, атрибуты, енумы,
формула live risk, что хранит и что не хранит.

Статусы и переходы — в `docs/lifecycles/Position.md`.

## Назначение

`Position` — runtime-сущность позиции внутри `Deal`. Отражает состояние
сопровождаемой позиции и отвечает на вопрос: «есть ли live-risk позиция
по сделке прямо сейчас», а после закрытия — **несёт положение закрытия**
(realized-факты закрытой позиции, добытые второй ногой `REFRESH_POSITION`,
см. §«Положение закрытия»). Хранит **только** данные, нужные для
сопровождения live-risk позиции и для финализации сделки по ней.

`Position` **не** отвечает за: итоговый profit/loss сделки (число живёт
полем `Deal`, здесь — **биржевой факт**, из которого его считает
финализатор), историю команд, историю strategy actions, сырые exchange
responses, полную копию response биржи.

## Структура

Java-класс `com.example.tradingbot.domain.model.core.position.Position`,
расширяет `Auditable` (`externalCreatedAt` / `externalModifiedAt`
наследуются).

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор позиции в БД. |
| `dealId` | `Long` | Сделка, в рамках которой сопровождается позиция. |
| `externalId` | `String` | Биржевой ID позиции, если биржа его отдаёт (OKX: `posId`). Не stable client id. |
| `status` | `Status` | Доменный статус (см. lifecycle). |
| `closeReason` | `CloseReason` | Причина закрытия / problem reason. Не дублирует `Deal.CloseReason`. |
| `direction` | `Direction` | Доменное направление (`LONG` / `SHORT`). |
| `externalSize` | `BigDecimal` | Размер по данным биржи, нормализованный абсолют (`abs(pos)`). |
| `externalAverageEntryPrice` | `BigDecimal` | Средняя цена входа. |
| `externalMarkPrice` | `BigDecimal` | Mark price. |
| `externalLiquidationPrice` | `BigDecimal` | Расчётная цена ликвидации. |
| `externalMargin` | `BigDecimal` | Маржа позиции. |
| `externalUnrealizedProfit` | `BigDecimal` | Нереализованный PnL. |
| `externalRealizedProfit` | `BigDecimal` | **Положение закрытия:** готовый net realized P&L закрытой позиции, посчитанный биржей (`realizedPnl` positions-history). `null`, пока позиция жива или запись закрытия не добыта. |
| `externalResultCurrency` | `String` | **Положение закрытия:** валюта, в которой посчитан `externalRealizedProfit` (`ccy` записи positions-history). |
| `externalCloseType` | `String` | **Положение закрытия:** сырой тип последнего закрытия источника (OKX `type`: `1`–`2` торговое, `3`–`6` ликвидация/ADL). Провенанс аварийного терминала (`docs/decisions/pnl-finalization-mechanics.md` реш.3). |

Поля §«Положение закрытия» пишет **вторая нога `REFRESH_POSITION`**
(positions-history), не финализатор; наследуемый `externalModifiedAt`
принимает `uTime` записи закрытия, `externalAverageEntryPrice` —
`openAvgPx`. Состав ограничен полями с **названным потребителем**
(codestyle §«Неиспользуемый код»); что осталось за бортом и почему —
§«Что `Position` не хранит».

## Инварианты

- `Position` принадлежит `Deal` через `dealId`. В рамках одной `Deal`
  допускается максимум одна `Position` (`relatedPositions` не нужны).
- `Position` **не** хранит `instrumentId`, `exchangeId`, `internalId`,
  `strategyActionId`, `strategyActionKey`. Эти данные приходят через
  `DealContext` (Exchange / Instrument), см. lifecycle.
- `Position` создаётся и обновляется только через `REFRESH_POSITION`
  executor (**обе ноги** — live и positions-history); FSM напрямую
  `Position` не создаёт и поля не заполняет.
- `Position` не client-created entity, не имеет stable client id.
  `externalId` (OKX `posId`) не вечен: биржа может очистить id после
  закрытия — поэтому не единственный источник идемпотентности. Точная
  адресация записи positions-history при **переиспользовании** `posId`
  — открытый вопрос (H6 `DOCS_CHECK_7`; предложение владельцу вынесено
  `GAPS_CLOSE_7`).

## Енумы

### `Direction`

`LONG` / `SHORT`. `NET` **не** используется как direction: net — это
режим/сторона позиции на бирже, а не направление рыночного риска.
Для OKX net-mode направление выводится из знака `pos` (см.
`docs/models/mapping/Position.md`). `posSide=net`
валидируется в adapter-layer и в `Position` не хранится.

### `Status`

`ACTIVE` / `CLOSED` / `ERROR`. Минимальный набор; промежуточные
`CREATED` / `PENDING` / `OPENING` / `CLOSING` / `PARTIALLY_CLOSED`
не вводятся. Значения и переходы — в `docs/lifecycles/Position.md`.

### `CloseReason`

- `CLOSED_BY_STRATEGY` — штатное закрытие как действие стратегии.
- `KILL_SWITCH` — аварийный safety-flow / kill-switch.
- `MANUAL_CLOSE` — ручное закрытие пользователем.
- `EXTERNAL_CLOSE` — позиция закрылась на стороне биржи без текущей
  команды close (SL/TP/trailing/liquidation/ADL/иной exchange-event).
- `EXCHANGE_INVARIANT_VIOLATION` — problem reason для `ERROR` (adapter
  обнаружил нарушение exchange-specific invariant).
- `UNKNOWN` — fallback, если причину безопасно определить не удалось.

`Position.CloseReason` (каким механизмом закрыта позиция) не дублирует
`Deal.CloseReason` (почему завершилась сделка с точки зрения торговой
логики). Примеры: SL → `EXTERNAL_CLOSE` / `Deal=STOP_LOSS`; явный
close → `CLOSED_BY_STRATEGY` / `Deal=STRATEGY_EXIT`; kill-switch →
`KILL_SWITCH` / `Deal=EMERGENCY_CLOSE`. Правило записи (write-once) —
в lifecycle.

## Live risk

Формула (первоисточник — здесь, `.claude/decisions/rule-source-of-truth.md`):

```java
public boolean hasLiveRisk() {
    return status == Status.ACTIVE
        && externalSize != null
        && externalSize.compareTo(BigDecimal.ZERO) > 0;
}
```

`ACTIVE` сам по себе ещё не означает live market risk — нужен
`externalSize > 0`. Различение `status` vs live risk и случай
`ACTIVE && externalSize == 0` (cleanup/anomaly/retry, live risk = false)
— в `docs/lifecycles/Position.md`.

## `PositionExternalSnapshot`

Нормализованный объект для обновления `Position` (не raw/diagnostic
exchange response; раздел модели по `.claude/decisions/model-granularity.md`).
Создаётся только если позиция реально найдена на бирже; если не
найдена — `IntegrationService` возвращает `null`, а не пустой snapshot.

Поля: `externalId`, `externalSize`, `externalAverageEntryPrice`,
`externalMarkPrice`, `externalLiquidationPrice`, `externalMargin`,
`externalUnrealizedProfit` (+ `externalCreatedAt` /
`externalModifiedAt` от `Auditable`). Если поле не обновляет
`Position` — в snapshot не попадает. OKX mapping — в
`docs/models/mapping/Position.md`.

Вторая нога `REFRESH_POSITION` (positions-history) нормализуется
**своим** граничным объектом `PositionCloseResultExternalSnapshot` и
обновляет ту же `Position` полями §«Положение закрытия»
(`docs/models/mapping/PositionCloseResult.md`). Два снапшота — потому
что это два разных ответа источника об одной сущности, а не потому что
сущностей две.

## Положение закрытия

**Добывается второй ногой `REFRESH_POSITION`** (H1/H3, `GAPS_CLOSE_7`).
`REFRESH_POSITION` проходит evidence-cycle **внутри одной команды**: live
`/account/positions` → при not-found (позиция закрыта)
`/account/positions-history` по `posId`. Это тот же within-command-обход,
которым `REFRESH_ORDER` эскалирует live → pending → history
(`docs/decisions/refresh-evidence-cycle-ownership.md`,
`docs/rules/command-lifecycle.md` §«Команды атомарны»); отдельной команды
`REFRESH_POSITIONS_HISTORY` **не вводится** — сущность одна (`Position`),
а refresh-набор держит по одной команде на сущность.

Добытое **приземляется на `Position`** (persisted), а не живёт транзитно:
`externalRealizedProfit`, `externalResultCurrency`, `externalCloseType`,
`externalModifiedAt` (`uTime` записи закрытия),
`externalAverageEntryPrice` (`openAvgPx`). Следствия:

- у факта закрытия есть **durable-дом** ⇒ он пересекает границу прохода
  FSM штатно, и потребители (финализатор штатной тропы, аварийный
  терминал, окно линковки bills) читают его со строки, а не из памяти
  чужого действия;
- **идемпотентность — командная**, как у любого `REFRESH_*`: повторное
  чтение приводит поля к состоянию биржи; вложенности «команда внутри
  команды» не возникает вовсе;
- поля пишутся **write-once по факту**: повторный проход перезаписывает их
  тем же значением записи (адресация по `posId`), а не накапливает.

Нормализация ответа и per-source-маппинг —
`docs/models/mapping/PositionCloseResult.md`; контракт эндпоинта —
`docs/integrations/okx/contracts/position.md` §«История закрытых позиций».

## Что Position не хранит

`Position` не хранит fills, слагаемые net (`pnl`, `fee`, `fundingFee`,
`liqPenalty` — категорийная разбивка живёт в `DealCashFlow`),
strategy/action/audit history, raw exchange response.

**Из положения закрытия не заводятся полями** (нет потребителя в фазе 1 —
codestyle §«Неиспользуемый код»; H22, `GAPS_CLOSE_7`): средняя цена выхода
(`closeAvgPx`) и цена триггера ликвидации/ADL (`triggerPx`). Оба —
кандидаты в носители **измеримости искажений** (провенанс ликвидации/ADL);
вопрос открыт (`PNL-Q1`, `.claude/work/questions/open-questions.md`) и
заводит поле вместе с потребителем, а не раньше. В инвентаре источника они
числятся неиспользуемыми
(`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`).

`Position` (live `/positions`) **сам по себе не считает** итоговый PnL:
заголовочное `Deal.resultProfit` = net `realizedPnl` из **positions-history**
(поле `externalRealizedProfit` этой модели), разбивка — из bills; правило
принадлежит `Deal` (`.claude/decisions/rule-source-of-truth.md`,
`docs/models/domain/aggregate/Deal.md` §Итоговый PnL,
`docs/decisions/result-profit-source.md`). Полное закрытие
подтверждается через `REFRESH_POSITION`, не через ACK (см.
`docs/rules/ack-not-runtime-truth.md`).
