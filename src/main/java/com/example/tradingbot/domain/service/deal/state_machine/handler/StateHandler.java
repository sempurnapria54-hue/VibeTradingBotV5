package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;

public interface StateHandler {

    Deal.Status supportedStatus();

    void checkEntryInvariants(DealContext context);

    TransitionResult handle(DealContext context, DealEvent event);

    void checkExitInvariants(DealContext context, TransitionResult result);
}