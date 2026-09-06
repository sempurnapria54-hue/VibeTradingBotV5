package com.example.strategies.domain.service;

import com.example.strategies.domain.event.OutboxWriter;
import com.example.strategies.persistence.model.StrategyEntity;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Пишет переход статуса определения и его событие <b>одной
 * транзакцией</b> (docs/architecture/contracts.md §«У каждого класса
 * события назван писатель, и он же писатель решения»).
 *
 * <p><b>Отдельный компонент, а не метод соседа, — по механике, а не по
 * вкусу.</b> Транзакцию открывает прокси Spring на входе в бин; метод,
 * вызванный изнутри того же бина, прокси минует, и аннотация транзакции
 * на нём молча не работает. Переход и строка outbox тогда легли бы
 * порознь — ровно то, против чего требование атомарности заведено.
 *
 * <p><b>Сети в этой транзакции нет.</b> Чужие операнды добывает
 * вызывающий ДО входа сюда: сетевой вызов внутри транзакции удерживал бы
 * соединение пула, а на отказе соседа транзакция висела бы до таймаута
 * (docs/rules/strategy-validation.md §«Что проверяется на активации»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyStatusWriter {

    private final StrategyDataService strategyDataService;
    private final OutboxWriter outboxWriter;

    /**
     * Переставить статус и записать событие.
     *
     * @param definition определение, чей статус переставляется
     * @param target     целевой статус
     * @param content    содержимое события: снимок дерева у активации,
     *                   идентичности у прочих переходов
     */
    @Transactional
    public Strategy commit(Strategy definition, Strategy.Status target, Object content) {
        StrategyEntity entity =
                strategyDataService.getRequiredEntityByInternalId(definition.getInternalId());
        strategyDataService.applyStatus(entity, target);
        outboxWriter.write(definition.getTenantId(), eventType(target), content);
        definition.setStatus(target);
        log.info("Strategy definition moved internalId={} status={}", definition.getInternalId(), target);
        return definition;
    }

    /**
     * Класс события по целевому статусу.
     *
     * <p>Перечень закрыт матрицей переходов: {@code CREATED} целью
     * перехода не бывает — его ставит создание, и события у него нет,
     * потому что черновик потребителю не адресован.
     */
    private StrategyEventType eventType(Strategy.Status target) {
        return switch (target) {
            case ACTIVE -> StrategyEventType.STRATEGY_ACTIVATED;
            case INACTIVE -> StrategyEventType.STRATEGY_DEACTIVATED;
            case DELETED -> StrategyEventType.STRATEGY_DELETED;
            case CREATED -> throw new IllegalStateException(
                    "CREATED is set by creation only and never emitted as a transition");
        };
    }
}
