package com.example.tradingbot.rest.model.response.exchange;

import com.example.tradingbot.rest.model.response.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange extends Auditable {

    private String internalId;
    private String name;
    private String baseUrl;
    private String status;
}
