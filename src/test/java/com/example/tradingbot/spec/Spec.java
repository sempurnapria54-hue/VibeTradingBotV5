package com.example.tradingbot.spec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Исполнимая спецификация: именованные величины и предикаты плюс примеры,
 * на которых они прогоняются.
 *
 * <p>Формат — JSON, дом каждой формулы один. Величина либо скалярная
 * ({@code expr}), либо агрегат по коллекции ({@code op/over/where/of}).
 * Пример задаёт состояние и ожидаемые значения названных величин.
 */
public final class Spec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Разбор со <b>строгим</b> отношением к повторному ключу.
     *
     * <p><b>Зачем.</b> Обычный разбор молча оставляет ПОСЛЕДНЮЮ пару, и
     * действующей декларацией становится та редакция, которая случайно
     * оказалась ниже, — а предыдущая исчезает без следа. Ни один детектор
     * корпуса этого не видит: он читает уже разобранное дерево, где дубля
     * нет. Замер по всему телу спек нашёл ровно один такой ключ, и выживала
     * неверная половина: контракт операнда противоречил и величине, которая
     * его резолвит, и собственному примеру спеки.
     */
    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    /** Спецификация проверяет алгебру, а не десятичный шум округления. */
    private static final BigDecimal TOLERANCE = new BigDecimal("1e-9");

    /**
     * Префикс расхождения, вызванного отказом корпуса, а не содержанием
     * примера: нечитаемый артефакт {@code stateFrom}, неразобранный файл.
     *
     * <p>Различение обязательно. Потребитель, считающий падением <b>любое</b>
     * расхождение, на сломанном корпусе получает «упало везде» и читает это
     * как «проверка сработала везде»: сломанный корпус выглядит лучше
     * здорового. Системный отказ означает «мерить нечем», а не «мутация
     * замечена».
     */
    public static final String SYSTEM_FAILURE = "СИСТЕМНЫЙ ОТКАЗ: ";

    private final String subject;

    private final Map<String, Map<String, Object>> values = new LinkedHashMap<>();

    private final List<Map<String, Object>> examples = new ArrayList<>();

    /**
     * Объявленные популяции: перечни состояний, на которых правило спеки
     * обязано выполняться.
     *
     * <p><b>Зачем.</b> Прогон примеров показывает, что объявленные величины
     * сходятся на тех состояниях, которые кто-то догадался подать. Он не
     * показывает, что поданы <b>все</b> состояния, где правило обязано
     * выполняться: правка, верная на состоянии-источнике находки, проходит
     * зелёной, а достижимое состояние без примера остаётся неизмеренным и
     * возвращается следующим прогоном.
     *
     * <p><b>Происхождение перечня и критерий покрытия</b> (редакция
     * 2026-08-31, решение держателя): перечень, согласованный с наличным
     * текстом, самореферентен — он молчит обо всём, чего в тексте нет.
     * Поэтому у популяции обязательны три ключа сверх {@code keys}:
     * <ul>
     *   <li>{@code rule} — величины <b>правила</b> популяции. Пример
     *       засчитывается покрытием члена, только если ожидает хотя бы одну
     *       из них: присутствие кортежа покрытием не является;
     *   <li>{@code derive} — происхождение перечня: {@code command} (команда
     *       вывода членов из предмета, сверяется
     *       {@code tools/population-derive-check.py}) либо {@code incomplete}
     *       (названная причина, по которой ось выводима только грунтом);
     *   <li>{@code excludes} — обязателен при {@code where}: имя класса,
     *       который фильтр выводит из популяции. Фильтр, ссылающийся на
     *       величину правила или на производную от неё, запрещён: он выводит
     *       из перечня ровно те состояния, на которых правило проверяется.
     * </ul>
     *
     * <p>Дом нормы — {@code .claude/processes/roadmap-step-execution.md}
     * §«Популяция правила предъявляется до правки, а не после»; процедура —
     * {@code .claude/skills/closure-population.md}.
     */
    private final List<Map<String, Object>> populations = new ArrayList<>();

    private Spec(Map<String, Object> raw, Path directory) throws IOException {
        this.subject = String.valueOf(raw.get("subject"));
        for (Object included : list(raw.get("includes"))) {
            Path file = directory.resolve(String.valueOf(included) + ".json");
            addValues(list(asMap(MAPPER.readValue(file.toFile(), Map.class)).get("values")),
                    String.valueOf(included));
        }
        addValues(list(raw.get("values")), subject);
        for (Object example : list(raw.get("examples"))) {
            examples.add(asMap(example));
        }
        for (Object population : list(raw.get("populations"))) {
            populations.add(asMap(population));
        }
    }

    /**
     * Подключает величины: свои и заимствованные по {@code includes}.
     * Одноимённая величина из двух источников — отказ: у формулы один дом.
     */
    private void addValues(List<Object> definitions, String origin) {
        for (Object value : definitions) {
            Map<String, Object> definition = asMap(value);
            String name = String.valueOf(definition.get("name"));
            if (values.containsKey(name)) {
                throw new SpecException("Величина объявлена дважды: " + name + " (в " + origin + ")");
            }
            values.put(name, definition);
        }
    }

    /** Загружает спецификацию из файла. */
    public static Spec load(Path file) throws IOException {
        Path directory = file.getParent() == null ? Path.of(".") : file.getParent();
        return new Spec(MAPPER.readValue(file.toFile(), Map.class), directory);
    }

    /** Все спецификации каталога, в порядке имён файлов. */
    public static List<Path> specFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    public String subject() {
        return subject;
    }

    public List<Map<String, Object>> examples() {
        return examples;
    }

    /** Прогон всех примеров; возвращает список расхождений (пустой — сошлось). */
    public List<String> run() {
        List<String> failures = new ArrayList<>();
        for (Map<String, Object> example : examples) {
            String label = String.valueOf(example.get("case"));
            Map<String, Object> state;
            try {
                state = stateOf(example);
            } catch (RuntimeException failure) {
                failures.add(SYSTEM_FAILURE + "%s / %s — состояние не собрано: %s"
                        .formatted(subject, label, failure.getMessage()));
                continue;
            }
            for (Map.Entry<String, Object> expected : asMap(example.get("expect")).entrySet()) {
                String name = expected.getKey();
                try {
                    Object actual = evaluate(name, state, Map.of(), new ArrayDeque<>());
                    if (!same(expected.getValue(), actual)) {
                        failures.add("%s / %s: %s ожидалось %s, получено %s"
                                .formatted(subject, label, name, expected.getValue(), actual));
                    }
                } catch (RuntimeException failure) {
                    // Любой отказ ВЫЧИСЛЕНИЯ — расхождение примера, а не отказ
                    // корпуса: пример его заметил. Отказ корпуса (несобранное
                    // состояние, неразобранный файл) помечен отдельно.
                    failures.add("%s / %s: %s — отказ вычисления: %s"
                            .formatted(subject, label, name, failure.getMessage()));
                }
            }
            Object refusal = example.get("expectRefusal");
            if (refusal != null) {
                failures.addAll(checkRefusal(label, state, asMap(refusal)));
            }
        }
        failures.addAll(populationFailures());
        return failures;
    }

    /**
     * Проверка объявленных популяций: каждый член предъявлен примером.
     *
     * <p>Три вида расхождения, и все три обязательны:
     * <ul>
     *   <li>объявленный достижимым член, которого не предъявил ни один
     *       обычный пример, — состояние, на котором правило не проверялось;
     *   <li>объявленный недостижимым член без контрпримера — недостижимость
     *       осталась прозой, а проза перечнем не проверяется;
     *   <li>пример, предъявляющий члена, которого перечень не объявляет, —
     *       перечень неполон, и его клейм полноты ложен.
     * </ul>
     *
     * <p>Пример, у которого хоть одно ключевое выражение не разрешается,
     * в популяции не участвует (у него нет её осей). Необязательный ключ
     * {@code where} сужает участие явным условием — им отсекаются примеры,
     * предъявляющие спеке состояние <b>вне</b> популяции намеренно (проба
     * недопустимого перехода при популяции допустимых). Популяция, в которой
     * не участвовал ни один пример, — расхождение: перечень объявлен и не
     * измерен ничем.
     */
    private List<String> populationFailures() {
        List<String> failures = new ArrayList<>();
        for (Map<String, Object> population : populations) {
            String axis = String.valueOf(population.get("axis"));
            List<Object> keys = list(population.get("keys"));
            Object filter = population.get("where");
            List<String> rule = new ArrayList<>();
            for (Object name : list(population.get("rule"))) {
                rule.add(String.valueOf(name));
            }
            failures.addAll(contractFailures(axis, rule, population));
            if (rule.isEmpty()) {
                continue;
            }
            Map<String, Integer> byOrdinary = new LinkedHashMap<>();
            Map<String, Integer> byCounter = new LinkedHashMap<>();
            Map<String, String> firstCase = new LinkedHashMap<>();
            Map<String, String> seenWithoutRule = new LinkedHashMap<>();
            int participants = 0;
            for (Map<String, Object> example : examples) {
                Map<String, Object> state;
                try {
                    state = stateOf(example);
                } catch (RuntimeException failure) {
                    continue;
                }
                if (filter != null) {
                    try {
                        if (!SpecExpression.truth(SpecExpression.parse(String.valueOf(filter))
                                .eval(id -> resolve(id, state, Map.of(), new ArrayDeque<>())))) {
                            continue;
                        }
                    } catch (RuntimeException failure) {
                        continue;
                    }
                }
                List<String> tuple = new ArrayList<>();
                boolean participates = true;
                for (Object key : keys) {
                    try {
                        Object actual = SpecExpression.parse(String.valueOf(key))
                                .eval(id -> resolve(id, state, Map.of(), new ArrayDeque<>()));
                        tuple.add(String.valueOf(actual));
                    } catch (RuntimeException failure) {
                        participates = false;
                        break;
                    }
                }
                if (!participates) {
                    continue;
                }
                participants++;
                String member = String.join(" \u2192 ", tuple);
                firstCase.putIfAbsent(member, String.valueOf(example.get("case")));
                if (!checksRule(example, rule)) {
                    // Пример предъявляет кортеж, но правила на нём не проверяет:
                    // членом он его не делает. Полноту перечня — делает.
                    seenWithoutRule.putIfAbsent(member, String.valueOf(example.get("case")));
                    continue;
                }
                boolean counter = example.containsKey("unreachable");
                (counter ? byCounter : byOrdinary).merge(member, 1, Integer::sum);
            }
            if (participants == 0) {
                failures.add("%s / популяция «%s»: не измерена ни одним примером — "
                        .formatted(subject, axis)
                        + "перечень объявлен, и ни один пример не предъявил его осей");
                continue;
            }
            List<String> declared = new ArrayList<>();
            for (Object entry : list(population.get("members"))) {
                Map<String, Object> single = asMap(entry);
                List<Object> parts = list(single.get("member"));
                List<String> asText = new ArrayList<>();
                for (Object part : parts) {
                    asText.add(String.valueOf(part));
                }
                String member = String.join(" \u2192 ", asText);
                declared.add(member);
                boolean unreachable = single.containsKey("unreachable");
                if (unreachable) {
                    if (byCounter.getOrDefault(member, 0) == 0) {
                        failures.add(("%s / популяция «%s»: член «%s» объявлен недостижимым, "
                                + "а контрпримера, предъявляющего это состояние и проверяющего на нём "
                                + "правило (%s), нет — недостижимость осталась прозой")
                                .formatted(subject, axis, member, String.join(", ", rule))
                                + hint(seenWithoutRule.get(member)));
                    }
                } else if (byOrdinary.getOrDefault(member, 0) == 0) {
                    failures.add(("%s / популяция «%s»: член «%s» не покрыт — ни один пример не "
                            + "проверяет на нём правило (%s); присутствие кортежа покрытием не является")
                            .formatted(subject, axis, member, String.join(", ", rule))
                            + hint(seenWithoutRule.get(member)));
                }
            }
            for (String observed : firstCase.keySet()) {
                if (!declared.contains(observed)) {
                    failures.add(("%s / популяция «%s»: пример «%s» предъявляет члена «%s», "
                            + "которого перечень не объявляет — клейм полноты перечня ложен")
                            .formatted(subject, axis, firstCase.get(observed), observed));
                }
            }
        }
        return failures;
    }

    /** Подсказка о примере, который кортеж предъявил, а правило на нём не проверял. */
    private static String hint(String caseName) {
        return caseName == null ? ""
                : " (кортеж предъявлен примером «" + caseName + "», но правила он не ожидает)";
    }

    /**
     * Ожидает ли пример хотя бы одну величину правила — прямым ожиданием
     * ({@code expect}) либо ожиданием отказа ({@code expectRefusal}).
     *
     * <p>Критерий покрытия: член покрыт <b>проверкой правила на нём</b>, а не
     * присутствием кортежа. Прежняя редакция считала покрытие присутствием, и
     * снятие всех ожиданий проверяемой величины оставляло прогон зелёным.
     */
    private static boolean checksRule(Map<String, Object> example, List<String> rule) {
        for (String name : rule) {
            if (asMap(example.get("expect")).containsKey(name)
                    || asMap(example.get("expectRefusal")).containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Контракт популяции: величины правила, происхождение перечня и
     * законность фильтра участия.
     *
     * <p>Три проверки — против трёх способов сделать полноту самореферентной:
     * перечень без названного правила меряет присутствие; перечень без
     * названного происхождения собран из того же текста, который проверяет;
     * фильтр, ссылающийся на величину правила, выводит из популяции ровно
     * фальсифицирующее состояние.
     */
    private List<String> contractFailures(String axis, List<String> rule,
                                          Map<String, Object> population) {
        List<String> failures = new ArrayList<>();
        String prefix = "%s / популяция «%s»: ".formatted(subject, axis);
        if (rule.isEmpty()) {
            failures.add(prefix + "не названа величина правила (ключ rule) — покрытие считалось бы "
                    + "присутствием кортежа, а не проверкой правила на нём");
        }
        for (String name : rule) {
            if (!values.containsKey(name)) {
                failures.add(prefix + "величина правила «" + name + "» не объявлена в спеке");
            }
        }
        Map<String, Object> derive = asMap(population.get("derive"));
        Object command = derive.get("command");
        Object incomplete = derive.get("incomplete");
        if (derive.isEmpty()) {
            failures.add(prefix + "не названо происхождение перечня (ключ derive) — перечень, "
                    + "выведенный из того же текста, который он проверяет, самореферентен");
        } else if (command == null && incomplete == null) {
            failures.add(prefix + "ключ derive не несёт ни command (команду вывода членов из "
                    + "предмета), ни incomplete (названную причину, по которой ось выводима "
                    + "только грунтом)");
        } else if (command != null && incomplete != null) {
            failures.add(prefix + "ключ derive несёт и command, и incomplete — происхождение "
                    + "перечня объявлено двумя несовместимыми способами");
        } else if (command != null && list(derive.get("from")).isEmpty()) {
            failures.add(prefix + "команда вывода не называет артефакт-предмет (ключ derive.from) — "
                    + "команда, ни на что не ссылающаяся, может печатать перечень из себя самой");
        } else if (incomplete != null && String.valueOf(incomplete).trim().isEmpty()) {
            failures.add(prefix + "неполнота перечня объявлена пустой причиной");
        }
        Object filter = population.get("where");
        if (filter == null) {
            return failures;
        }
        Object excludes = population.get("excludes");
        if (excludes == null || String.valueOf(excludes).trim().isEmpty()) {
            failures.add(prefix + "фильтр участия (where) не называет класс, который он выводит "
                    + "из популяции (ключ excludes)");
        }
        for (String referenced : identifiers(String.valueOf(filter))) {
            if (values.containsKey(referenced)) {
                failures.add(prefix + "фильтр участия ссылается на объявленную величину «"
                        + referenced + "» — фильтр выразим только операндами состояния: "
                        + "вычисленный вердикт в условии участия выводит из популяции ровно "
                        + "те состояния, на которых правило и проверяется");
            }
        }
        return failures;
    }

    /**
     * Имена, на которые ссылается выражение: корни идентификаторов без путей.
     * Строковые литералы вырезаются — {@code 'ACTIVE'} ссылкой на величину не
     * является, и совпадение литерала с именем не должно читаться как ссылка.
     */
    private static List<String> identifiers(String expression) {
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher matcher = IDENTIFIER.matcher(LITERAL.matcher(expression).replaceAll(" "));
        while (matcher.find()) {
            String name = matcher.group();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static final java.util.regex.Pattern IDENTIFIER =
            java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final java.util.regex.Pattern LITERAL =
            java.util.regex.Pattern.compile("'[^']*'");

    /** Число объявленных членов популяций каталога и из них недостижимых. */
    public int[] populationSize() {
        int members = 0;
        int unreachable = 0;
        for (Map<String, Object> population : populations) {
            for (Object entry : list(population.get("members"))) {
                members++;
                if (asMap(entry).containsKey("unreachable")) {
                    unreachable++;
                }
            }
        }
        return new int[] {populations.size(), members, unreachable};
    }

    /**
     * Состояние примера: объявленное в {@code state} плюс артефакты репозитория,
     * подгружаемые по {@code stateFrom} — чтобы спецификация проверялась против
     * реального входа, а не только против синтетики.
     */
    private static Map<String, Object> stateOf(Map<String, Object> example) {
        Map<String, Object> state = new LinkedHashMap<>(asMap(example.get("state")));
        for (Map.Entry<String, Object> entry : asMap(example.get("stateFrom")).entrySet()) {
            Path file = Path.of(String.valueOf(entry.getValue()));
            try {
                state.put(entry.getKey(), MAPPER.readValue(file.toFile(), Map.class));
            } catch (IOException failure) {
                throw new SpecException("Артефакт не прочитан: " + file + " — " + failure.getMessage());
            }
        }
        return state;
    }

    private List<String> checkRefusal(String label, Map<String, Object> state, Map<String, Object> refusal) {
        List<String> failures = new ArrayList<>();
        for (String name : refusal.keySet()) {
            try {
                Object actual = evaluate(name, state, Map.of(), new ArrayDeque<>());
                failures.add("%s / %s: %s должно было отказать, а вернуло %s"
                        .formatted(subject, label, name, actual));
            } catch (SpecException expected) {
                // отказ и требовался
            }
        }
        return failures;
    }

    /** Вычисляет названную величину в состоянии {@code state} и строке {@code row}. */
    public Object evaluate(String name, Map<String, Object> state, Map<String, Object> row, Deque<String> stack) {
        if (stack.contains(name)) {
            throw new SpecException("Циклическая ссылка величин: " + String.join(" → ", stack) + " → " + name);
        }
        Map<String, Object> definition = values.get(name);
        if (definition == null) {
            throw new SpecException("Величина не объявлена: " + name);
        }
        stack.push(name);
        try {
            if (definition.containsKey("over")) {
                return aggregate(definition, state, row, stack);
            }
            return SpecExpression.parse(String.valueOf(definition.get("expr")))
                    .eval(identifier -> resolve(identifier, state, row, stack));
        } finally {
            stack.pop();
        }
    }

    private Object aggregate(Map<String, Object> definition, Map<String, Object> state,
                            Map<String, Object> outerRow, Deque<String> stack) {
        String over = String.valueOf(definition.get("over"));
        List<Object> collection = SpecExpression.collect(outerRow, over);
        if (collection.isEmpty()) {
            collection = SpecExpression.collect(state, over);
        }
        String operation = String.valueOf(definition.get("op"));
        SpecExpression.Node where = definition.get("where") == null
                ? null
                : SpecExpression.parse(String.valueOf(definition.get("where")));
        SpecExpression.Node of = definition.get("of") == null
                ? null
                : SpecExpression.parse(String.valueOf(definition.get("of")));

        boolean numeric = "sum".equals(operation) || "min".equals(operation) || "max".equals(operation);
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal extremum = null;
        long count = 0;
        boolean any = false;
        boolean all = true;
        for (Object element : collection) {
            Map<String, Object> row = asMap(element);
            if (where != null && !SpecExpression.truth(where.eval(id -> resolve(id, state, row, stack)))) {
                continue;
            }
            count++;
            Object value = of == null ? Boolean.TRUE : of.eval(id -> resolve(id, state, row, stack));
            if (numeric) {
                BigDecimal item = SpecExpression.number(value);
                sum = sum.add(item);
                extremum = extremum == null ? item : pick(operation, extremum, item);
            } else if ("any".equals(operation)) {
                any = any || SpecExpression.truth(value);
            } else if ("all".equals(operation)) {
                all = all && SpecExpression.truth(value);
            }
        }
        return switch (operation) {
            case "sum" -> sum;
            case "count" -> BigDecimal.valueOf(count);
            case "min", "max" -> extremum;
            case "any" -> any;
            case "all" -> all;
            case "exists" -> count > 0;
            default -> throw new SpecException("Неизвестная операция агрегата: " + operation);
        };
    }

    private static BigDecimal pick(String operation, BigDecimal left, BigDecimal right) {
        return "min".equals(operation) ? left.min(right) : left.max(right);
    }

    private Object resolve(String identifier, Map<String, Object> state, Map<String, Object> row, Deque<String> stack) {
        Object fromRow = SpecExpression.path(row, identifier);
        if (fromRow != SpecScope.ABSENT) {
            return fromRow;
        }
        if (values.containsKey(identifier)) {
            return evaluate(identifier, state, row, stack);
        }
        Object fromState = SpecExpression.path(state, identifier);
        if (fromState != SpecScope.ABSENT) {
            return fromState;
        }
        throw new SpecException("Идентификатор не найден ни в строке, ни в величинах, ни в состоянии: " + identifier);
    }

    private static boolean same(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number && actual instanceof Number) {
            BigDecimal left = SpecExpression.number(expected);
            BigDecimal right = SpecExpression.number(actual);
            BigDecimal scale = left.abs().max(right.abs()).max(BigDecimal.ONE);
            return left.subtract(right).abs().compareTo(scale.multiply(TOLERANCE)) <= 0;
        }
        return expected.toString().equals(actual.toString());
    }

    /**
     * Базовый гейт корпуса: есть ли что мерить и цел ли вход.
     *
     * <p><b>Зачем.</b> Всякая измерительная команда, у которой нет базового
     * гейта, на сломанном входе отчитывается вместо отказа: перечень пуст,
     * последняя строка зелена, и это читается как «дефектов нет». Гейт
     * отделяет «измерено, дефектов нет» от «мерить было нечем».
     *
     * <p>Проверяется: каталог непуст; каждый файл разбирается и загружается;
     * каждый пример сходится (включая системные отказы — нечитаемый
     * {@code stateFrom}); ни одна величина не несёт снятый ключ
     * {@code provenBy}; каждый контрпример называет, чем его состояние
     * исключено.
     *
     * @return перечень препятствий; пустой — гейт пройден
     */
    public static List<String> baseGate(Path directory) {
        List<String> obstacles = new ArrayList<>();
        List<Path> files;
        try {
            files = specFiles(directory);
        } catch (IOException failure) {
            return List.of("каталог спецификаций не прочитан: " + directory + " — " + failure.getMessage());
        }
        if (files.isEmpty()) {
            return List.of("в каталоге " + directory + " нет ни одной спецификации — мерить нечего");
        }
        obstacles.addAll(hiddenSpecFiles(directory));
        int declared = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            Map<String, Object> raw;
            try {
                raw = asMap(MAPPER.readValue(file.toFile(), Map.class));
            } catch (IOException | RuntimeException failure) {
                obstacles.add(name + " — файл не разобран: " + failure.getMessage());
                continue;
            }
            Object values = raw.get("values");
            if (values != null && !(values instanceof List)) {
                obstacles.add(name + " — ключ values не список: корпус структурно битый, а не «дефектов нет»");
                continue;
            }
            declared += list(values).size();
            obstacles.addAll(duplicateKeyObstacles(name, file));
            obstacles.addAll(structuralObstacles(name, raw));
            try {
                load(file).run().stream()
                        .map(failure -> name + " — " + failure)
                        .forEach(obstacles::add);
            } catch (IOException | RuntimeException failure) {
                obstacles.add(name + " — спецификация не загружена: " + failure.getMessage());
            }
        }
        if (declared == 0) {
            obstacles.add("в каталоге " + directory + " не объявлено ни одной величины — мерить нечего");
        }
        return obstacles;
    }

    /**
     * Повторный ключ JSON в одном объекте.
     *
     * <p>Форма предмета, которой не видит ни один детектор корпуса: они
     * читают разобранное дерево, а в нём дубля уже нет — парсер оставил
     * последнюю пару. Поэтому проверка идёт <b>строгим разбором самого
     * файла</b>, а не обходом дерева.
     */
    private static List<String> duplicateKeyObstacles(String name, Path file) {
        try {
            STRICT_MAPPER.readValue(file.toFile(), Map.class);
            return List.of();
        } catch (IOException | RuntimeException failure) {
            return List.of(name + " — повторный ключ JSON: парсер молча оставляет последнюю пару, "
                    + "и предыдущая редакция исчезает без следа (" + failure.getMessage() + ")");
        }
    }

    /**
     * Спецификации, лежащие в ПОДКАТАЛОГАХ каталога спек.
     *
     * <p>Перечень файлов нерекурсивен намеренно — область измерения должна
     * быть плоской и обозримой. Но файл, уехавший в подкаталог, тогда молча
     * выпадает из замера, а прогон остаётся зелёным: «измерено» и «не
     * попало в измерение» перестают различаться. Поэтому такой файл —
     * препятствие базового гейта, а не тихий пропуск.
     */
    // Сообщение препятствия читается контрфактически: без этого гейта файл
    // молча выпадал бы из замера при зелёном прогоне; теперь прогон на нём
    // отказывает кодом 2 (E4 DOCS_CHECK_33).
    private static List<String> hiddenSpecFiles(Path directory) {
        List<String> hidden = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !directory.equals(path.getParent()))
                    .forEach(path -> hidden.add(path + " — спецификация в подкаталоге: в область измерения "
                            + "не попадает, а прогон остаётся зелёным"));
        } catch (IOException failure) {
            hidden.add("каталог " + directory + " не обойдён: " + failure.getMessage());
        }
        return hidden;
    }

    /**
     * Структурные препятствия одного файла: снятый ключ-исключение и
     * контрпример без названного основания недостижимости.
     */
    private static List<String> structuralObstacles(String name, Map<String, Object> raw) {
        List<String> obstacles = new ArrayList<>();
        for (Object value : list(raw.get("values"))) {
            Map<String, Object> definition = asMap(value);
            if (definition.containsKey("provenBy")) {
                obstacles.add(name + " / " + definition.get("name") + " — ключ provenBy снят: "
                        + "класс «величина-теорема» упразднён, охранный инвариант доказывается "
                        + "предъявленным контрпримером (см. .claude/processes/roadmap-step-execution.md "
                        + "§«Охранный инвариант доказывается предъявленным контрпримером»)");
            }
        }
        for (Object population : list(raw.get("populations"))) {
            Map<String, Object> single = asMap(population);
            String axis = String.valueOf(single.get("axis"));
            if (list(single.get("keys")).isEmpty()) {
                obstacles.add(name + " / популяция «" + axis + "» — не объявлены ключи оси: "
                        + "по чему перебирается перечень, неизвестно");
            }
            if (list(single.get("members")).isEmpty()) {
                obstacles.add(name + " / популяция «" + axis + "» — перечень членов пуст: "
                        + "популяция без членов зелена всегда и не мерит ничего");
            }
            for (Object entry : list(single.get("members"))) {
                Map<String, Object> member = asMap(entry);
                if (list(member.get("member")).isEmpty()) {
                    obstacles.add(name + " / популяция «" + axis + "» — член без ключа member");
                }
                if (member.containsKey("unreachable")
                        && String.valueOf(member.get("unreachable")).trim().isEmpty()) {
                    obstacles.add(name + " / популяция «" + axis + "» — член "
                            + member.get("member") + " объявлен недостижимым, "
                            + "но не назвал, чем состояние исключено");
                }
            }
        }
        for (Object example : list(raw.get("examples"))) {
            Map<String, Object> single = asMap(example);
            if (!single.containsKey("unreachable")) {
                continue;
            }
            String reason = String.valueOf(single.get("unreachable")).trim();
            if (reason.isEmpty() || "null".equals(reason)) {
                obstacles.add(name + " / " + single.get("case")
                        + " — контрпример не называет, чем его состояние исключено");
            }
            if (asMap(single.get("expect")).isEmpty() && single.get("expectRefusal") == null) {
                obstacles.add(name + " / " + single.get("case")
                        + " — контрпример ничего не ожидает: он не предъявляет падения");
            }
        }
        return obstacles;
    }

    /** Число примеров-контрпримеров каталога: состояний, исключённых по построению. */
    public static int counterExamples(Path directory) throws IOException {
        int counter = 0;
        for (Path file : specFiles(directory)) {
            for (Object example : list(asMap(MAPPER.readValue(file.toFile(), Map.class)).get("examples"))) {
                if (asMap(example).containsKey("unreachable")) {
                    counter++;
                }
            }
        }
        return counter;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    /**
     * Автономный прогон: {@code java Spec <каталог-спецификаций>}.
     *
     * <p>Код возврата: 0 — примеры сошлись; 1 — есть расхождения; 2 — прогон
     * <b>не состоялся</b> (каталога нет, спецификаций нет, файл не разобран,
     * состояние примера не собрано, есть препятствие базового гейта — дубль
     * ключа, спека в подкаталоге, структурный дефект). Третий код заведён
     * затем, чтобы «прогонять было нечего» не читалось как «расхождений нет».
     */
    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args.length > 0 ? args[0] : "docs/spec");
        if (!Files.isDirectory(directory)) {
            System.out.println("ПРОГОН НЕ СОСТОЯЛСЯ: каталог спецификаций не найден — " + directory);
            System.exit(2);
        }
        List<Path> files = specFiles(directory);
        if (files.isEmpty()) {
            System.out.println("ПРОГОН НЕ СОСТОЯЛСЯ: в каталоге " + directory
                    + " нет ни одной спецификации — прогонять нечего");
            System.exit(2);
        }
        // Препятствия базового гейта (дубль ключа строгим разбором, спека в
        // подкаталоге, структурные дефекты) исполняются и штатной тропой, а не
        // только мутационной командой: дефект этих классов, внесённый правкой
        // примера, прежде проходил штатный прогон зелёным (E4 DOCS_CHECK_33).
        List<String> structural = new ArrayList<>(hiddenSpecFiles(directory));
        for (Path file : files) {
            String name = file.getFileName().toString();
            structural.addAll(duplicateKeyObstacles(name, file));
            try {
                structural.addAll(structuralObstacles(name, asMap(MAPPER.readValue(file.toFile(), Map.class))));
            } catch (IOException | RuntimeException failure) {
                structural.add(name + " — файл не разобран: " + failure.getMessage());
            }
        }
        if (!structural.isEmpty()) {
            structural.forEach(System.out::println);
            System.out.println("ПРОГОН НЕ СОСТОЯЛСЯ: препятствий базового гейта " + structural.size()
                    + " — корпус структурно битый, а не «расхождений нет»");
            System.exit(2);
        }
        List<String> failures = new ArrayList<>();
        List<String> refusals = new ArrayList<>();
        int cases = 0;
        int populations = 0;
        int members = 0;
        int unreachableMembers = 0;
        for (Path file : files) {
            try {
                Spec spec = load(file);
                cases += spec.examples().size();
                int[] size = spec.populationSize();
                populations += size[0];
                members += size[1];
                unreachableMembers += size[2];
                failures.addAll(spec.run());
            } catch (IOException | RuntimeException failure) {
                refusals.add(file.getFileName() + " — спецификация не загружена: " + failure.getMessage());
            }
        }
        failures.stream().filter(failure -> failure.startsWith(SYSTEM_FAILURE)).forEach(refusals::add);
        failures.removeIf(failure -> failure.startsWith(SYSTEM_FAILURE));

        System.out.println("Спецификаций: " + files.size() + ", примеров: " + cases
                + " (из них контрпримеров: " + counterExamples(directory) + ")");
        System.out.println("Популяций: " + populations + ", объявленных членов: " + members
                + " (из них недостижимых: " + unreachableMembers + ")");
        refusals.forEach(System.out::println);
        failures.forEach(System.out::println);
        if (!refusals.isEmpty()) {
            System.out.println("ПРОГОН НЕ СОСТОЯЛСЯ: отказов корпуса " + refusals.size()
                    + " — часть примеров не исполнялась, и зелёный остаток ничего не удостоверяет");
            System.exit(2);
        }
        System.out.println(failures.isEmpty() ? "ВСЕ ПРИМЕРЫ СОШЛИСЬ" : "РАСХОЖДЕНИЙ: " + failures.size());
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }
}
