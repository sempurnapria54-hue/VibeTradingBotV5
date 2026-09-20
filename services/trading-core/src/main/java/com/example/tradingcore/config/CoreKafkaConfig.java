package com.example.tradingcore.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Клиент брокера у ядра — объявлен целиком, а не настроен поверх
 * умолчаний.
 *
 * <p><b>Почему явно.</b> Автоконфигурации Kafka в дереве зависимостей нет:
 * Boot 4 разнёс её по модулям {@code spring-boot-<технология>}, а
 * {@code spring-kafka} сам по себе ни фабрики, ни обработки
 * {@code @KafkaListener} не поднимает. До этого объявления бина
 * {@code KafkaTemplate} у ядра не существовало вовсе — и контекст
 * сервиса не поднимался ни разу: {@code EventPublisher} не собирался.
 * Форма повторяет ту, которой ось закрыли два durable-потребителя
 * ({@code ReceptionKafkaConfig} у `audit` и `statistics`), а не заводит
 * восьмую.
 *
 * <p><b>Стороны две, и они разные.</b> Ядро и ПУБЛИКУЕТ (реле outbox в
 * тему {@code trading-core.facts}), и ПОТРЕБЛЯЕТ (три класса определения
 * из темы владельца определений) — docs/architecture/contracts.md
 * §События.
 *
 * <p><b>Подтверждение от ВСЕХ синхронных реплик, и это несущее.</b>
 * Отметка «опубликовано» ставится после подтверждения брокером, и более
 * слабое обещание позволило бы пометить строку, которую брокер потеряет
 * вместе с лидером (docs/components/OutboxRelayJob.md).
 *
 * <p><b>Позиция чтения — с начала темы.</b> Пропущенная активация
 * оставила бы ядро без копии определения, по которому оно торгует, а тема
 * фактов хранится сроком заведомо большим допустимого отставания
 * (docs/architecture/data-ownership.md §«Outbox и доставка»).
 *
 * <p><b>Слушатель не запускается при ненастроенной тропе.</b> Пустой адрес
 * брокера — объявленное состояние ({@link BrokerProperties}); контейнер,
 * запущенный на нём, падает на разборе адреса и роняет подъём приложения
 * целиком — то есть ненастроенная тропа СОБЫТИЙ отнимала бы у ядра и
 * торговлю, которая от неё не зависит.
 */
@EnableKafka
@Configuration
@EnableConfigurationProperties(BrokerProperties.class)
public class CoreKafkaConfig {

    @Bean
    public ProducerFactory<String, String> coreProducerFactory(BrokerProperties properties) {
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
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> coreProducerFactory) {
        return new KafkaTemplate<>(coreProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> coreConsumerFactory(BrokerProperties properties) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(settings);
    }

    /**
     * Фабрика контейнеров слушателя. Имя бина — то, которое ищет обработка
     * {@code @KafkaListener} по умолчанию: фабрика у сервиса одна, и
     * называть её у каждого слушателя значило бы заводить выбор там, где
     * выбирать не из чего.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> coreConsumerFactory, BrokerProperties properties) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(coreConsumerFactory);
        factory.setAutoStartup(properties.isConfigured());
        return factory;
    }
}
