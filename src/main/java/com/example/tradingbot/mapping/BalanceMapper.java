package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.balance.BalanceDetail;
import com.example.tradingbot.client.model.okx.response.balance.BalanceResponse;
import com.example.tradingbot.domain.model.balance.Balance;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceContainerExternalSnapshot;
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

    Balance toDomain(BalanceEntity source);

    BalanceEntity toEntity(Balance source);

    BalanceExternalSnapshot clientOkxResponseToDomain(BalanceResponse source);

    BalanceResponse domainToClient(BalanceExternalSnapshot source);

    List<BalanceExternalSnapshot> clientOkxResponseToDomain(List<BalanceResponse> source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "currency", source = "ccy")
    @Mapping(target = "cashBalance", source = "cashBal")
    @Mapping(target = "availableBalance", source = "availBal")
    @Mapping(target = "equity", source = "eq")
    @Mapping(target = "frozenBalance", source = "frozenBal")
    @Mapping(target = "unrealizedProfit", source = "upl")
    @Mapping(target = "externalModifiedAt", source = "uTime", qualifiedByName = "toOffsetDateTimeUtc")
    BalanceExternalSnapshot clientOkxToExternalSnapshot(BalanceDetail source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<BalanceExternalSnapshot> clientOkxToExternalSnapshot(List<BalanceDetail> source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "exchangeId", source = "exchangeId")
    @Mapping(target = "totalEquity", source = "response.totalEq")
    @Mapping(target = "unrealizedProfit", source = "response.upl")
    @Mapping(target = "externalModifiedAt", source = "response.uTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "balanceExternalSnapshots", source = "response.details")
    BalanceContainerExternalSnapshot clientOkxToExternalSnapshot(Long exchangeId, BalanceResponse response);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "available", source = "availableBalance", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "frozen", source = "frozenBalance", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "total", source = "cashBalance", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalUpdatedAt", source = "externalModifiedAt")
    void updateDomainFromSnapshot(BalanceExternalSnapshot source, @MappingTarget Balance target);
}
