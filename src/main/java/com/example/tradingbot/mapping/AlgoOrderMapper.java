package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.search_params.AlgoOrderSearchParams;
import com.example.tradingbot.persistence.model.deal.algo_order.AlgoOrderEntity;
import com.example.tradingbot.rest.model.response.algo_order.AlgoOrderPageResponse;
import com.example.tradingbot.rest.model.response.algo_order.AlgoOrderResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface AlgoOrderMapper extends CommonMapper {

    /**
     * REST
     */

    @Mapping(target = "algoOrder", source = ".")
    AlgoOrderResponse domainToRest(AlgoOrder source);

    default AlgoOrderPageResponse domainToRest(Page<AlgoOrder> source) {
        if (source == null) {
            return new AlgoOrderPageResponse(Page.empty());
        }

        return new AlgoOrderPageResponse(source.map(this::domainToRestModel));
    }

    com.example.tradingbot.rest.model.response.algo_order.AlgoOrder domainToRestModel(AlgoOrder source);

    @Mapping(target = "conditionType", source = "type")
    @Mapping(target = "direction", source = "internalSide")
    AlgoOrder restToDomain(com.example.tradingbot.rest.model.request.algo_order.CreateAlgoOrderRequest source);

    AlgoOrderSearchParams restToDomainSearchParams(
            com.example.tradingbot.rest.model.request.algo_order.search_params.AlgoOrderSearchParams source);


    /**
     * DATA
     */

    default Page<AlgoOrder> dataToDomain(Page<AlgoOrderEntity> source) {
        if (source == null) {
            return Page.empty();
        }

        return source.map(this::dataToDomain);
    }

    AlgoOrder dataToDomain(AlgoOrderEntity source);

    AlgoOrderEntity domainToData(AlgoOrder source);


    /**
     * CLIENT
     */

    @Mapping(target = "orderType", source = "externalType")
    @Mapping(target = "size", source = "size")
    @Mapping(target = "clientOrderId", source = "internalId")
    com.example.tradingbot.client.model.okx.request.CreateAlgoOrderRequest domainToClientOkxRequest(AlgoOrder source);

    @Mapping(target = "algoOrderId", source = "externalId")
    @Mapping(target = "clientOrderId", source = "internalId")
    com.example.tradingbot.client.model.okx.request.CancelAlgoOrderRequest domainToClientOkxCancelRequest(AlgoOrder source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "internalId", source = "algoClOrdId")
    @Mapping(target = "externalType", source = "ordType")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "externalDirection", source = "side")
    @Mapping(target = "externalPositionSide", source = "posSide")
    @Mapping(target = "size", source = "sz", qualifiedByName = "stringToBigDecimal")
    AlgoOrder clientToDomain(com.example.tradingbot.client.model.okx.response.AlgoOrderResponse source);

    List<AlgoOrder> clientToDomain(List<com.example.tradingbot.client.model.okx.response.AlgoOrderResponse> source);

    List<AlgoOrderExternalSnapshot> clientToExternalSnapshot(
            List<com.example.tradingbot.client.model.okx.response.AlgoOrderResponse> source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "internalId", source = "algoClOrdId")
    @Mapping(target = "externalType", source = "ordType")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "externalDirection", source = "side")
    @Mapping(target = "externalPositionSide", source = "posSide")
    @Mapping(target = "condition.trigger.stopLoss.externalType", source = "slTriggerPxType")
    @Mapping(target = "condition.trigger.stopLoss.externalValue", source = "slTriggerPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "condition.trigger.takeProfit.externalType", source = "tpTriggerPxType")
    @Mapping(target = "condition.trigger.takeProfit.externalValue", source = "tpTriggerPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "condition.trailing.activationPrice.externalValue", source = "activePx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "condition.trailing.externalPrice", source = "moveTriggerPx", qualifiedByName = "stringToBigDecimal")
    AlgoOrderExternalSnapshot clientToExternalSnapshot(
            com.example.tradingbot.client.model.okx.response.AlgoOrderResponse source);


    /**
     * DOMAIN_COPY
     */

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "externalId", source = "externalId")
    @Mapping(target = "externalType", source = "externalType")
    @Mapping(target = "externalStatus", source = "externalStatus")
    @Mapping(target = "externalDirection", source = "externalDirection")
    @Mapping(target = "externalPositionSide", source = "externalPositionSide")
    @Mapping(target = "condition.trigger.stopLoss.externalType", source = "condition.trigger.stopLoss.externalType")
    @Mapping(target = "condition.trigger.stopLoss.externalValue", source = "condition.trigger.stopLoss.externalValue")
    @Mapping(target = "condition.trigger.takeProfit.externalType", source = "condition.trigger.takeProfit.externalType")
    @Mapping(target = "condition.trigger.takeProfit.externalValue", source = "condition.trigger.takeProfit.externalValue")
    @Mapping(target = "condition.trailing.activationPrice.externalValue", source = "condition.trailing.activationPrice.externalValue")
    @Mapping(target = "condition.trailing.externalPrice", source = "condition.trailing.externalPrice")
    void updateDomainFromExternalSnapshot(AlgoOrderExternalSnapshot source,
                                          @MappingTarget AlgoOrder target);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dealId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "externalType", ignore = true)
    @Mapping(target = "externalStatus", ignore = true)
    @Mapping(target = "externalDirection", ignore = true)
    @Mapping(target = "externalPositionSide", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "externalCreatedAt", ignore = true)
    @Mapping(target = "externalModifiedAt", ignore = true)
    void domainToDomainOnCreate(AlgoOrder source, @MappingTarget AlgoOrder target);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dealId", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    @Mapping(target = "conditionType", ignore = true)
    @Mapping(target = "size", ignore = true)
    @Mapping(target = "direction", ignore = true)
    @Mapping(target = "condition", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "externalCreatedAt", ignore = true)
    @Mapping(target = "externalModifiedAt", ignore = true)
    void domainToDomainOnUpdate(AlgoOrder source, @MappingTarget AlgoOrder target);
}
