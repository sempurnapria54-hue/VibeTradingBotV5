package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.safety.HoldSignal;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * Исход одного прохода FSM транша: команды и, если он разрешён, новый
 * статус транша (docs/components/DealTrancheStateMachine.md).
 *
 * <p><b>Ребра в ошибочный статус здесь нет и быть не может</b> — у транша
 * такого статуса не существует. Отказ, который нельзя разобрать на месте,
 * поднимается обработчику сделки признаком {@link #dealErrorRequested},
 * и ошибочное состояние ставится на СДЕЛКЕ
 * (docs/lifecycles/DealTranche.md).
 *
 * <p><b>Затребованная ступень — намерение, а не право.</b> Переход её
 * только несёт; поднимает ступень петля прохода, одной точкой на оба
 * уровня (docs/components/DealOrchestratorJob.md).
 *
 * <p><b>Причина закрытия едет вместе с терминалом</b>, а не отдельной
 * записью: писатель причины — обработчик терминального ребра, и пишет он
 * её той же транзакцией, которой ставит статус
 * (docs/lifecycles/DealTranche.md §«Писатель причины закрытия транша —
 * обработчик терминального ребра»).
 *
 * <p><b>Добыча фактов отделена от работы.</b> Команда, которая только
 * НАБЛЮДАЕТ сущность транша (звено добычи), едет своим списком: транш,
 * ждущий налива, опрашивает его каждым проходом, и засчитанная работой
 * такая команда занимала бы проход сделки бессрочно — её собственная
 * работа уровня сделки не отбиралась бы, пока жива хоть одна нога
 * (docs/processes/fsm-execution-layering.md §«Добыча не занимает
 * проход»).
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией у значения
 * нет (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Value
public class TrancheTransition {

    /** Команды прохода в порядке диспетчеризации; пусто — команд нет. */
    List<ServiceCommand> commands;

    /**
     * Команды добычи фактов — наблюдение, а не работа; диспетчеризуются
     * раньше команд работы (довод порядка —
     * {@link TrancheCascadeResult#passCommands()}). Пусто — наблюдать нечего.
     */
    List<ServiceCommand> observations;

    /** Целевой статус транша; пусто — транш остаётся в своём. */
    DealTranche.Status nextStatus;

    /** Причина закрытия; непуста только вместе с терминалом. */
    DealTranche.CloseReason closeReason;

    /** Обработчик просит увести СДЕЛКУ ошибочной тропой. */
    Boolean dealErrorRequested;

    /**
     * Обработчик просит увести СДЕЛКУ управляемым сворачиванием, назвав
     * причину выхода из штатного ведения; пусто — не просит.
     *
     * <p>Отдельное поле, а не ветвь ошибочной просьбы: управляемое
     * сворачивание снимает риск закрывающими действиями, а ошибочная
     * тропа — аварийно, и подмена одного другим рвала бы по рынку
     * позицию, которая под контролем (docs/lifecycles/Deal.md §«Причина
     * выхода из штатного ведения»).
     */
    Deal.ShutdownReason shutdownRequested;

    /** Затребованная ступень радиуса; пусто — ступени проход не просит. */
    HoldSignal holdSignal;

    private TrancheTransition(List<ServiceCommand> commands, List<ServiceCommand> observations,
                              DealTranche.Status nextStatus, DealTranche.CloseReason closeReason,
                              Boolean dealErrorRequested, Deal.ShutdownReason shutdownRequested,
                              HoldSignal holdSignal) {
        this.commands = List.copyOf(emptyIfNull(commands));
        this.observations = List.copyOf(emptyIfNull(observations));
        this.nextStatus = nextStatus;
        this.closeReason = closeReason;
        this.dealErrorRequested = dealErrorRequested;
        this.shutdownRequested = shutdownRequested;
        this.holdSignal = holdSignal;
    }

    /** Транш остаётся в своём статусе, делать этим проходом нечего. */
    public static TrancheTransition stay() {
        return new TrancheTransition(List.of(), List.of(), null, null, Boolean.FALSE, null, null);
    }

    /** Команда прохода без смены статуса. */
    public static TrancheTransition command(ServiceCommand command) {
        return new TrancheTransition(commandList(command), List.of(), null, null, Boolean.FALSE, null, null);
    }

    /**
     * Добыча факта без работы; пустая команда (звено ждёт отката повтора)
     * даёт пустой исход.
     */
    public static TrancheTransition observe(ServiceCommand observation) {
        return new TrancheTransition(List.of(), commandList(observation), null, null, Boolean.FALSE, null, null);
    }

    /** Переход в названный статус. */
    public static TrancheTransition moveTo(DealTranche.Status status) {
        return new TrancheTransition(List.of(), List.of(), status, null, Boolean.FALSE, null, null);
    }

    /** Терминал транша с его причиной — одним ходом. */
    public static TrancheTransition close(DealTranche.CloseReason reason) {
        return new TrancheTransition(List.of(), List.of(), DealTranche.Status.CLOSED, reason, Boolean.FALSE,
                null, null);
    }

    /** Просьба увести сделку ошибочной тропой; ступени проход не требует. */
    public static TrancheTransition escalate() {
        return new TrancheTransition(List.of(), List.of(), null, null, Boolean.TRUE, null, null);
    }

    /**
     * Просьба увести сделку УПРАВЛЯЕМЫМ сворачиванием с названной
     * причиной выхода из штатного ведения.
     */
    public static TrancheTransition requestShutdown(Deal.ShutdownReason reason) {
        return new TrancheTransition(List.of(), List.of(), null, null, Boolean.FALSE, reason, null);
    }

    /** Затребованная ступень без просьбы менять статус сделки. */
    public static TrancheTransition requestRung(HoldSignal signal) {
        return new TrancheTransition(List.of(), List.of(), null, null, Boolean.FALSE, null, signal);
    }

    /**
     * Просьба увести сделку ошибочной тропой ВМЕСТЕ с затребованной
     * ступенью: нарушение инварианта покрытия — это и то, и другое
     * (docs/rules/live-risk-protection.md §«Реакция на непокрытый риск»).
     */
    public static TrancheTransition escalate(HoldSignal signal) {
        return new TrancheTransition(List.of(), List.of(), null, null, Boolean.TRUE, null, signal);
    }

    /** Тот же исход плюс команда прохода. */
    public TrancheTransition withCommand(ServiceCommand command) {
        if (isNull(command)) {
            return this;
        }
        List<ServiceCommand> extended = new ArrayList<>(commands);
        extended.add(command);
        return new TrancheTransition(extended, observations, nextStatus, closeReason, dealErrorRequested,
                shutdownRequested, holdSignal);
    }

    /** Тот же исход плюс команда добычи факта. */
    public TrancheTransition withObservation(ServiceCommand observation) {
        if (isNull(observation)) {
            return this;
        }
        List<ServiceCommand> extended = new ArrayList<>(observations);
        extended.add(observation);
        return new TrancheTransition(commands, extended, nextStatus, closeReason, dealErrorRequested,
                shutdownRequested, holdSignal);
    }

    /** Переход несёт команды работы к диспетчеризации. */
    public Boolean hasCommands() {
        return isNotEmpty(commands);
    }

    /** Переход несёт команды добычи фактов. */
    public Boolean hasObservations() {
        return isNotEmpty(observations);
    }

    /** Переход двигает статус транша. */
    public Boolean movesStatus() {
        return nonNull(nextStatus);
    }

    /**
     * Тот же исход со статусным ребром — им пользуется выходная проверка
     * обработчика, дописывающая ребро к уже собранным командам.
     */
    public TrancheTransition withStatus(DealTranche.Status status) {
        return new TrancheTransition(commands, observations, status, closeReason, dealErrorRequested,
                shutdownRequested, holdSignal);
    }

    /** Тот же исход без статусного ребра — им пользуется гейт матрицы. */
    public TrancheTransition withoutStatus() {
        return new TrancheTransition(commands, observations, null, null, dealErrorRequested,
                shutdownRequested, holdSignal);
    }

    private static List<ServiceCommand> commandList(ServiceCommand command) {
        return isNull(command) ? List.of() : List.of(command);
    }
}
