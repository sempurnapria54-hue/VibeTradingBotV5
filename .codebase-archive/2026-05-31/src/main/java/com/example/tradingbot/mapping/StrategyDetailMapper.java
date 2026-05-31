package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStep;
import com.example.tradingbot.persistence.model.strategy.StrategyDetailsEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyStepEntity;
import com.example.tradingbot.rest.model.strategy.StrategyDetailsModel;
import com.example.tradingbot.rest.model.strategy.StrategyStepModel;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mapper(componentModel = "spring", uses = StrategyStepMapper.class)
public abstract class StrategyDetailMapper {

    @Autowired
    protected StrategyStepMapper strategyStepMapper;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "strategyId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "externalCreatedAt", ignore = true)
    @Mapping(target = "externalModifiedAt", ignore = true)
    public abstract StrategyDetails restToDomain(StrategyDetailsModel source);

    public abstract StrategyDetailsModel domainToRest(StrategyDetails source);

    public abstract Map<Deal.Status, List<StrategyStep>> restToDomain(Map<String, List<StrategyStepModel>> source);

    public abstract Map<String, List<StrategyStepModel>> domainToRest(Map<Deal.Status, List<StrategyStep>> source);

    @Mapping(target = "strategyId", expression = "java(resolveStrategyId(source))")
    @Mapping(target = "stepsByStatus", source = "steps")
    public abstract StrategyDetails dataToDomain(StrategyDetailsEntity source);

    @Mapping(target = "strategy", ignore = true)
    @Mapping(target = "steps", source = "stepsByStatus")
    public abstract StrategyDetailsEntity domainToData(StrategyDetails source);

    protected Map<Deal.Status, List<StrategyStep>> dataToDomain(List<StrategyStepEntity> source) {
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
                                                            .filter(item -> Objects.equals(requireDataDealStatus(item), status))
                                                            .sorted(Comparator.comparingInt(this::requireStepIndex))
                                                            .toList();

            if (stepsForStatus.isEmpty()) {
                continue;
            }

            List<StrategyStep> mappedSteps = new ArrayList<>();
            for (StrategyStepEntity stepEntity : stepsForStatus) {
                mappedSteps.add(strategyStepMapper.dataToDomain(stepEntity));
            }
            result.put(status, mappedSteps);
        }

        return result;
    }

    protected List<StrategyStepEntity> domainToData(Map<Deal.Status, List<StrategyStep>> source) {
        if (Objects.isNull(source)) {
            return null;
        }

        List<StrategyStepEntity> result = new ArrayList<>();
        if (source.isEmpty()) {
            return result;
        }

        List<Deal.Status> statuses = new ArrayList<>(source.keySet());
        statuses.sort(Comparator.comparingInt(Enum::ordinal));

        for (Deal.Status status : statuses) {
            if (Objects.isNull(status)) {
                throw new IllegalArgumentException("StrategyDetails.stepsByStatus contains null status");
            }

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

                StrategyStepEntity stepEntity = strategyStepMapper.domainToData(step);
                stepEntity.setDealStatus(status.name());
                stepEntity.setStepIndex(index);
                result.add(stepEntity);
            }
        }

        return result;
    }

    @AfterMapping
    protected void linkSteps(@MappingTarget StrategyDetailsEntity target) {
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

    protected Long resolveStrategyId(StrategyDetailsEntity source) {
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

    protected int requireStepIndex(StrategyStepEntity source) {
        if (Objects.isNull(source)) {
            throw new IllegalArgumentException("StrategyStepEntity is null");
        }

        if (Objects.isNull(source.getStepIndex())) {
            throw new IllegalArgumentException("StrategyStepEntity.stepIndex is null for id " + source.getId());
        }

        return source.getStepIndex();
    }

    protected Deal.Status requireDataDealStatus(StrategyStepEntity source) {
        if (Objects.isNull(source) || Objects.isNull(source.getDealStatus()) || source.getDealStatus().isBlank()) {
            throw new IllegalArgumentException("StrategyStepEntity.dealStatus is blank");
        }

        return Deal.Status.valueOf(source.getDealStatus());
    }
}
