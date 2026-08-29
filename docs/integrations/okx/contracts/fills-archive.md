# OKX contracts: fills-archive (async flow)

## На какой вопрос отвечает этот файл

Каков контракт операций для выгрузки fills > 3 месяцев.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Trade», секции apply/get transaction
details архива). При расхождении с офдоком побеждает офдок;
синхронизация — перевыкачка + дифф при каждом заходе интегратора
(`.claude/processes/api-docs-completion.md`, канал —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11.

## Endpoints

- **Запросить генерацию архива:** `POST /api/v5/trade/fills-archive`.
  Permission: Read. Rate limit: **10 запросов в день** по User ID.
  Body (JSON): `year` (4 цифры), `quarter` (`Q1`/`Q2`/`Q3`/`Q4`,
  **регистр важен**). Текущий незавершённый квартал обычно
  недоступен.
- **Получить ссылку на файл:** `GET /api/v5/trade/fills-archive`.
  Permission: Read. Rate limit: 5 req / 2 s по User ID. Query:
  `year` + `quarter`.

## Поток

```text
1. POST /trade/fills-archive { year, quarter }
   -> data[0].result = "true"  -> файл уже сгенерирован; шаг 2
   -> data[0].result = "false" -> биржа начала генерацию;
                                  ждать (часы–десятки часов) и polling шаг 2

2. GET /trade/fills-archive?year=...&quarter=...
   -> data[0].state = "ongoing"  -> повторять с разумным интервалом
   -> data[0].state = "finished" -> скачивать data[0].fileHref сразу
                                    (ссылка временная)
   -> data[0].state = "failed"   -> повторить POST (шаг 1)
```

Если генерация длится >48 часов — обычно поддержка биржи.

## Покрытие и ограничения

- Глубина: до ~2 лет назад, **не включая** последние 3 месяца
  (последние 3 месяца — `trade/fills-history`).
- Гранулярность: один запрос/файл = один квартал.
- Текущий квартал биржа обычно не отдаёт, пока не завершён.

## Скачивание и парсинг

- `fileHref` — обычно временная ссылка; скачивать без задержек.
- Формат файла — обычно CSV.gz; структура строк —
  `OkxFillResponse.md` fields.
