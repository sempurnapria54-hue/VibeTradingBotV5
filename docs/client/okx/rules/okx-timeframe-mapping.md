# OKX timeframe mapping

## На какой вопрос отвечает этот файл

Как доменный `TimeFrame` маппится в строки таймфреймов OKX и обратно.

## Контекст

Exchange-specific mapping для OKX. Доменный enum `TimeFrame` OKX-строк не
хранит (раздел `TimeFrame` в `docs/models/core/Strategy.md`; итоговое
размещение enum — открытый вопрос TIME-Q1 в
`.claude/work/questions/open-questions.md`). Весь OKX↔domain маппинг
строк живёт только здесь / в `TimeFrameMapper` (компонент adapter-слоя).

## Маппинг

Доменный `TimeFrame` ↔ строка таймфрейма OKX, например:

```text
TimeFrame.ONE_HOUR <-> "1H"
```

Полный набор доменных значений: `ONE_MINUTE`, `THREE_MINUTES`,
`FIVE_MINUTES`, `FIFTEEN_MINUTES`, `ONE_HOUR`, `TWO_HOURS`,
`FOUR_HOURS`, `ONE_DAY`.

## Правила

- Маппинг строгий: без `lowerCase` / `upperCase`, точное соответствие
  строк.
- `TimeFrameMapper` предоставляет обе стороны: `domainToOkxClient` и
  `okxClientToDomain`.
- Отдельный `TimeFrameResolver` на первом этапе не нужен.
