package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
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
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.BalanceContainerDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.util.OkxParse;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshBalanceExecutor implements CommandExecutor {

    private final BalanceContainerDataService balanceContainerDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeDataService exchangeDataService;
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
        observeRiskBase(dealContext.getExchange(), container);
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * <b>Первое наблюдение базы риска.</b> Пустую {@code Exchange.riskBase}
     * заполняет транзакция приземления снимка. Условие тройное, и каждый
     * конъюнкт несёт своё (docs/spec/risk-limits.json §riskBaseObserved):
     *
     * <ul>
     *   <li><b>база пуста</b> — запись однократная и только из пустоты:
     *       непустую базу приземление снимка не трогает ни при каком
     *       остатке, вверх она автоматически не ходит;</li>
     *   <li><b>операнд резолвился</b> — строка расчётной валюты в снимке
     *       есть;</li>
     *   <li><b>остаток СТРОГО положителен</b> — ноль и отрицательный
     *       наблюдением не считаются: записанный ноль автоматически уже
     *       не поднялся бы, и счёт остался бы в отказе до явного
     *       действия держателя.</li>
     * </ul>
     *
     * <p>База осталась пустой, хотя снимок приземлился, — это ОМИССИЯ, и
     * она направлена в разрешающую сторону, поэтому объявляется в лог:
     * без объявления пустая база не отличала бы «команда ни разу не
     * отрабатывала» от «отработала, наблюдать было нечего».
     */
    private void observeRiskBase(Exchange exchange, BalanceContainer container) {
        if (isNull(exchange) || nonNull(exchange.getRiskBase())) {
            return;
        }
        Balance settleRow = settlementRow(exchange, container);
        BigDecimal candidate = nonNull(settleRow) ? settleRow.getExternalAvailableBalance() : null;
        if (isNull(candidate) || candidate.signum() <= 0) {
            log.warn("RISK_BASE_NOT_OBSERVED exchangeId={} — snapshot landed, base stays empty",
                    exchange.getId());
            return;
        }
        exchange.setRiskBase(candidate);
        exchange.setRiskBaseCurrency(settleRow.getExternalCurrency());
        exchangeDataService.save(exchange);
    }

    /**
     * Строка расчётной валюты снимка. Валюта берётся у самой биржи, если
     * она уже названа; иначе — первая строка снимка: контур фазы 1
     * одновалютный, и «первая» здесь есть «единственная».
     */
    private Balance settlementRow(Exchange exchange, BalanceContainer container) {
        if (isEmpty(container.getBalances())) {
            return null;
        }
        if (isNull(exchange.getRiskBaseCurrency())) {
            return container.getBalances().getFirst();
        }
        return container.getBalances().stream()
                .filter(balance -> exchange.getRiskBaseCurrency().equals(balance.getExternalCurrency()))
                .findFirst()
                .orElse(null);
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
