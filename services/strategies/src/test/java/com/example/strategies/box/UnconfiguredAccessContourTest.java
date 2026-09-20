package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategies.StrategiesApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Ненастроенный контур доступа — клетка {@code B9.1} документа
 * `.claude/tests/cases/strategies.md`.
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b>
 * ожидание клетки — что контекст НЕ поднимается, а поднятый контекст есть
 * предусловие всякого кейса ящика
 * (.claude/decisions/test-contour-design-pass.md, решение 1). Поэтому
 * подъём здесь — вход, и производит его сам кейс.
 *
 * <p><b>Оси подаются аргументами запуска, а не умолчаниями:</b>
 * умолчания стоя́т ниже {@code application.yaml} сервиса, и поданный
 * умолчанием адрес базы перекрылся бы пустым — контекст падал бы, но НЕ
 * по той причине, которую клетка предъявляет.
 */
class UnconfiguredAccessContourTest {

    @Test
    @DisplayName("B9.1 — Ненастроенный контур доступа не поднимает поверхности")
    void b9_1_anUnconfiguredAccessContourRaisesNoSurface() {
        Map<String, String> properties = new LinkedHashMap<>(StrategiesSubstrate.defaults());
        properties.put(StrategiesSubstrate.ISSUER_KEY, "");
        properties.put("server.port", "0");
        String[] arguments = properties.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);

        assertThatThrownBy(() -> new SpringApplicationBuilder(StrategiesApplication.class)
                .run(arguments)
                .close())
                .as("незаданное означает отказ, а не открытую поверхность")
                .isInstanceOf(Exception.class);
    }
}
