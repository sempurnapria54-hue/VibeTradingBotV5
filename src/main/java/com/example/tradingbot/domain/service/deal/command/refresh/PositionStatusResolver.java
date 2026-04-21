package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

@Component
public class PositionStatusResolver {

    public Position.Status resolveStatus(PositionExternalSnapshot snapshot) {
        if (Objects.isNull(snapshot)) {
            return Position.Status.CLOSED;
        }
        if (Objects.isNull(snapshot.getSize())) {
            return Position.Status.CLOSED;
        }
        if (Objects.equals(snapshot.getSize().compareTo(BigDecimal.ZERO), 0)) {
            return Position.Status.CLOSED;
        }
        return Position.Status.ACTIVE;
    }
}
