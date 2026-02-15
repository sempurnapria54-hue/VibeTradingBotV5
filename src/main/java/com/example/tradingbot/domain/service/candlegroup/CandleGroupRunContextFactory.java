package com.example.tradingbot.domain.service.candlegroup;

import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.util.TimeframeMillis;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CandleGroupRunContextFactory {

    private final Clock clock;
    private final CandleGroupLeaseService candleGroupLeaseService;

    public CandleGroupRunContext create(CandleGroupEntity group) {
        long runNowMillis = clock.millis();
        long tfMillis = TimeframeMillis.toMillis(group.getTimeframe());
        long nowClosedTs = (runNowMillis / tfMillis) * tfMillis - tfMillis;
        return new CandleGroupRunContext(
            runNowMillis,
            tfMillis,
            nowClosedTs,
            candleGroupLeaseService.getInstanceId()
        );
    }
}
