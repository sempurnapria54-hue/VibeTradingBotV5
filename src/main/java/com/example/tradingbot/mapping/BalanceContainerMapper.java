package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.balance.BalanceResponse;
import com.example.tradingbot.domain.model.balance.BalanceContainer;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import com.example.tradingbot.persistence.model.balance.BalanceEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", uses = BalanceMapper.class)
public interface BalanceContainerMapper extends CommonMapper {

    /**
     * DATA
     */

    BalanceContainer dataToDomain(BalanceContainerEntity source);

    BalanceContainerEntity domainToData(BalanceContainer source);

    /**
     * CLIENT
     */

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "exchangeId", source = "exchangeId")
    @Mapping(target = "totalEquity", source = "response.totalEq")
    @Mapping(target = "unrealizedProfit", source = "response.upl")
    @Mapping(target = "externalModifiedAt", source = "response.uTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "balanceExternalSnapshots", source = "response.details")
    BalanceContainerExternalSnapshot clientToExternalSnapshot(Long exchangeId, BalanceResponse response);

    /**
     * DOMAIN_COPY
     */

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "totalEquity", source = "totalEquity", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "unrealizedProfit", source = "unrealizedProfit", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalUpdatedAt", source = "externalModifiedAt")
    void updateDomainFromExternalSnapshot(BalanceContainerExternalSnapshot source,
                                          @MappingTarget BalanceContainer target);

    @AfterMapping
    default void linkChildren(@MappingTarget BalanceContainerEntity target) {
        List<BalanceEntity> balances = target.getBalances();
        if (balances == null) {
            return;
        }
        for (BalanceEntity balance : balances) {
            balance.setBalanceContainer(target);
        }
    }
}
