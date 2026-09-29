# Транш с отправленным входом уходит из предвходовой проверки к выходу

## На какой вопрос отвечает этот файл

Что сделано по секции «Транш с отправленной входной ногой остаётся в предвходовой проверке».

## Итог

Кодовый заход 210 шага 12 фазы 2 (Д2275-Д2276). Под сворачиванием сделки
`TranchePrecheckHandler` у транша, чья входная нога вышла за локальное
заведение, терминала не просит, а ведёт его ребром `PRECHECK →
ENTRY_SUBMITTED`; дальше обработчик отправленного входа уводит его в выход.
Гейт `TrancheTransitionGate#riskCreatingUnderCollapse` это ребро набором риска
не считает: риск взят до окна. Находка `F14` документа
`.claude/tests/cases/e2e-exit-and-close.md` закрыта, клетка `E2.3` снята с
метки `debt`.

- Дом — `docs/rules/exit-teardown-order.md` (строка `PRECHECK` таблицы
  энфорсеров); исполнимая форма — `docs/spec/deal-tranche-lifecycle.json`,
  величины `trancheEntrySent` и `riskCreatingUnderCollapse` (новый пример
  падает на прежней форме); указатели — `docs/components/TranchePrecheckHandler.md`,
  `docs/lifecycles/DealTranche.md`.
- Юниты — `U14.13`-`U14.15`, `U17.17` (`.claude/tests/cases/trading-core-fsm.md`).
- Соседняя тропа — нога в `CREATED` под сворачиванием — припаркована:
  `.claude/work/backlog.md` §«Транш с незаведённой на площадке входной ногой
  под сворачиванием терминала не получает».
- Прогоны — модуль `trading-core` (1711 тестов, дефектов 0) и класс
  `ExitCascadePathTest` (7, дефектов 0); полный реактор — при валидации шага.
