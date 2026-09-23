package com.example.tests.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

/**
 * Сторона тропы — процесс, поднятый boot-jar'ом своего модуля
 * (.claude/decisions/test-contour-design-pass.md §«12. Сторона сквозной
 * тропы поднимается СВОИМ ПРОЦЕССОМ, а не вторым контекстом в той же
 * JVM»).
 *
 * <p><b>Наблюдается только снаружи</b>: поверхностью, базой, темой — и двумя
 * журналами процесса. Журнал приложения несёт записи фасадов о конце тика
 * (фасад асинхронен, и его ответ говорит только о запуске). Журнал доступа
 * контейнера несёт каждое обращение к поверхности стороны с методом, путём,
 * кодом и заголовком {@code Authorization} — им читается, что сосед пришёл,
 * чем и с каким токеном, без единого стаба между сторонами.
 *
 * <p><b>Журнал доступа — ось конфигурации процесса, а не подмена:</b>
 * ключи {@code server.tomcat.accesslog.*} едут стороне тем же способом, что
 * адреса соседей, и её кода не трогают.
 */
public final class Side {

    private static final Duration START_LIMIT = Duration.ofSeconds(180);

    private static final Duration STOP_LIMIT = Duration.ofSeconds(30);

    private static final String ACCESS_PREFIX = "access";

    private static final String ACCESS_SUFFIX = ".log";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String module;
    private final Path jar;
    private final Path log;
    private final Path accessDirectory;
    private final String host;
    private final Integer port;
    private final Map<String, String> settings;
    private final List<String> jvmOptions = new ArrayList<>();
    private Process process;

    /**
     * @param module   имя модуля стороны — {@code services/<module>}
     * @param trail    имя тропы — разводит журналы двух троп одного прогона
     * @param settings ключи конфигурации процесса
     */
    public Side(String module, String trail, Map<String, String> settings) {
        this(module, trail, settings, "localhost", freePort());
    }

    /**
     * Сторона на названном адресе — у тропы, где соседа находят по конвенции
     * кластера: своим loopback-адресом на общем порту владельцев.
     *
     * @param module   имя модуля стороны
     * @param trail    имя тропы
     * @param settings ключи конфигурации процесса
     * @param host     адрес, который слушает сторона
     * @param port     порт
     */
    public Side(String module, String trail, Map<String, String> settings, String host, Integer port) {
        this.module = module;
        this.jar = jarOf(module);
        Path directory = Workspace.workDirectory(trail);
        this.log = directory.resolve(module + ".log");
        this.accessDirectory = Workspace.workDirectory(trail + "/" + module + "-access");
        this.host = host;
        this.port = port;
        this.settings = new LinkedHashMap<>(settings);
    }

    /** Добавляет опцию JVM процесса к следующему подъёму. */
    public void jvmOption(String option) {
        jvmOptions.add(option);
    }

    /** Имя модуля стороны. */
    public String module() {
        return module;
    }

    /** Адрес поверхности стороны — им её зовут соседи и тест. */
    public String baseUrl() {
        return "http://" + host + ":" + port;
    }

    /** Перекрывает ключ конфигурации к следующему подъёму. */
    public void set(String key, String value) {
        settings.put(key, value);
    }

    /** Запускает процесс, не дожидаясь готовности. */
    public void launch() {
        if (isAlive()) {
            throw new IllegalStateException("Сторона " + module + " уже поднята");
        }
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-Xmx512m");
        command.add("-XX:+UseSerialGC");
        command.add("-Dfile.encoding=UTF-8");
        command.addAll(jvmOptions);
        command.add("-jar");
        command.add(jar.toString());
        settings.forEach((key, value) -> command.add("--" + key + "=" + value));
        command.add("--server.port=" + port);
        if (isFalse(Objects.equals("localhost", host))) {
            command.add("--server.address=" + host);
        }
        command.add("--server.tomcat.accesslog.enabled=true");
        command.add("--server.tomcat.accesslog.directory=" + accessDirectory);
        command.add("--server.tomcat.accesslog.prefix=" + ACCESS_PREFIX);
        command.add("--server.tomcat.accesslog.suffix=" + ACCESS_SUFFIX);
        command.add("--server.tomcat.accesslog.buffered=false");
        command.add("--server.tomcat.accesslog.pattern=%m %U%q %s %{X-Tenant-Id}i %{Authorization}i");
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                    .start();
        } catch (IOException failure) {
            throw new IllegalStateException("Процесс стороны " + module + " не запустился", failure);
        }
        Process launched = process;
        Runtime.getRuntime().addShutdownHook(new Thread(launched::destroyForcibly));
    }

    /**
     * Ждёт готовности поверхности.
     *
     * <p><b>Умерший процесс — отказ, а не ожидание до потолка:</b> сторона,
     * не поднявшая контекст, называет причину в своём журнале, и хвост его
     * едет в сообщение.
     */
    public void awaitUp() {
        Instant deadline = Instant.now().plus(START_LIMIT);
        while (Instant.now().isBefore(deadline)) {
            if (isFalse(isAlive())) {
                throw new IllegalStateException("Сторона " + module + " умерла на подъёме:\n" + logTail());
            }
            if (isTrue(healthy())) {
                return;
            }
            pause();
        }
        throw new IllegalStateException("Сторона " + module + " не поднялась за " + START_LIMIT + ":\n" + logTail());
    }

    /** Останавливает процесс: сперва штатно, затем принудительно. */
    public void stop() {
        if (isFalse(isAlive())) {
            return;
        }
        process.destroy();
        try {
            if (isFalse(process.waitFor(STOP_LIMIT.toSeconds(), TimeUnit.SECONDS))) {
                process.destroyForcibly().waitFor(STOP_LIMIT.toSeconds(), TimeUnit.SECONDS);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** Жив ли процесс стороны. */
    public Boolean isAlive() {
        return nonNull(process) && process.isAlive();
    }

    /** Отметка журнала приложения: от неё читается след хода. */
    public Long logMark() {
        try {
            return Files.exists(log) ? Files.size(log) : 0L;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Журнал приложения после отметки. */
    public String logSince(Long mark) {
        try {
            if (isFalse(Files.exists(log))) {
                return "";
            }
            byte[] content = Files.readAllBytes(log);
            Integer from = (int) Math.min(mark, content.length);
            return new String(content, from, content.length - from, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Отметка журнала доступа — число записанных обращений. */
    public Integer accessMark() {
        return accessLines().size();
    }

    /** Обращения к поверхности стороны после отметки — в порядке прихода. */
    public List<Access> accessSince(Integer mark) {
        List<String> lines = accessLines();
        return lines.subList(Math.min(mark, lines.size()), lines.size()).stream()
                .map(Access::parse)
                .toList();
    }

    private List<String> accessLines() {
        try (Stream<Path> files = Files.list(accessDirectory)) {
            List<Path> sorted = files.filter(file -> file.getFileName().toString().startsWith(ACCESS_PREFIX))
                    .sorted()
                    .toList();
            List<String> lines = new ArrayList<>();
            for (Path file : sorted) {
                Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                        .filter(line -> isFalse(line.isBlank()))
                        .forEach(lines::add);
            }
            return lines;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private Boolean healthy() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/actuator/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (IOException notYetListening) {
            return Boolean.FALSE;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание подъёма стороны прервано", failure);
        }
    }

    private String logTail() {
        String text = logSince(0L);
        return text.substring(Math.max(0, text.length() - 6000));
    }

    /**
     * Исполняемый артефакт стороны.
     *
     * <p><b>Отсутствующий и устаревший jar — отказ «не измерялось», а не
     * пропуск.</b> Jar'ы появляются фазой {@code package}; после
     * {@code mvn test} их нет, а jar, оставшийся от прошлой сборки, мерил бы
     * прошлое дерево кода и печатал бы о нём зелёное.
     */
    private static Path jarOf(String module) {
        Path target = Workspace.repositoryRoot().resolve("services").resolve(module).resolve("target");
        Path jar = target.resolve(module + "-0.0.1-SNAPSHOT.jar");
        if (isFalse(Files.exists(jar))) {
            throw new IllegalStateException("Не измерялось: исполняемого артефакта стороны нет — " + jar
                    + ". Набор гоняется фазой verify: mvn -o -am -pl tests verify");
        }
        FileTime built = lastModified(jar);
        FileTime newestClass = newest(target.resolve("classes"));
        if (nonNull(newestClass) && newestClass.compareTo(built) > 0) {
            throw new IllegalStateException("Не измерялось: артефакт стороны " + module
                    + " старше её классов — прогон мерил бы прошлое дерево кода. Набор гоняется фазой verify");
        }
        return jar;
    }

    private static FileTime newest(Path directory) {
        if (isFalse(Files.isDirectory(directory))) {
            return null;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(Side::lastModified)
                    .max(FileTime::compareTo)
                    .orElse(null);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static FileTime lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path javaExecutable() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path windows = bin.resolve("java.exe");
        return Files.exists(windows) ? windows : bin.resolve("java");
    }

    private static Integer freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new IllegalStateException("Свободного порта для стороны нет", failure);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание подъёма стороны прервано", failure);
        }
    }

    /**
     * Обращение к поверхности стороны, прочитанное её журналом доступа.
     *
     * @param method        метод
     * @param uri           путь с запросом
     * @param status        код ответа
     * @param tenant        значение заголовка контекста тенанта; {@code -} — заголовка не было
     * @param authorization значение заголовка {@code Authorization}; {@code -} — заголовка не было
     */
    public record Access(String method, String uri, Integer status, String tenant, String authorization) {

        static Access parse(String line) {
            String[] parts = line.split(" ", 5);
            return new Access(parts[0], parts[1], Integer.valueOf(parts[2]), parts[3],
                    parts.length > 4 ? parts[4] : "-");
        }

        /** Путь без запроса. */
        public String path() {
            return uri.split("\\?")[0];
        }

        /** Токен предъявителя, либо пусто. */
        public String bearer() {
            return authorization.startsWith("Bearer ") ? authorization.substring("Bearer ".length()) : null;
        }

        /** Путь начинается названным префиксом. */
        public Boolean under(String prefix) {
            return path().startsWith(prefix);
        }

        /** Обращение — служебная проба живости, а не вызов поверхности. */
        public Boolean isProbe() {
            return path().startsWith("/actuator");
        }

        @Override
        public String toString() {
            return method + " " + uri + " " + status + (isNull(bearer()) ? " (без токена)" : "");
        }
    }
}
