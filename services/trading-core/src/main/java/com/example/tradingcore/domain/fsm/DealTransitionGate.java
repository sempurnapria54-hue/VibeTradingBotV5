package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Гейт статусного ребра сделки: объявлено ли ребро, разрешено ли полное
 * закрытие нетто-экспозиции и выполнены ли контракты терминалов.
 * Исполнимая форма — docs/spec/deal-lifecycle.json; при расхождении верна
 * спека.
 *
 * <p><b>Живой риск читается одним носителем</b> — {@link DealTerminalGate}
 * (docs/spec/deal-lifecycle.json §{@code riskProvenAbsent}); свой,
 * упрощённый предикат здесь разошёлся бы с ним в разрешающую сторону.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealTransitionGate {

    /** Объявленные рёбра агрегата (docs/lifecycles/Deal.md). */
    private static final Map<Deal.Status, Set<Deal.Status>> DECLARED_EDGES =
            new EnumMap<>(Deal.Status.class);

    static {
        DECLARED_EDGES.put(Deal.Status.ACTIVE,
                Set.of(Deal.Status.EXIT_PENDING, Deal.Status.CLOSED, Deal.Status.ERROR));
        DECLARED_EDGES.put(Deal.Status.EXIT_PENDING, Set.of(Deal.Status.CLOSED, Deal.Status.ERROR));
        DECLARED_EDGES.put(Deal.Status.ERROR, Set.of(Deal.Status.EMERGENCY_CLOSED));
        DECLARED_EDGES.put(Deal.Status.CLOSED, Set.of());
        DECLARED_EDGES.put(Deal.Status.EMERGENCY_CLOSED, Set.of());
    }

    private final DealTerminalGate terminalGate;

    /** Ребро объявлено матрицей жизненного цикла сделки. */
    public Boolean edgeDeclared(Deal.Status from, Deal.Status to) {
        if (isNull(from) || isNull(to)) {
            return false;
        }
        return DECLARED_EDGES.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Полное закрытие нетто-экспозиции разрешено: живых ВХОДНЫХ ног у
     * траншей не осталось И граф предъявлен целиком
     * (docs/spec/deal-lifecycle.json §{@code netCloseAllowed}).
     *
     * <p>Порядок несущий: закрытие раньше снятия входных ног гонялось бы
     * за наливом, а на неполном графе живая нога не видна вовсе
     * (docs/rules/exit-teardown-order.md).
     */
    public Boolean netCloseAllowed(DealContext dealContext) {
        if (isNotTrue(dealContext.getGraphComplete())) {
            return false;
        }
        return dealContext.getDeal().getTranches().stream()
                .noneMatch(tranche -> isTrue(tranche.hasLiveEntryOrder()));
    }

    /**
     * Ветвь ШТАТНОГО терминала композиции {@code transitionAllowed}
     * (docs/spec/deal-lifecycle.json): все транши терминальны, живого риска
     * доказанно нет, и выполнена величина {@code cleanTerminalContract} —
     * число посчитано, валюта резолвлена у состоявшейся сделки. Имя
     * называет ветвь, а не одноимённую величину спеки: та несёт только
     * число и валюту.
     */
    public Boolean cleanTerminalAllowed(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(deal.allTranchesTerminal())) {
            return false;
        }
        if (isFalse(terminalGate.riskProvenAbsent(deal, deal.getTranches(), dealContext.getGraphComplete()))) {
            return false;
        }
        if (isNull(deal.getResultProfit())) {
            return false;
        }
        return isNotBlank(deal.getResultProfitCurrency()) || isFalse(deal.positionObserved());
    }

    /**
     * Ветвь АВАРИЙНОГО терминала композиции {@code transitionAllowed}
     * (docs/spec/deal-lifecycle.json): гейт мерит ПЕРВЫЙ конъюнкт —
     * доказанное отсутствие живого риска.
     *
     * <p><b>Второй конъюнкт — величину {@code emergencyTerminalContract}
     * («число посчитано, а не подставлено») — держат писатели числа, а не
     * этот гейт,</b> и спека называет их поимённо: провенанса у числа в
     * данных нет, и отличить подставленное от посчитанного по нему нельзя.
     * Каждый писатель пишет только посчитанное — финализация выхода по
     * доступному итогу, штатный терминал тропы без входа нулём-результатом
     * тропы, само аварийное звено по доступности либо пустоту
     * (docs/components/MarkDealEmergencyClosedExecutor.md).
     *
     * <p><b>Терминальность строк траншей его не гейтит:</b> строка транша
     * стала бы второй точкой отказа аварийного контура
     * (docs/components/ErrorHandler.md).
     */
    public Boolean emergencyTerminalAllowed(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        return terminalGate.riskProvenAbsent(deal, deal.getTranches(), dealContext.getGraphComplete());
    }

    /** Переход разрешён целиком: ребро объявлено, терминал — своим контрактом. */
    public Boolean transitionAllowed(DealContext dealContext, Deal.Status to) {
        Deal.Status from = dealContext.getDeal().getStatus();
        if (isFalse(edgeDeclared(from, to))) {
            log.warn("Deal edge is not declared dealId={} from={} to={}",
                    dealContext.getDeal().getId(), from, to);
            return false;
        }
        if (Deal.Status.CLOSED.equals(to)) {
            return cleanTerminalAllowed(dealContext);
        }
        if (Deal.Status.EMERGENCY_CLOSED.equals(to)) {
            return emergencyTerminalAllowed(dealContext);
        }
        return true;
    }
}
