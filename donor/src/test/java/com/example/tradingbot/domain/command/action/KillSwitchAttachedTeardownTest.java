package com.example.tradingbot.domain.command.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.config.KillSwitchProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.integration.service.IntegrationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает teardown kill-switch со ВСТРОЕННОЙ защитой
 * (`docs/components/CancelAttachedProtectionExecutor.md` §«Тропы, на
 * которых снятие эмитится», `docs/components/KillSwitchExecutor.md`).
 *
 * <p>Несущее: при непустом наливе родителя встроенная защита
 * материализуется самостоятельной заявкой на бирже и <b>переживает
 * терминал родителя</b> (`docs/models/domain/core/Order.md` §«Встроенная
 * защита»). Перечень живых algo-заявок её не содержит — значит teardown,
 * обходящий только его, оставлял бы условную заявку на бирже и при этом
 * подтверждал flat. Оба следствия проверяются здесь: снятие эмитится и
 * flat не подтверждается, пока защита жива.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KillSwitchAttachedTeardownTest {

    private static final String INST_ID = "ETH-USDT-SWAP";

    @Mock
    private IntegrationService integrationService;
    @Mock
    private ServiceCommandExecutor serviceCommandExecutor;
    @Mock
    private DealContextService dealContextService;

    private KillSwitchExecutor executor;

    @BeforeEach
    void setUp() {
        KillSwitchProperties properties = new KillSwitchProperties();
        properties.setMaxTeardownAttempts(1);
        executor = new KillSwitchExecutor(integrationService, serviceCommandExecutor,
                dealContextService, properties);
    }

    @Test
    @DisplayName("Живая встроенная защита снимается teardown'ом, хотя её родитель терминален")
    void materializedAttachedProtectionIsCancelled() {
        Deal deal = dealWithAttachedProtection(AttachedAlgoOrder.Status.ACTIVE);

        executor.execute(context(deal));

        verify(integrationService, times(1))
                .cancelAttachedProtection(any(AttachedAlgoOrder.class), eq(INST_ID));
    }

    @Test
    @DisplayName("Flat не подтверждается, пока встроенная защита жива")
    void flatIsNotConfirmedWhileAttachedProtectionIsLive() {
        Deal deal = dealWithAttachedProtection(AttachedAlgoOrder.Status.ACTIVE);

        var result = executor.execute(context(deal));

        assertEquals(Boolean.FALSE, result.getSuccess());
        assertTrue(result.getMessage().contains("could not confirm flat"), result.getMessage());
    }

    @Test
    @DisplayName("Контроль: снятая встроенная защита ни отмены не требует, ни flat не держит")
    void terminalAttachedProtectionNeitherCancelledNorBlocking() {
        Deal deal = dealWithAttachedProtection(AttachedAlgoOrder.Status.CANCELED);

        var result = executor.execute(context(deal));

        verify(integrationService, never()).cancelAttachedProtection(any(), any());
        assertEquals(Boolean.TRUE, result.getSuccess());
    }

    /** Сделка с ТЕРМИНАЛЬНЫМ родителем, несущим встроенную защиту заданного статуса. */
    private Deal dealWithAttachedProtection(AttachedAlgoOrder.Status status) {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setId(11L);
        protection.setOrderId(5L);
        protection.setStatus(status);

        Order parent = new Order();
        parent.setId(5L);
        parent.setStatus(Order.Status.COMPLETED);
        parent.setAttachedAlgoOrders(List.of(protection));

        Deal deal = new Deal();
        deal.setId(3L);
        deal.setOrders(List.of(parent));
        deal.setAlgoOrders(List.of());
        deal.setPositions(List.of());
        return deal;
    }

    private DealContext context(Deal deal) {
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setExternalId(INST_ID);
        return DealContext.builder().deal(deal).instrument(instrument).build();
    }
}
