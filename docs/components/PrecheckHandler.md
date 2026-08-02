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
есть `BalanceContainer` или его добудет звено `REFRESH_BALANCE_COMMAND`
добывающего действия (`REFRESH_DEAL_CONTEXT_ACTION`); refresh/search
не показывают >1 позиции; нет активной позиции/сделки (при максимуме
одной); **чистота инструмента** — нет чужого/висящего на инструменте (см.
ниже); нет borrow/debt; режим isolated. Не прошли безопасно → refresh /
остаться в `PRECHECK` / `ERROR`.

**Чистота инструмента берётся из стартового инструмент-скоупного сбора
итерации** (запрос биржи «что живо на инструменте» — видит и **незнакомые**
сущности), а не серией рефрешей только по известным
(`docs/components/IntegrationService.md`). **As-built (шаг 6):** проверка
чужого риска — узкая: `foreignLiveRisk` в фасаде `DealFsmSupport` (handler
`IntegrationService` напрямую не инъектит) читает позицию по инструменту
(`IntegrationService.getPosition`) при отсутствии локальной позиции сделки, и
чужой live risk (позиция с size > 0) уводит сделку в `ERROR`
(`MARK_DEAL_ERROR_COMMAND`, `markError`). Форвард: нет открытой сделки → биржа по
инструменту должна быть пуста; не пуста (чужой/висящий live order/algo) →
`AnomalyReport` + холд инструмента (`docs/rules/instrument-hold.md`). «Оптовую
команду» в command-layer не возвращаем (CMD-Q4: read **вне** command-layer).
Orphan-скан при уже открытой сделке и по неведомым ботом инструментам — зона
`AnomalyJob` (шаг 8); легитимное окно двойной reduce-only защиты REPLACE не
флагается (`docs/decisions/replace-not-amend.md`).

## Рабочая логика

Сначала обеспечить fresh `BalanceContainer` (absent/stale → добыча
звеном `REFRESH_BALANCE_COMMAND` через `REFRESH_DEAL_CONTEXT_ACTION` —
handler добывающие `REFRESH_*` напрямую не эмитит,
`docs/components/SystemActionExecutor.md`; остаться, не вызывать
`RiskValidator`/`CREATE_ORDER_COMMAND` на этой итерации). Затем: найти `ENTRY`/`GRID_ENTRY` step → freshness
(`checkForStep`) → при устаревании `marketDataExpiredSetting` → проверить
`StrategyCondition`. Если condition false и live risk нет → закрыть
candidate Deal без ошибки (`CLOSED` + `ENTRY_CONDITION_EXPIRED`); если live
risk есть/неизвестно → recovery/safety. Если condition true → взять
action, проверить `DealActionState`, вызвать `StrategyActionCalculator`,
создать `CREATE_ORDER_COMMAND` → `SUBMIT_ORDER_COMMAND` (рабочее плечо на биржу пишет inline
`SubmitOrderExecutor` перед постановкой открывающего ордера — см. ниже; сам
handler плечо не пишет). Risk-check entry action — через
risk-layer (`docs/processes/risk-evaluation.md`): BLOCKED в PRECHECK без
live risk → `CLOSED` + `RISK_CONTROL`.

**Ноль на тропах закрытия без входа пишется только после проверки биржи**
(H7 `DOCS_CHECK_10`, `docs/rules/trading-constraints.md` §«Гейт открытия
сделки»). Обе PRECHECK-тропы (`ENTRY_CONDITION_EXPIRED`, `RISK_CONTROL`)
перед записью `resultProfit = 0` проверяют факт операций по сделке на
бирже: операций не было ⇒ ноль как **исход проверки**; операции были ⇒
сделка по этой тропе не закрывается, а идёт обычным путём финализации и
получает реальное число. Рамка R-выборки ключуется тем же предикатом
(`docs/models/domain/aggregate/Deal.md` §«Рамка R-выборки»).

**Защита risk-creating входа обязательна.** Risk-creating вход
(открытие/наращивание позиции) **без резолвимого стопа** до постановки не
доходит: `RiskValidator` помечает `BLOCKED`
(`RISK_CREATING_ENTRY_WITHOUT_STOP`) — без fail-open allocation-сайзинга в
обход `RISK_PER_TRADE`; в `PRECHECK` без live risk это `CLOSED` +
`RISK_CONTROL` (инвариант `docs/rules/risk-creating-entry-protection.md`).
Reduce-only/закрывающие действия правило не трогает.

**Set-leverage перед постановкой (INSTR-Q2 — закрыт).** Рабочее плечо пишется
на биржу **перед постановкой открывающего ордера** (рабочее плечо динамическое —
зажато лимитом риска, меняется от сделки к сделке; без записи ордер уйдёт со
стейл-плечом). Операция **idempotent**: совпадает с уже выставленным → пустая.
Хранимое `Instrument.leverage` — потолок/умолчание. Запись — через
`IntegrationService` (граница к бирже).

**Представление решено (as-built шага 6): inline-write в submit-executor'е — не
отдельная команда `SET_LEVERAGE` и не запись в handler'е.** Плечо пишет
`SubmitOrderExecutor` (`ensureLeverage`) прямо перед place-вызовом: co-located с
постановкой (атомарно, непропускаемо, покрывает и наращивание позиции в
`MANAGING`); только для открывающих ордеров (reduce-only пропускается); из
`Instrument.leverage` (null → пропуск); неуспех → `ExchangeIntegrationException`.
**Сам `PrecheckHandler` плечо не пишет.** Спецификация —
`docs/components/SubmitOrderExecutor.md`. INSTR-Q2 закрыт
(`docs/decisions/instrument-external-rules-materialization.md`,
`docs/decisions/per-trade-risk-policy.md`).

## Выходные проверки

Entry action материализован в локальный `Order`; **резолвимая защита
risk-creating входа подтверждена** (attached SL / иной стоп — без неё entry
не выпускается, `docs/rules/risk-creating-entry-protection.md`); рабочее
плечо под расчёт пишет inline `SubmitOrderExecutor` перед постановкой
открывающего ордера (не handler — см. §«Set-leverage перед постановкой»);
`DealActionState` с целью в колонках (`targetEntityType = ORDER`,
`targetEntityId = orderId` — объект `RuntimeTarget` расплющен в колонки,
`docs/decisions/command-action-boundary.md` §3); order создан/отправлен;
нет критичных
конфликтов; нет риска под kill-switch. → `PRECHECK → ENTRY_SUBMITTED`.

## Допустимые StrategyStep

Steps: `ENTRY`, `GRID_ENTRY`, `FAIL_SAFE`. Перечень команд handler-док не
держит: состав команд — собственность действий
(`docs/decisions/fsm-execution-layering.md` §«Handler исполняет действия»;
реестры звеньев — `docs/decisions/command-action-boundary.md` §2,
`docs/components/SystemActionExecutor.md`).
Перечисление **неизвестных** live orders/algo по
инструменту для входной проверки чистоты берётся из стартового
инструмент-скоупного exchange-read **вне command-layer**
(`docs/components/IntegrationService.md`), не bulk-командой — Precheck-часть
CMD-Q4 закрыта; orphan-скан остаётся `AnomalyJob` (шаг 8).
