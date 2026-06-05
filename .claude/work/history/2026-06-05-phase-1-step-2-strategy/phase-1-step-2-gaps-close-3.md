# GAPS_CLOSE_3 — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

Что закрыто в `GAPS_CLOSE_3` шага 2 Фазы 1 (эскалации Н1/Н2/Н3 из
`DOCS_CHECK_3` плюс направление downstream-следствия) и каков результат.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия».
- Под-шаг: `GAPS_CLOSE_3` (`.claude/processes/roadmap-step-execution.md`).
- Вход: gap-отчёт `phase-1-step-2-docs-check-3.md` (Н1/Н2/Н3) плюс
  разобранное в чате решение. Н3 — **ревизия** прежнего решения по
  персистентности дерева (`GAPS_CLOSE_2`), не выравнивание.

## Что сделано (размещение знания)

### Н3 (главное) — листовые настройки хранятся JSONB внутри контейнера

`StrategyIndicatorSetting` / `StrategyMarketStructureSetting` (в обоих
контейнерах — `StrategyMarketPhaseSetting` и `StrategyDetail`) больше
**не** реляционные строки/таблицы: хранятся JSON-массивами на строке
контейнера; их `params` едут внутри того же JSON. Обоснование: на
настройку ничего не ссылается жёсткой FK (операнд `indicatorKey` и
мягкие ссылки JSON-листьев — по `key`); `id` настройки в рантайме не
используется; настройки immutable, читаются с родителем. Уникальность
`key` в контейнере — проверка приложения по JSON-массиву, не DB-UNIQUE.
Реляционными остаются контейнеры и каркасные узлы (`Strategy`,
`StrategyMarketPhaseSetting`, `StrategyDetail`, `StrategyStep`,
`StrategyAction`).

- `docs/models/domain/aggregate/Strategy.md` §Персистентность — интро
  переразложено (реляционный каркас только для контейнеров/каркаса;
  настройки + `params` — JSONB); раздел «Индикаторные `params`» заменён
  на «Настройки рыночных данных» (JSONB, ссылки по `key`, уникальность
  `key` — приложение).
- `docs/decisions/strategy-tree-persistence.md` — интро + раздел
  настроек переписаны под JSONB; добавлена врезка «Ревизия
  `GAPS_CLOSE_3`»; прежняя стойка `GAPS_CLOSE_2` вынесена в отвергнутые
  альтернативы; заголовочный вопрос файла обновлён.

Следствие (б) растворилось: `MarketStructureParams` / `MarketPhaseParams`
едут внутри JSONB-настройки, отдельного решения «JSONB vs колонки» для
них больше нет.

### Н3/(в) — `timeframe`: помечен провизорным, не выровнен

При JSONB персистентная асимметрия `timeframe` (колонка vs `params`)
исчезает. Доменное размещение оставлено как есть (индикатор → `params`,
структура/фаза → прямое поле настройки) — осознанный провизорный выбор;
ревизия с candle-loading. Зафиксировано отдельным подразделом в
`strategy-tree-persistence.md`.

### Н2 — убран `id` у `IndicatorParams`

`docs/models/domain/aggregate/Strategy.md` §IndicatorParams — `id` снят
из базы абстрактного `IndicatorParams` (рудимент архива при
JSONB-value-объекте; ссылок нет).

### Н1 — self-ссылка действия: ключ + self-FK

`docs/models/domain/aggregate/Strategy.md` (§Действия, §Внутридеревные
ссылки) и `docs/decisions/strategy-tree-persistence.md` (§Действия,
§Внутридеревные ссылки): базовая таблица `strategy_action` хранит **и**
`target_action_key` (форма ввода/чтения), **и** `target_action_id`
(self-FK `→ strategy_action.id`, резолвится при сохранении). Защита БД —
FK + CHECK `target_action_id <> id`. Денормализация принята как
безопасная при immutable-записи; пояснено, почему здесь FK (в отличие от
мягкой ссылки операнд→настройка по `key`) — ради БД-защиты ссылки
действие→действие.

### Downstream-следствие ревизии Н3 — направление решено

Под JSONB-настройки нет реляционного `id`, на который ссылались
результаты расчёта `IndicatorValue` (Фаза 3) / `MarketStructure`
(Фаза 4) через `*_setting_id`. Решено: результаты ключуются по
**идентичности считаемого** (тип + `timeframe` + canonical-`params`),
считаются раз на инструмент и шарятся всеми настройками; ссылка на
настройку убирается. Реализация (точная схема идентичности, поля
`UNIQUE`, проверка отсутствия зависимостей от прежнего ключа) — при
построении кластеров Фаз 3/4. `MarketPhase` не затронут (ключ —
контейнер `StrategyMarketPhaseSetting`, у него строка/`id` есть).

- Новый decision `docs/decisions/market-data-result-identity-keying.md`.
- Forward-flag в `strategy-tree-persistence.md` §Следствия обновлён
  («направление решено», ссылка на decision).
- Доки `IndicatorValue` / `MarketStructure` / job-доки **не правились**:
  старый `UNIQUE(…, *_setting_id, …)` переписывается на реализации
  Фаз 3/4, не раньше.

## Статус

- Статус шага 2: `DOCS_CHECK_3` → `GAPS_CLOSE_3` (`phase-1.md`); ролляп
  Фазы 1 — `IN_PROGRESS` без изменений.
- К `CODE` **не переходим**: следующее — `DOCS_CHECK_4` (четвёртая
  проверка целостности), пойдёт в новом чате.
- Открытые вопросы (`open-questions.md`): без изменений (15);
  downstream-следствие оформлено decision'ом, не открытым вопросом.

## Затронутые файлы (staged)

- `docs/models/domain/aggregate/Strategy.md`
- `docs/decisions/strategy-tree-persistence.md`
- `docs/decisions/market-data-result-identity-keying.md` (новый)
- `.claude/work/roadmap/phase-1.md`
- `.claude/work/progress/phase-1-step-2-gaps-close-3.md` (этот файл)
- `.claude/snapshots/snapshot-v25.md`
