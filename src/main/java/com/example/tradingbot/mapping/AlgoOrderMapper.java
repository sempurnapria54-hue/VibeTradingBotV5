package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.ConditionExternalSnapshot;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.TrailingExternalSnapshot;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.TriggerExternalSnapshot;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.TriggerPriceExternalSnapshot;
import com.example.tradingbot.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.tradingbot.integration.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.tradingbot.integration.model.okx.response.OkxAlgoOrderResponse;
import com.example.tradingbot.persistence.model.algoorder.AlgoOrderEntity;
import com.example.tradingbot.util.Constants;
import com.example.tradingbot.util.OkxParse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link AlgoOrder} между слоями: domain ↔ persistence
 * (condition — JSONB через {@link RuntimeJsonConverter}), OKX response →
 * snapshot (плоские поля → дерево condition вручную), domain → OKX
 * request (place/cancel; ordType из conditionType, side/triggerType
 * lower-case через {@link OkxResponseConverter}), OKX ack →
 * {@link ExchangeAck}. См. docs/models/domain/core/AlgoOrder.md,
 * docs/models/mapping/AlgoOrder.md.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {RuntimeJsonConverter.class, OkxResponseConverter.class}, imports = Constants.class)
public interface AlgoOrderMapper {

    AlgoOrderEntity domainToPersistence(AlgoOrder algoOrder);

    AlgoOrder persistenceToDomain(AlgoOrderEntity entity);

    /**
     * Обновление полей AlgoOrder из снапшота (REFRESH-контур). internalId
     * и condition (дерево обновляется отдельно) не перетираются;
     * доменный status / closeReason применяет исполнитель через резолвер.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "condition", ignore = true)
    void updateFromSnapshot(AlgoOrderExternalSnapshot snapshot, @MappingTarget AlgoOrder algoOrder);

    /** OKX algo response → snapshot: плоские OKX-поля → дерево condition. */
    default AlgoOrderExternalSnapshot integrationToSnapshot(OkxAlgoOrderResponse response) {
        if (isNull(response)) {
            return null;
        }
        return AlgoOrderExternalSnapshot.builder()
                .internalId(response.getAlgoClOrdId())
                .externalId(response.getAlgoId())
                .externalStatus(response.getState())
                .failCode(response.getFailCode())
                .externalSize(OkxParse.decimal(response.getActualSz()))
                .externalPrice(OkxParse.decimal(response.getActualPx()))
                .externalTriggerTime(OkxParse.instant(response.getTriggerTime()))
                .linkedOrderExternalIds(response.getOrdIdList())
                .condition(toConditionSnapshot(response))
                .externalCreatedAt(OkxParse.offsetTime(response.getcTime()))
                .externalModifiedAt(OkxParse.offsetTime(response.getuTime()))
                .build();
    }

    private ConditionExternalSnapshot toConditionSnapshot(OkxAlgoOrderResponse response) {
        return ConditionExternalSnapshot.builder()
                .trigger(TriggerExternalSnapshot.builder()
                        .stopLoss(toTriggerPrice(response.getSlTriggerPxType(), response.getSlTriggerPx()))
                        .takeProfit(toTriggerPrice(response.getTpTriggerPxType(), response.getTpTriggerPx()))
                        .build())
                .trailing(TrailingExternalSnapshot.builder()
                        .activationPrice(toTriggerPrice(null, response.getActivePx()))
                        .externalPrice(OkxParse.decimal(response.getMoveTriggerPx()))
                        .build())
                .build();
    }

    private TriggerPriceExternalSnapshot toTriggerPrice(String externalType, String externalValue) {
        if (isBlank(externalType) && isBlank(externalValue)) {
            return null;
        }
        return TriggerPriceExternalSnapshot.builder()
                .externalType(externalType)
                .externalValue(OkxParse.decimal(externalValue))
                .build();
    }

    @Mapping(target = "instId", source = "instId")
    @Mapping(target = "tdMode", expression = "java(Constants.Okx.TD_MODE_ISOLATED)")
    @Mapping(target = "posSide", expression = "java(Constants.Okx.POS_SIDE_NET)")
    @Mapping(target = "algoClOrdId", source = "algoOrder.internalId")
    @Mapping(target = "side", source = "algoOrder.direction", qualifiedByName = "okxAlgoSide")
    @Mapping(target = "ordType", expression = "java(resolveAlgoOrdType(algoOrder.getConditionType()))")
    @Mapping(target = "sz", source = "algoOrder.size")
    @Mapping(target = "reduceOnly", source = "algoOrder.positionReducingOnly")
    @Mapping(target = "slTriggerPx", source = "algoOrder.condition.trigger.stopLoss.value")
    @Mapping(target = "slTriggerPxType", source = "algoOrder.condition.trigger.stopLoss.type",
            qualifiedByName = "okxTriggerType")
    @Mapping(target = "slOrdPx", expression = "java(stopLossMarketFlag(algoOrder))")
    @Mapping(target = "tpTriggerPx", source = "algoOrder.condition.trigger.takeProfit.value")
    @Mapping(target = "tpTriggerPxType", source = "algoOrder.condition.trigger.takeProfit.type",
            qualifiedByName = "okxTriggerType")
    @Mapping(target = "tpOrdPx", expression = "java(takeProfitMarketFlag(algoOrder))")
    @Mapping(target = "callbackRatio", source = "algoOrder.condition.trailing.trailingPercents")
    @Mapping(target = "callbackSpread", source = "algoOrder.condition.trailing.trailingStepValue")
    @Mapping(target = "activePx", source = "algoOrder.condition.trailing.activationPrice.value")
    PlaceAlgoOrderOkxRequest domainToPlaceRequest(AlgoOrder algoOrder, String instId);

    @Mapping(target = "instId", source = "instId")
    @Mapping(target = "algoId", source = "algoOrder.externalId")
    @Mapping(target = "algoClOrdId", source = "algoOrder.internalId")
    CancelAlgoOrderOkxRequest domainToCancelRequest(AlgoOrder algoOrder, String instId);

    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "internalId", source = "algoClOrdId")
    @Mapping(target = "code", source = "sCode")
    @Mapping(target = "message", source = "sMsg")
    @Mapping(target = "success", source = "sCode", qualifiedByName = "okxAckSuccess")
    ExchangeAck integrationToAck(AlgoOrderAckOkxResponse response);

    /** OKX algo ordType из conditionType (одностороннe). */
    default String resolveAlgoOrdType(AlgoOrder.ConditionType type) {
        return switch (type) {
            case STOP_LOSS, TAKE_PROFIT, PARTIAL_STOP_LOSS, PARTIAL_TAKE_PROFIT ->
                    Constants.Okx.ALGO_ORD_TYPE_CONDITIONAL;
            case OCO_FULL -> Constants.Okx.ALGO_ORD_TYPE_OCO;
            case TRAILING_PERCENTS, TRAILING_VALUE -> Constants.Okx.ALGO_ORD_TYPE_MOVE_STOP;
        };
    }

    /** SL исполняется market после trigger (slOrdPx=-1), если SL-нога задана. */
    default String stopLossMarketFlag(AlgoOrder algoOrder) {
        return hasStopLoss(algoOrder) ? Constants.Okx.MARKET_PRICE_FLAG : null;
    }

    /** TP исполняется market после trigger (tpOrdPx=-1), если TP-нога задана. */
    default String takeProfitMarketFlag(AlgoOrder algoOrder) {
        return hasTakeProfit(algoOrder) ? Constants.Okx.MARKET_PRICE_FLAG : null;
    }

    private boolean hasStopLoss(AlgoOrder algoOrder) {
        return nonNull(algoOrder.getCondition())
                && nonNull(algoOrder.getCondition().getTrigger())
                && nonNull(algoOrder.getCondition().getTrigger().getStopLoss())
                && nonNull(algoOrder.getCondition().getTrigger().getStopLoss().getValue());
    }

    private boolean hasTakeProfit(AlgoOrder algoOrder) {
        return nonNull(algoOrder.getCondition())
                && nonNull(algoOrder.getCondition().getTrigger())
                && nonNull(algoOrder.getCondition().getTrigger().getTakeProfit())
                && nonNull(algoOrder.getCondition().getTrigger().getTakeProfit().getValue());
    }
}
