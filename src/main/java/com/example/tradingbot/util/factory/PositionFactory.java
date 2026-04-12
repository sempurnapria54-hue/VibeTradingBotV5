package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.Position.Side;
import com.example.tradingbot.domain.model.position.Position.Status;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class PositionFactory {

    public static Position createPosition(Long dealId) {
        Position position = new Position();
        position.setDealId(dealId);
        position.setInternalId(UUID.randomUUID()
                                   .toString());
        position.setSide(Side.NET);
        position.setStatus(Status.ACTIVE);
        return position;
    }
}
