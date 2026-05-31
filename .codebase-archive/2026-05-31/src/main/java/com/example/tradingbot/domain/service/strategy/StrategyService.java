package com.example.tradingbot.domain.service.strategy;

import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStatus;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStep;
import com.example.tradingbot.persistence.service.StrategyDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyDataService strategyDataService;
    private final StrategyValidator strategyValidator;

    /**
     * Получить активную стратегию по инструменту.
     * <p>
     * Если активной стратегии нет, сервис должен
     * выбросить доменное исключение.
     */
    public Strategy getActiveStrategyRequired(Long instrumentId) {
        return strategyDataService.findRequiredActiveByInstrumentId(instrumentId);
    }

    @Transactional
    public Strategy createStrategy(Strategy strategy) {
        strategyValidator.validateForCreate(strategy);
        assignMissingActionIds(strategy);
        strategy.setInternalId(UUID.randomUUID().toString());
        strategy.setStatus(StrategyStatus.CREATED);
        return strategyDataService.save(strategy);
    }

    public Strategy getByInternalId(String internalId) {
        return strategyDataService.findRequiredByInternalId(internalId);
    }

    @Transactional
    public Strategy activate(String internalId) {
        Strategy strategy = strategyDataService.findRequiredByInternalId(internalId);
        if (Objects.equals(strategy.getStatus(), StrategyStatus.ACTIVE)) {
            return strategy;
        }

        strategyValidator.validateStatusTransition(strategy, StrategyStatus.ACTIVE);
        strategyValidator.validateForActivation(strategy);

        Strategy activeStrategy = strategyDataService.findActiveByInstrumentId(strategy.getInstrumentId());
        if (nonNull(activeStrategy) && isFalse(Objects.equals(activeStrategy.getInternalId(), strategy.getInternalId()))) {
            strategyValidator.validateStatusTransition(activeStrategy, StrategyStatus.INACTIVE);
            activeStrategy.setStatus(StrategyStatus.INACTIVE);
            strategyDataService.save(activeStrategy);
        }

        strategy.setStatus(StrategyStatus.ACTIVE);
        return strategyDataService.save(strategy);
    }

    @Transactional
    public Strategy inactivate(String internalId) {
        Strategy strategy = strategyDataService.findRequiredByInternalId(internalId);
        if (Objects.equals(strategy.getStatus(), StrategyStatus.INACTIVE)) {
            return strategy;
        }

        strategyValidator.validateStatusTransition(strategy, StrategyStatus.INACTIVE);
        strategy.setStatus(StrategyStatus.INACTIVE);
        return strategyDataService.save(strategy);
    }

    @Transactional
    public Strategy delete(String internalId) {
        Strategy strategy = strategyDataService.findRequiredByInternalId(internalId);
        if (Objects.equals(strategy.getStatus(), StrategyStatus.DELETED)) {
            return strategy;
        }

        strategyValidator.validateStatusTransition(strategy, StrategyStatus.DELETED);
        strategy.setStatus(StrategyStatus.DELETED);
        return strategyDataService.save(strategy);
    }

    private void assignMissingActionIds(Strategy strategy) {
        if (Objects.isNull(strategy) || Objects.isNull(strategy.getDetails())) {
            return;
        }

        Set<Long> usedIds = collectActionIds(strategy.getDetails());
        long nextId = 1L;

        for (StrategyDetails detail : strategy.getDetails()) {
            if (Objects.isNull(detail) || Objects.isNull(detail.getStepsByStatus())) {
                continue;
            }

            for (List<StrategyStep> steps : detail.getStepsByStatus().values()) {
                if (Objects.isNull(steps)) {
                    continue;
                }

                for (StrategyStep step : steps) {
                    if (Objects.isNull(step) || Objects.isNull(step.getActions())) {
                        continue;
                    }

                    for (StrategyAction action : step.getActions()) {
                        if (Objects.isNull(action) || Objects.nonNull(action.getId())) {
                            continue;
                        }

                        while (usedIds.contains(nextId)) {
                            nextId++;
                        }
                        action.setId(nextId);
                        usedIds.add(nextId);
                        nextId++;
                    }
                }
            }
        }
    }

    private Set<Long> collectActionIds(List<StrategyDetails> details) {
        Set<Long> usedIds = new HashSet<>();
        for (StrategyDetails detail : details) {
            if (Objects.isNull(detail) || Objects.isNull(detail.getStepsByStatus())) {
                continue;
            }

            for (List<StrategyStep> steps : detail.getStepsByStatus().values()) {
                if (Objects.isNull(steps)) {
                    continue;
                }

                for (StrategyStep step : steps) {
                    if (Objects.isNull(step) || Objects.isNull(step.getActions())) {
                        continue;
                    }

                    for (StrategyAction action : step.getActions()) {
                        if (Objects.nonNull(action) && Objects.nonNull(action.getId())) {
                            usedIds.add(action.getId());
                        }
                    }
                }
            }
        }

        return usedIds;
    }
}
