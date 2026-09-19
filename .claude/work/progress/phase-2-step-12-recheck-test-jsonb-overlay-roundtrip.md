# Перепроверка кейсов `jsonb-overlay-roundtrip` — круг второй

## На какой вопрос отвечает этот файл

Что нашла перепроверка документа кейсов
`.claude/tests/cases/jsonb-overlay-roundtrip.md` после правки автора.

## Охват

**Предмет — дельта правки автора по находкам первого круга**
(`.claude/work/progress/phase-2-step-12-hand-test-jsonb-overlay-roundtrip.md`
§«Правка по находкам первого круга — передача на перепроверку»). **Круг
второй из трёх** (`.claude/skills/test-review.md` §«Граница петли»): круг —
ревью либо перепроверка с записанным вердиктом, и правка автора между кругами
кругом не являлась. Роль — `reviewer`; прогоняет не автор кейсов.

**Метка своей дельты отбита до первой правки** — `git write-tree` →
`08d62970c52d0d866f615823bf992ac6280e26bb`
(`.claude/rules/edit-kind-obligations.md` §«Предмет обязанностей — дельта
захода»). Дельта круга: этот отчёт, хроника шага, таблица статусов фазы,
снапшот. **Документ кейсов круг не правит** — ревьюер не чинит
(`.claude/skills/test-review.md` §«Назначение и границы»); дерево кода круг
**читал** и **прогонял пробами**, не меняя.

**Ось круга взята встречной к оси правки.** Правка закрыла три гейтящие
находки **сменой базовой сборки** — конвертер собирается на «маппере сборки
бина», а состав ключей строки выводится командой. Круг поэтому спросил у
правки ровно два вопроса: **держится ли измеренное равенство пина бину** (оно
стои́т под каждой из двенадцати групп) и **накрывает ли команда состава ту
популяцию, о которой пишет клейм**. Первый ответ — держится, и круг предъявил
его своим прогоном, а не чтением авторского. Второй дал находку `Ф-3`.

**Что прочитано целиком:**

| Носитель | Зачем |
|---|---|
| все семь классов предмета в трёх деревьях | конструкторы, охраны пустоты, примеси, классы отказа — дом половины ожиданий |
| `InstrumentExternalRules`, `MarketStructureParams`, `IndicatorParams`, `AtrParams`, `StrategyMarketDataExpiredSetting`, `MarketDataExpiredAction`, `RetryError` | предмет `Ф-3`: где лежат формы навеса и какие из них команда состава не берёт |
| `Jackson2AutoConfiguration` 4.0.0 (исходники из `spring-boot-jackson2-4.0.0-sources.jar`) | верна ли модель бина, на которой стои́т пин: `FEATURE_DEFAULTS`, `modulesToInstall`, условия бина |
| `spring-boot-jackson2-4.0.0.pom` | откуда на classpath сервиса берутся три модуля, которые пин добирает служебной загрузкой |
| `docs/models/domain/core/AlgoOrder.md` §«Условие срабатывания» | закрытие `Ф-1`: цитата `U1.9` — дословна ли |
| `docs/models/domain/aggregate/Strategy.md` целиком (заголовки и лид-жирные пассажи) | предмет `F12` и его радиус |
| `.claude/tests/cases/jsonb-overlay-roundtrip.md` — §«Маппер сборки бина…», §«Состав ключей строки…», §«Чем достаются выходы», §«Звенья кода…», §«Поверхность предмета», группы `U1`-`U12`, §«Пробелы покрытия», §«Находки владельцам» | все места, которых правка касалась, плюс их соседи |

## Механические оси круга — команды и их исход

### 1. Счёт документа и уникальность меток

```bash
export LC_ALL=C.UTF-8
grep -coE '^\| U[0-9]+\.[0-9]+ \|' .claude/tests/cases/jsonb-overlay-roundtrip.md
grep -oE '^\| U[0-9]+\.[0-9]+ \|' .claude/tests/cases/jsonb-overlay-roundtrip.md | sort -u | wc -l
grep -cE '^## U[0-9]+ ' .claude/tests/cases/jsonb-overlay-roundtrip.md
grep -coE '^\| Z[0-9]+ \|' .claude/tests/cases/jsonb-overlay-roundtrip.md
```

Исход: 87 строк, **81 уникальная метка**, **12 групп**, **20 звеньев**. Шесть
повторов — строки §«Состояния, недостижимые обычной тропой», где метка
называется второй раз намеренно. Счёт сходится с заявленным автором; счётного
клейма о себе документ по-прежнему не несёт (`.claude/rules/self-description-form.md`).

### 2. Равенство пина бину — прогоном круга, а не чтением авторского

Клейм правки — «пин даёт **ноль** расхождений с бином и совпадающие множества
модулей» — стои́т под базовой сборкой **каждой** группы, кроме `U8`, и однажды
уже оказался ложным (прежняя редакция мерила сборщик в одиночку). Поэтому круг
собрал **свою** пробу: бин моделируется `Jackson2ObjectMapperBuilder` плюс
карта `FEATURE_DEFAULTS`, снятая **из исходников автоконфигурации**, а не из
памяти; пин — ровно та форма, которую документ объявляет.

```bash
cd /c/Users/RomanKrd/IdeaProjects/VibeTradingBotV5
export LC_ALL=C.UTF-8
export JAVA_HOME="$HOME/.jdks/corretto-25.0.3"; export PATH="$JAVA_HOME/bin:$PATH"
M="C:/Users/RomanKrd/.m2/repository"; R="$(pwd -W)"; P="/tmp/probe-recheck2"   # тела проб — §«Тела проб круга»
CP="$R/services/common/model/domain/target/classes;$M/com/fasterxml/jackson/core/jackson-databind/2.20.1/jackson-databind-2.20.1.jar;$M/com/fasterxml/jackson/core/jackson-core/2.20.1/jackson-core-2.20.1.jar;$M/com/fasterxml/jackson/core/jackson-annotations/2.20/jackson-annotations-2.20.jar;$M/com/fasterxml/jackson/datatype/jackson-datatype-jdk8/2.20.1/jackson-datatype-jdk8-2.20.1.jar;$M/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.20.1/jackson-datatype-jsr310-2.20.1.jar;$M/com/fasterxml/jackson/module/jackson-module-parameter-names/2.20.1/jackson-module-parameter-names-2.20.1.jar;$M/org/springframework/spring-web/7.0.1/spring-web-7.0.1.jar;$M/org/springframework/spring-core/7.0.1/spring-core-7.0.1.jar;$M/org/springframework/spring-beans/7.0.1/spring-beans-7.0.1.jar;$M/org/springframework/spring-context/7.0.1/spring-context-7.0.1.jar;$M/org/apache/commons/commons-lang3/3.19.0/commons-lang3-3.19.0.jar;$P"
javac -nowarn -cp "$CP" -d "$P" "$P/R2.java" && java -cp "$CP" R2
```

Выдача:

```
=== PIN vs BEAN diffs=0
    modulesA=[com.fasterxml.jackson.datatype.jdk8.Jdk8Module, jackson-datatype-jsr310, jackson-module-parameter-names]
    modulesB=[com.fasterxml.jackson.datatype.jdk8.Jdk8Module, jackson-datatype-jsr310, jackson-module-parameter-names]
U7.1 core = {"status":"LIVE","externalTickSize":"0.1","externalTakerFeeRate":"0.0005","live":true}
U7.2 md   = {"instrumentId":7,"status":"LIVE","externalTickSize":"0.1","live":true}
U7.1 back instrumentId=null fee=0.0005 status=LIVE
U7.3 equal=true -> {"status":"SUSPEND","externalTickSize":"0.1","live":false}
U9.10 storage   = {"timeframe":"ONE_HOUR","warmup":50,"period":14}
U9.10 canonical = {"period":14,"timeframe":"ONE_HOUR","warmup":50}
```

Проба перебирает **все три** перечня признаков библиотеки (`MapperFeature` на
обеих конфигурациях, `SerializationFeature`, `DeserializationFeature`) и
множества `getRegisteredModuleIds()`. **Что выдача устанавливает:**

- `N-1` закрыта по существу: пин и бин неразличимы ни одним признаком и ни
  одним модулем, то есть базовая сборка документа воспроизводит ту, на которой
  конвертер работает в проде, с точностью до названного пробела `G7`;
- `U7.1`, `U7.2`, `U7.3`, `U10.3` верны дословно: ключ `live` в строке **есть**
  у обеих копий, изъятия у копий разные, на объекте с пустыми изымаемыми полями
  строки **совпадают побайтно**, а обратный ход своей же строки на этой сборке
  **проходит** и возвращает изъятое поле пустым;
- `U9.10` верна: хранимая форма несёт порядок объявления, каноническая —
  алфавитный, и различие ровно в порядке ключей.

**Модель бина проверена по исходникам, а не принята на слово:**

```bash
export LC_ALL=C.UTF-8
T=$(mktemp -d); cd "$T"
unzip -o -q ~/.m2/repository/org/springframework/boot/spring-boot-jackson2/4.0.0/spring-boot-jackson2-4.0.0-sources.jar
grep -n 'FEATURE_DEFAULTS' -A 8 org/springframework/boot/jackson2/autoconfigure/Jackson2AutoConfiguration.java
grep -n 'configureModules' -A 3 org/springframework/boot/jackson2/autoconfigure/Jackson2AutoConfiguration.java
```

Карта `FEATURE_DEFAULTS` несёт **ровно две** записи — `WRITE_DATES_AS_TIMESTAMPS`
и `WRITE_DURATIONS_AS_TIMESTAMPS`, обе `false`; бин строится
`builder.createXmlMapper(false).build()`; модули-бины добавляются
`modulesToInstall((modules) -> modules.addAll(this.modules))`, то есть **сверх**
служебной загрузки, а не вместо неё. Обе оговорки документа — и про
`FEATURE_DEFAULTS`, и про непроизводимые пином `JsonComponentModule` /
`JsonMixinModule` — фактике отвечают.

### 3. Откуда берутся три модуля — не совпадение, а объявленная зависимость

```bash
export LC_ALL=C.UTF-8
grep -A3 '<artifactId>jackson' ~/.m2/repository/org/springframework/boot/spring-boot-jackson2/4.0.0/spring-boot-jackson2-4.0.0.pom
grep -rn 'spring-boot-jackson2' services/*/pom.xml
```

`spring-boot-jackson2` тянет `jackson-datatype-jdk8`, `jackson-datatype-jsr310`
и `jackson-module-parameter-names` **компайл-скоупом** и объявлен у всех семи
сервисов, в том числе у всех трёх деревьев предмета. Значит
`findAndRegisterModules()` пина найдёт на тестовом classpath ровно тот набор,
который соберёт сборщик у бина, — равенство множеств не случайно.

### 4. Своей настройки маппера нет ни у одного сервиса — три оси, а не две

Документ снимает свой довод двумя командами; круг добавил **третью** ось,
которой у них нет по построению, — собственный бин `ObjectMapper` в дереве:

```bash
export LC_ALL=C.UTF-8
grep -rn 'FAIL_ON_UNKNOWN_PROPERTIES\|Jackson2ObjectMapperBuilder' services --include=*.java | grep '/main/'
grep -rn 'jackson' services/*/src/main/resources/application*.y*ml
grep -rlnP '(?s)@Bean[^}]{0,200}ObjectMapper' services --include=*.java
```

Все три печатают пусто. Довод документа («бин приезжает умолчаниями сборщика и
автоконфигурации, а не настройкой сервиса») держится и на третьей оси.

### 5. Цитаты и адреса, которых касалась правка

```bash
export LC_ALL=C.UTF-8
grep -n 'тип плюс параметры триггера' docs/models/domain/core/AlgoOrder.md
grep -n 'MarketStructureParams\|IndicatorParams\|Настройки рыночных данных' docs/models/domain/aggregate/Strategy.md
grep -rln 'Strategy.md (§IndicatorParams' services/common/model/domain/src/main/java
```

Первая печатает строку 119 под §«Условие срабатывания» — цитата `U1.9`
**дословна**, `Ф-1` закрыта. Вторая печатает **одну** строку — заголовок
§«Настройки рыночных данных» (135): ни `§MarketStructureParams`, ни
`§IndicatorParams` в доке нет, и `F12` верна по существу. Третья печатает
**два** файла — отсюда `Ф-4`.

### 6. Три клетки с пометкой «прогоном не проверено» — прогнаны

`U11.4` и `U11.5` прогнал первый круг (`null` даёт пустоту, `""` даёт
`MismatchedInputException` под `JsonProcessingException`). Оставшуюся `U3.5`
прогнал этот:

```bash
javac -nowarn -cp "$CP" -d "$P" "$P/R3.java" && java -cp "$CP" R3
```

```
U3.5 ok -> [abc, 5] types=String
U3.6 -> MismatchedInputException
```

Ожидание `U3.5` («число приводится к строке умолчанием библиотеки») верно;
`U3.6` отказывает классом, который конвертер оборачивает в
`IllegalStateException` (звено Z3). Непроверенных прогоном ожиданий у документа
не осталось — отсюда `Ф-5`.

## Диспозиция находок первого круга — каждая проверена, не принята на слово

| Находка | Класс | Что говорит автор | Что нашёл круг |
|---|---|---|---|
| `N-1` — базовая сборка «свежий маппер» роняет прямой ход `U7` | невалидность | закрыта сменой базовой сборки на маппер сборки бина | **закрыта**; равенство пина бину предъявлено прогоном круга (ось 2), модель бина сверена с исходниками автоконфигурации (ось 2), происхождение модулей — с pom (ось 3) |
| `N-2` — `U5.3` ждёт того, что конструктор запрещает | невалидность | закрыта переворотом кейса: вход — маппер с `Include.ALWAYS`, ожидание — что конструктор пинит политику безусловно | **закрыта**; конструктор обеих копий читается `copy().setDefaultPropertyInclusion(NON_NULL).disable(…)` — без условия, и кейс перестал дублировать `U8.5` |
| `N-3` — «весь выход» `U7` не называет ключ, который пишется | невалидность | закрыта названием ключа `live` в `U7.1`-`U7.3`, `U10.3` и сменой способа вывода состава | **закрыта по клеткам** — ключ назван и подтверждён прогоном круга; **способ вывода закрыт не полностью** — `Ф-3` |
| `Ф-1` — цитата `U1.9` в доке не живёт | форма | цитата приведена дословно | **закрыта**; ось 5 |
| `Ф-2` — `U6.6` говорит «пусто» на различающей оси | форма | ожидание стало «пустота, а не пустой перечень» | **закрыта**; формулировка различает обе стороны и ссылается на `U3.2`, `U3.3`, `U6.2` |
| `G-новый-1` — шесть сигнатур формы параметров структуры рынка не названы | пробел покрытия | закрыт кейсами `U6.8`, `U6.9`, `U9.9` плюс прогон в обеих копиях через `U10.1` | **закрыт**; обе пары публичных входов существуют в коде (`StrategyJsonConverter#marketStructureParamsToJson`/`#jsonToMarketStructureParams`, `ComputationParamsJsonConverter#jsonToMarketStructureParams`/`#toMarketStructureParams`), самостоятельность формы подтверждена — `MarketStructureParams` от `IndicatorParams` не наследует |
| `G-новый-2` — ось канонической формы не спрошена | пробел покрытия | закрыт `U9.10` плюс находкой `F11`; `U9.1` помечена как зелёная и на хранимой форме | **закрыт**; `U9.10` подтверждена прогоном (ось 2), отсутствие карт среди форм конвертера проверено грепом — `Map<` в каталоге форм не встречается ни разу |

## Находки круга

Класс каждой — по `.claude/skills/test-review.md` §«Критерий апрува документа
кейсов»; вердикт выводится по первому классу, а не по числу.

| Метка | Класс | Предмет |
|---|---|---|
| `Ф-3` | форма | популяция команды состава ключей у́же, чем «формы предмета», о которых пишет её клейм |
| `Ф-4` | пробел дока | радиус `F12` у́же дефекта: тот же неразрешимый адрес несут ещё два javadoc |
| `Ф-5` | форма | три пометки «прогоном не проверено» стои́т на клетках, которые прогнаны кругами |

**Невалидности нет ни одной.**

### `Ф-3` — команда состава ключей не накрывает двух форм предмета, а клейм над ней полный

**Что объявлено.** §«Состав ключей строки задаёт не перечень полей»: состав
выводится **грепом публичных нульарных `is`/`get`-методов формы**, и над
выдачей стои́т клейм — «**Выдача разобрана целиком, и предикат-свойство среди
форм предмета ровно один**».

**Чем опровергнуто.** Команда обходит **шесть каталогов одного дерева**
(`services/common/model/domain/.../aggregate/strategy/{setting,action,condition}`,
`.../core/{algo_order,instrument}`, `.../trade/market_snapshot`). Две формы
предмета лежат вне этой популяции **по построению**:

```bash
export LC_ALL=C.UTF-8
find services -name 'RetryError.java'
find services/common/model/domain/src/main/java/com/example/tradingbot/domain/model/aggregate/strategy -maxdepth 1 -name '*.java'
grep -nE 'public .* (is|get)[A-Z][A-Za-z0-9_]*\(\)' services/common/model/domain/src/main/java/com/example/tradingbot/domain/model/aggregate/strategy/*.java
grep -rnE 'public .* (is|get)[A-Z][A-Za-z0-9_]*\(\)' services/trading-core/src/main/java/com/example/tradingcore/domain/command/
```

- **`RetryError`** — `services/trading-core/src/main/java/com/example/tradingcore/domain/command/RetryError.java`,
  то есть **другое дерево целиком**. Это форма группы `U2`, проходящая через
  `RuntimeJsonConverter#retryErrorToJson`;
- **`StrategyMarketDataExpiredSetting`** — `.../aggregate/strategy/` **глубиной
  выше** трёх обходимых подкаталогов. Это форма `U6.7`, проходящая через
  `StrategyJsonConverter#expiredSettingToJson`.

Оба каталога, в которые они попадают, **не пусты по предмету команды**: первый
печатает шесть нульарных `is`-методов, второй — тоже шесть. То есть команда не
просто «могла бы» что-то потерять — она не смотрит туда, где методы её класса
есть.

**Внутреннее свидетельство того же.** Разбор выдачи сам называет класс,
которого команда напечатать не может: «Метод с параметрами свойством не
является, поэтому `StrategyMarketDataExpiredSetting#isUnprotected(…)` в состав
не входит». Этот класс лежит вне шести каталогов — значит разбор опирается на
знание помимо своей команды, а клейм полноты написан над одной командой.

**Что круг проверил сам, и почему это не невалидность.** Вывод документа
верен — круг вывел его независимо:

- `RetryError` — **запись** `record RetryError(String code, String message,
  RuntimeErrorCode type)` без единого дополнительного метода: свойств ровно три,
  ожидание `U2.1` «тождество по всем трём» полно;
- `StrategyMarketDataExpiredSetting` — оба его публичных метода
  (`resolve(…)`, `isUnprotected(…)`) **принимают параметры**, свойствами не
  являются, и ожидание `U6.7` полно;
- `MarketDataExpiredAction`, чьи два предиката печатает первая из команд выше,
  — **перечисление**: на проводе оно едет именем, и предикаты ключами не
  становятся.

Ни одна клетка поэтому не невалидна, и апрув находка не блокирует. Дефект — в
**способе**: клейм полноты стои́т над командой, которая своей популяции не
накрывает, а это ровно тот класс, против которого §«Полнота письменных форм
предмета — обязательное свойство детектора» и записана
(`.claude/rules/measurement-commands.md`).

**Что закрывает.** Либо команда доводится до популяции (добавить
`services/trading-core/src/main/java/com/example/tradingcore/domain/command` и
`.../aggregate/strategy` глубиной 1), либо клейм ужимается до охвата команды, а
две формы получают свой разбор рядом — с тем исходом, который круг уже
предъявил. Исполняет автор той же единицей, без нового круга
(`.claude/skills/test-review.md` §«Что апрув не блокирует»).

### `Ф-4` — `F12` названа по одному javadoc из трёх

**Что объявлено.** `F12`: «javadoc формы параметров структуры рынка ссылается
на `docs/models/domain/aggregate/Strategy.md` адресом `§MarketStructureParams`
— пассажа с таким именем в названном доке нет».

**Чем опровергнуто.** Тот же неразрешимый адрес того же дока несут ещё два
javadoc — под именем `§IndicatorParams`:

```bash
export LC_ALL=C.UTF-8
grep -rln 'Strategy.md (§IndicatorParams' services/common/model/domain/src/main/java
grep -nE '^\*\*|^#{2,3} ' docs/models/domain/aggregate/Strategy.md | grep -i 'indicatorparams\|marketstructureparams'
```

Первая печатает `IndicatorParams.java` и `EfficiencyRatioParams.java`; вторая
не печатает ничего — ни заголовка, ни лид-жирного пассажа с этими именами в
доке нет. Соседние адреса того же семейства при этом **разрешаются**:
`§StrategyIndicatorSetting`, `§StrategyMarketStructureSetting`,
`§StrategyMarketDataExpiredSetting` — лид-жирные пассажи §«Настройки рыночных
данных» и §«StrategyStep». То есть дефект узкий и поимённый, а не общий для
дока.

**Почему это не косметика.** `docs/models/domain/aggregate/Strategy.md` назван
**домом группы `U4`** — той самой, чьи ожидания о параметрах индикатора
документ из него и выводит. Указатель, ведущий в несуществующий пассаж дома
группы, — тот же класс, что `F9` и `F12`, и лежит он ближе к предмету, чем оба.

**Что закрывает.** Расширить радиус секции бэклога
(`.claude/work/backlog.md` §«Javadoc формы параметров структуры рынка ссылается
на несуществующий пассаж») до трёх javadoc и назвать оба имени адреса, либо
назвать `§IndicatorParams` отдельной находкой. Секции заводит автор той же
единицей.

### `Ф-5` — пометка «прогоном не проверено» пережила свои прогоны

`U3.5`, `U11.4`, `U11.5` несут в колонке «чем подтверждается» пометку
**«прогоном не проверено»**. Все три прогнаны: `U11.4` и `U11.5` — первым
кругом (`.claude/work/progress/phase-2-step-12-review-test-jsonb-overlay-roundtrip.md`
§«Ось 4…»), `U3.5` — этим (ось 6 выше). Пометка теперь говорит о документе
неправду в сторону **осторожности**, а не разрешения, поэтому класс её —
форма: заменяется указателем на отчёт круга, которым ожидание подтверждено.

## Вердикт

**АПРУВ.** Невалидных кейсов нет: все три гейтящие находки первого круга
закрыты **по существу**, и закрытие каждой предъявлено кругом независимо —
прогоном своей пробы, чтением исходников автоконфигурации и разбором кода, а не
чтением авторского отчёта. Четыре негейтящие первого круга исполнены.

**Круг второй из трёх.** Три находки круга (`Ф-3`, `Ф-4`, `Ф-5`) апрув не
блокируют: `Ф-3` и `Ф-5` — форма, `Ф-4` — пробел дока. Автор исполняет их
**той же единицей**, без нового круга
(`.claude/skills/test-review.md` §«Что апрув не блокирует», §«Петля с tester»).

**Чем апрув оканчивается для шага.** `jsonb-overlay-roundtrip` — четырнадцатый
и последний предмет уровня 2; с ним **уровень 2 закрыт целиком — четырнадцать
из четырнадцати**, а вместе с уровнями 1 (восемь из восьми) и 3 (пять из пяти)
закрыт и **под-шаг 2 `CODE`**. Открывается под-шаг 3 — код тестов; статус шага
переведён в `CODE·3/3`.

## Тела проб круга

Пробы живут вне репозитория (`/tmp/probe-recheck2/`): каталог верхнего уровня в
дереве уронил бы `tools/retired-check.py` кодом 2 — область свипа объявлена, и
незаявленный каталог она не пропускает. Тела приводятся здесь целиком, чтобы
команды осей 2 и 6 были воспроизводимы
(`.claude/rules/measurement-commands.md` §«Команда, объявленная воспроизводимой,
стои́т блоком кода, а не клеткой таблицы»).

**`R2.java`** — равенство пина бину, обе копии правил, обе формы параметров:

```java
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import java.util.*;

public class R2 {
    abstract static class CoreMixin { @JsonIgnore abstract Long getInstrumentId(); }
    abstract static class MdMixin { @JsonIgnore abstract String getExternalTakerFeeRate(); }

    static ObjectMapper bean() {
        ObjectMapper m = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder()
                .createXmlMapper(false).build();
        m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        m.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
        return m;
    }

    static ObjectMapper pin() {
        ObjectMapper m = new ObjectMapper();
        m.findAndRegisterModules();
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        m.setConfig(m.getSerializationConfig().without(MapperFeature.DEFAULT_VIEW_INCLUSION));
        m.setConfig(m.getDeserializationConfig().without(MapperFeature.DEFAULT_VIEW_INCLUSION));
        return m;
    }

    static int diff(String tag, ObjectMapper a, ObjectMapper b) {
        int n = 0;
        for (MapperFeature f : MapperFeature.values()) {
            if (a.getSerializationConfig().isEnabled(f) != b.getSerializationConfig().isEnabled(f)) {
                System.out.println("    MapperFeature(ser)." + f + ": A=" + a.getSerializationConfig().isEnabled(f) + " B=" + b.getSerializationConfig().isEnabled(f)); n++;
            }
            if (a.getDeserializationConfig().isEnabled(f) != b.getDeserializationConfig().isEnabled(f)) {
                System.out.println("    MapperFeature(deser)." + f + ": A=" + a.getDeserializationConfig().isEnabled(f) + " B=" + b.getDeserializationConfig().isEnabled(f)); n++;
            }
        }
        for (SerializationFeature f : SerializationFeature.values()) {
            if (a.isEnabled(f) != b.isEnabled(f)) { System.out.println("    Ser." + f + ": A=" + a.isEnabled(f) + " B=" + b.isEnabled(f)); n++; }
        }
        for (DeserializationFeature f : DeserializationFeature.values()) {
            if (a.isEnabled(f) != b.isEnabled(f)) { System.out.println("    Deser." + f + ": A=" + a.isEnabled(f) + " B=" + b.isEnabled(f)); n++; }
        }
        System.out.println("=== " + tag + " diffs=" + n);
        System.out.println("    modulesA=" + new TreeSet<>(a.getRegisteredModuleIds().stream().map(Object::toString).toList()));
        System.out.println("    modulesB=" + new TreeSet<>(b.getRegisteredModuleIds().stream().map(Object::toString).toList()));
        return n;
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper beanM = bean(), pinM = pin();
        diff("PIN vs BEAN", pinM, beanM);

        // U7: two copies on the pinned bean-build mapper
        ObjectMapper core = pinM.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .addMixIn(InstrumentExternalRules.class, CoreMixin.class);
        ObjectMapper md = pinM.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .addMixIn(InstrumentExternalRules.class, MdMixin.class);
        InstrumentExternalRules r = new InstrumentExternalRules();
        r.setInstrumentId(7L);
        r.setStatus(InstrumentExternalRules.Status.LIVE);
        r.setExternalTickSize("0.1");
        r.setExternalTakerFeeRate("0.0005");
        System.out.println("U7.1 core = " + core.writeValueAsString(r));
        System.out.println("U7.2 md   = " + md.writeValueAsString(r));
        InstrumentExternalRules back = core.readValue(core.writeValueAsString(r), InstrumentExternalRules.class);
        System.out.println("U7.1 back instrumentId=" + back.getInstrumentId() + " fee=" + back.getExternalTakerFeeRate() + " status=" + back.getStatus());

        InstrumentExternalRules r3 = new InstrumentExternalRules();
        r3.setStatus(InstrumentExternalRules.Status.SUSPEND);
        r3.setExternalTickSize("0.1");
        String s1 = core.writeValueAsString(r3), s2 = md.writeValueAsString(r3);
        System.out.println("U7.3 equal=" + s1.equals(s2) + " -> " + s1);

        // U9.10: stored vs canonical order
        ObjectMapper storage = pinM.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        ObjectMapper canonical = pinM.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        canonical.setConfig(canonical.getSerializationConfig().with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        AtrParams p = new AtrParams();
        p.setTimeframe(com.example.tradingbot.domain.model.trade.candle.TimeFrame.ONE_HOUR);
        p.setWarmup(50);
        p.setPeriod(14);
        System.out.println("U9.10 storage   = " + storage.writeValueAsString(p));
        System.out.println("U9.10 canonical = " + canonical.writeValueAsString(p));
    }
}
```

**`R3.java`** — приведение элемента перечня и класс отказа на чужой форме:

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.util.List;

public class R3 {
    static ObjectMapper pin() {
        ObjectMapper m = new ObjectMapper();
        m.findAndRegisterModules();
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        m.setConfig(m.getSerializationConfig().without(MapperFeature.DEFAULT_VIEW_INCLUSION));
        m.setConfig(m.getDeserializationConfig().without(MapperFeature.DEFAULT_VIEW_INCLUSION));
        return m;
    }
    public static void main(String[] a) throws Exception {
        ObjectMapper m = pin().copy().setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        try {
            List<String> v = m.readValue("[\"abc\",5]", new TypeReference<List<String>>() {});
            System.out.println("U3.5 ok -> " + v + " types=" + v.get(1).getClass().getSimpleName());
        } catch (Exception e) { System.out.println("U3.5 FAIL -> " + e.getClass().getName() + ": " + e.getMessage()); }
        try { System.out.println("U3.6 -> " + m.readValue("{\"a\":1}", new TypeReference<List<String>>() {})); }
        catch (Exception e) { System.out.println("U3.6 -> " + e.getClass().getSimpleName()); }
    }
}
```

## Связи

- Документ кейсов — `.claude/tests/cases/jsonb-overlay-roundtrip.md`.
- Первый круг — `.claude/work/progress/phase-2-step-12-review-test-jsonb-overlay-roundtrip.md`.
- Передача автора — `.claude/work/progress/phase-2-step-12-hand-test-jsonb-overlay-roundtrip.md`.
- Критерий апрува и граница петли — `.claude/skills/test-review.md`.
- Нормы проверочных команд — `.claude/rules/measurement-commands.md`.
- Хроника шага — `.claude/work/progress/phase-2-step-12-chronicle.md`.
