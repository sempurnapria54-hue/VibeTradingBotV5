package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.RefreshBalanceCommandPayload;
import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.BalanceContainerDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.util.OkxParse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет REFRESH_BALANCE (read-only): получает validated
 * BalanceContainerExternalSnapshot, upsert'ит BalanceContainer по
 * exchange_id, обновляет account-level поля (parse строк → BigDecimal) и
 * полностью заменяет список Balance. Не проходит через RiskValidator;
 * normal null-контракт не используется (пустой/невалидный → controlled
 * error в IntegrationService). См.
 * docs/components/RefreshBalanceExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class RefreshBalanceExecutor implements CommandExecutor {

    private final BalanceContainerDataService balanceContainerDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_BALANCE;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        RefreshBalanceCommandPayload payload = (RefreshBalanceCommandPayload) command.getPayload();
        BalanceContainerExternalSnapshot snapshot = integrationService.getBalance(payload.getSettleCurrency());
        Long exchangeId = dealContext.getExchange().getId();
        BalanceContainer container = balanceContainerDataService.findByExchangeId(exchangeId)
                .orElseGet(BalanceContainer::new);
        container.setExchangeId(exchangeId);
        container.setExternalUpdatedAt(snapshot.getExternalUpdatedAt());
        container.setExternalTotalEquity(OkxParse.decimal(snapshot.getExternalTotalEquity()));
        container.setExternalAdjustedEquity(OkxParse.decimal(snapshot.getExternalAdjustedEquity()));
        container.setExternalAvailableEquity(OkxParse.decimal(snapshot.getExternalAvailableEquity()));
        container.replaceBalances(buildBalances(snapshot.getBalances()));
        balanceContainerDataService.save(container);
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    private List<Balance> buildBalances(List<BalanceExternalSnapshot> snapshots) {
        if (isEmpty(snapshots)) {
            return null;
        }
        return snapshots.stream().map(this::toBalance).collect(toList());
    }

    private Balance toBalance(BalanceExternalSnapshot snapshot) {
        Balance balance = new Balance();
        balance.setExternalCurrency(snapshot.getExternalCurrency());
        balance.setExternalUpdatedAt(snapshot.getExternalUpdatedAt());
        balance.setExternalEquity(OkxParse.decimal(snapshot.getExternalEquity()));
        balance.setExternalCashBalance(OkxParse.decimal(snapshot.getExternalCashBalance()));
        balance.setExternalAvailableBalance(OkxParse.decimal(snapshot.getExternalAvailableBalance()));
        balance.setExternalFrozenBalance(OkxParse.decimal(snapshot.getExternalFrozenBalance()));
        return balance;
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
