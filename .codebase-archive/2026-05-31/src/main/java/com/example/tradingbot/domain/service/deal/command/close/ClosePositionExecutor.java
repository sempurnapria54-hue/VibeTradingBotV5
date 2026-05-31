package com.example.tradingbot.domain.service.deal.command.close;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.ClosePositionCommandPayload;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Component
@RequiredArgsConstructor
public class ClosePositionExecutor {

    private final PositionDataService positionDataService;

    private final ClientManager clientManager;

    @Transactional
    public void execute(DealContext context, ServiceCommand command) {
        ClosePositionCommandPayload payload = resolvePayload(command);
        if (isPartialClose(payload)) {
            throw new UnsupportedOperationException("partial position close is not implemented yet");
        }

        Exchange exchange = requireExchange(context);
        Instrument instrument = requireInstrument(context);
        ClientService clientService = clientManager.getClientService(exchange.getName());
        List<Position> livePositions = resolveLivePositions(context, payload);
        if (livePositions.isEmpty()) {
            return;
        }

        clientService.closePositions(instrument);
        for (Position position : livePositions) {
            if (Objects.isNull(position)) {
                continue;
            }
            position.toClose(Position.CloseReason.STRATEGY_EXIT);
            positionDataService.save(position);
        }
    }

    public void execute(ClientService clientService,
                        Instrument instrument,
                        List<Position> livePositions,
                        boolean hasExternalOpenPositions) {
        if (BooleanUtils.isFalse(hasExternalOpenPositions) && emptyIfNull(livePositions).isEmpty()) {
            return;
        }

        clientService.closePositions(instrument);
        for (Position position : emptyIfNull(livePositions)) {
            if (Objects.isNull(position)) {
                continue;
            }
            position.toClose(Position.CloseReason.EMERGENCY_CLOSE);
            positionDataService.save(position);
        }
    }

    private ClosePositionCommandPayload resolvePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            return null;
        }
        if (command.getPayload() instanceof ClosePositionCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("CLOSE_POSITION payload has unsupported type");
    }

    private boolean isPartialClose(ClosePositionCommandPayload payload) {
        if (Objects.isNull(payload) || Objects.isNull(payload.getCloseFractionPercents())) {
            return false;
        }
        return payload.getCloseFractionPercents()
                .compareTo(BigDecimal.valueOf(100)) < 0;
    }

    private List<Position> resolveLivePositions(DealContext context, ClosePositionCommandPayload payload) {
        List<Position> result = new ArrayList<>();
        if (Objects.nonNull(payload) && Objects.nonNull(payload.getPositionId())) {
            Position position = positionDataService.findByIdRequired(payload.getPositionId());
            if (position.isLive()) {
                result.add(position);
            }
            return result;
        }

        Position activePosition = context.getActivePosition();
        if (Objects.nonNull(activePosition) && activePosition.isLive()) {
            result.add(activePosition);
        }
        return result;
    }

    private Exchange requireExchange(DealContext context) {
        Exchange exchange = context.getExchange();
        if (Objects.isNull(exchange)) {
            throw new IllegalStateException("exchange is null");
        }
        return exchange;
    }

    private Instrument requireInstrument(DealContext context) {
        Instrument instrument = context.getInstrument();
        if (Objects.isNull(instrument)) {
            throw new IllegalStateException("instrument is null");
        }
        return instrument;
    }
}
