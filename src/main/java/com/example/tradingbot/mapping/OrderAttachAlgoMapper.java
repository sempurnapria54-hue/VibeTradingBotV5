package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxOrderAttachAlgoDto;
import com.example.tradingbot.domain.model.OrderAttachAlgo;
import com.example.tradingbot.rest.model.OrderAttachAlgoRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderAttachAlgoMapper {
    OrderAttachAlgo clientToDomain(OkxOrderAttachAlgoDto dto);

    OrderAttachAlgoRest domainToRest(OrderAttachAlgo domain);
}
