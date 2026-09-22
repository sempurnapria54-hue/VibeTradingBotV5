package com.example.bff.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Адрес брокера и имя группы: тропа потребления периметра.
 *
 * <p><b>Ключ свой, а не {@code spring.kafka.*}, и это не вкус.</b>
 * Автоконфигурации Kafka в дереве зависимостей нет — Boot 4 разнёс её по
 * модулям {@code spring-boot-<технология>}, а {@code spring-kafka} сам по
 * себе ни фабрик, ни обработки {@code @KafkaListener} не поднимает.
 * Свойства {@code spring.kafka.*} без такого модуля не читает НИКТО: они
 * выглядели бы настройкой и ею не были бы. Форма та же, что у ядра
 * ({@code BrokerProperties} у `trading-core`), у владельца определений и
 * у двух durable-потребителей ({@code ReceptionProperties} у `audit` и
 * `statistics`), — пятой формы не заводится.
 *
 * <p><b>Сторона здесь ОДНА — потребитель.</b> Периметр не публикует
 * ничего (docs/architecture/services/bff.md §«Что потребляет и что
 * публикует»), и фабрика производителя завела бы тропу, которой у него
 * не существует.
 *
 * <p><b>Группа своя у КАЖДОЙ реплики, и это несущее.</b> Событие обязано
 * дойти до всех открытых сессий, а общая группа отдала бы его одной
 * реплике (docs/architecture/contracts.md §«Живые данные в браузер»).
 * Отсюда случайный суффикс умолчания: группа живёт ровно столько, сколько
 * под, и брокер убирает её по своему сроку хранения смещений.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    /**
     * Адрес брокера. Пустое означает, что тропа потребления не
     * настроена: слушатель не соединяется, пульс молчит — и молчание
     * тогда честно (docs/components/… см. {@code StreamPulseJob}).
     */
    private String bootstrapServers;

    /** Имя группы потребителя — своё у каждой реплики (см. шапку класса). */
    private String groupId;
}
