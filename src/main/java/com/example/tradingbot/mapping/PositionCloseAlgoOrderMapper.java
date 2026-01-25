package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxPositionCloseAlgoOrder;
import com.example.tradingbot.domain.model.PositionCloseAlgoOrder;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PositionCloseAlgoOrderMapper {

    PositionCloseAlgoOrder clientToDomain(OkxPositionCloseAlgoOrder order);

    List<PositionCloseAlgoOrder> clientToDomain(List<OkxPositionCloseAlgoOrder> orders);

    com.example.tradingbot.rest.model.PositionCloseAlgoOrder domainToRest(PositionCloseAlgoOrder order);

    List<com.example.tradingbot.rest.model.PositionCloseAlgoOrder> domainToRest(List<PositionCloseAlgoOrder> orders);
}
