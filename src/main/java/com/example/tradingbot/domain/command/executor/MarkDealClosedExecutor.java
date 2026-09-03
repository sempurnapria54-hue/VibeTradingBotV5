package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationState;
import com.example.tradingbot.domain.command.DealFinalizationStateStatus;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.DealFinalizationStateDataService;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет MARK_DEAL_CLOSED — терминальное ребро штатного закрытия
 * (EXIT_PENDING → CLOSED). Ставит терминал только после подтверждённого
 * отсутствия live risk; иначе не терминализирует (failure → retry/error
 * path). Обязательные resultProfit/resultProfitCurrency: сам PnL-расчёт —
 * шаг 7 (граница 6 ↔ 7), здесь — механический placeholder (ZERO + settle
 * currency), чтобы удовлетворить инвариант наличия числа на чистом
 * терминале; шаг 7 заменит расчётом. Если число неисчислимо (нет settle
 * currency) — failure → DEAL-Q2 ошибочная тропа. RiskValidator не
 * вызывается. Retry-anchor — DealFinalizationState(deal, MARK_CLOSED). См.
 * docs/components/MarkDealClosedExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class MarkDealClosedExecutor implements CommandExecutor {

    private final DealFinalizationStateDataService finalizationStateDataService;
    private final DealDataService dealDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.MARK_DEAL_CLOSED;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        DealFinalizationState state = finalizationStateDataService
                .findByDealIdAndType(command.getDealId(), DealFinalizationType.MARK_CLOSED)
                .orElseThrow(() -> new IllegalStateException(
                        "DealFinalizationState(MARK_CLOSED) not found dealId=" + command.getDealId()));
        Deal deal = dealContext.getDeal();
        if (Deal.Status.CLOSED.equals(deal.getStatus())) {
            return complete(state);
        }
        if (hasLiveRisk(deal.livePosition()) || hasLiveEntities(deal)) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                    "Cannot mark deal CLOSED while live risk (position / orders / algo) remains");
        }
        if (isNull(deal.getResultProfit())) {
            String settleCurrency = settleCurrency(dealContext.getBalanceContainer());
            if (isNull(settleCurrency)) {
                return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR,
                        "Cannot resolve resultProfitCurrency for clean close");
            }
            // PnL-расчёт — шаг 7; здесь механический placeholder под инвариант чистого терминала.
            deal.setResultProfit(BigDecimal.ZERO);
            deal.setResultProfitCurrency(settleCurrency);
        }
        if (isNull(deal.getCloseReason())) {
            // Причина берётся СТАРШИНСТВОМ причин траншей, а не умолчанием:
            // подставленный STRATEGY_EXIT объявлял бы штатным выходом и то
            // закрытие, которого стратегия не запрашивала.
            deal.setCloseReason(deal.closeReasonBySeniority());
        }
        deal.setStatus(Deal.Status.CLOSED);
        dealDataService.save(deal);
        return complete(state);
    }

    private ServiceCommandExecutionResult complete(DealFinalizationState state) {
        if (DealFinalizationStateStatus.COMPLETED.equals(state.getStatus())) {
            return ServiceCommandExecutionResult.ok();
        }
        state.setStatus(DealFinalizationStateStatus.COMPLETED);
        finalizationStateDataService.save(state);
        return ServiceCommandExecutionResult.ok();
    }

    private Boolean hasLiveRisk(Position position) {
        return nonNull(position) && isTrue(position.hasLiveRisk());
    }

    /** Терминал не ставится, пока на сделке остаются live ordinary/algo orders (live risk шире позиции). */
    private Boolean hasLiveEntities(Deal deal) {
        boolean liveOrder = isNotEmpty(deal.getOrders())
                && deal.getOrders().stream().anyMatch(order -> isTrue(order.isLive()));
        boolean liveAlgo = isNotEmpty(deal.getAlgoOrders())
                && deal.getAlgoOrders().stream().anyMatch(algo -> isTrue(algo.isLive()));
        return liveOrder || liveAlgo;
    }

    private String settleCurrency(BalanceContainer balanceContainer) {
        if (isNull(balanceContainer) || isEmpty(balanceContainer.getBalances())) {
            return null;
        }
        return balanceContainer.getBalances().stream()
                .map(Balance::getExternalCurrency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
