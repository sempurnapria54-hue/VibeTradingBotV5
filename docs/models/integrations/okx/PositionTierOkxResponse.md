# PositionTierOkxResponse (OKX position tiers)

## На какой вопрос отвечает этот файл

Какие поля у OKX position-tiers response — записи позиционного тира.

## Инвентарь полей

### Используемые (под `InstrumentExternalRules`)

Ответ — `data[]` операции `GET /api/v5/public/position-tiers` с
`instType=SWAP`, `tdMode=isolated` и семьёй инструмента
(`docs/integrations/okx/contracts/position-tiers.md`). Каждая запись —
строка тира справочных правил инструмента
(`docs/models/mapping/InstrumentExternalRules.md`).

| OKX field | Тип (raw) | Семантика |
|---|---|---|
| `minSz` | string-decimal | нижняя граница размера позиции тира, в контрактах. Снапшот — `externalMinSize`, домен — `minSize` |
| `maxSz` | string-decimal | верхняя граница размера позиции тира, в контрактах. Снапшот — `externalMaxSize`, домен — `maxSize` |
| `mmr` | string-decimal | ставка поддерживающей маржи тира — доля нотинала. Снапшот — `externalMaintenanceMarginRate`, домен — `maintenanceMarginRate` |
| `instFamily` | string | эхо семьи запроса — операнд **проверки принадлежности** записи запросу; в снапшот не переносится |
| `tier` | string | номер тира — только в пояснении отказа валидации; в снапшот не переносится |

### Не используется (отбрасывается на маппинге)

- **`imr`, `maxLever`** — ставка начальной маржи и максимальное плечо тира:
  потолок плеча преконтроля остаётся инструмент-уровневым
  (`docs/integrations/okx/contracts/position-tiers.md`).
- **`uly`, `instId`** — прочая идентификация; у SWAP-запроса ось — семья.
- **`baseMaxLoan`, `quoteMaxLoan`** — лимиты займа MARGIN; вне контура.
- **`optMgnFactor`** — опционы; вне контура.

## Конвертация

Числа приходят строками и в снапшоте остаются ими; в `BigDecimal` их
разбирает материализация правил. Пустое обязательное поле и запись чужой
семьи — не молчаливая пустота, а нарушение инварианта контракта; число, не
разобравшееся при материализации, — то же нарушение
(`docs/models/mapping/InstrumentExternalRules.md`).
