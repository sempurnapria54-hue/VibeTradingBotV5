package com.example.marketdata.api;

import com.example.marketdata.integration.ExchangeAccessException;
import com.example.marketdata.integration.ExchangeReadException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Единая внешняя поверхность ошибок: один advice, один error-DTO
 * (docs/rules/error-handling-policy.md; конвенция —
 * .claude/rules/codestyle.md §«Обработка ошибок»).
 *
 * <p><b>Отказ площадки наружу переезжает как отказ ЗАВИСИМОСТИ, а не как
 * наша ошибка.</b> Читатель рыночных данных не виноват в том, что
 * площадка отказала, и {@code 500} сказал бы ему «чини запрос»;
 * {@code 502}/{@code 503} говорят «повтори позже» — единственная реакция,
 * которая здесь уместна.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Площадка отказала в доступе либо исчерпан лимит: повтор имеет смысл не сразу. */
    @ExceptionHandler(ExchangeAccessException.class)
    public ResponseEntity<ErrorApiResponse> onExchangeAccess(ExchangeAccessException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "EXCHANGE_ACCESS_REFUSED", failure.getMessage());
    }

    /** Прочий отказ чтения площадки через коннектор. */
    @ExceptionHandler(ExchangeReadException.class)
    public ResponseEntity<ErrorApiResponse> onExchangeRead(ExchangeReadException failure) {
        return response(HttpStatus.BAD_GATEWAY, "EXCHANGE_READ_FAILED", failure.getMessage());
    }

    /** Неизвестный инструмент, неизвестная идентичность и прочий негодный вход. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", failure.getMessage());
    }

    private ResponseEntity<ErrorApiResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorApiResponse.builder()
                .code(code)
                .message(message)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
