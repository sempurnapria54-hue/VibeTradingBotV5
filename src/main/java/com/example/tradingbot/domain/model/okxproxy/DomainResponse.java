package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DomainResponse<T> {

    private String code;
    private String message;
    private List<T> data;
}
