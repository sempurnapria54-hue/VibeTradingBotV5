package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxOrderLinkedAlgoDto;
import com.example.tradingbot.domain.model.OrderLinkedAlgo;
import com.example.tradingbot.rest.model.OrderLinkedAlgoRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderLinkedAlgoMapper {
    OrderLinkedAlgo clientToDomain(OkxOrderLinkedAlgoDto dto);

    OrderLinkedAlgoRest domainToRest(OrderLinkedAlgo domain);
}
