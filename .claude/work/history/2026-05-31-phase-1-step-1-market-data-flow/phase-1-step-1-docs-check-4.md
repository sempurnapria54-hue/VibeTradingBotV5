# DOCS_CHECK_4 — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

На каком шаге мы в четвёртой итерации проверки целостности концепции
доков под шаг 1 и каковы её результаты (gap-отчёт — финальная проверка
перед `CODE`).

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных (коннект к
  OKX, инструменты, цены/свечи, свежесть)».
- Под-шаг: `DOCS_CHECK_4` (четвёртая итерация),
  `.claude/processes/roadmap-step-execution.md`; стадийный обход
  `concept-review` (`.claude/skills/concept-review.md`), роль
  `reviewer`. Назначение прогона — **полный сквозной прогон концепции
  под код, финальная проверка перед `CODE`**.
- **Проверка — только по докам**: doc↔doc несогласованности,
  name-level пробелы, неотвеченные/отложенные вопросы. Код не
  читался, с кодом не сверялось.
- Вход: gap-отчёт `phase-1-step-1-docs-check-3.md` (Н(3-1), Н(3-2)) и
  `phase-1-step-1-gaps-close-3.md` (закрыты обе несогласованности +
  ревизия модели полей `Instrument`: snapshot↔domain шага 1 =
  идентичность + `externalStatus` + `externalLeverage`).
- **Единственное изменение с прошлой проверки — `GAPS_CLOSE_3`.**
  Свечная подсистема, процессы и компоненты в `GAPS_CLOSE_3` не
  затрагивались (подтверждено git stat коммита: правились только
  `Instrument` / `mapping/Instrument` / `mapping/InstrumentExternalRules` /
  `lifecycles/Instrument` / `raw-exchange-dto-boundary`, плюс на диске —
  `OkxInstrumentResponse` и `domain/other/InstrumentExternalRules`);
  фокусы 2-3 `DOCS_CHECK_3` (count/actual/density; candle-loading vs
  market-data-calculation) остались чистыми по построению.
- Особый фокус (что менялось в `GAPS_CLOSE_3`): граничная модель
  снапшота инструмента и её согласованность по всем затронутым докам.

## Охват

### Проверено (доки) — глубоко, особый фокус

Граничная модель снапшота инструмента, семь затронутых `GAPS_CLOSE_3`
доков:

- **Модель (domain/core):** `domain/core/Instrument.md`.
- **Mapping:** `mapping/Instrument.md`,
  `mapping/InstrumentExternalRules.md`.
- **Инвентарь источника (OKX):** `OkxInstrumentResponse.md`.
- **Сквозное правило:** `rules/raw-exchange-dto-boundary.md`.
- **Lifecycle:** `lifecycles/Instrument.md`.
- **Отложенная rules-модель:** `domain/other/InstrumentExternalRules.md`.

### Проверено (доки) — кросс-скан на дрейф

- `InstrumentExternalSnapshot` встречается **только** в семи фокус-доках
  — ни одного упоминания граничного снапшота вне затронутого набора.
- Старая формулировка «snapshot↔domain шага 1 = только идентичность»
  в докаx **не осталась** (нулевой результат поиска); оставшиеся
  вхождения `snapshot↔domain` в `Instrument.md` — новая формулировка
  («идентичность + `externalStatus` + `externalLeverage`»).
- `externalStatus`/`externalLeverage`/`externalMaxLeverage`/`externalState`
  вне фокус-доков встречаются у **другой сущности** (`Order`/`AlgoOrder`
  FSM — их собственный `externalStatus`, резолвится `external-status-resolution.md`)
  и в отложенном rules-downstream (`trading-constraints.md` — лимит
  плеча через `externalMaxLeverage`). Оба — не шаг-1-дом инструмента,
  дрейфа нет.
- Кросс-ссылка `mapping/Instrument.md` →
  `docs/integrations/okx/contracts/instrument.md` резолвится (файл есть).
- Процесс `candle-loading.md` (не правился) описывает онбординг
  `CREATED → SYNC → CANDLES_LOADING → ACTIVE` на процессном уровне и
  обогащённому `SYNC` (биржевые поля) **не противоречит** — детализация
  полей делегирована lifecycle/mapping.

### Вне охвата (помечено, не проверялось)

- Свечная подсистема, процессы загрузки/вычисления, компоненты
  (`CandleJob`/`CandleGroup`/`candle-loading`/`market-data-calculation`)
  — в `GAPS_CLOSE_3` не менялись, чисты по `DOCS_CHECK_3`; здесь
  только косвенно (онбординг-путь инструмента в `candle-loading`).
- Отложенная rules-подсистема (`InstrumentExternalRules` модель/mapping/
  sync-job, `trading-constraints.md`, `SizeCalculator`) — за пределами
  шага 1; проверена только на консистентность деферрала и как
  контр-источник по составу снапшота.
- Потребители рыночных данных поздних шагов (индикаторы/структура/фаза/
  стратегия/сделки/риск/FSM) — шаги 2-8, вне охвата.

## Стадия остановки

Обход **прошёл все стадии** (на гейте не остановлен).

- **Стадия 0 (гейтящие технические / скоуп) — чиста.** REST-first
  закреплён (OKX-Q4 шаг 1 не блокирует); владелец оркестрации
  онбординга/загрузки не материализуется по решению (ORCH-Q1, не
  гейт); снапшот-концепция rules и валидация плеча отложены (INSTR-Q1/
  INSTR-Q2, не гейт). Гейтящих вопросов нет.
- **Стадия 1 (процессы / lifecycles) — чиста.** `lifecycles/Instrument.md`
  обогащён биржевыми полями на `SYNC`, согласован с моделью и mapping;
  `candle-loading` не противоречит.
- **Стадия 2 (компоненты + модели) — чиста.** Граничная модель снапшота
  инструмента согласована по всем затронутым докам (см. фокусы ниже).

## Проверка фокусов `GAPS_CLOSE_3`

### Фокус 1 — состав `InstrumentExternalSnapshot` (шаг 1)

**Согласован во всех четырёх докаx.** Состав = идентичность +
`externalStatus` + `externalLeverage` (персистятся) + транзиентные
sizing/rounding-поля (base/quote/settle, `lotSz`/`minSz`/`ctVal`/
`ctMult`/`tickSz`), у которых персистентного дома в шаге 1 нет:

- `domain/core/Instrument.md` — §«Биржевое воплощение…» и нота
  разграничения: биржевые поля персистятся, справочные транзиентны;
- `mapping/Instrument.md` — mapping-flow и §«Что персистится»: то же;
- `OkxInstrumentResponse.md` — DTO-таблица: все 12 полей → snapshot,
  `state`/`lever` персистятся на `Instrument`, sizing транзиентны;
- `rules/raw-exchange-dto-boundary.md` — §«Граничные `*ExternalSnapshot`»:
  `InstrumentExternalSnapshot` присутствует с тем же составом.

Расхождений нет; старого «только идентичность» не осталось.

### Фокус 2 — единый шаг-1 дом `state`/`lever` = `Instrument`

**Согласован.** В шаге-1-маппинге `state`/`lever` идут **только** на
`Instrument` (`externalStatus`/`externalLeverage`):

- `mapping/InstrumentExternalRules.md` — из OKX-таблицы удалены строки
  `state`→`Status` и `lever`→`externalMaxLeverage`; §«Разграничение со
  снапшотом инструмента» перенаправляет `state`/`lever` на `Instrument`;
  они в «Не маппимые поля OKX»;
- `OkxInstrumentResponse.md` — `state`/`lever` перенесены в DTO-таблицу
  (→ `externalStatus`/`externalLeverage`), убраны из «потребляются
  rules».

Оговорка (не пробел): отложенная rules-**модель**
(`domain/other/InstrumentExternalRules.md`) сохраняет поля
`externalState`/`externalMaxLeverage` и enum `Status` — структура не
менялась по решению. Их сорсинг при материализации rules и соотнесение
с биржевыми полями `Instrument` помечены **INSTR-Q2** в самой модели и
в rules-mapping. Это сознательно отложенная rules-подсистема — по
заданию не пробел и не эскалируется.

### Фокус 3 — различение `leverage` ↔ `externalLeverage`

**Зафиксировано.** `leverage` (`Integer`, рабочее, задаётся при
создании, не из снапшота) и `externalLeverage` (`String`, биржевое,
сырое, из снапшота) различены в `domain/core/Instrument.md` (две
строки структуры + нота) и `mapping/Instrument.md` («не путать с
биржевым `externalLeverage`»; рабочее `leverage` из снапшота не
приходит).

### Фокус 4 — онбординг-lifecycle согласован с биржевыми полями на `SYNC`

**Согласован.** Путь `CREATED → SYNC → CANDLES_LOADING → ACTIVE`
изложен одинаково в `Instrument.md` (енум + ссылка),
`lifecycles/Instrument.md` (статусы/переходы/координация) и
`candle-loading.md` (онбординг). На `SYNC` биржевые `externalStatus`/
`externalLeverage` берутся из `InstrumentExternalSnapshot` — это
изложено в lifecycle (строка `SYNC` и переход `CREATED → SYNC`) и
согласовано с персистентностью модели (`external_status`/
`external_leverage` nullable, проставляются на `SYNC`).

## Пробелы по типам

### 1. Несогласованности между доками

**Нет.** Обе несогласованности `DOCS_CHECK_3` (Н(3-1) `lever`/`state`
приписаны идентичному снапшоту в `mapping/Instrument.md`; Н(3-2)
перечень граничных снапшотов без `InstrumentExternalSnapshot`) —
закрыты в `GAPS_CLOSE_3` и при повторном проходе согласованы. Новых не
выявлено.

### 2. Name-level без структуры (где структура нужна шагу)

**Нет.** Все сущности, конструируемые/заполняемые/маппящиеся в шаге 1
(`Instrument`, `InstrumentExternalSnapshot`, идентичность + биржевые
поля), заданы по полям/типам/nullability на функциональном пороге.
Транзиентные sizing-поля и отложенная rules-модель — осознанно
name-level/deferred (персистентный дом — INSTR-Q1), для шага 1
структура не требуется.

### 3. Неотвеченные / отложенные вопросы

**Новых нет.** Релевантные шагу 1 (INSTR-Q1, INSTR-Q2, ORCH-Q1) — все
отложены по решению, как блокеры не всплыли.

## Блокирующие открытые вопросы (проход по `open-questions.md`)

Гейтящих нет. По релевантности шагу 1:

- **INSTR-Q1** (снапшот-концепция rules / ренейм), **INSTR-Q2**
  (валидация рабочего плеча, роль `externalLeverage` как биржевого
  потолка, `HOLD`), **ORCH-Q1** (владелец оркестрации онбординга/
  загрузки) — открыты по решению; шаг 1 не блокируют (отложенная
  rules-подсистема / владелец не материализуется по решению).
- **OKX-Q4** (WS-каналы) — разблокирован для шага 1 (REST-first).
- **TIME-Q1** — для кода шага 1 закрыт (enum `TimeFrame` в `CandleGroup.md`);
  хвост (свёртка раздела в `Strategy.md`) — шаг 2.
- Остальные (DEAL-Q1/2/3, PROC-Q1, RISK-Q1, ENUM-Q1, CMD-Q1, OKX-Q1/2/3)
  — шаги 2-8, шаг 1 не блокируют.

## Н2 (тикер `instType`) — статус

Осознанно отложен в зону FSM/поздних шагов. В докаx шага 1 (онбординг
инструмента + загрузка свечей) как блокер не всплыл. Подтверждено — не
эскалируем (по заданию).

## Эскалации

**Нет.** Затронутая `GAPS_CLOSE_3` область целостна; нерешённого,
блокирующего шаг 1, не осталось. Все «хвосты» (`externalState`/
`externalMaxLeverage`/`Status` на отложенной rules-модели; лимит плеча
в `trading-constraints.md`) — помечены INSTR-Q1/INSTR-Q2, по заданию
не пробел и не предмет эскалации.

## Сводка

- **Несогласованности:** 0.
- **Name-level:** 0.
- **Неотвеченные:** 0 новых.
- **Эскалаций:** 0.
- **Фокусы `GAPS_CLOSE_3`:** все четыре (состав снапшота; единый дом
  `state`/`lever`; различение `leverage`/`externalLeverage`; онбординг-
  lifecycle на `SYNC`) — **чисто**.
- **Стадия остановки:** прошёл все стадии (0-2 чисты).
- **Итог: чисто.** Граничная модель снапшота инструмента согласована
  по всем семи затронутым докам; дрейфа вне затронутого набора нет;
  новых пробелов, неотвеченных вопросов и эскалаций нет. Сквозной
  прогон концепции под шаг 1 завершён без находок.
- **Рекомендация:** `GAPS_CLOSE_4` **не нужен**; шаг 1 готов к `CODE`.
  Переход к `CODE` — по решению пользователя (не выполняется в этом
  прогоне).

## Размещение знания — не здесь

Находок нет — размещать нечего. `concept-review` отчётом и
ограничивается.
