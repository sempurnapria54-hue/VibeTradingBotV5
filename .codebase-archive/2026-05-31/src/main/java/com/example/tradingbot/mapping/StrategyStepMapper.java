package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.trade.strategy.StrategyCondition;
import com.example.tradingbot.domain.model.trade.strategy.StrategyConditionRule;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStep;
import com.example.tradingbot.persistence.model.strategy.StrategyConditionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyConditionRuleEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyStepEntity;
import com.example.tradingbot.rest.model.strategy.StrategyConditionModel;
import com.example.tradingbot.rest.model.strategy.StrategyConditionRuleModel;
import com.example.tradingbot.rest.model.strategy.StrategyStepModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = StrategyActionMapper.class)
public interface StrategyStepMapper {

    StrategyStep restToDomain(StrategyStepModel source);

    java.util.List<StrategyStep> restToDomain(java.util.List<StrategyStepModel> source);

    StrategyStepModel domainToRest(StrategyStep source);

    java.util.List<StrategyStepModel> domainToRest(java.util.List<StrategyStep> source);

    StrategyCondition restToDomain(StrategyConditionModel source);

    StrategyConditionModel domainToRest(StrategyCondition source);

    StrategyConditionRule restToDomain(StrategyConditionRuleModel source);

    StrategyConditionRuleModel domainToRest(StrategyConditionRule source);

    StrategyStep dataToDomain(StrategyStepEntity source);

    java.util.List<StrategyStep> dataToDomain(java.util.List<StrategyStepEntity> source);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "strategyDetails", ignore = true)
    @Mapping(target = "strategyDetailsId", ignore = true)
    @Mapping(target = "dealStatus", ignore = true)
    @Mapping(target = "stepIndex", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "externalCreatedAt", ignore = true)
    @Mapping(target = "externalModifiedAt", ignore = true)
    StrategyStepEntity domainToData(StrategyStep source);

    java.util.List<StrategyStepEntity> domainToData(java.util.List<StrategyStep> source);

    StrategyCondition dataToDomain(StrategyConditionEntity source);

    StrategyConditionEntity domainToData(StrategyCondition source);

    StrategyConditionRule dataToDomain(StrategyConditionRuleEntity source);

    StrategyConditionRuleEntity domainToData(StrategyConditionRule source);
}
