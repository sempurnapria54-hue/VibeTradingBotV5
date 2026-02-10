package com.example.tradingbot.client.okx;

import org.springframework.http.HttpStatus;

public class OkxTransportException extends OkxApiException {

    public OkxTransportException(String message, HttpStatus httpStatus) {
        super("transport_error", message, httpStatus);
    }
}
