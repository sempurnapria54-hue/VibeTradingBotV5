package com.example.tradingbot.rest.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxProxyResponse<T> {

    private String code;
    private String msg;
    private List<T> data;
}
