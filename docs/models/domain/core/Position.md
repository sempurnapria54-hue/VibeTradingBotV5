# Position

## На какой вопрос отвечает этот файл

Что это за торговая модель `Position`: структура, атрибуты, енумы,
формула live risk, что хранит и что не хранит.

Статусы и переходы — в `docs/lifecycles/Position.md`.

## Назначение

`Position` — runtime-сущность позиции внутри `Deal`. Отражает текущее
состояние сопровождаемой позиции и отвечает на вопрос: «есть ли
live-risk позиция по сделке прямо сейчас». Хранит **только** данные,
нужные для сопровождения live-risk позиции.

`Position` **не** отвечает за: итоговый profit/loss сделки, историю
команд, историю strategy actions, сырые exchange responses, полную
копию response биржи.

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

## Инварианты

- `Position` принадлежит `Deal` через `dealId`. В рамках одной `Deal`
  допускается максимум одна `Position` (`relatedPositions` не нужны).
- `Position` **не** хранит `instrumentId`, `exchangeId`, `internalId`,
  `strategyActionId`, `strategyActionKey`. Эти данные приходят через
  `DealContext` (Exchange / Instrument), см. lifecycle.
- `Position` создаётся и обновляется только через `REFRESH_POSITION`
  executor; FSM напрямую `Position` не создаёт и поля не заполняет.
- `Position` не client-created entity, не имеет stable client id.
  `externalId` (OKX `posId`) не вечен: биржа может очистить id после
  закрытия — поэтому не единственный источник идемпотентности.

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

## Что Position не хранит

`Position` не хранит fills, `realizedPnl`, `fee`, `fundingFee`,
`closePrice`, strategy/action/audit history, raw exchange response.
Отвечает только за live-risk состояние: наличие/отсутствие позиции,
размер, направление, средняя цена входа, mark price, liquidation
price, margin, unrealized PnL.

`Position` **не** используется для итогового PnL: `Deal.resultProfit`
считается через `REFRESH_FILLS` (правило принадлежит `Deal` — см.
`.claude/decisions/rule-source-of-truth.md`; форвард-заметка для Deal
— в `.claude/work/questions/tasks/position.md`). Полное закрытие
подтверждается через `REFRESH_POSITION`, не через ACK (см.
`docs/rules/ack-not-runtime-truth.md`).
