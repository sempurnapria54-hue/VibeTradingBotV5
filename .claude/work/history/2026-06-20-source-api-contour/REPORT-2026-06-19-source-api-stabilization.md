# Отчёт: стабилизация контура source-api (OKX demo), 2026-06-19

## На какой вопрос отвечает этот файл

Что сделано в автономном цикле доведения контура тестов API источника
(source-api) до стабильного состояния: какие баги найдены и исправлены,
какие расхождения живого прогона с офдоком зафиксированы (C3), каков
итог.

## Среда (префлайт, проверено до прогона)

- **Vault** — unsealed (host :8200); test-токен резолвит оба пути:
  `secret/tradingbot/postgres-test` (DATASOURCE_*) и `…/okx-test`
  (OKX_API_KEY/SECRET_KEY/PASSPHRASE).
- **Postgres-test** — up (host :5441 → `tradingbot_test`), Flyway V1–V7 ок.
- **demo-OKX** — reachable; demo-аккаунт **чист и фондирован** (USDT
  avail ~5000, `posMode=net_mode`, `acctLv=2`).
- **Тулчейн** — JDK 25 (Corretto) + Maven 3.9.11 (в PATH не было —
  найдены в `~/.jdks` и `~/.m2/wrapper`).

## Цикл

compile → baseline live run → triage → fix (app + test) → targeted
re-run → full re-run до зелёного. Halt ни разу не сработал; demo-аккаунт
самоочищался (sweep) после write-цепочек.

## A. Баги app/infra контура (масштаб: весь baseline падал, 200 тестов)

В baseline-прогоне **всё** падало с каскадом из трёх взаимно
маскирующих багов. Все три — в CODE-поверхности контура (`/raw`-прокси +
тест-база), исправлены здесь.

| # | Где | Симптом | Корень | Фикс |
|---|---|---|---|---|
| A1 | `OkxRestClient.dispatch` / `OkxProxyController` / `OkxRawApiRequest` | `InvalidDefinitionException` на каждом success-ответе с данными → app 500 | Контур использовал Jackson-2 `com.fasterxml…JsonNode`, а веб- и RestClient-конвертеры SB4 — Jackson 3 (`tools.jackson`); Jackson 3 не строит Jackson-2 `JsonNode` (абстрактный тип) | Контурный `JsonNode` (тело запроса `/raw` + токен ответа) переведён на Jackson 3 `tools.jackson.databind.JsonNode` — сквозной round-trip через Jackson-3 стек |
| A2 | `OkxConfig` (оба RestClient) | OKX-реджекты параметров → app 500 вместо конверта | demo-OKX отдаёт **HTTP 4xx** (400) на ошибки параметров, не 200; дефолтный RestClient бросает `HttpClientErrorException` и тело-конверт теряется | no-op `defaultStatusHandler(HttpStatusCode::isError, …)` на обоих клиентах — тело `{code,msg,data}` декодируется, `/raw` отдаёт HTTP 200 с `code≠0` внутри |
| A3 | `OkxSourceApiLiveTestBase.isRateLimited` | `NullPointerException` на любом ответе без top-level `code` (наш 5xx) | `RATE_LIMIT_CODES` — immutable `Set.of(...)`, его `.contains(null)` бросает NPE | guard `nonNull(code)` перед `.contains` |

## B. Правки тестов под реальное поведение OKX (14 расхождений plan↔reality)

После A1–A3 baseline дал 200 тестов / 14 падений — все **assertion**
(не infra), т.е. ожидания плана vs живой OKX. Контур документирует
реальное поведение → правились тесты, не «чинился» OKX.

| Кейсы | Расхождение (реальный OKX) | Правка теста |
|---|---|---|
| M1.4 | несущ. `instId` на instruments → реджект **51001**, не пустой `data`+code0 | `assertRejectOrEmpty` |
| M15.1–3, M19.cond-sl | `orders-algo-history` требует `state`/`algoId` (иначе **50015**) — это уже было в `algo-order.md`, тест просто не слал `state` | добавлен `state=canceled` |
| MG8.3, MG10.3 | `after` из будущего → свежие свечи (size 100), не пустой `data` | принимаем непустой (как M4.3) |
| PG1.3, PG5.3 | `mark-price`/`open-interest` принимают `instId` без `instType` (code0) | `assertOk` (instType необязателен при instId) |
| AG3.3, PG8.4 | вне-доменный `type` **игнорируется** (code0 + данные), не реджект | `assertOk` + observe |
| TG8.2, PG7.2 | неизвестный путь → OKX **HTTP 404** с нестандартным телом (`code` число, `data` объект) → `/raw` отдаёт non-2xx | ассерт статус ≥400 (как M1.6) |
| AG5.1, AG5.2 | demo не инициирует архив: POST→**50026** «System error», GET→**51604** «initiate first» | принимаем эти demo-коды; эндпоинт достижим |

Дополнительно — **Tg3.1** (amend lifecycle): флака по времени. Amend
асинхронен (ACK ≠ runtime truth); отражение `px/sz` в `getOrder` на demo
иногда > дефолтных 25с. Фикс: per-case poll-таймаут 60с (план допускает
для «медленных» кейсов). Подтверждён повторным прогоном (14.6с).

## C. Находки C3 в апидоки (провенанс `рантайм`, дата 2026-06-19)

Зафиксированы в шапке «Внешний источник правды» / у параметра
затронутого дока:

- `contracts/mark-price.md`, `contracts/open-interest.md` — `instType`
  фактически **необязателен при заданном `instId`** (офдок помечает
  обязательным).
- `contracts/account-bills.md` — (1) вне-доменный фильтр `type`
  игнорируется; (2) demo bills-history-archive: POST→50026, GET→51604.
- `contracts/insurance-fund.md` — вне-доменный `type` игнорируется;
  подтверждены негативы 50014/50015.
- `contracts/candle.md` — `after` из будущего → свежие свечи (общее для
  market/history/index/mark-price candles).
- `contracts/instrument.md` — несущ. `instId` → реджект 51001.
- Кросс-режущая находка (OKX отдаёт **HTTP 4xx** на параметрические
  реджекты; `/raw` нормализует в 200-с-конвертом) — задокументирована в
  javadoc `OkxConfig` (авторитетное место поведения app).

## D. Известный баг вне этого цикла (не регрессия)

`ICredEmptyCredentialsLiveTest` штатно **наблюдает** открытый баг **§I3**:
при незаданных кредах подпись падает «голым» `NullPointerException`
(`OkxProperties.getSecret()` == null → `.getBytes()`), вместо понятного
«OKX credentials not configured». Тест зелёный (документирует баг). К
правке — отдельно (не входит в стабилизацию контура).

## Итог

- **Финальный полный прогон (full-04): 200 тестов, 0 падений, 0 ошибок,
  halt не сработал.** (Промежуточные: baseline — всё падало; после A1–A3
  + B — full-03 200/1 (флака Tg3); после Tg3-фикса — full-04 зелёный.)
- Все 60 in-perimeter эндпоинтов OKX отработали вживую против demo,
  включая write-цепочки (place/amend/cancel/close ордеров и algo,
  batch, account-write) с teardown и восстановлением состояния.
- demo-аккаунт после прогонов — чист (нет живых ордеров/позиций/algo).

## Квота AG5 (`bills-history-archive`, 12/сутки)

AG5.1 (валидный POST) гонялся в прогонах full-02 / validate-fixes /
full-03 / full-04; на demo всегда возвращает 50026 (system error —
вероятно не списывает слот обработки). Точечные перезапуски не
устраивали циклов по AG5.

## Артефакты

- Логи прогонов: `.claude/work/run-logs/{baseline-01,validate-reads-01,
  full-02,validate-fixes-01,full-03,validate-tg3-01,full-04}.log`.
- Раннер: `.claude/work/run-logs/run.sh` (JDK25+Maven+Vault-токен,
  `-Dgroups=source-api-live`).
- Сигнер demo-OKX (инспекция/sweep аккаунта, независим от app):
  `.claude/work/run-logs/okx_demo.py`.
