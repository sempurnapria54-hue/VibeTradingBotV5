package com.example.tradingbot.client.okx;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class OkxTransportException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public OkxTransportException(String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
