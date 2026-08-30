# ClosePositionExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CLOSE_POSITION_COMMAND`.

## Назначение

Получает `CLOSE_POSITION_COMMAND` — risk-reducing executor полного закрытия
позиции. Загружает `Position`, берёт `Exchange`/`Instrument` из
`DealContext` / command context, проверяет minimal domain / exchange
safety checks, отправляет full close request, сохраняет ACK / технический
результат; подтверждение факта закрытия — отдельным `REFRESH_POSITION_COMMAND`.

`CLOSE_POSITION_COMMAND` всегда full close — partial close запрещён (см.
`docs/rules/no-partial-close.md`). **Эмитентов команды два**: `DealExitPendingHandler` на тропе
условия-перехода и `ExitActionExecutor` на тропе явного действия
шага `EXIT`. Оба — уровня сделки: полное закрытие нетто-экспозиции
законно только при выходе всех траншей
(`docs/rules/exit-teardown-order.md`). Для самого исполнителя команды это различия не создаёт —
контракт один; различается **дочистка вокруг** неё, и её порядок задан
инвариантом `docs/rules/exit-teardown-order.md` (сначала отмена живых
входных заявок траншей, потом закрытие). Не блокируется `RiskValidator` (см.
`docs/rules/risk-validator-scope.md`). Не смешивает закрытие с доменной
автоотменой ордеров: OKX adapter может технически проставить
`autoCxl=true`, но handler всё равно подтверждает закрытие через
`REFRESH_POSITION_COMMAND` и дочищает известные live `Order`/`AlgoOrder`
командами `CANCEL_*` (unknown live tails → anomaly/safety-flow, см.
`docs/models/mapping/Position.md`).

**Закрытый объём команды исполнитель не возвращает и не хранит.** Ответ
источника идентификатора заявки не несёт, поэтому исполнение команды
наблюдается нетто-размером позиции, который приносит подтверждающий
`REFRESH_POSITION_COMMAND`, а разложение объёма по траншам делает правило
сопоставления (`docs/models/domain/aggregate/DealTranche.md`).
Собственного поля
под объём не заводится: величина производная.

Минимальные проверки: позиция существует и относится к сделке/инструменту;
закрывается весь размер; есть данные для exchange request; команда не
противоречит known exchange state. ACK не runtime truth (см.
`docs/rules/ack-not-runtime-truth.md`).

## ClosePositionCommandPayload

`positionId`, `requestedCloseReason` (`Position.CloseReason`). Не содержит
`closeFraction` — `CLOSE_POSITION_COMMAND` всегда full close (см.
`docs/rules/no-partial-close.md`). Не содержит `autoCancelOrders`/`autoCxl`
— это OKX-specific флаг adapter (см. `docs/models/mapping/Position.md`).
`instrumentExternalId`/`positionSide`/`marginMode` не нужны — приходят из
`DealContext` / adapter.
