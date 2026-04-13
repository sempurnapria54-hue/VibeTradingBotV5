package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.balance.BalanceDetail;
import com.example.tradingbot.domain.model.balance.Balance;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceExternalSnapshot;
import com.example.tradingbot.persistence.model.balance.BalanceEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BalanceMapper extends CommonMapper {

    @Mapping(target = "balanceContainerId", source = "balanceContainer.id")
    Balance dataToDomain(BalanceEntity source);

    @Mapping(target = "balanceContainer.id", source = "balanceContainerId")
    BalanceEntity domainToData(Balance source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "currency", source = "ccy")
    @Mapping(target = "cashBalance", source = "cashBal", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "availableBalance", source = "availBal", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "equity", source = "eq", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "frozenBalance", source = "frozenBal", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "unrealizedProfit", source = "upl", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalModifiedAt", source = "uTime", qualifiedByName = "toOffsetDateTimeUtc")
    BalanceExternalSnapshot clientOkxToExternalSnapshot(BalanceDetail source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<BalanceExternalSnapshot> clientOkxToExternalSnapshot(List<BalanceDetail> source);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "available", source = "availableBalance", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "frozen", source = "frozenBalance", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "total", source = "cashBalance", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalUpdatedAt", source = "externalModifiedAt")
    void updateDomainFromSnapshot(BalanceExternalSnapshot source, @MappingTarget Balance target);
}
