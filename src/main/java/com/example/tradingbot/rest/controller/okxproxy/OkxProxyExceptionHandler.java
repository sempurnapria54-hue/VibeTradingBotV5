package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxApiException;
import com.example.tradingbot.client.okx.OkxTransportException;
import com.example.tradingbot.rest.model.OkxErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OkxProxyExceptionHandler {

    @ExceptionHandler(OkxApiException.class)
    public ResponseEntity<OkxErrorResponse> handleOkxApiException(OkxApiException exception) {
        OkxErrorResponse response = new OkxErrorResponse();
        response.setCode(exception.getCode());
        response.setMsg(exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(OkxTransportException.class)
    public ResponseEntity<OkxErrorResponse> handleOkxTransportException(OkxTransportException exception) {
        OkxErrorResponse response = new OkxErrorResponse();
        response.setCode("UPSTREAM_ERROR");
        response.setMsg(exception.getMessage());
        HttpStatusCode statusCode = exception.getStatusCode();
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if (statusCode != null) {
            status = HttpStatus.resolve(statusCode.value());
        } else if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timeout")) {
            status = HttpStatus.GATEWAY_TIMEOUT;
        }
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return ResponseEntity.status(status).body(response);
    }
}
