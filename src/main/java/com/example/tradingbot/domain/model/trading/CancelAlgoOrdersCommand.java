package com.example.tradingbot.domain.model.trading;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelAlgoOrdersCommand {

    private Long exchangeId;
    private Long instrumentId;
    private List<String> internalIds;
}
