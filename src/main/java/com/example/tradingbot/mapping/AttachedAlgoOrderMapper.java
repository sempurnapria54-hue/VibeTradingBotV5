package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.persistence.model.deal.order.AttachedAlgoOrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(
        componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface AttachedAlgoOrderMapper {

    @Mapping(target = "orderId", source = "order.id")
    AttachedAlgoOrder dataToDomain(AttachedAlgoOrderEntity source);

    @Mapping(target = "order.id", source = "orderId")
    AttachedAlgoOrderEntity domainToData(AttachedAlgoOrder source);
}
