package com.example.tradingbot.client.okx.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxApiResponse<T> {

    private String code;
    private String msg;
    private List<T> data;
    private String inTime;
    private String outTime;
}
