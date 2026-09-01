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
`CANCEL_ALGO_ORDER_COMMAND`, `CANCEL_ATTACHED_PROTECTION_COMMAND`,
`CLOSE_POSITION_COMMAND`.

**Почему у снятия встроенной защиты своя команда, а не адресат в чужой.**
Отмена условной заявки адресует `AlgoOrder` по его локальному
идентификатору и несёт причину из **его** перечня; встроенная защита —
другая сущность с другим жизненным циклом и **непересекающимся**
словарём причин (`SWITCHED_BY_STRATEGY` у неё есть, у отдельной заявки
нет; `REPLACED_BY_STRATEGY` наоборот). Одна команда на обе не может
типизировать причину: пришлось бы завести отдельный словарь намерений и
матрицу «намерение × адресат» с недостижимыми клетками — след **больше**,
чем у второй команды, а не меньше. Бремя обоснования бо́льшего следа
(`.claude/rules/design-simplicity.md`) этим и снято. На бирже операция та
же самая — снятие условной заявки по её `algoId` либо клиентскому
идентификатору, — и общий эндпоинт командами не удваивается.

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
