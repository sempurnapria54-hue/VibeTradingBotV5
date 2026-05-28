# PositionStatusResolver

## На какой вопрос отвечает этот файл

Кто определяет доменный статус позиции по факту её наличия (компонент-
resolver): контракт, политика null/externalSize, реализация под биржу.

## Назначение

`PositionStatusResolver` работает не со строковым external status, а с
фактом наличия позиции: `PositionExternalSnapshot` / `null` →
`PositionStatusResolveResult` (см.
`docs/components/models/PositionStatusResolveResult.md`). Per-биржа
реализация: `OkxPositionStatusResolver`.

## Политика

```text
snapshot == null        -> CLOSED + closeReason candidate = EXTERNAL_CLOSE
snapshot != null        -> ACTIVE + null
snapshot.externalSize==0 -> ACTIVE, live risk = false
```

Успешный `null` по запросу позиции — нормальный closed-on-exchange факт,
**не** `ExternalNotFoundException`. Live risk: `ACTIVE && externalSize >
0` (см. `docs/models/domain/core/Position.md`).

## Границы

Возвращает result-object; применяет refresh/executor layer (`closeReason`
заполняется только если текущий `== null`, ранее установленный не
перетирается). Resolver не сохраняет сущность и не принимает FSM-решения.
OKX-детали — `docs/models/mapping/Position.md`.
