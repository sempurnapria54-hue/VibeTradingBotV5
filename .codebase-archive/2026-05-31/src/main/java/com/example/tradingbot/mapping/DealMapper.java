package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.persistence.model.deal.DealEntity;
import com.example.tradingbot.rest.model.response.DealResponse;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {
        OrderMapper.class,
        AlgoOrderMapper.class,
        PositionMapper.class
})
public interface DealMapper {

    /**
     * DATA
     */

    Deal dataToDomain(DealEntity source);

    DealEntity domainToData(Deal source);

    /**
     * REST
     */

    @Mapping(target = "orderEntities", source = "orders")
    @Mapping(target = "algoOrderEntities", source = "algoOrders")
    @Mapping(target = "positionEntities", source = "positions")
    DealResponse domainToRest(Deal source);
}
