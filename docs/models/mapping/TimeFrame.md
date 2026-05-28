# TimeFrame — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменный enum `TimeFrame` маппится в строки таймфреймов
источников.

## Контекст

Mapping-слой для `TimeFrame`. Доменный enum `TimeFrame` строк
источника не хранит (раздел `TimeFrame` в
`docs/models/domain/aggregate/Strategy.md`; итоговое размещение
enum — открытый вопрос TIME-Q1 в
`.claude/work/questions/open-questions.md`). Маппинг строк живёт
только здесь и в `TimeFrameMapper` (компонент adapter-слоя).

Текущие источники: **OKX**.

## OKX

Доменный `TimeFrame` ↔ строка таймфрейма OKX, например:

```text
TimeFrame.ONE_HOUR <-> "1H"
```

Полный набор доменных значений: `ONE_MINUTE`, `THREE_MINUTES`,
`FIVE_MINUTES`, `FIFTEEN_MINUTES`, `ONE_HOUR`, `TWO_HOURS`,
`FOUR_HOURS`, `ONE_DAY`.

### Правила

- Маппинг строгий: без `lowerCase` / `upperCase`, точное соответствие
  строк.
- `TimeFrameMapper` предоставляет обе стороны: `domainToOkxClient` и
  `okxClientToDomain`.
- Отдельный `TimeFrameResolver` на первом этапе не нужен.
