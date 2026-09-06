package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Прогон FSM каждого нетерминального транша сделки — по одному проходу на
 * транш — и сведе́ние их исходов к одному
 * (docs/components/DealActiveHandler.md §«Рабочая логика»).
 *
 * <p><b>Зовут каскад ДВА обработчика сделки из трёх</b> — активной сделки
 * и координированного выхода; обработчик ошибочного состояния его не
 * зовёт. Дом ответа — docs/processes/fsm-execution-layering.md §«Кто
 * прогоняет FSM транша».
 *
 * <p><b>Порядок прогона между траншами значения не имеет:</b> экспозиция
 * производная и пересчитывается из полного состояния. Порядок, который
 * значение имеет, — сопоставление закрывающего исполнения уровня сделки с
 * траншами, и задан он правилом
 * (docs/models/domain/aggregate/DealTranche.md), а не порядком прогона.
 *
 * <p><b>Аварийная просьба сильнее управляемой.</b> Когда один транш велел
 * сворачиваться штатно, а другой затребовал жёсткую ступень, побеждает
 * аварийная: обратный порядок отдал бы снятие риска закрывающим
 * действиям, которые считаются по тем самым данным, которым доверять
 * нельзя (docs/components/DealActiveHandler.md §«Реакция на устаревание
 * данных»).
 */
@Service
@RequiredArgsConstructor
public class TrancheCascade {

    private final DealTrancheStateMachine trancheStateMachine;

    /** Один проход каскада по всем живым траншам сделки. */
    public TrancheCascadeResult run(DealContext dealContext) {
        List<ServiceCommand> commands = new ArrayList<>();
        List<TrancheEdge> edges = new ArrayList<>();
        boolean errorRequested = false;
        Deal.ShutdownReason shutdown = null;
        HoldSignal signal = null;
        for (DealTranche tranche : dealContext.getDeal().liveTranches()) {
            TrancheTransition transition = trancheStateMachine.run(dealContext, tranche);
            commands.addAll(transition.getCommands());
            if (isTrue(transition.movesStatus())) {
                edges.add(new TrancheEdge(tranche, transition.getNextStatus(), transition.getCloseReason()));
            }
            errorRequested = errorRequested || isTrue(transition.getDealErrorRequested());
            shutdown = firstNonNull(shutdown, transition.getShutdownRequested());
            signal = stronger(signal, transition.getHoldSignal());
        }
        return new TrancheCascadeResult(commands, edges, errorRequested, shutdown, signal);
    }

    private Deal.ShutdownReason firstNonNull(Deal.ShutdownReason kept, Deal.ShutdownReason candidate) {
        return nonNull(kept) ? kept : candidate;
    }

    /** Из двух затребованных ступеней остаётся более жёсткая. */
    private HoldSignal stronger(HoldSignal kept, HoldSignal candidate) {
        if (isNull(candidate)) {
            return kept;
        }
        if (isNull(kept)) {
            return candidate;
        }
        return Objects.equals(HoldRung.HARD, kept.getRung()) ? kept : candidate;
    }
}
