# Шаг 12 фазы 2 — ветви гейта терминалов сделки и коллаборатор диспозиции

## На какой вопрос отвечает этот файл

Чем закрыты секции бэклога о гейте терминалов сделки и неиспользуемом коллабораторе обработчиков транша (заход 231).

## Итог

Сняты три секции `.claude/work/backlog.md` владельца `code-writer`;
решение — Д2341 (`.claude/work/decision-digest.md`).

- **§«Контракт аварийного терминала гейт мерит одним конъюнктом из двух».**
  Сведение — второй стороной, которую секция и называла: спека объявляет,
  что второй конъюнкт (`emergencyTerminalContract`, «число посчитано, а не
  подставлено») исполняют писатели числа, и называет всех трёх — провенанса
  у числа в данных нет, и гейт отличить подстановку не может. Нота
  `docs/spec/deal-lifecycle.json`, дом прозой `docs/lifecycles/Deal.md`,
  `docs/components/MarkDealEmergencyClosedExecutor.md`. Клетка `U3.11`
  снята с `debt` и перевёрнута: пинит, что ветвь провенанса не читает.
  Находка F-1 закрыта.
- **§«Имя `cleanTerminalContract` у спеки и у гейта называет разные
  конъюнкции».** Методы `DealTransitionGate` названы ветвями композиции
  `transitionAllowed`: `cleanTerminalAllowed`, `emergencyTerminalAllowed`.
  Находка F-8 закрыта.
- **§«Неиспользуемый коллаборатор диспозиции у двух обработчиков транша».**
  Поле снято у `TrancheProtectionSwitchedHandler` и
  `TrancheEntryFinalizedHandler`. Находка F-4 закрыта.

Кейсы — `.claude/tests/cases/trading-core-fsm.md`. Прогон — модуль
`services/trading-core`, 1742 теста, дефектов 0. Запись захода —
`.claude/work/progress/phase-2-step-12-chronicle.md`,
§«Сто шестьдесят третий заход (231) — ось отбора входной ноги».
