package com.example.connector.okx.integration.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Обёртка {@link ClientHttpResponse} с буферизованным телом: статус и
 * заголовки делегируются исходному ответу, тело отдаётся из массива
 * байт и потому читается повторно. Нужна, чтобы
 * {@link OkxWriteLoggingInterceptor} мог прочитать сырое тело для лога,
 * не лишив его downstream-десериализатор.
 */
@RequiredArgsConstructor
public class BufferedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;
    private final byte[] body;

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return delegate.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public InputStream getBody() {
        return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
