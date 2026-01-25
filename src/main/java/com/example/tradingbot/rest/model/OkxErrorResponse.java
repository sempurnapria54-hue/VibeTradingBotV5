package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxErrorResponse {

    private String code;
    private String msg;
}
