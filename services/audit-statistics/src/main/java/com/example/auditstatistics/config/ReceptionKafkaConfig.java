package com.example.auditstatistics.config;

import com.example.auditstatistics.integration.internal.event.JournalRebalanceListener;
import com.example.auditstatistics.integration.internal.event.JournalReceptionErrorHandler;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * Клиент брокера у группы журнала — объявлен целиком, а не настроен поверх
 * умолчаний.
 *
 * <p><b>Почему явно.</b> Автоконфигурации Kafka в дереве зависимостей нет:
 * Boot 4 разнёс автоконфигурацию по модулям {@code spring-boot-<технология>},
 * а {@code spring-kafka} сам по себе ни фабрики потребителя, ни обработки
 * {@code @KafkaListener} не поднимает. Свойства {@code spring.kafka.*} без
 * такого модуля не читает никто — они выглядели бы настройкой и ею не были
 * бы.
 *
 * <p><b>Две величины здесь несущие, а не вкусовые:</b>
 * <ul>
 *   <li><b>позиция чтения — с начала темы.</b> Группа журнала durable:
 *       пропущенное ею не восстанавливается ничем, и старт «с текущего
 *       момента» терял бы произведённое, пока потребителя не было
 *       (docs/models/domain/other/AuditRecord.md §«Позиция чтения и с
 *       какого момента журнал полон»);</li>
 *   <li><b>автоматическая фиксация смещений выключена.</b> На ней держится
 *       порядок «следствие записано → смещение зафиксировано»: таймер
 *       клиента продвинул бы смещение независимо от того, легла ли строка
 *       (docs/components/AuditEventListener.md §«Форма — слушатель
 *       контейнера, а не опрашивающий цикл»).</li>
 * </ul>
 */
@EnableKafka
@Configuration
@EnableConfigurationProperties(ReceptionProperties.class)
public class ReceptionKafkaConfig {

    @Bean
    public ConsumerFactory<String, String> journalConsumerFactory(ReceptionProperties properties) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        settings.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getGroupId());
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE);
        return new DefaultKafkaConsumerFactory<>(settings);
    }

    /**
     * Фабрика контейнеров слушателя. Имя бина — то, которое ищет обработка
     * {@code @KafkaListener} по умолчанию: фабрика у сервиса одна, и
     * называть её у каждого слушателя значило бы заводить выбор там, где
     * выбирать не из чего.
     *
     * <p><b>Обработчик ошибок и слушатель ребалансировки — часть
     * конструкции, а не настройка.</b> Первый держит остановку приёма на
     * отравленном сообщении, второй наблюдает разрыв смещений на назначении
     * партиций; без них умолчание даёт ветвь «смещение продвинулось, строки
     * нет» (docs/components/AuditEventListener.md).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> journalConsumerFactory,
            JournalReceptionErrorHandler errorHandler,
            JournalRebalanceListener rebalanceListener) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(journalConsumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setConsumerRebalanceListener(rebalanceListener);
        return factory;
    }

    /**
     * Административный клиент брокера — им тик состояния приёма добывает
     * срок хранения темы.
     *
     * <p><b>Он существует затем, чтобы носитель срока остался один.</b>
     * Срок лежит в манифесте темы чужого производителя, а порог алерта
     * считает потребитель; без тропы между ними число копировалось бы в
     * конфигурацию потребителя и расходилось при первой же калибровке —
     * причём в разрешающую сторону: срок, калиброванный вниз, оставляет
     * порог выше себя, и алерт молчит до самой потери
     * (docs/architecture/data-ownership.md §«Outbox и доставка»).
     *
     * <p><b>Клиент один и живёт со сроком контекста:</b> создавать его на
     * каждом такте значило бы поднимать соединение с брокером ради одного
     * вызова.
     */
    @Bean(destroyMethod = "close")
    public Admin journalKafkaAdmin(ReceptionProperties properties) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        return Admin.create(settings);
    }
}
