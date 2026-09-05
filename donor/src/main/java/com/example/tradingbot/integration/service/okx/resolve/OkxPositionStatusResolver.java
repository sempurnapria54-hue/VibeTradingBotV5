package com.example.tradingbot.integration.service.okx.resolve;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.command.resolve.PositionStatusResolver;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import org.springframework.stereotype.Component;

/**
 * OKX-резолвер статуса позиции по факту наличия: null → CLOSED +
 * EXTERNAL_CLOSE (нормальный closed-on-exchange факт); snapshot → ACTIVE
 * (live risk при externalSize > 0 — на стороне модели). См.
 * docs/components/PositionStatusResolver.md.
 */
@Component
public class OkxPositionStatusResolver implements PositionStatusResolver {

    @Override
    public StatusResolveResult<Position.Status, Position.CloseReason> resolve(PositionExternalSnapshot snapshot) {
        if (isNull(snapshot)) {
            return StatusResolveResult.of(Position.Status.CLOSED, Position.CloseReason.EXTERNAL_CLOSE);
        }
        return StatusResolveResult.of(Position.Status.ACTIVE, null);
    }
}
