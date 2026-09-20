package com.example.strategies.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Адрес брокера: тропа публикации владельца определений.
 *
 * <p><b>Ключ свой, а не {@code spring.kafka.*}, и это не вкус.</b>
 * Автоконфигурации Kafka в дереве зависимостей нет — Boot 4 разнёс её по
 * модулям {@code spring-boot-<технология>}, а {@code spring-kafka} сам по
 * себе ни фабрик, ни обработки {@code @KafkaListener} не поднимает.
 * Свойства {@code spring.kafka.*} без такого модуля не читает НИКТО: они
 * выглядели бы настройкой и ею не были бы. Форма та же, что у ядра
 * ({@code BrokerProperties} у `trading-core`) и у двух
 * durable-потребителей ({@code ReceptionProperties} у `audit` и
 * `statistics`).
 *
 * <p><b>Пустой адрес — объявленное состояние, а не недосмотр.</b> Он
 * означает, что тропа публикации не настроена: реле отказывает, строки
 * outbox копятся, а переходы статуса от этого не зависят
 * (docs/components/OutboxRelayJob.md). Решение уже записано своей
 * транзакцией, и публикация его не производит, а доставляет.
 *
 * <p><b>Потребительской стороны здесь нет, и это предмет, а не
 * экономия:</b> владелец определений не потребляет ничего — он существует
 * ради того, что публикует
 * (docs/architecture/services/strategies.md §«Что потребляет и что
 * публикует»). Фабрика потребителя завела бы тропу, которой у сервиса нет.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    /**
     * Адрес брокера. Пустое означает, что тропа публикации не настроена —
     * см. шапку класса.
     */
    private String bootstrapServers;
}
