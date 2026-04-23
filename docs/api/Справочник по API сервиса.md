# Справочник по API сервиса

## StrategyController

Управляет стратегиями: создание, получение, активация, деактивация и логическое удаление.

* `POST /api/strategies` — создаёт новую стратегию.
* `GET /api/strategies/{internalId}` — возвращает стратегию по `internalId`.
* `PUT /api/strategies/{internalId}/activate` — активирует стратегию.
* `PUT /api/strategies/{internalId}/inactivate` — деактивирует стратегию.
* `PUT /api/strategies/{internalId}/delete` — логически удаляет стратегию.
