package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.rest.model.CandleGroup;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CandleGroupMapper {

    List<CandleGroup> domainToRest(List<CandleGroupEntity> source);

    CandleGroup domainToRest(CandleGroupEntity source);

    CandleGroupEntity restToDomain(CandleGroup source);
}
