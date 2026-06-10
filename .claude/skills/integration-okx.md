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
  «Upcoming Changes»; первый ориентир при подозрении на дрейф спеки.
- Command-relevant разделы документации: **«Order Book Trading →
  Trade»** (ordinary order, algo order, fills, close-position) и
  **«Trading Account»** (balance, positions).

## Command-relevant разделы REST API

Соответствие команд (`docs/components/models/ServiceCommand.md`)
эндпоинтам OKX в скоупе шага 4 (write + 5 refresh). Подтверждено
сверкой на первом прогоне (2026-06-11).

| Команда | Метод + endpoint | Permission |
|---|---|---|
| `SUBMIT_ORDER` | `POST /api/v5/trade/order` | Trade |
| `AMEND_ORDER` | `POST /api/v5/trade/amend-order` | Trade |
| `CANCEL_ORDER` | `POST /api/v5/trade/cancel-order` | Trade |
| `REFRESH_ORDER` | `GET /api/v5/trade/order` (+ `orders-pending`, `orders-history`, `orders-history-archive` — звенья evidence-cycle) | Read |
| `SUBMIT_ALGO_ORDER` | `POST /api/v5/trade/order-algo` | Trade |
| `AMEND_ALGO_ORDER` | `POST /api/v5/trade/amend-algos` | Trade |
| `CANCEL_ALGO_ORDER` | `POST /api/v5/trade/cancel-algos` ⚠ см. «две семьи algo-cancel» | Trade |
| `REFRESH_ALGO_ORDER` | `GET /api/v5/trade/order-algo` (+ `orders-algo-pending`, `orders-algo-history`) | Read |
| `CLOSE_POSITION` | `POST /api/v5/trade/close-position` | Trade |
| `REFRESH_POSITION` | `GET /api/v5/account/positions` | Read |
| `REFRESH_BALANCE` | `GET /api/v5/account/balance` | Read |
| `REFRESH_FILLS` | `GET /api/v5/trade/fills` (+ `fills-history` — звено) | Read |

`CREATE_ORDER` / `CREATE_ALGO_ORDER` — внутренние (материализуют
сущность до отправки), биржевого endpoint не зовут; биржу зовёт
`SUBMIT_*`. `FINALIZE_DEAL_*` / `MARK_DEAL_*` / `EXECUTE_KILL_SWITCH`
биржевого endpoint напрямую не имеют (собираются из вышеперечисленных).

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
- **Две семьи algo-cancel:** `cancel-algos` — для ordinary algo
  (trigger / oco / conditional); `cancel-advance-algos` — для advance
  algo (iceberg / twap / **trailing `move_order_stop`**). Это
  корпусно подтверждённое различие сторонними клиентами; точную
  принадлежность `move_order_stop` подтверждать официальным доком
  (см. «Известные ограничения» про дрейф и SPA).

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
- **⚠ Официальный док OKX — JS-SPA.** Наивный `WebFetch` корня
  `docs-v5/en/` рендерит ненадёжно: суммаризатор **конфабулирует**
  (на первом прогоне вернул несуществующие пути
  `amend-order-algo` / `cancel-order-algo` вместо реальных
  `amend-algos` / `cancel-algos`). Поэтому: фактические пути/поля
  **кросс-чекать** (WebSearch, поддерживаемые SDK-списки эндпоинтов,
  несколько источников); факт, который пишется в док, подтверждать
  официальным доком, а при невозможности чистого чтения SPA —
  помечать находкой «требует подтверждения официальным доком», не
  фиксировать как факт.
- **Сторонние списки эндпоинтов = скелет + кросс-чек, не факт.**
  SDK-клиенты (например `tiagosiebler/okx-api`
  `docs/endpointFunctionList.md`, ccxt, nautilus_trader) дают удобный
  скелет поверхности и второй голос для кросс-чека, но **источник
  правды — официальный док OKX**. В манифесте покрытия каждая строка
  несёт провенанс (`офдок` / `сторонний`); расхождение «сторонний vs
  офдок» — находка.

## Пометка

Скилл наполнен на первом прогоне доукомплектации OKX (2026-06-11);
далее растёт по мере прогонов. Спекулятивно разделы не достраиваются.

## Связи

- Роль — `.claude/agents/integrator.md`.
- Процесс — `.claude/processes/api-docs-completion.md`.
- Манифест покрытия поверхности OKX —
  `docs/integrations/okx/coverage-manifest.md`.
- Отчёты прогонов —
  `.claude/work/progress/phase-1-step-4-integrator-run-1.md`,
  `.claude/work/progress/phase-1-step-4-integrator-run-2.md`.
- Интеграционные доки OKX — `docs/integrations/okx/`,
  `docs/models/integrations/okx/`.
