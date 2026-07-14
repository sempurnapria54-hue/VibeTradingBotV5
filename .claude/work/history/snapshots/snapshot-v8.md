# Snapshot v8

**Дата:** 2026-05-26.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез на дату).

## Состояние

С момента `snapshot-v7.md` (2026-05-26) проведена третья обкатка
скилла классификации — прогон восьми архивных процессных доков
(`.claude-archive/2026-05-21/docs/domain/processes/`) обновлённым
каркасом. Обкатка не закрыта: главный новый скрип NQ-F разобран и
закрыт двумя решениями; точечные NQ-G и NQ-H открыты, их разбор
перенесён в новый чат.

## Что изменилось относительно v7

**Решения (новые):**
- `.claude/decisions/runtime-value-object.md` — новый продуктовый
  тип Runtime value object (RVO) и каталог `docs/components/models/`
  под не-persisted носители данных без identity и lifecycle
  (`CalculationContext`, `DealContext`, `MarketPriceData`,
  `Calculated*`, `RiskValidationResult`). Закрыл NQ-F (часть 1).
- `.claude/decisions/models-core-vs-other.md` — разделение
  `docs/models/` на `core/` (торговые модели) и `other/` (прочие
  хранимые: свечи, индикаторы, аудит). Закрыл NQ-F (часть 2).

**Структура (`structure.md`):**
- Строка `docs/models/` заменена на `docs/models/core/` («что это
  за торговая модель?») и `docs/models/other/` («что это за
  модель?»).
- Добавлена строка `docs/components/models/` («что это за
  runtime-объект?», PascalCase).

**Скилл (`classify-type.md`):**
- В перечне продуктовых типов «доменная модель» заменена на
  «торговую модель» (`docs/models/core/`) и «прочую модель»
  (`docs/models/other/`); добавлен тип «Runtime value object»
  (`docs/components/models/`).
- В признаках различения: «компонент vs торговая/прочая модель»
  переформулирован; добавлен раздел «RVO vs торговая/прочая /
  биржевая модель / компонент»; в гранулярность добавлена ось
  «торговая (core) vs прочая (other)» — со ссылками на новые
  decisions.

**Открытые вопросы:**
- NQ-F закрыт (`runtime-value-object.md` + `models-core-vs-other.md`).
- В `open-questions.md` добавлены NQ-G (master-index /
  навигационные доки vs one-owner-принцип) и NQ-H (FSM-handler —
  компонент или раздел lifecycle). Открыты, разбор в новом чате.

**Backlog:**
- В список критериев миграции добавлены ссылки на
  `runtime-value-object.md` и `models-core-vs-other.md`. Путь
  миграции не изменился.

## Текущая структура

См. `.claude/rules/structure.md`.

**Продуктовые каталоги:**
- `docs/models/` — теперь зонтик с подкаталогами `core/` и `other/`
  (оба — заготовки с `.gitkeep`).
- `docs/components/models/` — заготовка с `.gitkeep` под RVO.
- Остальные `docs/*` — по-прежнему заготовки; реальная миграция —
  предстоящая задача из backlog.

## Активные задачи

- Третья обкатка классификации остаётся в
  `.claude/work/progress/обкатка-классификации-процессы.md` (не
  закрыта: NQ-G и NQ-H открыты). Сырой материал прогона —
  `.claude/notes/2026-05-26-обкатка-классификации-процессы.md`.

## Открытые общие вопросы

- **NQ-G** — допустимы ли master-index / навигационные доки (vs
  one-owner-принцип `rule-source-of-truth.md`).
- **NQ-H** — где граница между FSM-handler'ом как компонентом и
  как разделом lifecycle Deal (уточняет NQ-D).
- Q1-Q4 и NQ-A…NQ-F закрыты; история закрытия — в соответствующих
  decisions.

## Что в работе

- Разбор NQ-G и NQ-H перенесён в новый чат (третья обкатка не
  закрывается).
- Предстоит миграция архивных торговых сущностей в `docs/` (в
  backlog, запуск в новом чате) — теперь с применением критериев
  RVO и core/other.
- Каркас классификации после третьей обкатки: `docs/components/`,
  `model-granularity`, `rule-source-of-truth`, `client-layer-docs`
  держатся на процессном материале; добавлены RVO и core/other;
  скиллы `classify-area` и `classify-theme` — по-прежнему каркасы
  без содержания.
