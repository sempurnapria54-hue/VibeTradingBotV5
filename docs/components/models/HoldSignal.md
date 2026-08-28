# HoldSignal

## На какой вопрос отвечает этот файл

Что это за runtime value object `HoldSignal` — параметр вызова
`HoldService`: структура, фабрики, енумы `ReactionClass` / `HoldScope`.

## Назначение

`HoldSignal` — **параметр вызова** `HoldService.hold(signal)`: чем
описывается требуемая блокировка. RVO (`@Value`, immutable; см.
`.claude/decisions/runtime-value-object.md`), собираемый детектором на
call-site и **тут же передаваемый** исполнителю блокировки. **Сам сделку не закрывает** — её в
`ERROR` уводит FSM/handler; параметр адресует
**инструмент-/биржа-широкую** реакцию.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `scope` | `HoldScope` | Scope (радиус) холда: `INSTRUMENT` / `EXCHANGE`. |
| `reactionClass` | `ReactionClass` | **Класс реакции**: `SOFT` / `FREEZE` / `FULL` (см. `ReactionClass`). |
| `code` | `String` | Машинно-читаемый код причины; попадает в `AnomalyReport.code`. |
| `instrumentId` | `Long` | **Идентичность объекта блокировки** при `scope = INSTRUMENT`; пусто при `scope = EXCHANGE`. |
| `exchangeId` | `Long` | **Идентичность объекта блокировки** при `scope = EXCHANGE`; при `INSTRUMENT` — биржа инструмента, если детектор её знает, иначе пусто (резолвится сервисом). |

**Severity не несёт.** Severity задаётся на границе захода в
`AnomalyReportService`, а не переносится сигналом (см.
`docs/rules/error-handling-policy.md`, дизайн холдов шага 6). Класс
реакции — **не** severity: он говорит, **что делать**, а не насколько
серьёзно.

## Енум `ReactionClass` (H14 `DOCS_CHECK_11`)

Какая реакция поднимается сигналом.

- `FULL` — прежнее (и до шага 7 единственное) поведение реактивного
  контура: `TRADE_BLOCKED` + teardown через `KillSwitchExecutor`. На
  биржевом радиусе это **ступень 2** лестницы: flatten всей биржи,
  каскад активных сделок в `ERROR`, и после teardown — командная тишина
  (`docs/rules/exchange-hold.md`).
- `SOFT` — **мягкий холд**: `Instrument.Status.ENTRY_BLOCKED`;
  kill-switch **не гоняется**, живые сделки доживают
  (`docs/rules/instrument-hold.md`).
- `FREEZE` — **биржевая ступень 1**: `Exchange.HOLD` + `AnomalyReport`
  (`NON_CRITICAL`, `kind = STATE`); kill-switch **не гоняется**, каскада
  активных сделок в `ERROR` нет, командного блок-сета у ступени нет —
  биржа выпадает из entry-скана, живые сделки сопровождаются полностью
  (`docs/rules/exchange-hold.md`). `HoldService` ставит
  напрямую, координатор не зовётся.

**Класс реакции ≠ `severity` отчёта.** `Severity` отвечает на один вопрос —
критична аномалия или нет; `ReactionClass` отвечает на другой — что делать.
Мягкая ветка отчёта об аномалии не производит вовсе, поэтому вопрос «какую severity ставит
`SOFT`» не возникает.

## Фабрики

- `instrument(code, instrumentId)` — полный холд инструмента:
  `HoldScope.INSTRUMENT` + `ReactionClass.FULL` + code + идентичность.
- `instrumentSoft(code, instrumentId)` — мягкий холд инструмента:
  `HoldScope.INSTRUMENT` + `ReactionClass.SOFT` + code + идентичность.
- `exchange(code, exchangeId)` — **мягкий холд биржи (ступень 1)**:
  `HoldScope.EXCHANGE` + `ReactionClass.FREEZE` + code + идентичность →
  `Exchange.HOLD`. Единственный зовущий — исполнитель терминального ребра
  при расхождении сверки за калиброванным допуском (
  `ReactionClass`).
- `exchangeTradeBlock(code, exchangeId)` — **критическая реакция биржи
  (ступень 2)**: `HoldScope.EXCHANGE` + `ReactionClass.FULL` + code +
  идентичность → `Exchange.TRADE_BLOCKED`. Триггеров два и оба сюда:
  живой риск без защиты
  (`docs/rules/live-risk-protection.md`) и **неожиданное
  поведение биржи** — controlled-тропы, safety-каскад внешнего статуса,
  недостача обязательного поля добытой записи
  (`docs/rules/exchange-hold.md`).

Публичного конструктора для прямой сборки не используем — сигнал строится
фабрикой по scope и классу реакции, чтобы радиус и намерение читались на
call-site. **Биржевых фабрик две — по ступеням лестницы**; мягкой
(`SOFT`) биржевой фабрики нет и не будет: `SOFT` — инструментный класс,
а биржевой мягкий холд есть `FREEZE`, у него своя ступень и свой состав.

**Код `CLOSE_OUTCOME_UNDETERMINED` кодом холда быть перестал**: он остаётся кодом **аномалии**
(`docs/models/domain/other/AnomalyReport.md`), но сигнала не производит.
Нарушение контракта в добытой записи закрытия теперь обнаруживается **в
слое интеграции** и поднимает биржевую ступень 2 общим контуром — по
классу перехваченного `ControlledExchangeException`, то есть кодом
`reasonCode`-тропы. **`RECONCILIATION_OPERAND_MISSING` снят целиком**: недостача
**нашего** операнда допуска не производит ни сигнала, ни аномалии —
нарушенный инвариант ловит детектирующий контур отказом операции.

## Енум `HoldScope`

**Словарь радиусов проекта.** На каком радиусе действует реакция.
**Общий** для `HoldSignal` (сигнал из прохода) и `AnomalyReport` (журнал
инцидента) — своего дока не имеет, описан здесь.

- `INSTRUMENT` — один инструмент (см. `docs/rules/instrument-hold.md`).
- `EXCHANGE` — вся биржа/аккаунт: каскад на все инструменты биржи (см.
  `docs/rules/exchange-hold.md`).

**CODE-пункт:** javadoc `HoldScope` в коде всё ещё несёт **снятые**
ярлыки уровня («инструмент = уровень 3, биржа = уровень 4», «Уровни
error-градации»), которые (H4) с енума снял — то есть код
воспроизводит отменённое тождество scope ≡ уровень
(`.claude/work/backlog.md` 7). Ярлык устарел вдвойне: уровень 4
внутри двухступенчатый (`Exchange.HOLD` / `TRADE_BLOCKED`,
`docs/rules/exchange-hold.md`) и одним значением scope не выражается.

**Значения `INSTRUMENT_GROUP` в енуме нет**. Оно было **целевым** — вводилось на `CODE` шага 7 ради
единственного производителя, отчёта о несвежести комиссионных ставок; тот
теперь заводится **на инструмент** (по одному на каждый затронутый,
`docs/models/domain/other/AnomalyReport.md`), и производителей
у значения не осталось ни одного. Енум остаётся двузначным — тем же, что
в коде и в комментарии `V10`; вводить и тут же не использовать не нужно.

Перечень **не совпадает** с перечнем радиусов error-политики
(`docs/rules/error-handling-policy.md`:
инструмент / группа инструментов / биржа-аккаунт) — и это не расхождение,
а действующий принцип. **Групповой радиус выражается набором строк, а не
значением енума** (`docs/rules/instrument-hold.md`): блокировка группы — набор статусов
инструментов, журнал группы — набор отчётов на инструменты. Енум
описывает **субъект одной строки**, а не охват реакции.

**`HoldSignal` использует оба радиуса.** Мягкая ветка (`instrumentSoft`)
— про **один** инструмент; групповой и аккаунтный охват детектор
разворачивает в набор вызовов .

Отсюда следствие для поверхности создания отчёта:
журнальная тропа заводит `AnomalyReport` **без `HoldSignal`** — она не
рождена проходом над сделкой, `DealContext` у неё может не быть вовсе
(`InstrumentExternalRulesSyncJob`), и severity она задаёт сама
(`NON_CRITICAL`). Фабрика `instrumentGroup(code)` **не заводится**: полная
реакция групповым радиусом не оперирует
(`docs/models/domain/other/AnomalyReport.md`).

Scope описывает **радиус**, не уровень серьёзности: уровень — отдельная
ось, живёт в error-политике (`docs/rules/error-handling-policy.md`), и одному уровню могут соответствовать
разные радиусы.
Радиус не задаёт и **статус** enforcement: мягкая реакция ставит
`Instrument.Status.ENTRY_BLOCKED`, kill-switch-класс — `TRADE_BLOCKED`
(`docs/rules/instrument-hold.md`).

## Связи

- `docs/components/HoldService.md` — общий исполнитель блокировки,
  принимающий этот параметр.
- `docs/components/SafetyHoldCoordinator.md` — последовательность
  `FULL`-реакции.
- `docs/models/domain/other/AnomalyReport.md` — журнал инцидента (`code`
  сигнала → `code` отчёта; `scope` общий).
- `docs/rules/instrument-hold.md`, `docs/rules/exchange-hold.md`,
  `docs/rules/error-handling-policy.md` — правила холдов по scope и
  error-политика (уровни — там, ось отдельная).
- `docs/components/KillSwitchExecutor.md` — исполнитель teardown,
  запускаемый реакцией.
