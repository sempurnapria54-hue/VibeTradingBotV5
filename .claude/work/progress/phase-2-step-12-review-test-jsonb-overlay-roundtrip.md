# Ревью кейсов `jsonb-overlay-roundtrip` по критерию валидности

## На какой вопрос отвечает этот файл

Каков исход адверсариального ревью документа кейсов
`.claude/tests/cases/jsonb-overlay-roundtrip.md`.

## Охват

**Предмет** — пять форм JSONB-навеса, живущих семью классами в трёх деревьях
(`trading-core`, `market-data`, `strategies`): навес runtime-строк ядра, навес
дерева стратегии (две копии), навес справочных правил инструмента (две копии),
навес уровней книги заявок, навес и каноническая форма параметров вычисления.
**Четырнадцатый предмет уровня 2** перечня под-шага 1 `CODE` и **последний из
шести**, введённых правкой перечня 2026-09-17
(`.claude/decisions/test-contour-design-pass.md` §«Перечень предметов и порядок
работы»), **первый круг документа**.

Критерий апрува — `.claude/skills/test-review.md` §«Критерий апрува документа
кейсов»; потолок петли — три круга. Передача пришла с таблицей самопроверки
автора
(`.claude/work/progress/phase-2-step-12-hand-test-jsonb-overlay-roundtrip.md`)
— по строке на каждый из 76 кейсов; без неё круг не открывался бы.

**Метка своей дельты отбита до первой правки** (`git write-tree`,
`.claude/rules/edit-kind-obligations.md` §«Предмет обязанностей — дельта
захода»). Дельта круга: этот отчёт, одна секция бэклога, хроника шага и
снапшот. **Документ кейсов круг НЕ правит** — ревьюер не чинит
(`.claude/skills/test-review.md` §«Назначение и границы», §«Петля с tester»);
дерево кода круг **читал** и **прогонял пробами**, но не менял.

**Новое против тринадцати апрувленных документов уровня: документ вводит ось
«выход кейса есть тождество ПАРЫ, а оператора тождества у предмета нет», и
ревью обязано применить её к собственным клеткам документа.** Ось про то,
**чем** пара сравнивается; круг спросил у каждой клетки соседний вопрос —
**на чём** пара производится, то есть каким маппером собран конвертер. Ось
оправдалась: **два гейтящих дефекта из трёх пришли ровно оттуда**, и один из
них делает красным **прямой ход** целой группы, а не негатив.

**Что прочитано целиком** (носители, а не пассажи по имени величины):

| Носитель | Зачем |
|---|---|
| все семь классов предмета в трёх деревьях | дом половины ожиданий: 20 звеньев `Z1`-`Z20` документа |
| доменные формы навеса: `Condition`, `Trigger`, `Trailing`, `TriggerPrice`, `RetryError`, `InstrumentExternalRules`, `OrderBookLevel`, `StrategyCondition`, `IndicatorParams` и восемь подтипов, `MarketStructureParams`, `StrategyMarketDataExpiredSetting`, четыре формы действия | предмет тождества: что именно переживает запись |
| `docs/models/domain/core/AlgoOrder.md` §«Условие срабатывания», §Персистентность | дом групп `U1`, `U3`; здесь нашлась `Ф-1` |
| `docs/models/domain/other/InstrumentExternalRules.md`, `docs/models/domain/other/MarketOrderBook.md`, `docs/models/domain/other/IndicatorValue.md`, `docs/rules/persistence-representation.md` | дома групп `U4`-`U9` |
| `.claude/rules/codestyle.md` §«Неизменяемое значение, пересекающее сериализацию», §«Слои моделей и enum'ы» | дом группы `U2` и формы значения навеса |
| миграции трёх сервисов (`V1__*_baseline.sql`) | обязательность колонок — носитель `U8.2` |
| исходники `jackson-databind` 2.20.1, `spring-web` 6.1.11, автоконфиг `spring-boot-jackson2` 4.0.0 | **семь** клеток с пометкой «прогоном не проверено» — проверены прогоном (ниже) |

## Механические оси критерия — команды и их исход

### Ось 1. Метки уникальны, счёт документа сходится

```bash
export LC_ALL=C.UTF-8
grep -coE '^\| U[0-9]+\.[0-9]+ \|' .claude/tests/cases/jsonb-overlay-roundtrip.md
grep -oE '^\| U[0-9]+\.[0-9]+ \|' .claude/tests/cases/jsonb-overlay-roundtrip.md | sort -u | wc -l
grep -cE '^## U[0-9]+ ' .claude/tests/cases/jsonb-overlay-roundtrip.md
grep -coE '^\| Z[0-9]+ \|' .claude/tests/cases/jsonb-overlay-roundtrip.md
```

Исход: 82 строки, **76 уникальных меток**, **12 групп**, **20 звеньев**. Шесть
повторов — строки таблицы §«Состояния, недостижимые обычной тропой», где та же
метка называется второй раз намеренно. Счётного клейма о себе документ не
несёт вовсе (`.claude/rules/self-description-form.md`) — сверять нечего, и это
верная форма.

### Ось 2. Популяция выведена двумя входами — обе команды воспроизводимы

```bash
export LC_ALL=C.UTF-8
py tools/pure-logic-candidates.py | grep -i 'Converter'
grep -rln 'writeValueAsString\|convertValue' services/trading-core/src/main/java services/market-data/src/main/java services/strategies/src/main/java
```

Первая печатает **восемь** классов — семь предмета плюс `OkxResponseConverter`,
названный вне предмета с доводом. Вторая печатает семь конвертеров и четыре
класса сверх них, и все четыре в документе разобраны поимённо. Вход второй
дал класс, которого реестр не печатает (`AnomalyReportService`), — он назван
пробелом `G5`. **Ось сошлась**: популяция классов полна и разобрана.

**Но единица популяции выбрана классом, а не методом, и на этом уровне
теряется форма — находка `G-новый-1` ниже.**

### Ось 3. Цитаты носителей разрешаются в корпусе

Ось та же, что у круга `platform-shared-logic`: клетка, чей носитель —
**цитата** дока или javadoc, проверяема механически, а выдуманная цитата глазу
невидима — она читается как верная. Скрипт берёт все фрагменты в
кавычках-ёлочках длиной от 12 знаков, снимает разметку markdown и javadoc,
приводит к нижнему регистру и ищет каждый в `docs/**`, `.claude/**`,
`services/**`:

```bash
export LC_ALL=C.UTF-8
py "$TMP/quotes.py"   # тело скрипта — §«Скрипт оси 3» ниже
```

Исход: **61 уникальный фрагмент**, 9 не найдены нигде — и все девять оказались
собственными формулировками документа либо именами его же разделов. Гейтящего
эта половина не дала.

**Вторая половина оси дала `Ф-1`** — «где именно фрагмент найден». Фрагмент,
нашедшийся только в коде и рабочих файлах, но **не** в `docs/**` и не в
`.claude/rules/**`, печатается отдельно; так найдена клетка `U1.9`, называющая
доковый пассаж, в котором цитаты нет.

### Ось 4. Семь клеток «прогоном не проверено» — прогнаны

Документ честно пометил семь утверждений о поведении библиотеки как выведенные
чтением (`U1.7`, `U3.5`, `U4.7`, `U7.7`, `U9.7`, `U11.4`, `U11.5` и
родственные). Круг их **прогнал** — скомпилировав пробу против собранного
дерева доменной библиотеки и тех же версий `jackson-databind`, что в сборке:

```bash
export JAVA_HOME="$HOME/.jdks/corretto-25.0.3"; export PATH="$JAVA_HOME/bin:$PATH"
M="C:/Users/RomanKrd/.m2/repository"; R="$(pwd -W)"; P="$TMP/probe-review-jsonb"
CP="$R/services/common/model/domain/target/classes;$M/com/fasterxml/jackson/core/jackson-databind/2.20.1/jackson-databind-2.20.1.jar;$M/com/fasterxml/jackson/core/jackson-core/2.20.1/jackson-core-2.20.1.jar;$M/com/fasterxml/jackson/core/jackson-annotations/2.20/jackson-annotations-2.20.jar;$M/org/apache/commons/commons-lang3/3.19.0/commons-lang3-3.19.0.jar;$P"
javac -cp "$CP" -d "$P" "$P/Probe.java" "$P/Probe2.java" && java -cp "$CP" Probe && java -cp "$CP" Probe2
```

Проба собирает конвертер **ровно так, как объявляет базовая сборка документа**
— копией свежего маппера с политикой непустых полей и примесью — и печатает
строку навеса, исход обратного хода и контраст с маппером, настроенным как бин
Boot. **Прогон дал два гейтящих дефекта (`N-1`, `N-3`) и подтвердил третий
(`N-2`) на отдельной пробе.** Тела проб — §«Пробы оси 4» ниже.

## Находки

Класс каждой — по `.claude/skills/test-review.md` §«Критерий апрува документа
кейсов»; вердикт выводится по первому классу, а не по числу.

| Метка | Класс | Предмет |
|---|---|---|
| `N-1` | **невалидность** | базовая сборка «свежий маппер» делает красным **прямой ход** группы `U7` и шесть кейсов терпимости |
| `N-2` | **невалидность** | `U5.3` ожидает от конвертера того, что его конструктор запрещает безусловно |
| `N-3` | **невалидность** + находка владельцу | «весь выход» групп `U7`, `U10` не называет ключ, который в строку пишется |
| `Ф-1` | форма | `U1.9` приписывает доковому пассажу формулировку, которой в нём нет |
| `Ф-2` | форма | `U6.6` говорит «перечень пуст» на оси, которую документ сам объявил различающей |
| `G-новый-1` | пробел покрытия | четыре пары публичных методов двух конвертеров не названы ни меткой, ни пробелом |
| `G-новый-2` | пробел покрытия | ось, ради которой существует каноническая форма, не спрошена ни одним кейсом |

### `N-1` — базовая сборка «свежий маппер» противоречит звену `Z16` и роняет прямой ход `U7`

**Что объявлено.** §«Чем достаются выходы»: «все семь классов конструируются
`new` с маппером аргументом»; там же — «Конвертер, берущий маппер **копией**
(шесть из семи), от настроек бина не зависит, и **свежий маппер в кейсе
законен**». Базовые сборки групп `U1`, `U4`, `U9` называют свежий маппер
дословно. Звено `Z16` при этом читает поведение у **бина автоконфигурации
Boot**: «отказ на неизвестном свойстве выключен умолчанием».

**Чем опровергнуто.** Два умолчания расходятся, и `copy()` наследует то из
них, которое стои́т у источника:

```bash
export LC_ALL=C.UTF-8
grep -n 'FAIL_ON_UNKNOWN_PROPERTIES(' "$TMP/jx3/com/fasterxml/jackson/databind/DeserializationFeature.java"
grep -n 'FAIL_ON_UNKNOWN_PROPERTIES' "$TMP/jx2/org/springframework/http/converter/json/Jackson2ObjectMapperBuilder.java"
```

Первая печатает `FAIL_ON_UNKNOWN_PROPERTIES(true)` — умолчание **свежего**
`new ObjectMapper()` строгое. Вторая печатает строку 815 — `Jackson2ObjectMapperBuilder`,
через который бин собирает `Jackson2AutoConfiguration`, выключает эту охрану,
если её не задали явно. Собственной настройки маппера в дереве нет ни у одного
сервиса:

```bash
export LC_ALL=C.UTF-8
grep -rn 'FAIL_ON_UNKNOWN_PROPERTIES\|Jackson2ObjectMapperBuilder' services --include=*.java | grep '/main/'
grep -rn 'jackson' services/*/src/main/resources/application*.y*ml
```

Обе команды печатают пусто. Отсюда: **в проде конвертер терпим к неизвестному
полю, в кейсе на свежем маппере — нет**, и `copy()` эту разницу переносит
целиком: конструктор переопределяет только политику включения.

**Почему это не частность негатива, а прямой ход.** Строка навеса
`InstrumentExternalRules`, произведённая **тем же конвертером**, содержит ключ
без сеттера (см. `N-3`), поэтому обратный ход падает на своей же строке.
Прогон (блок `B` пробы):

```
A. {"status":"LIVE","externalTickSize":"0.1","externalTakerFeeRate":"0.0005","live":true}
B. ОТКАЗ: com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
   Unrecognized field "live" (class …InstrumentExternalRules), not marked as ignorable (19 known properties: …)
C. на маппере с настройками бина Boot — ПРОШЁЛ, tickSize=0.1, feeRate=0.0005
D. кейс U1.7/U11.1 на свежем маппере — ОТКАЗ: UnrecognizedPropertyException
```

**Что невалидно.** `U7.1`, `U7.2`, `U7.3`, `U7.5` — прямой ход группы, красный
по причине, к предмету кейса отношения не имеющей. `U1.7`, `U4.6`, `U4.7`,
`U7.7`, `U9.7`, `U11.1`, `U11.3` — ожидание «неизвестное поле отброшено,
отказа нет» на объявленной сборке красно. Критерий 2 нарушен у всех
одиннадцати: названный носитель (`Z16`) не соответствует сборке, которую
клетка себе объявила.

**Что ещё отсюда следует.** Строка §«Поверхность предмета» «независимость
формы навеса от чужого бина — маппер берётся копией, и правка общего бина
форму не двигает» **шире факта**: копия снимает слепок конфигурации бина
целиком, и переопределена в ней только политика включения. Правка бина
двигает форму у **всех семи**, а не у одного; у шести она не двигает ровно
`NON_NULL`. Контраст находки `F4` от этого не исчезает, но формулируется у́же:
седьмой отличается тем, что не пинит **включение**, а не тем, что «зависит от
бина».

**Что закрывает.** Либо базовая сборка называет маппер, настроенный как бин
(и это становится частью предмета — тогда `Z16` верен), либо сборка остаётся
свежей, а ожидания одиннадцати клеток переписываются на строгое поведение — и
тогда документ обязан сказать, что в проде тропа ведёт себя иначе. Первое
предпочтительнее: предмет кейса — конвертер в проде, а не конвертер в вакууме.

### `N-2` — `U5.3` ожидает того, что конструктор конвертера запрещает

**Что объявлено.** `U5.3`: вход — «тот же конвертер, собранный на маппере
**без** политики непустых полей (контрольный прогон)»; ожидаемый выход —
«строка несёт ключи с пустыми значениями». Носители — звенья `Z5`, `Z11`.

**Чем опровергнуто.** `Z5` сам и говорит, что конструктор ставит `NON_NULL`
копии; ставит он её **безусловно**, поэтому настройка источника на эту ось не
влияет:

```bash
export LC_ALL=C.UTF-8
grep -n -A4 'public StrategyJsonConverter' services/trading-core/src/main/java/com/example/tradingcore/mapping/StrategyJsonConverter.java
```

Прогон (блок `E` второй пробы):

```
stored(bare)   = {"fastPeriod":12}
stored(ALWAYS) = {"fastPeriod":12}
ключи с пустым значением есть: false
```

**Что невалидно.** `U5.3` целиком: ожидаемый выход недостижим на любом входе
этой группы. Контрольный прогон, который кейс хочет предъявить, возможен
только у **седьмого** конвертера — и там он уже написан (`U8.5`), с тем же
доводом. Ссылка `U5.3` на `Z11` это и выдаёт: звено принадлежит чужой группе.

**Что закрывает.** Кейс снимается как дубль `U8.5` — либо переписывается на
то, что у этой группы действительно наблюдаемо: что политика включения
**запинена конструктором** и настройка источника её не сдвигает. Второе
ценнее: это ровно та ось, которой у седьмого конвертера нет.

### `N-3` — состав ключей строки назван неполно

**Что объявлено.** `U7.1`: «в строке **нет** идентификатора владельца;
**ставка комиссии в строке ЕСТЬ**». `U7.2` — зеркально. `U7.3`: «строки
**совпадают дословно**». Колонка таблицы — «Ожидаемый выход — **весь**», и
§«Чем достаются выходы» объявляет «состав ключей строки» одной из пяти точек
наблюдения.

**Чем опровергнуто.** У формы есть предикат `public Boolean isLive()`, и он —
свойство Jackson, а не метод модели:

```bash
export LC_ALL=C.UTF-8
grep -n 'public Boolean isLive' services/common/model/domain/src/main/java/com/example/tradingbot/domain/model/core/instrument/InstrumentExternalRules.java
grep -n -A14 'private boolean _booleanType' "$TMP/jx3/com/fasterxml/jackson/databind/introspect/DefaultAccessorNamingStrategy.java"
```

Вторая команда печатает правило распознавания `is`-геттера: `Boolean.TYPE`
**и** `Boolean.class`. Прогон (блок `A`): строка навеса **обеих** копий несёт
двадцатый ключ `"live":true`, которого не объявляет ни одна клетка, и который
нельзя изъять примесью — у него нет поля.

**Что невалидно.** `U7.1`, `U7.2`, `U7.3` — их «весь выход» неполон на
объявленной точке наблюдения (критерий 1). `U10.3`, сверяющий изъятия пары,
опирается на те же клетки.

**Находка владельцу — своя, и она не совпадает ни с `F3`, ни с `F4`.**
Доменный **предикат** уезжает в персистентную колонку: состав колонки задаёт
не форма, а набор её `is`-методов; снятие или переименование предиката молча
меняет содержимое уже работающей колонки, а добавление нового предиката молча
её расширяет. `F3` адресует переименование **поля** (Lombok-аксессор), здесь —
метод, поля не имеющий вовсе; `F4` адресует политику включения. Секция
заведена: `.claude/work/backlog.md` §«Доменный предикат уезжает в колонку
JSONB-навеса вместе с полями».

### `Ф-1` — цитата `U1.9` в названном доковом пассаже не живёт

`U1.9` в клетке «чем подтверждается» называет
`docs/models/domain/core/AlgoOrder.md` §«Условие срабатывания» и цитирует
«ровно один механизм — trigger XOR trailing»; та же строка стои́т в таблице
самопроверки автора с исходом «сходится: да».

```bash
export LC_ALL=C.UTF-8
grep -c 'trigger XOR trailing\|ровно один механизм' docs/models/domain/core/AlgoOrder.md
grep -rn 'ровно один механизм' services/common/model/domain/src/main/java/com/example/tradingbot/domain/model/core/algo_order/Condition.java
```

Первая печатает `0`, вторая — javadoc `Condition`. Пассаж дока говорит «тип
плюс параметры триггера **либо** трейлинга» и о том, **кто** инвариант держит,
молчит. Ожидание кейса («конвертер не охраняет») опирается на звено `Z2` и от
этого не рушится — поэтому класс `форма`, а не `невалидность`. Правится
приведением цитаты к тому, что в доке написано, либо снятием кавычек и
пометкой, что дом инварианта — javadoc формы (тогда это `F`-находка того же
рода, что `F1`).

### `Ф-2` — `U6.6` говорит «пусто» на оси, которую документ объявил различающей

`U6.6`: вход — «строка условия — пустой объект», выход — «разбор проходит,
**перечень правил пуст**». Документ при этом трижды строит кейсы ровно на
различии пустоты и пустого перечня (`U3.2`, `U3.3`, `U6.2`) и объявляет его
одной из пяти точек наблюдения. Прогон (блок `G`): `rules == null` — то есть
**пустота**, а не пустой перечень. Формулировка накрывает оба исхода и на
своей же оси не различает; правится словом.

### `G-новый-1` — форма `MarketStructureParams` не названа ни меткой, ни пробелом

Документ объявляет единицей популяции **класс** («Единица популяции — класс
предмета»), и на этом уровне разбор полон. Единицей поверхности при этом
является **метод** (`.claude/skills/test-review.md` §«Полнота против границы
предмета»), и четыре пары публичных методов не покрыты и не названы:

```bash
export LC_ALL=C.UTF-8
grep -rn 'MarketStructureParams' services/trading-core/src/main/java/com/example/tradingcore/mapping/StrategyJsonConverter.java services/strategies/src/main/java/com/example/strategies/mapping/StrategyJsonConverter.java services/market-data/src/main/java/com/example/marketdata/mapping/ComputationParamsJsonConverter.java | grep 'public'
grep -c 'структур' .claude/tests/cases/jsonb-overlay-roundtrip.md
```

Первая печатает шесть публичных сигнатур в трёх классах
(`marketStructureParamsToJson` / `jsonToMarketStructureParams` у обеих копий
конвертера стратегии, `jsonToMarketStructureParams` / `toMarketStructureParams`
у конвертера параметров вычисления); вторая печатает `1` — единственное
вхождение слова, и оно о другом («тест закрепляет структуру»). Форма при этом
не подтип `IndicatorParams`, а самостоятельный класс, то есть у неё своя пара
методов и свой разбор.

Исход «не покрыта» законен, но требует **имени и довода**
(`.claude/skills/test-design.md` §«Исход „не покрыта“ законен…»). Апрув не
гейтит: непокрытая единица — пробел, а не невалидный кейс. Закрывается меткой
`G<n>` в документе либо кейсами.

### `G-новый-2` — ось, ради которой существует каноническая форма, не спрошена

`U9.1` подаёт «два объекта одних и тех же параметров, собранные **разным
порядком присваиваний**» и ожидает дословного совпадения канонических форм.
Порядок присваиваний на сериализацию POJO не влияет **ни на одном** маппере —
прогон (блок `F`):

```
stored(a) = {"fastPeriod":12,"slowPeriod":26,"signalPeriod":9}
stored(b) = {"fastPeriod":12,"slowPeriod":26,"signalPeriod":9}
stored совпали (без канонической формы): true
canonical(a) = {"fastPeriod":12,"signalPeriod":9,"slowPeriod":26}
```

Кейс зелен и на **хранимой** форме, то есть канонической не мерит. Настоящий
операнд канонического маппера виден в третьей строке — алфавитный порядок
свойств против порядка объявления, — и его не называет ни один кейс.

**Второй половины у канонического маппера операнда нет вовсе.**
`ORDER_MAP_ENTRIES_BY_KEYS` упорядочивает ключи **карт**, а карты среди форм
нет, и оба вызова канонической тропы принимают доменный объект:

```bash
export LC_ALL=C.UTF-8
grep -rn 'Map<' services/common/model/domain/src/main/java/com/example/tradingbot/domain/model/aggregate/strategy/setting/
grep -rn 'paramsToCanonical' services/market-data/src/main/java --include=*.java | grep -v ComputationParamsJsonConverter
```

Первая печатает пусто, вторая — два вызова с `config.getParams()`. **Это в
точности класс находки `F6`, которую автор нашёл у соседней настройки того же
семейства** (отключение таймстампов длительности без операнда), и здесь он
повторён на канонической форме. Апрув не гейтит; закрывается кейсом на
алфавитный порядок плюс `F`-находкой на `ORDER_MAP_ENTRIES_BY_KEYS`.

## Что круг проверил и НЕ нашёл дефекта

Названо, чтобы следующий круг не переоткрывал: проверено по носителям и коду,
расхождения нет.

| Предмет | Чем проверено |
|---|---|
| `Z1`-`Z3`, `Z5`-`Z15`, `Z17`-`Z20` — восемнадцать звеньев из двадцати | чтением семи классов целиком; формулировки сходятся дословно |
| `U4.3` «восемь подтипов, ветви умолчания нет» | `switch` без `default` в обеих копиях; `IndicatorValue.Type` несёт ровно восемь значений |
| `U4.4` «наружу выходит `IllegalArgumentException`, не `IllegalStateException`» | `IndicatorValue.Type.valueOf` стои́т **вне** `try` |
| `U9.6`, `U9.8` — свой класс отказа и охраны у публичных входов | `#convert`, `#readJson` без охраны, обе публичные тропы с охраной |
| `U8.2` «колонка объявлена обязательной» | `bids jsonb not null`, `asks jsonb not null` в миграции `market-data` |
| `U10.4` «различие ровно одно и намеренное» | `diff` двух копий: три строки — пакет, импорт, одно слово javadoc |
| `U7.8` «ветвь ненормализованного статуса разбором не выбирается» | `Status.UNKNOWN` — исход резолва, у разбора имени ветви умолчания нет |
| `U11.4`, `U11.5` — литерал пустоты против пустой строки | прогон: `null` даёт пустоту, `""` даёт `MismatchedInputException` под `JsonProcessingException` |
| формы навеса — ни одна не `@Value` | все восемь проверенных несут `@NoArgsConstructor`; `RetryError` — запись |
| `StrategyCondition` предикатов в строку не уносит | пять публичных методов, ни один не `get`/`is`-формы — контраст с `N-3` |
| вторая команда популяции, четыре класса сверх семи | все четыре разобраны поимённо и отнесены верно |

## Вердикт

**БРАКОВКА.** Гейтящих находок три — `N-1`, `N-2`, `N-3`; каждая нарушает
критерий валидности кейса, и `N-1` делает красным прямой ход целой группы.
Четыре негейтящие (`Ф-1`, `Ф-2`, `G-новый-1`, `G-новый-2`) исполняются автором
тем же заходом, что и правку, без нового круга
(`.claude/skills/test-review.md` §«Петля с tester»).

**Круг первый из трёх.** Следующая единица — правка автором
(`.claude/skills/test-design.md`), затем перепроверка — круг второй.

**Что правка обязана сделать сверх клеток.** `N-1` и `N-3` — не про
формулировку, а про **сборку**: пока базовая сборка не назовёт маппер,
настроенный как бин, ни одна клетка тождества `U7` не зелена. Решение сборки
принимается **один раз** и распространяется на все двенадцать групп; клетки
терпимости к неизвестному полю переписываются после него, а не до.

## Скрипт оси 3

```python
import re, io, glob
DOC = '.claude/tests/cases/jsonb-overlay-roundtrip.md'
doc = io.open(DOC, encoding='utf-8').read()
files = {}
for pat in ('docs/**/*.md', 'docs/**/*.json', '.claude/**/*.md', 'services/**/*.java', 'services/**/*.sql'):
    for p in glob.glob(pat, recursive=True):
        q = p.replace(chr(92), '/')
        if q == DOC:
            continue
        try:
            files[q] = io.open(p, encoding='utf-8').read()
        except Exception:
            pass
def norm(s):
    s = re.sub(r'(?m)^\s*\*\s?', ' ', s)
    s = re.sub(r'<[^>]{1,12}>', '', s)
    s = s.replace('{@code ', '')
    s = re.sub(r'[*`\u201e\u201c}"]', '', s)
    return re.sub(r'\s+', ' ', s).strip().lower()
nfiles = {k: norm(v) for k, v in files.items()}
seen, miss, hits = set(), [], {}
for q in re.findall(r'\u00ab([^\u00ab\u00bb]{12,})\u00bb', doc):
    for part in q.split('\u2026'):
        p = norm(part.strip(' .,;:'))
        if len(p) < 12 or p in seen:
            continue
        seen.add(p)
        where = [k for k, v in nfiles.items() if p in v]
        (hits.setdefault(p, where) if where else miss.append(p))
print('fragments:', len(seen), '| not found:', len(miss))
for m in miss:
    print('  MISS:', m[:160])
for p, w in sorted(hits.items()):
    if not any(k.startswith('docs/') or k.startswith('.claude/rules/') for k in w):
        print('  ONLY-OUTSIDE-DOCS:', p[:110], '->', w[:3])
```

## Пробы оси 4

`Probe.java` — состав строки навеса и обратный ход у копии ядра:

```java
ObjectMapper fresh = new ObjectMapper();
ObjectMapper core = fresh.copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .addMixIn(InstrumentExternalRules.class, CoreMixin.class);   // @JsonIgnore getInstrumentId
InstrumentExternalRules rules = new InstrumentExternalRules();
rules.setInstrumentId(42L);
rules.setStatus(InstrumentExternalRules.Status.LIVE);
rules.setExternalTickSize("0.1");
rules.setExternalTakerFeeRate("0.0005");
String json = core.writeValueAsString(rules);                        // блок A
core.readValue(json, InstrumentExternalRules.class);                 // блок B — отказ
new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .addMixIn(InstrumentExternalRules.class, CoreMixin.class)
        .readValue(json, InstrumentExternalRules.class);              // блок C — проходит
fresh.readValue("{\"externalTickSize\":\"0.1\",\"snyatoePole\":1}",
        InstrumentExternalRules.class);                               // блок D — отказ
```

`Probe2.java` — политика включения, каноническая форма и пустой объект условия:

```java
ObjectMapper stored = source.copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
ObjectMapper canonical = source.copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
canonical.setConfig(canonical.getSerializationConfig()
        .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
// E: stored(new ObjectMapper()) против stored(маппер с Include.ALWAYS) на MacdParams
// F: MacdParams, собранный двумя порядками присваиваний — stored против canonical
// G: stored.readValue("{}", StrategyCondition.class).getRules()
```

Пробы живут вне репозитория (`$TMP/probe-review-jsonb/`) и в дерево кода не
попадают: ревьюер дерева не меняет.

## Связи

- Документ — `.claude/tests/cases/jsonb-overlay-roundtrip.md`; передача —
  `.claude/work/progress/phase-2-step-12-hand-test-jsonb-overlay-roundtrip.md`.
- Критерий апрува и граница петли — `.claude/skills/test-review.md`.
- Форма кейса и самопроверка автора — `.claude/skills/test-design.md`.
- Находка владельцу — `.claude/work/backlog.md` §«Доменный предикат уезжает в
  колонку JSONB-навеса вместе с полями».
- Хроника шага — `.claude/work/progress/phase-2-step-12-chronicle.md`.
