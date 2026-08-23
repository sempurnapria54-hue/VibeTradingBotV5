# ClosePositionExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CLOSE_POSITION_COMMAND` (компонент-executor): что делает,
инвариант full close.

## Назначение

Получает `CLOSE_POSITION_COMMAND` — risk-reducing executor полного закрытия
позиции. Загружает `Position`, берёт `Exchange`/`Instrument` из
`DealContext` / command context, проверяет minimal domain / exchange
safety checks, отправляет full close request, сохраняет ACK / технический
результат; подтверждение факта закрытия — отдельным `REFRESH_POSITION_COMMAND`.

`CLOSE_POSITION_COMMAND` всегда full close — partial close запрещён (см.
`docs/rules/no-partial-close.md`). **Эмитентов команды два** (решение
держателя `GAPS_CLOSE_16`): `ExitPendingHandler` на тропе
условия-перехода и исполнитель `CLOSE_ACTION` на тропе явного действия
шага `EXIT`. Для самого исполнителя команды это различия не создаёт —
контракт один; различается **дочистка вокруг** неё, и она открыта
(`docs/models/domain/aggregate/Strategy.md` §Действия). Не блокируется `RiskValidator` (см.
`docs/rules/risk-validator-scope.md`). Не смешивает закрытие с доменной
автоотменой ордеров: OKX adapter может технически проставить
`autoCxl=true`, но handler всё равно подтверждает закрытие через
`REFRESH_POSITION_COMMAND` и дочищает известные live `Order`/`AlgoOrder`
командами `CANCEL_*` (unknown live tails → anomaly/safety-flow, см.
`docs/models/mapping/Position.md`).

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
