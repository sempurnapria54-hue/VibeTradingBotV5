package com.example.bff.config;

import java.util.HashMap;
import java.util.Map;
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
 * Клиент брокера у периметра — объявлен целиком, а не настроен поверх
 * умолчаний.
 *
 * <p><b>Почему явно.</b> Автоконфигурации Kafka в дереве зависимостей
 * нет: Boot 4 разнёс её по модулям {@code spring-boot-<технология>}, а
 * {@code spring-kafka} сам по себе ни фабрики потребителя, ни обработки
 * {@code @KafkaListener} не поднимает. До этого объявления контекст
 * периметра не поднимался ни разу — не собирался {@code StreamPulseJob},
 * которому нужен реестр контейнеров слушателя. Форма повторяет ту,
 * которой ось закрыли ядро ({@code CoreKafkaConfig}), владелец
 * определений ({@code StrategyKafkaConfig}) и два durable-потребителя
 * ({@code ReceptionKafkaConfig}), а не заводит пятую
 * (.claude/work/backlog.md §«Тропа к брокеру у построенных соседей…»).
 *
 * <p><b>Сторона одна — потребитель.</b> Периметр только принимает и не
 * публикует ничего, поэтому фабрики производителя и шаблона здесь нет:
 * они завели бы тропу, которой у сервиса не существует.
 *
 * <p><b>Три величины здесь несущие, и все три — ОБРАТНЫЕ величинам
 * durable-потребителя:</b>
 * <ul>
 *   <li><b>группа своя у каждой реплики.</b> Событие обязано дойти до
 *       ВСЕХ открытых сессий, а общая группа отдала бы его одной
 *       реплике (docs/architecture/contracts.md §«Живые данные в
 *       браузер»);</li>
 *   <li><b>позиция чтения — текущий момент.</b> Группа эфемерна и
 *       durable-потребителем периметр не является: {@code earliest}
 *       переиграл бы браузеру всю историю темы при каждом рестарте
 *       пода (docs/rules/durable-consumer-reception.md §«Кто
 *       durable-потребитель» — периметр в счёт не входит);</li>
 *   <li><b>автоматическая фиксация смещений оставлена включённой.</b>
 *       Порядка «следствие записано → смещение зафиксировано» у
 *       периметра нет вовсе: следствия, переживающего процесс, он не
 *       производит, а пропущенную запись не восстанавливает никто —
 *       дедуплицирует и догоняет КЛИЕНТ, и это часть внешнего
 *       контракта.</li>
 * </ul>
 */
@EnableKafka
@Configuration
@EnableConfigurationProperties(BrokerProperties.class)
public class PerimeterKafkaConfig {

    @Bean
    public ConsumerFactory<String, String> streamConsumerFactory(BrokerProperties properties) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        settings.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getGroupId());
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.TRUE);
        return new DefaultKafkaConsumerFactory<>(settings);
    }

    /**
     * Фабрика контейнеров слушателя. Имя бина — то, которое ищет
     * обработка {@code @KafkaListener} по умолчанию: фабрика у сервиса
     * одна, и называть её у каждого слушателя значило бы заводить выбор
     * там, где выбирать не из чего.
     *
     * <p><b>Обработчика отказа и слушателя ребалансировки здесь нет, и
     * это следствие предмета.</b> Остановка приёма на отравленном
     * сообщении есть свойство durable-потребителя, у которого пропуск
     * невосполним; у периметра пропуск стоит одной записи в потоке, а
     * остановленная раздача — всей картины
     * (docs/architecture/contracts.md §«Цена пропуска названа»).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> streamConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(streamConsumerFactory);
        return factory;
    }
}
