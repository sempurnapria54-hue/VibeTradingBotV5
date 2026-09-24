# Фокус `test` шага 12: проход по юнитам `market-data`, `platform` и навеса JSONB

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по юнитам `market-data`, общих артефактов и навеса JSONB 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 146; вернулся
после конверта сессии. Находки проверены им чтением кода; заходом, пишущим
отчёт, перепроверяются. Документы кейсов — `.claude/tests/cases/market-data-indicators.md`,
`platform-shared-logic.md`, `jsonb-overlay-roundtrip.md`. «Спорная» — оценка
прохода, довод в строке.

## Находки

**`market-data`, юниты расчёта:**

1. **U17.1, U17.2 — невалидность.** `services/market-data/src/test/java/com/example/marketdata/unit/calculation/CalculationLayerBoundariesTest.java:62-77`:
   клейм «в базу не пишет, соседу не звонит» читается рефлексией по полям;
   поле экземпляра есть у одного класса из десяти, гейта непустоты нет, вызовы
   в теле не видны (`.claude/skills/test-code.md` §«Уровень 2»). Правка: тело
   исходников без комментариев с гейтом непустоты.
2. **U17.3 — невалидность.** Там же `:89-92`: `doesNotContain("log")` по именам
   полей не видит `LOGGER` и `System.out`. Правка: греп тела исходника.
3. **U17.5, U15.9 — детерминизм.** Там же `:106-108`;
   `StructureConservativeOutcomeTest.java:160-162`: ожидание на
   `OffsetDateTime.now()`. Правка: сравнивать с `barAt(k)`; «часов не читает» —
   грепом исходника.
4. **U17.5, U17.6 — невалидность.** Там же `:103`, `:118`: проверен только
   первый калькулятор, а javadoc класса обещает все десять.
5. **U1.2 — невалидность, спорная.** `CalculatorContractTest.java:51-52, 78-83`:
   `2 * PERIOD` списан с реализации, а U3.5 объявляет это число без ожидания.
6. **U16.10 — невалидность, спорная.** `PhaseClauseOrderTest.java:139-148`: два
   вызова с одним входом не ловят кэширующий резолвер.
7. **U15.3 — невалидность, мелкая.** `StructureConservativeOutcomeTest.java:79-86`:
   не проверены `getWindowEndAt()`, `getConfirmedAt()`.

**`common/platform`:**

8. **U1.7 — невалидность.** `services/common/platform/src/test/java/com/example/platform/jobs/JobExecutionGuardTest.java:161-168`:
   тик другим ключом свободен при любом поведении — «до взятия замка» не
   различено.
9. **U1.1, U14.8 — детерминизм.** Там же `:66, 228, 243, 256, 270, 275`:
   `await()`/`join()` без таймаута — регрессия в `lock` повесит прогон.
10. **U4.2 — невалидность.** `…/exception/handler/AccessDenialHandlerTest.java:114, 122-125`:
    `isNotEqualTo(401)` при начальном `-1` зелен и без вызова писателя.
11. **U4.3 — форма.** Там же `:138-143`: из тела проверен только `code`;
    `verify(recorder, never())` на непереданном моке тавтологичен.
12. **U4.8, U5.6 — невалидность.** Там же `:198-202`, `:281-283`:
    `isInstanceOf(RuntimeException.class)` принимает любой отказ.

**`common/test-support`, контракты копий:**

13. **U9.9 — невалидность.** `services/common/test-support/src/main/java/com/example/testsupport/ServiceTokenProviderContract.java:171-173`:
    проверено отсутствие одного ключа атрибутов, утечка под другим не видна.
14. **U14.5 — невалидность.** Там же `:206-208`; то же в
    `services/market-data/src/test/java/com/example/marketdata/unit/integration/ServiceTokenProviderTest.java:127-130`:
    на этой тропе токена нет, проверка тавтологична.
15. **U12.1-U12.3 — форма, спорная.** `PeerCallContract.java:245-247`,
    `ServiceTokenProviderContract.java:185-190`, `ActorContextPropagationContract.java:187-189`:
    сверка копий на своём дереве тавтологична (`.claude/skills/test-code.md`
    §«Уровень 2»).
16. **U10.2, U10.3 — невалидность, спорная.** `ServiceTokenProviderTest.java:81, 100`
    (`market-data`): ассерты `debt` ослаблены до `isNotInstanceOf` и префикса пакета.

**Навес JSONB:**

17. **U12.1 — невалидность.** `services/trading-core/src/test/java/com/example/tradingcore/unit/mapping/RuntimeJsonConverterTest.java:303-306`:
    «нет ввода-вывода» проверено у трёх исходников ядра; `assertNoRuntimeIo`
    не зовётся в `market-data` и `strategies` — четыре класса не проверены.
18. **U12.1 — невалидность.** `services/common/test-support/src/main/java/com/example/testsupport/JsonbOverlayProbe.java:48-51`:
    шаблон `Slf4j|\blog\.` не видит `Logger`, `LOGGER.`, `System.out`;
    комментарии не сняты.
19. **U9.10 — невалидность.** `services/market-data/src/test/java/com/example/marketdata/unit/mapping/ComputationParamsJsonConverterTest.java:171-174`:
    `isNotEqualTo` зелен и при потере ключа.
20. **U10.3 — форма, спорная.** `InstrumentRulesOverlayCopyContract.java:127-132`:
    сравнение двух литералов порта своего дерева.
21. **U4.3 — форма, спорная.** `StrategyOverlayCopyContract.java:156-159`:
    `values().hasSize(8)` пинит размер перечня, а не отсутствие ветви `default`.

Моков доменных моделей и `Thread.sleep` нет; метка `debt` стоит на всех
красных клетках, названных документами.

## Охват прохода

Прочитаны целиком: `CalculatorContractTest`, `StructureConservativeOutcomeTest`,
`PhaseClauseOrderTest`, `CalculationLayerBoundariesTest`, `JobExecutionGuardTest`,
`ActorProviderTest` (без находок), `AccessDenialHandlerTest`, `PeerCallContract`,
`ServiceTokenProviderContract`, `JsonbOverlayProbe`, `RuntimeJsonConverterTest`,
`ComputationParamsJsonConverterTest`. Частично: `ServiceTokenProviderTest`
(`market-data`), `StrategyOverlayCopyContract`, `StrategyJsonConverterTest` в
обоих деревьях, `OrderBookLevelJsonConverterTest`. Ящик `market-data` и
сквозной набор этим проходом не покрыты.
