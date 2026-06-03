# Snapshot v26

**Дата:** 2026-06-03.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после `DOCS_CHECK_4` и полного
закрытия `GAPS_CLOSE_4` шага 2 Фазы 1: заведено общее правило
персистентности, закрыты Н2/Н3/Н4, Н1 оставлен открытым как STRAT-Q5;
готов переход к `DOCS_CHECK_5`).

## Состояние

Фаза 1 — `IN_PROGRESS`; шаг 1 — `DONE`; **шаг 2 (Стратегия) —
`GAPS_CLOSE_4`** (закрыт полностью); шаги 3-11 — `HOLD`. Шаг 2 идёт по
docs-first процессу: `DOCS_CHECK_1` → `GAPS_CLOSE_1` → `DOCS_CHECK_2`
→ `GAPS_CLOSE_2` → `DOCS_CHECK_3` → `GAPS_CLOSE_3` → `DOCS_CHECK_4`
(Н1/Н2/Н3/Н4 — несогласованности схемы дерева после ревизии) →
`GAPS_CLOSE_4`. Следующее — **`DOCS_CHECK_5`** (пятая проверка
целостности; пойдёт в новом чате, не запускалась).

## Что изменилось относительно v25

`DOCS_CHECK_4` нашёл 4 несогласованности (Н1 — персистентность
условия; Н2 — `UNIQUE(strategy_detail_id, key)` против схемы
`strategy_action`; Н3 — `MarketPhaseParams` смешан с листовыми params;
Н4 — дубль `indicatorType`). `GAPS_CLOSE_4` закрыл их не точечно, а
через **общее правило проекта** (кроме Н1 — оставлен открытым).

### Общее правило персистентности (главное)

Новый `docs/rules/persistence-representation.md` — представление любой
сущности в БД: **реляционно** — структурный каркас (для дерева
`Strategy`: `Strategy`, `StrategyMarketPhaseSetting`, `StrategyDetail`,
`StrategyStep`, `StrategyAction`) и сущности под FK из нескольких
мест; **всё навешанное** на каркас (настройки, `params`, операнды) —
JSONB в строке/JSON владельца, **число полей значения не имеет**
(критерий «>N полей → таблица» снят); **`params`** — всегда JSONB, в
коде десериализуется в подтип, прямых ссылок в БД нет; **полиморфный
JSONB** — дискриминатор на владельце (соседнее поле-тип), Jackson
`EXTERNAL_PROPERTY`, единственный источник тега.
`strategy-tree-persistence.md` помечен первоисточником-обобщением.

### Н2 — `strategy_action`: родительская FK + денормализация

Базовая таблица несёт `strategy_step_id` (FK на родительский шаг) и
**денормализованный** `strategy_detail_id` — `UNIQUE(strategy_detail_id,
key)` корректен (колонка в перечне). Денормализация безопасна при
immutable-дереве (как `target_action_id`). Правлено в `Strategy.md`
§Действия и `strategy-tree-persistence.md` §Действия.

### Н3 — `MarketPhaseParams` разведён с листовыми params

`MarketPhaseParams` — `params` **реляционного контейнера**: JSONB-колонка
на строке `strategy_market_phase_setting`, не часть JSON-массивов
листовых настроек. Из «едут внутри того же JSON» убран в обоих доках.

### Н4 — `indicatorType` снят с базы `IndicatorParams`

Дискриминатор подтипа `*Params` —
`StrategyIndicatorSetting.indicatorType` через Jackson
`EXTERNAL_PROPERTY`; подтипы несут только математику.

### Н1 — представление условия: оставлен открытым (STRAT-Q5)

Не закрыт сознательно. Под правило дефолт — JSONB на `strategy_step`,
но решение по условию принимается отдельно, правило не автоприменено.
Нестыковка доков (каркас без `strategy_condition_rule` vs трактовка
его таблицей) не разрешена — это содержание нового **STRAT-Q5** в
`open-questions.md` (переформулирован под правило: счётчик полей — не
аргумент); метка — в `Strategy.md` §Не зафиксировано.

### Проход по открытым вопросам

15 существующих проверены против правила: закрыто 0; отредактированы 2
(INSTR-Q1 — дефолт JSONB для будущих rules; DEAL-Q3 — БД-представление
вложенных объектов задаёт правило, открыта только доменная форма);
добавлен STRAT-Q5. Итого **16** открытых.

## Активные задачи

- **Шаг 2, `GAPS_CLOSE_4`** — закрыт полностью. Прогресс —
  `.claude/work/progress/phase-1-step-2-gaps-close-4.md` (рядом —
  `docs-check-1..4`, `gaps-close-1..3`). Итерационные progress-файлы
  шага 2 остаются в `progress/` до `DONE` шага.

## Текущий фронтир / следующее действие

- **`DOCS_CHECK_5`** — пятая проверка целостности концепции доков
  шага 2 (роль `reviewer`, фокус `concept-review`). В новом чате. Н1 на
  ней — известный открытый STRAT-Q5 (помечен в доках), не новый пробел;
  гейтит ли он `CODE` — решается там.
- **Коммит.** Все правки сессии — staged, не закоммичены (CC не
  коммитит); коммит — за пользователем.

## Открытые общие вопросы

`open-questions.md`: **16** (было 15; добавлен STRAT-Q5 —
представление условия в БД). По границе шага 2 STRAT-Q5 — единственный
кандидат в гейт миграции условия; остальные не гейтят.

## Что в работе / PK

- Шаг 2 — `GAPS_CLOSE_4` закрыт; дальше `DOCS_CHECK_5`.
- **Project Knowledge:** последний снапшот теперь **`snapshot-v26`**
  (заменяет v25 в префлайте). `structure.md` / `naming.md` /
  `CLAUDE.md` / `place-knowledge.md` в этой сессии **не менялись** —
  обновить только указатель на снапшот.
- Затронуто (всё staged): новый `docs/rules/persistence-representation.md`,
  `docs/models/domain/aggregate/Strategy.md`,
  `docs/decisions/strategy-tree-persistence.md`,
  `.claude/work/questions/open-questions.md`,
  `.claude/work/roadmap/phase-1.md`,
  `.claude/work/progress/phase-1-step-2-gaps-close-4.md`, этот снапшот.
