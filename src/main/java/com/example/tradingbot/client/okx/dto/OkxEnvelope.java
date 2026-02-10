package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OkxEnvelope<T> {

    private String code;
    private String msg;
    private List<T> data;
}
