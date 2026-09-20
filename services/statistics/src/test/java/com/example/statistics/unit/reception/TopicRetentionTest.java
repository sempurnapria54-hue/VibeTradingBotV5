package com.example.statistics.unit.reception;

import com.example.statistics.integration.internal.event.TopicRetentionProvider;
import com.example.testsupport.TopicRetentionProviderContract;
import java.util.Optional;
import org.apache.kafka.clients.admin.Admin;

/**
 * Копия добытчика срока хранения темы в дереве {@code statistics}
 * (`.claude/tests/cases/durable-reception.md`, группа `U12`, клетки
 * `U15.2`, `U16.6`).
 */
class TopicRetentionTest extends TopicRetentionProviderContract {

    @Override
    protected Optional<Long> retentionMs(Admin admin, String topic) {
        return new TopicRetentionProvider(admin).retentionMs(topic);
    }

    @Override
    protected Class<?> topicRetentionProviderType() {
        return TopicRetentionProvider.class;
    }
}
