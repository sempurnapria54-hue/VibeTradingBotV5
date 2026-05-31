# TimeFrame — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменный enum `TimeFrame` маппится в строки таймфреймов
источников.

## Контекст

Mapping-слой для `TimeFrame`. Доменный enum `TimeFrame` строк
источника не хранит; каноническое описание enum (первоисточник —
свечная подсистема) — `docs/models/domain/other/CandleGroup.md`
§«Енум `TimeFrame`». Маппинг строк живёт только здесь и в
`TimeFrameMapper` (компонент adapter-слоя). Остаточная развилка
размещения (свёртка раздела `TimeFrame` в
`docs/models/domain/aggregate/Strategy.md` до ссылки) — TIME-Q1 в
`.claude/work/questions/open-questions.md`.

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
- `TimeFrameMapper` предоставляет `domainToOkx` (домен → строка OKX).
  Обратное направление в коде шага 1 не используется и не заведено
  (по потребности). Строки баров живут в `util.Constants.Okx`.
- Отдельный `TimeFrameResolver` на первом этапе не нужен.
