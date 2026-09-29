# Шаг 12 фазы 2 — тело ответа соседа, момент журнала признаков, счёт поводов журнала

## На какой вопрос отвечает этот файл

Чем закрыты секции бэклога о разборе тела ответа соседа, моменте журнального отчёта признаков терминала и счёте поводов отказа чтения журнала (заход 231).

## Итог

Сняты три секции `.claude/work/backlog.md` владельца `code-writer`;
решение — Д2342 (`.claude/work/decision-digest.md`).

- **§«Неразбираемое тело ответа соседа читается как недоступность».** Обе
  копии `PeerCall` (`trading-core`, `strategies`): недоступность — только
  `ResourceAccessException` (таймаут, обрыв, отказ соединения), остаток
  семейства клиента — неразбираемое тело, неизвестный тип содержимого — наш
  дефект (`PeerReadException`). `U7.10`, `U7.11`
  `.claude/tests/cases/platform-shared-logic.md` сняты с `debt`; находка F2
  закрыта.
- **§«Журнальный отчёт признаков терминала пишется той же транзакцией, что и
  сам терминал».** `DealTerminalFeaturesWriter#journal` откладывает запись до
  коммита вызывающего (`util.AfterCommit`) и пишет её своей транзакцией
  (`AnomalyReportService#journalApart`, `REQUIRES_NEW`). `U8.12`
  `.claude/tests/cases/trading-core-calc.md` стала прогоняемой на уровне 2;
  находка `F5` закрыта.
- **§«Счёт отвержений вопроса чтения в javadoc выборки меньше
  действительного».** `JournalReadService#rejectUnlessAcceptable` перечисляет
  поводы без счёта; находка F-7 `.claude/tests/cases/audit.md` закрыта.

Мутация (прежний разбор соседа и немедленная запись журнала) — красны ровно
`U7.10`, `U7.11`, `U8.12`. Прогон — модули `services/common/test-support`,
`services/strategies`, `services/trading-core`, `services/audit`, дефектов 0.
Запись захода — `.claude/work/progress/phase-2-step-12-chronicle.md`,
§«Сто шестьдесят третий заход (231) — ось отбора входной ноги».
