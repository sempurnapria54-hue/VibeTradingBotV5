package com.example.connector.okx.integration.client;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Логирует сырое тело ответа OKX на write-операциях (POST приватного
 * клиента: place/cancel order, place/cancel algo, close position). На
 * write OKX отдаёт реджект телом со статусом 200 и per-order
 * {@code sCode}; без сырого тела причина реджекта не диагностируема
 * извне (см. находку F1 пилота source-api). GET (read-ops) проходят без
 * буферизации и логирования. Тело буферизуется и переотдаётся
 * downstream через {@link BufferedClientHttpResponse}, чтобы десериализатор
 * прочитал его повторно. Секреты живут в заголовках запроса и не
 * логируются.
 */
@Slf4j
@Component
public class OkxWriteLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        if (isFalse(Objects.equals(HttpMethod.POST, request.getMethod()))) {
            return response;
        }
        byte[] rawBody = StreamUtils.copyToByteArray(response.getBody());
        log.info("OKX write raw response [{}] status={} body={}", request.getURI().getRawPath(),
                response.getStatusCode().value(), new String(rawBody, StandardCharsets.UTF_8));
        return new BufferedClientHttpResponse(response, rawBody);
    }
}
