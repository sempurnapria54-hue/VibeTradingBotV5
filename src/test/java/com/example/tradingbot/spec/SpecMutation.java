package com.example.tradingbot.spec;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Мутационный замер исполнимой спецификации: доказательна ли объявленная
 * величина.
 *
 * <p><b>Предмет.</b> Зелёный прогон {@code tools/spec-run.sh} показывает,
 * что примеры <b>исполняются</b> и сходятся. Он не показывает, что они
 * что-то <b>проверяют</b>: величина, заменённая константой, может оставить
 * прогон зелёным — тогда объявленная форма не различает ничего, и любая её
 * ошибка пройдёт незамеченной. Ровно этот зазор и меряет мутация.
 *
 * <p><b>Как считается.</b> Определение величины подменяется константой
 * (перебираются {@code true}, {@code false}, {@code 0}, {@code 1},
 * {@code -1}), после чего прогоняется <b>весь</b> каталог спецификаций —
 * не только файл-дом: величина может проверяться примерами соседнего файла
 * через {@code includes}. Величина считается <b>доказанной</b>, если на
 * каждой из констант хотя бы один пример падает. Пережившая хотя бы одну
 * константу — <b>недоказательна</b>: она не имеет фальсифицирующего примера.
 *
 * <p><b>Величины-теоремы.</b> Часть охранных инвариантов ложна на всём
 * достижимом пространстве состояний по построению модели («снятие холда не
 * переводит объект в рабочее состояние»), и фальсифицирующего примера у них
 * не существует: их содержание <b>влечётся</b> другой величиной. Такая
 * величина объявляет {@code "provenBy": "<имя величины>"} и проверяется
 * <b>косвенной пробой</b>: мутируется названная величина, и хотя бы один
 * пример, ожидающий охранный инвариант, обязан упасть. Проба механическая —
 * указателем на произвольную величину от неё не отделаться: если инвариант
 * на мутацию своего основания не реагирует, он остаётся дефектом.
 *
 * <p>Стандарт приёмки — {@code .claude/processes/roadmap-step-execution.md}
 * §«Мутационная проба — условие приёмки спеки, а не только правки».
 */
public final class SpecMutation {

    /**
     * Базовые константы-нейтрализаторы. Набор дополняется <b>литералами
     * самого корпуса</b>: величина, ожидаемая во всех примерах одним и тем
     * же числом, фиксированным набором не ловится, а именно она и есть
     * недоказательная — её удовлетворяет константа, равная этому числу.
     */
    private static final List<String> BASE_CONSTANTS = List.of("true", "false", "0", "1", "null");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SpecMutation() {
    }

    /** Одна пережившая нейтрализацию величина: где объявлена и чем пережила. */
    public record Survivor(String spec, String value, String constant, String provenBy, boolean theorem) {

        @Override
        public String toString() {
            if (theorem) {
                return spec + " / " + value + " — теорема: ложна по построению, "
                        + "косвенная проба через " + provenBy + " пройдена";
            }
            if (provenBy != null) {
                return spec + " / " + value + " — переживает замену на " + constant
                        + "; объявленное основание " + provenBy + " косвенной пробы НЕ даёт";
            }
            return spec + " / " + value + " — переживает замену на " + constant;
        }
    }

    /**
     * Гоняет замер по каталогу спецификаций.
     *
     * @param source каталог-оригинал ({@code docs/spec})
     * @param work   рабочий каталог под мутированные копии
     * @return недоказательные величины, по одной записи на величину
     */
    public static List<Survivor> measure(Path source, Path work) throws IOException {
        Files.createDirectories(work);
        List<Path> files = Spec.specFiles(source);
        for (Path file : files) {
            Files.copy(file, work.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }

        Map<String, List<String>> expectedLiterals = expectedLiterals(files);
        List<Survivor> survivors = new ArrayList<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            Map<String, Object> raw = readMap(file);
            List<Object> declared = list(raw.get("values"));
            for (int index = 0; index < declared.size(); index++) {
                Map<String, Object> definition = asMap(declared.get(index));
                String name = String.valueOf(definition.get("name"));
                for (String constant : candidates(name, expectedLiterals)) {
                    writeMutated(work.resolve(fileName), raw, index, name, constant);
                    if (green(work)) {
                        Object provenBy = definition.get("provenBy");
                        String basis = provenBy == null ? null : String.valueOf(provenBy);
                        // Косвенная проба идёт по НЕмутированному охранному
                        // инварианту: иначе он и сам константа, и на мутацию
                        // основания среагировать не может.
                        Files.copy(file, work.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                        boolean theorem = basis != null
                                && entailed(files, work, expectedLiterals, basis, name);
                        survivors.add(new Survivor(fileName, name, constant, basis, theorem));
                        break;
                    }
                }
                // Вернуть файл в исходную форму до следующей величины.
                Files.copy(file, work.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return survivors;
    }

    /**
     * Косвенная проба величины-теоремы: мутируется её объявленное основание,
     * и хотя бы один пример, ожидающий {@code guard}, обязан упасть. Так
     * проверяется, что охранный инвариант читает содержание основания, а не
     * оказался тождественной константой сам по себе.
     */
    private static boolean entailed(List<Path> files, Path work, Map<String, List<String>> expectedLiterals,
                                    String basis, String guard) throws IOException {
        Path guardBackup = null;
        try {
            for (Path file : files) {
                Map<String, Object> raw = readMap(file);
                List<Object> declared = list(raw.get("values"));
                for (int index = 0; index < declared.size(); index++) {
                    if (!basis.equals(String.valueOf(asMap(declared.get(index)).get("name")))) {
                        continue;
                    }
                    String basisFile = file.getFileName().toString();
                    guardBackup = work.resolve(basisFile);
                    for (String constant : candidates(basis, expectedLiterals)) {
                        writeMutated(guardBackup, raw, index, basis, constant);
                        if (mentions(failures(work), guard)) {
                            Files.copy(file, guardBackup, StandardCopyOption.REPLACE_EXISTING);
                            return true;
                        }
                    }
                    Files.copy(file, guardBackup, StandardCopyOption.REPLACE_EXISTING);
                    return false;
                }
            }
            return false;
        } finally {
            if (guardBackup != null && !Files.exists(guardBackup)) {
                Files.createFile(guardBackup);
            }
        }
    }

    /** Расхождения прогона всего каталога (пустой список — всё сошлось). */
    private static List<String> failures(Path directory) throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path file : Spec.specFiles(directory)) {
            try {
                failures.addAll(Spec.load(file).run());
            } catch (RuntimeException failure) {
                failures.add("отказ загрузки " + file.getFileName() + ": " + failure.getMessage());
            }
        }
        return failures;
    }

    /** Есть ли среди расхождений хотя бы одно про названную величину. */
    private static boolean mentions(List<String> failures, String value) {
        String marker = ": " + value + " ";
        return failures.stream().anyMatch(failure -> failure.contains(marker));
    }

    /** Кандидаты-константы для величины: базовые плюс литералы её ожиданий. */
    private static List<String> candidates(String name, Map<String, List<String>> expectedLiterals) {
        List<String> candidates = new ArrayList<>(BASE_CONSTANTS);
        for (String literal : expectedLiterals.getOrDefault(name, List.of())) {
            if (!candidates.contains(literal)) {
                candidates.add(literal);
            }
        }
        return candidates;
    }

    /**
     * Литералы, ожидаемые примерами корпуса, по имени величины. Собираются
     * по ВСЕМ файлам: величина-дом может проверяться примерами соседа.
     */
    private static Map<String, List<String>> expectedLiterals(List<Path> files) throws IOException {
        Map<String, List<String>> literals = new LinkedHashMap<>();
        for (Path file : files) {
            for (Object example : list(readMap(file).get("examples"))) {
                for (Map.Entry<String, Object> expected : asMap(asMap(example).get("expect")).entrySet()) {
                    String rendered = render(expected.getValue());
                    if (rendered == null) {
                        continue;
                    }
                    List<String> forName = literals.computeIfAbsent(expected.getKey(), key -> new ArrayList<>());
                    if (!forName.contains(rendered)) {
                        forName.add(rendered);
                    }
                }
            }
        }
        return literals;
    }

    /** Значение примера — в литерал языка спецификаций; {@code null} — не выразимо. */
    private static String render(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof String text) {
            return "'" + text + "'";
        }
        return null;
    }

    /** Пишет копию файла, в которой определение величины заменено константой. */
    private static void writeMutated(Path target, Map<String, Object> raw, int index,
                                     String name, String constant) throws IOException {
        Map<String, Object> mutated = new LinkedHashMap<>(raw);
        List<Object> values = new ArrayList<>(list(raw.get("values")));
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("name", name);
        replacement.put("note", "МУТАЦИЯ: определение нейтрализовано константой");
        replacement.put("expr", constant);
        values.set(index, replacement);
        mutated.put("values", values);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), mutated);
    }

    /** Прогоняет весь каталог: {@code true} — ни один пример не упал. */
    private static boolean green(Path directory) throws IOException {
        for (Path file : Spec.specFiles(directory)) {
            try {
                if (!Spec.load(file).run().isEmpty()) {
                    return false;
                }
            } catch (RuntimeException failure) {
                // Отказ загрузки/вычисления — тоже падение: мутация замечена.
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> readMap(Path file) throws IOException {
        return asMap(MAPPER.readValue(file.toFile(), Map.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    /** Автономный прогон: {@code java SpecMutation <каталог-спецификаций> [рабочий-каталог]}. */
    public static void main(String[] args) throws IOException {
        Path source = Path.of(args.length > 0 ? args[0] : "docs/spec");
        Path work = Path.of(args.length > 1 ? args[1] : "target/spec-mutation");

        int declared = 0;
        for (Path file : Spec.specFiles(source)) {
            declared += list(asMap(MAPPER.readValue(file.toFile(), Map.class)).get("values")).size();
        }
        List<Survivor> survivors = measure(source, work);

        List<Survivor> theorems = survivors.stream().filter(Survivor::theorem).toList();
        List<Survivor> defects = survivors.stream().filter(survivor -> !survivor.theorem()).toList();

        System.out.println("Величин объявлено: " + declared
                + "; переживают прямую нейтрализацию: " + survivors.size()
                + " (из них теорем с пройденной косвенной пробой: " + theorems.size() + ")");
        if (!theorems.isEmpty()) {
            System.out.println("--- теоремы (ложны по построению, основание проверено косвенно)");
            theorems.forEach(theorem -> System.out.println("  " + theorem));
        }
        if (!defects.isEmpty()) {
            System.out.println("--- недоказательные величины");
            defects.forEach(defect -> System.out.println("  " + defect));
        }
        System.out.println(defects.isEmpty()
                ? "ВСЕ ВЕЛИЧИНЫ ДОКАЗАТЕЛЬНЫ"
                : "НЕДОКАЗАТЕЛЬНЫХ ВЕЛИЧИН: " + defects.size());
        if (!defects.isEmpty()) {
            System.exit(1);
        }
    }
}
