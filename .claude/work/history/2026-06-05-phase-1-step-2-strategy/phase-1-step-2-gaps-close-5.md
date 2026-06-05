# GAPS_CLOSE_5 — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

Что закрыто в `GAPS_CLOSE_5` шага 2 Фазы 1 (STRAT-Q5 — представление
условия в БД; гигиена М1–М3 из `DOCS_CHECK_5`) и каков результат.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия».
- Под-шаг: `GAPS_CLOSE_5` (`.claude/processes/roadmap-step-execution.md`).
- Вход: gap-отчёт `phase-1-step-2-docs-check-5.md` (STRAT-Q5 —
  единственный содержательный блокер; М1–М3 — гигиена) плюс решение из
  чата: STRAT-Q5 закрывается **вариантом A** — условие целиком JSONB на
  строке `strategy_step`, по дефолту правила
  `docs/rules/persistence-representation.md`.

## Что сделано (размещение знания)

### STRAT-Q5 — условие → JSONB на строке `strategy_step` (главное)

Условие (`StrategyCondition` с `rules` и операндами) персистится
целиком JSONB-полем `condition` на строке `strategy_step`: массив
правил (`level` / `ruleType` / `operator` + простые поля), операнды —
JSONB внутри того же объекта. Таблиц `strategy_condition` /
`strategy_condition_rule` нет; перечень каркасных узлов не пополняется.
Отвергнут вариант B (реляционные `strategy_condition` 1:1 +
`strategy_condition_rule`): исключение из правила без
storage-специфичной причины (нет SQL-запросов по правилам между
стратегиями, нет внешнего FK на правило; `strategy_condition` —
1:1-таблица без полезной нагрузки).

Классификация и размещение:

- **Решение с альтернативой** — тип «почему мы решили так, а не иначе
  (продукт)» → существующий decision
  `docs/decisions/strategy-tree-persistence.md` (та же тема — схема
  персистентности дерева; решение эволюционирует ревизиями, новый файл
  не нужен): новый §Условие (`GAPS_CLOSE_5`, STRAT-Q5) с обоснованием
  и отвергнутой альтернативой; §Следствия — новый пункт
  `GAPS_CLOSE_5`; шапка дока расширена («настройки и условие — JSONB у
  владельца»); §Связи — STRAT-Q5 убран из перечня открытых.
- **Схема в модели** — тип «что это за торговая модель» →
  `docs/models/domain/aggregate/Strategy.md` §Персистентность: интро
  дополнено условием; новый подраздел §Условие; §Внутридеревные ссылки
  — «операнды — JSONB на строке `strategy_condition_rule`» заменено на
  «внутри condition-JSONB на строке `strategy_step`»; из
  §Не зафиксировано убрана метка STRAT-Q5. Entity/миграция условия
  разблокированы.
- **Закрытие вопроса** — STRAT-Q5 удалён из `open-questions.md` (по
  конвенции: история закрытия — в decision); шапка файла обновлена
  (запись о закрытии со ссылкой на §Условие).

### М1 — устаревшее «уже принятое» в authoring-contract

`docs/decisions/strategy-condition-authoring-contract.md`, отвергнутая
альтернатива «`type`-строкой»: «настройки — реляционно с
JSONB-листьями» приведено к текущей правде — «настройки — JSONB-объекты
`{ key, type, params }` внутри контейнера; ревизия `GAPS_CLOSE_3` —
`strategy-tree-persistence.md`». Правка существующего файла, не новое
знание.

### М2 — мёртвые ссылки на `tasks/strategy.md`

Три ссылки на архивированный файл перенацелены на фактическое место —
`.claude/work/history/2026-05-27-миграция-торговых-сущностей/tasks-strategy.md`:
`Strategy.md` (раздел Strategy root — jobs; §Связи — кластеры),
`docs/lifecycles/Strategy.md` (§Природа статуса). Правка ссылок, не
новое знание.

### М3 — без отдельной правки

Фраза «строка `strategy_condition_rule`» была только в `Strategy.md` и
снята в рамках следствия STRAT-Q5; неточный текст самого STRAT-Q5
(«обоих доков») ушёл вместе с вопросом при закрытии.

## Проход по открытым вопросам (16 → 15)

- **Закрыт: 1** — STRAT-Q5 (решением выше).
- **Без изменений: 15** — STRAT-Q4 (непгейтящий бизнес-инкремент),
  DEAL-Q1/Q2/Q3, PROC-Q1, RISK-Q1, INSTR-Q1/Q2, ORCH-Q1, ENUM-Q1,
  CMD-Q1, OKX-Q1..Q4 (downstream; решение по условию их не трогает —
  DEAL-Q3 уже ссылается на общее правило, не на схему условия).
- **Добавлено: 0.**

## Статус

- Статус шага 2: `DOCS_CHECK_5` → `GAPS_CLOSE_5` (`phase-1.md`);
  ролляп Фазы 1 — `IN_PROGRESS` без изменений.
- Следующее — **`DOCS_CHECK_6`** (шестая проверка целостности):
  верифицировать согласованность применения решения по условию и
  зачисток М1–М2. Блокеров после закрытия STRAT-Q5 не остаётся — при
  чистом прогоне шаг готов к `CODE`.
- Открытые вопросы (`open-questions.md`): **15** (закрыт STRAT-Q5;
  новых нет).

## Затронутые файлы (staged)

- `docs/models/domain/aggregate/Strategy.md`
- `docs/decisions/strategy-tree-persistence.md`
- `docs/decisions/strategy-condition-authoring-contract.md`
- `docs/lifecycles/Strategy.md`
- `.claude/work/questions/open-questions.md`
- `.claude/work/roadmap/phase-1.md`
- `.claude/work/progress/phase-1-step-2-gaps-close-5.md` (этот файл)
