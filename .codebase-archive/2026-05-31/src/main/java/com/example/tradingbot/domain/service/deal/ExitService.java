package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.domain.model.core.deal.Deal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExitService {

    public void finalizeExit(Deal deal) {
        if (Objects.isNull(deal)) {
            throw new IllegalArgumentException("deal is null");
        }

        if (Objects.isNull(deal.getCloseReason())) {
            deal.setCloseReason(Deal.CloseReason.RECONCILIATION);
        }
    }
}
