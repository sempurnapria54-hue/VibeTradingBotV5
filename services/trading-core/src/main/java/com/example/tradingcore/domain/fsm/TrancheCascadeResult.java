package com.example.tradingcore.domain.fsm;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.safety.HoldSignal;
import java.util.List;
import lombok.Value;

/**
 * Сведённый исход каскада траншей: их команды, их одобренные рёбра и
 * просьбы, адресованные СДЕЛКЕ (роль каскада описана у обработчика
 * активной сделки — docs/components/DealActiveHandler.md).
 *
 * <p><b>Просьбы отделены от команд намеренно.</b> Транш ступени не
 * поднимает и статуса сделки не пишет — он их просит, ровно как просит
 * ребро собственного статуса (docs/processes/fsm-execution-layering.md).
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией нет.
 */
@Value
public class TrancheCascadeResult {

    /** Команды траншей в порядке прогона. */
    List<ServiceCommand> commands;

    /** Одобренные рёбра траншей, ждущие применения. */
    List<TrancheEdge> edges;

    /** Хоть один транш просит увести сделку ошибочной тропой. */
    Boolean dealErrorRequested;

    /** Первая названная причина выхода из штатного ведения; пусто — не просят. */
    Deal.ShutdownReason shutdownRequested;

    /** Самая жёсткая затребованная ступень; пусто — не просят. */
    HoldSignal holdSignal;

    /** Каскад что-то сделал: выдал команду либо одобрил ребро. */
    public Boolean acted() {
        return isNotEmpty(commands) || isNotEmpty(edges);
    }
}
