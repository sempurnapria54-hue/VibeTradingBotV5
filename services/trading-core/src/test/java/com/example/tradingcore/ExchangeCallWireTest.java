package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.config.NeighbourProperties;
import com.example.tradingcore.integration.ServiceTokenProvider;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Провод исходящего шлюза: адрес, операнды и идентичность вызова.
 *
 * <p><b>Проверяется то, чего не видит компилятор.</b> Ядро и коннектор —
 * разные модули, и путь с именами параметров держится не типом, а
 * совпадением двух строк по разные стороны границы
 * (docs/components/IntegrationService.md §«Входной контракт»). Разойдись
 * они — отказ придёт {@code 404}, то есть классом «наш дефект», и увидит
 * его первым живой прогон, а не сборка.
 *
 * <p><b>Названное ограничение теста:</b> он пиннит НАШУ сторону провода.
 * Встречную сторону мерит поверхность коннектора своим тестом; общей
 * механической сверки двух сторон нет, пока нет общего носителя контракта
 * — и это причина, по которой пути здесь выписаны дословно, а не собраны
 * из констант.
 */
class ExchangeCallWireTest {

    private static final String BASE = "http://connector-okx:8080";
    private static final String ACCOUNT = "ea-0001";
    private static final String INSTRUMENT = "BTC-USDT-SWAP";
    private static final String TOKEN = "service-token";

    private final ServiceTokenProvider tokenProvider = mock(ServiceTokenProvider.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ExchangeOperationsClient client =
            new ExchangeOperationsClient(builder, tokenProvider, properties());

    /**
     * Команда идёт по адресу счёта, несёт инструмент операндом и модель
     * телом; счёт стои́т в ПУТИ — по нему коннектор берёт ключи, и в теле
     * его не увидели бы ни сетевая политика, ни лог доступа.
     */
    @Test
    void placeOrderCarriesAccountInPathAndOrderInBody() {
        when(tokenProvider.getTokenValue("platform-services")).thenReturn(TOKEN);
        server.expect(requestTo(BASE + "/api/v1/accounts/" + ACCOUNT
                        + "/orders?externalInstrumentId=" + INSTRUMENT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json("{\"internalId\":\"ord-1\"}"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        Order order = new Order();
        order.setInternalId("ord-1");

        assertThat(client.placeOrder(ACCOUNT, order, INSTRUMENT).getSuccess()).isTrue();
        server.verify();
    }

    /**
     * Границы окна уезжают в UTC, даже когда вызывающий дал их со
     * смещением: смещение {@code +03:00} несёт {@code +}, и приёмная
     * сторона законно прочитала бы его пробелом — окно молча уехало бы на
     * три часа.
     */
    @Test
    void windowBoundsGoOutInUtc() {
        when(tokenProvider.getTokenValue("platform-services")).thenReturn(TOKEN);
        server.expect(requestTo(BASE + "/api/v1/accounts/" + ACCOUNT
                        + "/bills?begin=2026-09-05T09:00Z&end=2026-09-05T10:00Z"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.getBills(ACCOUNT,
                OffsetDateTime.of(2026, 9, 5, 12, 0, 0, 0, ZoneOffset.ofHours(3)),
                OffsetDateTime.of(2026, 9, 5, 10, 0, 0, 0, ZoneOffset.UTC));

        server.verify();
    }

    /**
     * Род условия — операнд запроса: у площадки условные заявки разных
     * родов лежат в разных перечнях, и запрос без рода вернул бы не «все»,
     * а один произвольный.
     */
    @Test
    void pendingAlgoOrdersCarryConditionType() {
        when(tokenProvider.getTokenValue("platform-services")).thenReturn(TOKEN);
        server.expect(requestTo(BASE + "/api/v1/accounts/" + ACCOUNT
                        + "/algo-orders/pending/instrument?externalInstrumentId=" + INSTRUMENT
                        + "&conditionType=STOP_LOSS"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.getPendingAlgoOrders(ACCOUNT, INSTRUMENT, AlgoOrder.ConditionType.STOP_LOSS);

        server.verify();
    }

    /**
     * Время площадки — публичная операция: счёта в адресе нет, потому что
     * ключи ей не нужны.
     */
    @Test
    void serverTimeIsReadWithoutAccount() {
        when(tokenProvider.getTokenValue("platform-services")).thenReturn(TOKEN);
        server.expect(requestTo(BASE + "/api/v1/market/time"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("\"2026-09-05T10:00:00Z\"", MediaType.APPLICATION_JSON));

        assertThat(client.getServerTime())
                .isEqualTo(OffsetDateTime.of(2026, 9, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        server.verify();
    }

    /** Пустой ответ остаётся пустотой, а не превращается в отказ. */
    @Test
    void emptyAnswerIsNotAFailure() {
        when(tokenProvider.getTokenValue("platform-services")).thenReturn(TOKEN);
        server.expect(requestTo(BASE + "/api/v1/accounts/" + ACCOUNT
                        + "/positions/instrument?externalInstrumentId=" + INSTRUMENT))
                .andRespond(withSuccess());

        assertThat(client.getPosition(ACCOUNT, INSTRUMENT)).isNull();
        server.verify();
    }

    private static NeighbourProperties properties() {
        NeighbourProperties properties = new NeighbourProperties();
        properties.getConnector().setBaseUrl(BASE);
        properties.getConnector().setClientRegistrationId("platform-services");
        return properties;
    }
}
