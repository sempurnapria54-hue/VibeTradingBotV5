package com.example.audit.unit.reception;

import com.example.audit.integration.internal.event.ConsumerLagProvider;
import com.example.testsupport.ConsumerLagProviderContract;
import java.util.Collection;
import java.util.Optional;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Копия добытчика остатка непринятого в дереве {@code audit}
 * (`.claude/tests/cases/durable-reception.md`, группа `U13`, клетки
 * `U15.3`, `U16.7`).
 */
class ConsumerLagTest extends ConsumerLagProviderContract {

    private final ConsumerLagProvider provider = new ConsumerLagProvider();

    @Override
    protected Optional<Long> unconsumedRecords(Collection<MessageListenerContainer> containers, String topic) {
        return provider.unconsumedRecords(containers, topic);
    }

    @Override
    protected Class<?> consumerLagProviderType() {
        return ConsumerLagProvider.class;
    }
}
