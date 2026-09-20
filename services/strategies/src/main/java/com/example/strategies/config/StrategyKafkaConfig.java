package com.example.strategies.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Клиент брокера у владельца определений — объявлен целиком, а не
 * настроен поверх умолчаний.
 *
 * <p><b>Почему явно.</b> Автоконфигурации Kafka в дереве зависимостей нет:
 * Boot 4 разнёс её по модулям {@code spring-boot-<технология>}, а
 * {@code spring-kafka} сам по себе ни фабрики, ни обработки
 * {@code @KafkaListener} не поднимает. До этого объявления бина
 * {@code KafkaTemplate} у сервиса не существовало вовсе — и контекст не
 * поднимался ни разу: {@code EventPublisher} не собирался. Форма
 * повторяет ту, которой ось закрыли ядро ({@code CoreKafkaConfig}) и два
 * durable-потребителя ({@code ReceptionKafkaConfig} у `audit` и
 * `statistics`), а не заводит четвёртую.
 *
 * <p><b>Сторона одна, и это предмет сервиса.</b> Владелец определений
 * только ПУБЛИКУЕТ — три класса перехода в тему {@code strategies.facts};
 * потребителем он не является ни одной тропой
 * (docs/architecture/services/strategies.md §«Что потребляет и что
 * публикует»). Отсюда ни {@code @EnableKafka}, ни фабрики потребителя
 * здесь нет: они завели бы тропу, которой у сервиса не существует.
 *
 * <p><b>Подтверждение от ВСЕХ синхронных реплик, и это несущее.</b>
 * Отметка «опубликовано» ставится после подтверждения брокером, и более
 * слабое обещание позволило бы пометить строку, которую брокер потеряет
 * вместе с лидером (docs/components/OutboxRelayJob.md).
 */
@Configuration
@EnableConfigurationProperties(BrokerProperties.class)
public class StrategyKafkaConfig {

    @Bean
    public ProducerFactory<String, String> strategyProducerFactory(BrokerProperties properties) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(settings);
    }

    /**
     * Публикующий шаблон. Соединения он при сборке не открывает: пустой
     * адрес брокера доживает до первой отправки и отказывает ей — ровно
     * то, что объявлено реле.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> strategyProducerFactory) {
        return new KafkaTemplate<>(strategyProducerFactory);
    }
}
