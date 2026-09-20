package com.example.audit.unit.reception;

import com.example.audit.util.Constants;
import com.example.testsupport.ReceptionFormContract;
import java.nio.file.Path;
import java.util.List;

/**
 * Форма приёма в дереве {@code audit}: перечень имён конверта и то, чего
 * предмет не делает (`.claude/tests/cases/durable-reception.md`, клетка
 * `U15.8`, группа `U16` в части, наблюдаемой исходником).
 *
 * <p>Пути даны от каталога модуля, из которого прогон и запускается, —
 * та же форма, что у прочих проб, читающих свой исходник.
 */
class ReceptionFormTest extends ReceptionFormContract {

    private static final Path DOMAIN_MODEL = Path.of("src", "main", "java", "com", "example", "audit",
            "domain", "model");
    private static final Path DOMAIN_SERVICE = Path.of("src", "main", "java", "com", "example", "audit",
            "domain", "service");
    private static final Path EVENT = Path.of("src", "main", "java", "com", "example", "audit",
            "integration", "internal", "event");
    private static final Path METRICS = Path.of("src", "main", "java", "com", "example", "audit", "metrics");

    @Override
    protected List<Path> subjectSources() {
        return List.of(
                DOMAIN_SERVICE.resolve("JournalCompletenessService.java"),
                DOMAIN_MODEL.resolve("ReceptionPairMoments.java"),
                EVENT.resolve("JournalEnvelopeReader.java"),
                EVENT.resolve("JournalRebalanceListener.java"),
                EVENT.resolve("ReceptionOffsetTracker.java"),
                EVENT.resolve("ReceptionHaltMarker.java"),
                EVENT.resolve("TopicRetentionProvider.java"),
                EVENT.resolve("ConsumerLagProvider.java"),
                METRICS.resolve("JournalReceptionMetrics.java"));
    }

    @Override
    protected Path envelopeReaderSource() {
        return EVENT.resolve("JournalEnvelopeReader.java");
    }

    @Override
    protected List<Path> clockReadingSources() {
        return List.of(
                EVENT.resolve("JournalRebalanceListener.java"),
                METRICS.resolve("JournalReceptionMetrics.java"));
    }

    @Override
    protected List<String> eventHeaderNames() {
        return Constants.EventHeaders.ALL;
    }
}
