# Локальные вопросы: миграция «Аудит и история исполнения»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «Аудит и история исполнения»
(локальные вопросы и форвард-заметки прохода 1).

## Открытые вопросы

- **АУ-Q1. Модели аудита не финализированы.** Сам архивный док — рабочий
  каркас: модели истории исполнения команд (`ServiceCommandExecutionHistory`),
  истории изменения сущностей и timeline сделки **не спроектированы**.
  §5 и §8 содержат ~30 нерешённых подвопросов (нужна ли запись на каждую
  попытку; before/after snapshots; raw response; correlation id; формат
  snapshot; группировка timeline; отдельные записи для risk/calculation
  decisions). На проходе 2 материализовать как тонкий процессный каркас
  `docs/processes/<audit-execution-history>.md` + вынести подвопросы в
  `open-questions.md` (новый продуктовый блок AUDIT-Q*). Связано с
  открытым **DEAL-Q2** (`open-questions.md`) и backlog п.6.
- **АУ-Q2. `closeReason` `ENTRY_RISK_BLOCKED` vs `RISK_CONTROL`.** §7.1
  допускает `ENTRY_RISK_BLOCKED`, но «Оценка рисков» §8.1 его явно
  отвергает в пользу `RISK_CONTROL` (так же в `lifecycles/Deal.md`).
  Решённый вариант — `RISK_CONTROL`; этот док старше. Зеркальная заметка
  в `tasks-оценка-рисков.md` (ОР-Q1).
- **АУ-Q3. `TradeFill` / `TradeFillsArchive`: персистить или нет.** Этот
  док и «Сервисные команды» фиксируют: «`Fill` как отдельную persisted
  entity на первом этапе НЕ храним» (REFRESH_FILLS только обновляет
  вложенные сущности). Но backlog п.6 планирует модели `TradeFill`/
  `TradeFillsArchive`. Противоречие/несинхрон по этапности. На проходе 2
  согласовать: материализация `TradeFill` — под вопросом (отложено по
  букве процессных доков). Связано с правилом «`Deal.resultProfit` через
  REFRESH_FILLS» (владелец — `Deal.md`).

## Форвард-заметки

- **АУ-FW1.** §1–4/§6 — устойчиво зафиксированные инварианты аудита
  (аудит не источник runtime-логики FSM; `Order`/`AlgoOrder`/`Position`
  не хранят `strategyActionId`; связь через `DealActionState`;
  `ServiceCommand` не persisted queue; `REFRESH_BALANCE` попадает в
  историю; `shutdownReason` виден в timeline, не заменяет `closeReason`;
  CLOSED vs EMERGENCY_CLOSED различимы; ENTRY_CONDITION_EXPIRED — норма).
  Кандидат: сквозное правило `<audit-not-runtime-source>` (`docs/rules/`)
  + расширения владельцев. Пересекается со «Сервисные команды» §2.3, §15.
- **АУ-FW2.** §7 (Q2–Q8 дополнение) — что аудит должен уметь объяснить
  про risk-layer / ошибки расчёта / EXCHANGE_ERROR / CLOSE_POSITION
  refresh-контур / REFRESH_FILLS. Дублирует решённый материал других
  доков; здесь — только «что показать в истории/timeline». Не дублировать
  модели, ссылаться на владельцев.
- **АУ-FW3.** §8.1 «Snapshot format» — открыт формат snapshot'ов для
  command execution history, entity history, `AnomalyReport`, KillSwitch
  report, `SynchronizeExecutionEnvironmentReport`. Пересекается с
  отложенным backlog-вопросом про стандарт персистентности jsonb-снимков
  (`AnomalyReport.internalBefore/After` и т.п.).
