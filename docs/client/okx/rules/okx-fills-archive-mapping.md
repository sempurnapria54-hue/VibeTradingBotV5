# OKX fills archive mapping

## На какой вопрос отвечает этот файл

Как устроен async-флоу выгрузки fills > 3 месяцев у OKX (генерация
архива + получение ссылки на файл) — контракт endpoint'ов и состояния.

## Контекст

Exchange-specific mapping для OKX. Используется, когда нужны fills
**старше 3 месяцев и до ~2 лет** (для последних 3 месяцев —
`okx-fills-mapping.md`). Поля responses — `OkxFillsArchiveResponse.md`.

Доменно ни executor, ни persisted-сущность `TradeFillsArchive` на
первом этапе **не вводим** (продуктовое решение по необходимости —
OKX-Q2 в `.claude/work/questions/open-questions.md`). Здесь
зафиксирован контракт операций для будущего использования.

Raw OKX DTO не выходит за adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`).

## Endpoints

- **Запросить генерацию архива:** `POST /api/v5/trade/fills-archive`.
  Permission: Read. Rate limit: **10 запросов в день** по User ID.
  Body (JSON): `year` (4 цифры), `quarter` (`Q1`/`Q2`/`Q3`/`Q4`,
  **регистр важен**). Текущий незавершённый квартал обычно недоступен.
- **Получить ссылку на файл:** `GET /api/v5/trade/fills-archive`.
  Permission: Read. Rate limit: 5 req / 2 s по User ID. Query:
  `year` + `quarter` (как и в POST).

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
- Формат файла — обычно CSV.gz; структура строк — `OkxFillResponse.md`
  fields.
- Парсинг и интеграция с `RefreshFillsExecutor` / возможной
  `TradeFillsArchive` модели — будущая работа после OKX-Q2.

## ClientService контракт

Endpoint'ы — обычные private REST с подписью. Обработка ошибок —
controlled exception на `code != "0"`, parse, invariant
(`docs/rules/controlled-exchange-exceptions.md`).
