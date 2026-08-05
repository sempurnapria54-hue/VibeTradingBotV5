# HoldSignal

## На какой вопрос отвечает этот файл

Что это за runtime value object `HoldSignal`: структура, фабрики, енум
`HoldScope`, ключевой инвариант «сигнал, не закрытие сделки».

## Назначение

`HoldSignal` — сигнал «поднять реактивный safety-холд scope». RVO
(`@Value`, immutable; см. `.claude/decisions/runtime-value-object.md`),
который несёт `DealTransition` рядом с уводом своей сделки в ERROR
(handler → transition). **Сам сделку не закрывает** — её в ERROR уводит
handler; сигнал адресует **инструмент-/биржа-широкую** реакцию,
координируемую `SafetyHoldCoordinator` в проходе `DealOrchestratorJob`
(см. `docs/components/SafetyHoldCoordinator.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `scope` | `HoldScope` | Scope (радиус) холда: `INSTRUMENT` / `EXCHANGE`. |
| `reactionClass` | `ReactionClass` | **Класс реакции**: `SOFT` / `FULL` (см. §Енум `ReactionClass`). |
| `code` | `String` | Машинно-читаемый код причины; попадает в `AnomalyReport.code`. |

**Severity не несёт.** Severity задаётся на границе захода в
`AnomalyReportService`, а не переносится сигналом (см.
`docs/rules/error-handling-policy.md`, дизайн холдов шага 6). Класс
реакции — **не** severity: он говорит, **что делать**, а не насколько
серьёзно.

## Енум `ReactionClass` (H14 `DOCS_CHECK_11`)

Какая реакция поднимается сигналом.

- `FULL` — прежнее (и до шага 7 единственное) поведение реактивного
  контура: `TRADE_BLOCKED` + teardown через `KillSwitchExecutor`.
- `SOFT` — **мягкий холд**: `Instrument.Status.ENTRY_BLOCKED` +
  журнальный `AnomalyReport` (`NON_CRITICAL`); kill-switch **не
  гоняется**, живые сделки доживают
  (`docs/rules/instrument-hold.md` §Enforcement).

**Почему класс на сигнале, а не отдельный производитель вне контура**
(решение пользователя). `instrument-hold.md` §«Носитель серии»
ратифицировал, что блокировка инструмента **работает уже в шаге 7**, а
класс триггера определяет реакцию — мягкую или полную. Механизма для
мягкой ветки не было: всякий `HoldSignal` вёл в kill-switch-контур, то
есть вход-сайд `FAILED` дал бы `TRADE_BLOCKED` + teardown — ровно то
поведение, которое ратифицированная мягкая политика **сняла**
(kill-switch «за радиусом», платящий рыночное закрытие при нулевом
снижении риска). Расширение существующего канала стоит поля в RVO, ветки
в координаторе и одного кода реестра; альтернатива «отдельный
производитель по образцу синка» заводила бы второй параллельный путь к
той же реакции.

**Оговорка к «реактивный контур по определению `CRITICAL`».** Она
описывала контур, у которого была одна ветка. С вводом класса реакции
верно так: `CRITICAL` — свойство ветки `FULL`, а не контура; ветка
`SOFT` журнальная (`NON_CRITICAL`).

## Фабрики

- `instrument(code)` — полный холд инструмента: `HoldScope.INSTRUMENT` +
  `ReactionClass.FULL` + code.
- `instrumentSoft(code)` — мягкий холд инструмента:
  `HoldScope.INSTRUMENT` + `ReactionClass.SOFT` + code.
- `exchange(code)` — холд биржи: `HoldScope.EXCHANGE` +
  `ReactionClass.FULL` + code.

Публичного конструктора для прямой сборки не используем — сигнал строится
фабрикой по scope и классу реакции, чтобы радиус и намерение читались на
call-site. Мягкой биржевой фабрики нет: биржевой радиус мягкой ветки не
имеет (`docs/rules/exchange-hold.md`).

## Енум `HoldScope`

**Словарь радиусов проекта.** На каком радиусе действует реакция.
**Общий** для `HoldSignal` (сигнал из прохода) и `AnomalyReport` (журнал
инцидента) — своего дока не имеет, описан здесь.

- `INSTRUMENT` — один инструмент (см. `docs/rules/instrument-hold.md`).
- `INSTRUMENT_GROUP` — инструменты одной комиссионной группы: обесценен
  факт, общий для группы (несвежесть ставки / ключа группы,
  `docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии»).
  **Значение целевое — вводится на `CODE` шага 7** (H22 `DOCS_CHECK_10`):
  в коде enum сегодня содержит **два** значения (`INSTRUMENT`,
  `EXCHANGE`), и комментарий `V10` знает только их. Пометка обязательна по
  образцу соседней дельты (`Instrument.Status.ENTRY_BLOCKED` — «целевой,
  вводится на `CODE` шага 7», `docs/rules/instrument-hold.md`
  §Enforcement): доки трактовали трёхзначный `HoldScope` как факт, а
  реестр кодов уже назначал `INSTRUMENT_GROUP` двум кодам.
  **CODE-пункт того же ввода:** javadoc `HoldScope` в коде всё ещё несёт
  **снятые** ярлыки уровня («инструмент = уровень 3, биржа = уровень 4»,
  «Уровни error-градации»), которые `GAPS_CLOSE_6` (H4) с енума снял —
  то есть код воспроизводит отменённое тождество scope ≡ уровень
  (`.claude/work/backlog.md` §Шаг 7).
- `EXCHANGE` — вся биржа/аккаунт: каскад на все инструменты биржи (см.
  `docs/rules/exchange-hold.md`).

Перечень совпадает с перечнем радиусов error-политики
(`docs/rules/error-handling-policy.md` §«Перечень scope и реакций»):
инструмент / группа инструментов / биржа-аккаунт.

**`HoldSignal` использует подмножество радиусов.** Групповым радиусом
реактивный контур не оперирует ни в одном классе реакции:
`INSTRUMENT_GROUP` производит журнальная тропа синка, и живёт он на
`AnomalyReport.scope` (H4, `GAPS_CLOSE_6`). Значение не «неиспользуемое»:
у него другой производитель, не другой енум. Мягкая ветка сигнала
(`instrumentSoft`) групповым радиусом тоже не оперирует — она про
**один** инструмент.

Отсюда следствие для поверхности создания отчёта (H23, `GAPS_CLOSE_7`):
журнальная тропа заводит `AnomalyReport` **без `HoldSignal`** — она не
рождена проходом над сделкой, `DealContext` у неё может не быть вовсе
(`InstrumentExternalRulesSyncJob`), и severity она задаёт сама
(`NON_CRITICAL`). Фабрика `instrumentGroup(code)` в `HoldSignal` **не
заводится**: это был бы сигнал без адресата — реактивный координатор
групповым радиусом не оперирует
(`docs/models/domain/other/AnomalyReport.md` §«Производящая поверхность»).

Scope описывает **радиус**, не уровень серьёзности: уровень — отдельная
ось, живёт в error-политике (`docs/rules/error-handling-policy.md`
§«Радиус ущерба задаёт scope»), и одному уровню могут соответствовать
разные радиусы (ярлыки уровня сняты с енума — H6, `GAPS_CLOSE_5`).
Радиус не задаёт и **статус** enforcement: мягкая реакция ставит
`Instrument.Status.ENTRY_BLOCKED`, kill-switch-класс — `TRADE_BLOCKED`
(`docs/rules/instrument-hold.md` §Enforcement).

## Связи

- `docs/components/SafetyHoldCoordinator.md` — координатор, потребляющий
  сигнал.
- `docs/models/domain/other/AnomalyReport.md` — журнал инцидента (`code`
  сигнала → `code` отчёта; `scope` общий).
- `docs/rules/instrument-hold.md`, `docs/rules/exchange-hold.md`,
  `docs/rules/error-handling-policy.md` — правила холдов по scope и
  error-политика (уровни — там, ось отдельная).
- `docs/components/KillSwitchExecutor.md` — исполнитель teardown,
  запускаемый реакцией.
