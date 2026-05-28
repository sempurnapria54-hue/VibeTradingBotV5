# OKX WebSocket: лимиты соединения

## На какой вопрос отвечает этот файл

Какие лимиты у WebSocket соединений OKX и какие требования по keep-alive
/ количеству подписок.

## Контекст

Exchange-specific правило OKX для канального уровня. Применяется к
любым WS-каналам (public / private / business). REST-лимиты — в
соответствующих mapping-файлах (`okx-order-mapping.md`,
`okx-balance-mapping.md` и т.п.).

> Альтернатива при размещении: объединить с `okx-service-urls.md` в
> `okx-connectivity.md`. Не выбрана, чтобы сохранить «один файл —
> одна забота» (лимиты протокола vs география endpoint'ов).

## Лимиты

- **Connection limit (по IP):** до **3 подключений в секунду**.
- **Request limit (per connection):** суммарно
  `subscribe` + `unsubscribe` + `login` ≤ **480 раз в час** на одно
  соединение.
- **Timeout / keep-alive:** если подписка не установлена или пушей
  нет > **30 секунд** — соединение может быть разорвано биржей.
  Рекомендуется WS-ping и ожидание pong (фрейм-ping/pong WS или
  application-level в зависимости от клиента).
- **Channel connection count limit (per sub-account, per channel):**
  до **30 WS-коннектов** на один и тот же канал из списка
  (`orders`, `account`, `positions`, `balance_and_position` и др.).
  При превышении последняя подписка обычно отклоняется; биржа
  возвращает `channel-conn-count-error`.

**Не затронуто этим лимитом:** операции ордеров через WS (`place` /
`amend` / `cancel`) — отдельный лимит, общий с соответствующими REST
endpoint'ами.

## Применение в коде

- При старте — единое соединение public + private + business
  (отдельные сокеты по типу). Не создавать новое соединение под каждую
  подписку.
- Подписки группируются батчами; общее количество ops под лимит
  480/час контролируется в client-layer.
- Heartbeat / ping — обязателен (> 30 с пауза → reconnect risk).
- Reconnect / resubscribe — централизованная политика; после reconnect
  делать REST-snapshot (`orders-pending`, `positions`, `balance`),
  затем подписываться (WS `orders` не присылает начальный snapshot).
