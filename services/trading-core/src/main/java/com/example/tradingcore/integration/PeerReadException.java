package com.example.tradingcore.integration;

/**
 * Сосед по ярусу домена ответил отказом на НАШ запрос: {@code 4xx},
 * включая отказ идентичности.
 *
 * <p><b>Отделено от {@link PeerServiceUnavailableException} по границе,
 * которую провёл дом класса:</b> недоступностью там названы таймаут,
 * обрыв и {@code 5xx}
 * (docs/rules/runtime-error-classification.md). Всё, что сосед отверг
 * осознанно, — неверный запрос либо ненастроенная идентичность, то есть
 * наш дефект: повтор тем же запросом даст тот же отказ.
 */
public class PeerReadException extends RuntimeException {

    public PeerReadException(String message, Throwable cause) {
        super(message, cause);
    }

    public PeerReadException(String message) {
        super(message);
    }
}
