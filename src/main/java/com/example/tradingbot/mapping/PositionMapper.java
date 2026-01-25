package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxPosition;
import com.example.tradingbot.domain.model.Position;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = PositionCloseAlgoOrderMapper.class)
public interface PositionMapper {

    Position clientToDomain(OkxPosition position);

    List<Position> clientToDomain(List<OkxPosition> positions);

    com.example.tradingbot.rest.model.Position domainToRest(Position position);

    List<com.example.tradingbot.rest.model.Position> domainToRest(List<Position> positions);
}
