package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Имя окружения не задано — клетка {@code B1.12} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Клетка разводит две половины поверхности по ОДНОМУ входу.</b> Имя
 * окружения — первый сегмент адреса ключей, и без него адрес указывал бы в
 * чужое окружение либо в корень хранилища; приватный вызов поэтому
 * отказывает ДО похода в хранилище. Публичное чтение ключей не требует
 * вовсе и проходит — то есть незаданная ось гасит ровно то, что от неё
 * зависит, и ничего сверх.
 */
class UnnamedEnvironmentBoxTest extends ConnectorBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        ConnectorSubstrate.register(registry, Map.of("platform.environment.name", ""));
    }

    @Test
    @DisplayName("B1.12 — незаданное имя окружения делает адрес ключей невычислимым")
    void b1_12_anUnnamedEnvironmentMakesTheKeyAddressIncomputable() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok(Okx.instrument(INSTRUMENT).text()));
        Integer readsBefore = secrets.reads();

        Answer refused = get(account("/positions"));
        Answer allowed = get(market("/instruments?externalInstrumentType=SWAP"));

        assertThat(refused.status()).isNotEqualTo(200);
        assertThat(secrets.reads() - readsBefore).isEqualTo(0);
        assertThat(exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH)).isEmpty();
        assertThat(allowed.status()).isEqualTo(200);
        assertThat(allowed.asList()).hasSize(1);
    }
}
