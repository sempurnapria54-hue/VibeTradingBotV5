# OkxFillsArchiveResponse (OKX trade fills archive)

## На какой вопрос отвечает этот файл

Какие поля у OKX fills-archive responses (генерация и получение
ссылки) — двух операций async-флоу выгрузки fills > 3 месяцев.

## Generate response (POST /api/v5/trade/fills-archive)

`data[0]`:

| OKX field | Назначение |
|---|---|
| `result` | `"true"` — ссылка уже есть (можно сразу `GET`); `"false"` — биржа начала генерацию |
| `ts` | время первой регистрации запроса (Unix ms) |

## File-link response (GET /api/v5/trade/fills-archive)

`data[0]`:

| OKX field | Назначение |
|---|---|
| `year` | год запрашиваемого квартала (4 цифры) |
| `quarter` | квартал (`Q1`/`Q2`/`Q3`/`Q4` — регистр важен) |
| `state` | `ongoing` (генерируется) / `finished` (готов) / `failed` (ошибка генерации) |
| `ts` | время обновления state / формирования ссылки (Unix ms) |
| `fileHref` | URL для скачивания файла (заполнен при `state=finished`; пуст иначе) |

`fileHref` — обычно **временная ссылка**, скачивать сразу.

## Содержимое архивного файла

Файл — fills-выгрузка за квартал (формат — обычно CSV.gz). Структура
строки соответствует полям `OkxFillResponse.md` (`tradeId`, `ordId`,
`clOrdId`, `billId`, `fillPx`, `fillSz`, `side`, `posSide`, `execType`,
`feeCcy`, `fee`, `ts`, `instType`, `instId`, `tag`). Точная схема
выгрузки в архиве не зафиксирована; при материализации
`TradeFillsArchive` — сверять по реальному ответу OKX.
