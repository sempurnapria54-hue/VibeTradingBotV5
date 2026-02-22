package com.example.tradingbot.rest.error;

import com.example.tradingbot.client.exception.ExternalApiException;
import com.example.tradingbot.domain.service.trading.TradingCommandException;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiErrorResponse> handleOkxApiException(ExternalApiException exception) {
        ApiErrorResponse body = new ApiErrorResponse(exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus()).body(body);
    }


    @ExceptionHandler(TradingCommandException.class)
    public ResponseEntity<ApiErrorResponse> handleTradingCommandException(TradingCommandException exception) {
        ApiErrorResponse body = new ApiErrorResponse(exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus()).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason();
        if (Objects.isNull(message) || message.isBlank()) {
            message = exception.getStatusCode().toString();
        }
        ApiErrorResponse body = new ApiErrorResponse("request_error", message);
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        ApiErrorResponse body = new ApiErrorResponse("internal_error", "Unexpected server error");
        return ResponseEntity.internalServerError().body(body);
    }
}
