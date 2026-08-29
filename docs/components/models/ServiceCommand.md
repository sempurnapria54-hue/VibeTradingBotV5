# ServiceCommand

## На какой вопрос отвечает этот файл

Что это за атомарная команда над runtime-сущностью.

## Назначение

Атомарная операция, которую можно передать исполнителю. Неизменяемый
runtime-объект: **не хранимая сущность и не durable-очередь**.

Отвечает на вопрос «какую простую операцию выполнить»; исполнитель — «как
технически»; FSM — «зачем сейчас и можно ли идти дальше».

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `type` | `ServiceCommandType` | Тип атомарной операции. |
| `dealId` | `Long` | Сделка. |
| `dealActionStateId` | `Long` | Строка исполнения — анкер повторов; один на оба вида действий. Пусто только у дочисток. |
| `payload` | `ServiceCommandPayload` | Параметры выполнения. |

**Анкер по виду действия:** у команд ног действий стратегии —
стратегийное исполнение; у звеньев системных действий — системное; у
дочисток анкера нет вовсе, поэтому нет и бюджета отказов.

## Енум `ServiceCommandType`

**Создание и отправка:** `CREATE_ORDER_COMMAND`,
`SUBMIT_ORDER_COMMAND`, `CREATE_ALGO_ORDER_COMMAND`,
`SUBMIT_ALGO_ORDER_COMMAND`.

**Отмена и закрытие:** `CANCEL_ORDER_COMMAND`,
`CANCEL_ALGO_ORDER_COMMAND`, `CLOSE_POSITION_COMMAND`.

**Добыча:** `REFRESH_ORDER_COMMAND`, `REFRESH_ALGO_ORDER_COMMAND`,
`REFRESH_POSITION_COMMAND`, `REFRESH_BALANCE_COMMAND`,
`REFRESH_BILLS_COMMAND`.

**Финализация:** `FINALIZE_DEAL_ENTRY_COMMAND`,
`FINALIZE_DEAL_EXIT_COMMAND`, `MARK_DEAL_CLOSED_COMMAND`,
`MARK_DEAL_ERROR_COMMAND`, `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`.

Амендных команд нет: ремодел идёт замещением
(`docs/rules/replace-not-amend.md`).

## Ключевой инвариант

**Не persisted-очередь.** Ожидающие команды в базе не хранятся: после
рестарта нужная команда выводится из фактов заново. Критерий атомарности
— `docs/rules/command-lifecycle.md`.

## Связи

- Жизненный цикл — `docs/rules/command-lifecycle.md`.
- Параметры — `docs/components/models/ServiceCommandPayload.md`.
- Исполнение — `docs/components/ServiceCommandExecutor.md`.
