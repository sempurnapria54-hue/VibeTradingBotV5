package com.example.connector.okx.box;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Сырые ответы площадки, снятые прогоном мишени {@code -D}, — записи для
 * стаба мишени {@code -S} ({@code B11.5-D}).
 *
 * <p><b>Дом записей — ресурсы тестов коннектора</b>
 * ({@code services/connector-okx/src/test/resources}; норма —
 * .claude/skills/test-code.md §«Уровень 1 — чёрный ящик сервиса»): это
 * ответы площадки, а не знание о ней. Каталог — {@link #DIRECTORY} от корня
 * модуля; запись на ответ, имя — операция площадки и состояние, в котором
 * она снята. Повторный прогон записи переписывает: свежий ответ площадки и
 * есть запись, а что изменилось, показывает история файла.
 *
 * <p><b>Записи копятся в памяти и пишутся одним ходом</b> в конце набора: кейс,
 * упавший посреди, не оставляет каталог наполовину переписанным чужими
 * состояниями.
 *
 * <p><b>Идентичность счёта маскируется, форма — нет.</b> Значения
 * {@link #MASKED_FIELDS} заменяются меткой: запись едет в репозиторий, а
 * стабу нужна форма ответа, а не идентификатор пользователя площадки. Ключей
 * в ответах площадки нет по построению — они живут в заголовках запроса.
 */
final class DemoRecordings {

    /** Каталог записей от корня модуля. */
    static final Path DIRECTORY = Path.of("src", "test", "resources", "okx", "demo");

    /** Поля ответа, чьё значение — идентичность счёта, а не форма. */
    private static final List<String> MASKED_FIELDS = List.of("uid", "mainUid", "ip", "label");

    private static final String MASK = "masked";

    private static final Map<String, String> RECORDS = new ConcurrentSkipListMap<>();

    private DemoRecordings() {
    }

    /** Запоминает сырой ответ под названным именем; повтор имени заменяет. */
    static void keep(String name, String body) {
        RECORDS.put(name, mask(body));
    }

    /** Имена запомненного. */
    static Set<String> names() {
        return Set.copyOf(RECORDS.keySet());
    }

    /**
     * Пишет запомненное в каталог записей.
     *
     * @return пути записанных файлов
     */
    static List<Path> flush() {
        Path directory = moduleRoot().resolve(DIRECTORY);
        try {
            Files.createDirectories(directory);
            for (Map.Entry<String, String> record : RECORDS.entrySet()) {
                Files.writeString(directory.resolve(record.getKey() + ".json"), record.getValue() + "\n",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Записи demo не записаны в " + directory, failure);
        }
        return RECORDS.keySet().stream()
                .map(name -> directory.resolve(name + ".json"))
                .collect(Collectors.toList());
    }

    /** Корень модуля: рабочий каталог surefire — каталог модуля. */
    private static Path moduleRoot() {
        return Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
    }

    private static String mask(String body) {
        String masked = body;
        for (String field : MASKED_FIELDS) {
            masked = Pattern.compile("\"" + field + "\":\"[^\"]*\"")
                    .matcher(masked)
                    .replaceAll("\"" + field + "\":\"" + MASK + "\"");
        }
        return masked;
    }
}
