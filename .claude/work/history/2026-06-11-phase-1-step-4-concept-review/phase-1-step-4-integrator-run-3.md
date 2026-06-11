# Интегратор, прогон 3: докачка полей периметра офдок-grade

## На какой вопрос отвечает этот файл

Каков результат прогона 3 `integrator`: исполнение закрытого пакета
прогона 2 — канал чтения офдока самообслуживанием, поле-уровневая
докачка всех `пробел`-строк периметра, закрытие И-1 исходом (а),
проверка симметрии advance-семейства, разнос форвардов и отказов.

## Контекст

Все развилки прогона 2 закрыты в чате (см. `...-run-2.md` §Закрытие
пакета). Этот прогон — исполнение: докачка по новому каналу чтения +
фиксация решений. Ступень — Предложение: факты легли в доки
напрямую, выборы — на валидацию (ниже ровно один — И-2).

## Канал чтения — подтверждён практикой

`https://www.okx.com/docs-v5/en/` отдал **статический HTML 5.1 МБ**
сырым fetch'ем (curl, браузерный UA); локальный парсер (Python
stdlib, якоря `h1/h2/h3` + таблицы → markdown) извлёк **555 секций**
детерминированно. Гипотеза чата подтверждена: прежний диагноз «SPA
нечитаем» был свойством WebFetch-суммаризатора, не страницы.
Changelog `log_en/` — тоже статический (1.1 МБ). Сырая выкачка — в
temp, в репозиторий не коммитится (решение B). Канал и
самоподдержка актуальности зафиксированы: скилл `integration-okx`
§Канал чтения, процесс `api-docs-completion` §4a, decision
`integrator-agent` §Канал и хранение.

## Докачка: все `пробел`-строки периметра закрыты

Создано **18 контракт-доков** (`docs/integrations/okx/contracts/`):

- **Trade:** `batch-operations.md` (3 batch-эндпоинта),
  `cancel-all-after.md`, `order-precheck.md`,
  `account-rate-limit.md`.
- **Account:** `account-config.md` (config + set-position-mode +
  set-leverage + leverage-info), `max-size.md` (max-size +
  max-avail-size), `trade-fee.md`, `account-position-risk.md`,
  `position-tiers.md`.
- **Market Data:** `order-book.md` (books + books-full),
  `public-trades.md` (trades + history-trades), `index-data.md`
  (index-tickers + index-candles + history).
- **Public Data:** `mark-price.md` (mark-price + mark-price-candles
  + history), `price-limit.md`, `funding-rate.md` (rate + history),
  `open-interest.md`, `server-time.md`, `insurance-fund.md`.

Обновлено: `position.md` (+§История закрытых позиций — В-3),
`account-bills.md` (bills-archive сверен поле-уровнево; +deep-архив
с 2021 `bills-history-archive` — закрыл прежнее «глубже 3 месяцев
пути не зафиксировано»; +справочник bill types `account/subtypes`),
`algo-order.md` (И-1/И-2/И-3, симметрия advance), манифест
(статусы/провенанс/новые строки). Каждый контракт-док несёт шапку
«Внешний источник правды» (источник, процедура синка, дата сверки) —
правило `external-source-sync`; нативные модели
(`docs/models/integrations/okx/`) шапкой не покрывались — их сверка
идёт через контракты (кандидат следующего захода, если нужна).

Манифест: `пробел`-строк в периметре **не осталось** — статусы
`создан`/`обновлён`/`есть-док` с провенансом `офдок` либо явные
`вне-периметра`/`сознательно-вне` с причиной.

## И-1 — закрыт исходом (а); симметрия advance проверена

- **Решение (пользователь):** `CANCEL_ALGO_ORDER` ветвит cancel-путь
  по семье (ordinary → `cancel-algos`; advance/trailing →
  `cancel-advance-algos`). Основание — новый продуктовый факт:
  стратегия предусматривает trailing-защиту; зафиксирован в
  `Strategy.md` §TrailingSettings (там, где живёт защитная модель).
  Разнесено: `algo-order.md` §Ветвление, `mapping/AlgoOrder.md`
  §Семья algo, `CancelAlgoOrderExecutor.md`, скилл.
- **Симметрия по офдоку:** query-звенья (details / pending /
  history) advance-семью **видят** (`ordType` обеих семей; +новые
  типы `chase`, `smart_iceberg`) — evidence-cycle для trailing не
  ломается. **Amend — асимметрия:** только Stop/Trigger, advance не
  амендится (находка И-3; не достроено — паттерн cancel+place лишь
  упомянут, решение за шагом реализации).

## Находки прогона 3

| # | Находка | Статус |
|---|---|---|
| **И-2** | `cancel-advance-algos` **выведен из офдока** (changelog 2025-04-24: «Delisted endpoints from the document»); норматив `cancel-algos` ограничения семьи не несёт, но SDK-пример той же секции несёт («not including Iceberg/TWAP/Trailing»). Фактура прогона 2 («две семьи», офдок через okx.com-поиск) опиралась на устаревший индекс. Следствие для И-1(а): ветвление может вырождаться в один путь — либо advance-ветка живёт на изъятом из дока endpoint'е. | **Провалидировано (2026-06-11, принято без правки):** ветвление (а) стоит, advance-ветка помечена. Снятие — runtime-проверка в demo trading на `CODE` шага 4 (поставить + отменить `move_order_stop` через `cancel-algos`); **кредов demo trading пока нет — проверка ждёт их появления (за пользователем)**; до проверки документальная фактура прогона 3 принимается достоверной. |
| **И-3** | Advance-семья (вкл. standalone trailing) **не амендится** (`amend-algos`: «Support Stop order and Trigger order only»). | Факт в доках; следствие для `AMEND_ALGO_ORDER` по trailing — за шагом реализации. |
| Н-1 | `mass-cancel` — только MMP-ордера (Option, Portfolio Margin). | Манифест: `пробел` → `вне-периметра`. |
| Н-2 | `order-precheck` — только MCM/PM (`acctLv` 3/4). | Ограничение в доке и в форвард-заметке В-2 (шаг 5). |
| Н-3 | `position-tiers` — путь **`/api/v5/public/position-tiers`**, не `account/` (сторонний скелет ошибался). | Манифест поправлен; пометка «путь к подтверждению» снята. |
| Н-4 | Bills deep-архив с 02.2021 существует (`POST+GET /account/bills-history-archive`, поквартальный async-файл) + справочник `account/subtypes`. | `account-bills.md` дополнен; релевантно OKX-Q3/шагу 7. |
| Н-5 | `orders-algo-history.state`: `partially_failed` ушёл из офдока (осталось effective/canceled/order_failed). | Дрейф зафиксирован в `algo-order.md`; ⚠ resolver статусов держит `partially_failed` как unknown-ветку — поведения не меняет. |
| Н-6 | Новые ordType: `chase` (FUTURES/SWAP), `smart_iceberg`; приватный `GET /account/instruments`. | Зафиксировано в доках/манифесте; для command-layer не требуется. |

## Сводка манифеста после прогона 3

| Раздел | есть-док | создан/обновлён (прогон 3) | вне-/сознательно-вне |
|---|---|---|---|
| Trade | 13 | 5 доков (7 эндпоинтов) | 2 (mass-cancel, easy-convert) |
| Algo | 3 | 4 строки обновлены (И-1/И-2/И-3, history) | — |
| Account | 4 | 9 доков/строк (12 эндпоинтов) | 3 группы |
| Market Data | 4 | 4 дока (9 эндпоинтов) | 3 |
| Public Data | 1 | 8 доков (9 эндпоинтов) | 1 группа |
| Funding/Asset | — | — | сознательно-вне |
| WebSocket | (ws-limits) | — | сознательно-вне (OKX-Q4) |

## Открытое после прогона 3

Единственный пункт на валидацию был **И-2** — **провалидирован
пользователем 2026-06-11, принят без правки по существу**: ветвление
И-1(а) стоит, advance-ветка сохраняет пометку «endpoint вне текущего
офдока»; снятие И-2 — runtime-проверка в demo trading на `CODE`
шага 4. Оговорка: кредов demo trading у пользователя пока нет —
runtime-проверка ждёт их появления (креды — за пользователем); до
проверки документальная фактура прогона 3 принимается как
достоверная. Прочее — перенос фактов, закрыт в доках. Гейт `CODE`
шага 4 прогоном **не меняется** (он уже пройден; перевод — за
пользователем).
