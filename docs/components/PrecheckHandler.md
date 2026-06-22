# PrecheckHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `PRECHECK` (компонент): проверки, логика,
шаги, команды.

## Назначение

Готовит сделку к созданию entry order. `Deal` уже создана
`DealOpeningService`, но runtime-сущности входа ещё не подтверждены.
Конструкция handler'а (3 проверки) — `docs/components/DealStateMachine.md`;
статусная механика и переходы — `docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = PRECHECK`; есть pinned `StrategyDetail` и `Instrument`;
есть `BalanceContainer` или можно создать `REFRESH_BALANCE`; refresh/search
не показывают >1 позиции; нет активной позиции/сделки (при максимуме
одной); **чистота инструмента** — нет чужого/висящего на инструменте (см.
ниже); нет borrow/debt; режим isolated. Не прошли безопасно → refresh /
остаться в `PRECHECK` / `ERROR`.

**Чистота инструмента берётся из стартового инструмент-скоупного сбора
итерации** (запрос биржи «что живо на инструменте» — видит и **незнакомые**
сущности), а не серией рефрешей только по известным
(`docs/components/IntegrationService.md`). Нет открытой сделки → биржа по
инструменту должна быть пуста; не пуста (чужой/висящий live order/algo) →
`AnomalyReport` + холд инструмента (`docs/rules/instrument-hold.md`). «Оптовую
команду» в command-layer не возвращаем (CMD-Q4: read **вне** command-layer).
Orphan-скан при уже открытой сделке и по неведомым ботом инструментам — зона
`AnomalyJob` (шаг 8); легитимное окно двойной reduce-only защиты REPLACE не
флагается (`docs/decisions/replace-not-amend.md`).

## Рабочая логика

Сначала обеспечить fresh `BalanceContainer` (absent/stale →
`REFRESH_BALANCE`, остаться, не вызывать `RiskValidator`/`CREATE_ORDER` на
этой итерации). Затем: найти `ENTRY`/`GRID_ENTRY` step → freshness
(`checkForStep`) → при устаревании `marketDataExpiredSetting` → проверить
`StrategyCondition`. Если condition false и live risk нет → закрыть
candidate Deal без ошибки (`CLOSED` + `ENTRY_CONDITION_EXPIRED`); если live
risk есть/неизвестно → recovery/safety. Если condition true → взять
action, проверить `DealActionState`, вызвать `StrategyActionCalculator`,
выставить рабочее плечо на бирже (см. ниже), создать `CREATE_ORDER` →
`SUBMIT_ORDER`. Risk-check entry action — через
risk-layer (`docs/processes/risk-evaluation.md`): BLOCKED в PRECHECK без
live risk → `CLOSED` + `RISK_CONTROL`.

**Защита risk-creating входа обязательна.** Risk-creating вход
(открытие/наращивание позиции) **без резолвимого стопа** до постановки не
доходит: `RiskValidator` помечает `BLOCKED`
(`RISK_CREATING_ENTRY_WITHOUT_STOP`) — без fail-open allocation-сайзинга в
обход `RISK_PER_TRADE`; в `PRECHECK` без live risk это `CLOSED` +
`RISK_CONTROL` (инвариант `docs/rules/risk-creating-entry-protection.md`).
Reduce-only/закрывающие действия правило не трогает.

**Set-leverage перед постановкой (INSTR-Q2).** Рабочее плечо пишется на
биржу **перед каждой сделкой, прямо перед `SUBMIT_ORDER`** (рабочее плечо
динамическое — зажато лимитом риска, меняется от сделки к сделке; без записи
ордер уйдёт со стейл-плечом). Операция **idempotent**: совпадает с уже
выставленным → пустая. Хранимое `Instrument.leverage` — потолок/умолчание,
не источник рабочего. Запись плеча — через `IntegrationService` (граница к
бирже); конкретное представление (отдельная команда `SET_LEVERAGE` vs
inline-write адаптера) — деталь `CODE`. Остаток INSTR-Q2 продвинут
(`docs/decisions/instrument-external-rules-materialization.md`,
`docs/decisions/per-trade-risk-policy.md`).

## Выходные проверки

Entry action материализован в локальный `Order`; **резолвимая защита
risk-creating входа подтверждена** (attached SL / иной стоп — без неё entry
не выпускается, `docs/rules/risk-creating-entry-protection.md`); рабочее
плечо выставлено на бирже под расчёт; `DealActionState` →
`RuntimeTarget(ORDER, orderId)`; order создан/отправлен; нет критичных
конфликтов; нет риска под kill-switch. → `PRECHECK → ENTRY_SUBMITTED`.

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `ENTRY`, `GRID_ENTRY`, `FAIL_SAFE`. Команды: `REFRESH_BALANCE`,
`REFRESH_POSITION`, `CREATE_ORDER`, `SUBMIT_ORDER`, `MARK_DEAL_ERROR`,
`EXECUTE_KILL_SWITCH`. Перечисление **неизвестных** live orders/algo по
инструменту для входной проверки чистоты берётся из стартового
инструмент-скоупного exchange-read **вне command-layer**
(`docs/components/IntegrationService.md`), не bulk-командой — Precheck-часть
CMD-Q4 закрыта; orphan-скан остаётся `AnomalyJob` (шаг 8).
