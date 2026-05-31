package com.example.tradingbot.domain.service.deal.command.core;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.service.deal.ExitService;
import com.example.tradingbot.domain.service.deal.command.algo.AmendAlgoOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.algo.CancelAlgoOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.algo.CreateAlgoOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.algo.SubmitAlgoOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.close.ClosePositionExecutor;
import com.example.tradingbot.domain.service.deal.command.order.AmendOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.order.CancelOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.order.CreateOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.order.SubmitOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshAlgoOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshBalanceExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshPositionExecutor;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.kill_switch.KillSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Service
@RequiredArgsConstructor
public class ServiceCommandExecutor {

    private final RefreshPositionExecutor refreshPositionExecutor;

    private final RefreshBalanceExecutor refreshBalanceExecutor;

    private final RefreshOrderExecutor refreshOrderExecutor;

    private final RefreshAlgoOrderExecutor refreshAlgoOrderExecutor;

    private final CreateOrderExecutor createOrderExecutor;

    private final SubmitOrderExecutor submitOrderExecutor;

    private final AmendOrderExecutor amendOrderExecutor;

    private final CancelOrderExecutor cancelOrderExecutor;

    private final CreateAlgoOrderExecutor createAlgoOrderExecutor;

    private final SubmitAlgoOrderExecutor submitAlgoOrderExecutor;

    private final AmendAlgoOrderExecutor amendAlgoOrderExecutor;

    private final CancelAlgoOrderExecutor cancelAlgoOrderExecutor;

    private final ClosePositionExecutor closePositionExecutor;

    private final ExitService exitService;

    private final KillSwitchService killSwitchService;

    public void execute(DealContext context, List<ServiceCommand> commands) {
        Objects.requireNonNull(context);

        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        for (ServiceCommand command : emptyIfNull(commands)) {
            executeSingleCommand(context, command);
        }
    }

    private void executeSingleCommand(DealContext context, ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getType())) {
            throw new IllegalStateException("service command type is null");
        }

        ServiceCommandType type = command.getType();
        switch (type) {
            case REFRESH_POSITION -> executeRefreshPosition(context);
            case REFRESH_BALANCE -> executeRefreshBalance(context);
            case REFRESH_ORDER, REFRESH_PENDING_ORDERS, REFRESH_ORDER_HISTORY -> executeRefreshOrder(context);
            case REFRESH_ALGO_ORDER, REFRESH_ALGO_ORDERS, REFRESH_ALGO_ORDER_HISTORY -> executeRefreshAlgoOrder(context);
            case REFRESH_FILLS -> {
            }
            case CREATE_ORDER -> createOrderExecutor.execute(command);
            case SUBMIT_ORDER -> submitOrderExecutor.execute(context, command);
            case AMEND_ORDER -> amendOrderExecutor.execute(context, command);
            case CANCEL_ORDER -> cancelOrderExecutor.execute(context, command);
            case CREATE_ALGO_ORDER -> createAlgoOrderExecutor.execute(command);
            case SUBMIT_ALGO_ORDER -> submitAlgoOrderExecutor.execute(context, command);
            case AMEND_ALGO_ORDER -> amendAlgoOrderExecutor.execute(context, command);
            case CANCEL_ALGO_ORDER -> cancelAlgoOrderExecutor.execute(context, command);
            case CLOSE_POSITION -> closePositionExecutor.execute(context, command);
            case FINALIZE_DEAL_ENTRY -> {
            }
            case FINALIZE_DEAL_EXIT -> exitService.finalizeExit(requireDeal(context));
            case MARK_DEAL_CLOSED -> requireDeal(context).setStatus(Deal.Status.CLOSED);
            case MARK_DEAL_ERROR -> requireDeal(context).setStatus(Deal.Status.ERROR);
            case EXECUTE_KILL_SWITCH -> killSwitchService.executeKillSwitch(requireDeal(context));
        }
    }

    private void executeRefreshPosition(DealContext context) {
        refreshPositionExecutor.execute(requireExchange(context), requireInstrument(context), requireDeal(context).getId());
    }

    private void executeRefreshBalance(DealContext context) {
        refreshBalanceExecutor.execute(requireExchange(context));
    }

    private void executeRefreshOrder(DealContext context) {
        refreshOrderExecutor.execute(requireExchange(context), requireInstrument(context), requireDeal(context).getId());
    }

    private void executeRefreshAlgoOrder(DealContext context) {
        refreshAlgoOrderExecutor.execute(requireExchange(context), requireInstrument(context), requireDeal(context).getId());
    }

    private Exchange requireExchange(DealContext context) {
        Exchange exchange = context.getExchange();
        if (Objects.isNull(exchange)) {
            throw new IllegalStateException("exchange is null");
        }
        return exchange;
    }

    private Instrument requireInstrument(DealContext context) {
        Instrument instrument = context.getInstrument();
        if (Objects.isNull(instrument)) {
            throw new IllegalStateException("instrument is null");
        }
        return instrument;
    }

    private Deal requireDeal(DealContext context) {
        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }
        return deal;
    }
}
