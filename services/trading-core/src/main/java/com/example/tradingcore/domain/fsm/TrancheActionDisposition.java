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
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshBalanceCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Переводит план действия в исход прохода транша: команду, терминал
 * кандидата, просьбу увести сделку ошибочной тропой либо добычу фактов —
 * в том числе снимка средств, которого ждёт отложенный акт, создающий риск.
 *
 * <p><b>Носитель один на шесть обработчиков.</b> Карта реакции
 * принадлежит резолверу (docs/components/RiskBlockResolver.md), и её
 * копия у каждого обработчика разошлась бы первой же правкой: реакций
 * четыре, а разрешающая среди них одна.
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
            return blocked(plan.getBlocked(), tranche);
        }
        if (isTrue(plan.hasCalculationError())) {
            return isTrue(workRunner.calculationFailureIsFatal(plan.getCalculationError()))
                    ? TrancheTransition.escalate()
                    : TrancheTransition.stay();
        }
        if (isTrue(plan.getAwaitingBalance())) {
            log.debug("Risk-creating act awaits a fresh balance snapshot, fetch ordered trancheId={}",
                    tranche.getId());
            return balanceFetch(dealContext);
        }
        return TrancheTransition.stay();
    }

    private TrancheTransition blocked(RiskBlockAction reaction, DealTranche tranche) {
        log.info("Risk precheck blocked action trancheId={} type={} code={} comment={}",
                tranche.getId(), reaction.getType(), reaction.getRiskCode(), reaction.getComment());
        return switch (reaction.getType()) {
            case CLOSE_CANDIDATE_DEAL -> TrancheTransition.close(DealTranche.CloseReason.RISK_CONTROL);
            case MOVE_DEAL_TO_ERROR -> TrancheTransition.escalate();
            case SKIP_ACTION, CONTINUE -> TrancheTransition.stay();
        };
    }

    /**
     * Добыча снимка средств звеном системного действия. Заказчиков у неё
     * два: предвходовая проверка до работы и план действия, отложивший акт,
     * создающий риск, несвежим снимком, — на всякой стадии транша
     * (docs/components/models/ActionPlan.md). Обработчик
     * добывающих команд напрямую не эмитит: у добычи есть анкер, бюджет
     * попыток и цель, и все три живут на строке исполнения
     * (docs/components/SystemActionExecutor.md). Повтор заказа, пока добыча
     * не сошлась, ограничивает та же строка — её откат и бюджет попыток;
     * пустой исход значит, что звено ждёт отката.
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
     * Добыча факта НАЗВАННОЙ заявки — ноги, чей налив либо снятие ждёт
     * наблюдения. Звено называется явно, а не выводится: производный вывод
     * берёт первую живую заявку СДЕЛКИ, и на многотраншевой сделке ею
     * оказывалась бы нога соседа — эта не наблюдалась бы ни за какое число
     * проходов (docs/components/SystemActionExecutor.md §«Вторая форма
     * называет звено ЯВНО»). Пусто — звено ждёт отката повтора.
     */
    public Optional<ServiceCommand> orderFetch(DealContext dealContext, Long orderId) {
        return systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext, null,
                ServiceCommandType.REFRESH_ORDER_COMMAND, new RefreshOrderCommandPayload(orderId));
    }

    /** Добыча факта названной отдельной условной заявки; довод явного звена — тот же. */
    public Optional<ServiceCommand> algoOrderFetch(DealContext dealContext, Long algoOrderId) {
        return systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext, null,
                ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND, new RefreshAlgoOrderCommandPayload(algoOrderId));
    }

    /**
     * Добыча позиции: живой эпизод и запись закрытия. Звено сделки одно —
     * эпизод у неё не бывает чужим, — но называется явно по тому же доводу:
     * пока у сделки есть живая заявка, производный вывод до позиции не
     * доходит.
     */
    public Optional<ServiceCommand> positionFetch(DealContext dealContext) {
        return systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext, null,
                ServiceCommandType.REFRESH_POSITION_COMMAND, null);
    }

    /**
     * Терминал транша, закрытого КАСКАДОМ сворачивания, причиной, которую он
     * наследует от сделки. Своего значения под это не заводится: транш
     * получает то же значение, которым уводится в выход сама сделка
     * (docs/lifecycles/DealTranche.md §«Писатель причины закрытия транша —
     * обработчик терминального ребра»).
     *
     * <p><b>Пустая наследуемая причина терминала не даёт — исход просьба
     * ошибочной тропы, без ребра и без причины.</b> Её пишет тот же ход, что
     * уводит сделку в выход, поэтому пустота значит нарушенный инвариант
     * агрегата, а не исход транша: подобранное значение записало бы в журнал
     * исход, которого не было, а сделка дошла бы до штатного терминала с
     * пустой причиной. Отказ громкий — его носитель статус сделки.
     */
    public TrancheTransition inheritedClose(Deal deal) {
        DealTranche.CloseReason inherited = deal.inheritedTrancheCloseReason();
        if (isNull(inherited)) {
            log.warn("Collapsing deal carries no inheritable close reason, deal goes to ERROR dealId={}",
                    deal.getId());
            return TrancheTransition.escalate();
        }
        return TrancheTransition.close(inherited);
    }

    private TrancheTransition command(Optional<ServiceCommand> command) {
        return command.map(TrancheTransition::command).orElseGet(TrancheTransition::stay);
    }
}
