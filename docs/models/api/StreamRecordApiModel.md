# StreamRecordApiModel

## На какой вопрос отвечает этот файл

Какую форму имеет запись потока живых данных, которую периметр отдаёт браузеру?

## Назначение

`StreamRecordApiModel` — единственная форма, которую `bff` кладёт в поток
живых данных (`GET /api/v1/bff/stream`, SSE). Форма порождена периметром и
принадлежит ему. Доменного класса в ней нет ни в одном поле: содержимое факта —
всегда форма периметра из перечня ниже.

Признак, по которому компонент владельца попадает в форму периметра, и довод
«порождённое — своё, пересланное — чужое» живут в
`docs/architecture/contracts.md` (периметр объявляет состав своей формы сам).
Здесь — сам состав.

## Поля записи

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `String` | Идентичность записи. У факта — идентичность события; у записи периметра пусто. |
| `type` | `String` | Класс записи: имя класса события у факта либо слово записи периметра (`PERIMETER_PULSE`, `PERIMETER_GAP`). |
| `occurredAt` | `OffsetDateTime` | У факта — момент происшествия из конверта события; пусто, когда момент не добыт. У записи периметра — момент отправки. Время UTC. |
| `content` | объект | Содержимое факта в форме периметра (раздел «Содержимое факта»). У пульса и разрыва пусто. |

## Кадр SSE

Запись уходит одним событием протокола: имя события — `type`, данные — запись
целиком в JSON, идентичность события протокола — `id`, когда она непуста. У
записи периметра идентичности протокола нет, и `Last-Event-ID` на неё не
указывает.

Открытие подписки подтверждается комментарием протокола `PERIMETER_OPENED`.
Это не запись: класса у него нет, в окно переигрывания он не попадает.

## Роды записи

| Род | `type` | `id` | `occurredAt` | `content` | Писатель и момент |
|---|---|---|---|---|---|
| факт | класс события | идентичность события | момент происшествия либо пусто | форма содержимого класса | слушатель тем периметра, на каждом принятом событии тенанта, у которого есть подписка либо окно |
| пульс | `PERIMETER_PULSE` | пусто | момент отправки | пусто | тик пульса, каждый такт, пока у реплики есть подписки и потребитель тем жив |
| разрыв | `PERIMETER_GAP` | пусто | момент отправки | пусто | реестр подписок, при открытии подписки, чья позиция не нашлась в окне переигрывания |

В окно переигрывания ложатся только факты. Правила окна, пульса, разрыва,
дедупликации у клиента и нижней границы дочитывания — `docs/architecture/contracts.md`
(живые данные в браузер). Перечень классов событий и их производители — там
же.

Факт, у которого нет тенанта, идентичности либо класса, чей класс периметру
неизвестен, чьё содержимое не разобралось объявленной формой либо чей момент
происшествия предъявлен и не разбирается, в поток не уходит вовсе.

## Содержимое факта

| Класс события | Форма `content` |
|---|---|
| `ORDER_DECIDED` | `OrderDecidedStreamApiModel` |
| `ALGO_ORDER_DECIDED` | `AlgoOrderDecidedStreamApiModel` |
| `DEAL_OPENED` | `DealOpenedStreamApiModel` |
| `DEAL_SHUTDOWN_INITIATED` | `DealShutdownInitiatedStreamApiModel` |
| `DEAL_CLOSED` | `DealClosedStreamApiModel` |
| `HOLD_RAISED` | `HoldRaisedStreamApiModel` |
| `HOLD_RELEASED` | `HoldReleasedStreamApiModel` |
| `ANOMALY_REPORTED` | `AnomalyReportedStreamApiModel` |
| `STRATEGY_ACTIVATED` | `StrategyActivatedStreamApiModel` |
| `STRATEGY_DEACTIVATED`, `STRATEGY_DELETED` | `StrategyLifecycleStreamApiModel` |

Все формы содержимого — записи из строковых полей. Поле, чьё значение — имя
значения доменного перечня, несёт это имя, а область значений живёт в доме
перечня, названном у поля. Десятичные числа едут строкой. Пустое поле значит,
что значения у события нет (`docs/rules/absent-value-semantics.md`).

Каждую форму пишет маппер периметра из содержимого события; компонент, которого
форма не объявила, к браузеру не едет. Актора хода не несёт ни одна форма.

### `OrderDecidedStreamApiModel` — решение о заявке

| Поле | Назначение |
|---|---|
| `orderInternalId` | Идентичность заявки. |
| `dealInternalId` | Идентичность сделки, к которой относится заявка. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента. |
| `replacesInternalId` | Идентичность предшественника в цепочке замещений; пусто — первичная постановка. |
| `orderType` | Бизнес-тип заявки — значение `Order.Type` (`docs/models/domain/core/Order.md`). |
| `direction` | Сторона заявки — значение `Order.Side` (там же). |
| `plannedSizeContracts` | Запланированный объём в контрактах, десятичное число. |
| `plannedEntryPrice` | Запланированная цена размещения, десятичное число; пусто у рыночной заявки. |

### `AlgoOrderDecidedStreamApiModel` — решение об отдельной условной заявке

| Поле | Назначение |
|---|---|
| `algoOrderInternalId` | Идентичность условной заявки. |
| `dealInternalId` | Идентичность сделки, к которой относится заявка. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента. |
| `replacesInternalId` | Идентичность предшественника в цепочке замещений; пусто — первичная постановка. |
| `conditionType` | Тип условия — значение `AlgoOrder.ConditionType` (`docs/models/domain/core/AlgoOrder.md`). |
| `direction` | Сторона — значение `AlgoOrder.Direction` (там же). |
| `sizeContracts` | Размер в контрактах, десятичное число. |
| `stopLossTriggerPrice` | Уровень триггера остановки убытка, десятичное число; пусто, когда такой ноги у условия нет. |
| `takeProfitTriggerPrice` | Уровень триггера фиксации прибыли, десятичное число; пусто, когда такой ноги у условия нет. |

### `DealOpenedStreamApiModel` — сделка создана

| Поле | Назначение |
|---|---|
| `dealInternalId` | Идентичность сделки. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента. |
| `entryReason` | Причина заведения сделки — значение `Deal.EntryReason` (`docs/models/domain/aggregate/Deal.md`). |
| `direction` | Направление сделки — значение `StrategyTradeDirection` (`docs/models/domain/aggregate/Strategy.md`). |

### `DealShutdownInitiatedStreamApiModel` — сделка перестала вестись штатно

| Поле | Назначение |
|---|---|
| `dealInternalId` | Идентичность сделки. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента. |
| `status` | Состояние, в которое сделка ушла, — значение `Deal.Status` (`docs/models/domain/aggregate/Deal.md`). |
| `shutdownReason` | Причина выхода из штатного ведения — значение `Deal.ShutdownReason` (там же). |

У одной сделки таких записей бывает больше одной, и каждая описывает своё
происшествие: клиент различает их по `id` записи, а не по сделке.

### `DealClosedStreamApiModel` — сделка закрыта

| Поле | Назначение |
|---|---|
| `dealInternalId` | Идентичность сделки. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента. |
| `status` | Терминальное состояние сделки — значение `Deal.Status` (`docs/models/domain/aggregate/Deal.md`). |
| `closeReason` | Причина закрытия — значение `Deal.CloseReason` (там же). |
| `result` | Финансовый исход сделки, десятичное число; смысл величины — итоговый результат сделки (там же). |
| `resultCurrency` | Валюта исхода; пусто — число есть, а валюта неизвестна. |

### `HoldRaisedStreamApiModel` — ступень блокировки поднята

| Поле | Назначение |
|---|---|
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента; пусто, когда радиус инструмента не называет. |
| `scope` | Радиус блокировки — значение `HoldScope` (`docs/components/models/HoldSignal.md`). |
| `rung` | Поднятая ступень — значение `HoldRung` (там же). |
| `code` | Машинный код причины. |

### `HoldReleasedStreamApiModel` — ступень блокировки снята

| Поле | Назначение |
|---|---|
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента; пусто, когда радиус инструмента не называет. |
| `scope` | Радиус снятия — значение `HoldScope` (`docs/components/models/HoldSignal.md`). |
| `rung` | Снятая ступень — значение `HoldRung` (там же). |

Кода причины у снятия нет: основание одно, направление называет `type` записи.

### `AnomalyReportedStreamApiModel` — отчёт о происшествии заведён

| Поле | Назначение |
|---|---|
| `anomalyReportInternalId` | Идентичность отчёта о происшествии. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента; пусто, когда радиус инструмента не называет. |
| `scope` | Радиус происшествия — значение `HoldScope` (`docs/components/models/HoldSignal.md`). |
| `severity` | Тяжесть происшествия — значение `AnomalyReport.Severity` (`docs/models/domain/other/AnomalyReport.md`). |
| `code` | Машинный код класса происшествия (там же). |

### `StrategyActivatedStreamApiModel` — определение активировано

| Поле | Назначение |
|---|---|
| `strategyInternalId` | Идентичность определения стратегии. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента. |
| `name` | Имя определения, видимое человеку; берётся из снимка определения в содержимом события. |

Дерева определения форма не несёт: определение браузер читает у владельца
проксируемой поверхностью (`docs/models/domain/aggregate/Strategy.md`).

### `StrategyLifecycleStreamApiModel` — определение деактивировано либо удалено

| Поле | Назначение |
|---|---|
| `strategyInternalId` | Идентичность определения стратегии. |
| `exchangeAccountInternalId` | Идентичность биржевого счёта. |
| `instrumentInternalId` | Идентичность инструмента. |

Какое из двух происшествий случилось, называет `type` записи.

## Границы

- Отказ точки подписки идёт единым error-DTO поверхности, а не этой формой
  (`docs/rules/error-handling-policy.md`).
- Билет, открывающий подписку, — `SubscriptionTicketApiResponse.md`.
- Периметр, поток и билет — `docs/architecture/contracts.md`;
  сервис — `docs/architecture/services/bff.md`.
