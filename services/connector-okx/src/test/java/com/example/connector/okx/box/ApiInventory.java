package com.example.connector.okx.box;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Инвентарь полей нативной модели источника — дом апидока
 * {@code docs/models/integrations/okx/<Модель>.md}, прочитанный как вход
 * детектора дрейфа мишени {@code -D}
 * (.claude/tests/case-material/connector-okx.md §«3. Наблюдение → находка →
 * правка апидоков»).
 *
 * <p><b>Читаются два множества, и роли у них разные.</b>
 * <ul>
 *   <li><b>объявленные</b> — имена из первой клетки строк таблиц инвентаря,
 *       кроме таблиц подобъекта: поле, которое DTO источника ждёт на верхнем
 *       уровне записи. Его отсутствие в ответе — расхождение, на котором наш
 *       разбор теряет факт;</li>
 *   <li><b>упомянутые</b> — всякое имя в обратных кавычках где угодно в доке,
 *       включая перечни неиспользуемого прозой. Поле ответа, не упомянутое
 *       нигде, инвентарём не учтено вовсе.</li>
 * </ul>
 *
 * <p><b>Пустое объявленное множество — отказ, а не «расхождений нет»</b>: оно
 * значит, что разбор дока промахнулся по его форме, и сравнивать было не с чем
 * (.claude/rules/measurement-commands.md §«Измерительная команда отказывается,
 * когда мерить нечего»).
 *
 * @param declared  объявленные поля верхнего уровня
 * @param mentioned все упомянутые имена
 */
record ApiInventory(Set<String> declared, Set<String> mentioned) {

    private static final Pattern NAME = Pattern.compile("`([A-Za-z][A-Za-z0-9]*)");

    private static final Pattern FIRST_CELL = Pattern.compile("^\\|([^|]*)\\|");

    /** Признак заголовка таблицы подобъекта: её поля верхнему уровню не принадлежат. */
    private static final String NESTED_HEADING = "Подобъект";

    /**
     * Инвентарь модели по пути дома от корня репозитория.
     *
     * @param docFromRepositoryRoot путь дока, например
     *                              {@code docs/models/integrations/okx/InstrumentOkxResponse.md}
     */
    static ApiInventory of(String docFromRepositoryRoot) {
        Path doc = repositoryRoot().resolve(docFromRepositoryRoot).normalize();
        List<String> lines;
        try {
            lines = Files.readAllLines(doc);
        } catch (IOException failure) {
            throw new IllegalStateException("Инвентарь апидока не прочитан: " + doc, failure);
        }
        Set<String> declared = new TreeSet<>();
        Set<String> mentioned = new TreeSet<>();
        Boolean nested = Boolean.FALSE;
        for (String line : lines) {
            if (line.startsWith("#")) {
                nested = line.contains(NESTED_HEADING);
            }
            collect(NAME.matcher(line), mentioned);
            Matcher cell = FIRST_CELL.matcher(line);
            if (Boolean.FALSE.equals(nested) && cell.find()) {
                collect(NAME.matcher(cell.group(1)), declared);
            }
        }
        if (isEmpty(declared)) {
            throw new IllegalStateException("Инвентарь апидока не разобран — объявленных полей нет: " + doc);
        }
        return new ApiInventory(declared, mentioned);
    }

    /** Объявленные поля, которых нет в записи ответа. */
    Set<String> missingFrom(Set<String> recordFields) {
        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(recordFields);
        return missing;
    }

    /** Поля записи ответа, которых инвентарь не упоминает нигде. */
    Set<String> unaccountedIn(Set<String> recordFields) {
        Set<String> unaccounted = new TreeSet<>(recordFields);
        unaccounted.removeAll(mentioned);
        return unaccounted;
    }

    private static void collect(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }

    /** Корень репозитория: модуль лежит на два уровня ниже ({@code services/connector-okx}). */
    private static Path repositoryRoot() {
        return Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).resolve("../..");
    }
}
