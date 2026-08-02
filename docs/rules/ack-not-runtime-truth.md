# ACK биржи не является runtime truth

## На какой вопрос отвечает этот файл

Какое правило системы определяет, что ACK команды на бирже не
подтверждает фактическое состояние сущности.

## Правило

ACK (успешный response команды, `code = 0`) подтверждает только, что
биржа приняла команду, — но **не** является runtime truth о
фактическом состоянии сущности.

- ACK от `CLOSE_POSITION_COMMAND` не закрывает `Position` и не меняет
  `Position.status` на `CLOSED`. Факт закрытия подтверждается
  отдельным `REFRESH_POSITION_COMMAND`.
- Аналогично для других команд: фактическое состояние сущности
  подтверждается соответствующим `REFRESH_*`, а не ACK-ом.
- Executor после ACK не переводит сущность в финальный статус.

### ACK от submit / cancel / close

- `SUBMIT_*`: ACK не означает, что сущность точно отправлена/активна;
  факт подтверждается refresh/search/history. После ACK action может
  перейти в `SUBMITTED`, но это не подтверждённый финал. Для
  REPLACE-ремодела (амендных команд нет —
  `docs/decisions/replace-not-amend.md`) это правило секвенсит ноги:
  следующая нога идёт только после подтверждения предыдущей
  **фактом**, не ACK-ом.
- `CANCEL_ORDER_COMMAND` / `CANCEL_ALGO_ORDER_COMMAND`: ACK не финализирует сущность,
  `CANCELED` по ACK не ставится; `closeReason` не перетирается, если уже
  установлен. Если refresh/history показывает другой факт (например, algo
  `effective`/`partially_effective`) — система верит exchange facts.
- `CLOSE_POSITION_COMMAND`: см. выше — full close подтверждается
  `REFRESH_POSITION_COMMAND`.

## Почему

Сквозное правило по командам (`.claude/decisions/rule-source-of-truth.md`
— «ACK не runtime truth → docs/rules/»). ACK и фактическое состояние
на бирже могут расходиться (частичное исполнение, гонки, отложенный
эффект); единственный источник истины о состоянии — refresh-snapshot
с биржи.

## Связанное

- `docs/lifecycles/Position.md`, `docs/rules/no-partial-close.md`.
- Подсистема `ServiceCommand` / executors —
  `docs/components/models/ServiceCommand.md`,
  `docs/components/ServiceCommandExecutor.md`,
  `docs/rules/command-lifecycle.md`.
