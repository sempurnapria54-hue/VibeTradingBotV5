package com.example.tradingbot.domain.service.deal.command.core;

import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.service.core.AlgoOrderService;
import com.example.tradingbot.domain.service.core.OrderService;
import com.example.tradingbot.domain.service.deal.ExitService;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshBalanceExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshAlgoOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshPositionExecutor;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.kill_switch.KillSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ServiceCommandExecutor {

    private final RefreshPositionExecutor refreshPositionExecutor;

    private final RefreshBalanceExecutor refreshBalanceExecutor;

    private final RefreshOrderExecutor refreshOrderExecutor;

    private final RefreshAlgoOrderExecutor refreshAlgoOrderExecutor;

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
            case REFRESH_POSITIONS -> refreshPositionExecutor.execute(exchange, instrument, deal.getId());

            case REFRESH_BALANCE -> refreshBalanceExecutor.execute(exchange);

            case REFRESH_PENDING_ORDERS -> refreshOrderExecutor.execute(exchange, instrument, deal.getId());

            case CREATE_ENTRY_ORDER -> orderService.createEntryOrder(deal);

            case REFRESH_ENTRY_ORDER -> orderService.refreshEntryOrder(deal);

            case REFRESH_ALGO_ORDERS -> refreshAlgoOrderExecutor.execute(exchange, instrument, deal.getId());

            case CREATE_MAIN_PROTECTION -> algoOrderService.createMainProtection(deal);

            case CANCEL_ATTACHED_PROTECTION -> algoOrderService.cancelAttachedProtection(deal);

            case AMEND_MAIN_PROTECTION -> algoOrderService.amendMainProtection(deal);

            case FINALIZE_EXIT -> exitService.finalizeExit(deal);

            case EXECUTE_KILL_SWITCH -> killSwitchService.executeKillSwitch(deal);

            default -> throw new IllegalStateException("Unsupported service command: " + command);
        }
    }
}
