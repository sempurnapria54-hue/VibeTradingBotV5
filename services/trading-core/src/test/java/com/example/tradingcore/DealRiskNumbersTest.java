package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.risk.DealRiskNumbers;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.persistence.service.DealDataService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Четвёрка чисел риска сделки: единственное ожидание, не поглощённое
 * кейсами уровня 2.
 *
 * <p><b>Проба РАЗДЕЛЕНА, а не снята.</b> Одиннадцать её прежних ожиданий
 * поглощены группами {@code U27} и {@code U28} документа
 * `.claude/tests/cases/trading-core-risk.md`; двенадцатое — что нога,
 * лежащая только в донорском поле агрегата, в числа не входит, — ни
 * одной клеткой не покрыто: клетки говорят об обходе траншей, а не о
 * том, что соседнее поле общей библиотеки ядром не читается
 * (`.claude/work/backlog.md` §«Донорские поля агрегата сделки в общей
 * библиотеке»).
 */
class DealRiskNumbersTest {

    private final DealRiskNumbersService service = new DealRiskNumbersService(mock(DealDataService.class));

    /**
     * Ноги берутся ОБХОДОМ ТРАНШЕЙ: донорское поле агрегата ядром не
     * читается, и нога, лежащая только в нём, в числа не входит.
     */
    @Test
    void legsComeFromTranchesNotFromTheAggregateField() {
        Order onlyInAggregate = entryLeg();
        Deal deal = deal(livePosition(), tranche());
        deal.setOrders(List.of(onlyInAggregate));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getPlannedRiskAmount()).isEqualByComparingTo("0");
    }

    private static Deal deal(Position live, DealTranche tranche) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setPositions(List.of(live));
        deal.setTranches(List.of(tranche));
        return deal;
    }

    private static DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(10L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of());
        return tranche;
    }

    private static Order entryLeg() {
        Order leg = new Order();
        leg.setDealTrancheId(10L);
        leg.setType(Order.Type.ENTRY);
        leg.setStatus(Order.Status.COMPLETED);
        leg.setPlannedRiskAmount(new BigDecimal("500"));
        leg.setPlannedSizeContracts(new BigDecimal("100"));
        leg.setAccumulatedFillSize(new BigDecimal("100"));
        leg.setPlannedEntryPrice(new BigDecimal("3000"));
        leg.setPlannedStopPrice(new BigDecimal("2910"));
        leg.setPlannedContractValue(new BigDecimal("0.1"));
        leg.setPositionId(1L);
        return leg;
    }

    private static Position livePosition() {
        Position position = new Position();
        position.setId(1L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(new BigDecimal("100"));
        return position;
    }
}
