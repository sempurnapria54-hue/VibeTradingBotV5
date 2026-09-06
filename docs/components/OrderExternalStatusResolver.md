# OrderExternalStatusResolver

## На какой вопрос отвечает этот файл

Кто переводит внешний статус ordinary order в доменный.

## Назначение

`OrderExternalStatusResolver` преобразует внешний статус/факт ordinary
order биржи в доменный `Order.Status` + optional `closeReason candidate`.
Per-биржа реализация: `OkxOrderExternalStatusResolver`. FSM/handlers с
сырыми строками биржи не работают (см.
`docs/rules/external-status-resolution.md`).

## Где живёт

**У коннектора.** Вход резолвера — слово площадки, и знает его словарь
только эта сторона; применяет статус к отдаваемой модели сама граница
чтения, последним шагом. Критерий и доводы —
`docs/rules/external-status-resolution.md` §«Где резолвится — сторона
выбирается по словарю источника».

**Через границу сервисов едет статус, не пара.** Причину закрытия
назначает ядро: её операнды — намерение отмены, уже стоящая причина и
исчерпание цикла — живут у исполнителя.

## Контракт и границы

Возвращает result-object (`status + closeReason candidate`); применяет его
к сущности и сохраняет refresh/executor layer, не resolver. Resolver не
сохраняет сущность, не принимает FSM-решения, не запускает cleanup/
kill-switch, не закрывает сделку, не создаёт anomaly report.

Неизвестный статус не маппится молча в `UNKNOWN` — бросается
`ExternalStatusException` (см.
`docs/rules/controlled-exchange-exceptions.md`).

## OKX-маппинг

`live`→ACTIVE, `partially_filled`→PARTIALLY_COMPLETED, `filled`→COMPLETED,
`canceled`/`mmp_canceled`→CANCELED, unknown→`ExternalStatusException`
(детали и closeReason — `docs/models/mapping/Order.md`).
`ExternalNotFoundException` — только после полного order evidence-cycle.
