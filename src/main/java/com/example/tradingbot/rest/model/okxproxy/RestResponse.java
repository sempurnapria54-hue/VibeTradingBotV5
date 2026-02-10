package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RestResponse<T> {

    private String code;
    private String message;
    private List<T> data;
}
