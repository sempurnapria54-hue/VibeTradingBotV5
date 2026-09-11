package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.event.StrategyActivatedContent;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.event.StrategyLifecycleContent;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingcore.domain.service.StrategyDefinitionApplier;
import com.example.tradingcore.persistence.service.InboxDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Применение событий владельца определений к копии в базе ядра.
 *
 * <p><b>Что здесь проверяется по существу.</b> Три вещи, каждая из
 * которых, будучи нарушенной, ломает торговлю молча:
 *
 * <ul>
 *   <li><b>повтор доставки не применяется дважды</b> — реле публикует
 *       раньше, чем помечает опубликованное, поэтому дубль штатен
 *       (docs/rules/idempotency-via-unique.md);</li>
 *   <li><b>повторная активация НЕ переписывает дерево</b> — определение
 *       неизменяемо, а живая сделка закрепила его деталь: переписывание
 *       оборвало бы закрепление ровно там, где оно требуется
 *       (docs/models/domain/aggregate/Strategy.md §«Что у копии
 *       неизменяемо, а что состояние»);</li>
 *   <li><b>отметка обработки ставится только вместе со следствием</b> —
 *       отметка без следствия потеряла бы событие навсегда.</li>
 * </ul>
 */
class StrategyDefinitionApplierTest {

    private static final String EVENT = "ev-0001";
    private static final String STRATEGY = "st-0001";
    private static final String ACTOR = "holder";

    private final StrategyDataService strategyDataService = mock(StrategyDataService.class);
    private final InboxDataService inboxDataService = mock(InboxDataService.class);

    private final StrategyDefinitionApplier applier =
            new StrategyDefinitionApplier(strategyDataService, inboxDataService);

    /** Первая активация заводит копию деревом. */
    @Test
    void theFirstActivationWritesTheTree() {
        when(inboxDataService.isConsumed(EVENT)).thenReturn(false);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.empty());

        applier.applyActivated(EVENT, activation(definition()));

        verify(strategyDataService).saveTree(any());
        verify(inboxDataService).markConsumed(EVENT, StrategyEventType.STRATEGY_ACTIVATED.name());
    }

    /**
     * Повторная активация уже известного определения двигает только
     * статус: дерево неизменяемо, и его переписывание оборвало бы
     * закрепление детали у живой сделки.
     */
    @Test
    void aRepeatedActivationMovesTheStatusWithoutRewritingTheTree() {
        when(inboxDataService.isConsumed(EVENT)).thenReturn(false);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.of(definition()));

        applier.applyActivated(EVENT, activation(definition()));

        verify(strategyDataService).applyStatus(STRATEGY, Strategy.Status.ACTIVE);
        verify(strategyDataService, never()).saveTree(any());
    }

    /** Повтор доставки не применяется дважды: дедуп по идентичности события. */
    @Test
    void aRedeliveredEventIsNotAppliedTwice() {
        when(inboxDataService.isConsumed(EVENT)).thenReturn(true);

        applier.applyActivated(EVENT, activation(definition()));

        verify(strategyDataService, never()).saveTree(any());
        verify(strategyDataService, never()).applyStatus(any(), any());
        verify(inboxDataService, never()).markConsumed(any(), any());
    }

    /** Удаление двигает статус копии; строка копии при этом остаётся. */
    @Test
    void deletionMovesTheCopyStatusAndKeepsTheRow() {
        when(inboxDataService.isConsumed(EVENT)).thenReturn(false);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.of(definition()));

        applier.applyLifecycle(EVENT, StrategyEventType.STRATEGY_DELETED,
                new StrategyLifecycleContent(STRATEGY, "ea-0001", "in-0001", ACTOR));

        verify(strategyDataService).applyStatus(STRATEGY, Strategy.Status.DELETED);
        verify(inboxDataService).markConsumed(EVENT, StrategyEventType.STRATEGY_DELETED.name());
    }

    /**
     * Удаление определения, которое НИКОГДА не активировалось, копии не
     * ищет и обработку не роняет.
     *
     * <p>Тропа штатная и проходится в один ход: копия заводится только
     * активацией, а удалить владелец позволяет и из {@code CREATED}. Отказ
     * здесь занял бы партию циклом повторов и остановил бы применение
     * СЛЕДУЮЩИХ событий — тех, чьи копии подвинуть было нужно.
     */
    @Test
    void aLifecycleFactWithoutACopyIsConsumedWithoutEffect() {
        when(inboxDataService.isConsumed(EVENT)).thenReturn(false);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.empty());

        applier.applyLifecycle(EVENT, StrategyEventType.STRATEGY_DELETED,
                new StrategyLifecycleContent(STRATEGY, "ea-0001", "in-0001", ACTOR));

        verify(strategyDataService, never()).applyStatus(any(), any());
        // Отметка ставится: событие обработано — следствия у него нет, и
        // повторная доставка искала бы ту же несуществующую копию.
        verify(inboxDataService).markConsumed(EVENT, StrategyEventType.STRATEGY_DELETED.name());
    }

    /**
     * Событие активации без идентичности определения роняет обработку.
     *
     * <p>Пропуск оставил бы копию отсутствующей молча, и отбор входа не
     * нашёл бы стратегию, которую владелец считает активной.
     */
    @Test
    void anActivationWithoutIdentityIsRefused() {
        when(inboxDataService.isConsumed(EVENT)).thenReturn(false);

        assertThatThrownBy(() -> applier.applyActivated(EVENT, activation(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carries no definition identity");
        verify(inboxDataService, never()).markConsumed(any(), any());
    }

    /**
     * Содержимое активации: идентичности радиуса верхнего уровня плюс
     * снимок. Пустой снимок оставляет идентичности пустыми — так выглядит
     * событие, у которого определения нет вовсе.
     */
    private StrategyActivatedContent activation(Strategy definition) {
        return isNull(definition)
                ? new StrategyActivatedContent(null, null, null, ACTOR, null)
                : new StrategyActivatedContent(definition.getInternalId(),
                        definition.getExchangeAccountInternalId(),
                        definition.getInstrumentInternalId(), ACTOR, definition);
    }

    private Strategy definition() {
        Strategy definition = new Strategy();
        definition.setInternalId(STRATEGY);
        definition.setTenantId("tn-0001");
        definition.setExchangeAccountInternalId("ea-0001");
        definition.setInstrumentInternalId("in-0001");
        definition.setName("baseline");
        definition.setStatus(Strategy.Status.CREATED);
        return definition;
    }
}
