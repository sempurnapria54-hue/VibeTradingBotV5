package com.example.bff.config;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Тропа потребления периметра настроена: адрес брокера задан и тема
 * подписки хотя бы одна (docs/architecture/services/bff.md §«Что
 * потребляет и что публикует»).
 *
 * <p><b>Ненастроенная тропа — отсутствие слушателя, а не отказ подъёма.</b>
 * Поверхность периметра проксирует владельцев и без потока; поток же без
 * слушателя молчит, и пульс молчит вместе с ним — живости потребителя нет,
 * и молчание честно. Заведённый на пустой оси, слушатель отказывал бы
 * подъёму контекста целиком, и отказ называл бы клиент брокера, а не
 * незаданную ось.
 *
 * <p><b>Условие читает те же предикаты, что и свойства</b>, связывая их
 * до появления бинов: второй записи признака «настроено» не заводится.
 */
public class StreamConsumptionCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Binder binder = Binder.get(context.getEnvironment());
        BrokerProperties broker = binder.bindOrCreate(BrokerProperties.PREFIX, BrokerProperties.class);
        PerimeterProperties perimeter = binder.bindOrCreate(PerimeterProperties.PREFIX, PerimeterProperties.class);
        return isTrue(broker.isConfigured()) && isNotEmpty(perimeter.getStream().topicNames());
    }
}
