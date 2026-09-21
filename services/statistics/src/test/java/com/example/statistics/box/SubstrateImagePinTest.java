package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.kafka.common.utils.AppInfoParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проба совпадения образов субстрата с их вторыми записями.
 *
 * <p><b>Она не клетка документа, а обязанность решения:</b> образ пинится тем
 * же тегом, что у стенда, и совпадение сверяет проба — иначе две записи тега
 * разошлись бы молча
 * (.claude/decisions/test-contour-design-pass.md, решение 2).
 *
 * <p><b>У ЭТОГО предмета образ базы несущий вдвойне:</b> обе таблицы фактов —
 * гипертаблицы Timescale, и на стоковом образе миграция {@code V1} не
 * накатывается вовсе (docs/models/domain/other/StatisticsFact.md
 * §Персистентность). Расхождение тега здесь роняет не клетку, а подъём
 * первого же контекста.
 *
 * <p><b>Сверяется с артефактом ВНЕ прогона</b> — манифестом
 * {@code deploy/base}, — то есть проба принадлежит классу `корпус`: ящик
 * манифестов не поднимает, и её предмет остаётся сверкой двух записей, а не
 * поведением сервиса.
 *
 * <p><b>У образа брокера вторая запись ДРУГАЯ, и это названо, а не
 * умолчано.</b> Манифест стенда версии брокера не называет вовсе — её выбирает
 * оператор Strimzi, — поэтому пин сверяется с версией клиента
 * {@code kafka-clients} в дереве зависимостей: она единственная запись, с
 * которой расхождение образа наблюдаемо.
 */
class SubstrateImagePinTest {

    private static final Path MANIFEST =
            Path.of("..", "..", "deploy", "base", "data", "postgres-cluster.yaml");

    @Test
    @DisplayName("Образ базы субстрата совпадает с образом манифеста стенда")
    void theSubstrateImageMatchesTheStandManifest() throws IOException {
        String manifest = Files.readString(MANIFEST, StandardCharsets.UTF_8);

        assertThat(manifest).contains(StatisticsSubstrate.DATABASE_IMAGE);
    }

    @Test
    @DisplayName("Образ брокера субстрата несёт версию клиента дерева зависимостей")
    void theBrokerImageCarriesTheVersionOfTheClientInTheTree() {
        String clientVersion = AppInfoParser.getVersion();

        // Базовый гейт: версия, которой клиент о себе не сообщил, сделала бы
        // сравнение верным на пустом месте.
        assertThat(clientVersion).isNotBlank();
        assertThat(StatisticsSubstrate.BROKER_IMAGE).isEqualTo("apache/kafka:" + clientVersion);
    }
}
