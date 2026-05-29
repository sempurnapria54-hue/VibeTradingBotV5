# Онтология слоёв моделей

## На какой вопрос отвечает этот файл

Как организованы доменные и интеграционные модели в `docs/models/`,
куда уходит не-модельное биржевое знание, как живёт маппинг между
слоями.

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
Бизнес-логика всегда выполняется на доменной модели: мапим в домен
→ выполняем логику → при необходимости мапим обратно. Сквозное
правило — `docs/rules/business-logic-on-domain-model.md`.

### Цепочка слоёв

```text
integrations/{name}  →  externalSnapshot  →  domain  →  persistence
                                              ↓
                                            rest
```

Каждый слой — отдельный тир `docs/models/`, со своим типом моделей и
своим вопросом.

### Слои

- **`docs/models/integrations/{name}/`** — нативные модели внешнего
  источника. Один источник = один `{name}`. Для биржи — `okx`,
  `binance` (будущий); для не-биржевых сервисов — по имени сервиса.
  Файл = инвентарь полей источника (имя, тип, семантика, used/
  unused). PascalCase, имя совпадает с DTO источника.
  Маппинг «native → externalSnapshot» **не здесь** — в `mapping/`.

- **`docs/models/externalSnapshot/`** — нормализованные граничные
  модели (`*ExternalSnapshot`). Единственное, что выходит за
  `ClientService` / adapter (см.
  `docs/rules/raw-exchange-dto-boundary.md`). Смыслово принадлежит
  домену, но материально — отдельный тир (граница между интеграцией
  и доменом). Отдельный файл создаётся только при наличии
  самостоятельного содержания (валидация в конструкторе, нетривиальная
  структура и т. п.); иначе — пустой каталог-скаффолд.

- **`docs/models/domain/core/`** — торговая модель с биржевым
  воплощением. Текущий состав: `Position`, `Order`, `AlgoOrder`,
  `BalanceContainer`; reference-core `Instrument`, `Exchange`
  (добавлены в `GAPS_CLOSE_1` шага 1 — дискриминатор «биржевое
  воплощение» / идентичность; классификация на ревью, см.
  `.claude/work/progress/phase-1-step-1-gaps-close-1.md`).

- **`docs/models/domain/aggregate/`** — сущность без биржевой
  привязки, нужная для торговли. Текущий состав: `Deal`, `Strategy`.

- **`docs/models/domain/other/`** — прочая хранимая модель (свечи,
  индикаторы, аудит, инструмент-rules, market structure / phase).

- **`docs/models/persistence/`** — модель хранимого слоя
  (entity-классы / jsonb-снимки / persistence-проекции). На момент
  введения слой пуст — скаффолд.

- **`docs/models/rest/`** — модель API нашего сервиса (request /
  response DTO REST-API). На момент введения слой пуст — скаффолд.

### Маппинг — со-локированный тип под `models/`

`docs/models/mapping/<Сущность>.md` — один файл на доменную сущность,
несёт переходы между слоями для этой сущности:

- `native → externalSnapshot` (таблица полей; источники подразделами,
  если их несколько);
- `externalSnapshot → domain` (материализация в домен);
- `domain → request` (если применимо: формирование request body /
  параметров операций источника);
- статус-резолвер (`externalStatus → domain.Status`).

Per-source детали — подразделами внутри одного файла (`## OKX`,
`## Binance`); source-agnostic ядро (`externalSnapshot ↔ domain`)
живёт один раз. Имя файла — PascalCase, как доменная сущность.

### Не-модельное биржевое знание — вне `models/`

`docs/integrations/{name}/`:

- **`contracts/`** — контракт + лимиты источника: endpoints,
  permissions, rate limits, ACK-семантика (`sCode=0` ≠ runtime
  truth; создание/amend/cancel response), пагинация. Один файл на
  ресурс/тему (`order.md`, `algo-order.md`, `position.md`,
  `balance.md`, `candle.md`, `fills.md`, `fills-archive.md`,
  `account-bills.md`, `service-urls.md` и т. п.).
- **`rules/`** — правила источника: инварианты и конвенции,
  специфичные для этого источника. Например: reduce-only invariant,
  adapter-константы (`tdMode=isolated`, `posSide=net`), WS-лимиты,
  evidence-cycle политика not-found.

### Сквозные правила — без изменений

`docs/rules/` остаётся: `raw-exchange-dto-boundary`,
`ack-not-runtime-truth`, `external-status-resolution`,
`business-logic-on-domain-model` (новое), и т. д. Layer-crossing
правила пока плоско (одной директорией) — без подкаталога
`mapping/`. Если накопится достаточно правил, специфичных именно
для маппинга, — выделим в отдельную тему по потребности.

### Роспуск `docs/client/`

`docs/client/` распускается полностью:
- `docs/client/<биржа>/models/*` → `docs/models/integrations/<биржа>/*`
- mapping-доки `docs/client/<биржа>/rules/*-mapping.md` дробятся на
  три части:
  - mapping + статус-резолвер → `docs/models/mapping/<Сущность>.md`
  - contract + лимиты → `docs/integrations/<биржа>/contracts/<тема>.md`
  - правила источника → `docs/integrations/<биржа>/rules/<тема>.md`
- настоящие правила (`okx-ws-limits.md`, `okx-service-urls.md`) →
  соответственно в `docs/integrations/okx/rules/` и
  `docs/integrations/okx/contracts/`.

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
- `docs/rules/business-logic-on-domain-model.md` — новое сквозное
  правило, к которому привязан принцип.
- Обновлены ссылки в `rule-source-of-truth.md`,
  `raw-exchange-dto-boundary.md`, `ack-not-runtime-truth.md`,
  `external-status-resolution.md`, `classify-type.md`, и в моделях /
  lifecycles / processes, упоминавших `docs/client/…` или
  `docs/models/{core,other}/…`.
- `docs/client/` распущен.
