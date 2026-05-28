# OkxAccountBillResponse (OKX account bill records)

## На какой вопрос отвечает этот файл

Какие поля у OKX bill response — одной записи денежного движения по
торговому аккаунту.

## Контекст

Raw OKX response endpoint'ов `GET /api/v5/account/bills` (последние 7
дней) и `GET /api/v5/account/bills-archive` (последние 3 месяца).

В отличие от fills (факт исполнения ордера) bills показывают **изменение
денег на аккаунте**: realized PnL, комиссии, rebate, funding fee и
прочие cashflow-события. Для итогового финансового результата сделки
(`Deal.resultProfit`) bills могут быть более полным источником, чем
fills (включают funding).

Доменно `AccountBill` / `DealCashFlow` как persisted-сущность на первом
этапе **не вводим** — нет executor'а и нет домена; вопрос целесообразности
— OKX-Q3 в `.claude/work/questions/open-questions.md`. Mapping и
контракт endpoint'ов — `docs/client/okx/rules/okx-account-bills-mapping.md`.

Raw OKX DTO не выходит за adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`).

## Поля одного bill (по архивному источнику)

Поля документированы; сужение до used отложено до закрытия OKX-Q3.

### Идентификация

| OKX field | Назначение |
|---|---|
| `billId` | id записи; **якорь пагинации** через `after`/`before`; используется для идемпотентности |
| `type` | тип bill-записи |
| `subType` | подтип. Для funding: `173` (expense) / `174` (income). Актуальный список — справочник OKX |
| `ts` | время bill-события (Unix ms) |

### Инструмент и валюта

| OKX field | Назначение |
|---|---|
| `instType` | тип инструмента (`SWAP`/`FUTURES`/`SPOT`/...) |
| `instId` | инструмент (`ETH-USDT-SWAP`) |
| `ccy` | валюта движения баланса (`USDT`) |
| `mgnMode` | режим маржи (`isolated`/`cross`/`cash`) |

### Денежные поля

| OKX field | Назначение |
|---|---|
| `balChg` | изменение баланса (главный кандидат для `DealCashFlow.amount`) |
| `bal` | баланс после события |
| `pnl` | profit/loss в рамках события (если применимо) |
| `fee` | комиссия / rebate (отрицательное — списание, положительное — rebate) |

### Позиция / ордер

| OKX field | Назначение |
|---|---|
| `ordId` | id ордера, если bill связан с ордером |
| `sz` | размер (если применимо) |
| `posBalChg` | изменение баланса позиции |
| `posBal` | баланс позиции после события |

### Переводы / примечания

| OKX field | Назначение |
|---|---|
| `from` | откуда переведены средства (если bill — перевод) |
| `to` | куда переведены |
| `notes` | текстовое примечание |

Все числа приходят строками; numeric → `BigDecimal` при парсинге.
