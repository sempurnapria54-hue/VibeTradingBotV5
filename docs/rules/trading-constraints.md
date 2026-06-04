# Торговые ограничения проекта

## На какой вопрос отвечает этот файл

Какое у нас правило: в каком торговом контуре и с какими ограничениями
работает бот.

## Правило

- **Контур:** OKX SWAP/FUTURES.
- **Только свои средства:** без margin borrow, без loan/debt-логики
  (признаки borrow/debt — anomaly, см. `docs/components/AnomalyJob.md`).
- **Режим маржи:** isolated.
- **Плечо:** динамическое — рабочее значение на сделку выводится из
  торговых правил (риск на сделку, инвариант «ликвидация за стопом») и
  рыночных условий (волатильность). Наших потолков нет; единственный
  жёсткий предел — биржевой максимум из `InstrumentExternalRules`
  (`externalMaxLeverage`, см.
  `docs/models/domain/other/InstrumentExternalRules.md`).
- **Позиции:** не более одной позиции на инструмент.
- **Сделки:** максимум одна активная сделка на инструмент, если будущая
  политика явно не разрешит иначе.

## Первоисточник и смежное

Правило сквозное — общесистемные торговые ограничения, единого
владельца-сущности нет (`.claude/decisions/rule-source-of-truth.md`).
Enforcement: проверки плеча/маржи/borrow — risk-layer (`RiskCheckCode`:
`EXCHANGE_MAX_LEVERAGE_EXCEEDED`,
`MARGIN_MODE_NOT_ISOLATED`, `BORROW_OR_DEBT_DETECTED`, см.
`docs/components/models/RiskCheckResult.md`); «одна позиция/инструмент»,
«чужой live risk» — `docs/components/AnomalyJob.md`. OKX-константы
`tdMode=isolated`/`posSide=net` — adapter (см.
`docs/models/mapping/Order.md`).
