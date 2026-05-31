# DOCS_CHECK_2 — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

На каком шаге мы в проверке целостности концепции доков под код
шага 2 (вторая итерация) и какие пробелы найдены.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия (абстракция: объявляет
  нужные индикаторы и условие сигнала; одна реализация)».
- Под-шаг: `DOCS_CHECK_2` (вторая итерация), после `GAPS_CLOSE_1`.
- Тулинг: роль `reviewer`, фокус `concept-review`.
- Граница охвата — **только доки**, код не читается.
- **Scope зафиксирован на `GAPS_CLOSE_1` (Э1, вариант 1):** шаг 2 —
  **полное монолитное дерево `Strategy`** как описано в доках; «одна
  реализация» = одна полностью заполненная реальная стратегия сквозь
  всё дерево. Связанность с `MarketPhase` / `Deal.Status` /
  `StrategyAction` принята как scope шага 2 (не пробел). Поэтому
  функциональный порог теперь применяется ко **всему дереву**:
  конструирование + персистентность + чтение одной заполненной
  стратегии.

## Охват

### Проверено (в охвате)

- **Модель (ядро шага):** `docs/models/domain/aggregate/Strategy.md`
  — весь tree: root, market-data settings (`StrategyMarketPhaseSetting`/
  `MarketPhaseParams`/`StrategyIndicatorSetting`/`IndicatorParams`+7
  наследников/`StrategyMarketStructureSetting`/`MarketStructureParams`),
  `StrategyDetail`, `StrategyStep` (+ `StrategyMarketDataExpiredSetting`),
  условия (`StrategyCondition`/`StrategyConditionRule`/
  `StrategyConditionOperand` + енумы), действия (`StrategyAction` +
  Order/AlgoOrder/Position actions, placement, protection, SL/trailing),
  12-пунктная валидация, §TimeFrame (теперь ссылка — TIME-Q1 закрыт).
- **Lifecycle:** `docs/lifecycles/Strategy.md` (admin-статусы — целостны).
- **Целевые модели объявлений (в охвате как типы полей дерева):**
  `IndicatorValue.md` (`Type`), `MarketPhase.md` (`Type`),
  `MarketStructure.md` (`Type` + `MarketPriceLevel.Type`).
- **Cross-ref проверка enum'ов, на которые ссылается дерево**
  (downstream-модели, прочитаны точечно — что ссылка разрешается):
  `Order.md` (`Order.Type`, `AttachedAlgoOrder.Type`), `AlgoOrder.md`
  (`ConditionType`, `TriggerPriceType`), `Deal.md` (`Status`,
  `EntryStepType`, `EntryReason`, `CloseReason`, `ShutdownReason`,
  `direction: StrategyTradeDirection`).
- **Границы (из DOCS_CHECK_1):** `StrategyConditionEvaluator.md`,
  `EntryScannerJob.md`, процессы `market-data-calculation.md`,
  `strategy-action-calculation.md`.
- **Словарь:** `docs/dictionary/` — пуст.

### Вне охвата (downstream — ссылки name-level, не пробел)

- Генерация / жизненный цикл команд (шаг 4): `ServiceCommand*`,
  `*Executor*`, `ServiceCommandFactory`.
- Риск (шаг 5), FSM (шаг 6), runtime сделок/P&L (шаг 7), расчёт
  индикаторов (шаг 3), расчёт фазы/структуры (Фаза 4:
  `IndicatorJob`/`MarketStructureJob`/`MarketPhaseJob`).
- **Связанные runtime-модели** `DealActionState`/`RuntimeTarget`
  (связь `StrategyAction → runtime entity`) — downstream.

Примечание: все enum'ы, на которые ссылается дерево
(`Order.Type`, `AttachedAlgoOrder.Type`, `ConditionType`,
`TriggerPriceType`, `Deal.Status`/`EntryStepType`/`CloseReason`/
`ShutdownReason`, `MarketPhase.Type`, `MarketStructure.Type`,
`MarketPriceLevel.Type`), **определены** в своих downstream-моделях;
ссылки разрешаются согласованно — пробелов нет.

## Стадия остановки

**Прошёл все стадии (до стадии 2).** Стадия 0 (гейт Э1) снята
решением `GAPS_CLOSE_1`. Стадия 1 (процессы): отдельного процесса
создания/валидации стратегии нет, но для конструирования/
персистентности/чтения самой модели процессный слой не гейтит —
чисто. Стадия 2 (компоненты + модели): найдены пробелы структуры
(см. ниже), обход доведён до конца.

## Пробелы по типам

### 1. Несогласованности между доками

**Не выявлено.** Перекрёстные ссылки дерева на downstream-модели
проверены и согласованы: `StrategyOrderAction.orderType` ↔
`Order.Type` (ENTRY/ENTRY_ATTACHED_STOP_LOSS); `attachedType` ↔
`AttachedAlgoOrder.Type` (ATTACHED_STOP_LOSS);
`StrategyAlgoOrderAction.conditionType` ↔ `AlgoOrder.ConditionType`
(включая OCO_FULL); `triggerPriceType` ↔ `AlgoOrder.TriggerPriceType`
(LAST/INDEX/MARK; отделён от `StrategyPriceSource` намеренно);
`stepsByStatus` ключи ↔ `Deal.Status`; `StrategyStepType`
ENTRY/GRID_ENTRY ↔ `Deal.EntryStepType`; PRECHECK-claim ↔
`Deal.CloseReason.ENTRY_CONDITION_EXPIRED`; `MarketPhase.Type`
матрица `phaseEntryPolicy` ↔ `MarketPhase.md`; `StrategyPriceBaseType`
⊇ `MarketPriceLevel.Type`. Противоречий нет.

### 2. Name-level без структуры (где структура нужна шагу 2)

**N1. У `Strategy` нет спецификации персистентности — при том, что
шаг 2 её персистит (Э4).** По Э1 шаг 2 строит и **персистит** полное
immutable-дерево + сидит одну заполненную стратегию. Функциональный
порог для persisted-сущности требует от доков схему хранения (типы,
nullability, ограничения, индексы — то, что попадёт в миграцию). У
всех доменных моделей шага 1 есть раздел «Персистентность»; у
`Strategy.md` его **нет вовсе**, при этом косвенные признаки
представления **противоречат друг другу**:
- *реляционные:* `id: Long` на root/detail/step/`IndicatorParams`;
  `UNIQUE(strategy_detail_id, key)`; форвард-заметка про загрузку
  через `@EntityGraph`/`JOIN FETCH` (tasks-strategy §36);
- *документные (JSON):* `actionKind` — JSON-дискриминатор, `key`
  «задаётся в JSON», `Strategy API examples.md`, immutable-params
  («`version`/`canonicalJson` не нужны»).
Не задано: стратегия наследования для абстрактных иерархий
(`IndicatorParams` — 7 наследников; `StrategyAction` — интерфейс, 3
подтипа); как персистится `stepsByStatus: Map<Deal.Status,
List<StrategyStep>>`; как хранятся/резолвятся внутридеревные
объектные ссылки (`StrategyConditionRule.indicatorSetting`/
`marketStructureSetting`; `targetActionKey` → внутренняя ссылка).
Сопутствующее: типы числовых полей дерева (Integer vs BigDecimal для
`*Bars`/`*Period` vs `*Percents`/`*Score`/`*Ratio`/`*Multiplier`) и
nullability по таблицам не проставлены явно (выводимы из нейминга +
codestyle, но не зафиксированы). → эскалация Э4.

**N2. Под-определена грамматика условия сигнала (Э2, переподнято).**
`Strategy.md` («Условия») перечисляет поля `StrategyConditionRule` и
значения енумов, но не задаёт **семантику сборки**: какие поля
применимы к какому `ruleType` и как складываются в конкретное
сравнение. Видна избыточность представления (`sourceType` — и на
правиле, и на операнде; `leftOperand: String` vs структурированный
`rightOperand: StrategyConditionOperand`; объектные ссылки
`indicatorSetting`/`marketStructureSetting` дублируют то, что мог бы
нести операнд; `StrategyConditionOperand.valueType` пересекается с
`sourceType`). Чтобы сконструировать, персистить и прочитать одно
конкретное рабочее условие входа (напр. «RSI(14) `CROSSED_ABOVE` 30
на `ONE_HOUR`»), контракт авторинга должен быть определён — или явно
отнесён к деталям `CODE`/evaluator. → эскалация Э2.

### 3. Неотвеченные вопросы (open-questions)

Проход по `open-questions.md` (14 открытых после закрытия TIME-Q1).
По границе шага 2 **ни один не гейтит** (RISK-Q1, DEAL-Q1/Q2/Q3,
PROC-Q1, ENUM-Q1, CMD-Q1, INSTR-Q1/Q2, ORCH-Q1, OKX-Q1..Q4 —
downstream). TIME-Q1 закрыт на `GAPS_CLOSE_1`. Новые незакрытые
аспекты — терминология «сигнала» (Э3) и scope валидатора/создания
(Э5) — ещё не оформлены как open-questions.

## Блокирующие открытые вопросы

Из `open-questions.md` шаг 2 **не блокирует ни один**. Найденные
пробелы (Э2, Э4) и уточнения (Э3, Э5) — **новые**, в `open-questions`
пока не заведены; заводятся/решаются на `GAPS_CLOSE_2`.

## Эскалации (решает пользователь на `GAPS_CLOSE_2`)

- **Э4 (структура/глубина — главное). Персистентность `Strategy`.**
  Задать схему хранения дерева в доках (как у моделей шага 1) или
  принять явное решение о представлении. Развилка: (а) нормализованное
  реляционное дерево (таблицы detail/step/action/setting с id и FK,
  inheritance-стратегия для `IndicatorParams`/`StrategyAction`); (б)
  JSON-документ с промотированными/индексируемыми колонками
  (`instrumentId`, `status`, `internalId`) — тогда зафиксировать это и
  снять реляционные сигналы; (в) гибрид. Заодно — типы/nullability
  числовых полей дерева. Без решения нельзя написать entity + Flyway-
  миграцию заполненной стратегии.

- **Э2 (глубина грамматики условия — переподнято с DOCS_CHECK_1).**
  Достаточно ли перечня полей `StrategyConditionRule`, или доки должны
  задать семантику сборки (поля↔`ruleType`; роль `leftOperand: String`
  vs объектных ссылок vs операнда; разнесение `sourceType`/`valueType`)
  — без чего нельзя авторизовать одно конкретное рабочее условие. Либо
  подтвердить, что сборка — деталь `CODE`/evaluator (шаг 3+), а доки
  фиксируют только структуру данных.

- **Э3 (терминология «сигнала» — переподнято с DOCS_CHECK_1).**
  «Условие сигнала» = входной `StrategyCondition` ENTRY/`GRID_ENTRY`-
  step, или `StrategyConditionSourceType.SIGNAL` /
  `StrategyConditionRuleType.SIGNAL_SCORE_REACHED`. Во втором случае в
  доках нет производителя «сигнала / signal-score» — name-level без
  источника. Зафиксировать значение термина.

- **Э5 (scope — новое). Создание и валидация одной реализации.**
  «Одна реализация» материализуется как: API создания стратегии
  (кластер `Strategy API` — форвард-заметка STR-FW9), сид/миграция,
  или тестовая фикстура? И входит ли в шаг 2 компонент-валидатор
  (12-пунктная валидация key/targetActionKey/CLOSE_FULL/partial-exit;
  правила в `Strategy.md` есть, сам валидатор — форвард-заметка
  STR-FW8 / backlog п.8), или валидация откладывается. Нижний
  приоритет относительно Э4/Э2.

## Сводка

- **Несогласованности (doc↔doc):** 0 (перекрёстные ссылки дерева
  проверены — согласованы).
- **Name-level без структуры:** 2 (N1 — персистентность дерева / Э4;
  N2 — семантика сборки условия / Э2).
- **Открытые вопросы:** 0 гейтящих; TIME-Q1 закрыт; 14 — downstream.
- **Эскалаций:** 4 (Э4 персистентность — главная; Э2 грамматика
  условия; Э3 терминология «сигнала»; Э5 создание/валидация — нижний
  приоритет).
- **Итог: не чисто.** Стадия 0 снята (Э1 решён), но на стадии 2
  выявлены пробелы структуры. Нужен `GAPS_CLOSE_2` (Э4 → Э2 → Э3 →
  Э5), затем `DOCS_CHECK_3`.

## Размещение знания — не здесь

`concept-review` помечает пробелы, но не решает, где и как их
закрывать. Это `GAPS_CLOSE_2` (разбор Э2-Э5 в чате → штатный поток
`recognize-knowledge` → классификаторы → `place-knowledge`; для
персистентности — раздел «Персистентность» в `Strategy.md` по
образцу моделей шага 1, после решения о представлении).
