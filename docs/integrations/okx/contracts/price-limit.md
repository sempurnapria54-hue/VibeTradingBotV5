# OKX contracts: ценовые лимиты инструмента

## На какой вопрос отвечает этот файл

Каков контракт операции чтения ценовых лимитов (`price-limit`).

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, раздел «Public
Data → REST API», секция «Get limit price»). Процедура сверки и
приоритет офдока — `.claude/rules/external-source-sync.md`. Последняя
сверка: 2026-06-11 (поле-уровневая дистилляция).

## Статус использования

Не используется (в фазе 1). Динамический `[sellLmt, buyLmt]` требует
live-вызова, а `RiskValidator` фазы 1 в биржу не ходит и кода ценового
бэнда не несёт (`RiskCheckResult.md`); ордер с ценой вне лимитов биржа
отклонит (или скорректирует при `pxAmendType=1` — см. `order.md`).
Предварительная сверка цены с лимитами в преконтроль не берётся: отказ
площадки по цене вне лимитов громкий, его разбирает граница коннектора
классификацией отказа команды (`docs/rules/runtime-error-classification.md`),
и риска сверх объявленного он не создаёт. Решено на шаге 5
(`docs/models/domain/other/InstrumentExternalRules.md`); подтверждено
разбором кандидатов преконтроля
(`.claude/decisions/server-side-precheck-not-adopted.md`).

## GET /api/v5/public/price-limit

Rate limit 20 req / 2 s по IP. Query: `instId` (обяз.).

### Response (`data[0]`)

| Поле | Семантика |
|---|---|
| `buyLmt` | Максимальная цена buy-ордера; `""` при `enabled=false`. |
| `sellLmt` | Минимальная цена sell-ордера; `""` при `enabled=false`. |
| `enabled` | Действует ли сейчас ценовой лимит (Boolean). |
| `instType` / `instId` | Идентификация инструмента. |
| `ts` | Время данных. |
