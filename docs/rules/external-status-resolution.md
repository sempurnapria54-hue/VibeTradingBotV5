# Резолвинг внешнего статуса и safety-каскад

## На какой вопрос отвечает этот файл

Какое правило системы определяет работу с сырым внешним статусом
сущности и реакцию на нераспознанный статус / ненайденную сущность.

## Правило

- FSM и handlers **не** используют сырой внешний статус биржи
  (`externalStatus`) напрямую. Сначала он проходит через resolver
  (`OrderExternalStatusResolver` и аналоги), который нормализует
  его в доменный статус либо бросает controlled exception. Resolver
  не сохраняет сущность, не меняет `Deal`, не создаёт команды, не
  принимает FSM-решения.
- **Unknown external status** не маппится в обычный доменный статус
  и не в `Status.ERROR` как mapping-результат. Resolver бросает
  `ExternalStatusException(reasonCode = UNKNOWN_EXTERNAL_STATUS)`;
  refresh/executor boundary ловит её и выполняет safety-каскад.
- **Not found после полного evidence-cycle**: если ожидаемая
  сущность не найдена после **полного** цикла источников (details +
  pending + history + archive при необходимости), boundary бросает
  `ExternalNotFoundException` и выполняет safety-каскад с
  `MISSING_AFTER_REFRESH`. Пустой ответ одного endpoint — **не**
  основание для финального вывода.
- **Result-object и write-once closeReason.** Resolver возвращает
  result-object `status + optional closeReason candidate` (обобщённо
  `EntityStatusResolveResult` / `StatusResolveResult<S,C>`); refresh/
  executor применяет `status` всегда, а `closeReason candidate` —
  только если текущий `closeReason == null` (ранее установленный не
  перетирается). Для `Order`/`AlgoOrder` resolver работает с внешним
  статусом биржи; для `Position` — с фактом наличия позиции
  (`PositionExternalSnapshot` / `null`), где успешный `null` =
  нормальный closed-on-exchange факт (`CLOSED` + `EXTERNAL_CLOSE`), а
  не `ExternalNotFoundException`. Компоненты-resolver'ы —
  `docs/components/OrderExternalStatusResolver.md`,
  `AlgoOrderExternalStatusResolver.md`, `PositionStatusResolver.md`.

## Safety-каскад

```text
<entity>.status = ERROR
<entity>.closeReason = UNKNOWN_EXTERNAL_STATUS | MISSING_AFTER_REFRESH
Deal.status = ERROR
Exchange.status = HOLD   (см. docs/rules/exchange-hold.md)
```

`ERROR` — это локальное safety-состояние сущности после
невозможности безопасно интерпретировать внешний факт, а не
распознанный биржевой статус. `MISSING_AFTER_REFRESH` означает, что
система не смогла найти expected entity и безопасно объяснить её
финал — признак ошибки интеграции / id mapping / query / pagination /
history-window; требует остановки normal trading-flow до разбора.

## Почему

Сквозное правило по нескольким сущностям (`Order`, `AlgoOrder`,
attached protection) без единственного владельца — первоисточник в
сквозном слое (`.claude/decisions/rule-source-of-truth.md`).
Exchange-specific перечень статусов и evidence-cycle endpoints — в
`docs/client/<Биржа>/rules/` (для OKX —
`docs/client/okx/rules/okx-order-mapping.md`).

## Связанное

- `docs/rules/exchange-hold.md`, `docs/rules/ack-not-runtime-truth.md`.
- `docs/lifecycles/Order.md`.
