# GAPS_CLOSE_2 — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

Что сделано на под-шаге `GAPS_CLOSE_2` шага 1 (закрытие пробелов
`DOCS_CHECK_2`): что размещено/изменено, какой вопрос заведён, что
осталось на `DOCS_CHECK_3`.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных».
- Под-шаг: `GAPS_CLOSE_2` (`.claude/processes/roadmap-step-execution.md`).
- Вход — gap-отчёт `phase-1-step-1-docs-check-2.md` (Н1, Н2, Н3,
  N1, N2, Q1; эскалации Э(2-1), Э(2-2), Э(2-3)).
- Эскалации разобраны в чате; решения применены ниже.

## Решения по эскалациям — как применены

- **Э(2-1) Скоуп `InstrumentExternalRules` [Н1, N2].**
  `InstrumentExternalRules` отложена за пределы шага 1
  (округление/sizing/риск — поздние шаги; backlog п.9). Слой модели
  шага 1 = `Instrument` (идентичность + статус +
  `plannedCandleStartDate`) + `InstrumentExternalSnapshot`
  (транзиентная граница). Н1 снят деферралом: rules больше не
  претендует на base/quote/settle (поля убраны из модели). N2:
  персистентного дома справочных полей в шаге 1 нет; snapshot↔domain
  шага 1 = только идентичность.
- **Э(2-2) Глубина lifecycle инструмента [N1].** Материализован
  **только онбординг-путь шага 1** (`CREATED → SYNC →
  CANDLES_LOADING → ACTIVE`) + координация с `CandleGroup`.
  Периферийные статусы (`HOLD`, `ERROR`-recovery, повторный
  онбординг, `CLOSED`) — отложены (backlog п.9), помечены в
  lifecycle-доке.
- **Э(2-3) Классификация Н3.** Признано несогласованностью: введён
  `CandleExternalSnapshot`. Путь свечи: OKX-массив →
  `CandleExternalSnapshot` → domain `Candle`. Правило DTO-границы
  остаётся абсолютным; снапшот добавлен в перечень граничных.

## Что размещено (новые доки)

| Файл | Тип/слой | Закрывает |
|---|---|---|
| `docs/models/mapping/Instrument.md` | mapping | N2 (snapshot↔domain = идентичность) |
| `docs/lifecycles/Instrument.md` | lifecycle | N1 (онбординг-путь + координация) |

## Что изменено (реконсиляции)

| Файл | Что |
|---|---|
| `docs/models/domain/core/Instrument.md` | + `plannedCandleStartDate`; Status → ссылка на lifecycle; снято «mapping на DOCS_CHECK_2» → `mapping/Instrument.md`; ">" нота переписана (разграничение для шага 1 + INSTR-Q1). |
| `docs/models/domain/other/InstrumentExternalRules.md` | Н1: модель помечена отложенной за шаг 1; убраны `externalBaseCurrency`/`externalQuoteCurrency`/`externalSettleCurrency`; ссылка на INSTR-Q1. |
| `docs/models/domain/other/CandleGroup.md` | Q1: `coverage*` → `actualFirstUtcMillis`/`actualLastUtcMillis`; + `count`; раздел «Целостность по count (density-инвариант)»; персистентность обновлена. |
| `docs/lifecycles/CandleGroup.md` | Q1: §«Что отложено» → §«Политика загрузки и целостности» (глубина/SYNC/CHECK/REPAIR/ERROR, бинарный поиск, реконсиляция count); coverage→actual в потоке и таблице. |
| `docs/components/CandleJob.md` | Q1: CRON раз в минуту; реконсиляция `count` на старте; coverage→actual; снят деферрал на `DOCS_CHECK_2`. |
| `docs/processes/market-data-calculation.md` | Q1: раздел загрузки обновлён (горизонт/actual/count, снят деферрал); + §«Онбординг инструмента и готовность свечей» (N1). |
| `docs/models/mapping/Candle.md` | Н3: + §«Граница: `CandleExternalSnapshot`»; путь OKX-массив → snapshot → domain. |
| `docs/rules/raw-exchange-dto-boundary.md` | Н3: `CandleExternalSnapshot` в перечне граничных снапшотов. |
| `docs/integrations/okx/contracts/candle.md` | Q1: стоп пагинации `coverage_start` → `plannedCandleStartDate`. |

## Пробелы отчёта — статус

- **Н1** (дубль base/quote/settle) — закрыт: снят деферралом rules +
  удаление полей.
- **Н2** (тикер тянет `instType`, которого нет в `OkxTickerResponse`)
  — **не трогаем** (решение 5): не блокер, тикер отложен в зону FSM;
  починим при материализации тикера.
- **Н3** (правило DTO-границы vs маппинг свечей) — закрыт:
  `CandleExternalSnapshot`.
- **N1** (lifecycle онбординга `Instrument`) — закрыт онбординг-путём
  + координацией; периферия отложена.
- **N2** (`mapping/Instrument.md` + персистентный дом) — закрыт:
  mapping создан (идентичность); дом справочных полей в шаге 1
  отсутствует осознанно (rules отложена).
- **Q1** (политика загрузки/целостности свечей) — закрыта в
  lifecycle/модели/процессе/CandleJob (count-based, density,
  бинарный поиск, расписание; числа — на `CODE`).

## Вопросы — заведено

- **INSTR-Q1** (новый, `open-questions.md`): как снапшот-концепция
  ляжет на `InstrumentExternalRules` и не потребуется ли ренейм
  rules. Якорь пересмотра — материализация rules на поздних шагах.

## Классификационные решения (на ревью)

- **`plannedCandleStartDate` → `Instrument`** (на инструмент, общий
  для всех ТФ), не на `CandleGroup`. `actualFirst`/`actualLast`/
  `count` → `CandleGroup` (per-ТФ). Промежуточного объекта нет;
  `Instrument` → `CandleGroup` напрямую (1:many).
- **Density-инвариант** размещён в модели `CandleGroup` (структурный
  инвариант), операционная политика (BACKFILL/SYNC/CHECK/REPAIR,
  бинарный поиск, расписание) — в lifecycle + процесс + CandleJob.
  Отдельного файла-правила не заведено (тема привязана к `CandleGroup`).
- **`CandleExternalSnapshot`** документирован разделом в
  `mapping/Candle.md` (простой случай по
  `docs/models/externalSnapshot/README.md` — поля фиксируются в
  mapping), отдельный файл не создан.
- **Lifecycle `Instrument`** — отдельный файл (как `CandleGroup`,
  `Order`, `AlgoOrder`), не раздел модели. Владелец записи
  `Instrument.Status` — деталь `CODE` (job не выдумываем
  превентивно); семантика переходов и координация зафиксированы.
- **`InstrumentExternalRules`** — поля base/quote/settle удалены
  (не помечены), т.к. модель отложена и претензии сняты; маппинг
  `InstrumentExternalRules.md` уже маршрутизировал их в снапшот
  (Н1 был односторонним дублем в модели).

## Статус роадмапа

- Шаг 1: `DOCS_CHECK_2` → `GAPS_CLOSE_2` (`phase-1.md`).
- Фаза 1: `IN_PROGRESS` (ролляп без изменений; шаг 1 не-HOLD,
  прочие HOLD).
- Следующее — `DOCS_CHECK_3` (повторная сквозная проверка по
  стадийному обходу `concept-review`).

## Что осталось на DOCS_CHECK_3

1. Проверить целостность после правок: связки `Instrument` ↔
   lifecycle ↔ `mapping/Instrument.md` ↔ `CandleGroup`; согласие
   `count`/`actual*` во всех доках свечной подсистемы.
2. Н2 (тикер `instType`) остаётся открытым по решению (не блокер
   шага 1) — проверить, что нигде не всплыл как блокер.
3. INSTR-Q1 — открыт; шаг 1 не блокирует.

## Сводка

- Новых доков: 2. Изменено доков: 9. Вопросов заведено: 1
  (INSTR-Q1). Пробелов закрыто: Н1, Н3, N1, N2, Q1; Н2 — осознанно
  не трогаем. Затронуто работы-трекинга: open-questions, backlog
  (п.9 + связанные вопросы), phase-1, прогресс.
- Эскалации Э(2-1)…Э(2-3) — разобраны и применены.
- Итог под-шага: пробелы закрыты; открыт `DOCS_CHECK_3`.
