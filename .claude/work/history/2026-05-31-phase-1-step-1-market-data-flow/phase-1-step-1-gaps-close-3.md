# GAPS_CLOSE_3 — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

Что закрыто в `GAPS_CLOSE_3` шага 1 Фазы 1 и каков результат
подтверждающей проверки затронутой области.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных».
- Под-шаг: `GAPS_CLOSE_3` (`.claude/processes/roadmap-step-execution.md`).
- Вход: gap-отчёт `phase-1-step-1-docs-check-3.md` (Н(3-1), Н(3-2);
  эскалаций 0) **плюс** ревизия модели инструмента, согласованная в
  чате.
- Ревизия — **дополнение к `GAPS_CLOSE_2`, не противоречие**:
  прежняя формулировка «snapshot↔domain шага 1 = только
  идентичность» заменена на «идентичность + биржевые поля».

## Решение (модель полей `Instrument`, шаг 1)

Шаг 1 `Instrument` несёт, помимо идентичности и онбординг-`Status`
(`CREATED→SYNC→CANDLES_LOADING→ACTIVE`, без изменений) и
`plannedCandleStartDate`, биржевые поля:

- `externalStatus` (`String`) — биржевой статус, источник OKX `state`
  из снапшота; **персистится**.
- `externalLeverage` (`String`) — биржевое значение плеча, источник
  OKX `lever` из снапшота; **персистится**.
- `leverage` (`Integer`) — рабочее плечо, **задаётся при создании**
  инструмента (не из снапшота).

snapshot↔domain (`InstrumentExternalSnapshot`, шаг 1) = идентичность
+ `externalStatus` + `externalLeverage`. Справочные sizing/rounding-
поля (base/quote/settle, `lotSz`/`minSz`/`ctVal`/`ctMult`/`tickSz`) —
транзиентны, персистентного дома в шаге 1 не имеют (INSTR-Q1).
Модель `InstrumentExternalRules` (sizing/rounding) — остаётся
отложенной, структурно не менялась.

## Что сделано (размещение знания)

### Н(3-1) — снято ошибочное размещение `lever`/`state`

- `docs/models/domain/core/Instrument.md` — в структуру добавлены
  `externalStatus`/`externalLeverage`; `leverage` уточнён («задаётся
  при создании»); переписаны §«Биржевое воплощение…» и нота
  разграничения; в персистентности `external_status`/
  `external_leverage` — nullable (проставляются на `SYNC`); в связи
  добавлен INSTR-Q2.
- `docs/models/mapping/Instrument.md` — mapping-flow и §«Что
  персистится» переписаны (идентичность + биржевые поля); в OKX-
  таблицу добавлены `state`→`externalStatus`, `lever`→`externalLeverage`;
  `lever`/`state` исключены из перечня транзиентных справочных полей.
- `docs/models/integrations/okx/OkxInstrumentResponse.md` —
  `state`/`lever` перенесены в таблицу полей DTO (→ snapshot
  `externalStatus`/`externalLeverage`); удалены из «Поля, которые НЕ
  входят в этот DTO / потребляются rules»; нота разграничения
  обновлена.
- `docs/models/mapping/InstrumentExternalRules.md` — из OKX-таблицы
  удалены строки `lever`→`externalMaxLeverage` и `state`→`Status`,
  удалён OKX status resolver, из mapping-flow убраны
  `externalMaxLeverage`/`externalState`; добавлена §«Разграничение со
  снапшотом инструмента (шаг 1)»; `state`/`lever` добавлены в «Не
  маппимые поля OKX» (→ `Instrument`).
- `docs/models/domain/other/InstrumentExternalRules.md` — **структура
  не менялась** (отложена). Добавлены пометки в deferred-блок и в
  §Енум `Status`: биржевые `state`/`lever` шага 1 живут на
  `Instrument`; сорсинг rules-полей и роль `externalLeverage` —
  INSTR-Q2.

### Н(3-2) — пополнен перечень граничных снапшотов

- `docs/rules/raw-exchange-dto-boundary.md` — в §«Граничные
  `*ExternalSnapshot`» добавлен `InstrumentExternalSnapshot` (состав:
  идентичность + `externalStatus`/`externalLeverage` + транзиентные
  sizing-поля), согласованно с `mapping/Instrument.md`.

### Согласование затронутой области

- `docs/lifecycles/Instrument.md` — описание `SYNC` и перехода
  `CREATED → SYNC` дополнено биржевыми `externalStatus`/
  `externalLeverage`.
- `.claude/work/questions/open-questions.md` — INSTR-Q1 приведён к
  новой модели (`state`/`lever` персистятся на `Instrument`, не на
  rules); заведён **INSTR-Q2** (валидация рабочего плеча, роль
  `externalLeverage` как биржевого потолка, действие `HOLD`); статус-
  врезка обновлена (Три вопроса из шага 1).

### Открытый вопрос (отложен, вне шага 1)

INSTR-Q2 — валидация рабочего `leverage` против конфигового максимума
(превышение → инструмент не выпускается на биржу, `HOLD` как
нарушение торгового правила). Открытые аспекты: роль
`externalLeverage` как биржевого потолка; состояние/действие `HOLD` в
lifecycle инструмента (в текущем онбординг-пути его нет). Шаг 1 не
блокирует.

## Статус

- Статус шага 1: `DOCS_CHECK_3` → `GAPS_CLOSE_3` (`phase-1.md`);
  ролляп фазы 1 — `IN_PROGRESS` без изменений.
- К `CODE` **не переходим** (по заданию).

## Подтверждающая проверка затронутой области

Повторный проход по граничной модели снапшота инструмента после
правок:

- **Состав `InstrumentExternalSnapshot` согласован** во всех доках
  (`Instrument.md`, `mapping/Instrument.md`, `OkxInstrumentResponse.md`,
  `raw-exchange-dto-boundary.md`): идентичность + `externalStatus` +
  `externalLeverage` персистятся на `Instrument`; sizing-поля
  транзиентны. Старого «только идентичность» не осталось.
- **`lever`/`state` имеют единственный шаг-1 дом** — `Instrument`
  (`externalLeverage`/`externalStatus`). Атрибуция rules-снапшоту в
  шаге 1 снята в трёх доках; rules-маппинг явно перенаправляет на
  `Instrument`.
- **`leverage` (рабочее, из создания) ≠ `externalLeverage` (сырое
  биржевое)** — различение зафиксировано в модели и mapping.
- **Остаток (не несогласованность, помечен открытым вопросом).**
  Отложенная модель `InstrumentExternalRules` сохраняет поля
  `externalState`/`externalMaxLeverage`/`Status` и downstream
  (`trading-constraints.md` — лимит плеча через
  `externalMaxLeverage`; `InstrumentExternalRulesService`/`SyncJob` —
  проверки max leverage/торгуемости). Их сорсинг и соотнесение с
  биржевыми полями `Instrument` (дубль/удаление) сознательно вынесены
  в **INSTR-Q2** (роль `externalLeverage` как потолка) и **INSTR-Q1**
  (снапшот-концепция rules). Это отложенная rules-подсистема —
  `trading-constraints.md`/`Service`/`SyncJob` не правились по
  решению «rules без изменений».

Затронутая область целостна; нерешённого, блокирующего шаг 1, не
осталось.
