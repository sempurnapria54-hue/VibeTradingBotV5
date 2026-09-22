package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.common.utils.AppInfoParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проба совпадения образа субстрата со второй его записью.
 *
 * <p><b>Она не клетка документа, а обязанность решения:</b> образ пинится
 * записью, с которой расхождение наблюдаемо, и совпадение сверяет проба
 * — иначе две записи разошлись бы молча
 * (.claude/decisions/test-contour-design-pass.md, решение 2).
 *
 * <p><b>У ЭТОГО ящика сверяемая запись ОДНА, и это следствие субстрата.</b>
 * Контейнер базы здесь не поднимается вовсе — своей базы у периметра
 * нет, — поэтому сверять с манифестом {@code deploy/base} нечего. У
 * образа брокера вторая запись — версия клиента {@code kafka-clients} в
 * дереве зависимостей: манифест стенда версии брокера не называет вовсе,
 * её выбирает оператор Strimzi.
 */
class SubstrateImagePinTest {

    @Test
    @DisplayName("Образ брокера субстрата несёт версию клиента дерева зависимостей")
    void theBrokerImageCarriesTheVersionOfTheClientInTheTree() {
        String clientVersion = AppInfoParser.getVersion();

        // Базовый гейт: версия, которой клиент о себе не сообщил, сделала
        // бы сравнение верным на пустом месте.
        assertThat(clientVersion).isNotBlank();
        assertThat(BffSubstrate.BROKER_IMAGE).isEqualTo("apache/kafka:" + clientVersion);
    }
}
