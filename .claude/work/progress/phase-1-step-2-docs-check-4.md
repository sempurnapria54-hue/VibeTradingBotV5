# DOCS_CHECK_4 — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

На каком шаге мы в проверке целостности концепции доков под код
шага 2 (четвёртая итерация) и какие пробелы найдены.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия (абстракция: объявляет
  нужные индикаторы и условие сигнала; одна реализация)».
- Под-шаг: `DOCS_CHECK_4` (четвёртая итерация), после `GAPS_CLOSE_3`.
- Тулинг: роль `reviewer`, фокус `concept-review`. Граница охвата —
  **только доки**, код не читается.
- **Scope** (зафиксирован на `GAPS_CLOSE_1`, Э1): шаг 2 строит,
  **персистит** и читает полное монолитное immutable-дерево `Strategy`
  + материализует одну заполненную стратегию через Strategy API. Порог
  глубины применяется ко всему дереву (конструирование +
  персистентность + чтение).
- **Фокус итерации:** `GAPS_CLOSE_3` сделал **ревизию** персистентности
  (Н3): листовые настройки (`StrategyIndicatorSetting`,
  `StrategyMarketStructureSetting`) и их `params` переведены в **JSONB
  внутри контейнера** (были реляционными узлами по стойке `GAPS_CLOSE_2`);
  реляционными остались только контейнеры/каркасные узлы. Плюс выровнял
  Н1 (self-ссылка действия — `target_action_key` + self-FK
  `target_action_id` с CHECK), Н2 (убран `IndicatorParams.id`), пометил
  провизорной асимметрию `timeframe` (Н3/в) и завёл decision
  `market-data-result-identity-keying.md` (downstream-следствие ревизии).
  Четвёртая проверка верифицирует, что ревизия применена согласованно
  и не оставила новых doc↔doc хвостов в схеме персистентности дерева.

## Охват

### Проверено (в охвате)

- **Ядро:** `docs/models/domain/aggregate/Strategy.md` целиком, акцент на
  §Персистентность (интро, §Настройки рыночных данных, §Действия,
  §`stepsByStatus`, §Внутридеревные ссылки, §Не зафиксировано),
  §IndicatorParams, §Связь с DealActionState, §StrategyIndicatorSetting/
  §StrategyMarketPhaseSetting.
- **Decision'ы (на внутреннюю согласованность и согласованность с
  моделью):** `strategy-tree-persistence.md` (включая врезку «Ревизия
  `GAPS_CLOSE_3`» и §Следствия), новый `market-data-result-identity-keying.md`,
  ранее закрытые `strategy-condition-authoring-contract.md`,
  `strategy-signal-is-entry-condition.md`,
  `strategy-materialization-and-validation.md`.
- **Cross-ref:** `docs/lifecycles/Strategy.md` (материализация/статус — без
  изменений к ревизии), downstream-доки результатов расчёта
  (`IndicatorValue.md`, `MarketStructure.md`, `MarketPhase.md`,
  `IndicatorJob.md`, `MarketStructureJob.md`, `MarketPhaseJob.md`) —
  только на предмет dangling `*_setting_id` после ревизии Н3.
- **Open-questions:** проход по всем 15 на гейтинг шага 2.

### Вне охвата (downstream — ссылки name-level, не пробел)

Без изменений к `DOCS_CHECK_3`: команды/executors (шаг 4), риск (шаг 5),
FSM (шаг 6), runtime сделок/P&L (шаг 7), расчёт индикаторов/структуры
(шаги 3/Фаза 4), `DealActionState`/`RuntimeTarget`, evaluator. OKX-mapping
вне (Strategy не биржевая сущность). Реализация ключевания результатов
расчёта по идентичности (схема `UNIQUE`, канонизация `params`) — Фазы 3/4
(`market-data-result-identity-keying.md`), вне шага 2.

## Стадия остановки

**Прошёл все стадии (до стадии 2).** Стадия 0 (гейт scope) снята Э1.
Стадия 1 (процессы): отдельного процесса создания/валидации стратегии
нет; материализация через Strategy API лежит поверх описанного lifecycle
(`docs/lifecycles/Strategy.md`), новой механики ревизия не вносит —
чисто. Стадия 2 (компоненты + модели): ревизия Н3 применена согласованно
по обоим докам в части листовых настроек, но обнажила/оставила
несогласованности в смежных частях схемы дерева, которые шаг 2 пишет
(condition, strategy_action, MarketPhaseParams). Обход доведён до конца.

## Пробелы по типам

### 1. Несогласованности между доками

**Н1. Персистентность `StrategyCondition`/`StrategyConditionRule` не
описана; `strategy_condition_rule` упомянут как таблица, но в каркас
не входит.** §Персистентность интро (`Strategy.md`, `strategy-tree-persistence.md`
§Принятое решение) перечисляет реляционный каркас **замкнутым** списком:
`Strategy`, `StrategyMarketPhaseSetting`, `StrategyDetail`, `StrategyStep`,
`StrategyAction`. `StrategyCondition`/`StrategyConditionRule` в списке нет.
При этом §Внутридеревные ссылки (`Strategy.md` ~стр.446;
`strategy-tree-persistence.md` §Внутридеревные ссылки) пишет: «операнды —
JSONB **на строке `strategy_condition_rule`**» — т.е. трактует
`strategy_condition_rule` как реляционную таблицу. Отдельного раздела
персистентности условия (как у §Действия / §`stepsByStatus` / §Настройки)
нет. Шаг 2 строит и **персистит** условие каждого `StrategyStep`
(`condition: StrategyCondition` с `rules` + операндами) — без выбора
представления entity/миграцию не написать. Не определено: `strategy_condition`
1:1 на шаге + дочерние строки `strategy_condition_rule` (колонки `level`/
`rule_type`/`operator` + операнды JSONB), либо всё условие — JSONB на
`strategy_step`. Текущая формулировка совмещает оба прочтения. → эскалация Н1
(главная по эффекту на миграцию).

**Н2. `UNIQUE(strategy_detail_id, key)` на `strategy_action` ссылается на
колонку, которой нет в схеме таблицы; родительская FK действия не
зафиксирована.** §Связь с DealActionState (`Strategy.md` стр.382) и интро
`strategy-tree-persistence.md` (стр.21) объявляют инвариант
`UNIQUE(strategy_detail_id, key)`. Но §Действия перечисляет колонки базовой
таблицы `strategy_action` как `id, action_kind, key, action_type, level,
target_action_key, target_action_id` (`Strategy.md` стр.425;
`strategy-tree-persistence.md` стр.104) — без `strategy_detail_id` и без
родительской FK. По модели действие принадлежит `StrategyStep`
(`StrategyStep.actions`), а `strategy_step` хранится плоскими строками с
`strategy_detail_id`; уникальность `key` по правилу валидации №2 —
**в рамках `StrategyDetail`** (через несколько шагов). DB-`UNIQUE` через join
невозможен. Не определено: (а) родительская FK действия
(`strategy_step_id`); (б) как реализуется per-detail уникальность `key` —
денормализованная колонка `strategy_detail_id` на `strategy_action` +
DB-`UNIQUE`, либо проверка приложения (как уникальность `key` у
JSONB-настроек после ревизии Н3). → эскалация Н2 (на миграцию).

**Н3. `MarketPhaseParams` смешан с params листовых настроек, хотя
принадлежит реляционному контейнеру; место хранения размыто.** §Настройки
рыночных данных (persistence) в обоих доках пишет: «`params` настройки
(`IndicatorParams` + 7 наследников; `MarketStructureParams` /
**`MarketPhaseParams`**) едут внутри **того же JSON**» (`Strategy.md`
~стр.415-417; `strategy-tree-persistence.md` стр.59-61). Но
`MarketPhaseParams` — это `params` **контейнера** `StrategyMarketPhaseSetting`
(§StrategyMarketPhaseSetting, стр.88-91), который ревизия Н3 оставила
**реляционным** узлом каркаса; он не лежит внутри JSON-массивов
`indicatorSettings`/`marketStructureSettings`. Куда едет `MarketPhaseParams`
(скорее всего — JSONB-колонка `params` на строке
`strategy_market_phase_setting`) формулировкой «внутри того же JSON»
не задано — она склеивает контейнерный params с листовыми. Шаг 2 персистит
контейнер с его `MarketPhaseParams`. → эскалация Н3 (выравнивание; нижний
приоритет относительно Н1/Н2).

**Н4 (мелкая). `indicatorType` продублирован на `StrategyIndicatorSetting`
и на базе `IndicatorParams`.** §StrategyIndicatorSetting: `indicatorType:
IndicatorValue.Type` (стр.99-100). База §IndicatorParams: тоже
`indicatorType: IndicatorValue.Type` (стр.110). При JSONB-хранении params
дискриминатор подтипа в JSON нужен — `indicatorType` мог бы им служить; но
это дубль поля контейнерной настройки (риск рассинхрона «настройка говорит
EMA, params — RSI»). Тот же класс хвоста, что снятый `GAPS_CLOSE_3`
`IndicatorParams.id`. → эскалация Н4 (намеренный JSON-дискриминатор vs
рудимент; низкий приоритет).

### 2. Name-level без структуры (где структура нужна шагу 2)

Структурные дефициты, требующие схемы под шаг 2, сосредоточены **внутри
Н1 и Н2** (а не отдельным пунктом):

- **Схема персистентности условия** (Н1): таблицы/колонки
  `strategy_condition`/`strategy_condition_rule` либо JSONB-форма на шаге —
  не заданы.
- **Родительская привязка действия + реализация `UNIQUE(detail, key)`**
  (Н2): `strategy_step_id` и способ enforce'а уникальности — не заданы.

Прочая под-определённость (типы/nullability числовых полей дерева) явно
отложена к entity/Flyway (§Не зафиксировано) — это осознанный отложенный
порог, не пробел.

### 3. Неотвеченные вопросы (open-questions)

Проход по `open-questions.md` (15 открытых). По границе шага 2 **ни один не
гейтит** — без изменений к `DOCS_CHECK_3`:
- **STRAT-Q4** (percent-anchor) — явно непгейтящий бизнес-инкремент. Не
  эскалируем.
- DEAL-Q1/Q2/Q3, PROC-Q1, RISK-Q1, INSTR-Q1/Q2, ORCH-Q1, ENUM-Q1, CMD-Q1,
  OKX-Q1..Q4 — downstream. Не эскалируем.

Новых открытых вопросов `GAPS_CLOSE_3` не вносил (downstream-следствие
ревизии оформлено decision'ом `market-data-result-identity-keying.md`, не
открытым вопросом). Найденное (Н1-Н4) — локальные хвосты схемы дерева, в
`open-questions` не заводятся (закрываются правкой доков на `GAPS_CLOSE_4`).

## Наблюдение (downstream — не пробел шага 2)

Ревизия Н3 убрала реляционный `id` у листовых настроек, но downstream-доки
результатов расчёта по-прежнему несут ссылку на него:
`IndicatorValue.md` стр.60 (`UNIQUE(instrument_id,
strategy_indicator_setting_id, …)`), `MarketStructure.md` стр.71
(`…strategy_market_structure_setting_id…`), `IndicatorJob.md` стр.48,
`MarketStructureJob.md` стр.36 — и **без** форвард-метки на новый decision.
`MarketPhase.md` стр.46 / `MarketPhaseJob.md` стр.31
(`strategy_market_phase_setting_id`) — корректны (контейнер сохраняет `id`).
Это сознательно отложено `GAPS_CLOSE_3` к реализации Фаз 3/4 и зафиксировано
в `market-data-result-identity-keying.md` (§Границы и отложенное). **Шаг 2
не гейтит** (он эти модели не персистит). Отмечается как отслеживаемый
downstream-хвост; при желании на `GAPS_CLOSE_4` дёшево добавить в эти доки
форвард-метку на decision (гигиена), но это не требование шага 2.

## Блокирующие открытые вопросы

Из `open-questions.md` шаг 2 **не блокирует ни один**. Найденное (Н1-Н4) —
локальные хвосты ревизии/схемы дерева; закрываются правкой доков на
`GAPS_CLOSE_4`.

## Эскалации (решает пользователь на `GAPS_CLOSE_4`)

- **Н1 (главная). Персистентность условия.** Зафиксировать схему
  `StrategyCondition`/`StrategyConditionRule`: реляционные строки
  (`strategy_condition` 1:1 на шаге + дочерние `strategy_condition_rule`
  с `level`/`rule_type`/`operator` + операнды JSONB) — тогда внести
  `strategy_condition_rule`(/`strategy_condition`) в перечень каркасных
  узлов в обоих доках и добавить §персистентности условия; **либо** всё
  условие — JSONB на `strategy_step` — тогда снять формулировку «строка
  `strategy_condition_rule`». Рекомендация: реляционные строки правил
  (консистентно с тем, что условие — структурированная часть дерева с
  `level`-порядком, а каркас уже реляционный), операнды — JSONB на строке
  правила (как уже сказано).

- **Н2 (на миграцию). Привязка действия + `UNIQUE(detail, key)`.**
  Зафиксировать родительскую FK `strategy_action.strategy_step_id` и способ
  enforce'а per-detail уникальности `key`: денормализованная колонка
  `strategy_detail_id` на `strategy_action` + DB-`UNIQUE(strategy_detail_id,
  key)` (тогда внести её в перечень колонок базовой таблицы в обоих доках),
  **либо** проверка приложения (тогда снять нотацию `UNIQUE(...)`,
  оставить как app-инвариант — по образцу уникальности `key` JSONB-настроек).
  Рекомендация: денормализованный `strategy_detail_id` + DB-`UNIQUE`
  (immutable-запись делает денормализацию безопасной, FK→`strategy_step` —
  реальный родитель; целостность ссылки действие→действие уже на
  `target_action_id`).

- **Н3 (выравнивание). Хранение `MarketPhaseParams`.** Развести
  контейнерный params от листовых: `MarketPhaseParams` — JSONB-колонка
  `params` на строке реляционного `strategy_market_phase_setting`; из фразы
  «`params` настройки (… `MarketPhaseParams`) едут внутри того же JSON»
  убрать `MarketPhaseParams` (он не часть JSON-массивов листовых настроек).
  Почти зачистка формулировки.

- **Н4 (зачистка/уточнение). `indicatorType` на `IndicatorParams`.**
  Решить: оставить как намеренный JSON-дискриминатор подтипа (тогда явно
  пометить его ролью дискриминатора, чтобы дубль с
  `StrategyIndicatorSetting.indicatorType` был осознанным), либо убрать из
  базы `IndicatorParams` как рудимент (как сняли `id`). Низкий приоритет.

## Сводка

- **Несогласованности (doc↔doc):** 4 (Н1 — персистентность условия /
  `strategy_condition_rule` вне каркаса; Н2 — `UNIQUE(strategy_detail_id,
  key)` против схемы `strategy_action` + родительская FK; Н3 —
  `MarketPhaseParams` смешан с листовыми params; Н4 — дубль `indicatorType`).
- **Name-level без структуры:** структурные дефициты учтены внутри Н1
  (схема условия) и Н2 (родительская FK + enforce `UNIQUE`); отдельным
  пунктом не дублируются.
- **Открытые вопросы:** 0 гейтящих; STRAT-Q4 непгейтящий; 14 — downstream.
- **Downstream-наблюдение:** dangling `*_setting_id` в доках результатов
  расчёта — сознательно отложено к Фазам 3/4 (decision), шаг 2 не гейтит.
- **Эскалаций:** 4 (Н1 — на миграцию, главная; Н2 — на миграцию; Н3 —
  выравнивание; Н4 — зачистка, низкий приоритет).
- **Итог: не чисто.** Ревизия Н3 (`GAPS_CLOSE_3`) применена согласованно в
  части листовых настроек, но обнажила/оставила несогласованности в смежных
  частях схемы дерева, которые шаг 2 персистит: персистентность условия
  (Н1) и привязка/уникальность действия (Н2) — обе на миграцию; плюс
  выравнивание `MarketPhaseParams` (Н3) и дубль `indicatorType` (Н4). Нужен
  **`GAPS_CLOSE_4`** (Н1 → Н2 → Н3 → Н4), затем `DOCS_CHECK_5`.

## Размещение знания — не здесь

`concept-review` помечает пробелы, но не решает, где их закрывать. Это
`GAPS_CLOSE_4`: Н1 — после решения о представлении условия, либо §персистентности
условия + дополнение перечня каркасных узлов в `Strategy.md` §Персистентность
и `strategy-tree-persistence.md`, либо снятие упоминания «строка
`strategy_condition_rule`»; Н2 — выравнивание §Действия / §Связь с
DealActionState (колонки `strategy_action` + формулировка `UNIQUE`) в обоих
доках; Н3/Н4 — точечная правка §Настройки рыночных данных / §IndicatorParams.
</content>
</invoke>
