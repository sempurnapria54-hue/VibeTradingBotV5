package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OkxApiResponse<T> {

    private String code;
    private String msg;
    private List<T> data;
}
