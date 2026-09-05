# MarketStructureService

## На какой вопрос отвечает этот файл

Кто отдаёт готовую структуру рынка.

## Назначение

`MarketStructureService` отдаёт готовую `MarketStructure` вместе с её
`MarketPriceLevel` (`docs/models/domain/other/MarketStructure.md`). Сам
уровни по свечам не ищет — их заранее считает
`docs/components/MarketStructureJob.md`.

## Контракт

- `Optional<MarketStructure> getLatestStructure(Long instrumentId,
  Long marketStructureConfigId, Duration tolerance)` — последняя
  структура идентичности, свежая под срок **запрашивающего**; точка
  отсчёта — `windowEndAt`.

Срок приезжает операндом по тому же доводу, что у значений индикатора
(`docs/components/IndicatorService.md`). Выбора уровня контракт не несёт:
уровень из уже отданной структуры достаёт её читатель предикатом самой
модели, а не чужой сервис.

## Поведение при отсутствии / устаревании

Структуры нет либо она старше названного срока — отдаётся пустота; обе
пустоты неразличимы и ведут к одной реакции читателя
(`docs/rules/market-data-freshness.md`).
