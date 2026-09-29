package com.example.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.integration.internal.api.OwnerAddressResolver;
import com.example.bff.integration.internal.api.OwnerProxyClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Ответ владельца доезжает его решением, а соединение владельца — нет
 * (docs/architecture/contracts.md §«Периметр: что `bff` отдаёт и чего не
 * делает»).
 *
 * <p><b>Почему это охраняется здесь, а не только сквозным кейсом.</b> Стаб
 * владельца у ящика периметра отвечает с {@code Content-Length}, и
 * пересланный заголовок соединения он не показывает; сквозной кейс видит его
 * только у владельца, отвечающего кусками.
 */
class OwnerProxyHeadersTest {

    private static final String OWNER = "trading-core";
    private static final String PATH = "/api/v1/trading-core/deals/absent-deal";
    private static final String URL = "http://trading-core:8080" + PATH;

    @Test
    @DisplayName("Заголовки соединения владельца браузеру не пересылаются; статус, тип и тело — как у владельца")
    void theOwnerConnectionHeadersStayAtThePerimeter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.add(HttpHeaders.TRANSFER_ENCODING, "chunked");
        ownerHeaders.add(HttpHeaders.CONNECTION, "keep-alive");
        ownerHeaders.add("Keep-Alive", "timeout=60");
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(ownerHeaders)
                        .body("{\"code\":\"NOT_FOUND\"}"));

        ResponseEntity<byte[]> answer = clientOver(builder)
                .forward(OWNER, HttpMethod.GET, PATH, new HttpHeaders(), null);

        assertThat(answer.getHeaders().containsHeader(HttpHeaders.TRANSFER_ENCODING)).isFalse();
        assertThat(answer.getHeaders().containsHeader(HttpHeaders.CONNECTION)).isFalse();
        assertThat(answer.getHeaders().containsHeader("Keep-Alive")).isFalse();
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(new String(answer.getBody())).isEqualTo("{\"code\":\"NOT_FOUND\"}");
        server.verify();
    }

    private OwnerProxyClient clientOver(RestClient.Builder builder) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.setOwnerUrlTemplate("http://{owner}:8080");
        properties.setReadRetries(0);
        return new OwnerProxyClient(builder, new OwnerAddressResolver(properties), properties);
    }
}
