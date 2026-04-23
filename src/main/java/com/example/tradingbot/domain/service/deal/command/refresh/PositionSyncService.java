package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.mapping.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

import static com.example.tradingbot.util.factory.PositionFactory.createPosition;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class PositionSyncService {

    private final PositionMapper positionMapper;
    private final PositionStatusResolver positionStatusResolver;

    public void applySnapshot(Position target, PositionExternalSnapshot snapshot) {
        if (Objects.isNull(target) || Objects.isNull(snapshot)) {
            return;
        }

        positionMapper.updateDomainFromExternalSnapshot(snapshot, target);
        target.setSide(resolveSide(snapshot.getExternalSide()));
        target.setStatus(positionStatusResolver.resolveStatus(snapshot));
        if (Objects.equals(target.getStatus(), Position.Status.ACTIVE)) {
            target.setCloseReason(null);
        }
    }

    public Position createFromSnapshot(PositionExternalSnapshot snapshot, Long dealId) {
        Position position = createPosition(dealId);
        applySnapshot(position, snapshot);
        return position;
    }

    public void closeMissingPosition(Position position) {
        if (Objects.isNull(position)) {
            return;
        }

        position.toClose(Position.CloseReason.UNKNOWN);
    }

    private Position.Side resolveSide(String externalSide) {
        if (isFalse(isNotBlank(externalSide))) {
            return Position.Side.NET;
        }

        String normalizedSide = externalSide.trim()
                                            .toLowerCase(Locale.ROOT);
        return switch (normalizedSide) {
            case "long" -> Position.Side.LONG;
            case "short" -> Position.Side.SHORT;
            default -> Position.Side.NET;
        };
    }

    private boolean isNotBlank(String value) {
        return Objects.nonNull(value) && isFalse(value.isBlank());
    }
}
