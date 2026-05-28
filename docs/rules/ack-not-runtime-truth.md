# ACK биржи не является runtime truth

## На какой вопрос отвечает этот файл

Какое правило системы определяет, что ACK команды на бирже не
подтверждает фактическое состояние сущности.

## Правило

ACK (успешный response команды, `code = 0`) подтверждает только, что
биржа приняла команду, — но **не** является runtime truth о
фактическом состоянии сущности.

- ACK от `CLOSE_POSITION` не закрывает `Position` и не меняет
  `Position.status` на `CLOSED`. Факт закрытия подтверждается
  отдельным `REFRESH_POSITION`.
- Аналогично для других команд: фактическое состояние сущности
  подтверждается соответствующим `REFRESH_*`, а не ACK-ом.
- Executor после ACK не переводит сущность в финальный статус.

### ACK от submit / amend / cancel / close

- `SUBMIT_*`: ACK не означает, что сущность точно отправлена/активна;
  факт подтверждается refresh/search/history. После ACK action может
  перейти в `SUBMITTED`, но это не подтверждённый финал.
- `AMEND_*`: ACK не подтверждает новые параметры — подтверждает refresh.
- `CANCEL_ORDER` / `CANCEL_ALGO_ORDER`: ACK не финализирует сущность,
  `CANCELED` по ACK не ставится; `closeReason` не перетирается, если уже
  установлен. Если refresh/history показывает другой факт (например, algo
  `effective`/`partially_effective`) — система верит exchange facts.
- `CLOSE_POSITION`: см. выше — full close подтверждается
  `REFRESH_POSITION`.

## Почему

Сквозное правило по командам (`.claude/decisions/rule-source-of-truth.md`
— «ACK не runtime truth → docs/rules/»). ACK и фактическое состояние
на бирже могут расходиться (частичное исполнение, гонки, отложенный
эффект); единственный источник истины о состоянии — refresh-snapshot
с биржи.

## Связанное

- `docs/lifecycles/Position.md`, `docs/rules/no-partial-close.md`.
- Подсистема `ServiceCommand` / executors — форвард-заметки в
  task-вопросах соответствующих сущностей.
