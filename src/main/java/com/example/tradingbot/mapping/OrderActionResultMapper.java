package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxOrderActionResultDto;
import com.example.tradingbot.domain.model.OrderActionResult;
import com.example.tradingbot.rest.model.OrderActionResultRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderActionResultMapper {
    OrderActionResult clientToDomain(OkxOrderActionResultDto dto);

    OrderActionResultRest domainToRest(OrderActionResult domain);
}
