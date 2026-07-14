# Snapshot v14

**Дата:** 2026-05-28.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после реструктуризации
`docs/models/` и роспуска `docs/client/`).

## Состояние

`docs/models/` реорганизован по слоям
(`integrations → externalSnapshot → domain → persistence`, + `rest`,
+ `mapping`); `docs/client/` распущен; не-модельное биржевое знание
перенесено в `docs/integrations/{name}/contracts/` + `rules/`. Слои
`externalSnapshot`, `persistence`, `rest` — скаффолды (наполнения
пока нет). История миграции —
`.claude/work/history/2026-05-28-реструктуризация-слоёв-моделей.md`.

## Что изменилось относительно v13

### Новые decisions

- `.claude/decisions/model-layer-ontology.md` — онтология слоёв
  моделей (принцип, цепочка, mapping co-located, не-модельное
  знание вне `models/`, альтернативы).

### Superseded decisions

- `client-layer-docs.md` → SUPERSEDED.
- `models-core-vs-other.md` → SUPERSEDED.

### Новое сквозное правило

- `docs/rules/business-logic-on-domain-model.md`.

### Перестановка models

- `docs/models/core/*` → `docs/models/domain/core/` (Position,
  Order, AlgoOrder, BalanceContainer) + `domain/aggregate/` (Deal,
  Strategy).
- `docs/models/other/*` → `docs/models/domain/other/`.
- `docs/client/okx/models/*` → `docs/models/integrations/okx/`
  (контент переработан как инвентарь полей).

### Новые слои в models/

- `docs/models/mapping/` — 9 файлов (Order, AlgoOrder, Position,
  Balance, InstrumentExternalRules, MarketPriceData, TimeFrame,
  Candle, TradeFill-стаб).
- `docs/models/externalSnapshot/` — скаффолд (README).
- `docs/models/persistence/` — скаффолд (README).
- `docs/models/rest/` — скаффолд (README).

### Новый каталог integrations

- `docs/integrations/okx/contracts/` — 10 файлов (`order`,
  `algo-order`, `position`, `balance`, `instrument`,
  `market-price-data`, `candle`, `fills`, `fills-archive`,
  `account-bills`, `service-urls`).
- `docs/integrations/okx/rules/` — 3 файла (`adapter-constants`,
  `reduce-only-invariant`, `ws-limits`).

### Удалено

- `docs/client/` целиком (роспуск).
- 11 файлов `docs/client/okx/rules/okx-*-mapping.md` (раздроблены).
- `.gitkeep` в `docs/models/core/`, `docs/models/other/`.

### Обновлено

- `.claude/rules/structure.md` — переписаны model-строки таблицы,
  добавлены строки для `integrations/{name}/`.
- `.claude/skills/classify-type.md` — типы продуктовой области под
  новые слои.
- `.claude/decisions/rule-source-of-truth.md` —
  пути mapping-слоя и правил источника.
- `.claude/decisions/cross-cutting-parking.md` — путь mapping.
- `docs/rules/raw-exchange-dto-boundary.md` — пути.
- ~30 live-документов знания (rules, lifecycles, components,
  models, components/models) — batch-rename ссылок.

### Open-questions

Не закрыто ни одного вопроса (реструктуризация — методологический
проход, не продуктовый). Формулировки `DEAL-Q3` и `TIME-Q1`
уточнены под новые пути слоёв.

## Текущая структура `docs/models/`

```
docs/models/
├── integrations/
│   └── okx/                  ← 7 файлов (Okx*Response)
├── externalSnapshot/         ← скаффолд (README)
├── domain/
│   ├── core/                 ← Position, Order, AlgoOrder, BalanceContainer
│   ├── aggregate/            ← Deal, Strategy
│   └── other/                ← AnomalyReport, IndicatorValue,
│                                InstrumentExternalRules, MarketPhase,
│                                MarketStructure
├── persistence/              ← скаффолд (README)
├── rest/                     ← скаффолд (README)
└── mapping/                  ← 9 файлов (PascalCase по сущности)
```

## Текущая структура `docs/integrations/`

```
docs/integrations/
└── okx/
    ├── contracts/            ← 10 файлов (endpoints + лимиты)
    └── rules/                ← 3 файла (правила источника)
```

## Активные задачи

Нет активных задач исполнения. Следующий шаг — на выбор пользователя
из cross-cutting пунктов backlog.

## Открытые общие вопросы

`open-questions.md`: PROC-Q1, RISK-Q1, ENUM-Q1, DEAL-Q3, TIME-Q1,
CMD-Q1, DEAL-Q1, DEAL-Q2, OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4 — все
12. Закрытых ещё нет (после реструктуризации).

## Что в работе

- Ничего в активной работе. Project Knowledge требует обновления —
  изменения значительные (новый decision, structure.md, supersede,
  новый snapshot, новый rule, обновлённый backlog/open-questions).
