package com.example.bff.unit.stream;

import com.example.bff.integration.internal.event.ConsumerLivenessProvider;
import com.example.testsupport.ConsumerLivenessProviderContract;
import java.util.Collection;
import java.util.Map;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Копия провайдера живости потребления в дереве {@code bff}
 * (`.claude/tests/cases/durable-reception.md`, группа `U17`).
 */
class ConsumerLivenessTest extends ConsumerLivenessProviderContract {

    @Override
    protected Boolean isLive(Map<String, Object> consumerSettings, Collection<MessageListenerContainer> containers) {
        return new ConsumerLivenessProvider(new DefaultKafkaConsumerFactory<>(consumerSettings)).isLive(containers);
    }

    @Override
    protected Class<?> consumerLivenessProviderType() {
        return ConsumerLivenessProvider.class;
    }
}
