package com.example.tests.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Где лежит то, что набор читает с диска: корень репозитория и рабочий
 * каталог прогона.
 *
 * <p><b>Оба адреса приезжают свойством прогона</b> ({@code pom.xml} набора,
 * конфигурация surefire), а не выводятся из рабочего каталога процесса:
 * прогон из реактора и прогон из IDE начинаются в разных каталогах.
 */
public final class Workspace {

    private static final String ROOT_KEY = "e2e.repository.root";

    private static final String WORK_KEY = "e2e.work.directory";

    private Workspace() {
    }

    /** Корень репозитория: от него читаются jar'ы сторон и эталоны владельцев. */
    public static Path repositoryRoot() {
        return Path.of(required(ROOT_KEY)).toAbsolutePath().normalize();
    }

    /**
     * Рабочий каталог прогона — журналы процессов сторон и их журналы доступа.
     *
     * @param name имя подкаталога
     * @return существующий каталог
     */
    public static Path workDirectory(String name) {
        Path directory = Path.of(required(WORK_KEY)).toAbsolutePath().normalize().resolve(name);
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new IllegalStateException("Рабочий каталог прогона не заводится: " + directory, failure);
        }
        return directory;
    }

    /**
     * Опустошает рабочий каталог тропы: журналы прошлого прогона читались бы
     * как след этого.
     *
     * @param name имя подкаталога
     */
    public static void clear(String name) {
        Path directory = workDirectory(name);
        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> deepestFirst = files.sorted(Comparator.reverseOrder()).toList();
            for (Path file : deepestFirst) {
                if (isFalse(file.equals(directory))) {
                    Files.delete(file);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Рабочий каталог тропы не опустошается: " + directory, failure);
        }
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (isNull(value) || isBlank(value)) {
            throw new IllegalStateException("Не измерялось: свойство прогона " + key
                    + " не задано — набор запускается maven-модулем tests, а не голым классом");
        }
        return value;
    }
}
