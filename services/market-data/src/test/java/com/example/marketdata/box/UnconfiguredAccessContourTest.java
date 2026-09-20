package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketdata.MarketDataApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Ненастроенный контур доступа — клетка {@code B9.1} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b>
 * ожидание клетки — что контекст НЕ поднимается, а поднятый контекст есть
 * предусловие всякого кейса ящика (решение 1). Поэтому подъём здесь —
 * вход, и производит его сам кейс.
 *
 * <p><b>Оси подаются аргументами запуска, а не умолчаниями:</b>
 * умолчания стоя́т ниже {@code application.yaml} сервиса, и поданный
 * умолчанием адрес базы перекрылся бы пустым — контекст падал бы, но НЕ
 * по той причине, которую клетка предъявляет.
 */
class UnconfiguredAccessContourTest {

    @Test
    @DisplayName("B9.1 — ненастроенный контур доступа не поднимает поверхности")
    void b9_1_anUnconfiguredAccessContourRaisesNoSurface() {
        Map<String, String> properties = new LinkedHashMap<>(MarketDataSubstrate.defaults());
        properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", "");
        properties.put("server.port", "0");
        String[] arguments = properties.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);

        assertThatThrownBy(() -> new SpringApplicationBuilder(MarketDataApplication.class)
                .run(arguments)
                .close())
                .isInstanceOf(Exception.class);
    }
}
