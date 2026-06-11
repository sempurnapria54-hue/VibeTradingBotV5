# OKX Service URLs

## На какой вопрос отвечает этот файл

Какие URL у OKX по окружениям (production, demo) и регионам.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
разделы Overview / REST Authentication / WebSocket; смена доменов —
changelog `log_en/`, напр. вывод AWS-доменов 2025-04-28). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора
(`.claude/processes/api-docs-completion.md` §4a, канал —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(существование/пути; поле-уровневая перевычитка — при заходе по
теме).

## Контекст

Exchange-specific контракт инфраструктуры OKX: фактические endpoint-URL
для REST и WS. Используется client-layer при сборке HTTP-клиента и
WS-соединений.
Конкретные REST endpoints (`/api/v5/...`) — в mapping-файлах
(`okx-order-mapping.md`, `okx-balance-mapping.md` и т.п.); здесь —
base URL.

> Альтернатива при размещении: объединить с
> `docs/integrations/okx/rules/ws-limits.md` в `connectivity.md`. Не
> выбрана, чтобы сохранить «один файл — одна забота» (география
> endpoint'ов — контракт инфраструктуры vs лимиты протокола —
> правило источника).

## Production

### EEA (по документации OKX)

- **REST:** `https://eea.okx.com`
- **WS public:** `wss://wseea.okx.com:8443/ws/v5/public`
- **WS private:** `wss://wseea.okx.com:8443/ws/v5/private`
- **WS business:** `wss://wseea.okx.com:8443/ws/v5/business`

### Глобальные (часто используются в практике)

- **REST:** `https://www.okx.com`
- **WS public:** `wss://ws.okx.com:8443/ws/v5/public`
- **WS private:** `wss://ws.okx.com:8443/ws/v5/private`
- **WS business:** `wss://ws.okx.com:8443/ws/v5/business`

## Demo trading

- **REST:** `https://eea.okx.com` (тот же, demo flag через header).
- **WS public/private/business:** `wss://wseeapap.okx.com:8443/ws/v5/...`.

**Header для demo (REST и WS login):** `x-simulated-trading: 1`. Без
него запросы будут уходить в production-окружение даже при demo-ключах
— возможна ошибка несоответствия окружения/ключа.

## Правило выбора

URL **выбирается по региону** доступа и наличию ключа (EEA vs
глобальные) в client-layer; жёстко не фиксируется в коде, конфигурируется
извне (Spring profile / config / Vault). Для production по умолчанию —
EEA-вариант; глобальные `www.okx.com`/`ws.okx.com` — fallback.

## WS-каналы

WS-каналы OKX (имена: `account`, `positions`, `orders`,
`balance_and_position`, `tickers`, `candle<bar>`, `instruments`,
`algo-orders`, `algo-advance` и др.) — полноценно не задокументированы
в текущем заходе (REST-only миграция). Список и контракты WS-каналов —
отдельный заход (см. OKX-Q4 в `.claude/work/questions/open-questions.md`).

REST `okx-*-mapping.md` указывают WS-альтернативу там, где она есть, но
не описывают её детально.
