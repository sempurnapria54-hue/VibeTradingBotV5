package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingcore.domain.service.StrategyDefinitionApplier;
import com.example.tradingcore.exception.PoisonStrategyFactException;
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
 *
 * <p><b>Дедуп решает безопасная вставка отметки, а не проверка перед
 * ней</b> (docs/rules/idempotency-via-unique.md): здесь её исход задаётся
 * ответом {@code markConsumedIfAbsent}, а атомарность самой вставки держит
 * ключ базы и мерит чёрный ящик (группа {@code B8}).
 */
class StrategyDefinitionApplierTest {

    private static final String EVENT = "ev-0001";
    private static final String STRATEGY = "st-0001";

    private final StrategyDataService strategyDataService = mock(StrategyDataService.class);
    private final InboxDataService inboxDataService = mock(InboxDataService.class);

    private final StrategyDefinitionApplier applier =
            new StrategyDefinitionApplier(strategyDataService, inboxDataService);

    /** Первая активация заводит копию деревом. */
    @Test
    void theFirstActivationWritesTheTree() {
        firstDelivery(StrategyEventType.STRATEGY_ACTIVATED);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.empty());

        applier.applyActivated(EVENT, definition());

        verify(strategyDataService).saveTree(any());
        verify(inboxDataService).markConsumedIfAbsent(EVENT, StrategyEventType.STRATEGY_ACTIVATED.name());
    }

    /**
     * Повторная активация уже известного определения двигает только
     * статус: дерево неизменяемо, и его переписывание оборвало бы
     * закрепление детали у живой сделки.
     */
    @Test
    void aRepeatedActivationMovesTheStatusWithoutRewritingTheTree() {
        firstDelivery(StrategyEventType.STRATEGY_ACTIVATED);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.of(definition()));

        applier.applyActivated(EVENT, definition());

        verify(strategyDataService).applyStatus(STRATEGY, Strategy.Status.ACTIVE);
        verify(strategyDataService, never()).saveTree(any());
    }

    /**
     * Повтор доставки не применяется дважды: отметка по идентичности
     * события уже стоит, вставка поглощена, и следствия нет — ни
     * заведения копии, ни даже чтения её.
     */
    @Test
    void aRedeliveredEventIsNotAppliedTwice() {
        when(inboxDataService.markConsumedIfAbsent(EVENT, StrategyEventType.STRATEGY_ACTIVATED.name()))
                .thenReturn(false);

        applier.applyActivated(EVENT, definition());

        verify(strategyDataService, never()).findByInternalId(any());
        verify(strategyDataService, never()).saveTree(any());
        verify(strategyDataService, never()).applyStatus(any(), any());
    }

    /** Повтор доставки факта жизненного цикла статуса копии не двигает. */
    @Test
    void aRedeliveredLifecycleFactDoesNotMoveTheStatus() {
        when(inboxDataService.markConsumedIfAbsent(EVENT, StrategyEventType.STRATEGY_DELETED.name()))
                .thenReturn(false);

        applier.applyLifecycle(EVENT, StrategyEventType.STRATEGY_DELETED, STRATEGY);

        verify(strategyDataService, never()).findByInternalId(any());
        verify(strategyDataService, never()).applyStatus(any(), any());
    }

    /** Удаление двигает статус копии; строка копии при этом остаётся. */
    @Test
    void deletionMovesTheCopyStatusAndKeepsTheRow() {
        firstDelivery(StrategyEventType.STRATEGY_DELETED);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.of(definition()));

        applier.applyLifecycle(EVENT, StrategyEventType.STRATEGY_DELETED, STRATEGY);

        verify(strategyDataService).applyStatus(STRATEGY, Strategy.Status.DELETED);
        verify(inboxDataService).markConsumedIfAbsent(EVENT, StrategyEventType.STRATEGY_DELETED.name());
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
        firstDelivery(StrategyEventType.STRATEGY_DELETED);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.empty());

        applier.applyLifecycle(EVENT, StrategyEventType.STRATEGY_DELETED, STRATEGY);

        verify(strategyDataService, never()).applyStatus(any(), any());
        // Отметка ставится: событие обработано — следствия у него нет, и
        // повторная доставка искала бы ту же несуществующую копию.
        verify(inboxDataService).markConsumedIfAbsent(EVENT, StrategyEventType.STRATEGY_DELETED.name());
    }

    /**
     * Событие активации без идентичности определения роняет обработку
     * классом ОТРАВЛЕННОЙ записи.
     *
     * <p>Класс несущий: им одним обработчик отказа приёма отличает запись,
     * которую не исправит никакой повтор, от отложенного применения. Под
     * общим классом такая запись встала бы в бесконечный повтор и
     * остановила бы применение всех следующих фактов темы.
     */
    @Test
    void anActivationWithoutIdentityIsRefusedAsPoison() {
        assertThatThrownBy(() -> applier.applyActivated(EVENT, null))
                .isInstanceOf(PoisonStrategyFactException.class)
                .hasMessageContaining("carries no definition identity");
        verify(inboxDataService, never()).markConsumedIfAbsent(any(), any());
    }

    /**
     * Проекции счёта у ядра ещё нет — отказ всплывает КАК ЕСТЬ, а не
     * классом отравленной записи: его лечит время (тик синка проекций), и
     * применение откладывается, а не пропускается.
     */
    @Test
    void anUnresolvedProjectionIsNotPoisonAndPropagates() {
        firstDelivery(StrategyEventType.STRATEGY_ACTIVATED);
        when(strategyDataService.findByInternalId(STRATEGY)).thenReturn(Optional.empty());
        when(strategyDataService.saveTree(any()))
                .thenThrow(new IllegalArgumentException("ExchangeAccount not found: ea-0001"));

        assertThatThrownBy(() -> applier.applyActivated(EVENT, definition()))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(PoisonStrategyFactException.class);
    }

    /** Первая доставка: отметка по идентичности события ложится этим вызовом. */
    private void firstDelivery(StrategyEventType type) {
        when(inboxDataService.markConsumedIfAbsent(EVENT, type.name())).thenReturn(true);
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
