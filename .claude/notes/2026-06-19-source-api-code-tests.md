# Код-тесты контура source-api (OKX): что сделано, отклонения

## На какой вопрос отвечает этот файл

Что реализовано в код-тестах контура тестов API источника OKX (этап CODE)
и какие отклонения от плана/нерешённое зафиксированы на момент сдачи.

## Что сделано

Реализован полный набор `@SpringBootTest`-код-тестов (исполнитель прогона,
`.claude/skills/test-code.md`) 1:1 с аппрувнутым планом
(`.claude/tests/source-api/okx/plan.md`, 60 эндпоинтов / 310 запросов).

- **Расположение:** `src/test/java/com/example/tradingbot/integration/sourceapi/okx/`.
- **62 файла:** база `OkxSourceApiLiveTestBase` + 60 классов-эндпоинтов
  (M1–M21, TG1–TG9, AG1–AG12, MG1–MG10, PG1–PG8) + offline-probe
  `ICredEmptyCredentialsLiveTest` (I-cred).
- **Структурный изоморфизм:** класс на эндпоинт, тест-метод на кейс плана
  (`@DisplayName` несёт метку кейса `M2.1`/`Climit`/`TG1.1`). Prose-кейсы
  «покрыт цепочкой …» не дублируются (M7.3/7.4, M8.3, M9.3, M10.4, M11.4,
  M12.3, M13.3/4, M14.6, M15.6, M18.3, M20.2, M21.2, TG2.1).
- **Механика — тот же `/raw`:** каждый кейс шлёт конверт
  `{method,path,query,body,signed}` в `POST /api/proxy/okx/raw` и ассертит
  **сырые поля JSON OKX** (`b.code`, `b.data[0].sCode`, `b.data[0].<field>`).
- **Профиль-гейт:** `@ActiveProfiles("test")` + структурный no-prod
  (`@BeforeEach` assumption: на профиле prod кейсы пропускаются). Контекст
  Spring кэшируется между классами.
- **Инвариант восстановления:** каждый stateful-кейс — Snapshot.start →
  … → restore (try/finally) → Verify.end через **wait-until-condition**
  (поллинг, таймаут 25с / интервал 0.5с; не sleep).
- **A0-фикстура:** при реджекте place reduce-only algo открывается min
  market-позиция, place повторяется, позиция закрывается в teardown
  (M19 conditional/oco/trailing, M19.dup, TG9).
- **Компиляция проверена `mvn test-compile`** (Maven 3.9.11, JDK corretto-25)
  против реального SB4-classpath — BUILD SUCCESS, 0 ошибок. (Голый `javac`
  по всему `~/.m2` давал ложный успех: подхватывал стейл-jar SB3 с
  `TestRestTemplate`; авторитетен `mvn`.) Demo-прогон (Vault test-креды +
  demo + поднятый DB) — за пользователем.

## Отклонения от плана (осознанные)

1. **Поллинг везде для схождения.** План помечал `M17.4.canceled` как
   одношаговый best-effort (для коллекции). Код-тесты — исполнитель;
   красная нить «поллинг — в код-тестах» → реализован `waitUntil` для всех
   Verify.end/canceled/filled/flat. Поллинг строго перекрывает one-shot+retry.
2. **Самодостаточность цепочек vs кросс-ссылки плана.** `M17.3` («отмена
   отменённого») в плане ссылается на состояние Climit (M16); реализован
   **самодостаточно** (place → cancel → re-cancel → teardown). `TG4.2`
   (частичный реджект amend-batch) обёрнут в snapshot/place/teardown/Verify.end,
   т.к. требует живого ордера, — чтобы держать инвариант восстановления.
3. **И-2 (cancel-advance-algos) — мягко.** Для M19 trailing/trailing-spread
   и M21.1 шаги cancel/canceled/verify по семье **advance** не жёстко
   фейлят: `observe` + лог находки C3 (плана: «фейл cancel-advance =
   находка»). Следствие: при делистинге эндпоинта возможен остаточный
   advance-algo на demo (документированный residual-state-флаг), а не
   красный тест.
4. **A0-триггер — любой реджект place, не парсинг «нет позиции».** Точный
   код реджекта — наблюдение; узкий матч по коду рискует промахнуться,
   поэтому A0 открывается при любом непринятии place и снимается в teardown.
5. **Реверсивные account-write/`acctLv`** (AG7 posMode, AG8 leverage,
   TG7 acctLv): реджект switch/set/restore — **находка + флаг остаточного
   состояния** (лог), не жёсткий фейл write-шага; Verify.end всё равно
   поллит к старту (инвариант на восстановлении, не на самом switch).
6. **Коды негатива не выдумываются.** Точный код реджекта нигде не
   ассертится — всегда `observe()`/лог (план: код = наблюдение → находка C3).
7. **Бланк отчёта.** Код-тесты дают авто-вердикт pass/fail и **питают**
   бланк через `observe()`/лог + описательные assertion-сообщения; сам
   заполненный отчёт (таблицы плана с колонкой «факт») — артефакт этапа
   RUN (`test-run`), не генерируется здесь.
8. **I-cred — offline-юнит-тест** над `OkxSigningInterceptor` с пустыми
   `OkxProperties` (без Spring/сети): ассертит исключение до сети и
   наблюдает, открыт ли I3 (NPE) или закрыт (внятная ошибка про credentials)
   — не подгоняя вердикт под брешь.
9. **HTTP-механика — JDK `HttpClient` + `@LocalServerPort`**, не
   `TestRestTemplate`. Скилл `test-code.md` упоминает «MockMvc /
   TestRestTemplate», но Spring Boot 4.0.0 **удалил `TestRestTemplate`**
   (и перенёс `@LocalServerPort` в `org.springframework.boot.test.web.server`).
   База шлёт конверт через `java.net.http.HttpClient` (не бросает на
   4xx/5xx — нужно для M1.6 «сломанный конверт»), тело/ответ — свой Jackson
   `ObjectMapper`, ассерты по сырому JSON. **Follow-on:** doc-sync скилла
   `test-code.md` под SB4 (TestRestTemplate→HttpClient/RestTestClient).

## Переделка инфраструктуры (overhaul, тест-слой; production не тронут)

Четыре изменения в `OkxSourceApiLiveTestBase` + Verify.end stateful-кейсов
(`mvn test-compile` — BUILD SUCCESS):

1. **Throttle + rate-limit (в `raw()`).** Перед каждым запросом к бирже —
   пауза-троттл (пол `throttle`, дефолт 1с; **per-case override** полем
   `throttle`), измеряется глобально (`LAST_REQUEST_NANOS`), поэтому
   покрывает и стык между тестами. Rate-limit (HTTP 429 / коды
   `50011`/`50013`/`50061`) → экспоненциальный backoff + повтор (до 4).
   Троттл — единственный пейсер поллинга: `pollUntil` не добавляет свой
   фикс-интервал поверх (только 50мс anti-spin floor), чтобы троттл и
   poll-таймаут не складывались наивно.
2. **Осадка — per-case таймаут.** `pollTimeout` (дефолт 25с) — поле,
   per-case override либо аргумент `waitUntil(desc, timeout, cond)`.
   Троттл и poll-таймаут — два разных per-case числа.
3. **Sweep + halt (`assertRestoredOrHalt`).** Единый Verify.end для
   сущностей и настроек: невозврат → жёсткий фейл + находка C3 (всегда) +
   принудительный sweep (снять/закрыть; настройку — re-set к снапшоту);
   не вычистило → `halt` (статический флаг; последующие кейсы абортятся
   в `@BeforeEach`). 15 call-site'ов (M16×3, M17×2, M19×2, TG1/TG3/TG9×1,
   TG4×2, TG7/AG7/AG8×1). **Переопределяет** прежнее (пп. ниже):
   - *было* «невозврат настройки → находка, не фейл» (AG7/AG8/TG7 soft) →
     *стало* настройки под sweep+halt как сущности (находка пишется);
   - *было* И-2 trailing Verify.end soft (catch→лог) → *стало* sweep+halt
     (cancel-advance не вычистил → halt); находка C3 логируется.
4. **Формат ошибки.** `RawResponse` несёт породивший `RawRequest`;
   `currentCaseId` (из `@DisplayName`/`step()`) — контекст. Все пути фейла
   (assert-хелперы, `SourceApiException` из `raw()`, таймаут `waitUntil`,
   halt) несут `[case=…] METHOD path body/query → ошибка`.

Дефолты throttle/таймаута — стартовые; подкрутка на прогоне. Затронутый
инвариант (настройки под sweep+halt) отражён в
`source-api-target-rebase.md` §Принцип, `plan.md` §Сквозные проверки,
`test-code.md`.

## Детерминированный порядок прогона (halt осмыслен)

JUnit 5 по умолчанию не гарантирует порядок классов/методов — halt был
best-effort. Зафиксирован порядок по структуре плана:

- **Класс на эндпоинт** — `@Order(1..60)`: M1–M21 → TG1–9 → AG1–12 →
  MG1–10 → PG1–8.
- **Метод на кейс** — `@Order(10,20,…)` в порядке плана (= порядок
  объявления в файле).
- **Включение orderers** — `src/test/resources/junit-platform.properties`:
  `junit.jupiter.testclass.order.default=…$ClassOrderer$OrderAnnotation`,
  `…testmethod.order.default=…$MethodOrderer$OrderAnnotation`; база несёт
  `@TestMethodOrder(OrderAnnotation)` (наследуется).
- I-cred (offline-probe, не extends база) — без `@Order`; идёт последним,
  в halt-цепочке не участвует.

Прогон воспроизводим → **halt** («невычищаемое грязное состояние → стоп»)
теперь означает ровно «всё, что после в этом порядке». Аннотации — источник
правды порядка; properties лишь включает orderers (`mvn test-compile` —
BUILD SUCCESS, 60 классов / 199 методов).

## Нерешённое / за пользователем

- **Demo-прогон** (`test-run`): требует Vault test-кредов, demo-аккаунта
  OKX и поднятого Postgres-test; запускается пользователем. Открытые при
  прогоне наблюдения (точные коды реджектов, живость `cancel-advance-algos`,
  состояние бага I3, реальная необходимость A0) — закрываются как
  наблюдения/находки интегратору (C3, правка апидоков).
- **AG5 (`bills-history-archive`)**: POST-кейсы расходуют квоту 12/сутки —
  запускать осознанно (помечено в классе).

## Связи

- Скилл — `.claude/skills/test-code.md`; решение/ре-база —
  `.claude/decisions/source-api-target-rebase.md`.
- План (источник 1:1) — `.claude/tests/source-api/okx/plan.md`.
- Контур доукомплектации апидоков (C3) —
  `.claude/processes/api-docs-completion.md`,
  `.claude/rules/external-source-sync.md`.
