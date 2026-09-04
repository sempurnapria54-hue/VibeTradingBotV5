package com.example.tradingbot.api;

import com.example.tradingbot.api.model.response.ErrorApiResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Единственный сборщик {@link ErrorApiResponse} внешней поверхности
 * (docs/rules/error-handling-policy.md).
 *
 * <p><b>Почему сборщик вынесен из обработчика.</b> Отказ доступа возникает
 * <b>до</b> контроллера, в фильтр-цепочке, и глобальный {@code @RestControllerAdvice}
 * его не видит по построению. Клейм «единый error-DTO» стал бы ложным ровно на
 * шаге, который вводит второй формат, — поэтому у класса отказа доступа свой
 * энфорсер (точки входа отказа), но <b>тот же</b> сборщик ответа.
 */
@Component
public class ErrorApiResponseFactory {

    public ErrorApiResponse build(HttpStatus status, String message, String path) {
        return ErrorApiResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
    }
}
