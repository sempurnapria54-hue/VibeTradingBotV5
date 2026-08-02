# AnomalyJob

## На какой вопрос отвечает этот файл

Кто ищет нарушения базовых инвариантов системы (компонент-job): что
ищет, чем не является.

## Назначение

`AnomalyJob` ищет не штатные runtime-ситуации, а нарушения базовых
инвариантов — сравнивает exchange live facts с доменными active
entities. Фиксирует anomaly/safety report (`AnomalyReport`, см.
`docs/models/domain/other/AnomalyReport.md`) и может инициировать safety-flow по
правилам системы. Сделку по FSM **не** ведёт.

## Типовые anomaly-кейсы

- live `Order`/`AlgoOrder` есть на бирже, но нет соответствующей
  runtime-сущности в БД;
- active position на бирже без active `Deal`, объясняющего её появление;
- terminal `Deal` имеет live position;
- больше одной позиции на инструмент (при максимуме одной);
- DB entity closed, но exchange entity всё ещё live;
- unknown external status; нарушен isolated margin; плечо выше
  биржевого максимума (`externalMaxLeverage`); чужой live risk на
  инструменте; после cleanup остались неизвестные live-хвосты.

## Исключение (не anomaly)

Entry order исполнился → exchange position появилась → локальной
`Position` ещё нет → следующий `REFRESH_POSITION_COMMAND` создаёт `Position` и
привязывает к `Deal`. Это не anomaly при наличии active `Deal` и
известного entry flow.

## Смежное

Зона «live risk после terminal / позиция без active Deal» делит границу с
`ReconciliationJob` (в этой миграции не материализован — только название,
backlog п.7). Полный safety/kill-switch кластер — backlog п.7.
