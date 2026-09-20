package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.ConnectorOkxApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Ненастроенный контур доступа — клетка {@code B9.1} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b>
 * ожидание клетки — что контекст НЕ поднимается, а поднятый контекст есть
 * предусловие всякого кейса ящика (решение 1). Поэтому подъём здесь —
 * вход, и производит его сам кейс.
 *
 * <p><b>Что проверяется:</b> пустая ось контура доступа означает отказ, а
 * не открытую поверхность (`docs/concept.md` П1; комментарий оси в
 * {@code application.yaml}). Поверхность при этом не отвечает ни на одной
 * точке — ни закрытой, ни открытой: процесса нет вовсе.
 */
class UnconfiguredAccessContourTest {

    @Test
    @DisplayName("B9.1 — ненастроенный контур доступа не поднимает поверхности")
    void b9_1_anUnconfiguredAccessContourRaisesNoSurface() {
        Map<String, Object> properties = new LinkedHashMap<>(ConnectorSubstrate.defaults());
        properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", "");
        properties.put("server.port", "0");

        assertThatThrownBy(() -> new SpringApplicationBuilder(ConnectorOkxApplication.class)
                .properties(properties)
                .run()
                .close())
                .isInstanceOf(Exception.class);
    }
}
