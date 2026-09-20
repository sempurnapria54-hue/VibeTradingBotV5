package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Исполняемые тела исходников слоя перевода форм.
 *
 * <p><b>Клейм «ни один класс слоя не делает X» прогоном не
 * доказывается:</b> прогон видит только пройденную тропу, и класс,
 * который кейс не позвал, останется непредъявленным. Утверждение об
 * <b>области</b> читается по исполняемому телу исходников — за вычетом
 * блочных и строчных комментариев, потому что предмет клейма есть
 * поведение, а не текст пояснений (греп §Предмет документа даёт одно
 * попадание, и то в комментарии).
 *
 * <p><b>Базовый гейт непустоты обязателен:</b> пустой перечень файлов
 * делает всякое утверждение об отсутствии тождественно истинным, и
 * клейм тогда мерит не предмет, а путь к нему.
 */
final class LayerSources {

    /** Четыре пакета предмета: перевод, граничные формы, резолверы, разбор. */
    static final List<String> PACKAGES = List.of("mapping", "snapshot", "resolve", "util");

    private static final Path ROOT = Path.of("src", "main", "java", "com", "example", "connector", "okx");

    private LayerSources() {
    }

    /** Исполняемые тела всех файлов четырёх пакетов: имя файла → тело без комментариев. */
    static Map<String, String> ofLayer() {
        return of(PACKAGES);
    }

    /** Исполняемые тела файлов названных пакетов. */
    static Map<String, String> of(List<String> packages) {
        Map<String, String> bodies = new LinkedHashMap<>();
        for (String name : packages) {
            Map<String, String> ofPackage = read(ROOT.resolve(name));
            assertThat(ofPackage)
                    .as("базовый гейт: исходники пакета %s прочитаны", name)
                    .isNotEmpty();
            bodies.putAll(ofPackage);
        }
        return bodies;
    }

    private static Map<String, String> read(Path directory) {
        assertThat(directory).as("базовый гейт: каталог пакета существует").isDirectory();
        try (Stream<Path> files = Files.list(directory)) {
            Map<String, String> bodies = new LinkedHashMap<>();
            files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> bodies.put(path.getFileName().toString(), body(path)));
            return bodies;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Тело файла за вычетом блочных и строчных комментариев. */
    private static String body(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
