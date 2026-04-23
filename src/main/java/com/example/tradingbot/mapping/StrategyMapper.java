package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.trade.strategy.StopLossSettings;
import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.trade.strategy.StrategyCondition;
import com.example.tradingbot.domain.model.trade.strategy.StrategyConditionRule;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.model.trade.strategy.StrategyOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPositionAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPricePlacement;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStep;
import com.example.tradingbot.domain.model.trade.strategy.TrailingSettings;
import com.example.tradingbot.persistence.model.strategy.StopLossSettingsEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyAlgoOrderActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyAttachedProtectionSettingsEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyConditionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyConditionRuleEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyDetailsEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyOrderActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyPositionActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyPricePlacementEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyStepEntity;
import com.example.tradingbot.persistence.model.strategy.TrailingSettingsEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface StrategyMapper {

    @Mapping(target = "details", source = "detailEntities")
    Strategy dataToDomain(StrategyEntity source);

    @Mapping(target = "detailEntities", source = "details")
    StrategyEntity domainToData(Strategy source);

    @Mapping(target = "strategyId", expression = "java(resolveStrategyId(source))")
    @Mapping(target = "stepsByStatus", source = "steps")
    StrategyDetails dataToDomain(StrategyDetailsEntity source);

    @Mapping(target = "strategy", ignore = true)
    @Mapping(target = "steps", source = "stepsByStatus")
    StrategyDetailsEntity domainToData(StrategyDetails source);

    StrategyStep dataToDomain(StrategyStepEntity source);

    @Mapping(target = "strategyDetails", ignore = true)
    @Mapping(target = "strategyDetailsId", ignore = true)
    @Mapping(target = "dealStatus", ignore = true)
    @Mapping(target = "stepIndex", ignore = true)
    StrategyStepEntity domainToData(StrategyStep source);

    StrategyCondition dataToDomain(StrategyConditionEntity source);

    StrategyConditionEntity domainToData(StrategyCondition source);

    StrategyConditionRule dataToDomain(StrategyConditionRuleEntity source);

    StrategyConditionRuleEntity domainToData(StrategyConditionRule source);

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

    default Map<Deal.Status, List<StrategyStep>> dataToDomain(List<StrategyStepEntity> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        Map<Deal.Status, List<StrategyStep>> result = new LinkedHashMap<>();
        if (source.isEmpty()) {
            return result;
        }

        for (Deal.Status status : Deal.Status.values()) {
            List<StrategyStepEntity> stepsForStatus = source.stream()
                                                            .filter(Objects::nonNull)
                                                            .filter(item -> Objects.equals(resolveDealStatus(item.getDealStatus()),
                                                                                           status))
                                                            .sorted(Comparator.comparingInt(this::requireStepIndex))
                                                            .toList();

            if (stepsForStatus.isEmpty()) {
                continue;
            }

            List<StrategyStep> mappedSteps = new ArrayList<>();
            for (StrategyStepEntity stepEntity : stepsForStatus) {
                mappedSteps.add(dataToDomain(stepEntity));
            }
            result.put(status, mappedSteps);
        }

        return result;
    }

    default List<StrategyStepEntity> domainToData(Map<Deal.Status, List<StrategyStep>> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyStepEntity> result = new ArrayList<>();
        if (source.isEmpty()) {
            return result;
        }

        List<Deal.Status> statuses = new ArrayList<>();
        for (Deal.Status status : source.keySet()) {
            if (Objects.isNull(status)) {
                throw new IllegalArgumentException("StrategyDetails.stepsByStatus contains null status");
            }
            statuses.add(status);
        }
        statuses.sort(Comparator.comparingInt(Enum::ordinal));

        for (Deal.Status status : statuses) {
            List<StrategyStep> steps = source.get(status);
            if (Objects.isNull(steps)) {
                throw new IllegalArgumentException(
                        "StrategyDetails.stepsByStatus contains null step list for status " + status
                );
            }

            if (steps.isEmpty()) {
                throw new IllegalArgumentException(
                        "StrategyDetails.stepsByStatus contains empty step list for status " + status
                                + ", which cannot be stored without data loss"
                );
            }

            for (int index = 0; index < steps.size(); index++) {
                StrategyStep step = steps.get(index);
                if (Objects.isNull(step)) {
                    throw new IllegalArgumentException(
                            "StrategyDetails.stepsByStatus contains null step for status " + status
                    );
                }

                StrategyStepEntity stepEntity = domainToData(step);
                stepEntity.setDealStatus(status.name());
                stepEntity.setStepIndex(index);
                result.add(stepEntity);
            }
        }

        return result;
    }

    default List<StrategyAction> dataToDomainActions(List<StrategyActionEntity> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyAction> result = new ArrayList<>();
        for (StrategyActionEntity actionEntity : source) {
            if (Objects.isNull(actionEntity)) {
                throw new IllegalArgumentException("StrategyStepEntity.actions contains null action");
            }
            result.add(dataToDomain(actionEntity));
        }
        return result;
    }

    default List<StrategyActionEntity> domainToDataActions(List<StrategyAction> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyActionEntity> result = new ArrayList<>();
        for (StrategyAction action : source) {
            if (Objects.isNull(action)) {
                throw new IllegalArgumentException("StrategyStep.actions contains null action");
            }
            result.add(domainToData(action));
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

        throw new IllegalArgumentException(
                "Unsupported StrategyActionEntity type: " + source.getClass().getName()
        );
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

        throw new IllegalArgumentException(
                "Unsupported StrategyAction type: " + source.getClass().getName()
        );
    }

    @AfterMapping
    default void linkDetails(@MappingTarget StrategyEntity target) {
        if (Objects.isNull(target) || Objects.isNull(target.getDetailEntities())) {
            return;
        }

        for (StrategyDetailsEntity detailEntity : target.getDetailEntities()) {
            if (Objects.isNull(detailEntity)) {
                continue;
            }

            detailEntity.setStrategy(target);
            if (Objects.nonNull(target.getId())) {
                detailEntity.setStrategyId(target.getId());
            }
            linkSteps(detailEntity);
        }
    }

    @AfterMapping
    default void linkSteps(@MappingTarget StrategyDetailsEntity target) {
        if (Objects.isNull(target) || Objects.isNull(target.getSteps())) {
            return;
        }

        for (StrategyStepEntity stepEntity : target.getSteps()) {
            if (Objects.isNull(stepEntity)) {
                continue;
            }

            stepEntity.setStrategyDetails(target);
            if (Objects.nonNull(target.getId())) {
                stepEntity.setStrategyDetailsId(target.getId());
            }
        }
    }

    default Long resolveStrategyId(StrategyDetailsEntity source) {
        if (Objects.isNull(source)) {
            return null;
        }

        if (Objects.nonNull(source.getStrategyId())) {
            return source.getStrategyId();
        }

        if (Objects.nonNull(source.getStrategy())) {
            return source.getStrategy().getId();
        }

        return null;
    }

    default Deal.Status resolveDealStatus(String source) {
        if (Objects.isNull(source) || source.isBlank()) {
            throw new IllegalArgumentException("StrategyStepEntity.dealStatus is blank");
        }

        try {
            return Deal.Status.valueOf(source);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported StrategyStepEntity.dealStatus value: " + source,
                    exception
            );
        }
    }

    default int requireStepIndex(StrategyStepEntity source) {
        if (Objects.isNull(source)) {
            throw new IllegalArgumentException("StrategyStepEntity is null");
        }

        if (Objects.isNull(source.getStepIndex())) {
            throw new IllegalArgumentException("StrategyStepEntity.stepIndex is null for id " + source.getId());
        }

        return source.getStepIndex();
    }
}
