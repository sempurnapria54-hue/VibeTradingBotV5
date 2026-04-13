package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.persistence.model.deal.position.PositionEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PositionMapper extends CommonMapper {

    /**
     * DATA
     */
    Position dataToDomain(PositionEntity source);

    PositionEntity domainToData(Position source);


    /**
     * REST
     */
    com.example.tradingbot.rest.model.response.PositionResponse domainToRest(Position source);

    /**
     * CLIENT
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalId", source = "posId")
    @Mapping(target = "externalSide", source = "posSide", qualifiedByName = "stringToPositionSide")
    @Mapping(target = "size", source = "pos", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "averagePrice", source = "avgPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "markPrice", source = "markPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "liquidationPrice", source = "liqPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "leverage", source = "lever", qualifiedByName = "stringToInteger")
    @Mapping(target = "marginMode", source = "mgnMode")
    @Mapping(target = "unrealizedProfit", source = "upl", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalCreatedAt", source = "cTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "externalModifiedAt", source = "uTime", qualifiedByName = "toOffsetDateTimeUtc")
    PositionExternalSnapshot clientOkxToExternalSnapshot(PositionResponse source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<PositionExternalSnapshot> clientOkxToExternalSnapshot(List<PositionResponse> source);


    @Mapping(source = "externalId", target = "instrumentId")
    ClosePositionRequest domainToClientOkxCloseRequest(Instrument source);


    /**
     * DOMAIN_COPY
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dealId", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "side", ignore = true)
    void updateDomainFromExternalSnapshot(PositionExternalSnapshot source, @MappingTarget Position target);

}
