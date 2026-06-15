package com.example.tradingbot.mapping;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.integration.model.okx.request.CancelOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.response.AttachAlgoOrdOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OrderOkxResponse;
import com.example.tradingbot.persistence.model.order.AttachedAlgoOrderEntity;
import com.example.tradingbot.persistence.model.order.OrderEntity;
import com.example.tradingbot.util.Constants;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link Order} и вложенного {@link AttachedAlgoOrder} между
 * слоями: domain ↔ persistence, OKX response → snapshot, domain → OKX
 * request (place/cancel), OKX ack → {@link ExchangeAck}. Список
 * attachedAlgoOrders — дочерние строки (оркестрация в OrderDataService).
 * OKX-строки нормализуются {@link OkxResponseConverter}; tdMode/posSide —
 * adapter-константы. См. docs/models/domain/core/Order.md,
 * docs/models/mapping/Order.md.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = OkxResponseConverter.class, imports = {Constants.class, StringUtils.class})
public interface OrderMapper {

    OrderEntity domainToPersistence(Order order);

    Order persistenceToDomain(OrderEntity entity);

    AttachedAlgoOrderEntity domainToPersistence(AttachedAlgoOrder attached);

    AttachedAlgoOrder persistenceToDomain(AttachedAlgoOrderEntity entity);

    @Mapping(target = "internalId", source = "clOrdId")
    @Mapping(target = "externalId", source = "ordId")
    @Mapping(target = "type", source = "ordType")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "price", source = "px")
    @Mapping(target = "size", source = "sz")
    @Mapping(target = "accumulatedFillSize", source = "accFillSz")
    @Mapping(target = "averagePrice", source = "avgPx")
    @Mapping(target = "externalCreatedAt", source = "cTime")
    @Mapping(target = "externalModifiedAt", source = "uTime")
    @Mapping(target = "attachedAlgoInternalId", source = "attachAlgoClOrdId")
    @Mapping(target = "takeProfitTriggerPrice", source = "tpTriggerPx")
    @Mapping(target = "stopLossTriggerPrice", source = "slTriggerPx")
    @Mapping(target = "attachedAlgoOrders", source = "attachAlgoOrds")
    OrderExternalSnapshot integrationToSnapshot(OrderOkxResponse response);

    @Mapping(target = "externalAttachedId", source = "attachAlgoId")
    @Mapping(target = "internalId", source = "attachAlgoClOrdId")
    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "externalType", source = "tpOrdKind")
    @Mapping(target = "stopLossTriggerPrice", source = "slTriggerPx")
    AttachedAlgoOrderExternalSnapshot integrationToSnapshot(AttachAlgoOrdOkxResponse response);

    /**
     * Обновление полей Order из снапшота (REFRESH-контур). internalId
     * (stable client id), бизнес-type и attached не перетираются;
     * доменный status / closeReason применяет исполнитель через резолвер.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "attachedAlgoOrders", ignore = true)
    void updateFromSnapshot(OrderExternalSnapshot snapshot, @MappingTarget Order order);

    @Mapping(target = "instId", source = "instId")
    @Mapping(target = "tdMode", expression = "java(Constants.Okx.TD_MODE_ISOLATED)")
    @Mapping(target = "posSide", expression = "java(Constants.Okx.POS_SIDE_NET)")
    @Mapping(target = "tag", expression = "java(Constants.Okx.ORDER_TAG)")
    @Mapping(target = "ordType", expression = "java(resolveOrdType(order))")
    @Mapping(target = "clOrdId", source = "order.internalId")
    @Mapping(target = "side", source = "order.side")
    @Mapping(target = "sz", source = "order.size")
    @Mapping(target = "px", source = "order.price")
    @Mapping(target = "reduceOnly", source = "order.positionReducingOnly")
    PlaceOrderOkxRequest domainToPlaceRequest(Order order, String instId);

    @Mapping(target = "instId", source = "instId")
    @Mapping(target = "ordId", source = "order.externalId")
    @Mapping(target = "clOrdId", source = "order.internalId")
    CancelOrderOkxRequest domainToCancelRequest(Order order, String instId);

    /**
     * OKX ack → {@link ExchangeAck}. {@code code}/{@code message} —
     * per-order {@code sCode}/{@code sMsg}; если они пусты (наблюдалось
     * на реджекте, находка F1), падаем на top-level {@code code}/{@code msg}
     * ответа, чтобы ack не нёс null на реджекте.
     */
    @Mapping(target = "externalId", source = "ack.ordId")
    @Mapping(target = "internalId", source = "ack.clOrdId")
    @Mapping(target = "code", expression = "java(StringUtils.firstNonBlank(ack.getsCode(), topLevelCode))")
    @Mapping(target = "message", expression = "java(StringUtils.firstNonBlank(ack.getsMsg(), topLevelMessage))")
    @Mapping(target = "success", source = "ack.sCode", qualifiedByName = "okxAckSuccess")
    ExchangeAck integrationToAck(OrderAckOkxResponse ack, String topLevelCode, String topLevelMessage);

    /** OKX exec ordType из доменного Order: цена задана → limit, иначе market. */
    default String resolveOrdType(Order order) {
        return nonNull(order.getPrice()) ? Constants.Okx.ORD_TYPE_LIMIT : Constants.Okx.ORD_TYPE_MARKET;
    }
}
