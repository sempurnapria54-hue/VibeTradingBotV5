package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.exchange.ExchangeFailureClass;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Адрес площадки не задан — клетка {@code B9.2} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Ось ведёт себя иначе, чем контур доступа, и различие объявлено
 * комментарием самой оси:</b> ненастроенный контур гасит ПОДЪЁМ, а
 * незаданный адрес площадки подъёму не мешает — отказ наступает на
 * вызове. Клетка мерит именно это: контекст поднялся, а вызов отказал, и
 * ни один запрос не ушёл на случайный адрес.
 *
 * <p><b>Ожидание взято из дома, а не из текущего поведения</b>, и прогон
 * застал его исполненным: отказ приезжает классом перечня в едином
 * error-DTO. Клетка зелена, и метки долга на ней поэтому нет — помеченная
 * зелёная клетка выпала бы из умолчания прогона, ничего не охраняя.
 */
class UnconfiguredExchangeAddressBoxTest extends ConnectorBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        ConnectorSubstrate.register(registry, Map.of("okx.base-url", ""));
    }

    @Test
    @DisplayName("B9.2 — незаданный адрес площадки отказывает на вызове, а не на подъёме")
    void b9_2_anUnsetExchangeAddressRefusesOnTheCallNotOnStartup() {
        Answer publicRead = get(market("/instruments?externalInstrumentType=SWAP"));
        Answer privateRead = get(account("/positions"));

        assertThat(exchange.count()).isEqualTo(0);
        assertThat(publicRead.carriesErrorDto()).isTrue();
        assertThat(privateRead.carriesErrorDto()).isTrue();
        assertThat(publicRead.errorCode()).isIn(boundaryClasses());
        assertThat(privateRead.errorCode()).isIn(boundaryClasses());
    }

    /** Объявленный перечень классов отказа границы. */
    private static Object[] boundaryClasses() {
        return Arrays.stream(ExchangeFailureClass.values())
                .map(Enum::name)
                .collect(Collectors.toList())
                .toArray();
    }
}
