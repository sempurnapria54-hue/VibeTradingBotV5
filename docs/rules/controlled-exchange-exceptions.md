# Controlled exchange exceptions

## На какой вопрос отвечает этот файл

Какое у нас правило о трёх категориях controlled exchange exceptions и
runtime-реакции на них.

## Правило

В command / refresh / client-layer используются три категории
controlled exceptions для ситуаций, когда внешний факт получен (или
должен был быть найден), но продолжать normal runtime-flow небезопасно.

### ExternalStatusException

Бросает status resolver конкретной сущности/биржи (`OrderExternalStatusResolver`
и др.), если внешний статус получен, но неизвестен или означает
problem-state. `reasonCode`: `UNKNOWN_EXTERNAL_STATUS`, `ORDER_FAILED`,
`PARTIALLY_FAILED`. Неизвестный статус не маппится молча в `UNKNOWN`.

```text
entity -> ERROR; closeReason = reasonCode
Deal -> ERROR; Exchange -> HOLD
```

### ExternalInvariantViolationException

Бросает `IntegrationService` / adapter-layer, если response получен, но
нарушает ожидаемый exchange invariant (`tdMode != isolated`, `posSide !=
net`, `side`/`ordType`/`reduceOnly` != expected).

```text
entity -> ERROR; closeReason = EXCHANGE_INVARIANT_VIOLATION
Deal -> ERROR; Exchange -> HOLD
```

### ExternalNotFoundException

Бросает `RefreshExecutor` / recovery-search boundary, если после
**полного** evidence-cycle сущность не найдена и финал нельзя безопасно
объяснить. **Нельзя** бросать после одного пустого response. Для
`Position` успешный `null` (нет позиции в snapshot по инструменту) — это
нормальный closed-on-exchange факт, не `ExternalNotFoundException` (см.
`docs/rules/external-status-resolution.md`).

```text
entity -> ERROR; closeReason = MISSING_AFTER_REFRESH
Deal -> ERROR; Exchange -> HOLD
```

## Эскалация — безусловный L4 (все три категории)

`Exchange -> HOLD` во всех трёх блоках — **безусловный L4-холд биржи**,
единообразный для всех трёх категорий (HOLD-Q1,
`docs/decisions/controlled-violation-exchange-wide-hold.md`): один
controlled-эксепшн на одной сделке → flatten всех активных сделок биржи
(`KillSwitchService.fireExchange`) + `Exchange.TRADE_BLOCKED`. Квалификатор
«по severity / safetyImpact» для `ExternalStatusException` **снят** —
эскалация безусловна и **доминирует над L3** (бесстоповая позиция
постфактум). Обоснование: контролируемая биржевая ошибка — сигнал
недоверенной интеграции, истинный радиус поражения неизвестен, потому
тормозим консервативно (вся биржа).

## Разделение ответственности

Resolver FSM-решение не принимает и сущность не сохраняет; client/adapter
сделку в новый статус напрямую не переводит; refresh/executor boundary
ловит exception, обновляет сущность и отдаёт факты FSM/handler'у. `Deal`
→ `ERROR` — non-terminal runtime status для `ErrorHandler`/safety-flow.

## Первоисточник и смежное

Правило сквозное по командам/статусам (`.claude/decisions/rule-source-of-truth.md`);
пересекается с `docs/rules/external-status-resolution.md` (resolver-
result, unknown→exception) и `docs/rules/exchange-hold.md` (что HOLD
блокирует/разрешает). Эффект на `Deal` — `docs/lifecycles/Deal.md`.
