package com.example.tradingbot.domain.service.deal.state_machine;

import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.service.AlgoOrderService;
import com.example.tradingbot.domain.service.BalanceService;
import com.example.tradingbot.domain.service.OrderService;
import com.example.tradingbot.domain.service.PositionService;
import com.example.tradingbot.domain.service.deal.ExitService;
import com.example.tradingbot.domain.service.deal.KillSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ServiceCommandExecutor {

    private final PositionService positionService;

    private final BalanceService balanceService;

    private final OrderService orderService;

    private final AlgoOrderService algoOrderService;

    private final ExitService exitService;

    private final KillSwitchService killSwitchService;

    /**
     * Выполнить сервисные команды для текущего контекста сделки.
     * <p>
     * Важно:
     * - executor не принимает торговых решений;
     * - executor только маршрутизирует команды в соответствующие сервисы;
     * - после выполнения команд оркестратор обязан заново загрузить DealContext.
     */
    public void execute(DealContext context, List<ServiceCommandType> commands) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(commands);

        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        for (ServiceCommandType command : commands) {
            executeSingleCommand(context, command);
        }
    }

    private void executeSingleCommand(DealContext context, ServiceCommandType command) {
        Exchange exchange = context.getExchange();
        Instrument instrument = context.getInstrument();
        if (exchange == null) {
            throw new IllegalStateException("exchange is null");
        }
        if (instrument == null) {
            throw new IllegalStateException("instrument is null");
        }

        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        switch (command) {
            case REFRESH_POSITIONS -> positionService.refreshPositions(exchange, instrument, deal);

            case REFRESH_BALANCE -> balanceService.refreshBalance(exchange);

            case REFRESH_PENDING_ORDERS -> orderService.refreshPendingOrders(exchange, instrument);

            case CREATE_ENTRY_ORDER -> orderService.createEntryOrder(deal);

            case REFRESH_ENTRY_ORDER -> orderService.refreshEntryOrder(deal);

            case REFRESH_ALGO_ORDERS -> algoOrderService.refreshActiveAlgoOrders(deal);

            case CREATE_MAIN_PROTECTION -> algoOrderService.createMainProtection(deal);

            case CANCEL_ATTACHED_PROTECTION -> algoOrderService.cancelAttachedProtection(deal);

            case AMEND_MAIN_PROTECTION -> algoOrderService.amendMainProtection(deal);

            case FINALIZE_EXIT -> exitService.finalizeExit(deal);

            case EXECUTE_KILL_SWITCH -> killSwitchService.executeKillSwitch(deal);

            default -> throw new IllegalStateException("Unsupported service command: " + command);
        }
    }
}
