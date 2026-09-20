package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Схема как вход — клетка {@code B6.3} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Своей пустоты клетка не требует:</b> предмет — применённость
 * миграций и сверка отображения, а она наблюдаема при любом подъёме.
 * Сам факт того, что контекст поднялся, и есть половина ожидания:
 * {@code ddl-auto: validate} роняет подъём на колонке, объявленной
 * моделью и не заведённой миграцией, — то есть расхождение
 * обнаруживается здесь, а не в проде.
 */
class SchemaInputBoxTest extends SharedAuthBox {

    @Test
    @DisplayName("B6.3 — схема накатывается миграциями и сверяется с отображением")
    void b6_3_theSchemaIsMigratedAndValidatedAgainstTheMapping() {
        assertThat(rows.appliedMigrations()).contains("1", "2");
        assertThat(rows.tableNames())
                .contains("tenants", "memberships", "exchange_accounts", "flyway_schema_history");

        Answer answer = get("/actuator/health");

        assertThat(answer.status()).isEqualTo(200);
    }
}
