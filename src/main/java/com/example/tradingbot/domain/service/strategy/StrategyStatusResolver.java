package com.example.tradingbot.domain.service.strategy;

import com.example.tradingbot.domain.model.trade.strategy.StrategyStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class StrategyStatusResolver {

    public boolean canTransition(StrategyStatus from, StrategyStatus to) {
        if (Objects.isNull(from) || Objects.isNull(to)) {
            return false;
        }

        return switch (from) {
            case CREATED -> Objects.equals(to, StrategyStatus.ACTIVE)
                    || Objects.equals(to, StrategyStatus.INACTIVE)
                    || Objects.equals(to, StrategyStatus.DELETED);
            case ACTIVE -> Objects.equals(to, StrategyStatus.INACTIVE);
            case INACTIVE -> Objects.equals(to, StrategyStatus.ACTIVE)
                    || Objects.equals(to, StrategyStatus.DELETED);
            case DELETED -> false;
        };
    }
}
