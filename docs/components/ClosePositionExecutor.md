# ClosePositionExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CLOSE_POSITION` (компонент-executor): что делает,
инвариант full close.

## Назначение

Получает `CLOSE_POSITION` — risk-reducing executor полного закрытия
позиции. Загружает `Position`, берёт `Exchange`/`Instrument` из
`DealContext` / command context, проверяет minimal domain / exchange
safety checks, отправляет full close request, сохраняет ACK / технический
результат; подтверждение факта закрытия — отдельным `REFRESH_POSITION`.

`CLOSE_POSITION` всегда full close — partial close запрещён (см.
`docs/rules/no-partial-close.md`). Не блокируется `RiskValidator` (см.
`docs/rules/risk-validator-scope.md`). Не смешивает закрытие с доменной
автоотменой ордеров: OKX adapter может технически проставить
`autoCxl=true`, но handler всё равно подтверждает закрытие через
`REFRESH_POSITION` и дочищает известные live `Order`/`AlgoOrder`
командами `CANCEL_*` (unknown live tails → anomaly/safety-flow, см.
`docs/models/mapping/Position.md`).

Минимальные проверки: позиция существует и относится к сделке/инструменту;
закрывается весь размер; есть данные для exchange request; команда не
противоречит known exchange state. ACK не runtime truth (см.
`docs/rules/ack-not-runtime-truth.md`).
