package com.example.tradingbot.rest.error;

import com.example.tradingbot.exception.ExternalApiException;
import com.example.tradingbot.exception.TradingCommandException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiErrorResponse> handleOkxApiException(ExternalApiException exception) {
        ApiErrorResponse body = new ApiErrorResponse(exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus())
                             .body(body);
    }


    @ExceptionHandler(TradingCommandException.class)
    public ResponseEntity<ApiErrorResponse> handleTradingCommandException(TradingCommandException exception) {
        ApiErrorResponse body = new ApiErrorResponse(exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus())
                             .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        List<String> messages = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            if (Objects.nonNull(fieldError.getDefaultMessage()) && !fieldError.getDefaultMessage().isBlank()) {
                messages.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
            }
        }

        if (org.springframework.util.CollectionUtils.isEmpty(messages)) {
            messages.add("Request body validation failed");
        }

        ApiErrorResponse body = new ApiErrorResponse(
                "request_error",
                String.join("; ", messages)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        String message = "Malformed request body";
        if (Objects.nonNull(exception.getMostSpecificCause())
                && Objects.nonNull(exception.getMostSpecificCause().getMessage())
                && !exception.getMostSpecificCause().getMessage().isBlank()) {
            message = exception.getMostSpecificCause().getMessage();
        }

        ApiErrorResponse body = new ApiErrorResponse("request_error", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason();
        if (Objects.isNull(message) || message.isBlank()) {
            message = exception.getStatusCode()
                               .toString();
        }
        ApiErrorResponse body = new ApiErrorResponse("request_error", message);
        return ResponseEntity.status(exception.getStatusCode())
                             .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException exception) {
        if (Objects.nonNull(exception.getMessage()) && exception.getMessage().contains("No enum constant")) {
            ApiErrorResponse body = new ApiErrorResponse("request_error", exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(body);
        }

        ApiErrorResponse body = new ApiErrorResponse("internal_error", "Unexpected server error");
        return ResponseEntity.internalServerError()
                             .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        ApiErrorResponse body = new ApiErrorResponse("internal_error", "Unexpected server error");
        return ResponseEntity.internalServerError()
                             .body(body);
    }
}
