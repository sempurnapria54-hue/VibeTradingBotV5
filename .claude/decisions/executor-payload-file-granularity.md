# Гранулярность файлов command-layer: file-per-executor, payload у своего executor'а

## На какой вопрос отвечает этот файл

Почему документация command-layer гранулируется file-per-executor,
а payload'ы документируются при своих executor'ах, а не отдельными
файлами и не общим агрегирующим файлом.

## Контекст

Проход 2 миграции процессов дал ~14 executor'ов и 9+
payload-классов («Сервисные команды» §13, §10). Рабочее решение
прохода: executor'ы — file-per-executor, payload'ы — один общий
`docs/components/models/ServiceCommandPayload.md` с разделами;
гранулярность была помечена открытым вопросом CMD-Q1.

## Решение

- **Executor'ы — file-per-executor**
  (`docs/components/<X>Executor.md`).
- **Payload документируется разделом в доке своего executor'а.**
  Отдельного агрегирующего файла payload'ов нет — прежнее рабочее
  решение «один `ServiceCommandPayload.md` с разделами»
  отменяется.
- **Четыре refresh-executor'а** (`REFRESH_PENDING_ORDERS` /
  `REFRESH_ALGO_ORDERS` / `REFRESH_ORDER_HISTORY` /
  `REFRESH_ALGO_ORDER_HISTORY`), не имевшие отдельных секций в
  источнике, отдельными файлами не заводятся (покрыты общей
  семантикой `REFRESH_*` и `ServiceCommandType`).
  **Обновление (CMD-Q3, 2026-06-10):** эти четыре команды сняты из
  `ServiceCommandType` вовсе — их эндпоинты стали звеньями внутреннего
  evidence-cycle entity-refresh-команд
  (`docs/rules/command-lifecycle.md`).

## Обоснование

- Прецедент «Java-класс-исполнитель → компонент, 1:1 с элементом
  структуры» — `.claude/decisions/fsm-handler-as-component.md`;
  фактическая структура (15 executor-файлов + 7 handler-файлов)
  устоялась без дублей и неудобств.
- Payload вне своего родителя-executor'а ценности не имеет: его
  потребитель и контекст — ровно один executor; раздел при
  executor'е выражает это сильнее, чем общий файл-агрегат
  (углубление критерия гранулярности
  `.claude/decisions/model-granularity.md`).

## Альтернативы (отвергнуты)

- **Группировка executor'ов по семантике**
  (CREATE_*/SUBMIT_*/AMEND_*/CANCEL_*/REFRESH_*): ломает
  соответствие «файл = Java-класс», осложняет ссылки.
- **Payload'ы отдельными файлами:** 9+ мелких файлов без
  самостоятельной ценности.
- **Один общий файл payload'ов с разделами** (рабочее решение
  прохода 2): хранит payload отдельно от его единственного
  потребителя; отменено этим решением.

## Отложенный подвопрос (закрыт)

CMD-Q2 (общий базовый тип/дискриминатор payload'ов + судьба
`ServiceCommandPayload.md`) закрыт на `GAPS_CLOSE_1` шага 4 (2026-06-10)
решением `docs/components/models/ServiceCommandPayload.md`: маркер-база
`ServiceCommandPayload` (без поведения), дискриминатор — `ServiceCommandType`
на команде, `ServiceCommandPayload.md` — дом базового типа. Payload-разделы
перенесены к своим executor'ам.

## Закрытие вопроса

CMD-Q1 закрыт 2026-06-06: R1-пакет делегирования
(`knowledge-curator`) принят с правкой пользователя на валидации —
payload'ы документируются при своих executor'ах вместо общего
файла с разделами.
