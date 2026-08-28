# Интеграция OKX

## На какой вопрос отвечает этот файл

Какова специфика источника OKX при доукомплектации интеграционных
доков: где официальная документация, какие разделы REST API
релевантны, конвенции и известные ограничения площадки.

## Когда применять

Опора роли `integrator` (`.claude/agents/integrator.md`) при работе
с источником OKX по процессу
`.claude/processes/api-docs-completion.md`. Процесс — source-agnostic;
вся OKX-специфика — здесь.

## Входные точки официальной документации

- **Корень REST/WS-документации v5:** `https://www.okx.com/docs-v5/en/`
  — источник правды по контрактам, полям, лимитам, ACK-семантике,
  пагинации.
- **Лог изменений API:** `https://www.okx.com/docs-v5/log_en/` —
  «Upcoming Changes» + датированные записи (вкл. «Delisted endpoints
  from the document»); первый ориентир при подозрении на дрейф спеки.
- Command-relevant разделы документации: **«Order Book Trading →
  Trade / Algo Trading»** (ordinary order, algo order, fills,
  close-position) и **«Trading Account»** (balance, positions).

## Канал чтения офдока — самообслуживание

Обе страницы — **статический HTML** (Slate-разметка, ~5 МБ у корня):
контент целиком в выдаче, секции под якорями `h1/h2/h3` с `id`,
поля — HTML-таблицами. Канал: **сырой fetch + локальный
детерминированный парсинг** (например, `curl` с браузерным UA →
скрипт по якорям/таблицам → секции в temp), **без
WebFetch-суммаризатора** — суммаризатор на гигантской странице
конфабулирует, факты снимаются только с локально распарсенного
сырого HTML. okx.com-поиск — кросс-чек, не факт: индекс может
отдавать **устаревший контент**. Если конкретная страница всё же
упрётся в JS-only рендер — эскалировать с фактурой, не
конфабулировать.

Сырая выкачка — **временный рабочий материал прогона** (temp вне
репозитория), в репозиторий не коммитится; хранится только дистиллят
в доках (решение B —
`.claude/decisions/integrator-agent.md` §Канал и хранение).

**Самоподдержка актуальности:** перевыкачка + дифф против
манифеста/доков — при каждом заходе интегратора по источнику и по
явной задаче «актуализируй»; дрейф фактов — правками, дрейф выборов
— решением владельца в режиме автономии с фиксацией в дайджесте
(`.claude/work/decision-digest.md`). Дата последней сверки — в шапке
«Внешний источник правды» каждого контракт-дока (правило
`.claude/rules/external-source-sync.md`).

## Command-relevant разделы REST API

Соответствие команд (`docs/components/models/ServiceCommand.md`)
эндпоинтам OKX в скоупе шага 4 (write + 5 refresh). Подтверждено
сверкой с офдоком (последняя — 2026-06-11).

| Команда | Метод + endpoint | Permission |
|---|---|---|
| `SUBMIT_ORDER` | `POST /api/v5/trade/order` | Trade |
| `CANCEL_ORDER` | `POST /api/v5/trade/cancel-order` | Trade |
| `REFRESH_ORDER` | `GET /api/v5/trade/order` (+ `orders-pending`, `orders-history`, `orders-history-archive` — звенья evidence-cycle) | Read |
| `SUBMIT_ALGO_ORDER` | `POST /api/v5/trade/order-algo` | Trade |
| `CANCEL_ALGO_ORDER` | ветвление по семье (И-1 исход (а)): ordinary → `POST /api/v5/trade/cancel-algos`; advance/trailing → `cancel-advance-algos` (⚠ И-2: endpoint вне текущего офдока) | Trade |
| `REFRESH_ALGO_ORDER` | `GET /api/v5/trade/order-algo` (+ `orders-algo-pending`, `orders-algo-history`) | Read |
| `CLOSE_POSITION` | `POST /api/v5/trade/close-position` | Trade |
| `REFRESH_POSITION` | `GET /api/v5/account/positions` | Read |
| `REFRESH_BALANCE` | `GET /api/v5/account/balance` | Read |
| `REFRESH_FILLS` | `GET /api/v5/trade/fills` (+ `fills-history` — звено) | Read |

`CREATE_ORDER` / `CREATE_ALGO_ORDER` — внутренние (материализуют
сущность до отправки), биржевого endpoint не зовут; биржу зовёт
`SUBMIT_*`. `FINALIZE_DEAL_*` / `MARK_DEAL_*` / `EXECUTE_KILL_SWITCH`
биржевого endpoint напрямую не имеют (собираются из вышеперечисленных).
Амендных команд нет — ремоделирование идёт REPLACE-оркестрацией
существующих команд (`docs/rules/replace-not-amend.md`); биржевые
amend-эндпоинты задокументированы как поверхность, доменом не
используются.

## Конвенции источника

- **Числа — строками** во всех ответах (`px`, `sz`, `fee`, …); адаптер
  парсит в `BigDecimal`, пустая строка → `null`.
- **Время — Unix-миллисекунды строкой** (`cTime`, `uTime`, `ts`).
- **ACK поэлементно:** `sCode`/`sMsg` per `data[i]`; top-level
  `code`/`msg` — статус запроса. `sCode=0` ≠ runtime truth (см.
  `docs/rules/ack-not-runtime-truth.md`).
- **Пагинация — `after`/`before` по id-якорю** (не по времени):
  `ordId` / `algoId` / `billId` в зависимости от endpoint; `limit` ≤ 100.
- **Auth (private REST):** заголовки `OK-ACCESS-KEY`,
  `OK-ACCESS-SIGN`, `OK-ACCESS-TIMESTAMP`, `OK-ACCESS-PASSPHRASE`.
  Demo trading — заголовок `x-simulated-trading: 1`.
- **Две семьи algo и cancel-путь:** семьи — ordinary (trigger / oco
  / conditional; + новый `chase`) и advance (iceberg / `smart_iceberg`
  / twap / **trailing `move_order_stop`**). Принятое решение И-1
  (исход (а)) — `CANCEL_ALGO_ORDER` ветвится по семье. ⚠ Свежий
  офдок (2026-06-11): `cancel-advance-algos` **выведен из
  документации** (changelog 2025-04-24), норматив `cancel-algos`
  ограничения семьи не несёт, но SDK-пример страницы — несёт;
  конфликт поднят находкой **И-2** (runtime-подтверждение в demo
  trading) — `docs/integrations/okx/contracts/algo-order.md`.
  Amend advance-семьи биржей не поддерживается (находка **И-3**).

## Известные ограничения

- **Окна хранения истории:** `orders-history` — 7 дней,
  `orders-history-archive` — 3 месяца (отменённые без исполнений в
  `orders-history` ~2 часа); `fills` — 3 дня, `fills-history` — 3
  месяца, архив 3m+ — async-флоу (`fills-archive`, `OKX-Q2`, шаг 7);
  `orders-algo-history` — 3 месяца; закрытые позиции (`posId`) —
  ~30 дней.
- **Rate limits — per-endpoint**, не единый: например `trade/order`
  60 req/2s по User+Instrument, `account/positions` 10 req/2s,
  `order-algo` 20 req/2s. Точное значение — из раздела конкретного
  endpoint, не обобщать.
- **Сторонние списки эндпоинтов = скелет + кросс-чек, не факт.**
  SDK-клиенты (например `tiagosiebler/okx-api`
  `docs/endpointFunctionList.md`, ccxt, nautilus_trader) дают удобный
  скелет поверхности и второй голос для кросс-чека, но **источник
  правды — официальный док OKX**. В манифесте покрытия каждая строка
  несёт провенанс (`офдок` / `сторонний`); расхождение «сторонний vs
  офдок» — находка.

## Пометка

Скилл растёт по мере прогонов доукомплектации. Спекулятивно
разделы не достраиваются.

## Связи

- Роль — `.claude/agents/integrator.md`.
- Процесс — `.claude/processes/api-docs-completion.md`.
- Манифест покрытия поверхности OKX —
  `.claude/processes/api-docs-completion.md`.
- Интеграционные доки OKX — `docs/integrations/okx/`,
  `docs/models/integrations/okx/`.
