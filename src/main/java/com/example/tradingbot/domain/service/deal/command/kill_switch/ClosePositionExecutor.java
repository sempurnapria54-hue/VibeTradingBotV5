package com.example.tradingbot.domain.service.deal.command.kill_switch;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ClosePositionExecutor {

    private final PositionDataService positionDataService;

    public void execute(ClientService clientService,
                        Instrument instrument,
                        List<Position> livePositions,
                        boolean hasExternalOpenPositions) {
        if (!hasExternalOpenPositions && livePositions.isEmpty()) {
            return;
        }

        clientService.closePositions(instrument);
        for (Position position : livePositions) {
            if (position == null) {
                continue;
            }
            position.toClose(Position.CloseReason.EMERGENCY_CLOSE);
            positionDataService.save(position);
        }
    }
}
