package com.example.tradingbot.spec;

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

    /** Спецификация проверяет алгебру, а не десятичный шум округления. */
    private static final BigDecimal TOLERANCE = new BigDecimal("1e-9");

    private final String subject;

    private final Map<String, Map<String, Object>> values = new LinkedHashMap<>();

    private final List<Map<String, Object>> examples = new ArrayList<>();

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
            Map<String, Object> state = stateOf(example);
            for (Map.Entry<String, Object> expected : asMap(example.get("expect")).entrySet()) {
                String name = expected.getKey();
                try {
                    Object actual = evaluate(name, state, Map.of(), new ArrayDeque<>());
                    if (!same(expected.getValue(), actual)) {
                        failures.add("%s / %s: %s ожидалось %s, получено %s"
                                .formatted(subject, label, name, expected.getValue(), actual));
                    }
                } catch (SpecException failure) {
                    failures.add("%s / %s: %s — отказ вычисления: %s"
                            .formatted(subject, label, name, failure.getMessage()));
                }
            }
            Object refusal = example.get("expectRefusal");
            if (refusal != null) {
                failures.addAll(checkRefusal(label, state, asMap(refusal)));
            }
        }
        return failures;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    /** Автономный прогон: {@code java Spec <каталог-спецификаций>}. */
    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args.length > 0 ? args[0] : "docs/spec");
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (Path file : specFiles(directory)) {
            Spec spec = load(file);
            cases += spec.examples().size();
            failures.addAll(spec.run());
        }
        System.out.println("Спецификаций: " + specFiles(directory).size() + ", примеров: " + cases);
        failures.forEach(System.out::println);
        System.out.println(failures.isEmpty() ? "ВСЕ ПРИМЕРЫ СОШЛИСЬ" : "РАСХОЖДЕНИЙ: " + failures.size());
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }
}
