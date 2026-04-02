package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.persistence.model.DealEntity;
import com.example.tradingbot.rest.model.response.DealResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DealMapper {


    Deal dataToDomain(DealEntity source);

    DealEntity domainToData(Deal source);

    DealResponse domainToRest(Deal source);
}
