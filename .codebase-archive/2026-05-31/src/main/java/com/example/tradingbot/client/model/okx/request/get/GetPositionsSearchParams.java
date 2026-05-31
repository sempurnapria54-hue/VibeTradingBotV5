package com.example.tradingbot.client.model.okx.request.get;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetPositionsSearchParams {

    private String instrumentExternalId;
    private String instrumentExternalType;
}
