package com.example.tradingbot.domain.service.strategy;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Прикладные операции стратегии (docs/lifecycles/Strategy.md):
 * создание полного immutable-дерева (CREATED) и административные
 * переходы статуса через PUT (activate / inactivate / delete).
 * Правки правил на месте нет — изменение стратегии = новая стратегия.
 * Семантика готовности к запуску — 422 (нарушение переходов
 * lifecycle, вторая ACTIVE на инструмент); коды зафиксированы
 * decision'ом strategy-materialization-and-validation, общая
 * error-конвенция — TBD (codestyle §Обработка ошибок).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyDataService strategyDataService;
    private final InstrumentDataService instrumentDataService;

    /** Создаёт стратегию в CREATED; инструмент резолвится по instrumentInternalId. */
    public Strategy create(Strategy strategy, String instrumentInternalId) {
        strategy.setInstrumentId(instrumentDataService.getRequiredIdByInternalId(instrumentInternalId));
        strategy.setStatus(Strategy.Status.CREATED);
        return strategyDataService.save(strategy);
    }

    public Strategy getRequiredByInternalIdWithTree(String internalId) {
        return strategyDataService.getRequiredByInternalIdWithTree(internalId);
    }

    /**
     * Административный переход статуса. Допустимость перехода проверяет
     * сама модель (rich-модель); активация дополнительно держит
     * инвариант «одна ACTIVE на инструмент» (страховка в БД — частичный
     * UNIQUE-индекс).
     */
    public Strategy updateStatus(String internalId, Strategy.Status target) {
        Strategy strategy = strategyDataService.getRequiredByInternalId(internalId);
        if (isFalse(strategy.canTransitionTo(target))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Status transition not allowed: " + strategy.getStatus() + " -> " + target);
        }
        if (Objects.equals(target, Strategy.Status.ACTIVE)
                && isTrue(strategyDataService.existsActiveByInstrumentId(strategy.getInstrumentId()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Instrument already has an ACTIVE strategy");
        }
        strategyDataService.updateStatus(internalId, target);
        log.info("Strategy status updated: internalId={}, {} -> {}", internalId, strategy.getStatus(), target);
        return strategyDataService.getRequiredByInternalIdWithTree(internalId);
    }
}
