package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auditstatistics.domain.model.PairLagOperands;
import com.example.auditstatistics.metrics.JournalReceptionMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Стык между рядами экспорта и правилами алерта манифеста
 * (deploy/base/services/audit-statistics.yaml).
 *
 * <p><b>Класс дефекта, против которого проба заведена, механически не
 * ловится ничем другим.</b> Правило алерта называет ряд ИМЕНЕМ, а имя
 * печатает соглашение реестра; переименование поля, смена реестра,
 * пропавшая экспозиция, метка, снятая со службы, — каждое из них
 * оставляет манифест синтаксически верным, а ряда или цели для него не
 * существует. «Ряда нет» на стороне наблюдателя неотличимо от «алерт не
 * срабатывает», то есть отказ приходит МОЛЧАНИЕМ (docs/concept.md, П1).
 *
 * <p><b>Общего носителя у Java и YAML не бывает</b>, поэтому охрана здесь
 * — не чтение и не константа, а сверка ДВУХ ВЫДАЧ: того, что назвал
 * манифест, и того, что напечатал настоящий реестр.
 *
 * <p><b>Предмет пробы — имена и тропа, а не семантика правил.</b>
 * Семантику (границу порога, второй конъюнкт, три исхода «измеритель не
 * мерит») мерит прогон {@code promtool} по выражениям того же манифеста —
 * .claude/work/progress/phase-2-step-10-code-pass-k9.md.
 */
class AlertRuleContractTest {

    /** Путь от каталога модуля, из которого прогон и запускается. */
    private static final Path MANIFEST = Path.of("..", "..", "deploy", "base", "services", "audit-statistics.yaml");

    /** Имя ряда приёма: префикс общий, хвост у каждой величины свой. */
    private static final Pattern SERIES = Pattern.compile("audit_journal_reception_[a-z_]+");

    private static final String SCRAPE_PATH = "/actuator/prometheus";
    private static final String DOCUMENT_SEPARATOR = "\n---\n";
    private static final String SELECTOR_HEADER = "    matchLabels:\n";

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final JournalReceptionMetrics metrics = new JournalReceptionMetrics(registry);

    @Test
    @DisplayName("Каждое имя ряда, названное правилом алерта, реестр действительно печатает")
    void everySeriesNamedByTheRulesIsActuallyExported() throws IOException {
        Set<String> named = seriesNamedByManifest();
        assertThat(named)
                .as("проба без имён прошла бы зелёной, ничего не измерив")
                .isNotEmpty();

        metrics.replaceWith(List.of(new PairLagOperands(
                "trading-core.facts", OffsetDateTime.now(ZoneOffset.UTC), 302_400_000L, 17L)));
        String exposition = registry.scrape();

        assertThat(named)
                .as("имя печатает соглашение реестра, а правило берёт его на веру — расхождение молчит")
                .allSatisfy(series -> assertThat(exposition).contains(series));
    }

    @Test
    @DisplayName("Наблюдатель приходит на ту тропу, которую выпускает экспозиция actuator")
    void theScrapePathMatchesTheExposedEndpoint() throws IOException {
        assertThat(manifest())
                .as("тропа съёма объявлена ServiceMonitor'ом манифеста")
                .contains("path: " + SCRAPE_PATH);

        String configuration = new String(
                new ClassPathResource("application.yaml").getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(configuration)
                .as("без эндпоинта в экспозиции наблюдатель получает 404, ряды не собираются, "
                        + "и оба правила молчат — без единого отказа")
                .containsPattern("include:.*prometheus");
    }

    @Test
    @DisplayName("Наблюдатель выбирает службу теми метками, которые у службы действительно стоят")
    void theMonitorSelectorMatchesTheServiceLabels() throws IOException {
        String manifest = manifest();
        String metadata = metadataOf(document(manifest, "kind: Service"));
        List<String> selector = labelsUnder(document(manifest, "kind: ServiceMonitor"), SELECTOR_HEADER);

        assertThat(selector)
                .as("проба без меток отбора прошла бы зелёной, ничего не измерив")
                .isNotEmpty();
        assertThat(selector)
                .as("ServiceMonitor выбирает СЛУЖБУ, а не под: без метки в её метаданных целей у "
                        + "наблюдателя не будет ни одной, и он об этом не скажет")
                .allSatisfy(pair -> assertThat(metadata).contains(pair.trim()));
    }

    /** Имена рядов приёма, названные текстом манифеста. */
    private Set<String> seriesNamedByManifest() throws IOException {
        Set<String> named = new LinkedHashSet<>();
        Matcher matcher = SERIES.matcher(manifest());
        while (matcher.find()) {
            named.add(matcher.group());
        }
        return named;
    }

    /** Документ манифеста, объявленный названным видом. */
    private String document(String manifest, String kind) {
        for (String candidate : manifest.split(DOCUMENT_SEPARATOR)) {
            if (candidate.contains("\n" + kind + "\n")) {
                return candidate;
            }
        }
        throw new IllegalStateException("В манифесте нет документа " + kind);
    }

    /** Метаданные документа: от их заголовка до начала описания. */
    private String metadataOf(String document) {
        return document.substring(document.indexOf("\nmetadata:\n"), document.indexOf("\nspec:\n"));
    }

    /** Пары «ключ: значение» блока, начинающегося названной строкой. */
    private List<String> labelsUnder(String document, String header) {
        String rest = document.substring(document.indexOf(header) + header.length());
        List<String> pairs = new ArrayList<>();
        for (String line : rest.split("\n")) {
            if (line.startsWith("      ")) {
                pairs.add(line);
                continue;
            }
            break;
        }
        return pairs;
    }

    private String manifest() throws IOException {
        assertThat(Files.exists(MANIFEST))
                .as("манифест ищется от каталога модуля: %s", MANIFEST.toAbsolutePath())
                .isTrue();
        return Files.readString(MANIFEST, StandardCharsets.UTF_8);
    }
}
