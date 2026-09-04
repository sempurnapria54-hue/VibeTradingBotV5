package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Оркестратор FSM сделки: выбирает {@link DealFsmHandler} по текущему
 * {@link Deal.Status}, запускает его и возвращает {@link DealTransition}.
 * Запускается DealOrchestratorJob. Владелец оркестрации порядка ног
 * REPLACE — петля/handler по подтверждённым фактам (фабрика — одна команда
 * за проход, docs/decisions/action-orchestration-vs-command.md). Сам на
 * биржу не ходит, команды не исполняет, аудит не строит. Terminal-статусы
 * handler'ов не имеют — для них проход пустой. См.
 * docs/components/DealStateMachine.md.
 */
@Slf4j
@Service
public class DealStateMachine {

    /**
     * Объявленные рёбра жизненного цикла сделки. Матрица — единственный
     * носитель ответа «ребро существует»; исполнимая форма —
     * docs/spec/deal-lifecycle.json §edgeDeclared.
     */
    private static final Map<Deal.Status, Set<Deal.Status>> DECLARED_EDGES = declaredEdges();

    private static Map<Deal.Status, Set<Deal.Status>> declaredEdges() {
        Map<Deal.Status, Set<Deal.Status>> edges = new EnumMap<>(Deal.Status.class);
        edges.put(Deal.Status.ACTIVE,
                EnumSet.of(Deal.Status.EXIT_PENDING, Deal.Status.CLOSED, Deal.Status.ERROR));
        edges.put(Deal.Status.EXIT_PENDING,
                EnumSet.of(Deal.Status.CLOSED, Deal.Status.ERROR));
        edges.put(Deal.Status.ERROR,
                EnumSet.of(Deal.Status.EMERGENCY_CLOSED));
        return edges;
    }

    private final Map<Deal.Status, DealFsmHandler> handlers;
    private final DealTerminalGate terminalGate;

    public DealStateMachine(List<DealFsmHandler> handlers, DealTerminalGate terminalGate) {
        this.handlers = handlers.stream()
                                .collect(toMap(DealFsmHandler::supportedStatus, identity()));
        this.terminalGate = terminalGate;
    }

    /** Ребро объявлено матрицей жизненного цикла сделки. */
    public Boolean edgeDeclared(Deal.Status from, Deal.Status to) {
        if (isNull(from) || isNull(to)) {
            return false;
        }
        return DECLARED_EDGES.getOrDefault(from, EnumSet.noneOf(Deal.Status.class)).contains(to);
    }

    /**
     * Разрешён ли переход сделки прямо сейчас: ребро объявлено, а
     * терминал — только когда все транши терминальны и живой риск
     * доказанно отсутствует.
     *
     * <p>Гейт живого риска стои́т на ОБОИХ терминалах — штатном и
     * аварийном: аварийная тропа отличается контрактом результата, а не
     * правом оставить непогашенный риск. Контракты результата (чистый и
     * аварийный) сюда пока не входят — их операнды суть поля финализации
     * P&L, которых модель ещё не несёт; это названное ограничение, а не
     * умолчание.
     */
    public Boolean transitionAllowed(DealContext dealContext, Deal.Status to, Boolean graphComplete) {
        Deal deal = dealContext.getDeal();
        if (isFalse(edgeDeclared(deal.getStatus(), to))) {
            log.debug("Deal edge is not declared dealId={} from={} to={}",
                    deal.getId(), deal.getStatus(), to);
            return false;
        }
        if (Deal.Status.CLOSED.equals(to)) {
            return isTrue(deal.allTranchesTerminal())
                    && isTrue(terminalGate.riskProvenAbsent(deal, deal.getTranches(), graphComplete));
        }
        if (Deal.Status.EMERGENCY_CLOSED.equals(to)) {
            return terminalGate.riskProvenAbsent(deal, deal.getTranches(), graphComplete);
        }
        return true;
    }

    /**
     * Один проход FSM по сделке: handler текущего статуса → команды/переход.
     */
    public DealTransition advance(DealContext dealContext) {
        Deal.Status status = dealContext.getDeal()
                                        .getStatus();
        DealFsmHandler handler = handlers.get(status);
        if (isNull(handler)) {
            log.debug("No FSM handler for status {} dealId={}", status, dealContext.getDeal()
                                                                                   .getId());
            return DealTransition.stay();
        }
        return handler.checkEntry(dealContext)
                      .or(() -> handler.checkTransition(dealContext))
                      .orElseGet(() -> handler.handle(dealContext));
    }
}
