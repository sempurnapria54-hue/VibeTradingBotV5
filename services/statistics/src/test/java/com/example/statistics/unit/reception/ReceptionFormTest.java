package com.example.statistics.unit.reception;

import com.example.statistics.util.Constants;
import com.example.testsupport.ReceptionFormContract;
import java.nio.file.Path;
import java.util.List;

/**
 * Форма приёма в дереве {@code statistics}: перечень имён конверта и то, чего
 * предмет не делает (`.claude/tests/cases/durable-reception.md`, клетка
 * `U15.8`, группа `U16` в части, наблюдаемой исходником).
 *
 * <p>Пути даны от каталога модуля, из которого прогон и запускается, —
 * та же форма, что у прочих проб, читающих свой исходник.
 */
class ReceptionFormTest extends ReceptionFormContract {

    private static final Path DOMAIN_MODEL = Path.of("src", "main", "java", "com", "example", "statistics",
            "domain", "model");
    private static final Path DOMAIN_SERVICE = Path.of("src", "main", "java", "com", "example", "statistics",
            "domain", "service");
    private static final Path EVENT = Path.of("src", "main", "java", "com", "example", "statistics",
            "integration", "internal", "event");
    private static final Path METRICS = Path.of("src", "main", "java", "com", "example", "statistics", "metrics");

    @Override
    protected List<Path> subjectSources() {
        return List.of(
                DOMAIN_SERVICE.resolve("ReceptionCompletenessService.java"),
                DOMAIN_MODEL.resolve("ReceptionPairMoments.java"),
                EVENT.resolve("EnvelopeReader.java"),
                EVENT.resolve("ReceptionRebalanceListener.java"),
                EVENT.resolve("ReceptionOffsetTracker.java"),
                EVENT.resolve("ReceptionHaltMarker.java"),
                EVENT.resolve("TopicRetentionProvider.java"),
                EVENT.resolve("ConsumerLagProvider.java"),
                METRICS.resolve("ReceptionMetrics.java"));
    }

    @Override
    protected Path envelopeReaderSource() {
        return EVENT.resolve("EnvelopeReader.java");
    }

    @Override
    protected List<Path> clockReadingSources() {
        return List.of(
                EVENT.resolve("ReceptionRebalanceListener.java"),
                METRICS.resolve("ReceptionMetrics.java"));
    }

    @Override
    protected List<String> eventHeaderNames() {
        return Constants.EventHeaders.ALL;
    }
}
