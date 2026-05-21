# ADR-0007: Структура `docs/spec/`

**Статус:** Proposed
**Дата:** 2026-05-15
**Контекст:** Стратегия миграции `docs/domain/` в `docs/spec/`. Структурное решение по расположению документов в `docs/spec/` и связанных папках.
**Связанные ADR:** ADR-0002 (стандарт документа `docs/spec/`), ADR-0006 (принципы спецификации), ADR-0008 (журнал open-questions), ADR-0009 (стратегия миграции).

## Context

ADR-0002 ввёл шесть жанров spec-документов (model, lifecycle, process, integration mapping, reference, invariant), задал формальный стандарт документа и шаблоны. Но не описал внутреннюю структуру каталогов в `docs/spec/`.

ADR-0006 принял четыре принципа организации содержания:

- Один бизнес-объект — один model-документ; вложенные модели — разделами внутри.
- Деление model на core (persistent, имеют identity, переживают рестарт) и runtime (транзитные).
- Локальные инварианты — в model/lifecycle/process документах; кросс-модельные — в отдельных документах жанра invariant.
- Двухуровневый резолвер биржевых статусов: общий контракт в invariant, конкретные таблицы в integration.

Применение этих принципов требует структурного решения: **где именно** в файловой системе живут разные подвиды документов. ADR-0007 фиксирует структуру `docs/spec/` на момент завершения фазы 1 миграции, плюс структуру `docs/spec/integrations/` (применяется в фазе 2), плюс расположение глоссария и сосуществование с `docs/api/`.

ADR-0007 не описывает **что должно быть** в каждом документе содержательно (это ADR-0006). ADR-0007 описывает **где документ лежит** и какая папочная структура.

## Decision

Четыре подраздела.

### 1. Структура `docs/spec/models/`: деление на `core/` и `runtime/`

Жанр model по ADR-0002 имеет внутреннее деление на два подкаталога:

- `docs/spec/models/core/` — модели, описывающие персистентные сущности системы.
- `docs/spec/models/runtime/` — модели, описывающие транзитные runtime-объекты.

Применение критерия core/runtime — по ADR-0006.

**Состав `docs/spec/models/core/` после фазы 1 (13 документов):**

- `AnomalyReport.md` — aggregate root, есть таблица `anomaly_reports`, FSM. Уже существует в `docs/spec/`, требует приведения к стандарту.
- `Deal.md` — aggregate root, есть таблица `deals`, явный FSM.
- `Order.md` — aggregate root, есть таблица `orders`. Включает `AttachedAlgoOrder` как вложенную модель.
- `AlgoOrder.md` — aggregate root, есть таблица `algo_orders`. Включает `Condition`, `Trigger`, `Trailing`, `TriggerPrice` как вложенные.
- `Position.md` — aggregate root, есть таблица `positions`.
- `Balance.md` — aggregate root, есть таблица `balance_containers` + `balances`. Включает `Balance` как вложенную.
- `Strategy.md` — aggregate root, есть таблица `strategies`. Включает `StrategyDetail`, `StrategyAction` и подтипы, `StrategyCondition`, настройки protection и market-phase, и др. как вложенные.
- `Exchange.md` — aggregate root, есть таблица `exchanges`.
- `Instrument.md` — aggregate root, есть таблица `instruments`. Включает `CandleGroup` и `Candle` как вложенные.
- `IndicatorValue.md` — результат IndicatorJob, persistent.
- `MarketStructure.md` — результат MarketStructureJob, persistent.
- `MarketPhase.md` — результат MarketPhaseJob, persistent.
- `MarketPriceData.md` — текущая рыночная цена/уровень, persistent.

**Состав `docs/spec/models/runtime/` после фазы 1 (3 документа):**

- `DealContext.md` — runtime-сборка для одного цикла FSM сделки. Не persistent, транзитная, нет identity. Используется всеми StateHandler'ами и сервисами FSM.
- `DealActionState.md` — runtime state одного действия стратегии внутри цикла FSM. Не persistent на момент проектирования (по доке — целевое состояние).
- `ServiceCommand.md` — runtime-обёртка для payload, передаваемая в executor. Включает `ServiceCommandPayload` и каталог 9 конкретных payloads. Не persistent.

Возможно появление дополнительных runtime-документов по ходу миграции (например, `CalculationContext.md`) — решается при миграции соответствующего кластера, по объёму описания.

**Структура каталога:**
docs/spec/models/
├── core/
│   ├── AnomalyReport.md
│   ├── Deal.md
│   ├── Order.md
│   ├── AlgoOrder.md
│   ├── Position.md
│   ├── Balance.md
│   ├── Strategy.md
│   ├── Exchange.md
│   ├── Instrument.md
│   ├── IndicatorValue.md
│   ├── MarketStructure.md
│   ├── MarketPhase.md
│   └── MarketPriceData.md
└── runtime/
├── DealContext.md
├── DealActionState.md
└── ServiceCommand.md
### 2. Структура `docs/spec/integrations/<exchange>/`

Жанр integration по ADR-0002 имеет внутреннее деление на два подкаталога:
docs/spec/integrations/<exchange>/
├── models/<name>.md       # описание внешних DTO биржи
└── mapping/<name>.md      # маппинг DTO ↔ домен, включая resolver-таблицы статусов
- `models/` — описание формата DTO биржи (поля REST/WS-ответов, типы, обязательности). Это **не описание доменных моделей** — это описание формата внешней системы.
- `mapping/` — маппинг внешних DTO в доменные модели. Включает: соответствие полей DTO ↔ полей доменной модели, таблицы резолва статусов (см. ADR-0006, принцип 3), локальные отклонения от общих правил резолюции.

Структура зафиксирована **заранее**, до её применения. Применяется в фазе 2 миграции (`docs/domain/models/mapping/okx/` → `docs/spec/integrations/okx/`).

В фазе 1 domain-документы могут ссылаться на integration-документацию **нейтральными формулировками** («integration-документация биржи»), без указания конкретных путей и без привязки к OKX как единственной бирже.

### 3. Расположение `docs/GLOSSARY.md`

Глоссарий проекта живёт в **корне `docs/`**, не в `docs/spec/references/`.

Это **осознанное исключение** из жанровой структуры. Глоссарий по жанру ADR-0002 — это reference, и формально его место в `docs/spec/references/`. Однако:

- Глоссарий — навигационная точка входа, должна быть **видна сразу** при открытии `docs/`.
- Глоссарий уже зарезервирован как TBD в `docs/README.md`.
- Прятать точку входа в подкаталог жанра — снижение её роли.

Содержание глоссария — компактно (~26 entries: 13 core + 3 runtime + ~6 сквозных концепций + ~4 терминологических соглашения). Подробно — в ADR-0006 и ADR-0009.

Жанр reference при этом сохраняется как самостоятельный (`docs/spec/references/`); глоссарий — единственное явное исключение из жанрового расположения.

### 4. Общая структура `docs/spec/` после фазы 1
docs/
├── GLOSSARY.md            # глоссарий проекта, исключение из spec/references/
├── README.md
├── spec/
│   ├── models/
│   │   ├── core/          # 13 документов (см. §1)
│   │   └── runtime/       # 3 документа (см. §1)
│   ├── lifecycle/
│   │   └── Deal.md        # 1 документ: FSM сделки + переходы (см. ADR-0006, принцип 3)
│   ├── processes/         # 6 документов
│   │   ├── strategy-action-calculator.md
│   │   ├── risk-validator.md
│   │   ├── service-command-executor.md
│   │   ├── market-data-jobs.md
│   │   ├── reconcile-anomaly.md
│   │   └── audit.md
│   ├── invariants/        # ~5 документов
│   │   ├── kill-switch-policy.md
│   │   ├── anomaly-classification.md
│   │   ├── freshness-baseline.md
│   │   ├── cleanup-on-finalization.md
│   │   └── status-resolution.md
│   ├── integrations/      # создаётся в фазе 2; структура зафиксирована в §2
│   ├── references/        # жанр сохранён, в фазе 1 не наполняется
│   └── _templates/        # как в ADR-0002
├── api/                   # API-документация endpoints — самостоятельный жанр (см. §5)
├── domain/                # legacy, упраздняется в фазе 6
├── conventions/           # без изменений
├── planning/              # без изменений
└── ops/                   # без изменений
Жанр `references/` сохраняется по ADR-0002 как самостоятельный, но в фазе 1 не наполняется. Создаётся пустой каталог как зарезервированное место.

### 5. Сосуществование с `docs/api/`

**`docs/api/` остаётся как самостоятельный жанр**, не сливающийся с `docs/spec/integrations/`.

- `docs/api/` описывает endpoint'ы внешних API: request/response, auth, rate limits, бизнес-смысл endpoint'ов. Это документация по самому API биржи.
- `docs/spec/integrations/<exchange>/` описывает **связь** биржи с доменной моделью: формат DTO в нашем употреблении, маппинг в доменные модели, resolver-таблицы.

Это два разных жанра документации. Они сосуществуют, не сливаясь. Реорганизация `docs/api/` — отдельная фаза 4 (отдельный ADR при старте фазы 4).

## Alternatives

**По §1 (деление core/runtime):**

- *Вариант: без деления, все model-документы в `docs/spec/models/` без подкаталогов.* Отброшен. Объединение persistent бизнес-объектов и транзитных runtime-сборок в одной папке затрудняет навигацию. При 16+ документах появляется реальная необходимость в структуре.
- *Вариант: вместо подкаталогов — суффикс/префикс в имени файла (`Deal-core.md`, `DealContext-runtime.md`).* Отброшен. Уродует имена документов; навигация по подкаталогам естественнее.
- *Выбран: подкаталоги `core/` и `runtime/`.*

**По §2 (структура integration):**

- *Вариант: один документ на сущность, без подкаталогов (`docs/spec/integrations/okx/order.md` содержит и DTO, и mapping).* Отброшен. Это два разных вида знания — описание формата биржи vs описание связи с доменом. Сваливание в один документ затрудняет ревью, размер растёт.
- *Вариант: `docs/spec/integrations/<exchange>/` с подкаталогами по другим осям (`status/`, `request/`, `response/`).* Отброшен. Произвольная категоризация, не соответствует жанровым ролям документов.
- *Выбран: подкаталоги `models/` (внешние DTO) и `mapping/` (DTO ↔ домен + resolver-таблицы).*

**По §3 (расположение глоссария):**

- *Вариант: глоссарий в `docs/spec/references/glossary.md` (по жанру).* Отброшен. Точка входа в подкаталоге снижает её роль; читатель не найдёт глоссарий с первого взгляда на `docs/`.
- *Вариант: глоссарий в `docs/spec/references/` + редирект-README в корне `docs/`.* Отброшен. Дополнительный артефакт ради формализма; редирект устаревает легко.
- *Выбран: `docs/GLOSSARY.md` в корне `docs/` как осознанное исключение.*

**По §5 (сосуществование с `docs/api/`):**

- *Вариант: всё про OKX (endpoint'ы + DTO + маппинг) — в `docs/spec/integrations/okx/`. `docs/api/` упраздняется.* Отброшен. Документация endpoint'ов как таковых — отдельный жанр (бизнес-смысл API, auth, rate limits — не про доменное маппирование). Сваливание в один каталог теряет границу жанров.
- *Вариант: `docs/api/` поглощает функции integration-документации.* Отброшен. `docs/api/` про внешний API сам по себе, без привязки к нашему домену.
- *Выбран: сосуществование как самостоятельные жанры.*

## Consequences

### Что создаётся в ходе фазы 1

- `docs/spec/models/core/` — папка, в неё мигрируются 13 model-документов кластеров C1-C8 + приводится к стандарту существующий `AnomalyReport.md`.
- `docs/spec/models/runtime/` — папка, в неё мигрируются 3 model-документа (DealContext, DealActionState, ServiceCommand).
- `docs/spec/lifecycle/` — папка, в неё мигрируется `Deal.md`.
- `docs/spec/processes/` — папка, в неё мигрируются 6 process-документов.
- `docs/spec/invariants/` — папка, в неё создаются ~5 invariant-документов по ходу миграции соответствующих кластеров.
- `docs/spec/references/` — папка создаётся пустой (зарезервировано).
- `docs/GLOSSARY.md` — создаётся в начале фазы 1 как рабочий артефакт миграции, наполняется по ходу.

### Что создаётся в ходе фазы 2

- `docs/spec/integrations/okx/models/` — описание внешних DTO OKX.
- `docs/spec/integrations/okx/mapping/` — маппинг DTO ↔ домен + resolver-таблицы.

Для будущих бирж — аналогичная структура `docs/spec/integrations/<exchange>/{models,mapping}/`.

### Что не меняется

- ADR-0002 (стандарт документа) — без изменений. ADR-0007 уточняет расположение, не формальную структуру документа.
- Шаблоны в `docs/spec/_templates/` (по ADR-0002) — без изменений. Применяются для документов обоих подвидов model (core и runtime) одинаково.
- `docs/api/` остаётся как есть до фазы 4.
- `docs/domain/` остаётся как legacy до фазы 6 (упраздняется только после полной миграции и цикла ревью).
- Другие папки `docs/` (`conventions/`, `planning/`, `ops/`) — не затрагиваются.

### Открытый рабочий вопрос: шаблон `runtime-model.md`

Шаблон `model.md` из ADR-0002 был спроектирован для core-моделей. Runtime-модели имеют отличающуюся структуру: меньше про поля, больше про lifecycle одной операции, больше про контракты с потребителями. Возможно, потребуется отдельный шаблон `runtime-model.md`.

Решение откладывается до миграции первого runtime-документа (вероятно — `DealContext.md` в кластере C1 фазы 1). По результату первой миграции принимается решение: создавать отдельный шаблон или адаптировать `model.md` для обоих случаев. Это **не блокирующий** вопрос для принятия ADR-0007.

### Связь с другими ADR

- **ADR-0002** (стандарт документа). ADR-0007 уточняет жанровую структуру (внутреннее деление model на core/runtime, внутреннее деление integration на models/mapping). Сам стандарт документа из ADR-0002 не меняется.
- **ADR-0006** (принципы спецификации). ADR-0007 — структурное применение принципов ADR-0006. Список 13 core + 3 runtime моделей — следствие применения критерия бизнес-объекта и критерия core vs runtime из ADR-0006.
- **ADR-0008** (журнал open-questions). Журнал живёт вне `docs/spec/` (в `.claude/questions/`), но при миграции в spec-документах могут появляться inline-ссылки на Q-N (см. ADR-0008).
- **ADR-0009** (стратегия миграции). Описывает операционный план создания структуры ADR-0007 по фазам.

## Примечания

### Технические

(пусто)

### Содержательные

(пусто; разрешены только в статусах Superseded и Deprecated)
