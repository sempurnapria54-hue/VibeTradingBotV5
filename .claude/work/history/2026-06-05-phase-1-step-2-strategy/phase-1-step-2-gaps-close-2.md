# GAPS_CLOSE_2 — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

Что сделано на под-шаге `GAPS_CLOSE_2` шага 2 (закрытие пробелов
`DOCS_CHECK_2`): какие эскалации закрыты, какими решениями, что
вынесено в новый открытый вопрос.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия (абстракция: объявляет
  нужные индикаторы и условие сигнала; одна реализация)».
- Под-шаг: `GAPS_CLOSE_2` (`.claude/processes/roadmap-step-execution.md`).
- Вход — gap-отчёт `phase-1-step-2-docs-check-2.md`: эскалации Э4
  (персистентность), Э2 (грамматика условия), Э3 (терминология
  «сигнала»), Э5 (создание/валидация одной реализации).
- Проход вёлся в **две сессии**: первая (2026-06-01) закрыла Э4 и
  зафиксировала направление Э2, завела STRAT-Q1/Q2/Q3; вторая
  (2026-06-02) разобрала STRAT-Q1/Q2/Q3 и **дозакрыла Э2/Э3/Э5**.
  Теперь `GAPS_CLOSE_2` закрыт полностью; следующее — `DOCS_CHECK_3`.

## Э4 — персистентность Strategy (закрыто, сессия 1)

**Решение.** Каркас дерева — реляционный; объектные связи через FK;
загрузка целиком через `@EntityGraph` / `JOIN FETCH`. Индикаторные
`params` — JSONB (отход от архива); действия — наследование `JOINED`;
`stepsByStatus` — плоские строки; ссылки — FK/мягкие/self-FK по типу.
Размещение: `docs/decisions/strategy-tree-persistence.md` + раздел
«Персистентность» в `docs/models/domain/aggregate/Strategy.md`.

Уточнено в сессии 2: бул «правило условия → настройка = FK» снят —
ссылку несёт операнд по ключу (см. Э2/STRAT-Q1 ниже).

## Э2 / STRAT-Q1 — контракт авторинга условия (закрыто, сессия 2)

**Решение.** Источник истины — объектная settings-модель; строка-метка
`leftOperand` + инлайновый `params` — форма ввода. Настройка индикатора
`{ key, type, params }` (`timeframe`/`warmup` — в `params`); `warmup`
выводится реализацией, override возможен (эффективный = override ??
derived; потребитель — candle-loading). Правило — единая структура,
операнды опциональны; rule-level `sourceType`/`timeframe`/объектные
ссылки убраны; операнд самоописателен, `valueType`/`value` только у
`CONSTANT`, индикаторный операнд → настройка по `indicatorKey`.

**Размещение.** `docs/decisions/strategy-condition-authoring-contract.md`
(новый); переписан §Условия в `Strategy.md` (контракт, правило,
операнд), §StrategyIndicatorSetting (`key`), §IndicatorParams
(`timeframe`/`warmup` + вывод warmup), архитектурный инвариант о ключах
настроек, §Внутридеревные ссылки в §Персистентности; уточнён
`strategy-tree-persistence.md`. Cross-cutting warmup → глубина загрузки
запаркован в `docs/processes/candle-loading.md` + cross-ref в
`docs/components/IndicatorJob.md`.

## Э3 / STRAT-Q2 — «сигнал» = условие входа (закрыто, сессия 2)

**Решение.** «Условие сигнала» = `condition.rules` входного шага
(`ENTRY`/`GRID_ENTRY`); отдельной сущности «сигнала» нет. Удалены
орфаны `StrategyConditionSourceType.SIGNAL` и
`StrategyConditionRuleType.SIGNAL_SCORE_REACHED` (рационал удаления
зафиксирован, чтобы не вернуть по инерции).

**Размещение.** `docs/decisions/strategy-signal-is-entry-condition.md`
(новый); enum'ы вычищены в `Strategy.md` §Условия.

## Э5 / STRAT-Q3 — материализация и валидация (закрыто, сессия 2)

**Решение.** Материализация «одной реализации» — Strategy API полным
жизненным циклом (`POST`/`GET`/`PUT`), дом API — шаг 2. Валидатор —
линия реза create (структурно-ссылочные пункты, 400) / activate
(семантика действий, 422 — отложено до шагов 4/7).

**Размещение.** `docs/decisions/strategy-materialization-and-validation.md`
(новый); §key / targetActionKey и валидация в `Strategy.md` дополнен
линией create/activate; backlog п.8 обновлён (scope решён, остаётся
воспроизведение `Strategy API examples`).

## Новый открытый вопрос

- **STRAT-Q4** — percent-anchor («−N% относительно чего»: вход /
  предыдущая свеча / хай). Вынесен из STRAT-Q1 как самостоятельная
  бизнес-развилка. Заведён в `.claude/work/questions/open-questions.md`.

## Что изменено

| Файл | Что |
|---|---|
| `docs/decisions/strategy-tree-persistence.md` | Э4 (сессия 1); уточнён бул ссылки операнд→настройка, следствия/связи (сессия 2). |
| `docs/decisions/strategy-condition-authoring-contract.md` | Новый (Э2/STRAT-Q1). |
| `docs/decisions/strategy-signal-is-entry-condition.md` | Новый (Э3/STRAT-Q2). |
| `docs/decisions/strategy-materialization-and-validation.md` | Новый (Э5/STRAT-Q3). |
| `docs/models/domain/aggregate/Strategy.md` | Инвариант ключей; §StrategyIndicatorSetting (`key`); §IndicatorParams (`timeframe`/`warmup`); §Условия (контракт/правило/операнд/enum'ы); §валидация (create/activate); §Персистентность (операнд→настройка). |
| `docs/processes/candle-loading.md` | Запаркована глубина под прогрев (warmup → загрузка). |
| `docs/components/IndicatorJob.md` | Cross-ref на объявленную глубину warmup. |
| `.claude/work/questions/open-questions.md` | STRAT-Q1/Q2/Q3 закрыты; заведён STRAT-Q4; §Статус. |
| `.claude/work/backlog.md` | П.8: scope валидатора/материализации решён (STRAT-Q3). |

## Статус роадмапа

- Шаг 2: `GAPS_CLOSE_2` — **закрыт полностью** (Э4/Э2/Э3/Э5).
- Фаза 1: `IN_PROGRESS` (ролляп без изменений).
- Следующее — `DOCS_CHECK_3` (третья проверка целостности; в этой
  сессии **не** запускалась).

## Сводка

- Эскалаций закрыто: 4 из 4 (Э4 — сессия 1; Э2/Э3/Э5 — сессия 2).
- Новых решений: 3 (`strategy-condition-authoring-contract`,
  `strategy-signal-is-entry-condition`,
  `strategy-materialization-and-validation`) + уточнён
  `strategy-tree-persistence`.
- Закрыто открытых вопросов: 3 (STRAT-Q1/Q2/Q3); заведено: 1 (STRAT-Q4).
- `GAPS_CLOSE_2` чист; готов переход к `DOCS_CHECK_3`.
