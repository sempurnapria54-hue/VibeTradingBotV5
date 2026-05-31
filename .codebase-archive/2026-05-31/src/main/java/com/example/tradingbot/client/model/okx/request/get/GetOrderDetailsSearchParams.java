package com.example.tradingbot.client.model.okx.request.get;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetOrderDetailsSearchParams {

    private String instrumentExternalId;
    private String externalId;
    private String internalId;
}
