# InstrumentExternalRulesDataService

## На какой вопрос отвечает этот файл

Кто отдаёт внешние правила инструмента (компонент — граница
domain ↔ persistence): что возвращает, как хранит.

## Назначение

`InstrumentExternalRulesDataService` — граница domain ↔ persistence для
`InstrumentExternalRules` (см.
`docs/models/domain/other/InstrumentExternalRules.md`). Отдаёт актуальные
правила из БД (`findByInstrumentId` → `Optional`, пусто — навес ещё не
материализован) и сохраняет/обновляет их (`save`). Сам правила у биржи
не запрашивает — их обновляет
`docs/components/InstrumentExternalRulesSyncJob.md`.

Хранение — JSONB-навесом на строке-владельце `instruments` (собственной
таблицы у правил нет): чтение через проекцию навеса (без вытягивания всей
сущности), запись через load-modify строки-владельца (чтобы audit-поля
инструмента обновлялись штатным JPA auditing).

## Использование

Читают правила `CalculationContextFactory` (кладёт в
`CalculationContext`) и `RiskValidator` (напрямую) — для округления
цены/размера, расчёта размера в контрактах (`ctVal`/`lotSz`/`minSz`),
проверки min/max limits, биржевого max leverage и торгуемости
инструмента (`status`). Если актуальных правил нет — это блокирующее
условие: фабрика/валидатор возвращают controlled ошибку
(`INSTRUMENT_RULES_MISSING` у `RiskValidator`).
