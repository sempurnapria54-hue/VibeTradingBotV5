# Фокус `test` шага 12: проход по `strategies`, движку стратегии и доменным моделям

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по тестам `strategies`, `common/strategy-engine` и `common/model/domain` 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 146; вернулся
после конверта сессии. Находки проверены им чтением кода; заходом, пишущим
отчёт, перепроверяются. Строки кейсов — `.claude/tests/cases/strategies.md`,
`strategy-definition-validation.md`, `strategy-engine-condition.md`.

## Находки по коду тестов

1. **U7.11, U7.12 — невалидность, следствие правки T6.** `services/common/strategy-engine/src/test/java/com/example/strategy/engine/unit/condition/MarketStructureRuleTest.java:124-130`,
   `:135-140`: правый операнд пуст — новая охрана `isNull(declared)` в
   `evaluateRangeBreakout` даёт ложь раньше проверяемой оси. Правка: правым
   операндом `enumConstant("UP")`.
2. **B9.1 — невалидность.** `services/strategies/src/test/java/com/example/strategies/box/UnconfiguredAccessContourTest.java:39-43`:
   `isInstanceOf(Exception.class)` — причина отказа не установлена, а пустой
   `issuer-uri` старт сам не роняет (ловушка CS-013). Правка: корневая причина
   плюс контрольный подъём с издателем.
3. **B9.5, второй носитель B5.1 — невалидность.** `box/SchemaInputBoxTest.java:64-90`,
   `box/DefinitionActivationBoxTest.java:76-79`: засчитан любой отказ базы, имя
   ограничения не проверено.
4. **B9.4 — невалидность (тавтология).** `SchemaInputBoxTest.java:51-53`:
   множественное число проверено на константе теста, а не на таблицах базы.
5. **B6.5 — невалидность.** `box/DefinitionEventBoxTest.java:174-176`: смещение
   `Z` у `timestamptz` — свойство драйвера. Правка: мгновение между отметками до
   и после перехода.
6. **B3.2 — невалидность.** `box/DefinitionReadBoxTest.java:203-209`:
   неразличимость «нет» и «чужое» не проверена по телу.
7. **B3.1 — половина выхода.** Там же `:184-188`: порядок шагов и действий не
   проверен.
8. **B1.1, B7.4 — невалидность.** `box/DefinitionIntakeBoxTest.java:36-59`,
   `box/UnavailableBrokerBoxTest.java:76-92`: «в тему не публикуется ничего» не
   утверждено.
9. **B8.2 — невалидность.** `box/AccessContourBoxTest.java:81-84`:
   `path.contains("token")` пропускает `/token/introspect`.
10. **B4.5 — невалидность.** `box/DefinitionTransitionBoxTest.java:132-134`:
    строка другого класса в outbox прошла бы.
11. **U10.9 — форма.** `RiskWithinGlobalTest.java:136-138`: клетка — два
    нарушения, ассерт — четыре (под `debt`).
12. **U12.3 — половина выхода.** `NotionalHeadroomTest.java:77-81`: оба числа в
    тексте не проверены.
13. **U28 — форма.** `RuleContractTest.java:111`, `124`, `138`: путь до узла не
    проверен, хотя документ (`strategy-definition-validation.md:77-97`) называет
    его несущей величиной.
14. **B6.9 — форма.** `DefinitionEventBoxTest.java:272-275`: у предусловия
    проверен только `status != 200`, код отказа — нет.
15. **Шапка U28 устарела — форма.** `RuleContractTest.java:26-31`,
    `strategy-definition-validation.md:894`: «дома нет (F6)», а клетки уже
    ссылаются на `docs/rules/strategy-validation.md`.

## Вне кода тестов — владельцам

- **Возможный торговый дефект интерпретатора, адресат `code-writer`.** Пустое
  направление у события пробоя при правиле `NE UP` даёт **истину**:
  `hasConfirmedBreakout(UP)` ложно, `NE` его инвертирует
  (`StrategyConditionEvaluator.evaluateRangeBreakout`). Против javadoc
  `MarketStructure#hasConfirmedBreakout` и консервативной лжи
  `docs/rules/absent-value-semantics.md`. Клетки нет. Перекликается со
  входом фокусу `divergence` захода 145 («оператор не проверяется»).
- **Пробелы покрытия:** нет клетки у `MarketStructure#hasConfirmedBreakout(Direction)`;
  не проверена ветвь равных моментов у `MarketPriceLevel#isSupersededBy`; в
  расчётном документе нет клетки «стоп и размещение от последнего
  подтверждённого свинга» — только на уровне модели.
- **Таблица второго рубежа** `strategy-engine-condition.md:578` не обновлена
  после T6: у пробоя нет направления и оператора.
- **Трейлинг и последний подтверждённый уровень расхождений не дали** — U6.5,
  U6.7, U18.17, U18.26, U18.27 совпадают с клетками.

## Охват прохода

Все классы ящика `strategies`; юниты `RuleContractTest`, `RiskWithinGlobalTest`,
`NotionalHeadroomTest`, `MarketStructureRuleTest`, `TakeProfitAndTrailingTest`,
`StopLossLevelTest`, `CrossoverTest`, `NotRequiredCalculationTest`,
`MarketModelsTest`, `AlgoOrderTest`, `ConditionGrammarTest`,
`OrderAndAttachedTest`. `@MockitoBean`, моков моделей, `sleep`, `now()` и
случайности нет.
