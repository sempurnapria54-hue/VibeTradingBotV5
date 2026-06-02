# Snapshot v24

**Дата:** 2026-06-02.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после полного закрытия
`GAPS_CLOSE_2` шага 2 Фазы 1: разобраны STRAT-Q1/Q2/Q3, закрыты
эскалации Э2/Э3/Э5; готов переход к `DOCS_CHECK_3`).

## Состояние

Фаза 1 — `IN_PROGRESS`; шаг 1 — `DONE`; **шаг 2 (Стратегия) —
`GAPS_CLOSE_2`** (закрыт полностью); шаги 3-11 — `HOLD`. Шаг 2 идёт по
docs-first процессу: `DOCS_CHECK_1` → `GAPS_CLOSE_1` (Э1: полное
монолитное дерево; TIME-Q1 закрыт) → `DOCS_CHECK_2` (эскалации
Э4/Э2/Э3/Э5) → `GAPS_CLOSE_2` (Э4 — сессия 2026-06-01; Э2/Э3/Э5 —
сессия 2026-06-02). Следующее — **`DOCS_CHECK_3`** (третья проверка
целостности; сейчас не запускалась).

## Что изменилось относительно v23

### Э2 / STRAT-Q1 — контракт авторинга условия закрыт

Источник истины — объектная settings-модель; строка-метка
`leftOperand` + инлайновый `params` — форма ввода (API резолвит при
сохранении). Ключевое:

- **Настройка индикатора** `{ key, type, params }`: `key` — для ссылки
  операнда (`indicatorKey`); `timeframe` и `warmup` живут **в
  `params`** (JSONB). `timeframe` поднять в колонку — позже, по
  потребности candle-loading.
- **`warmup`** выводится реализацией индикатора из `type`+`period`
  (оконные `=period`; рекурсивные EMA/RSI/ATR — кратно; MACD — от
  старшего); автор может override; эффективный `= override ?? derived`;
  потребитель — candle-loading.
- **Правило** — единая структура, операнды опциональны; rule-level
  `sourceType`/`timeframe`/объектные ссылки `indicatorSetting`/
  `marketStructureSetting` убраны; любой источник на любой стороне
  (indicator-vs-indicator — базовый кроссовер).
- **Операнд** самоописателен: `valueType`/`value` только у `CONSTANT`;
  вычисляемые источники — тип из `sourceType`, значение в рантайме;
  индикаторный операнд → настройка по `indicatorKey`.
- **Иммутабельность**: любое изменение = новая версия стратегии.

Размещено: новый decision
`docs/decisions/strategy-condition-authoring-contract.md`; переписан
§Условия в `Strategy.md`, §StrategyIndicatorSetting (`key`),
§IndicatorParams (`timeframe`/`warmup` + вывод), архитектурный
инвариант о ключах настроек, §Внутридеревные ссылки; уточнён
`strategy-tree-persistence.md` (ссылка операнд→настройка по ключу, не
rule-level FK).

### Э3 / STRAT-Q2 — «сигнал» = условие входа

«Условие сигнала» = `condition.rules` входного шага
(`ENTRY`/`GRID_ENTRY`); отдельной сущности нет. Удалены орфаны
`StrategyConditionSourceType.SIGNAL` и
`StrategyConditionRuleType.SIGNAL_SCORE_REACHED` (рационал удаления
зафиксирован). Decision
`docs/decisions/strategy-signal-is-entry-condition.md`; enum'ы вычищены
в `Strategy.md`.

### Э5 / STRAT-Q3 — материализация и валидация

Материализация «одной реализации» — Strategy API полным жизненным
циклом (`POST`/`GET`/`PUT`), дом — шаг 2. Валидатор — линия реза
create (структурно-ссылочные пункты, 400) / activate (семантика
действий, 422 — отложено до шагов 4/7). Decision
`docs/decisions/strategy-materialization-and-validation.md`; §валидация
в `Strategy.md` дополнен; backlog п.8 обновлён.

### Cross-cutting запаркован

Глубина под прогрев индикаторов (эффективный `warmup` + `timeframe`) —
вход для глубины загрузки свечей; конкретное перекладывание в
`plannedCandleStartDate` запарковано в `docs/processes/candle-loading.md`
(+ cross-ref в `docs/components/IndicatorJob.md`), всплывёт с ORCH-Q1.

### Заведён STRAT-Q4

percent-anchor («−N% относительно чего»: вход / предыдущая свеча / хай)
— вынесен из STRAT-Q1 как самостоятельная бизнес-развилка.

## Активные задачи

- **Шаг 2, `GAPS_CLOSE_2`** — закрыт полностью (Э4/Э2/Э3/Э5). Прогресс
  — `.claude/work/progress/phase-1-step-2-gaps-close-2.md` (рядом —
  `docs-check-1/2`, `gaps-close-1`).

## Текущий фронтир / следующее действие

- **`DOCS_CHECK_3`** — третья проверка целостности концепции доков
  шага 2 (роль `reviewer`, фокус `concept-review`). В этой сессии
  **не** запускалась.
- **Коммит.** Все правки сессии — staged, не закоммичены (CC не
  коммитит); коммит — за пользователем.

## Открытые общие вопросы

`open-questions.md`: **15** открыто. Закрыты STRAT-Q1/Q2/Q3 (три
decision'а в `docs/decisions/`); заведён STRAT-Q4. Прежние 14 без
изменений (DEAL-Q1/Q2/Q3, PROC-Q1, RISK-Q1, INSTR-Q1/Q2, ORCH-Q1,
ENUM-Q1, CMD-Q1, OKX-Q1..Q4). По границе шага 2 не гейтит ни один
(STRAT-Q4 — бизнес-инкремент, контракт не блокирует).

## Что в работе / PK

- Шаг 2 — `GAPS_CLOSE_2` закрыт; дальше `DOCS_CHECK_3`.
- **Project Knowledge:** последний снапшот теперь **`snapshot-v24`**
  (заменяет v23 в префлайте). `structure.md` / `naming.md` в этой
  сессии **не менялись** — их копии в PK обновлять не нужно; обновить
  только указатель на снапшот.
- Затронуто (всё staged): три новых decision'а в `docs/decisions/`
  (`strategy-condition-authoring-contract`,
  `strategy-signal-is-entry-condition`,
  `strategy-materialization-and-validation`),
  `docs/decisions/strategy-tree-persistence.md` (уточнения),
  `docs/models/domain/aggregate/Strategy.md`,
  `docs/processes/candle-loading.md`, `docs/components/IndicatorJob.md`,
  `.claude/work/questions/open-questions.md`, `.claude/work/backlog.md`,
  `.claude/work/progress/phase-1-step-2-gaps-close-2.md`, этот снапшот.
