package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ConditionMapper extends CommonMapper {

    /**
     * REST
     */

    com.example.tradingbot.rest.model.response.algo_order.Condition domainToRest(Condition source);

    /**
     * DATA
     */

    Condition dataToDomain(com.example.tradingbot.persistence.model.deal.algo_order.Condition source);

    com.example.tradingbot.persistence.model.deal.algo_order.Condition domainToData(Condition source);

    /**
     * DOMAIN_COPY
     */

    default void updateDomainFromExternalSnapshot(AlgoOrderExternalSnapshot.ConditionExternalSnapshot source,
                                                  @MappingTarget Condition target) {
        if (source == null) {
            return;
        }
        if (target == null) {
            return;
        }

        updateTriggerFromExternalSnapshot(source.getTrigger(), target.getTrigger());
        updateTrailingFromExternalSnapshot(source.getTrailing(), target.getTrailing());
    }

    default void updateTriggerFromExternalSnapshot(AlgoOrderExternalSnapshot.TriggerExternalSnapshot source,
                                                   @MappingTarget Trigger target) {
        if (source == null) {
            return;
        }
        if (target == null) {
            return;
        }

        updateTriggerPriceFromExternalSnapshot(source.getStopLoss(), target.getStopLoss());
        updateTriggerPriceFromExternalSnapshot(source.getTakeProfit(), target.getTakeProfit());
    }

    default void updateTrailingFromExternalSnapshot(AlgoOrderExternalSnapshot.TrailingExternalSnapshot source,
                                                    @MappingTarget Trailing target) {
        if (source == null) {
            return;
        }
        if (target == null) {
            return;
        }

        updateTriggerPriceFromExternalSnapshot(source.getActivationPrice(), target.getActivationPrice());

        if (source.getExternalPrice() != null) {
            target.setExternalPrice(source.getExternalPrice());
        }
    }

    default void updateTriggerPriceFromExternalSnapshot(AlgoOrderExternalSnapshot.TriggerPriceExternalSnapshot source,
                                                        @MappingTarget TriggerPrice target) {
        if (source == null) {
            return;
        }
        if (target == null) {
            return;
        }

        if (source.getExternalType() != null) {
            target.setExternalType(source.getExternalType());
        }

        if (source.getExternalValue() != null) {
            target.setExternalValue(source.getExternalValue());
        }
    }
}