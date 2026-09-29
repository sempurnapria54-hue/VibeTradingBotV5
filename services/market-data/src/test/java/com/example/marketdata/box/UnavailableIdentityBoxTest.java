package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Недоступная служебная идентичность — клетка {@code B8.13} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контекст здесь нужен ДВАЖДЫ.</b> Во-первых, отказ в выдаче —
 * ось конфигурации (точка выдачи токена). Во-вторых, добытый токен живёт
 * в реестре авторизованных клиентов контекста, и в контексте, где он уже
 * добыт, отказ выдачи не наблюдался бы вовсе: клиент взял бы токен из
 * своего реестра.
 *
 * <p><b>Пустой токен — отказ, а не анонимный вызов.</b> Коннектор закрыт
 * по умолчанию, и уйти к нему без токена значило бы получить отказ на его
 * стороне с причиной, неотличимой от «ключи отвергнуты».
 */
class UnavailableIdentityBoxTest extends MarketDataBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("spring.security.oauth2.client.provider.platform.token-uri",
                IdentityStub.stub().refusingTokenUri());
        MarketDataSubstrate.register(registry, axes);
    }

    /**
     * Пустой токен есть ОТКАЗ, а не анонимный вызов, и отказ этот — класса
     * доступа: недобытая идентичность одинакова для всех инструментов, и
     * вызывающему её повтор имеет смысл не сразу ({@code ServiceTokenProvider}
     * javadoc; docs/architecture/services/market-data.md §«Какие вызовы
     * делает и какие принимает»). К соседу без заголовка идентичности не
     * уходит ни один запрос.
     */
    @Test
    @DisplayName("B8.13 — недоступная служебная идентичность — отказ, а не анонимный вызов")
    void b8_13_anUnavailableServiceIdentityIsAFailureNotAnAnonymousCall() {
        // Каталог ставится прямой записью: тропа его заведения сама ходит
        // к соседу, а в этом контексте к нему не уйти ни одним вызовом.
        rows.put("insert into instruments (internal_id, exchange_code, external_id, external_type, status) "
                + "values ('MD-B8-13', ?, ?, 'SWAP', 'ACTIVE')",
                MarketDataSubstrate.EXCHANGE_CODE, INSTRUMENT);
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), Feed.prices(INSTRUMENT, "50500"));

        Answer answer = get(INSTRUMENTS + "/MD-B8-13/prices");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_ACCESS_REFUSED");
        assertThat(connector.count(ConnectorStub.pricesOf(INSTRUMENT))).isEqualTo(0);
    }
}
