package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.trade.strategy.StopLossSettings;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.trade.strategy.StrategyOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPositionAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPricePlacement;
import com.example.tradingbot.domain.model.trade.strategy.TrailingSettings;
import com.example.tradingbot.persistence.model.strategy.StopLossSettingsEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyAlgoOrderActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyAttachedProtectionSettingsEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyOrderActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyPositionActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyPricePlacementEntity;
import com.example.tradingbot.persistence.model.strategy.TrailingSettingsEntity;
import com.example.tradingbot.rest.model.strategy.StopLossSettingsModel;
import com.example.tradingbot.rest.model.strategy.StrategyActionModel;
import com.example.tradingbot.rest.model.strategy.StrategyAlgoOrderActionModel;
import com.example.tradingbot.rest.model.strategy.StrategyAttachedProtectionSettingsModel;
import com.example.tradingbot.rest.model.strategy.StrategyOrderActionModel;
import com.example.tradingbot.rest.model.strategy.StrategyPositionActionModel;
import com.example.tradingbot.rest.model.strategy.StrategyPricePlacementModel;
import com.example.tradingbot.rest.model.strategy.TrailingSettingsModel;
import org.mapstruct.Mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface StrategyActionMapper {

    default StrategyAction restToDomain(StrategyActionModel source) {
        if (Objects.isNull(source)) {
            return null;
        }

        if (source instanceof StrategyOrderActionModel orderActionModel) {
            return restToDomain(orderActionModel);
        }

        if (source instanceof StrategyAlgoOrderActionModel algoOrderActionModel) {
            return restToDomain(algoOrderActionModel);
        }

        if (source instanceof StrategyPositionActionModel positionActionModel) {
            return restToDomain(positionActionModel);
        }

        throw new IllegalArgumentException("Unsupported strategy action request type: " + source.getClass().getName());
    }

    default List<StrategyAction> restToDomain(List<StrategyActionModel> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyAction> result = new ArrayList<>();
        for (StrategyAction action : source.stream().map(this::restToDomain).toList()) {
            result.add(action);
        }
        return result;
    }

    default StrategyActionModel domainToRest(StrategyAction source) {
        if (Objects.isNull(source)) {
            return null;
        }

        if (source instanceof StrategyOrderAction orderAction) {
            return domainToRest(orderAction);
        }

        if (source instanceof StrategyAlgoOrderAction algoOrderAction) {
            return domainToRest(algoOrderAction);
        }

        if (source instanceof StrategyPositionAction positionAction) {
            return domainToRest(positionAction);
        }

        throw new IllegalArgumentException("Unsupported strategy action domain type: " + source.getClass().getName());
    }

    default List<StrategyActionModel> domainToRest(List<StrategyAction> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyActionModel> result = new ArrayList<>();
        for (StrategyActionModel action : source.stream().map(this::domainToRest).toList()) {
            result.add(action);
        }
        return result;
    }

    default StrategyAction dataToDomain(StrategyActionEntity source) {
        if (Objects.isNull(source)) {
            return null;
        }

        if (source instanceof StrategyOrderActionEntity orderActionEntity) {
            return dataToDomain(orderActionEntity);
        }

        if (source instanceof StrategyAlgoOrderActionEntity algoOrderActionEntity) {
            return dataToDomain(algoOrderActionEntity);
        }

        if (source instanceof StrategyPositionActionEntity positionActionEntity) {
            return dataToDomain(positionActionEntity);
        }

        throw new IllegalArgumentException("Unsupported StrategyActionEntity type: " + source.getClass().getName());
    }

    default List<StrategyAction> dataToDomain(List<StrategyActionEntity> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyAction> result = new ArrayList<>();
        for (StrategyAction action : source.stream().map(this::dataToDomain).toList()) {
            result.add(action);
        }
        return result;
    }

    default StrategyActionEntity domainToData(StrategyAction source) {
        if (Objects.isNull(source)) {
            return null;
        }

        if (source instanceof StrategyOrderAction orderAction) {
            return domainToData(orderAction);
        }

        if (source instanceof StrategyAlgoOrderAction algoOrderAction) {
            return domainToData(algoOrderAction);
        }

        if (source instanceof StrategyPositionAction positionAction) {
            return domainToData(positionAction);
        }

        throw new IllegalArgumentException("Unsupported StrategyAction type: " + source.getClass().getName());
    }

    default List<StrategyActionEntity> domainToData(List<StrategyAction> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyActionEntity> result = new ArrayList<>();
        for (StrategyActionEntity action : source.stream().map(this::domainToData).toList()) {
            result.add(action);
        }
        return result;
    }

    StrategyOrderAction restToDomain(StrategyOrderActionModel source);

    StrategyOrderActionModel domainToRest(StrategyOrderAction source);

    StrategyAlgoOrderAction restToDomain(StrategyAlgoOrderActionModel source);

    StrategyAlgoOrderActionModel domainToRest(StrategyAlgoOrderAction source);

    StrategyPositionAction restToDomain(StrategyPositionActionModel source);

    StrategyPositionActionModel domainToRest(StrategyPositionAction source);

    StrategyPricePlacement restToDomain(StrategyPricePlacementModel source);

    StrategyPricePlacementModel domainToRest(StrategyPricePlacement source);

    StrategyAttachedProtectionSettings restToDomain(StrategyAttachedProtectionSettingsModel source);

    StrategyAttachedProtectionSettingsModel domainToRest(StrategyAttachedProtectionSettings source);

    StopLossSettings restToDomain(StopLossSettingsModel source);

    StopLossSettingsModel domainToRest(StopLossSettings source);

    TrailingSettings restToDomain(TrailingSettingsModel source);

    TrailingSettingsModel domainToRest(TrailingSettings source);

    StrategyOrderAction dataToDomain(StrategyOrderActionEntity source);

    StrategyOrderActionEntity domainToData(StrategyOrderAction source);

    StrategyAlgoOrderAction dataToDomain(StrategyAlgoOrderActionEntity source);

    StrategyAlgoOrderActionEntity domainToData(StrategyAlgoOrderAction source);

    StrategyPositionAction dataToDomain(StrategyPositionActionEntity source);

    StrategyPositionActionEntity domainToData(StrategyPositionAction source);

    StrategyPricePlacement dataToDomain(StrategyPricePlacementEntity source);

    StrategyPricePlacementEntity domainToData(StrategyPricePlacement source);

    StrategyAttachedProtectionSettings dataToDomain(StrategyAttachedProtectionSettingsEntity source);

    StrategyAttachedProtectionSettingsEntity domainToData(StrategyAttachedProtectionSettings source);

    StopLossSettings dataToDomain(StopLossSettingsEntity source);

    StopLossSettingsEntity domainToData(StopLossSettings source);

    TrailingSettings dataToDomain(TrailingSettingsEntity source);

    TrailingSettingsEntity domainToData(TrailingSettings source);
}
