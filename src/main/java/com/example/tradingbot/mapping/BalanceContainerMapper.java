package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.balance.BalanceContainer;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface BalanceContainerMapper extends CommonMapper {

    BalanceContainer toDomain(BalanceContainerEntity source);

    BalanceContainerEntity toEntity(BalanceContainer source);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "totalEquity", source = "totalEquity", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "unrealizedProfit", source = "unrealizedProfit", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalUpdatedAt", source = "externalModifiedAt")
    void updateDomainFromSnapshot(BalanceContainerExternalSnapshot source, @MappingTarget BalanceContainer target);
}
