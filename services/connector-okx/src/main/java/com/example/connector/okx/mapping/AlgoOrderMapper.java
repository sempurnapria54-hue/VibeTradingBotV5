package com.example.connector.okx.mapping;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.connector.okx.snapshot.AlgoOrderExternalSnapshot;
import com.example.connector.okx.snapshot.ConditionExternalSnapshot;
import com.example.connector.okx.snapshot.TrailingExternalSnapshot;
import com.example.connector.okx.snapshot.TriggerExternalSnapshot;
import com.example.connector.okx.snapshot.TriggerPriceExternalSnapshot;
import com.example.connector.okx.integration.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.connector.okx.integration.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.connector.okx.integration.model.okx.response.AlgoOrderOkxResponse;
import com.example.connector.okx.util.OkxConstants;
import com.example.connector.okx.util.OkxParse;
import org.apache.commons.lang3.StringUtils;
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
        uses = OkxResponseConverter.class, imports = {OkxConstants.class, StringUtils.class})
public interface AlgoOrderMapper {

    /**
     * Обновление полей AlgoOrder из снапшота (REFRESH-контур). internalId
     * и condition (дерево обновляется отдельно) не перетираются;
     * доменный status / closeReason применяет исполнитель через резолвер.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "condition", ignore = true)
    void updateFromSnapshot(AlgoOrderExternalSnapshot snapshot, @MappingTarget AlgoOrder algoOrder);

    /**
     * Снапшот → доменная условная заявка, СОЗДАНИЕМ: коннектор отдаёт
     * модель, а не доливает чужую. Доменный статус резолвит вызывающий.
     */
    AlgoOrder snapshotToDomain(AlgoOrderExternalSnapshot snapshot);

    /** OKX algo response → snapshot: плоские OKX-поля → дерево condition. */
    default AlgoOrderExternalSnapshot integrationToSnapshot(AlgoOrderOkxResponse response) {
        if (isNull(response)) {
            return null;
        }
        return AlgoOrderExternalSnapshot.builder()
                .externalInstrumentId(response.getInstId())
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

    private ConditionExternalSnapshot toConditionSnapshot(AlgoOrderOkxResponse response) {
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

    @Mapping(target = "tdMode", expression = "java(OkxConstants.TD_MODE_ISOLATED)")
    @Mapping(target = "posSide", expression = "java(OkxConstants.POS_SIDE_NET)")
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

    @Mapping(target = "algoId", source = "algoOrder.externalId")
    @Mapping(target = "algoClOrdId", source = "algoOrder.internalId")
    CancelAlgoOrderOkxRequest domainToCancelRequest(AlgoOrder algoOrder, String instId);

    /**
     * OKX algo ack → {@link ExchangeAck}. {@code code}/{@code message} —
     * per-order {@code sCode}/{@code sMsg}; если они пусты (наблюдалось
     * на реджекте, находка F1), падаем на top-level {@code code}/{@code msg}
     * ответа, чтобы ack не нёс null на реджекте.
     */
    @Mapping(target = "externalId", source = "ack.algoId")
    @Mapping(target = "internalId", source = "ack.algoClOrdId")
    @Mapping(target = "code", expression = "java(StringUtils.firstNonBlank(ack.getsCode(), topLevelCode))")
    @Mapping(target = "message", expression = "java(StringUtils.firstNonBlank(ack.getsMsg(), topLevelMessage))")
    @Mapping(target = "success", source = "ack.sCode", qualifiedByName = "okxAckSuccess")
    ExchangeAck integrationToAck(AlgoOrderAckOkxResponse ack, String topLevelCode, String topLevelMessage);

    /** OKX algo ordType из conditionType (одностороннe). */
    default String resolveAlgoOrdType(AlgoOrder.ConditionType type) {
        return switch (type) {
            case STOP_LOSS, TAKE_PROFIT, PARTIAL_STOP_LOSS, PARTIAL_TAKE_PROFIT ->
                    OkxConstants.ALGO_ORD_TYPE_CONDITIONAL;
            case OCO_FULL -> OkxConstants.ALGO_ORD_TYPE_OCO;
            case TRAILING_PERCENTS, TRAILING_VALUE -> OkxConstants.ALGO_ORD_TYPE_MOVE_STOP;
        };
    }

    /** SL исполняется market после trigger (slOrdPx=-1), если SL-нога задана. */
    default String stopLossMarketFlag(AlgoOrder algoOrder) {
        return hasStopLoss(algoOrder) ? OkxConstants.MARKET_PRICE_FLAG : null;
    }

    /** TP исполняется market после trigger (tpOrdPx=-1), если TP-нога задана. */
    default String takeProfitMarketFlag(AlgoOrder algoOrder) {
        return hasTakeProfit(algoOrder) ? OkxConstants.MARKET_PRICE_FLAG : null;
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
