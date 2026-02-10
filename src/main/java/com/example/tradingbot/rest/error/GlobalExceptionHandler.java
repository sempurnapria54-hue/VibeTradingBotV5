package com.example.tradingbot.rest.error;

import com.example.tradingbot.client.okx.OkxApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OkxApiException.class)
    public ResponseEntity<ApiErrorResponse> handleOkxApiException(OkxApiException exception) {
        ApiErrorResponse body = new ApiErrorResponse(exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        ApiErrorResponse body = new ApiErrorResponse("internal_error", "Unexpected server error");
        return ResponseEntity.internalServerError().body(body);
    }
}
