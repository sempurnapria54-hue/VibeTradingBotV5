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
import java.util.stream.Stream;

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
 * {@code null} плюс литералы ожиданий самого корпуса), после чего
 * прогоняется <b>весь</b> каталог спецификаций — не только файл-дом:
 * величина может проверяться примерами соседнего файла через
 * {@code includes}. Величина <b>доказана</b>, если на каждой из констант
 * хотя бы один пример расходится. Пережившая хотя бы одну константу —
 * <b>недоказательна</b>: фальсифицирующего примера у неё нет.
 *
 * <p><b>Замер отказывается, когда мерить нечего.</b> Три условия отказа, и
 * каждое отделяет «измерено, дефектов нет» от «измерять было нечем»:
 *
 * <ul>
 *   <li><b>базовый гейт</b> ({@link Spec#baseGate}) — немутированный корпус
 *       обязан быть зелен. Иначе всякая мутация «замечается» системным
 *       отказом, и сломанный корпус выглядит лучше здорового;</li>
 *   <li><b>системный отказ под мутацией</b> — нечитаемый артефакт или
 *       неразобранный файл падением мутации <b>не</b> засчитывается: замер
 *       останавливается громко;</li>
 *   <li><b>батарея осей</b> ({@link #battery}) — исполняется <b>той же
 *       командой</b> перед замером. Инструмент, чьи оси доказываются
 *       отдельным скриптом, принимается по факту, что скрипт когда-то
 *       прогоняли; здесь зелёный замер влечёт доказанность осей.</li>
 * </ul>
 *
 * <p><b>Исключений нет.</b> Класс «величина-теорема» с ключом
 * {@code provenBy} упразднён: ярлык удостоверял не свойство величины, а
 * наличие указателя. Охранный инвариант, ложный на всём достижимом
 * пространстве, доказывается <b>предъявленным контрпримером</b> — примером
 * с ключом {@code unreachable}, который называет, чем состояние исключено,
 * и на котором инвариант принимает запрещённое значение. Такая величина
 * падает на нейтрализации наравне со всеми и никакого ярлыка не требует.
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
    public record Survivor(String spec, String value, String constant) {

        @Override
        public String toString() {
            return spec + " / " + value + " — переживает замену на " + constant;
        }
    }

    /** Исход прогона каталога: сошлось / разошлось / корпус отказал. */
    private enum Outcome {
        GREEN, DIVERGED, BROKEN
    }

    /**
     * Гоняет замер по каталогу спецификаций.
     *
     * @param source каталог-оригинал ({@code docs/spec})
     * @param work   рабочий каталог под мутированные копии
     * @return недоказательные величины, по одной записи на величину
     * @throws SpecException замер не проводится: базовый гейт не пройден либо
     *                       корпус отказал под мутацией
     */
    public static List<Survivor> measure(Path source, Path work) throws IOException {
        List<String> obstacles = Spec.baseGate(source);
        if (!obstacles.isEmpty()) {
            throw new SpecException("базовый гейт не пройден (" + obstacles.size() + "):"
                    + System.lineSeparator() + "  " + String.join(System.lineSeparator() + "  ", obstacles));
        }
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
                    Outcome outcome = outcome(work);
                    if (outcome == Outcome.BROKEN) {
                        Files.copy(file, work.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                        throw new SpecException("корпус отказал под мутацией " + fileName + " / " + name
                                + " := " + constant + " — системный отказ падением мутации не засчитывается");
                    }
                    if (outcome == Outcome.GREEN) {
                        survivors.add(new Survivor(fileName, name, constant));
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
     * Прогон всего каталога. {@link Outcome#BROKEN} — отказ корпуса
     * (неразобранный файл, несобранное состояние): он означает «мерить
     * нечем», а не «мутация замечена», и от расхождения примера отличается
     * маркером {@link Spec#SYSTEM_FAILURE}.
     */
    private static Outcome outcome(Path directory) throws IOException {
        Outcome outcome = Outcome.GREEN;
        for (Path file : Spec.specFiles(directory)) {
            List<String> failures;
            try {
                failures = Spec.load(file).run();
            } catch (RuntimeException failure) {
                return Outcome.BROKEN;
            }
            for (String failure : failures) {
                if (failure.startsWith(Spec.SYSTEM_FAILURE)) {
                    return Outcome.BROKEN;
                }
                outcome = Outcome.DIVERGED;
            }
        }
        return outcome;
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

    // --- батарея осей ------------------------------------------------------

    /**
     * Фикстура батареи: самодостаточная спека с величинами, доказательность
     * каждой из которых известна по построению.
     *
     * <p>Самодостаточность намеренна: проба, заякоренная на величину живого
     * корпуса, зеленеет от любой правки этого корпуса, и переякоривание проб
     * становится постоянной работой.
     */
    private static final String FIXTURE = """
            {
             "subject": "probe-mutation",
             "question": "Ловит ли мутационный замер недоказательную величину",
             "home": "src/test/java/com/example/tradingbot/spec/SpecMutation.java",
             "operands": { "amount": "число примера — единственный операнд фикстуры" },
             "values": [
              { "name": "probeProven",
                "note": "ДОКАЗАНА: примеры ожидают и true, и false.",
                "expr": "amount > 0" },
              { "name": "probeUnreferenced",
                "note": "НЕДОКАЗАТЕЛЬНА: величину не зовёт ни один пример.",
                "expr": "amount < 0" },
              { "name": "probeOneSided",
                "note": "НЕДОКАЗАТЕЛЬНА: предикат ожидается true во всех примерах.",
                "expr": "amount >= 0" },
              { "name": "probeNumberSingle",
                "note": "НЕДОКАЗАТЕЛЬНА: число ожидается одним и тем же номиналом.",
                "expr": "amount * 0 + 7" },
              { "name": "probeNumberVaried",
                "note": "ДОКАЗАНА: число ожидается двумя разными номиналами.",
                "expr": "amount * 2" },
              { "name": "probeGuardWithCounterexample",
                "note": "ДОКАЗАНА: охранный инвариант с предъявленным контрпримером.",
                "expr": "amount < 0" },
              { "name": "probeGuardWithoutCounterexample",
                "note": "НЕДОКАЗАТЕЛЬНА: охранный инвариант, ожидаемый false везде и не предъявивший состояния, на котором он истинен.",
                "expr": "amount < -1" }
             ],
             "examples": [
              { "case": "положительное значение",
                "state": { "amount": 5 },
                "expect": { "probeProven": true, "probeOneSided": true, "probeNumberSingle": 7,
                            "probeNumberVaried": 10, "probeGuardWithCounterexample": false,
                            "probeGuardWithoutCounterexample": false } },
              { "case": "нулевое значение",
                "state": { "amount": 0 },
                "expect": { "probeProven": false, "probeOneSided": true, "probeNumberSingle": 7,
                            "probeNumberVaried": 0, "probeGuardWithCounterexample": false,
                            "probeGuardWithoutCounterexample": false } },
              { "case": "контрпример: отрицательный объём",
                "unreachable": "объём операции неотрицателен по построению модели",
                "state": { "amount": -3 },
                "expect": { "probeGuardWithCounterexample": true } }
             ]
            }
            """;

    /**
     * Вторая фикстура батареи: формы, которых нет в первой — <b>агрегат</b>
     * ({@code op/over/where/of}) и величина, проверяемая примерами
     * <b>соседней</b> спеки через {@code includes}. Обе формы объявлены
     * шапкой команды, и обе обязаны иметь свою ось: перечень форм без оси
     * на каждую — тот самый клейм полноты, который стандарт отменяет.
     */
    private static final String FIXTURE_HOME = """
            {
             "subject": "probe-home",
             "question": "Ловит ли замер формы, которых нет в скалярной однофайловой фикстуре",
             "home": "src/test/java/com/example/tradingbot/spec/SpecMutation.java",
             "operands": { "rows[]": "коллекция строк фикстуры", "amount": "число примера" },
             "values": [
              { "name": "probeAggregateOneSided",
                "note": "НЕДОКАЗАТЕЛЬНА: агрегат ожидается одним и тем же числом.",
                "op": "count", "over": "rows", "where": "size > 0" },
              { "name": "probeAggregateVaried",
                "note": "ДОКАЗАНА: агрегат ожидается двумя разными числами.",
                "op": "sum", "over": "rows", "of": "size" },
              { "name": "probeHomeCalledByNeighbour",
                "note": "НЕДОКАЗАТЕЛЬНА: величину зовут только примеры СОСЕДА, и там она ожидается одним исходом.",
                "expr": "amount >= 0" }
             ],
             "examples": [
              { "case": "две строки", "state": { "amount": 1, "rows": [ { "size": 2 }, { "size": 3 } ] },
                "expect": { "probeAggregateOneSided": 2, "probeAggregateVaried": 5 } },
              { "case": "две строки, другие размеры", "state": { "amount": 2, "rows": [ { "size": 1 }, { "size": 1 } ] },
                "expect": { "probeAggregateOneSided": 2, "probeAggregateVaried": 2 } }
             ]
            }
            """;

    private static final String FIXTURE_NEIGHBOUR = """
            {
             "subject": "probe-neighbour",
             "question": "Проверяет ли сосед величину чужого дома",
             "home": "src/test/java/com/example/tradingbot/spec/SpecMutation.java",
             "includes": ["probe-home"],
             "operands": { "amount": "число примера" },
             "values": [],
             "examples": [
              { "case": "сосед зовёт величину дома", "state": { "amount": 5 },
                "expect": { "probeHomeCalledByNeighbour": true } }
             ]
            }
            """;


    /**
     * Третья фикстура батареи: <b>популяция</b> — перечень состояний, на
     * которых правило спеки обязано выполняться.
     *
     * <p>Форма объявлена шапкой команды и обязана иметь свои оси: перечень,
     * который никто не проверяет, зелен всегда. Три вида расхождения —
     * непокрытый член, недостижимость без контрпримера, пример вне перечня —
     * и контроль на полной популяции.
     */
    private static final String FIXTURE_POPULATION = """
            {
             "subject": "probe-population",
             "question": "Ловит ли замер непокрытую популяцию",
             "home": "src/test/java/com/example/tradingbot/spec/SpecMutation.java",
             "operands": { "from": "статус до", "to": "статус после", "amount": "число примера" },
             "values": [
              { "name": "probePopulationVaried",
                "note": "ДОКАЗАНА: ожидается двумя разными номиналами.",
                "expr": "amount * 2" }
             ],
             "populations": [
              { "axis": "рёбра пробы",
                "rule": ["probePopulationVaried"],
                "derive": { "incomplete": "перечень рёбер пробы задан фикстурой, а не выводим из корпуса" },
                "keys": ["from", "to"],
                "members": [
                 { "member": ["A", "B"] },
                 { "member": ["A", "C"] },
                 { "member": ["B", "A"], "unreachable": "обратного ребра у пробы нет по построению" }
                ] }
             ],
             "examples": [
              { "case": "ребро A-B", "state": { "from": "A", "to": "B", "amount": 1 },
                "expect": { "probePopulationVaried": 2 } },
              { "case": "ребро A-C", "state": { "from": "A", "to": "C", "amount": 2 },
                "expect": { "probePopulationVaried": 4 } },
              { "case": "контрпример: обратное ребро",
                "unreachable": "у пробы обратного ребра нет",
                "state": { "from": "B", "to": "A", "amount": 3 },
                "expect": { "probePopulationVaried": 6 } }
             ]
            }
            """;

    /** Одна ось батареи: имя, ожидание и его исход. */
    private record Axis(String name, boolean passed, String observed) {
    }

    /**
     * Батарея осей замера: исполняется <b>той же командой</b>, что и замер.
     *
     * <p>Каждая ось — фикстура с заведомо известным исходом; ось засчитана,
     * только когда замер разложил фикстуру ровно так, как объявлено. Оси
     * отказа (сломанный корпус, расходящийся корпус, пустой каталог, снятый
     * ключ) проверяют, что замер <b>отказывается</b>, а не отчитывается.
     *
     * @return перечень осей с исходами
     */
    public static List<Axis> battery(Path sandbox) throws IOException {
        List<Axis> axes = new ArrayList<>();
        Path corpus = sandbox.resolve("корпус");
        Files.createDirectories(corpus);
        Files.writeString(corpus.resolve("probe-mutation.json"), FIXTURE);
        List<String> survivors;
        try {
            survivors = measure(corpus, sandbox.resolve("работа")).stream()
                    .map(Survivor::value).toList();
        } catch (SpecException refusal) {
            axes.add(new Axis("фикстура батареи проходит базовый гейт", false, refusal.getMessage()));
            return axes;
        }
        axes.add(present(survivors, "1. величину не зовёт ни один пример", "probeUnreferenced", true));
        axes.add(present(survivors, "2. предикат без фальсифицирующего примера", "probeOneSided", true));
        axes.add(present(survivors, "3. число с единственным ожидаемым номиналом", "probeNumberSingle", true));
        axes.add(present(survivors, "4. охранный инвариант без контрпримера",
                "probeGuardWithoutCounterexample", true));
        axes.add(present(survivors, "5. контроль: предикат с обоими исходами", "probeProven", false));
        axes.add(present(survivors, "6. контроль: число с двумя номиналами", "probeNumberVaried", false));
        axes.add(present(survivors, "7. контроль: охранный инвариант с предъявленным контрпримером",
                "probeGuardWithCounterexample", false));

        Path pair = Files.createTempDirectory(sandbox, "формы");
        Files.writeString(pair.resolve("probe-home.json"), FIXTURE_HOME);
        Files.writeString(pair.resolve("probe-neighbour.json"), FIXTURE_NEIGHBOUR);
        List<String> pairSurvivors;
        try {
            pairSurvivors = measure(pair, Files.createTempDirectory(sandbox, "работа")).stream()
                    .map(Survivor::value).toList();
        } catch (SpecException refusal) {
            pairSurvivors = List.of("ОТКАЗ: " + firstLine(refusal.getMessage()));
        }
        axes.add(present(pairSurvivors, "8. форма-агрегат (op/over/where/of)",
                "probeAggregateOneSided", true));
        axes.add(present(pairSurvivors, "9. контроль: агрегат с двумя исходами",
                "probeAggregateVaried", false));
        axes.add(present(pairSurvivors, "10. величина, проверяемая примерами СОСЕДА через includes",
                "probeHomeCalledByNeighbour", true));

        axes.add(refusesHidden(sandbox, "11. спецификация в подкаталоге — замер отказывает"));
        axes.add(refuses(sandbox, "12. корпус без единой величины — замер отказывает",
                "{\"subject\": \"пусто\", \"values\": [], \"examples\": []}"));
        axes.add(refuses(sandbox, "13. ключ values не список — замер отказывает",
                FIXTURE.replace("\"values\": [", "\"values\": {\"нет\": [")
                       .replace("\"examples\": [", "}, \"examples\": [")));
        axes.add(refuses(sandbox, "14. корпус не собирает состояние — замер отказывает",
                FIXTURE.replace("\"state\": { \"amount\": 5 }",
                        "\"stateFrom\": { \"загрузка\": \"нет-такого-файла.json\" }, \"state\": { \"amount\": 5 }")));
        axes.add(refuses(sandbox, "15. корпус расходится с примером — замер отказывает",
                FIXTURE.replace("\"probeNumberVaried\": 10", "\"probeNumberVaried\": 11")));
        axes.add(refuses(sandbox, "16. снятый ключ provenBy — замер отказывает",
                FIXTURE.replace("\"name\": \"probeOneSided\",",
                        "\"name\": \"probeOneSided\", \"provenBy\": \"probeProven\",")));
        axes.add(refuses(sandbox, "17a. повторный ключ JSON — замер отказывает",
                FIXTURE.replace("\"subject\": \"probe-mutation\",",
                        "\"subject\": \"probe-mutation\", \"subject\": \"вторая редакция того же ключа\",")));
        axes.add(refusesEmpty(sandbox, "17. пустой каталог — замер отказывает"));

        // --- популяция: перечень состояний, на которых правило обязано выполняться
        axes.add(accepts(sandbox, "18. контроль: полная популяция замер не роняет",
                FIXTURE_POPULATION));
        axes.add(refuses(sandbox, "19. член популяции без единого примера — замер отказывает",
                FIXTURE_POPULATION.replace("\"to\": \"C\"", "\"to\": \"B\"")));
        axes.add(refuses(sandbox, "20. недостижимый член без контрпримера — замер отказывает",
                FIXTURE_POPULATION.replace("\"unreachable\": \"у пробы обратного ребра нет\",",
                        "\"note\": \"пример перестал быть контрпримером\",")));
        axes.add(refuses(sandbox, "21. пример предъявляет члена вне перечня — замер отказывает",
                FIXTURE_POPULATION.replace("{ \"member\": [\"A\", \"C\"] },", "")));
        axes.add(refuses(sandbox, "22. популяция без ключей оси — замер отказывает",
                FIXTURE_POPULATION.replace("\"keys\": [\"from\", \"to\"],", "")));
        // Фильтр where отсекает пример, предъявляющий состояние ВНЕ популяции
        // намеренно. Ось контрольная: без неё фильтр мог бы отсекать всё
        // подряд, и популяция зеленела бы на пустом участии.
        axes.add(accepts(sandbox, "23. фильтр where исключает непричастный пример",
                FIXTURE_POPULATION
                        .replace("\"keys\": [\"from\", \"to\"],",
                                "\"keys\": [\"from\", \"to\"], \"where\": \"amount < 9\", "
                                        + "\"excludes\": \"пробы, поданные спеке вне популяции рёбер\",")
                        .replace("\"examples\": [",
                                "\"examples\": [\n"
                                        + "  { \"case\": \"вне популяции: члена X-Y перечень не объявляет\",\n"
                                        + "    \"state\": { \"from\": \"X\", \"to\": \"Y\", \"amount\": 9 },\n"
                                        + "    \"expect\": { \"probePopulationVaried\": 18 } },")));
        axes.add(refuses(sandbox, "24. контроль оси 23: без фильтра тот же пример роняет замер",
                FIXTURE_POPULATION.replace("\"examples\": [",
                        "\"examples\": [\n"
                                + "  { \"case\": \"вне популяции: члена X-Y перечень не объявляет\",\n"
                                + "    \"state\": { \"from\": \"X\", \"to\": \"Y\", \"amount\": 9 },\n"
                                + "    \"expect\": { \"probePopulationVaried\": 18 } },")));

        // --- происхождение перечня и критерий покрытия (редакция 2026-08-31)
        // Три способа сделать полноту самореферентной: перечень без названного
        // правила меряет ПРИСУТСТВИЕ кортежа; перечень без названного
        // происхождения собран из того же текста, который проверяет; фильтр,
        // выраженный вычисленным вердиктом, выводит из популяции ровно
        // фальсифицирующее состояние. Каждый способ — своя ось.
        axes.add(refuses(sandbox, "25. популяция не называет величину правила — замер отказывает",
                FIXTURE_POPULATION.replace("\"rule\": [\"probePopulationVaried\"],", "")));
        axes.add(refuses(sandbox, "26. величина правила не объявлена в спеке — замер отказывает",
                FIXTURE_POPULATION.replace("\"rule\": [\"probePopulationVaried\"],",
                        "\"rule\": [\"probeНетТакойВеличины\"],")));
        axes.add(refuses(sandbox, "27. кортеж предъявлен, а правило на нём не проверяется — член не покрыт",
                FIXTURE_POPULATION.replace(
                        "\"expect\": { \"probePopulationVaried\": 4 } }",
                        "\"expect\": {} }")));
        axes.add(refuses(sandbox, "28. популяция без объявленного происхождения перечня — замер отказывает",
                FIXTURE_POPULATION.replace(
                        "\"derive\": { \"incomplete\": \"перечень рёбер пробы задан фикстурой, "
                                + "а не выводим из корпуса\" },", "")));
        axes.add(refuses(sandbox, "29. команда вывода не называет артефакт-предмет — замер отказывает",
                FIXTURE_POPULATION.replace(
                        "\"incomplete\": \"перечень рёбер пробы задан фикстурой, а не выводим из корпуса\"",
                        "\"command\": \"printf 'A\\\\tB'\"")));
        axes.add(refuses(sandbox, "30. фильтр участия не называет исключаемый класс — замер отказывает",
                FIXTURE_POPULATION.replace("\"keys\": [\"from\", \"to\"],",
                        "\"keys\": [\"from\", \"to\"], \"where\": \"amount < 9\",")));
        axes.add(refuses(sandbox, "31. фильтр участия выражен вычисленным вердиктом — замер отказывает",
                FIXTURE_POPULATION.replace("\"keys\": [\"from\", \"to\"],",
                        "\"keys\": [\"from\", \"to\"], \"where\": \"probePopulationVaried < 99\", "
                                + "\"excludes\": \"пробы с большим номиналом\",")));
        axes.add(accepts(sandbox, "32. контроль: объявленная неполнота перечня замер не роняет",
                FIXTURE_POPULATION));
        return axes;
    }

    private static Axis present(List<String> survivors, String axis, String value, boolean expected) {
        boolean actual = survivors.contains(value);
        String observed = actual ? value + " в перечне недоказательных" : value + " вне перечня";
        return new Axis(axis, actual == expected, observed);
    }

    /**
     * Ось контроля: на здоровом корпусе замер обязан <b>отчитаться</b>.
     *
     * <p>Без неё оси отказа ничего не устанавливают: команда, отказывающая
     * всегда, прошла бы их все.
     */
    private static Axis accepts(Path sandbox, String axis, String fixture) throws IOException {
        Path corpus = Files.createTempDirectory(sandbox, "контроль");
        Files.writeString(corpus.resolve("probe-population.json"), fixture);
        try {
            measure(corpus, Files.createTempDirectory(sandbox, "работа"));
            return new Axis(axis, true, "замер отчитался, как и требовалось");
        } catch (SpecException refusal) {
            return new Axis(axis, false, "ОТКАЗ на здоровом корпусе: " + firstLine(refusal.getMessage()));
        }
    }

    /** Ось отказа: на подложенном корпусе замер обязан отказаться, а не отчитаться. */
    private static Axis refuses(Path sandbox, String axis, String fixture) throws IOException {
        Path corpus = Files.createTempDirectory(sandbox, "отказ");
        Files.writeString(corpus.resolve("probe-mutation.json"), fixture);
        try {
            List<Survivor> survivors = measure(corpus, Files.createTempDirectory(sandbox, "работа"));
            return new Axis(axis, false, "замер отчитался: недоказательных " + survivors.size());
        } catch (SpecException refusal) {
            return new Axis(axis, true, "отказ: " + firstLine(refusal.getMessage()));
        }
    }

    /** Ось отказа: спецификация в подкаталоге в область измерения не попадает. */
    private static Axis refusesHidden(Path sandbox, String axis) throws IOException {
        Path corpus = Files.createTempDirectory(sandbox, "подкаталог");
        Files.writeString(corpus.resolve("probe-mutation.json"), FIXTURE);
        Path nested = Files.createDirectory(corpus.resolve("вложенный"));
        Files.writeString(nested.resolve("probe-hidden.json"), FIXTURE);
        try {
            measure(corpus, Files.createTempDirectory(sandbox, "работа"));
            return new Axis(axis, false, "замер отчитался, не заметив спеки в подкаталоге");
        } catch (SpecException refusal) {
            return new Axis(axis, true, "отказ: " + firstLine(refusal.getMessage()));
        }
    }

    private static Axis refusesEmpty(Path sandbox, String axis) throws IOException {
        Path corpus = Files.createTempDirectory(sandbox, "пусто");
        try {
            measure(corpus, Files.createTempDirectory(sandbox, "работа"));
            return new Axis(axis, false, "замер отчитался на пустом каталоге");
        } catch (SpecException refusal) {
            return new Axis(axis, true, "отказ: " + firstLine(refusal.getMessage()));
        }
    }

    private static String firstLine(String message) {
        int end = message.indexOf(System.lineSeparator());
        return end < 0 ? message : message.substring(0, end);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // временный каталог: остаток уборки не меняет исхода замера
                }
            });
        }
    }

    /**
     * Автономный прогон: {@code java SpecMutation <каталог-спек> [рабочий-каталог]}.
     *
     * <p>Код возврата: 0 — все величины доказательны; 1 — есть
     * недоказательные; 2 — <b>замер не проводился</b> (ось батареи не
     * доказана, базовый гейт не пройден, корпус отказал под мутацией).
     */
    public static void main(String[] args) throws IOException {
        Path source = Path.of(args.length > 0 ? args[0] : "docs/spec");
        Path work = Path.of(args.length > 1 ? args[1] : "target/spec-mutation");

        Path sandbox = Files.createTempDirectory("spec-mutation-battery");
        List<Axis> axes;
        try {
            axes = battery(sandbox);
        } finally {
            deleteTree(sandbox);
        }
        System.out.println("--- батарея осей замера (исполняется той же командой)");
        axes.forEach(axis -> System.out.println("  " + (axis.passed() ? "доказана" : "НЕ ДОКАЗАНА")
                + ": " + axis.name() + " — " + axis.observed()));
        long broken = axes.stream().filter(axis -> !axis.passed()).count();
        if (broken > 0) {
            System.out.println("ЗАМЕР НЕ ПРОВОДИТСЯ: недоказанных осей " + broken
                    + " — перечень величин ничего не удостоверял бы");
            System.exit(2);
        }

        int declared = 0;
        try {
            for (Path file : Spec.specFiles(source)) {
                declared += list(asMap(MAPPER.readValue(file.toFile(), Map.class)).get("values")).size();
            }
        } catch (IOException | RuntimeException refusal) {
            System.out.println("ЗАМЕР НЕ ПРОВОДИТСЯ: перечень величин не собран — " + refusal.getMessage());
            System.exit(2);
            return;
        }
        List<Survivor> survivors;
        try {
            survivors = measure(source, work);
        } catch (RuntimeException refusal) {
            // Любой отказ разбора корпуса — «не измерялось» (код 2), а не
            // «измерено, дефекты есть»: структурно битый вход прежде уходил
            // наружу исключением и давал код 1, то есть ровно тот зазор,
            // против которого третий код и введён.
            System.out.println("ЗАМЕР НЕ ПРОВОДИТСЯ: " + refusal.getMessage());
            System.exit(2);
            return;
        }

        System.out.println("Величин объявлено: " + declared
                + "; контрпримеров в корпусе: " + Spec.counterExamples(source)
                + "; переживают нейтрализацию: " + survivors.size());
        if (!survivors.isEmpty()) {
            System.out.println("--- недоказательные величины");
            survivors.forEach(survivor -> System.out.println("  " + survivor));
        }
        System.out.println(survivors.isEmpty()
                ? "ВСЕ ВЕЛИЧИНЫ ДОКАЗАТЕЛЬНЫ"
                : "НЕДОКАЗАТЕЛЬНЫХ ВЕЛИЧИН: " + survivors.size());
        if (!survivors.isEmpty()) {
            System.exit(1);
        }
    }
}
