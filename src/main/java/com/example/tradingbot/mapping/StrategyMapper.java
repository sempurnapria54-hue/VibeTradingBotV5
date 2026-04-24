package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.persistence.model.strategy.StrategyDetailsEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import com.example.tradingbot.rest.model.request.strategy.CreateStrategyRequest;
import com.example.tradingbot.rest.model.response.strategy.StrategyResponse;
import com.example.tradingbot.rest.model.response.strategy.StrategyStatusResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Objects;

@Mapper(componentModel = "spring", uses = StrategyDetailMapper.class)
public interface StrategyMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "externalCreatedAt", ignore = true)
    @Mapping(target = "externalModifiedAt", ignore = true)
    Strategy restToDomain(CreateStrategyRequest source);

    StrategyResponse domainToRest(Strategy source);

    StrategyStatusResponse domainToRestStatus(Strategy source);

    @Mapping(target = "details", source = "detailEntities")
    Strategy dataToDomain(StrategyEntity source);

    @Mapping(target = "detailEntities", source = "details")
    StrategyEntity domainToData(Strategy source);

    @AfterMapping
    default void linkDetails(@MappingTarget StrategyEntity target) {
        if (Objects.isNull(target) || Objects.isNull(target.getDetailEntities())) {
            return;
        }

        for (StrategyDetailsEntity detailEntity : target.getDetailEntities()) {
            if (Objects.isNull(detailEntity)) {
                continue;
            }

            detailEntity.setStrategy(target);
            if (Objects.nonNull(target.getId())) {
                detailEntity.setStrategyId(target.getId());
            }
        }
    }
}
