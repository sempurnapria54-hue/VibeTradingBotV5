# PositionStatusResolver

## На какой вопрос отвечает этот файл

Кто определяет доменный статус позиции по факту её наличия.

## Назначение

`PositionStatusResolver` работает не со строковым external status, а с
фактом наличия позиции: добытая позиция / её отсутствие →
`PositionStatusResolveResult` (см.
`docs/components/models/PositionStatusResolveResult.md`).

**Живёт у ядра, и это следствие критерия, а не размещение по вкусу.**
Сырого статуса у позиции нет вовсе — резолвить словарь площадки здесь
нечего, а отсутствие записи трактуется вместе с исчерпанием цикла добычи,
то есть операндом исполнителя. Пер-источниковой реализации у резолвера
поэтому нет: он один. Критерий и его доводы —
`docs/rules/external-status-resolution.md` §«Где резолвится — сторона
выбирается по словарю источника».

## Политика

```text
позиция не добыта       -> CLOSED + closeReason candidate = EXTERNAL_CLOSE
позиция добыта          -> ACTIVE + null
externalSize == 0       -> ACTIVE, live risk = false
```

Успешное «не найдено» по запросу позиции — нормальный closed-on-exchange
факт, **не** `ExternalNotFoundException`. Live risk: `ACTIVE &&
externalSize > 0` (см. `docs/models/domain/core/Position.md`).

## Границы

Возвращает result-object; применяет исполнитель добычи (`closeReason`
заполняется, только если текущий пуст — ранее установленный не
перетирается). Resolver не сохраняет сущность и не принимает FSM-решений.
OKX-детали разбора записи — `docs/models/mapping/Position.md`.
