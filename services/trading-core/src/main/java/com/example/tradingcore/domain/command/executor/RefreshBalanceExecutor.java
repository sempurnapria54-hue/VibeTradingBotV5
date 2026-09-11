package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.RefreshBalanceCommandPayload;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.BalanceContainerDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Обновляет снимок средств счёта: приземляет ответ площадки, заводя строку
 * при её отсутствии и <b>замещая</b> набор валютных строк целиком
 * (docs/components/RefreshBalanceExecutor.md).
 *
 * <p>Команда читающая и риска не создаёт: преконтроль её не проверяет
 * (docs/rules/risk-validator-scope.md). Средства управляемой торговой
 * сущностью не являются — своего жизненного цикла и резолвера статуса у
 * снимка нет.
 *
 * <p><b>Пустого контракта у чтения нет:</b> успешное чтение обязано
 * вернуть снимок с расчётной валютой; пустой ответ, отсутствие валюты либо
 * негодные поля дают контролируемую ошибку на границе коннектора
 * (docs/rules/raw-exchange-dto-boundary.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshBalanceExecutor implements CommandExecutor {

    private final BalanceContainerDataService balanceContainerDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final AnomalyReportService anomalyReportService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_BALANCE_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        RefreshBalanceCommandPayload payload = (RefreshBalanceCommandPayload) command.getPayload();
        ExchangeAccount account = dealContext.getExchangeAccount();
        BalanceContainer observed = exchangeOperationsClient.getBalance(account.getInternalId(),
                payload.getSettleCurrency());
        BalanceContainer landed = balanceContainerDataService.save(merge(account.getId(), observed));
        observeRiskBase(dealContext, landed);
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Снимок счёта один и заводится при первом чтении: идентичность
     * прежней строки сохраняется, поля и набор валют берутся из ответа
     * площадки.
     */
    private BalanceContainer merge(Long exchangeAccountId, BalanceContainer observed) {
        BalanceContainer container = balanceContainerDataService.findByExchangeAccountId(exchangeAccountId)
                .orElseGet(BalanceContainer::new);
        container.setExchangeAccountId(exchangeAccountId);
        container.setExternalUpdatedAt(observed.getExternalUpdatedAt());
        container.setExternalTotalEquity(observed.getExternalTotalEquity());
        container.setExternalAdjustedEquity(observed.getExternalAdjustedEquity());
        container.setExternalAvailableEquity(observed.getExternalAvailableEquity());
        container.replaceBalances(observed.getBalances());
        return container;
    }

    /**
     * <b>Первое наблюдение базы риска.</b> Пустую базу счёта заполняет та
     * же транзакция, что приземляет снимок. Условие тройное, и каждый
     * конъюнкт несёт своё (docs/spec/risk-limits.json,
     * {@code riskBaseObserved}):
     *
     * <ul>
     *   <li><b>база пуста</b> — запись однократная и только из пустоты:
     *       непустую базу приземление снимка не трогает ни при каком
     *       остатке;</li>
     *   <li><b>операнд резолвился</b> — строка расчётной валюты в снимке
     *       есть;</li>
     *   <li><b>остаток СТРОГО положителен</b> — ноль и отрицательный
     *       наблюдением не считаются: записанный ноль автоматически уже не
     *       поднялся бы, и счёт остался бы в отказе до явного действия
     *       держателя.</li>
     * </ul>
     *
     * <p>База осталась пустой, хотя снимок приземлился, — это ОМИССИЯ,
     * направленная в разрешающую сторону, поэтому она объявляется
     * ПЕРСИСТЕНТНОЙ строкой, а не логом: лог не запрашивается, не
     * агрегируется и не переживает ротацию (docs/concept.md). Без строки
     * пустая база не отличала бы «команда ни разу не отрабатывала» от
     * «отработала, наблюдать было нечего».
     */
    private void observeRiskBase(DealContext dealContext, BalanceContainer container) {
        ExchangeAccount account = dealContext.getExchangeAccount();
        if (nonNull(account.getRiskBase())) {
            return;
        }
        Balance settleRow = settlementRow(dealContext.getInstrument(), container);
        BigDecimal candidate = nonNull(settleRow) ? settleRow.getExternalAvailableBalance() : null;
        if (isNull(candidate) || candidate.signum() <= 0) {
            journalNotObserved(dealContext);
            return;
        }
        account.setRiskBase(candidate);
        account.setRiskBaseCurrency(settleRow.getExternalCurrency());
        exchangeAccountDataService.applyRiskBase(account.getId(), candidate,
                settleRow.getExternalCurrency());
    }

    /**
     * Журнальная строка о ненаблюдённой базе. Природа факта — СОСТОЯНИЕ
     * (пока база пуста, команда тикает каждым проходом), поэтому пишет её
     * тропа состояния: вторая строка по тому же ключу не заводится, пока
     * состояние держится. Радиус — счёт: база живёт на его строке.
     *
     * <p><b>Журнал реакции не гейтит:</b> сбой записи снимок не валит —
     * иначе отказ носителя наблюдаемости отнимал бы приземлившийся факт.
     * Дом клаузы — docs/rules/error-handling-policy.md §«Отказ журнального
     * носителя реакцию не гейтит».
     */
    private void journalNotObserved(DealContext dealContext) {
        log.warn("RISK_BASE_NOT_OBSERVED accountId={} — snapshot landed, base stays empty",
                dealContext.getExchangeAccount().getId());
        try {
            anomalyReportService.journalState(dealContext,
                    HoldSignal.exchangeAccountJournal(Constants.Hold.RISK_BASE_NOT_OBSERVED), null);
        } catch (RuntimeException e) {
            log.error("Journal RISK_BASE_NOT_OBSERVED failed accountId={}",
                    dealContext.getExchangeAccount().getId(), e);
        }
    }

    /**
     * Строка расчётной валюты снимка. Валюта резолвится по ИНСТРУМЕНТУ,
     * ради которого команда эмитирована, — единственная зависимость этого
     * исполнителя от инструмента, и без неё строку операнда не выбрать.
     */
    private Balance settlementRow(Instrument instrument, BalanceContainer container) {
        if (isEmpty(container.getBalances()) || isNull(instrument)) {
            return null;
        }
        return container.getBalances().stream()
                .filter(balance -> instrument.getExternalSettlementCurrency()
                        .equals(balance.getExternalCurrency()))
                .findFirst()
                .orElse(null);
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
