package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Адрес брокера и имена его тем: тропа событий ядра.
 *
 * <p><b>Ключ свой, а не {@code spring.kafka.*}, и это не вкус.</b>
 * Автоконфигурации Kafka в дереве зависимостей нет — Boot 4 разнёс её по
 * модулям {@code spring-boot-<технология>}, а {@code spring-kafka} сам по
 * себе ни фабрик, ни обработки {@code @KafkaListener} не поднимает.
 * Свойства {@code spring.kafka.*} без такого модуля не читает НИКТО: они
 * выглядели бы настройкой и ею не были бы. Форма та же, что у двух
 * durable-потребителей ({@code ReceptionProperties} у `audit` и
 * `statistics`).
 *
 * <p><b>Пустой адрес — объявленное состояние, а не недосмотр.</b> Он
 * означает, что тропа публикации не настроена: реле отказывает, строки
 * outbox копятся, а решения от этого не зависят
 * (docs/components/OutboxRelayJob.md). Слушатель темы определений при
 * пустом адресе не запускается вовсе — иначе контейнер падал бы на
 * разборе адреса и ронял подъём приложения целиком, то есть ненастроенная
 * тропа событий отнимала бы у ядра и торговлю.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    /**
     * Адрес брокера. Пустое означает, что тропа событий не настроена —
     * см. шапку класса.
     */
    private String bootstrapServers;

    /** Настроена ли тропа событий: им решается запуск слушателя. */
    public Boolean isConfigured() {
        return StringUtils.isNotBlank(bootstrapServers);
    }
}
