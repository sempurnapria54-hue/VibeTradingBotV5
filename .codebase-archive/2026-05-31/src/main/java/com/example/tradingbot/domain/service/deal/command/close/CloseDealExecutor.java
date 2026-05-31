package com.example.tradingbot.domain.service.deal.command.close;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CloseDealExecutor {

    private final DealDataService dealDataService;

    public void execute(List<Deal> liveDeals) {
        for (Deal deal : liveDeals) {
            if (deal == null) {
                continue;
            }
            deal.toError(Deal.CloseReason.EMERGENCY_STOP);
            dealDataService.save(deal);
        }
    }
}
