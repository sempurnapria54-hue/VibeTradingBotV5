# Инвентаризация донора: что из него живо и что ещё не взято

## На какой вопрос отвечает этот файл

Что в `donor/` ещё используется живым и что ещё не взято в сервисы.

## Момент и метод

Замер 2026-09-16 по указанию держателя. **Ничего не удалено и не
перенесено** — это перечень с исходом по каждой позиции.

Метод и его граница названы: соответствие донорского класса сервисному
устанавливалось **по имени файла**, а несовпавшие имена (37 из 412)
разбирались поимённо — грепом по предмету в `services/**`. Объявление
«область портирована» живёт в хрониках закрывших шагов (`donor/README.md`),
и этот замер их не читал: он мерит **фактику деревьев**, а не объявления.

```bash
find donor/src/main/java -name '*.java' | wc -l          # 412
find donor/src/test/java -name '*.java' | wc -l          # 115
find donor/src/test/java -path '*sourceapi*' -name '*.java' | wc -l   # 65
ls donor/src/main/resources/db/migration | wc -l         # 26
grep -rl 'donor/' --include='*.md' --include='*.py' --include='*.sh' \
  --include='*.json' --include='*.java' --include='*.xml' \
  .claude docs services tools deploy | grep -v /history/  # 31 файл
grep -c 'нет-файла:donor/pom.xml' .claude/work/backlog.md # 4 секции
```

## 1. Что в доноре используется живым

| Позиция | Кто и как использует | Исход |
|---|---|---|
| Модуль `donor` в реакторе | `pom.xml` корня (строка `<module>donor</module>`), `tools/reactor-test.sh` чистит и собирает его классы вместе с остальными | **ещё нужно**: гейт «донор собирается и зелёный» держится этим прогоном |
| Контурные тесты `integration/sourceapi/okx/**` | `tools/preconditions-check.sh` берёт этот каталог путём по умолчанию и сверяет «реестр == план == фактика» | **ещё нужно** — и прямо связано с п. 1 пересмотра границы: тропа контура живёт только здесь |
| `strategy-examples/trend-following-ema.json` | `docs/spec/strategy-reference.json` и `docs/spec/strategy-walkthrough.json` называют **донорский путь** операндом `strategy` | **ещё нужно**: адаптированная копия уже лежит у `strategies` (`src/test/resources/strategy-examples/`), указатель спеки на неё не переведён |
| Пути донорских файлов в популяциях снятых редакций | `tools/retired-check.py` (строки популяций и их носители) | **ещё нужно**: популяция снимается вместе со своей записью, а не отдельно |
| Условие `нет-файла:donor/pom.xml` | четыре секции `.claude/work/backlog.md` (три донорских остатка общей библиотеки, указатели javadoc); пятая — §«Код-тесты контура источника в доноре» — ждёт исчезновения базового класса контура | **ещё нужно**: удаление донора и есть их оживитель |
| `application-prod.yaml` (ключи `risk-appetite`) | `.claude/work/prod-checks.md` §«Боевые числа риск-аппетита…» называет их как вторую площадку тех же чисел | **ещё нужно** до назначения боевых чисел |
| Упоминания донора в доках и ролях | `CLAUDE.md`, `.claude/rules/structure.md`, `docs/architecture/platform.md`, `docs/rules/api-access-policy.md`, `docs/models/domain/core/Exchange.md`, четыре роли `.claude/agents/**` | **ещё нужно** как описание положения дел; снимается ходом удаления |
| `tools/doc-pointer-check.py` | `donor/**` объявлен **вне области** намеренно | **было мёртво** для прогона: указатели донора не мерятся |

## 2. Код `src/main` — 412 классов

**375 имён** встречаются в `services/**` — взяты (порт шёл по классам, имена
в основном сохранены). Разобраны поимённо **37 несовпавших**:

| Группа (донорские классы) | Исход |
|---|---|
| `TradingBotApplication`, `OkxConfig`, `ApiAccessProperties`, `ApiAccessSecurityConfig`, `AsyncSecurityContextConfig`, `ErrorApiResponseFactory` | **было мёртво**: форма монолита. У каждого сервиса свой класс приложения, свой контур доступа (bearer вместо Basic) и общий `ErrorApiResponse` в `common/model/api` |
| `RiskAppetiteProperties`, `RiskAppetiteStartupCheck` | **взято иначе**: числа риск-аппетита уехали в базу (`tenant_risk_appetites`), читатели — `trading-core` и `strategies` (`TenantRiskAppetiteReader`) |
| `IntegrationService`, `OkxIntegrationService`, `OkxPositionStatusResolver` | **взято под другим именем**: `ExchangeGateway` / `OkxExchangeGateway` у коннектора, `PositionStatusResolver` у ядра |
| `DealFsmHandler`, `TrancheFsmHandler`, `DealFsmSupport` | **взято**: разложено в `trading-core/domain/fsm/**` (`DealTransitionGate`, `DealActiveHandler` и соседи) |
| `MarketConditionContextFactory` | **взято под другим именем**: `CalculationContextFactory` у ядра |
| `HoldClearanceGate` | **взято, растворено**: предикат снятия жёсткой ступени — `DealTerminalGate.riskProvenAbsent`, вызывается из `ManualHaltService` |
| `DealRiskNumbersWriter`, `StrategyStepEligibility` | **взято, растворено** в исполнителях ядра (`domain/command/risk/**`, `domain/command/strategy/**`) |
| `InstrumentService`, `CreateInstrumentApiRequest`, `CandleGroupController`, `InstrumentExternalRulesSyncJob` (+ фасад, + свойства) | **взято, слито**: `InstrumentCatalogService`, `InstrumentController`, `CandleGroupApiResponse`, синк правил внутри `InstrumentSyncJob` у `market-data` |
| `StrategyService`, `StrategyCreateRequestValidator` | **взято под другим именем**: `StrategyCreationService`, `StrategyDefinitionValidator` у `strategies` |
| `ClientIdGenerator` | **взято, растворено**: `clOrdId`-маркер ставит коннектор (`OkxRestClient` и формы запроса); у ядра слова `clOrdId` нет вовсе |
| `ExchangeEntity`, `ExchangeRepository`, `ExchangeDataService`, `ExchangeService`, `ExchangeMapper`, `ExchangeController`, `ExchangeApiResponse`, `CreateExchangeApiRequest` | **ещё нужно**: справочника площадок у `auth` нет — схема не заведена (`.claude/work/backlog.md` §«Справочник площадок обещан шагом 4, а схемы у `auth` нет») |
| `OkxProxyController`, `OkxRawApiRequest` | **развилка, а не исход** — см. §«Что требует хода держателя» |

## 3. Тесты `src/test` — 115 файлов

- **65 файлов контура источника** (`integration/sourceapi/okx/**`, база
  `OkxSourceApiLiveTestBase`) — **ещё нужно**: это единственный носитель кода
  контура; его судьбу держатель решает после изложения понимания концепции.
  Своя секция бэклога у них есть (§«Код-тесты контура источника в доноре»,
  оживитель — исчезновение базового класса).
- **50 юнит-тестов.** Два имени совпадают с сервисными
  (`AccessDenialRowTest`, `DealRiskNumbersTest`), остальные 48 — нет:
  сервисы писали свой набор под своими именами (`trading-core` — 50 тестов,
  `connector-okx` — 6, `market-data` — 8, `strategies` — 7). Исход:
  **предмет взят, кейс — нет**. Донорский тест закреплял поведение монолита;
  сервисный набор покрывает те же предметы иначе, и **совпадение покрытия
  никем не мерено** — это и есть вход под-шага 1 `CODE` шага 12 (кейсы), а
  не повод переносить файлы.
- Отдельно называю три донорских теста, чей предмет в сервисах **не
  построен**: `ApiAccessSurfaceTest` (Basic-контур монолита — **было
  мёртво**), `RiskAppetitePropertiesBindingTest` (числа уехали в базу —
  **было мёртво**), `ExchangeServiceTest` (справочник площадок — **ещё
  нужно** вместе с кодом из п. 2).

## 4. Ресурсы

| Позиция | Исход |
|---|---|
| `db/migration/**` — 26 миграций монолита | **было мёртво**: у каждого сервиса своя цепочка с `V1`, схема донора объявлена историей (`donor/README.md`) |
| `application.yaml`, `application-test.yaml` | **было мёртво**: конфигурация монолита, у сервисов своя |
| `application-prod.yaml` | **ещё нужно** до назначения боевых чисел риск-аппетита (п. 1) |
| `strategy-examples/trend-following-ema.json` | **взято** копией у `strategies` (расходятся первой строкой — копия адаптирована под новый контракт); **ещё нужно** перевести операнд двух спек на копию |
| `tradingbot.iml`, `target/**` | **было мёртво**: артефакты IDE и сборки |

## 5. Знание

`donor/README.md` — живое: держит условия жизни донора (собирается и
зелёный, новой способности нет, портированная область заморожена, схема —
история, секреты на своей раскладке). **Ещё нужно** до удаления каталога.

Продуктового знания в `donor/` нет по правилу размещения
(`.claude/rules/structure.md`): доки живут в `docs/`.

## Что требует хода держателя

1. **`OkxProxyController` + `OkxRawApiRequest` (`/raw`-passthrough).** Карта
   владельцев (`.claude/rules/knowledge-ownership-by-service.md`, строка
   `docs/models/api/`) объявляет: сырой вызов источника «отходит коннектору
   **ходом удаления донора**». Пересмотр границы 2026-09-16 говорит: отдельной
   тест-обращённой поверхности в сервисах **не строится**. Два носителя
   расходятся; чей текст верен — ход держателя, и от него же зависит судьба
   кода контура (65 файлов) и его знания.
2. **Совпадение покрытия донорских юнит-тестов сервисным набором не мерено
   ничем** (п. 3). Это не дефект донора, а предмет под-шага 1 `CODE`: кейсы
   пишутся от построенного, а донорский набор — вход-ориентир.
