# InstrumentExternalRulesService

## На какой вопрос отвечает этот файл

Кто отдаёт внешние правила инструмента (компонент-сервис): что
возвращает.

## Назначение

`InstrumentExternalRulesService` отдаёт актуальные
`InstrumentExternalRules` (см.
`docs/models/domain/other/InstrumentExternalRules.md`) из БД. Сам правила не
запрашивает у биржи — их обновляет
`docs/components/InstrumentExternalRulesSyncJob.md`.

## Использование

Калькуляторы читают правила для округления цены/размера, расчёта размера
в контрактах (`ctVal`/`lotSz`/`minSz`), проверки min/max limits,
биржевого max leverage и торгуемости инструмента (`status`). Если
актуальных правил нет — это блокирующее условие для расчёта
data-dependent action (controlled `CalculationError`).
