package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandFactory;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ErrorHandler implements StateHandler {

    private final ServiceCommandFactory commandFactory;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ERROR;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        if (BooleanUtils.isFalse(Objects.equals(deal.getStatus(), Deal.Status.ERROR))) {
            throw new IllegalStateException("deal.status must be ERROR");
        }
    }

    @Override
    public TransitionResult handle(DealContext context, DealEvent event) {
        return switch (event) {
            case CHECK_ENTRY_INVARIANTS, FAIL -> TransitionResult.stay();
            case PROCESS, RETRY, CHECK_EXIT_INVARIANTS -> process(context);
            default -> TransitionResult.stay();
        };
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
    }

    private TransitionResult process(DealContext context) {
        List<ServiceCommand> commands = List.of(
                commandFactory.system(context, ServiceCommandType.EXECUTE_KILL_SWITCH),
                commandFactory.system(context, ServiceCommandType.MARK_DEAL_ERROR)
        );
        return TransitionResult.stay(commands);
    }
}
