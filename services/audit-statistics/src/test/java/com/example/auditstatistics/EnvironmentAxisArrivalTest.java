package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auditstatistics.config.EnvironmentProperties;
import com.example.auditstatistics.domain.model.JournalRetentionProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Ось окружения доезжает до сервиса и разбирается им
 * (docs/models/domain/other/AuditRecord.md §«Глубина хранения»: «ось
 * обязана дойти до исполнителя … иначе предикат применимости не
 * вычисляется ничем»).
 *
 * <p><b>Значения берутся из дома, а не пишутся рядом.</b> Тест читает
 * {@code deploy/<окружение>/env.yaml} — то самое место, где значения оси
 * назначаются, — и требует, чтобы <b>каждое</b> из них разбиралось в
 * значение перечня. Список значений, написанный в тесте руками, был бы
 * вторым носителем и разошёлся бы с манифестами на первом же новом
 * окружении или профиле.
 *
 * <p><b>Что тест мерит, а что нет.</b> Он мерит два звена тропы: имя
 * свойства, которым ось входит в сервис, и разбор значения манифеста.
 * Среднего звена — что ключ манифеста сервиса
 * ({@code deploy/base/services/audit-statistics.yaml}) подставляет
 * переменную окружения в это свойство — он не мерит: она резолвится
 * кластером, и здесь её нет. Ограничение названо, а не умолчано.
 */
class EnvironmentAxisArrivalTest {

    /** Свойство, которым ось входит в сервис; форма — application.yaml. */
    private static final String AXIS_PROPERTY = "platform.environment.journal-retention-profile";

    /** Дом значений оси: манифесты окружений монорепозитория. */
    private static final Path ENVIRONMENTS = Path.of("..", "..", "deploy");

    /** Ключ оси в манифесте окружения (docs/architecture/platform.md). */
    private static final Pattern AXIS_LINE =
            Pattern.compile("^\\s*journalRetentionProfile:\\s*\"?([A-Za-z-]+)\"?\\s*$");

    @Test
    @DisplayName("Каждое значение оси из манифестов окружений разбирается перечнем")
    void everyAxisValueDeclaredByAnEnvironmentIsUnderstood() throws IOException {
        Map<String, String> declared = declaredAxisValues();

        assertThat(declared)
                .as("манифестов окружений с осью не найдено — мерить нечего, а не «всё сошлось»")
                .isNotEmpty();
        declared.forEach((environment, value) ->
                assertThat(bind(value))
                        .as("значение оси окружения %s обязано разбираться перечнем", environment)
                        .isNotNull());
    }

    @Test
    @DisplayName("Пустое значение не подменяется профилем: ось просто не доехала")
    void anEmptyAxisStaysEmpty() {
        assertThat(bind("")).isNull();
    }

    /** Значения оси по окружениям, прочитанные из их манифестов. */
    private Map<String, String> declaredAxisValues() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (Path manifest : environmentManifests()) {
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                Matcher matcher = AXIS_LINE.matcher(line);
                if (matcher.matches()) {
                    values.put(manifest.getParent().getFileName().toString(), matcher.group(1));
                }
            }
        }
        return values;
    }

    private List<Path> environmentManifests() throws IOException {
        assertThat(Files.isDirectory(ENVIRONMENTS))
                .as("каталог манифестов окружений не найден — тест мерил бы пустоту")
                .isTrue();
        List<Path> manifests = new ArrayList<>();
        try (Stream<Path> environments = Files.list(ENVIRONMENTS)) {
            environments.filter(Files::isDirectory)
                    .map(environment -> environment.resolve("env.yaml"))
                    .filter(Files::isRegularFile)
                    .forEach(manifests::add);
        }
        return manifests;
    }

    /** Разбор идёт тем же биндером, каким его делает Boot на старте. */
    private JournalRetentionProfile bind(String value) {
        ConfigurationPropertySource source =
                new MapConfigurationPropertySource(Map.of(AXIS_PROPERTY, value));
        return new Binder(source)
                .bind("platform.environment", EnvironmentProperties.class)
                .map(EnvironmentProperties::getJournalRetentionProfile)
                .orElse(null);
    }
}
