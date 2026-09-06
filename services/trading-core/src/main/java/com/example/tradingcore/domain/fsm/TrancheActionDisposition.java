package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.payload.RefreshBalanceCommandPayload;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Переводит план действия в исход прохода транша: команду, терминал
 * кандидата, просьбу увести сделку ошибочной тропой либо добычу фактов.
 *
 * <p><b>Носитель один на шесть обработчиков.</b> Карта реакции
 * принадлежит резолверу (docs/components/RiskBlockResolver.md), и её
 * копия у каждого обработчика разошлась бы первой же правкой: реакций
 * шесть, а разрешающих среди них две.
 *
 * <p><b>Терминал кандидата ставится только там, где живого риска нет.</b>
 * Реакцию {@code CLOSE_CANDIDATE_DEAL} резолвер производит исключительно
 * на бессрочном отказе до появления живого риска, то есть в предвходовой
 * проверке; на прочих статусах она недостижима, и отдельной охраны здесь
 * не заводится — вторая копия того же условия разошлась бы с первой.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrancheActionDisposition {

    private final SystemActionExecutor systemActionExecutor;
    private final StrategyWorkRunner workRunner;

    /** Исход прохода по плану действия. */
    public TrancheTransition dispose(ActionPlan plan, DealContext dealContext, DealTranche tranche) {
        if (isTrue(plan.hasCommand())) {
            return TrancheTransition.command(plan.getCommand());
        }
        if (isTrue(plan.isBlocked())) {
            return blocked(plan.getBlocked(), dealContext, tranche);
        }
        if (isTrue(plan.hasCalculationError())) {
            return isTrue(workRunner.calculationFailureIsFatal(plan.getCalculationError()))
                    ? TrancheTransition.escalate()
                    : TrancheTransition.stay();
        }
        return TrancheTransition.stay();
    }

    private TrancheTransition blocked(RiskBlockAction reaction, DealContext dealContext, DealTranche tranche) {
        log.info("Risk precheck blocked action trancheId={} type={} code={} comment={}",
                tranche.getId(), reaction.getType(), reaction.getRiskCode(), reaction.getComment());
        return switch (reaction.getType()) {
            case CLOSE_CANDIDATE_DEAL -> TrancheTransition.close(DealTranche.CloseReason.RISK_CONTROL);
            case MOVE_DEAL_TO_ERROR -> TrancheTransition.escalate();
            case REQUEST_REFRESH -> balanceFetch(dealContext);
            case SKIP_ACTION, CONTINUE, CONTINUE_WITH_WARNING -> TrancheTransition.stay();
        };
    }

    /**
     * Добыча снимка средств звеном системного действия. Обработчик
     * добывающих команд напрямую не эмитит: у добычи есть анкер, бюджет
     * попыток и цель, и все три живут на строке исполнения
     * (docs/components/SystemActionExecutor.md).
     */
    public TrancheTransition balanceFetch(DealContext dealContext) {
        String settleCurrency = isNull(dealContext.getInstrument())
                ? null
                : dealContext.getInstrument().getExternalSettlementCurrency();
        return command(systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext, null,
                        ServiceCommandType.REFRESH_BALANCE_COMMAND,
                        new RefreshBalanceCommandPayload(settleCurrency)));
    }

    /**
     * Добыча фактов по первой ненакрытой сущности сделки: живая заявка,
     * живая условная заявка, живая позиция. Звено выводит сам исполнитель
     * системных действий — состав цикла един для всех троп.
     */
    public TrancheTransition contextFetch(DealContext dealContext) {
        return command(systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext,
                null));
    }

    /**
     * Причина сделки, наследуемая траншем, закрытым КАСКАДОМ сворачивания.
     * Своего значения под это не заводится: транш получает то же значение,
     * которым уводится в выход сама сделка
     * (docs/lifecycles/DealTranche.md §«Писатель причины закрытия транша —
     * обработчик терминального ребра»).
     *
     * <p>Пустой причины сделки на этом ребре не бывает — её пишет тот же
     * ход, что уводит сделку в выход; наблюдение пустоты означает
     * рассогласование, и запись идёт запасным значением, а не молчаливым
     * пропуском.
     */
    public DealTranche.CloseReason inheritedCloseReason(Deal deal) {
        DealTranche.CloseReason inherited = deal.inheritedTrancheCloseReason();
        if (isNull(inherited)) {
            log.warn("Collapsing deal carries no inheritable close reason dealId={}", deal.getId());
            return DealTranche.CloseReason.UNKNOWN;
        }
        return inherited;
    }

    private TrancheTransition command(Optional<ServiceCommand> command) {
        return command.map(TrancheTransition::command).orElseGet(TrancheTransition::stay);
    }
}
