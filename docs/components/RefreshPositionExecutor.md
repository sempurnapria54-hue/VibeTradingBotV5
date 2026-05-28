# RefreshPositionExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_POSITION` (компонент-executor): что делает,
политика null/externalSize.

## Назначение

Получает `REFRESH_POSITION`. Берёт `Exchange`/`Instrument` из
`DealContext` / command context, вызывает `ClientService` для текущей
позиции по инструменту, получает `PositionExternalSnapshot` или `null`,
прогоняет через `PositionStatusResolver`, применяет `status`, заполняет
`closeReason candidate` только если текущий `== null`. Для OKX — один
логический запрос `GET /account/positions` по instType+instId (см.
`docs/client/okx/rules/okx-position-mapping.md`).

## Политика результата

```text
snapshot == null -> позиции на бирже нет -> Position.status = CLOSED
snapshot != null -> позиция есть         -> Position.status = ACTIVE
```

- snapshot найден → обновляет external-поля `Position`;
- snapshot не найден → существующую `Position` → `CLOSED`; новой `CLOSED`
  не создаёт;
- локальной `Position` нет, snapshot найден в active Deal flow → создаёт
  `Position` и привязывает к `Deal`.

`externalSize == 0` при `ACTIVE` — exchange record есть, live risk нет
(cleanup/anomaly/retry). Live risk: `status == ACTIVE && externalSize > 0`
(см. `docs/models/core/Position.md`). При обычном запуске
`requestedCloseReason` не получает. Общая семантика `REFRESH_*` —
`docs/components/ServiceCommandExecutor.md`.

> Гранулярность executor-файлов под вопросом — CMD-Q1.
