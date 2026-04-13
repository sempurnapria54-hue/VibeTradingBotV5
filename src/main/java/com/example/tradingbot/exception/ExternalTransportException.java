package com.example.tradingbot.exception;

import org.springframework.http.HttpStatus;

public class ExternalTransportException extends ExternalApiException {

    public ExternalTransportException(String message, HttpStatus httpStatus) {
        super("transport_error", message, httpStatus);
    }
}
