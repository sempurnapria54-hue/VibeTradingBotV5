package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketdata.MarketDataApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Стоковый образ базы — клетка {@code B9.7} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контейнер здесь — предмет кейса, а не оснастка:</b>
 * состояние субстрата целиком и есть то, о чём клетка утверждает
 * (.claude/decisions/test-contour-design-pass.md, §«Оснований брать свой
 * контейнер ДВА»). Образ без расширения временных рядов не поднимает
 * гипертаблиц вовсе, и миграция {@code V1} обязана упасть ГРОМКО:
 * молчаливое падение на обычные таблицы было бы ошибкой в разрешающую
 * сторону — сервис поднялся бы, а объявленной формы хранения не было бы.
 *
 * <p><b>Тест на неподходящем субстрате не скипается</b> (там же, решение
 * 9): пропуск читался бы как «дефектов нет».
 */
class StockDatabaseImageBoxTest {

    /** Стоковый образ: расширения временных рядов в нём нет. */
    private static final String STOCK_IMAGE = "postgres:17-alpine";

    @Test
    @DisplayName("B9.7 — стоковый образ базы прогон не проходит")
    void b9_7_theRunDoesNotPassOnAStockDatabaseImage() {
        try (PostgreSQLContainer stock = MarketDataSubstrate.startOn(STOCK_IMAGE)) {
            Map<String, String> properties = new LinkedHashMap<>(MarketDataSubstrate.defaults());
            properties.putAll(MarketDataSubstrate.databaseAddress(stock));
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
}
