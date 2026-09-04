# OKX: манифест покрытия поверхности API

## На какой вопрос отвечает этот файл

Какова полнота покрытия поверхности OKX REST API нашими
интеграционными доками — что задокументировано, что пробел, что вне
продуктового периметра.

## Почему манифест здесь, а не в `.claude/processes/`

Манифест — не описание процесса, а **состояние покрытия поверхности
источника**: строки эндпоинтов со статусом дока и колонкой покрытия
контуром. Лежал он в `.claude/processes/`, чей вопрос — «как устроен
методологический процесс», и в `.claude/knowledge-tree.md` не значился
вовсе; шесть живых носителей при этом адресовали его как
`docs/integrations/{name}/coverage-manifest.md` — файла по такому адресу
никогда не было (F10 `DOCS_CHECK_27`).

Дом выбран по **принципу**, а не по букве строки таблицы
(`.claude/rules/structure.md` §Принципы: «тип знания определяется
вопросом, на который оно отвечает»). Вопрос манифеста — «что из
поверхности источника покрыто нашей проверкой и нашими доками», то есть
знание **проверочной деятельности**, а не продуктовое знание об
источнике; потребители — план и реестр предусловий, лежащие здесь же.
Тот же довод переселил сюда реестр предусловий
(`code-preconditions.md` §«Почему реестр здесь, а не в `docs/`»), и
манифест — тот же жанр. Возврат в `docs/` отвергнут: он воспроизводит
ошибку размещения, которую переписывание корпуса как раз вычищало.

**Оговорка о букве правила.** Комментарий строки
`.claude/tests/{testType}/{source}/` в `structure.md` пока перечисляет
только «тест-планы и библиотеку кейсов» — формулировка у́же
фактического содержимого каталога: в нём лежат ещё реестр предусловий и
этот манифест. Расширение предложено дословно в
`.claude/work/history/2026-09-03-phase-1-step-7-deals-and-pnl/phase-1-step-7-gaps-close-27/node-H8.md`
§«Предлагаемая клауза в `structure.md`»; до его внесения размещение
опирается на §Принципы, а не на этот комментарий (B4 мини-петли критики
`GAPS_CLOSE_27`: обоснование не должно ссылаться на строку, которая
файла не описывает).

## Внешний источник правды

Карта строится по официальному доку OKX
(`https://www.okx.com/docs-v5/en/`; changelog —
`https://www.okx.com/docs-v5/log_en/`). Синхронизация — перевыкачка
+ дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md`,
канал чтения — `.claude/skills/integration-okx.md`). Последняя
сверка: 2026-06-11 (прогон 3 — поле-уровневая докачка периметра).

## Назначение

Полная карта поверхности OKX v5 REST API по разделам. Каждая строка
несёт **статус** дока, **покрытие** контуром и **провенанс** факта.
Ведётся по процессу `.claude/processes/api-docs-completion.md` (владелец
— `integrator`); колонку покрытия ведёт `tester`
(`.claude/processes/source-api-testing.md`).

**Уровень клейма полноты назван.** «Полнота по манифесту» означает: ни
одна **операция** поверхности не осталась без строки и без метки —
раздел либо покрыт, либо явно вне периметра / отложен с причиной.
Клеймом «каждый **кейс** прогнан» это не является и им не подменяется:
покейсная полнота живёт в `plan.md`, а полнота гейтящих слотов — в
`code-preconditions.md`. Раздел без таблицы — дефект манифеста, а не
«нечего покрывать»: он читается как полнота и ею не является (F5
`DOCS_CHECK_28`).

### Легенда

**Статус:** `есть-док` (задокументирован) · `обновлён` / `создан` (в
этом прогоне — прогон 3, 2026-06-11) · `пробел` (в периметре, ещё не
задокументирован) · `вне-периметра` (док не заводим, с причиной) ·
`сознательно-вне` (в периметре, отложено решением, с якорем).

Дата сверки в шапке — дата **полного** свипа поверхности (прогон 3). Точечные
заходы по теме её не двигают и помечаются в примечании своей строки: заход шага 7 (**2026-07-14**, пробел H1 — fee-wiring) поле-уровнево
сверил строки `Fee rates` и `Instruments`, полного свипа не делал.

**Провенанс:** `офдок` — подтверждено официальным источником OKX
(прямое чтение docs-v5 / changelog); `сторонний` — пока только из
скелета/стороннего источника, официальным доком не подтверждено;
`рантайм` (`подтверждён-прогоном`) — подтверждено живым прогоном контура
тестов API источника, **в том числе против офдока** (C3,
`.claude/decisions/source-api-target-rebase.md`). Рантайм-факт против
офдока фиксируется этим провенансом, не выдаёт себя за офдок и не
теряется (канон — `cancel-advance-algos` жив на demo вопреки офдоку
2025-04-24).

**Покрытие** — метка стадии покрытия строки контуром тестов API
источника (через единственный generic-эндпоинт
`POST /api/proxy/okx/raw`): `🔴 не в плане` · `🟡 в плане` ·
`🟢 в коде` · `⚪ не-runtime` · `—` вне периметра. Значения метки, её
**гранулярность** (строка = операция, не кейс) и **правило отката**
`🟢 → 🟡` — дом: `.claude/processes/source-api-testing.md` §«Колонка
покрытия в манифесте»; здесь метки только проставлены. Покейсное
покрытие читается в `plan.md`, полнота гейтящих слотов — в
`code-preconditions.md`.

**Счёт (пересобран из таблиц ниже, 2026-08-31).** In-perimeter строк —
**60**: `🟢 в коде` — **53**, `🟡 в плане` — **5**, `⚪ не-runtime` —
**2** (`Fills 3d`, `Fills 3m`); `🔴` — нет. Вне периметра —
**10** строк с `—`. Пять `🟡` — ровно те пять методов плана, у
которых есть **непокрытые** кейсы (7 кейсов; из них гейтящих `CODE` —
ни одного: все гейтящие слоты реестра предусловий носители имеют,
состояние наблюдения читается колонкой реестра).

Прежний счёт (девять `🟡`, 16 кейсов) не пересобирался после того, как
код-тесты были дописаны, и метки четырёх строк отстали от фактики. Сама
проверочная команда при этом была слепа в двух местах, и оба исправлены
здесь же: она засчитывала носителем упоминание кейса **в комментарии**
(javadoc базового класса) и не видела делегирования цепочке, записанного
формой «Покрыт**ы** цепочкой» — множественное число мимо шаблона
`^Покрыт ` — и в заголовке, объявляющем **два** кейса сразу.

Счёт **самой колонки** пересобирается из неё же:

```bash
awk -F'|' '/^\| / && NF>=7 { v=$5; gsub(/^[ \t]+|[ \t]+$/,"",v)
    if (v!="Покрытие") c[v]++ } END { for (k in c) printf "%-16s %d\n", k, c[k] }' \
  .claude/tests/source-api/okx/coverage-manifest.md
```

Эта команда проверяет **арифметику счёта, а не правильность меток** — она
читает ту же колонку, из которой счёт выведен. Нагруженную половину
клейма («🟡 стоят ровно на строках с непокрытыми кейсами») проверяет
**независимый** от колонки перечень — план против каталога код-тестов, с
обеими формами покрытия (свой код-тест **или** делегирование цепочке):

```bash
P=.claude/tests/source-api/okx/plan.md
T=donor/src/test/java/com/example/tradingbot/integration/sourceapi/okx/
for c in $(grep '^### ' "$P" | grep -oE '[A-Za-z]+[0-9]+[.][0-9]+' | sort -u); do
  re="(^|[^0-9A-Za-z.])${c//./\\.}([^0-9A-Za-z.]|\\.[^0-9A-Za-z]|\\.$|$)"
  # носитель — ИСПОЛНИМАЯ строка: комментарии вычищаются ИЗ строк до грепа
  # (trailing-//, однострочный /*...*/, открывающий /*, продолжения javadoc)
  # — тот же фильтр, что у оси 3 tools/preconditions-check.sh; прежняя форма
  # отбрасывала только строки, НАЧИНАЮЩИЕСЯ с маркера (F-1 DOCS_CHECK_33)
  found=""
  for f in $(grep -rlE -- "$re" "$T" 2>/dev/null); do
    sed -e 's@//.*$@@' -e 's@/\*.*\*/@@g' -e 's@/\*.*$@@' \
        -e 's@^[[:space:]]*\*.*@@' "$f" | grep -qE -- "$re" && { found=1; break; }
  done
  [ -n "$found" ] && continue
  # делегирование ищется по ЗАГОЛОВКУ, содержащему кейс (заголовок объявляет
  # два кейса сразу), и по любой форме слова «Покрыт»
  awk -v id="$c" 'index($0,"### ")==1 && index($0,id)>0 {f=1; next} /^### |^## /{if(f)exit} f' "$P" \
    | grep -qE '^Покрыт' && continue                      # делегирован цепочке
  echo "$c"
done
```

→ **7** кейсов (`AG12.4`, `AG12.5`, `AG3.5`, `AG3.6`, `M15.7`, `M1.7`,
`MG9.5`); их `##`-методы — `M1`, `M15`, `AG3`, `AG12`, `MG9`: **пять**, и
ровно они несут `🟡`. Без второй половины (делегирование) проверка даёт
больше и даёт ложные откаты — `M18.3`, `M20.2`, `M21.2`, `M7.3`, `M7.4`,
`M13.3`, `M13.4` покрыты цепочками `Cmarket` / `Climit` / `M19*`
(поймано мини-петлёй критики `GAPS_CLOSE_28` и прогоном `_32`).

**Граница кейса — та же, что у проверки реестра.** Кейс ищется как
идентификатор, а не как регулярка: точка экранируется, границы стоят с
**обеих** сторон. Форма взята дословно из `tools/preconditions-check.sh`;
там же она доказана падающими пробами на обе границы. Ослабленная правая
граница, стоявшая здесь прежде, засчитала бы `AG6.1b` носителем кейса
`AG6.1` — непокрытый кейс читался бы покрытым.

**Что команда НЕ проверяет:** что названные в `Покрыт …` звенья сами
покрыты код-тестами. Механически это не выражается (план и тест пишут
звено по-разному), и половина остаётся предметом ревью — уровень назван
в доме, `.claude/processes/source-api-testing.md` §«Уровень механической
проверки клаузы (б) назван».

**Что было не так до 2026-08-30** (и почему счёт назван датой): при
переезде манифеста из `docs/integrations/okx/` пропали **две таблицы
целиком** — `Trade` (18 строк) и `Public Data` (10 строк) — и абзац
легенды про эту колонку. Заголовки разделов остались пустыми, а из
оставшихся 35 строк **все** несли `🟢`; файл при этом продолжал клеймить
«пустых строк нет». План переписывания корпуса
(`.claude/work/history/2026-09-03-phase-1-step-7-deals-and-pnl/corpus-rewrite-mapping.md`) снятия таблиц не
предусматривал — уходить должны были процессные ссылки и снятые
редакции. Таблицы восстановлены из `git show ea20cbd^` и пересверены с
планом; метка приведена к правилу отката.

### Канал чтения офдока

Источниковый дефицит прогона 2 («SPA нечитаем») закрыт
самообслуживанием: страница — статический HTML, канал — сырой fetch
+ локальный парсинг (см. `.claude/skills/integration-okx.md`
чтения, `.claude/decisions/integrator-agent.md` и хранение).
Строки `пробел` периметра докачаны до поле-уровневых доков в
прогоне 3.

## Продуктовый периметр

- **В периметре:** Trade, Algo Trading, Account, Market Data, Public
  Data; Funding/Asset — **по потребности**.
- **Вне периметра:** Sub-account, Grid, Recurring buy, Copy Trading,
  Spread/Block Trading (RFQ), Broker, Earn/Finance/Staking/Savings,
  Convert, Fiat/P2P, Affiliate, Status — не нужны алготрейдинг-боту
  фазы 1.
- **WebSocket** — `сознательно-вне`, якорь **OKX-Q4** (рубеж).

## Trade (`/api/v5/trade/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Place order | POST `/trade/order` | есть-док | 🟢 в коде | офдок | `contracts/order.md`, `OrderOkxResponse`; `placeOrder` (limit/market) |
| Place batch orders | POST `/trade/batch-orders` | **создан** | 🟢 в коде | офдок | `batch-operations.md`; до 20, лимит считается ордерами; **В-4 рассмотрено, не берём** — метода клиента нет |
| Cancel order | POST `/trade/cancel-order` | есть-док | 🟢 в коде | офдок | `order.md`; `cancelOrder`. Покрытие: `M17.5` несёт код-тест `M17CancelOrderLiveTest.m17_5_attachedProtectionOnParentCancel`; состояние наблюдения слота — колонка реестра (`code-preconditions.md`, п. 17) |
| Cancel batch orders | POST `/trade/cancel-batch-orders` | **создан** | 🟢 в коде | офдок | `batch-operations.md`; В-4 — метода клиента нет |
| Amend order | POST `/trade/amend-order` | есть-док | 🟢 в коде | офдок | `order.md`; доменом не используется — REPLACE-only (`replace-not-amend`); метода клиента нет |
| Amend batch orders | POST `/trade/amend-batch-orders` | **создан** | 🟢 в коде | офдок | `batch-operations.md`; В-4 — метода клиента нет |
| Close position | POST `/trade/close-position` | есть-док | 🟢 в коде | офдок | `position.md`; `closePosition` |
| Order details | GET `/trade/order` | есть-док | 🟢 в коде | офдок | `order.md`; `getOrder` |
| Pending orders | GET `/trade/orders-pending` | есть-док | 🟢 в коде | офдок | `order.md` (звено evidence-cycle); `getPendingOrders` |
| Order history 7d | GET `/trade/orders-history` | есть-док | 🟢 в коде | офдок | `order.md`; `getOrderHistory` |
| Order history 3m | GET `/trade/orders-history-archive` | есть-док | 🟢 в коде | офдок | `order.md`; архив 3м, метода клиента нет |
| Fills 3d | GET `/trade/fills` | есть-док | ⚪ не-runtime | офдок | `fills.md`, `FillOkxResponse`; **`REFRESH_FILLS` снимается** на `CODE` шага 7 (`pnl-finalization-mechanics.md`; в коде команда пока жива) ⇒ эндпоинт в целевом runtime фазы 1 не используется (order-fill-метрики из `OrderOkxResponse`); контракт справочно |
| Fills 3m | GET `/trade/fills-history` | есть-док | ⚪ не-runtime | офдок | `fills.md`; **не используется** в целевом runtime (снятие `REFRESH_FILLS` — на `CODE` шага 7); справочно |
| Mass cancel | POST `/trade/mass-cancel` | **вне-периметра** | — | офдок | прогон 3: только MMP-ордера, Option в Portfolio Margin — не кейс SWAP-бота (прежний статус `пробел` снят) |
| Cancel All After (DMS) | POST `/trade/cancel-all-after` | **создан** | 🟢 в коде | офдок | `cancel-all-after.md`; **В-1** → шаг 8 (safety); метода клиента нет |
| Order precheck | POST `/trade/order-precheck` | **создан** | 🟢 в коде | офдок | `order-precheck.md`; **В-2** → шаг 5; ⚠ только acctLv 3/4 (MCM/PM); метода клиента нет |
| Account rate limit | GET `/trade/account-rate-limit` | **создан** | 🟢 в коде | офдок | `account-rate-limit.md`; fill-ratio-based лимит; метода клиента нет |
| Easy convert / one-click repay | GET/POST `/trade/easy-convert*`, `/one-click-repay*` | вне-периметра | — | офдок | конвертация/репэй, не торговый цикл |

## Algo Trading (`/api/v5/trade/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Place algo order | POST `/trade/order-algo` | есть-док | 🟢 в коде | офдок | `algo-order.md`; ordType: conditional/oco/trigger/`chase`(новый)/move_order_stop/iceberg/`smart_iceberg`/twap; `placeAlgoOrder` строит conditional/oco/move_order_stop (вариант-gap: trailing-value `callbackSpread`) |
| Cancel algo (ordinary) | POST `/trade/cancel-algos` | **обновлён** | 🟢 в коде | офдок | `algo-order.md`; **И-1 закрыт (а)** — ветвление по семье; `cancelAlgos`|
| Cancel advance algo | POST `/trade/cancel-advance-algos` | **обновлён** | 🟢 в коде | офдок | **И-2:** выведен из офдока (changelog 2025-04-24); advance-ветка И-1(а) — runtime-подтверждение; `cancelAdvanceAlgos`|
| Amend algo | POST `/trade/amend-algos` | **обновлён** | 🟢 в коде | офдок | только Stop/Trigger; advance не амендится — **И-3** (следствие закрыто: REPLACE-only); доменом не используется; метода клиента нет |
| Algo details | GET `/trade/order-algo` | есть-док | 🟢 в коде | офдок | `algo-order.md`; обе семьи видны; `getAlgoOrder` |
| Algo pending | GET `/trade/orders-algo-pending` | есть-док | 🟢 в коде | офдок | `algo-order.md` (звено); ordType обеих семей; `getPendingAlgoOrders` |
| Algo history 3m | GET `/trade/orders-algo-history` | **обновлён** | 🟡 в плане | офдок | `state`: effective/canceled/order_failed (дрейф: `partially_failed` ушёл из офдока); `getAlgoOrderHistory`. Откат 🟢→🟡: кейс `M15.7` (семантика `actualPx`) не покрыт: ни код-теста, ни делегирования цепочке |

## Account (`/api/v5/account/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Get balance | GET `/account/balance` | есть-док | 🟢 в коде | офдок | `balance.md`, `BalanceOkxResponse`; `getBalance` |
| Get positions | GET `/account/positions` | есть-док | 🟢 в коде | офдок | `position.md`, `PositionOkxResponse`; `getPositions` |
| Positions history | GET `/account/positions-history` | **обновлён** | 🟢 в коде | офдок | `position.md`; **В-3 закрыт**: источник числа `resultProfit` (net `realizedPnl`); native `PositionsHistoryOkxResponse`, снапшот `PositionCloseResult`; **:** добывается **второй ногой `REFRESH_POSITION_COMMAND`** (evidence-cycle live → history), результат — поля положения закрытия на `Position` (отдельной команды `REFRESH_POSITIONS_HISTORY` нет); инвариант агрегации — рантайм-верификация (N11, `.claude/tests/source-api/okx/plan.md`); пагинация по `uTime`; метода клиента нет. Покрытие: все девять кейсов `AG1.*` несут код-тесты (`Ag1PositionsHistoryLiveTest`, `Ag1DealFixtureLiveTest`, `Ag1FundingHorizonLiveTest`); пункт следа автоделевериджа **выведен из-под гейта** 2026-08-30 (`.claude/decisions/unorderable-fact-substitutes.md`) |
| Account & position risk | GET `/account/account-position-risk` | **создан** | 🟢 в коде | офдок | `account-position-risk.md`; единый временной срез; метода клиента нет |
| Bills 7d | GET `/account/bills` | есть-док | 🟡 в плане | офдок | `account-bills.md`, `AccountBillOkxResponse`; **:** команда **`REFRESH_BILLS_COMMAND`** → `DealCashFlow` (разбивка P&L; `DealCashFlow.md`), линковка по **окну + `instId`**, дедуп по паре (`exchangeId`, `billId`); метода клиента нет. Откат 🟢→🟡: два непокрытых кейса — `AG3.5` и `AG3.6` (`AG3.4` покрыт `Ag1DealFixtureLiveTest.ag3_4_feeCurrency`; `AG3.6` тест-метода не имеет — единственное упоминание в javadoc базового класса, носителем оно не является) |
| Bills archive 3m | GET `/account/bills-archive` | **обновлён** | 🟢 в коде | офдок | `account-bills.md`; поле-уровнево сверен (прогон 3); метода клиента нет |
| Bills deep-архив (с 2021) | POST+GET `/account/bills-history-archive` | **создан** | 🟢 в коде | офдок | `account-bills.md`; поквартально, async-файл; 12 заявок/сутки; метода клиента нет. **Success-контракт на demo неверифицируем** (заявка → `50026`, GET → `51604`): прямой кейс проверяется **на проде ад-хок, вне контура** — зелёный контур-тест подтверждает только demo-реджект, не success |
| Bill types | GET `/account/subtypes` | **создан** | 🟢 в коде | офдок | `account-bills.md` bill types; метода клиента нет. Покрытие: оба кейса несут код-тесты (`Ag6BillSubtypesLiveTest.ag6_1_directDictionary`, `Ag1DealFixtureLiveTest.ag6_2_typesOutsideDealEconomics`); оба пишут исход-перечень персистентно, состояние наблюдения слотов — колонка реестра (`code-preconditions.md`, пп. 2, 16) |
| Account config | GET `/account/config` | **создан** | 🟢 в коде | офдок | `account-config.md`; **В-9** → шаг 5 / bootstrap; `getAccountConfig` (диагностический сырой String) |
| Set position mode | POST `/account/set-position-mode` | **создан** | 🟢 в коде | офдок | `account-config.md`; метода клиента нет |
| Set leverage | POST `/account/set-leverage` | **создан** | 🟢 в коде | офдок | `account-config.md`; INSTR-Q2; метода клиента нет |
| Leverage info | GET `/account/leverage-info` | **создан** | 🟢 в коде | офдок | `account-config.md`; метода клиента нет |
| Max order size | GET `/account/max-size` | **создан** | 🟢 в коде | офдок | `max-size.md`; метода клиента нет |
| Max avail size | GET `/account/max-avail-size` | **создан** | 🟢 в коде | офдок | `max-size.md`; метода клиента нет |
| Fee rates | GET `/account/trade-fee` | **обновлён** | 🟡 в плане | офдок | `trade-fee.md`; **В-7 активирован** (G6): ставка прогнозной комиссии в риск-сайзинге; ** (H1, сверка 2026-07-14):** native `TradeFeeOkxResponse` создан, дом ставки — **`TradeFeeRate`** (отдельная модель, строка на группу; на навесе остался лишь ключ `externalFeeGroupId`), ось запроса — группа (`instType`), резолв — пара (`instType`,`groupId`), перечень групп не хардкодим; дочитывает `InstrumentExternalRulesSyncJob`; **:** ось резолва — **сырая** пара (`externalInstrumentType`,`externalFeeGroupId`), не доменный enum (H7); поверхность чтения (аксессор `takerFeeRate()`) не двинулась, троп чтения навеса две, гидрирует хранилищный слой `InstrumentExternalRulesDataService` (H1); контур — **SWAP-only**, один вызов `instType=SWAP` (H8); `level` — часть значения группы (смена → новая строка, H11); знак источника (минус = комиссия) **снимается при маппинге** `× −1`, ниже маппинга ставка — издержка (H2, `mapping/TradeFeeRate.md`); несвежесть → холд **инструментов группы**, не биржи (H3/H4); **:** реакция на несвежесть — **мягкая**, kill-switch снят, живые сделки доживают (H2); радиус — по режиму отказа, «синк выключен» не источник холда (H4); **:** снятие мягкого холда — **вручную** (H2), enforcement — отдельный статус `Instrument.Status.ENTRY_BLOCKED` (H3), свежесть меряется у **обеих** половин резолва — значения ставки и ключа группы на навесе (H9); дрейф офдока: `instType` включает EVENTS, поле `settle` (EVENTS-only), «Open API will not reflect zero-fee trading», инвариант organic-base-rates; upcoming `elpMaker`→`rpiMaker` (прод 2026-07-28) — unused, не гейтит; wiring — шаг 7 CODE. Откат 🟢→🟡: кейсы `AG12.4` и `AG12.5` не покрыты: ни код-тестов, ни делегирования цепочке |
| Instruments (private) | GET `/account/instruments` | вне-периметра | — | офдок | инвентарь с учётом режима счёта; используем публичный `public/instruments` |
| Interest / borrow-repay / VIP loan / spot-margin | various | вне-периметра | — | офдок | margin/loan вне скоупа SWAP-бота фазы 1 |
| Greeks / isolated-mode / MMP / move-positions / collateral / account-mode-switch / прочее сервисное | various | вне-периметра | — | офдок | опционы / PM-сервис / переносы — вне торгового цикла фазы 1 |

## Market Data (`/api/v5/market/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Tickers | GET `/market/tickers` | есть-док | 🟢 в коде | офдок | `market-price-data.md`; клиент строит только одиночный `getTicker`, плюрал-эндпоинта нет |
| Ticker | GET `/market/ticker` | есть-док | 🟢 в коде | офдок | `market-price-data.md`, `TickerOkxResponse`; `getTicker`. Носителем курса cross-ccy **не является** выбрал свечу на момент операции: единичный тик наименее робастен, а `appliedRate` фиксируется однократно и навсегда входит в число |
| Candles | GET `/market/candles` | есть-док | 🟢 в коде | офдок | `candle.md`, `CandleOkxResponse`; `getLatestCandles` |
| History candles | GET `/market/history-candles` | есть-док | 🟢 в коде | офдок | `candle.md`; `getHistoryCandles` |
| Order book | GET `/market/books` | **создан** | 🟢 в коде | офдок | `order-book.md`; ≤ 400 уровней; фазе 1 не нужен (стратегия на свечах); метода клиента нет |
| Order book full | GET `/market/books-full` | **создан** | 🟢 в коде | офдок | `order-book.md`; ≤ 5000 уровней; метода клиента нет |
| Public trades | GET `/market/trades` | **создан** | 🟢 в коде | офдок | `public-trades.md`; ≤ 500; метода клиента нет |
| Trades history | GET `/market/history-trades` | **создан** | 🟢 в коде | офдок | `public-trades.md`; 3 месяца; метода клиента нет |
| Index tickers | GET `/market/index-tickers` | **создан** | 🟢 в коде | офдок | `index-data.md`; метода клиента нет. Носителем курса cross-ccy не выбран (см. свечные строки ниже) |
| Index candles | GET `/market/index-candles` | **создан** | 🟢 в коде | офдок | `index-data.md`; 1440 точек; метода клиента нет. **Кандидат носителя курса cross-ccy**: курс берётся из свечи на момент операции, секундное разрешение при доступности. Индексная свеча не требует онбординга спота, но метод клиента пришлось бы строить. Покрытие: `MG7.5` несёт код-тест `Mg7IndexCandlesLiveTest`; состояние наблюдения слота — колонка реестра (`code-preconditions.md`, п. 5) |
| Index candles history | GET `/market/history-index-candles` | **создан** | 🟢 в коде | офдок | `index-data.md`; метода клиента нет. **Кандидат носителя курса cross-ccy** для операций за пределами окна свежих свечей — глубина хранения и доступность секундного разрешения проверяются прогоном. **Выбор носителя, разрешения и правила деградации — за `integrator`** (`docs/components/RefreshBillsExecutor.md`); после выбора строка операции заводится здесь |
| Mark price candles | GET `/market/mark-price-candles` | **создан** | 🟡 в плане | офдок | `mark-price.md`; релевантно `tpTriggerPxType=mark`; метода клиента нет. Откат 🟢→🟡: кейс `MG9.5` (базис `last` ↔ `mark`) не покрыт: ни код-теста, ни делегирования цепочке |
| Mark price candles history | GET `/market/history-mark-price-candles` | **создан** | 🟢 в коде | офдок | `mark-price.md`; метода клиента нет |
| Platform 24h volume | GET `/market/platform-24-volume` | вне-периметра | — | офдок | агрегат платформы |
| Option trades / call auction | various | вне-периметра | — | офдок | OPTION / аукцион — вне SWAP-скоупа |
| SBE Market Data | various | вне-периметра | — | офдок | бинарный фид (HFT) — вне скоупа |

## Public Data (`/api/v5/public/`)

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Instruments | GET `/public/instruments` | **обновлён** | 🟡 в плане | офдок | `instrument.md`, `InstrumentOkxResponse`; `getInstruments`; `groupId` переведён в used — ключ комиссионной группы (пара `instType`+`groupId` резолвит ставку `trade-fee`); прежде числился среди неиспользуемых. Откат 🟢→🟡: кейс `M1.7` (`groupId` непуст у наших SWAP-инструментов) не покрыт: ни код-теста, ни делегирования цепочке |
| Mark price | GET `/public/mark-price` | **создан** | 🟢 в коде | офдок | `mark-price.md`; **В-8** → шаг 5; метода клиента нет |
| Price limit | GET `/public/price-limit` | **создан** | 🟢 в коде | офдок | `price-limit.md`; **В-8** → шаг 5; метода клиента нет |
| Funding rate | GET `/public/funding-rate` | **создан** | 🟢 в коде | офдок | `funding-rate.md`; **В-6/OKX-Q3 разрешены**: funding в P&L — через bills + positions-history, не через ставки; интервал по `fundingTime`↔`nextFundingTime`; метода клиента нет |
| Funding rate history | GET `/public/funding-rate-history` | **создан** | 🟢 в коде | офдок | `funding-rate.md`; **не источник числа** `resultProfit` (funding — через bills/positions-history, `result-profit-source.md`); лишь прогноз/сверка; метода клиента нет |
| Open interest | GET `/public/open-interest` | **создан** | 🟢 в коде | офдок | `open-interest.md`; метода клиента нет |
| Position tiers | GET `/public/position-tiers` | **создан** | 🟢 в коде | офдок | `position-tiers.md`; **находка прогона 3:** путь public, не `account/` (сторонний скелет ошибался); метода клиента нет |
| Server time | GET `/public/time` | **создан** | 🟢 в коде | офдок | `server-time.md`; синхронизация подписи; **якорь верхней границы окна bills** — метод клиента заводится шагом 7 |
| Insurance fund | GET `/public/insurance-fund` | **создан** | 🟢 в коде | офдок | `insurance-fund.md` (офдок: «security fund»); метода клиента нет |
| Delivery/exercise, settlement, estimated price, underlying, discount-rate, premium history, exchange-rate, index-components, tick bands, series/events/markets (EVENTS), economic calendar, historical market data | various | вне-периметра | — | офдок | FUTURES delivery / OPTIONS / EVENTS / индекс-сервисы — вне скоупа SWAP |

## Funding / Asset (`/api/v5/asset/`) — по потребности

| Операция | Метод · путь | Статус | Покрытие | Провенанс | Примечание |
|---|---|---|---|---|---|
| Currencies / balances / transfer / transfer-state / asset-bills / deposit-* / withdrawal-* | various | сознательно-вне | — | сторонний | По потребности. Бот фазы 1 торгует на торговом счёте; фондовые переводы/пополнения/выводы не нужны. Заводить при появлении потребности. |

## Прочие разделы — вне периметра

`Sub-account`, `Grid Trading`, `Recurring buy`, `Copy Trading`,
`Spread Trading`, `Block Trading (RFQ)`, `Broker (ND/FD)`,
`Earn / Finance / Staking / Savings / On-chain`, `Convert`,
`Fiat / P2P`, `Affiliate`, `Status` — все `вне-периметра`
(`сторонний` скелет): не входят в продуктовый периметр алготрейдинг-бота
фазы 1. Доки не заводим.

## WebSocket — сознательно-вне

Все WS-каналы (public: `tickers`/`books`/`candles`/…; private:
`account`/`positions`/`orders`/`orders-algo`/`balance_and_position`;
trade: order ops по WS) — `сознательно-вне`, якорь **OKX-Q4** (рубеж).
Лимиты WS частично задокументированы — `rules/ws-limits.md`
(`есть-док`). Прогон 3: канал advance algo orders в офдоке
по-прежнему есть (семья advance жива в WS, при том что REST
`cancel-advance-algos` из дока выведен — контекст И-2).

## Инфраструктурные доки (вне разбивки по эндпоинтам)

`contracts/service-urls.md` (базовые URL/окружения; AWS-домены
выведены — changelog 2025-04-28), правила `rules/adapter-constants.md`,
`rules/timeframe-constants.md`, `rules/reduce-only-invariant.md`,
`contracts/fills-archive.md` + `FillsArchiveOkxResponse` — `есть-док`.

## Связи

- Процесс — `.claude/processes/api-docs-completion.md`.
- Скилл OKX (канал чтения, command-relevant разделы, конвенции,
  ограничения) — `.claude/skills/integration-okx.md`.
- Отчёт прогона 2 (скан + эскалация источникового дефицита) —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-integrator-run-2.md`.
- Отчёт прогона 3 (докачка офдок-grade, находки И-2/И-3) —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-integrator-run-3.md`.
- Контракты и нативные модели — `docs/integrations/okx/contracts/`,
  `docs/models/integrations/okx/`.
