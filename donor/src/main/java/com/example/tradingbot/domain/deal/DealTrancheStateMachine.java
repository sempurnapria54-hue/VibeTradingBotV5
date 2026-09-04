package com.example.tradingbot.domain.deal;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Контракт переходов транша: какое ребро объявлено и разрешён ли переход
 * по нему прямо сейчас. Исполнимая форма контракта —
 * docs/spec/deal-tranche-lifecycle.json (величины trancheEdgeDeclared,
 * reopenEdge, reopenPermitted, riskCreatingUnderCollapse,
 * terminalContract, trancheTransitionAllowed); этот класс её выражает, а
 * не переизобретает — при расхождении верна спека.
 *
 * <p>Машина ничего не сохраняет и на биржу не ходит: она отвечает на
 * вопрос «можно ли», а перевод статуса делает вызывающий.
 *
 * <p>См. docs/components/DealTrancheStateMachine.md,
 * docs/lifecycles/DealTranche.md.
 */
@Slf4j
@Service
public class DealTrancheStateMachine {

    /**
     * Объявленные рёбра жизненного цикла транша. Матрица — единственный
     * носитель ответа «ребро существует»: переход, которого в ней нет,
     * не объявлен, и разрешить его нечем.
     */
    private static final Map<DealTranche.Status, Set<DealTranche.Status>> DECLARED_EDGES =
            declaredEdges();

    private static Map<DealTranche.Status, Set<DealTranche.Status>> declaredEdges() {
        Map<DealTranche.Status, Set<DealTranche.Status>> edges = new EnumMap<>(DealTranche.Status.class);
        edges.put(DealTranche.Status.PRECHECK,
                EnumSet.of(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.CLOSED));
        edges.put(DealTranche.Status.ENTRY_SUBMITTED,
                EnumSet.of(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.EXIT_PENDING,
                        DealTranche.Status.CLOSED));
        edges.put(DealTranche.Status.ENTRY_FINALIZED,
                EnumSet.of(DealTranche.Status.PROTECTION_SWITCHED, DealTranche.Status.MANAGING));
        edges.put(DealTranche.Status.PROTECTION_SWITCHED,
                EnumSet.of(DealTranche.Status.MANAGING));
        edges.put(DealTranche.Status.MANAGING,
                EnumSet.of(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.EXIT_PENDING));
        edges.put(DealTranche.Status.EXIT_PENDING,
                EnumSet.of(DealTranche.Status.CLOSED));
        return edges;
    }

    private final Map<DealTranche.Status, TrancheFsmHandler> handlers;

    public DealTrancheStateMachine(List<TrancheFsmHandler> handlers) {
        this.handlers = handlers.stream()
                                .collect(toMap(TrancheFsmHandler::supportedStatus, identity()));
    }

    /**
     * Один проход FSM по траншу: handler текущего статуса → команды и
     * НАМЕРЕНИЕ перехода. Разрешение намерения — отдельный ход
     * ({@link #transitionAllowed}): исход прохода не есть право его
     * применить.
     */
    public TrancheTransition advance(DealContext dealContext, DealTranche tranche) {
        TrancheFsmHandler handler = handlers.get(tranche.getStatus());
        if (isNull(handler)) {
            log.debug("No tranche FSM handler for status {} trancheId={}",
                    tranche.getStatus(), tranche.getId());
            return TrancheTransition.stay();
        }
        return handler.checkEntry(dealContext, tranche)
                      .or(() -> handler.checkTransition(dealContext, tranche))
                      .orElseGet(() -> handler.handle(dealContext, tranche));
    }

    /** Ребро объявлено матрицей жизненного цикла транша. */
    public Boolean edgeDeclared(DealTranche.Status from, DealTranche.Status to) {
        if (isNull(from) || isNull(to)) {
            return false;
        }
        return DECLARED_EDGES.getOrDefault(from, EnumSet.noneOf(DealTranche.Status.class))
                .contains(to);
    }

    /** Ребро переоткрытия эпизода: сопровождение снова отправляет вход. */
    public Boolean reopenEdge(DealTranche.Status from, DealTranche.Status to) {
        return DealTranche.Status.MANAGING.equals(from)
                && DealTranche.Status.ENTRY_SUBMITTED.equals(to);
    }

    /**
     * Переоткрытие разрешено: стратегия его допускает, прежняя экспозиция
     * погашена целиком и новая входная нога уже живая. Все три условия
     * обязательны — переоткрытие поверх непогашенной экспозиции удваивало
     * бы риск транша молча.
     */
    public Boolean reopenPermitted(DealTranche tranche, Boolean reopenAllowed) {
        return isTrue(reopenAllowed)
                && tranche.exposure().compareTo(BigDecimal.ZERO) == 0
                && isTrue(tranche.hasLiveEntryOrder());
    }

    /**
     * Переход создаёт риск под сворачиванием: сделка уже идёт к выходу, а
     * транш просится во вход. Запрещено независимо от объявленности ребра.
     */
    public Boolean riskCreatingUnderCollapse(DealTranche.Status to, Deal deal) {
        return DealTranche.Status.ENTRY_SUBMITTED.equals(to)
                && Deal.Status.EXIT_PENDING.equals(deal.getStatus());
    }

    /**
     * Контракт терминала транша: граф исполнения полон и риска транш не
     * несёт. Пока хоть одно из двух ложно, транш в CLOSED не уходит —
     * иначе система забыла бы про живой риск, признав транш завершённым.
     */
    public Boolean terminalContract(DealTranche tranche, Boolean graphComplete) {
        return isTrue(graphComplete) && isFalse(tranche.isRiskBearing());
    }

    /**
     * Разрешён ли переход транша прямо сейчас. Конъюнкция контракта:
     * ребро объявлено, риск под сворачиванием не создаётся, переоткрытие
     * (если это оно) разрешено, а терминал — только по своему контракту.
     */
    public Boolean transitionAllowed(DealTranche tranche, DealTranche.Status to, Deal deal,
                                     Boolean reopenAllowed, Boolean graphComplete) {
        DealTranche.Status from = tranche.getStatus();
        if (isFalse(edgeDeclared(from, to))) {
            log.debug("Tranche edge is not declared trancheId={} from={} to={}",
                    tranche.getId(), from, to);
            return false;
        }
        if (isTrue(riskCreatingUnderCollapse(to, deal))) {
            return false;
        }
        if (isTrue(reopenEdge(from, to)) && isFalse(reopenPermitted(tranche, reopenAllowed))) {
            return false;
        }
        if (DealTranche.Status.CLOSED.equals(to)) {
            return terminalContract(tranche, graphComplete);
        }
        return true;
    }
}
