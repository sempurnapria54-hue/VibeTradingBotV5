# DOCS_CHECK_3 — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

На каком шаге мы в проверке целостности концепции доков под код
шага 2 (третья итерация) и какие пробелы найдены.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия (абстракция: объявляет
  нужные индикаторы и условие сигнала; одна реализация)».
- Под-шаг: `DOCS_CHECK_3` (третья итерация), после `GAPS_CLOSE_2`.
- Тулинг: роль `reviewer`, фокус `concept-review`. Граница охвата —
  **только доки**, код не читается.
- **Scope** (зафиксирован на `GAPS_CLOSE_1`, Э1): шаг 2 строит,
  **персистит** и читает полное монолитное immutable-дерево `Strategy`
  + материализует одну заполненную стратегию через Strategy API. Порог
  глубины применяется ко всему дереву (конструирование +
  персистентность + чтение).
- **Фокус итерации:** `GAPS_CLOSE_2` переписал в `Strategy.md` §Условия,
  §StrategyIndicatorSetting, §IndicatorParams, §key/валидация,
  §Персистентность и завёл три decision'а
  (`strategy-condition-authoring-contract`,
  `strategy-signal-is-entry-condition`,
  `strategy-materialization-and-validation`) + уточнил
  `strategy-tree-persistence`. Третья проверка верифицирует, что эти
  правки закрыли Э2/Э3/Э4/Э5 целостно и не внесли новых doc↔doc
  расхождений.

## Охват

### Проверено (в охвате)

- **Ядро:** `docs/models/domain/aggregate/Strategy.md` целиком, с
  акцентом на переписанные `GAPS_CLOSE_2` разделы (инвариант ключей,
  §StrategyIndicatorSetting/§IndicatorParams, §Условия
  (правило/операнд/enum'ы), §key/валидация (линия create/activate),
  §Персистентность).
- **Четыре decision'а** (на внутреннюю согласованность и согласованность
  с моделью): `strategy-condition-authoring-contract.md`,
  `strategy-signal-is-entry-condition.md`,
  `strategy-materialization-and-validation.md`,
  `strategy-tree-persistence.md`.
- **Cross-ref после правок:** `docs/lifecycles/Strategy.md` (под decision
  о материализации — `POST`/`GET`/`PUT`, «одна `ACTIVE` на инструмент»),
  `docs/processes/candle-loading.md` §«Глубина под прогрев»,
  `docs/components/IndicatorJob.md` §Warmup (потребитель warmup).
- **Open-questions:** проход по всем 15 на гейтинг шага 2.

### Вне охвата (downstream — ссылки name-level, не пробел)

Без изменений к `DOCS_CHECK_2`: команды/executors (шаг 4), риск
(шаг 5), FSM (шаг 6), runtime сделок/P&L (шаг 7), расчёт индикаторов
(шаг 3), расчёт фазы/структуры (Фаза 4), `DealActionState`/
`RuntimeTarget`, OKX-mapping (`Strategy` не биржевая сущность). Все
enum'ы дерева, ссылающиеся в downstream-модели (`Order.Type`,
`AttachedAlgoOrder.Type`, `ConditionType`, `TriggerPriceType`,
`Deal.Status`/`EntryStepType`/`CloseReason`, `MarketPhase.Type`,
`MarketStructure.Type`, `MarketPriceLevel.Type`), повторно подтверждены
согласованными на `DOCS_CHECK_2`; `GAPS_CLOSE_2` их не трогал.

## Стадия остановки

**Прошёл все стадии (до стадии 2).** Стадия 0 (гейт scope) снята Э1.
Стадия 1 (процессы): отдельного процесса создания/валидации стратегии
нет; для конструирования/персистентности/чтения модели процессный
слой не гейтит — материализация через Strategy API лежит поверх уже
описанного lifecycle (`docs/lifecycles/Strategy.md`), не новая
механика — чисто. Стадия 2 (компоненты + модели): выявлены локальные
несогласованности и под-определённость схемы (ниже), обход доведён до
конца.

## Пробелы по типам

### 1. Несогласованности между доками

**Н1. Стораж self-ссылки действия назван двояко: `target_action_key`
(колонка базовой таблицы) vs `target_action_id` (self-FK).**
В одном и том же разделе персистентности базовая таблица описана с
колонкой `target_action_key`, а двумя абзацами ниже сказано, что
`targetActionKey` при сохранении **резолвится в self-FK
`target_action_id → strategy_action.id`**. Расхождение продублировано в
обоих доках синхронно:
- `Strategy.md` §Персистентность/§Действия: `strategy_action (… ,
  target_action_key)`; §Внутридеревные ссылки: «резолвится в self-FK
  `target_action_id`».
- `strategy-tree-persistence.md` §Действия: то же `target_action_key` в
  базовой таблице; §Внутридеревные ссылки: «резолвится в self-FK
  `target_action_id`».
Хранимая форма ссылки неоднозначна (строковый ключ vs числовой self-FK
vs оба). Шаг 2 пишет entity + Flyway-миграцию `strategy_action` —
колонку нельзя задать без выбора. → эскалация Н1.

**Н2. `IndicatorParams.id` — рудимент при JSONB-хранении.**
Модель (`Strategy.md` §IndicatorParams) перечисляет в базе абстрактного
`IndicatorParams` поле `id`. Но §Персистентность и
`strategy-tree-persistence.md` §«Индикаторные params» постановляют, что
`params` хранится **JSONB-полем на строке настройки, без отдельной
таблицы params и без inheritance-маппинга** — у JSONB-value-объекта
строкового `id` нет. Поле `id` в базе `IndicatorParams` — leftover от
архивной схемы «отдельная сущность с наследованием», от которой
сознательно отказались. Затрагивает форму value-класса и JSONB-структуру
(иначе в JSON сериализуется бессмысленный `id`). → эскалация Н2
(практически зачистка).

### 2. Name-level без структуры (где структура нужна шагу 2)

**Н3. Под-определена реляционная схема под-узлов settings (хотя шаг 2
персистит дерево целиком).** §Персистентность адресно закрыла
структурно-сложные места (наследование `StrategyAction` — `JOINED`;
`IndicatorParams` — JSONB; `stepsByStatus` — плоские строки; операнды —
JSONB; типы/nullability чисел явно отложены к entity/Flyway). Но для
строк-настроек остаётся не зафиксированным то, что общий принцип
«каждый узел — таблица с `id`, связи через FK» **не доопределяет**:

- **Двойное родительство** `StrategyIndicatorSetting` и
  `StrategyMarketStructureSetting`: обе живут и внутри
  `StrategyMarketPhaseSetting` (для фазы), и внутри `StrategyDetail`
  (после выбора детали). Как моделируется владение — две nullable-FK
  (`market_phase_setting_id` / `strategy_detail_id`), отдельные таблицы
  на контекст, или полиморфный родитель — не сказано; это структурное
  решение, попадающее в миграцию.
- **`MarketStructureParams` / `MarketPhaseParams`** — JSONB (по аналогии
  с `IndicatorParams`) или колонками? Для `IndicatorParams` JSONB задан
  явно; для этих двух — только общий «часть листовых настроек — JSONB»,
  без явного отнесения.
- **Асимметрия `timeframe`:** у индикатора `timeframe` ушёл в `params`
  (STRAT-Q1), у `StrategyMarketStructureSetting` `timeframe` остался
  прямым полем. Намеренно ли (у структуры нет warmup) или хвост
  переписывания — не зафиксировано.

Часть этого выводима из общего каркаса + отложенных типов, но
двойное родительство settings — реальная схемная развилка, которую
каркас не снимает. → эскалация Н3 (глубина: достаточно ли общего каркаса
или зафиксировать схему settings-узлов сейчас).

### 3. Неотвеченные вопросы (open-questions)

Проход по `open-questions.md` (15 открытых). По границе шага 2 **ни один
не гейтит**:
- **STRAT-Q4** (percent-anchor) — заведён на `GAPS_CLOSE_2`, явно
  непгейтящий: контракт авторинга условия закрыт, якорь процента —
  бизнес-инкремент (`strategy-condition-authoring-contract.md` §«Что
  осталось открытым»). Не эскалируем.
- DEAL-Q1/Q2/Q3, PROC-Q1, RISK-Q1, INSTR-Q1/Q2, ORCH-Q1, ENUM-Q1,
  CMD-Q1, OKX-Q1..Q4 — downstream. Не эскалируем.

STRAT-Q1/Q2/Q3 закрыты тремя decision'ами; орфаны-enum'ы (`SIGNAL`,
`SIGNAL_SCORE_REACHED`) убраны и в `Strategy.md`, и в decision —
согласованно. Нейминг поля-ссылки операнда (`indicatorKey`/`structureKey`
vs generic `key`) и консолидация литерала `CONSTANT` — **явно**
объявлены инкрементальной деталью и не блокируют шаг 2 (ссылки внутри
JSONB-листьев — мягкие, на схему миграции не влияют).

## Блокирующие открытые вопросы

Из `open-questions.md` шаг 2 **не блокирует ни один**. Найденное
(Н1-Н3) — новые локальные хвосты переписывания `GAPS_CLOSE_2`, в
`open-questions` не заводятся (закрываются правкой доков на
`GAPS_CLOSE_3`).

## Эскалации (решает пользователь на `GAPS_CLOSE_3`)

- **Н1 (несогласованность — главная по эффекту на миграцию). Хранимая
  форма self-ссылки действия.** Базовая таблица `strategy_action`
  хранит `target_action_id` (self-FK, `targetActionKey` — только форма
  ввода, резолвится при сохранении) — и тогда убрать `target_action_key`
  из списка колонок базовой таблицы в обоих доках; либо хранится именно
  строковый `target_action_key` — и тогда снять формулировку «резолвится
  в self-FK». Рекомендация: self-FK `target_action_id`, ключ — только
  вход (консистентно с «резолвится во внутреннюю ссылку» в §валидации).

- **Н2 (зачистка). `IndicatorParams.id`.** Убрать `id` из базы
  `IndicatorParams` (JSONB-value-объект без строки/таблицы). Почти не
  требует обсуждения — выровнять модель под уже принятое JSONB-решение.

- **Н3 (глубина схемы settings).** Достаточно ли общего каркаса
  «узел = таблица с `id` + FK» + отложенных типов, или доки должны
  зафиксировать сейчас: (а) как моделируется двойное родительство
  `StrategyIndicatorSetting`/`StrategyMarketStructureSetting`
  (market-phase setting vs detail); (б) JSONB-vs-колонки для
  `MarketStructureParams`/`MarketPhaseParams`; (в) намеренность
  асимметрии `timeframe` (индикатор → `params`, структура → прямое
  поле). Нижний приоритет относительно Н1/Н2.

## Сводка

- **Несогласованности (doc↔doc):** 2 (Н1 — `target_action_key` vs
  self-FK `target_action_id`; Н2 — рудиментарный `IndicatorParams.id`).
- **Name-level без структуры:** 1 (Н3 — схема settings-узлов: двойное
  родительство, JSONB-vs-колонки структуры/фазы params, асимметрия
  `timeframe`).
- **Открытые вопросы:** 0 гейтящих; STRAT-Q4 непгейтящий; 14 —
  downstream.
- **Эскалаций:** 3 (Н1 — на миграцию; Н2 — зачистка; Н3 — глубина схемы
  settings, нижний приоритет).
- **Итог: почти чисто.** Концепция шага 2 после `GAPS_CLOSE_2`
  существенно закрыта (Э2/Э3/Э4/Э5 сняты согласованно); остались три
  локальных хвоста переписывания — два цвета «зачистка/выравнивание»
  (Н1/Н2) и одна развилка глубины схемы (Н3). Нужен **лёгкий
  `GAPS_CLOSE_3`** (Н1 → Н2 → Н3), затем `DOCS_CHECK_4`.

## Размещение знания — не здесь

`concept-review` помечает пробелы, но не решает, где их закрывать.
Это `GAPS_CLOSE_3`: Н1/Н2 — выравнивание §Персистентность/§IndicatorParams
в `Strategy.md` + `strategy-tree-persistence.md`; Н3 — после решения о
глубине, либо фиксация схемы settings-узлов в §Персистентность, либо
явная отметка «выводится из общего каркаса / типы — на entity/Flyway».
