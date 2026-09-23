# Онтология слоёв моделей

## На какой вопрос отвечает этот файл

Почему модельное и биржевое знание разложено по слою модели, а не по
природе источника.

## Контекст

До этого решения:
- Доменные модели делились на `docs/models/core/` (торговые) и
  `docs/models/other/` (прочие хранимые) — `models-core-vs-other.md`
  (superseded ниже).
- Биржевое знание лежало плоско в `docs/client/<Биржа>/models/` +
  `rules/` — `client-layer-docs.md` (superseded ниже): нативная
  модель источника, mapping в домен, контракт endpoint'а, лимиты,
  ACK, инварианты — всё в одном `rules/`-файле.
- Связка «нативная модель → snapshot → домен» при появлении ещё одного
  источника требовала бы дублировать source-agnostic ядро
  (`externalSnapshot ↔ domain`) в каждом per-source mapping-файле.

При добавлении второй биржи (или внешнего сервиса другого рода —
не биржи) такая структура не масштабируется. Нужен «один корень»
моделей, организованный по **слою** (роль модели в потоке данных), а
не по «природе источника».

## Принятое решение

### Принцип

Модели документируются по слою. `docs/models/` организован по слоям.
Выполнение бизнес-логики на доменной модели живёт в
`.claude/rules/codestyle.md` §«Rich-доменные модели».

### Цепочка слоёв

Живёт в `.claude/rules/structure.md` §«Таблица размещения», строки
`docs/models/*`.

### Слои

Действующие слои — нативные модели источника, доменные `core`,
`aggregate`, `other` и модель API — живут в `.claude/rules/structure.md`
§«Таблица размещения», строки `docs/models/integrations/{name}/`,
`docs/models/domain/*`, `docs/models/api/`.

**Два слоя-скаффолда сняты (`GAPS_CLOSE_28`, N7).** Каталоги
`docs/models/externalSnapshot/` и `docs/models/persistence/` так и не
получили ни одного носителя, тогда как знание, ради которого они
вводились, живёт и потребляется в других домах: состав
`*ExternalSnapshot` — в mapping-доке своей сущности, представление в
хранимом слое — в §Персистентность доменного дока плюс
`docs/rules/persistence-representation.md`. Строки сняты из
`.claude/rules/structure.md` и `.claude/skills/classify-type.md`:
исполняемая процедура классификации направляла фрагмент в несуществующий
путь, и ошибка была тихой. Описание обоих слоёв ниже сохранено как
**история решения**; если persistence-проекции появятся в `CODE`, тип
вводится заново — этим решением, а не по памяти.

- **`docs/models/externalSnapshot/`** (снят) — нормализованные граничные
  модели (`*ExternalSnapshot`). Единственное, что выходит за
  `ClientService` / adapter (см.
  `docs/rules/raw-exchange-dto-boundary.md`). Смыслово принадлежит
  домену, но материально — отдельный тир (граница между интеграцией
  и доменом). Отдельный файл создаётся только при наличии
  самостоятельного содержания (валидация в конструкторе, нетривиальная
  структура и т. п.); иначе — пустой каталог-скаффолд.

- **`docs/models/persistence/`** (снят) — модель хранимого слоя
  (entity-классы / jsonb-снимки / persistence-проекции). На момент
  введения слой пуст — скаффолд.

### Маппинг — со-локированный тип под `models/`

Живёт в `.claude/rules/structure.md` §«Таблица размещения», строка
`docs/models/mapping/`.

### Не-модельное биржевое знание — вне `models/`

Живёт в `.claude/rules/structure.md` §«Таблица размещения», строки
`docs/integrations/{name}/contracts/` и `docs/integrations/{name}/rules/`.

### Сквозные правила — без изменений

`docs/rules/` остаётся: `raw-exchange-dto-boundary`,
`ack-not-runtime-truth`, `external-status-resolution`,
`business-logic-on-domain-model` (новое), и т. д. Layer-crossing
правила пока плоско (одной директорией) — без подкаталога
`mapping/`. Если накопится достаточно правил, специфичных именно
для маппинга, — выделим в отдельную тему по потребности.

### Роспуск `docs/client/`

Хроника — `.claude/work/history/2026-09-23-decision-holds-only-fork.md`.

## Альтернативы

- **A. Сохранить `docs/client/` (два корня — `client/` и `models/`).**
  Отклонено: один корень моделей даёт единую точку входа, упрощает
  навигацию и масштабируется на не-биржевые источники.
- **B. Контракты/правила тоже под `docs/models/`** (например,
  `docs/models/integrations/{name}/contracts/`). Отклонено: растягивает
  смысл `models` (контракт endpoint — не модель). Не-модельное
  биржевое знание уходит в `docs/integrations/{name}/`.
- **C. `client` как отдельный слой-этап (между `integrations` и
  `externalSnapshot`).** Отклонено: материализации нет — в текущем
  потоке нативная модель источника идёт прямо в snapshot;
  `integrations/{name}` покрывает любой источник без промежуточного
  тира.
- **D. Per-source mapping** (`docs/models/mapping/<биржа>/<Сущность>.md`).
  Отклонено: source-agnostic ядро (`externalSnapshot ↔ domain`)
  дублировалось бы в каждом per-source файле. Подразделами в одном
  файле — компактнее, ядро живёт один раз.
- **E. `docs/models/mapping/rules/` или `docs/rules/mapping/`**
  (группировка mapping-правил отдельной темой). Отложено: пока
  плоско, разделим по потребности, если накопится критическая масса.

## Supersedes

- `client-layer-docs.md` — superseded. Содержание (где живут
  exchange-specific факты) покрыто новой онтологией: нативная модель
  источника — `docs/models/integrations/{name}/`; контракт и правила
  — `docs/integrations/{name}/`; mapping — `docs/models/mapping/`.
- `models-core-vs-other.md` — superseded. Содержание (разделение
  моделей на торговые vs прочие хранимые) расширено: новый ярус
  `domain/` дробится на `core` (с биржевым воплощением), `aggregate`
  (без биржевой привязки) и `other` (прочие хранимые).

## Следствия

- `.claude/rules/structure.md` — переписаны model-строки таблицы.
- `.claude/decisions/client-layer-docs.md` и `models-core-vs-other.md`
  помечены `SUPERSEDED` ссылкой на этот файл.
- `.claude/rules/codestyle.md` — новое сквозное
  правило, к которому привязан принцип.
- Обновлены ссылки в `rule-source-of-truth.md`,
  `raw-exchange-dto-boundary.md`, `ack-not-runtime-truth.md`,
  `external-status-resolution.md`, `classify-type.md`, и в моделях /
  lifecycles / processes, упоминавших `docs/client/…` или
  `docs/models/{core,other}/…`.
- `docs/client/` распущен.
