package com.example.tradingbot.domain.deal.tranche;

import static java.math.BigDecimal.ZERO;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.TrancheFsmHandler;
import com.example.tradingbot.domain.deal.TrancheTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса EXIT_PENDING ТРАНША: сворачивает свой транш —
 * снимает его живую входную заявку, затем его живые защиты, — в порядке
 * инварианта docs/rules/exit-teardown-order.md.
 *
 * <p><b>Полное закрытие нетто-экспозиции этот обработчик не эмитит:</b>
 * оно уровня сделки и законно только при выходе всех траншей
 * (docs/components/DealExitPendingHandler.md). Числа сделки, движения
 * средств и терминал сделки — тоже не его: они агрегатные.
 *
 * <p><b>Своей reduce-only ноги под каскадом сворачивания сделки транш не
 * выпускает:</b> экспозицию гасит одно закрытие уровня сделки, а траншу
 * она списывается приписанным объёмом (правило сопоставления,
 * docs/models/domain/aggregate/DealTranche.md). Иначе на общем выходе
 * экспозицию гасили бы дважды — N ног плюс одна команда.
 *
 * <p><b>Названное ограничение:</b> собственной reduce-only ноги
 * САМОСТОЯТЕЛЬНОГО выхода транша (запрещённое переоткрытие, риск-блок,
 * истёкшее условие) здесь нет — её фабрики в коде не существует, и
 * заводить её ради формы значило бы завести мёртвый путь. На этих тропах
 * транш доходит до терминала по нулевой экспозиции.
 *
 * <p>Дочистка преконтроль риска не проходит: это риск-снижающие операции
 * (docs/rules/risk-validator-scope.md). Учёта серии неудач у неё нет — у
 * дочистки нет исполнения-действия, а значит и бюджета отказов.
 *
 * <p>См. docs/components/TrancheExitPendingHandler.md.
 */
@Component
@RequiredArgsConstructor
public class TrancheExitPendingHandler implements TrancheFsmHandler {

    private final DealFsmSupport support;

    @Override
    public DealTranche.Status supportedStatus() {
        return DealTranche.Status.EXIT_PENDING;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        List<Order> entryLegs = liveEntryLegs(tranche);
        if (isNotEmpty(entryLegs)) {
            return TrancheTransition.command(support.cancelOrderCommand(dealContext,
                    entryLegs.getFirst().getId(), Order.CloseReason.CANCELED_BY_STRATEGY));
        }
        if (tranche.exposure().compareTo(ZERO) > 0) {
            // Экспозиция ещё жива: её гасит закрытие уровня сделки, и до
            // подтверждения защиты снимать нельзя — снятая раньше времени
            // защита оставила бы живой риск непокрытым.
            return TrancheTransition.stay();
        }
        List<Order> reduceOnlyLegs = liveReduceOnlyLegs(tranche);
        if (isNotEmpty(reduceOnlyLegs)) {
            return TrancheTransition.command(support.cancelOrderCommand(dealContext,
                    reduceOnlyLegs.getFirst().getId(), Order.CloseReason.CANCELED_BY_STRATEGY));
        }
        List<AlgoOrder> liveProtections = tranche.liveAlgoOrders();
        if (isNotEmpty(liveProtections)) {
            return TrancheTransition.command(support.cancelAlgoOrderCommand(dealContext,
                    liveProtections.getFirst().getId(), AlgoOrder.CloseReason.CANCELED_BY_STRATEGY));
        }
        return terminal(dealContext, tranche);
    }

    /**
     * Терминал транша: живого риска у него нет и это подтверждено — И
     * граф предъявлен целиком. Охрана полнотой графа у терминала транша
     * та же, что у терминала сделки: все три признака считаются
     * агрегатами по загружаемым коллекциям, и на неполном графе «риска
     * нет» означало бы «не предъявлено» (docs/lifecycles/DealTranche.md).
     */
    private TrancheTransition terminal(DealContext dealContext, DealTranche tranche) {
        if (isTrue(tranche.isRiskBearing()) || isFalse(support.graphComplete(dealContext))) {
            return TrancheTransition.stay();
        }
        return TrancheTransition.builder()
                .nextStatus(DealTranche.Status.CLOSED)
                .closeReason(closeReason(dealContext))
                .build();
    }

    /**
     * Причину закрытия пишет обработчик терминального ребра той же
     * транзакцией. Значение — по ИНИЦИАТОРУ выхода: сворачивание сделки
     * несёт свою причину и передаёт её вниз; там, где инициатора нет
     * вовсе (экспозиция обнулилась вне нашего ведения, включая штатный
     * конец восстановленного транша), — {@code EXTERNAL_CLOSE}.
     */
    private DealTranche.CloseReason closeReason(DealContext dealContext) {
        Deal.CloseReason dealReason = dealContext.getDeal().getCloseReason();
        return switch (dealReason) {
            case null -> DealTranche.CloseReason.EXTERNAL_CLOSE;
            case STRATEGY_EXIT -> DealTranche.CloseReason.STRATEGY_EXIT;
            case RISK_CONTROL -> DealTranche.CloseReason.RISK_CONTROL;
            case STOP_LOSS -> DealTranche.CloseReason.STOP_LOSS;
            case TIME_STOP -> DealTranche.CloseReason.TIME_STOP;
            case TAKE_PROFIT -> DealTranche.CloseReason.TAKE_PROFIT;
            case ENTRY_CONDITION_EXPIRED -> DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED;
            default -> DealTranche.CloseReason.EXTERNAL_CLOSE;
        };
    }

    /** Живые ВХОДНЫЕ ноги транша — снимаются первыми: они ещё могут долить объём. */
    private List<Order> liveEntryLegs(DealTranche tranche) {
        return tranche.liveOrders().stream()
                .filter(order -> isFalse(order.getPositionReducingOnly()))
                .collect(Collectors.toList());
    }

    /** Живые reduce-only ноги транша — дочищаются после подтверждённого закрытия. */
    private List<Order> liveReduceOnlyLegs(DealTranche tranche) {
        return tranche.liveOrders().stream()
                .filter(order -> isTrue(order.getPositionReducingOnly()))
                .collect(Collectors.toList());
    }
}
