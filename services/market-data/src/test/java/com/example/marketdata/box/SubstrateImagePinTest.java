package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проба совпадения образа базы с манифестом стенда.
 *
 * <p><b>Она не клетка документа, а обязанность решения:</b> образ
 * пинится тем же тегом, что у стенда, и совпадение сверяет проба —
 * иначе две записи тега разошлись бы молча, а стоковый {@code postgres}
 * не поднял бы гипертаблиц вовсе
 * (.claude/decisions/test-contour-design-pass.md, решение 2).
 *
 * <p><b>Сверяется с артефактом ВНЕ прогона</b> — манифестом
 * {@code deploy/base}, — то есть проба принадлежит классу `корпус`
 * ревизии набора: ящик манифестов не поднимает, и её предмет остаётся
 * сверкой двух записей, а не поведением сервиса.
 */
class SubstrateImagePinTest {

    private static final Path MANIFEST =
            Path.of("..", "..", "deploy", "base", "data", "postgres-cluster.yaml");

    @Test
    @DisplayName("Образ базы субстрата совпадает с образом манифеста стенда")
    void theSubstrateImageMatchesTheStandManifest() throws IOException {
        String manifest = Files.readString(MANIFEST, StandardCharsets.UTF_8);

        assertThat(manifest).contains(MarketDataSubstrate.DATABASE_IMAGE);
    }
}
