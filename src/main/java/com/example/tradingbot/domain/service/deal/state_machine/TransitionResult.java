package com.example.tradingbot.domain.service.deal.state_machine;

import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.core.deal.Deal;

import java.util.List;

public class TransitionResult {

    private final Deal.Status nextStatus;

    private final List<ServiceCommandType> commands;

    public TransitionResult(Deal.Status nextStatus, List<ServiceCommandType> commands) {
        this.nextStatus = nextStatus;
        this.commands = commands == null ? List.of() : List.copyOf(commands);
    }

    public static TransitionResult stay() {
        return new TransitionResult(null, List.of());
    }

    public static TransitionResult stay(List<ServiceCommandType> commands) {
        return new TransitionResult(null, commands);
    }

    public static TransitionResult moveTo(Deal.Status nextStatus) {
        return new TransitionResult(nextStatus, List.of());
    }

    public static TransitionResult moveTo(Deal.Status nextStatus, List<ServiceCommandType> commands) {
        return new TransitionResult(nextStatus, commands);
    }

    public Deal.Status getNextStatus() {
        return this.nextStatus;
    }

    public List<ServiceCommandType> getCommands() {
        return this.commands;
    }
}